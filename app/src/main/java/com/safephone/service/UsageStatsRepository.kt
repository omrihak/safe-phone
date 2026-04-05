package com.safephone.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.safephone.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class UsageStatsRepository(context: Context) : UsageStatsReader {
    private val appContext = context.applicationContext
    private val usage = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val fgTodayLock = Any()
    private var fgCacheDay: Long = -1L
    private var fgCachePkg: String? = null
    private var fgCacheAt: Long = 0L
    private var fgCacheTotal: Long = 0L
    private var fgCacheOpenAtCache: Boolean = false

    override fun foregroundPackageBestEffort(): String? {
        val end = System.currentTimeMillis()
        // INTERVAL_BEST often returns no buckets for a 2-minute range, so foreground was always null
        // and blocking never ran. Use a wide window, then fall back to recent usage events.
        val statsBegin = end - TimeUnit.HOURS.toMillis(6)
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, statsBegin, end)
        if (!stats.isNullOrEmpty()) {
            val pkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName
            if (pkg != null) return pkg
        }
        return lastForegroundPackageFromUsageEvents(end - TimeUnit.MINUTES.toMillis(15), end)
    }

    @Suppress("DEPRECATION")
    private fun lastForegroundPackageFromUsageEvents(begin: Long, end: Long): String? {
        val events = usage.queryEvents(begin, end) ?: return null
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            val isForegroundTransition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type == UsageEvents.Event.MOVE_TO_FOREGROUND
            } else {
                type == UsageEvents.Event.ACTIVITY_RESUMED
            }
            if (!isForegroundTransition) continue
            val ts = event.timeStamp
            if (ts >= lastTs) {
                lastTs = ts
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    override fun usageMsSinceLocalMidnight(zone: ZoneId): Map<String, Long> {
        val now = ZonedDateTime.now(zone)
        val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        // INTERVAL_DAILY snaps to platform-defined day buckets and often disagrees with local midnight.
        // INTERVAL_BEST returns smaller buckets; we sum per package and prorate partial overlap with
        // [startOfDay, end] so totals match the same window as opensSinceLocalMidnight (events).
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startOfDay, end) ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        for (s in stats) {
            if (s.packageName == BuildConfig.APPLICATION_ID) continue
            val fg = s.totalTimeInForeground
            if (fg <= 0L) continue
            val first = s.firstTimeStamp
            val last = s.lastTimeStamp
            val overlapStart = maxOf(first, startOfDay)
            val overlapEnd = minOf(last, end)
            if (overlapEnd <= overlapStart) continue
            val bucketSpan = (last - first).coerceAtLeast(1L)
            val overlapSpan = overlapEnd - overlapStart
            val attributed = if (overlapSpan >= bucketSpan) {
                fg
            } else {
                (fg.toDouble() * overlapSpan.toDouble() / bucketSpan.toDouble()).toLong()
            }
            if (attributed <= 0L) continue
            val pkg = s.packageName
            map[pkg] = (map[pkg] ?: 0L) + attributed
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

    override fun foregroundMsTodayForPackage(zone: ZoneId, packageName: String): Long {
        if (packageName == BuildConfig.APPLICATION_ID) return 0L
        val now = System.currentTimeMillis()
        val day = ZonedDateTime.now(zone).toLocalDate().toEpochDay()
        synchronized(fgTodayLock) {
            val throttleMs = 500L
            if (packageName == fgCachePkg && day == fgCacheDay && now - fgCacheAt < throttleMs) {
                return fgCacheTotal + if (fgCacheOpenAtCache) (now - fgCacheAt) else 0L
            }
            val (total, hadOpen) = computeForegroundMsTodayFromUsageEvents(zone, packageName, now)
            fgCacheDay = day
            fgCachePkg = packageName
            fgCacheAt = now
            fgCacheTotal = total
            fgCacheOpenAtCache = hadOpen
            return total
        }
    }

    /**
     * Sums foreground intervals for [packageName] overlapping [local midnight, endMs], using events
     * from slightly before midnight so a session that started before midnight still counts after it.
     */
    @Suppress("DEPRECATION")
    private fun computeForegroundMsTodayFromUsageEvents(
        zone: ZoneId,
        packageName: String,
        endMs: Long,
    ): Pair<Long, Boolean> {
        val startOfDay = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMs), zone)
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val queryBegin = (startOfDay - TimeUnit.HOURS.toMillis(6)).coerceAtLeast(0L)
        val events = usage.queryEvents(queryBegin, endMs) ?: return 0L to false
        val event = UsageEvents.Event()
        var sessionStart: Long? = null
        var total = 0L

        fun addClip(sessionStartTs: Long, endClip: Long) {
            val clipStart = maxOf(sessionStartTs, startOfDay)
            val clipEnd = minOf(endClip, endMs)
            if (clipEnd > clipStart) total += clipEnd - clipStart
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            val type = event.eventType
            when {
                isForegroundUsageEvent(type) -> {
                    if (sessionStart == null) sessionStart = event.timeStamp
                }
                isBackgroundUsageEvent(type) -> {
                    val s = sessionStart ?: continue
                    addClip(s, event.timeStamp)
                    sessionStart = null
                }
            }
        }
        val hadOpen = sessionStart != null
        sessionStart?.let { addClip(it, endMs) }
        return total to hadOpen
    }

    private fun isForegroundUsageEvent(type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.MOVE_TO_FOREGROUND
        } else {
            @Suppress("DEPRECATION")
            type == UsageEvents.Event.ACTIVITY_RESUMED
        }

    private fun isBackgroundUsageEvent(type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.MOVE_TO_BACKGROUND
        } else {
            @Suppress("DEPRECATION")
            type == UsageEvents.Event.ACTIVITY_PAUSED
        }
}
