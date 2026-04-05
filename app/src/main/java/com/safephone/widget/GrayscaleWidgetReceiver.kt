package com.safephone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.safephone.R
import com.safephone.data.FocusPreferences
import com.safephone.service.GrayscaleManualToggleResult
import com.safephone.service.SystemMonochromeController
import kotlinx.coroutines.runBlocking

class GrayscaleWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_GRAYSCALE) {
            val appCtx = context.applicationContext
            val result = runBlocking {
                val prefs = FocusPreferences(appCtx)
                SystemMonochromeController(appCtx, prefs).toggleManualGrayscale()
            }
            val msg = when (result) {
                GrayscaleManualToggleResult.NoPermission ->
                    appCtx.getString(R.string.widget_grayscale_toast_no_permission)
                GrayscaleManualToggleResult.TurnedOn ->
                    appCtx.getString(R.string.widget_grayscale_toast_on)
                GrayscaleManualToggleResult.TurnedOff ->
                    appCtx.getString(R.string.widget_grayscale_toast_off)
                GrayscaleManualToggleResult.Error ->
                    appCtx.getString(R.string.widget_grayscale_toast_error)
            }
            Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE_GRAYSCALE = "com.safephone.WIDGET_TOGGLE_GRAYSCALE"

        fun refreshAll(context: Context) {
            val appCtx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appCtx)
            val cn = ComponentName(appCtx, GrayscaleWidgetReceiver::class.java)
            val ids = mgr.getAppWidgetIds(cn)
            if (ids.isEmpty()) return
            updateWidgets(appCtx, mgr, ids)
        }

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            val appCtx = context.applicationContext
            val prefs = FocusPreferences(appCtx)
            val controller = SystemMonochromeController(appCtx, prefs)
            val canApply = controller.canApply()
            val monoOn = controller.isMonochromeActive()
            val title = appCtx.getString(R.string.widget_grayscale_title)
            val subtitle = when {
                !canApply -> appCtx.getString(R.string.widget_grayscale_subtitle_need_permission)
                monoOn -> appCtx.getString(R.string.widget_grayscale_subtitle_on)
                else -> appCtx.getString(R.string.widget_grayscale_subtitle_off)
            }
            val views = RemoteViews(appCtx.packageName, R.layout.widget_grayscale).apply {
                setTextViewText(R.id.widget_grayscale_title, title)
                setTextViewText(R.id.widget_grayscale_subtitle, subtitle)
                setViewVisibility(
                    R.id.widget_grayscale_indicator_on,
                    if (monoOn && canApply) View.VISIBLE else View.GONE,
                )
                val pi = PendingIntent.getBroadcast(
                    appCtx,
                    0,
                    Intent(appCtx, GrayscaleWidgetReceiver::class.java).setAction(ACTION_TOGGLE_GRAYSCALE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_grayscale_root, pi)
            }
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
