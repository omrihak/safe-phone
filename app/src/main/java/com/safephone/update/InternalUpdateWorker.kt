package com.safephone.update

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.safephone.BuildConfig
import com.safephone.R
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import androidx.core.content.pm.PackageInfoCompat

class InternalUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(InternalUpdateManifest::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.ENABLE_INTERNAL_AUTO_UPDATE) {
            return@withContext Result.success()
        }
        val base = BuildConfig.INTERNAL_UPDATE_BASE_URL.trim()
        val track = BuildConfig.INTERNAL_UPDATE_TRACK_REF.trim()
        if (base.isEmpty() || track.isEmpty()) {
            return@withContext Result.success()
        }
        // Matches CI: one prerelease per branch, tag internal-<sanitizedBranch>
        val manifestUrl = "${base.trimEnd('/')}/releases/download/internal-$track/manifest.json"
        if (!manifestUrl.startsWith("https://")) {
            return@withContext Result.failure()
        }

        InternalUpdateNotifications.ensureChannels(applicationContext)
        setForeground(
            ForegroundInfo(
                InternalUpdateNotifications.NOTIFICATION_ID_PROGRESS,
                progressNotification(applicationContext.getString(R.string.internal_update_progress_check)),
                foregroundServiceType(),
            ),
        )

        val manifestBody = fetchUtf8(manifestUrl) ?: return@withContext Result.retry()
        val manifest = try {
            moshi.fromJson(manifestBody)!!
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        val pm = applicationContext.packageManager
        val pkg = applicationContext.packageName
        val currentCode = try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            PackageInfoCompat.getLongVersionCode(info).toInt()
        } catch (_: Exception) {
            return@withContext Result.failure()
        }

        if (manifest.versionCode <= currentCode) {
            return@withContext Result.success()
        }

        if (!manifest.apkUrl.startsWith("https://")) {
            return@withContext Result.failure()
        }

        setForeground(
            ForegroundInfo(
                InternalUpdateNotifications.NOTIFICATION_ID_PROGRESS,
                progressNotification(applicationContext.getString(R.string.internal_update_progress_download)),
                foregroundServiceType(),
            ),
        )

        val apkFile = File(applicationContext.cacheDir, "internal-update-${manifest.versionCode}.apk")
        if (!downloadToFile(manifest.apkUrl, apkFile)) {
            apkFile.delete()
            return@withContext Result.retry()
        }

        val actualSha = apkFile.sha256Hex().lowercase()
        if (actualSha != manifest.sha256.lowercase()) {
            apkFile.delete()
            return@withContext Result.failure()
        }

        val committed = installWithSession(applicationContext, apkFile)
        apkFile.delete()
        if (committed) {
            return@withContext Result.success()
        }
        return@withContext Result.retry()
    }

    private fun progressNotification(text: String): Notification =
        InternalUpdateNotifications.progressNotification(
            applicationContext,
            applicationContext.getString(R.string.internal_update_progress_title),
            text,
        )

    private fun fetchUtf8(urlStr: String): String? {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadToFile(urlStr: String, outFile: File): Boolean {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
        }
        return try {
            FileOutputStream(outFile).use { fos ->
                conn.inputStream.use { ins ->
                    ins.copyTo(fos)
                }
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun installWithSession(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        return try {
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, -1).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callbackIntent = Intent(context, InternalUpdateInstallerReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                @Suppress("DEPRECATION")
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                flags,
            )
            session.commit(pendingIntent.intentSender)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { session.abandon() }
            false
        }
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private fun File.sha256Hex(): String {
        val md = MessageDigest.getInstance("SHA-256")
        inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
