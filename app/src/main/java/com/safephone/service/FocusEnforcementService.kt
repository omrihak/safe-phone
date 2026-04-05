package com.safephone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.safephone.BreakTimeFormatter
import com.safephone.BuildConfig
import com.safephone.R
import com.safephone.accessibility.FocusAccessibilityService
import com.safephone.data.FocusPreferences
import com.safephone.policy.PolicyEngine
import com.safephone.ui.MainActivity
import com.safephone.ui.overlay.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import com.safephone.SafePhoneApp

class FocusEnforcementService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val prefsInit = (application as SafePhoneApp).prefs
        val breakEndInit = runBlocking { prefsInit.breakEndEpochMs.first() }
        val notification = buildForegroundNotification(breakEndInit, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        loopJob?.cancel()
        loopJob = scope.launch {
            val app = application as SafePhoneApp
            val prefs = app.prefs
            val assembler = app.policyAssembler
            val usageRepo = app.usageStatsRepository
            val blockStatsDao = app.database.blockStatsDao()
            var lastNonSelfForegroundPackage: String? = null
            var lastWebHostWhileBrowserForeground: String? = null
            /** Debounce: one count per block "session" (same kind+target), not per poll tick. */
            var lastBlockStatsSignature: String? = null
            var lastNotifKey: String? = null
            val mono = SystemMonochromeController(applicationContext, prefs)
            while (isActive) {
                resetBreakDayIfNeeded(prefs)
                val rawFg = usageRepo.foregroundPackageBestEffort()
                if (rawFg != null && rawFg != BuildConfig.APPLICATION_ID) {
                    lastNonSelfForegroundPackage = rawFg
                }
                val enforcementOverlayUp = BlockOverlayActivity.isShowing()
                val fg = when {
                    rawFg != null && rawFg != BuildConfig.APPLICATION_ID -> rawFg
                    enforcementOverlayUp -> lastNonSelfForegroundPackage ?: rawFg
                    else -> rawFg
                }
                val rawHost = FocusAccessibilityService.lastBrowserHost
                val navPending = FocusAccessibilityService.isBrowserNavigationPending()
                if (fg != null && PolicyEngine.isBrowserPackage(fg)) {
                    when {
                        navPending -> lastWebHostWhileBrowserForeground = null
                        !rawHost.isNullOrBlank() -> lastWebHostWhileBrowserForeground = rawHost
                    }
                } else {
                    lastWebHostWhileBrowserForeground = null
                }
                val host = when {
                    navPending -> null
                    !rawHost.isNullOrBlank() -> rawHost
                    enforcementOverlayUp && fg != null && PolicyEngine.isBrowserPackage(fg) ->
                        lastWebHostWhileBrowserForeground
                    else -> rawHost
                }
                val input = assembler.build(fg, host)
                val decision = PolicyEngine.evaluate(input)
                mono.sync(decision.applyGrayscale)
                if (decision.blockApp || decision.blockWebOverlay) {
                    val browserPkg =
                        if (fg != null && PolicyEngine.isBrowserPackage(fg)) fg else null
                    val blockType = when {
                        decision.blockWebOverlay &&
                            decision.reason == "Strict browser lock (no URL)" -> "browser_lock"
                        decision.blockWebOverlay -> "web"
                        decision.blockApp -> "app"
                        else -> ""
                    }
                    val landingHost =
                        if (decision.blockWebOverlay) host?.takeIf { it.isNotBlank() } else null
                    val landingPkg =
                        if (decision.blockApp) fg?.takeIf { it.isNotBlank() } else null
                    val targetKey = when (blockType) {
                        "browser_lock" -> (browserPkg ?: fg)?.takeIf { it.isNotBlank() }.orEmpty()
                        "web" ->
                            landingHost?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
                                ?: "unknown_host"
                        "app" -> landingPkg ?: ""
                        else -> ""
                    }
                    val signature =
                        if (blockType.isNotBlank() && targetKey.isNotBlank()) "$blockType|$targetKey" else null
                    if (signature != null && signature != lastBlockStatsSignature) {
                        blockStatsDao.increment(
                            LocalDate.now(ZoneId.systemDefault()).toEpochDay(),
                            blockType,
                            targetKey,
                        )
                        lastBlockStatsSignature = signature
                    }
                    BlockOverlayActivity.show(
                        applicationContext,
                        decision.reason,
                        browserPkg,
                        blockType,
                        landingHost,
                        landingPkg,
                    )
                } else {
                    lastBlockStatsSignature = null
                    BlockOverlayActivity.dismiss(applicationContext)
                }
                val breakWall = prefs.breakEndEpochMs.first()
                val wallNow = System.currentTimeMillis()
                val notifKey =
                    if (breakWall != null && wallNow < breakWall) {
                        "b|${(breakWall - wallNow) / 1000}"
                    } else {
                        "f"
                    }
                if (notifKey != lastNotifKey) {
                    lastNotifKey = notifKey
                    val n = buildForegroundNotification(breakWall, wallNow)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIF_ID,
                            n,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                        )
                    } else {
                        startForeground(NOTIF_ID, n)
                    }
                }
                val aggressive = prefs.aggressivePoll.first()
                delay(if (aggressive) 400 else 1200)
            }
        }
        return START_STICKY
    }

    private suspend fun resetBreakDayIfNeeded(prefs: FocusPreferences) {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val stored = prefs.breakDayEpochDay.first()
        // Include stored == 0 (never written): prefs default must not block rolling over to "today".
        if (stored != today) {
            val end = prefs.breakEndEpochMs.first()
            val nowMs = System.currentTimeMillis()
            val breakStillActive = end != null && nowMs < end
            prefs.setBreakState(
                endEpochMs = if (breakStillActive) end else null,
                breaksUsed = 0,
                dayEpochDay = today,
                lastBreakEnd = prefs.lastBreakEndedEpochMs.first(),
            )
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        job.cancel()
        BlockOverlayActivity.dismiss(applicationContext)
        val app = application as SafePhoneApp
        runBlocking {
            SystemMonochromeController(applicationContext, app.prefs).restoreIfHeld()
        }
        super.onDestroy()
    }

    private fun buildForegroundNotification(breakEndWallMs: Long?, nowWallMs: Long): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pending)
            .setOngoing(true)
        val end = breakEndWallMs
        if (end != null && nowWallMs < end) {
            val remaining = (end - nowWallMs).coerceAtLeast(0L)
            b.setContentTitle(getString(R.string.fg_notification_title_on_break))
                .setContentText(
                    getString(
                        R.string.fg_notification_text_on_break,
                        BreakTimeFormatter.formatMmSs(remaining),
                    ),
                )
        } else {
            b.setContentTitle(getString(R.string.fg_notification_title))
                .setContentText(getString(R.string.fg_notification_text))
        }
        return b.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.special_use_fgs)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "focus_enforcement"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.safephone.STOP_ENFORCEMENT"

        fun start(context: Context) {
            val i = Intent(context, FocusEnforcementService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusEnforcementService::class.java))
        }
    }
}
