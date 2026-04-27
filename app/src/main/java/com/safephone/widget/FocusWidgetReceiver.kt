package com.safephone.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import java.time.ZoneId

class FocusWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        scheduleNextGrantAlarm(context)
    }

    override fun onDisabled(context: Context) {
        cancelGrantAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_WIDGET_GRANT_ALARM) {
            refreshAll(context)
            return
        }
        if (intent?.action == ACTION_START_BREAK) {
            val appCtx = context.applicationContext
            runBlocking {
                val prefs = FocusPreferences(appCtx)
                val policy = AppDatabase.getInstance(appCtx).breakPolicyDao().get() ?: BreakPolicyEntity()
                val mgr = BreakManager(appCtx, prefs)
                when {
                    mgr.isOnBreakNow() ->
                        Toast.makeText(appCtx, appCtx.getString(R.string.widget_toast_already_on_break), Toast.LENGTH_SHORT).show()
                    mgr.breaksRemainingToday(policy) <= 0 -> {
                        val waitMinutes = mgr.minutesUntilNextGrant(policy)
                        val msg =
                            if (waitMinutes != null && waitMinutes > 0) {
                                appCtx.getString(R.string.widget_toast_break_gap, waitMinutes)
                            } else {
                                appCtx.getString(R.string.widget_toast_no_breaks)
                            }
                        Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
                    }
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
        const val ACTION_WIDGET_GRANT_ALARM = "com.safephone.WIDGET_GRANT_ALARM"
        private const val REQ_GRANT_ALARM = 1002

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
            scheduleNextGrantAlarm(appCtx)
        }

        fun scheduleNextGrantAlarm(context: Context) {
            val appCtx = context.applicationContext
            val alarmMgr = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appCtx, FocusWidgetReceiver::class.java).setAction(ACTION_WIDGET_GRANT_ALARM)
            val pi = PendingIntent.getBroadcast(
                appCtx,
                REQ_GRANT_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmMgr.cancel(pi)
            val nextGrantMs = runBlocking {
                val policy = AppDatabase.getInstance(appCtx).breakPolicyDao().get() ?: BreakPolicyEntity()
                BreakManager.nextGrantEpochMs(policy.maxBreaksPerDay, System.currentTimeMillis(), ZoneId.systemDefault())
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextGrantMs, pi)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, nextGrantMs, pi)
            }
        }

        private fun cancelGrantAlarm(context: Context) {
            val appCtx = context.applicationContext
            val alarmMgr = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appCtx, FocusWidgetReceiver::class.java).setAction(ACTION_WIDGET_GRANT_ALARM)
            val pi = PendingIntent.getBroadcast(
                appCtx,
                REQ_GRANT_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmMgr.cancel(pi)
        }
    }
}
