package com.safephone.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/**
 * Receives the result of [SmsManager.sendTextMessage] for partner alerts. The matching
 * [PendingIntent] must use [PendingIntent.FLAG_MUTABLE] on API 31+.
 */
class PartnerSmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (resultCode) {
            Activity.RESULT_OK ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = true,
                    errorDetail = null,
                )
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = false,
                    errorDetail = "GENERIC_FAILURE",
                )
            SmsManager.RESULT_ERROR_NO_SERVICE ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = false,
                    errorDetail = "NO_SERVICE",
                )
            SmsManager.RESULT_ERROR_NULL_PDU ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = false,
                    errorDetail = "NULL_PDU",
                )
            SmsManager.RESULT_ERROR_RADIO_OFF ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = false,
                    errorDetail = "RADIO_OFF",
                )
            else ->
                PartnerBlockAlert.onPartnerSmsSendFinished(
                    context.applicationContext,
                    success = false,
                    errorDetail = "resultCode=$resultCode",
                )
        }
    }
}
