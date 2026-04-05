package com.safephone.test

import com.safephone.service.UsageStatsReader
import java.time.ZoneId

class FakeUsageStatsReader(
    var foregroundPackage: String? = null,
    var usageSinceMidnight: Map<String, Long> = emptyMap(),
    var opensSinceMidnight: Map<String, Int> = emptyMap(),
    /** Live foreground ms today per package (for budget while in-app). */
    var foregroundTodayByPackage: Map<String, Long> = emptyMap(),
) : UsageStatsReader {
    override fun foregroundPackageBestEffort(): String? = foregroundPackage

    override fun usageMsSinceLocalMidnight(zone: ZoneId): Map<String, Long> = usageSinceMidnight

    override fun opensSinceLocalMidnight(zone: ZoneId): Map<String, Int> = opensSinceMidnight

    override fun foregroundMsTodayForPackage(zone: ZoneId, packageName: String): Long =
        foregroundTodayByPackage[packageName] ?: 0L
}
