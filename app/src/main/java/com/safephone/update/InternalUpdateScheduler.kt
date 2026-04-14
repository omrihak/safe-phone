package com.safephone.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.safephone.BuildConfig
import java.util.concurrent.TimeUnit

object InternalUpdateScheduler {
    private const val PERIODIC_NAME = "internal_update_periodic"
    private const val SESSION_NAME = "internal_update_session"

    fun scheduleIfNeeded(context: Context) {
        if (!BuildConfig.ENABLE_INTERNAL_AUTO_UPDATE) return
        if (BuildConfig.INTERNAL_UPDATE_BASE_URL.isBlank() || BuildConfig.INTERNAL_UPDATE_TRACK_REF.isBlank()) {
            return
        }
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<InternalUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    fun enqueueSessionCheck(context: Context) {
        if (!BuildConfig.ENABLE_INTERNAL_AUTO_UPDATE) return
        if (BuildConfig.INTERNAL_UPDATE_BASE_URL.isBlank() || BuildConfig.INTERNAL_UPDATE_TRACK_REF.isBlank()) {
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val once = OneTimeWorkRequestBuilder<InternalUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SESSION_NAME,
            ExistingWorkPolicy.KEEP,
            once,
        )
    }
}
