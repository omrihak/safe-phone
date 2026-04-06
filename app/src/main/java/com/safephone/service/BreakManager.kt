package com.safephone.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.safephone.data.BreakPolicyEntity
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class BreakManager(
    private val context: Context,
    private val prefs: FocusPreferences,
) {
    private val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val deviceZone: ZoneId get() = ZoneId.systemDefault()

    suspend fun effectiveBreaksUsedToday(): Int {
        val day = LocalDate.now(deviceZone).toEpochDay()
        val storedDay = prefs.breakDayEpochDay.first()
        var used = prefs.breaksUsedToday.first()
        if (storedDay != day) used = 0
        return used
    }

    suspend fun breaksRemainingToday(policy: BreakPolicyEntity): Int {
        val used = effectiveBreaksUsedToday()
        return (policy.maxBreaksPerDay - used).coerceAtLeast(0)
    }

    suspend fun isOnBreakNow(): Boolean {
        val end = prefs.breakEndEpochMs.first() ?: return false
        return System.currentTimeMillis() < end
    }

    /**
     * True if [policy.minGapBetweenBreaksMinutes] has passed since the last break ended
     * (or no break has ended yet — [FocusPreferences.lastBreakEndedEpochMs] is 0).
     */
    suspend fun gapSatisfied(policy: BreakPolicyEntity): Boolean {
        val lastEnd = prefs.lastBreakEndedEpochMs.first()
        if (lastEnd <= 0L) return true
        val minutes = policy.minGapBetweenBreaksMinutes.coerceAtLeast(0)
        val requiredMs = minutes * 60_000L
        return System.currentTimeMillis() - lastEnd >= requiredMs
    }

    suspend fun canStartBreak(policy: BreakPolicyEntity): Boolean {
        return breaksRemainingToday(policy) > 0 && !isOnBreakNow() && gapSatisfied(policy)
    }

    suspend fun startBreak(durationMinutes: Int, policy: BreakPolicyEntity): Boolean {
        if (!canStartBreak(policy)) return false
        val now = System.currentTimeMillis()
        val day = LocalDate.now(deviceZone).toEpochDay()
        var used = prefs.breaksUsedToday.first()
        val storedDay = prefs.breakDayEpochDay.first()
        if (storedDay != day) used = 0
        val end = now + durationMinutes * 60_000L
        prefs.setBreakState(
            endEpochMs = end,
            breaksUsed = used + 1,
            dayEpochDay = day,
            lastBreakEnd = prefs.lastBreakEndedEpochMs.first(),
        )
        val intent = Intent(context, BreakAlarmReceiver::class.java).setAction(ACTION_END_BREAK)
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_BREAK_END,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
        } else {
            alarm.setExact(AlarmManager.RTC_WAKEUP, end, pi)
        }
        return true
    }

    suspend fun endBreakEarly() {
        prefs.clearBreakTimer()
        val cancelIntent = Intent(context, BreakAlarmReceiver::class.java).setAction(ACTION_END_BREAK)
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_BREAK_END,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pi)
        val used = prefs.breaksUsedToday.first()
        prefs.setBreakState(
            endEpochMs = null,
            breaksUsed = used,
            dayEpochDay = LocalDate.now(deviceZone).toEpochDay(),
            lastBreakEnd = System.currentTimeMillis(),
        )
    }

    companion object {
        const val ACTION_END_BREAK = "com.safephone.END_BREAK"
        private const val REQ_BREAK_END = 1001
    }
}
