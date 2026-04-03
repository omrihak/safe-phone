package com.safephone.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.safephone.BuildConfig
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class UsageStatsRepository(context: Context) : UsageStatsReader {
    private val appContext = context.applicationContext
    private val usage = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    override fun foregroundPackageBestEffort(): String? {
        val end = System.currentTimeMillis()
        val begin = end - TimeUnit.MINUTES.toMillis(2)
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end) ?: return null
        if (stats.isEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    override fun usageMsSinceLocalMidnight(zone: ZoneId): Map<String, Long> {
        val now = ZonedDateTime.now(zone)
        val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, end) ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        for (s in stats) {
            if (s.packageName == BuildConfig.APPLICATION_ID) continue
            val total = s.totalTimeInForeground
            if (total > 0) map[s.packageName] = total
        }
        return map
    }

    @Suppress("DEPRECATION")
    override fun opensSinceLocalMidnight(zone: ZoneId): Map<String, Int> {
        val now = ZonedDateTime.now(zone)
        val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val events = usage.queryEvents(startOfDay, end) ?: return emptyMap()
        val event = UsageEvents.Event()
        val counts = mutableMapOf<String, Int>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == BuildConfig.APPLICATION_ID) continue
            val type = event.eventType
            val isForegroundTransition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type == UsageEvents.Event.MOVE_TO_FOREGROUND
            } else {
                type == UsageEvents.Event.ACTIVITY_RESUMED
            }
            if (!isForegroundTransition) continue
            val pkg = event.packageName ?: continue
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        return counts
    }
}
