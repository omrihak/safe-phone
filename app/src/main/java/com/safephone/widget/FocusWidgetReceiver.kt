package com.safephone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.safephone.R
import com.safephone.data.AppDatabase
import com.safephone.data.BreakPolicyEntity
import com.safephone.data.FocusPreferences
import com.safephone.service.BreakManager
import com.safephone.service.FocusEnforcementService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FocusWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_START_BREAK) {
            val appCtx = context.applicationContext
            runBlocking {
                val prefs = FocusPreferences(appCtx)
                val policy = AppDatabase.getInstance(appCtx).breakPolicyDao().get() ?: BreakPolicyEntity()
                val mgr = BreakManager(appCtx, prefs)
                when {
                    mgr.isOnBreakNow() ->
                        Toast.makeText(appCtx, appCtx.getString(R.string.widget_toast_already_on_break), Toast.LENGTH_SHORT).show()
                    mgr.breaksRemainingToday(policy) <= 0 ->
                        Toast.makeText(appCtx, appCtx.getString(R.string.widget_toast_no_breaks), Toast.LENGTH_SHORT).show()
                    !mgr.gapSatisfied(policy) ->
                        Toast.makeText(appCtx, appCtx.getString(R.string.widget_toast_break_gap), Toast.LENGTH_SHORT).show()
                    mgr.startBreak(policy.breakDurationMinutes, policy) -> {
                        FocusEnforcementService.start(appCtx)
                        Toast.makeText(appCtx, appCtx.getString(R.string.widget_toast_break_started), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_START_BREAK = "com.safephone.WIDGET_START_BREAK"

        private data class WidgetUpdate(
            val title: String,
            val subtitle: String,
            val onBreak: Boolean,
            val chronoBaseElapsed: Long,
        )

        fun refreshAll(context: Context) {
            val appCtx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appCtx)
            val cn = ComponentName(appCtx, FocusWidgetReceiver::class.java)
            val ids = mgr.getAppWidgetIds(cn)
            if (ids.isEmpty()) return
            updateWidgets(appCtx, mgr, ids)
        }

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            val appCtx = context.applicationContext
            val (title, subtitle, onBreak, chronoBaseElapsed) = runBlocking {
                val prefs = FocusPreferences(appCtx)
                val policy = AppDatabase.getInstance(appCtx).breakPolicyDao().get() ?: BreakPolicyEntity()
                val mgr = BreakManager(appCtx, prefs)
                val onBreakNow = mgr.isOnBreakNow()
                val breakEndWall = prefs.breakEndEpochMs.first()
                val nowWall = System.currentTimeMillis()
                val remaining = mgr.breaksRemainingToday(policy)
                val titleStr =
                    if (onBreakNow) appCtx.getString(R.string.widget_title_on_break)
                    else appCtx.getString(R.string.widget_title_take_break)
                val subtitleStr = when {
                    onBreakNow -> ""
                    remaining <= 0 -> appCtx.getString(R.string.widget_subtitle_no_breaks)
                    else -> appCtx.resources.getQuantityString(R.plurals.widget_breaks_left, remaining, remaining)
                }
                val chronoBase =
                    if (onBreakNow && breakEndWall != null && nowWall < breakEndWall) {
                        SystemClock.elapsedRealtime() + (breakEndWall - nowWall)
                    } else {
                        0L
                    }
                WidgetUpdate(titleStr, subtitleStr, onBreakNow, chronoBase)
            }
            val views = RemoteViews(appCtx.packageName, R.layout.widget_focus).apply {
                setTextViewText(R.id.widget_title, title)
                if (onBreak) {
                    setViewVisibility(R.id.widget_subtitle, View.GONE)
                    setViewVisibility(R.id.widget_break_chronometer, View.VISIBLE)
                    setChronometerCountDown(R.id.widget_break_chronometer, true)
                    setChronometer(R.id.widget_break_chronometer, chronoBaseElapsed, null, true)
                } else {
                    setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                    setViewVisibility(R.id.widget_break_chronometer, View.GONE)
                    setChronometer(R.id.widget_break_chronometer, 0, null, false)
                    setTextViewText(R.id.widget_subtitle, subtitle)
                }
                val pi = PendingIntent.getBroadcast(
                    appCtx,
                    0,
                    Intent(appCtx, FocusWidgetReceiver::class.java).setAction(ACTION_START_BREAK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_root, pi)
            }
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
