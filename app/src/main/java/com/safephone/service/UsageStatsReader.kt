package com.safephone.service

import java.time.ZoneId

/** Abstraction for [UsageStatsRepository] to enable fakes in tests. */
interface UsageStatsReader {
    fun foregroundPackageBestEffort(): String?
    fun usageMsSinceLocalMidnight(zone: ZoneId): Map<String, Long>
    /** Foreground transitions per package since local midnight (for daily open caps). */
    fun opensSinceLocalMidnight(zone: ZoneId): Map<String, Int>

    /**
     * Milliseconds this [packageName] spent in the foreground today (local midnight), including
     * the current open session. Derived from usage events so budgets apply while still in-app;
     * [usageMsSinceLocalMidnight] often lags until the app backgrounds.
     */
    fun foregroundMsTodayForPackage(zone: ZoneId, packageName: String): Long
}
