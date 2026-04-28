package com.safephone.policy

import com.safephone.data.FocusProfileEntity
import java.time.Instant
import java.time.ZoneId

data class PolicyInput(
    val now: Instant,
    val zone: ZoneId,
    val selfPackageName: String,
    val activeProfile: FocusProfileEntity?,
    val blockedPackages: Set<String>,
    val domainBlockPatterns: List<String>,
    val breakEndMs: Long?,
    val maxBreaksPerDay: Int,
    val breaksUsedToday: Int,
    val breakDayEpochDay: Long,
    val usageMsTodayByPackage: Map<String, Long>,
    val usageOpensTodayByPackage: Map<String, Int>,
    val budgetMaxMinutesByPackage: Map<String, Int>,
    val budgetMaxOpensPerDayByPackage: Map<String, Int>,
    val currentForegroundPackage: String?,
    val currentWebHost: String?,
    val strictBrowserLock: Boolean,
    val calendarStricterActive: Boolean,
    /** Days of the week on which focus rules are enforced (1=Mon … 7=Sun). Defaults to all days. */
    val activeDaysOfWeek: Set<Int> = (1..7).toSet(),
    /** Hour of day (0–23) at which schedule enforcement starts. Defaults to 0. */
    val scheduleStartHour: Int = 0,
    /** Hour of day (1–24) at which schedule enforcement ends (exclusive). Defaults to 24. */
    val scheduleEndHour: Int = 24,
)

data class PolicyDecision(
    val withinSchedule: Boolean,
    val onBreak: Boolean,
    val enforcing: Boolean,
    val blockApp: Boolean,
    val blockWebOverlay: Boolean,
    val applyGrayscale: Boolean,
    val reason: String,
)

object PolicyEngine {

    fun evaluate(input: PolicyInput): PolicyDecision {
        if (input.activeProfile == null) {
            return idle("No active profile")
        }

        val todayDow = input.now.atZone(input.zone).dayOfWeek.value
        if (input.activeDaysOfWeek.isEmpty() || todayDow !in input.activeDaysOfWeek) {
            return idle("Off day – no rules scheduled")
        }

        val currentHour = input.now.atZone(input.zone).hour
        if (currentHour < input.scheduleStartHour || currentHour >= input.scheduleEndHour) {
            return idle("Outside schedule hours")
        }

        val withinSchedule = true
        val onBreak = input.breakEndMs != null && input.now.toEpochMilli() < input.breakEndMs
        val enforcing = withinSchedule && !onBreak && !input.activeProfile.softEnforcement
        val calendarEnforce = input.calendarStricterActive && !onBreak
        val hardEnforce = enforcing || calendarEnforce

        if (input.selfPackageName == input.currentForegroundPackage) {
            return PolicyDecision(
                withinSchedule = withinSchedule,
                onBreak = onBreak,
                enforcing = enforcing,
                blockApp = false,
                blockWebOverlay = false,
                applyGrayscale = hardEnforce,
                reason = "SafePhone foreground",
            )
        }

        if (!enforcing && !input.calendarStricterActive) {
            return PolicyDecision(
                withinSchedule = withinSchedule,
                onBreak = onBreak,
                enforcing = false,
                blockApp = false,
                blockWebOverlay = false,
                applyGrayscale = false,
                reason = "On break / soft mode",
            )
        }

        if (!hardEnforce) {
            return PolicyDecision(
                withinSchedule = withinSchedule,
                onBreak = onBreak,
                enforcing = false,
                blockApp = false,
                blockWebOverlay = false,
                applyGrayscale = false,
                reason = "Not enforcing",
            )
        }

        val pkg = input.currentForegroundPackage

        var blockApp = false
        var reason = "OK"

        if (pkg != null) {
            if (input.blockedPackages.contains(pkg)) {
                blockApp = true
                reason = "Blocked list"
            } else {
                val budgetMin = input.budgetMaxMinutesByPackage[pkg]
                if (budgetMin != null && budgetMin > 0) {
                    val usedMs = input.usageMsTodayByPackage[pkg] ?: 0L
                    if (usedMs >= budgetMin * 60_000L) {
                        blockApp = true
                        reason = "Daily budget exceeded"
                    }
                }
                if (!blockApp) {
                    val budgetOpens = input.budgetMaxOpensPerDayByPackage[pkg]
                    if (budgetOpens != null && budgetOpens > 0) {
                        val opens = input.usageOpensTodayByPackage[pkg] ?: 0
                        if (opens > budgetOpens) {
                            blockApp = true
                            reason = "Daily open limit exceeded"
                        }
                    }
                }
            }
        }

        var blockWeb = false
        val host = input.currentWebHost?.lowercase()
        val browser = pkg?.let { isBrowserPackage(it) } == true
        if (browser && hardEnforce) {
            when {
                host.isNullOrBlank() && input.activeProfile.strictBrowserLock -> {
                    blockWeb = true
                    reason = "Strict browser lock (no URL)"
                }
                !host.isNullOrBlank() -> {
                    if (domainBlocked(host, input.domainBlockPatterns)) {
                        blockWeb = true
                        reason = "Blocked domain"
                    }
                }
            }
        }

        // Calm screen whenever rules are actively enforced (not on break); independent of profile flag.
        val grayscale = hardEnforce

        return PolicyDecision(
            withinSchedule = withinSchedule,
            onBreak = onBreak,
            enforcing = hardEnforce,
            blockApp = blockApp,
            blockWebOverlay = blockWeb,
            applyGrayscale = grayscale,
            reason = reason,
        )
    }

    private fun idle(reason: String) = PolicyDecision(
        withinSchedule = false,
        onBreak = false,
        enforcing = false,
        blockApp = false,
        blockWebOverlay = false,
        applyGrayscale = false,
        reason = reason,
    )

    fun normalizeHost(host: String): String {
        var h = host.lowercase().trim()
        if (h.startsWith("www.")) h = h.removePrefix("www.")
        return h
    }

    fun domainBlocked(host: String, patterns: List<String>): Boolean {
        val h = normalizeHost(host)
        return patterns.any { p -> matchesPattern(h, p) }
    }

    private fun matchesPattern(host: String, pattern: String): Boolean {
        val p = pattern.lowercase().trim()
        return when {
            p.startsWith("*.") -> host.endsWith(p.removePrefix("*"))
            p.startsWith(".") -> host.endsWith(p.removePrefix(".")) || host == p.removePrefix(".").removePrefix(".")
            else -> host == p || host.endsWith(".$p")
        }
    }

    fun isBrowserPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("chrome") ||
            p.contains("firefox") ||
            p.contains("edge") ||
            p.contains("samsung.android.sbrowser") ||
            p.contains("opera") ||
            p.contains("brave") ||
            p.contains("vivaldi") ||
            p == "com.android.browser"
    }

    fun minutesUsedToday(
        usageMsByPackage: Map<String, Long>,
        packageName: String,
        zone: ZoneId,
    ): Long {
        return usageMsByPackage[packageName] ?: 0L
    }
}
