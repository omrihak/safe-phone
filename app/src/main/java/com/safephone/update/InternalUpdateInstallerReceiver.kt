package com.safephone.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.safephone.R

class InternalUpdateInstallerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        InternalUpdateNotifications.ensureChannels(context)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                InternalUpdateNotifications.showResult(
                    context,
                    context.getString(R.string.internal_update_notif_installed_title),
                    context.getString(R.string.internal_update_notif_installed_text),
                )
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                } else {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
                InternalUpdateNotifications.showResult(
                    context,
                    context.getString(R.string.internal_update_notif_action_title),
                    context.getString(R.string.internal_update_notif_action_text),
                )
            }
            else -> {
                val detail = if (message.isNotEmpty()) message else status.toString()
                InternalUpdateNotifications.showResult(
                    context,
                    context.getString(R.string.internal_update_notif_failed_title),
                    context.getString(R.string.internal_update_notif_failed_text, detail),
                )
            }
        }
    }
}
