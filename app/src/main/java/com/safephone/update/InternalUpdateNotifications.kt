package com.safephone.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safephone.R

internal object InternalUpdateNotifications {
    const val CHANNEL_ID_PROGRESS = "internal_update_progress"
    const val CHANNEL_ID_RESULT = "internal_update_result"
    const val NOTIFICATION_ID_PROGRESS = 7101
    const val NOTIFICATION_ID_RESULT = 7102

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_PROGRESS,
                context.getString(R.string.internal_update_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_RESULT,
                context.getString(R.string.internal_update_channel_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun progressNotification(context: Context, title: String, text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun showResult(context: Context, title: String, text: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID_RESULT)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RESULT, n)
    }
}
