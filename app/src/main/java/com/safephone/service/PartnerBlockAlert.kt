package com.safephone.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.safephone.R
import com.safephone.data.BlockStatsDao
import com.safephone.data.FocusPreferences
import com.safephone.ui.MainActivity
import kotlinx.coroutines.flow.first

private const val TAG = "PartnerBlockAlert"

/**
 * Sends an automatic SMS to a configured number when block-session counts cross the daily threshold
 * for a given app or site (once per target per day).
 */
object PartnerBlockAlert {

    private const val CHANNEL_ID = "partner_block_alert"
    private const val NOTIF_ID_DENIED = 7101
    private const val NOTIF_ID_SMS_RESULT = 7102

    suspend fun maybeNotifyAfterIncrement(
        context: Context,
        prefs: FocusPreferences,
        blockStatsDao: BlockStatsDao,
        dayEpochDay: Long,
        kind: String,
        targetKey: String,
    ) {
        if (!prefs.partnerBlockAlertEnabled.first()) return
        val threshold = prefs.partnerBlockAlertThreshold.first()
        if (threshold < 1) return
        val dest = normalizeSmsDestination(prefs.partnerAlertPhoneDigits.first()) ?: return

        val count = blockStatsDao.getCount(dayEpochDay, kind, targetKey) ?: 0
        if (count < threshold) return
        if (!prefs.claimPartnerAlertForTarget(dayEpochDay, kind, targetKey)) return

        val body = buildMessage(kind, targetKey, count)
        ensureChannel(context)
        queueSms(context, dest, body)
    }

    /** Sends a short test SMS using the number in the field (does not require Save). */
    fun sendTestSmsNow(context: Context, rawPhone: String) {
        val dest = normalizeSmsDestination(rawPhone) ?: return
        ensureChannel(context)
        queueSms(
            context,
            dest,
            context.getString(R.string.partner_alert_test_message_body),
        )
    }

    fun onPartnerSmsSendFinished(context: Context, success: Boolean, errorDetail: String?) {
        ensureChannel(context)
        if (success) {
            showNotification(
                context,
                NOTIF_ID_SMS_RESULT,
                context.getString(R.string.partner_alert_notif_title_sms_sent),
                context.getString(R.string.partner_alert_notif_text_sms_sent_ok),
                null,
            )
        } else {
            val detail = errorDetail ?: "unknown"
            Log.w(TAG, "Partner SMS failed: $detail")
            showNotification(
                context,
                NOTIF_ID_SMS_RESULT,
                context.getString(R.string.partner_alert_notif_title_sms_failed),
                context.getString(R.string.partner_alert_notif_text_sms_failed_detail, detail),
                null,
            )
        }
    }

    private fun buildMessage(kind: String, targetKey: String, count: Int): String {
        val what = when (kind) {
            "web" -> "site"
            "browser_lock" -> "browser (strict lock)"
            "app" -> "app"
            else -> kind
        }
        val full =
            "SafePhone: I was blocked again ($what: $targetKey). Block sessions today: $count."
        return truncateForSingleSmsSegment(full)
    }

    /** One segment where possible so one [SMS_SENT] result reflects the whole alert. */
    private fun truncateForSingleSmsSegment(message: String, maxChars: Int = 140): String =
        if (message.length <= maxChars) message else message.take(maxChars - 1) + "…"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.partner_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.partner_alert_channel_desc)
        }
        nm.createNotificationChannel(ch)
    }

    private fun queueSms(context: Context, destinationE164: String, message: String) {
        val appCtx = context.applicationContext
        if (!hasSendSmsPermission(appCtx)) {
            showOpenAppNotification(
                appCtx,
                NOTIF_ID_DENIED,
                appCtx.getString(R.string.partner_alert_notif_title_sms_denied),
                appCtx.getString(R.string.partner_alert_notif_text_sms_denied),
            )
            return
        }
        try {
            val sms = smsManagerForSending(appCtx)
            val sentPi = PendingIntent.getBroadcast(
                appCtx,
                0,
                Intent(appCtx, PartnerSmsSentReceiver::class.java),
                smsPendingIntentFlags(),
            )
            sms.sendTextMessage(destinationE164, null, message, sentPi, null)
        } catch (e: Exception) {
            Log.e(TAG, "sendTextMessage threw", e)
            onPartnerSmsSendFinished(
                appCtx,
                success = false,
                errorDetail = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private fun smsPendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return flags
    }

    private fun smsManagerForSending(context: Context): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(SmsManager::class.java)!!
        }
        @Suppress("DEPRECATION")
        return SmsManager.getDefault()
    }

    private fun showOpenAppNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
    ) {
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        showNotification(context, id, title, text, openApp)
    }

    private fun showNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent?,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val pi = contentIntent ?: PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(id, n)
    }

    fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Strips to digits, returns E.164 with leading `+` (required by many carriers/RIL stacks).
     */
    fun normalizeSmsDestination(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "+$digits"
    }
}
