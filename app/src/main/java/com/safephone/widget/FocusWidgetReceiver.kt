package com.safephone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.safephone.R
import com.safephone.data.FocusPreferences
import com.safephone.service.FocusEnforcementService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FocusWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = FocusPreferences(context.applicationContext)
        val enabled = runBlocking { prefs.enforcementEnabled.first() }
        val views = RemoteViews(context.packageName, R.layout.widget_focus).apply {
            setTextViewText(
                R.id.widget_text,
                if (enabled) context.getString(R.string.widget_on) else context.getString(R.string.widget_off),
            )
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, FocusWidgetReceiver::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_root, pi)
        }
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE) {
            val prefs = FocusPreferences(context.applicationContext)
            runBlocking {
                val next = !prefs.enforcementEnabled.first()
                prefs.setEnforcementEnabled(next)
                if (next) FocusEnforcementService.start(context.applicationContext)
                else FocusEnforcementService.stop(context.applicationContext)
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, FocusWidgetReceiver::class.java))
            onUpdate(context, mgr, ids)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.safephone.WIDGET_TOGGLE"
    }
}
