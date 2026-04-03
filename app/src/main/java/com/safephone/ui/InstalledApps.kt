package com.safephone.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.safephone.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

suspend fun Context.loadLaunchableApps(): List<LaunchableApp> = withContext(Dispatchers.Default) {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    @Suppress("DEPRECATION")
    val flags = PackageManager.MATCH_ALL
    val activities = pm.queryIntentActivities(intent, flags)
    activities
        .mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == BuildConfig.APPLICATION_ID) return@mapNotNull null
            val label = ri.loadLabel(pm).toString().trim().ifEmpty { pkg }
            LaunchableApp(packageName = pkg, label = label)
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
}

fun Context.applicationIcon(packageName: String): Drawable? =
    try {
        packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
