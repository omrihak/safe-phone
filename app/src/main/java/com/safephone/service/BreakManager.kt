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
        val granted = breaksGrantedToday(policy)
        val used = effectiveBreaksUsedToday()
        return (granted - used).coerceAtLeast(0)
    }

    suspend fun breaksGrantedToday(policy: BreakPolicyEntity): Int {
        return grantedBreaksByNow(
            maxBreaksPerDay = policy.maxBreaksPerDay,
            nowEpochMs = System.currentTimeMillis(),
            zoneId = deviceZone,
        )
    }

    suspend fun isOnBreakNow(): Boolean {
        val end = prefs.breakEndEpochMs.first() ?: return false
        return System.currentTimeMillis() < end
    }

    suspend fun canStartBreak(policy: BreakPolicyEntity): Boolean {
        return breaksRemainingToday(policy) > 0 && !isOnBreakNow()
    }

    suspend fun minutesUntilNextGrant(policy: BreakPolicyEntity): Int? {
        val maxBreaks = policy.maxBreaksPerDay.coerceAtLeast(0)
        val used = effectiveBreaksUsedToday()
        if (used >= maxBreaks) return null
        if (breaksRemainingToday(policy) > 0) return 0
        val now = System.currentTimeMillis()
        val nextGrantEpochMs = nextGrantEpochMs(maxBreaks, now, deviceZone) ?: return null
        return ((nextGrantEpochMs - now + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
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

        internal fun grantedBreaksByNow(maxBreaksPerDay: Int, nowEpochMs: Long, zoneId: ZoneId): Int {
            val maxBreaks = maxBreaksPerDay.coerceAtLeast(0)
            if (maxBreaks == 0) return 0
            val localDate = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
            val dayStartEpochMs = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nextDayStartEpochMs = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayLengthMs = (nextDayStartEpochMs - dayStartEpochMs).coerceAtLeast(1L)
            val elapsedMs = (nowEpochMs - dayStartEpochMs).coerceIn(0L, dayLengthMs - 1L)
            val granted = ((elapsedMs * maxBreaks.toLong()) / dayLengthMs).toInt() + 1
            return granted.coerceAtMost(maxBreaks)
        }

        internal fun nextGrantEpochMs(maxBreaksPerDay: Int, nowEpochMs: Long, zoneId: ZoneId): Long? {
            val maxBreaks = maxBreaksPerDay.coerceAtLeast(0)
            if (maxBreaks <= 0) return null
            val localDate = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
            val dayStartEpochMs = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nextDayStartEpochMs = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayLengthMs = (nextDayStartEpochMs - dayStartEpochMs).coerceAtLeast(1L)
            val elapsedMs = (nowEpochMs - dayStartEpochMs).coerceAtLeast(0L)
            val nextGrantIndex = ((elapsedMs * maxBreaks.toLong()) / dayLengthMs).toInt() + 1
            if (nextGrantIndex >= maxBreaks) return null
            return dayStartEpochMs + ((nextGrantIndex.toLong() * dayLengthMs) / maxBreaks.toLong())
        }
    }
}
