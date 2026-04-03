package com.safephone.service

import com.safephone.BuildConfig
import com.safephone.data.AppDatabase
import com.safephone.data.FocusPreferences
import com.safephone.policy.PolicyInput
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class PolicyAssembler(
    private val db: AppDatabase,
    private val prefs: FocusPreferences,
    private val usageStats: UsageStatsReader,
    private val calendar: CalendarKeywordChecker,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    suspend fun build(
        foregroundPackage: String?,
        webHost: String?,
    ): PolicyInput {
        val profileId = prefs.activeProfileId.first()
        val profile = profileId?.let { db.focusProfileDao().getById(it) }
            ?: db.focusProfileDao().getAll().firstOrNull()
        val schedules = if (profile != null) {
            db.scheduleWindowDao().getForProfile(profile.id)
        } else {
            emptyList()
        }
        val blocked = db.blockedAppDao().getAll().map { it.packageName }.toSet()
        val domains = db.domainRuleDao().getAll()
        val block = domains.filter { !it.isAllowlist }.map { it.pattern }
        val policy = db.breakPolicyDao().get() ?: com.safephone.data.BreakPolicyEntity()
        val budgetRows = db.appBudgetDao().getAll()
        val budgets = budgetRows.associate { it.packageName to it.maxMinutesPerDay }
        val budgetOpens = budgetRows.associate { it.packageName to it.maxOpensPerDay }
        val usage = usageStats.usageMsSinceLocalMidnight(zone)
        val opens = usageStats.opensSinceLocalMidnight(zone)
        val breakEnd = prefs.breakEndEpochMs.first()
        val used = prefs.breaksUsedToday.first()
        val day = prefs.breakDayEpochDay.first()
        val calendarOn = prefs.calendarAware.first()
        val keywords = db.calendarKeywordDao().getAll()
        val calendarStricter = calendarOn && calendar.isFocusKeywordActiveNow(keywords)
        val forcedUntil = prefs.forcedEnforceUntilMs.first()

        return PolicyInput(
            now = Instant.now(),
            zone = zone,
            selfPackageName = BuildConfig.APPLICATION_ID,
            activeProfile = profile,
            schedules = schedules,
            blockedPackages = blocked,
            domainBlockPatterns = block,
            breakEndMs = breakEnd,
            maxBreaksPerDay = policy.maxBreaksPerDay,
            breaksUsedToday = used,
            breakDayEpochDay = day,
            usageMsTodayByPackage = usage,
            usageOpensTodayByPackage = opens,
            budgetMaxMinutesByPackage = budgets,
            budgetMaxOpensPerDayByPackage = budgetOpens,
            currentForegroundPackage = foregroundPackage,
            currentWebHost = webHost,
            strictBrowserLock = profile?.strictBrowserLock == true,
            calendarStricterActive = calendarStricter,
            forcedEnforceUntilMs = forcedUntil,
        )
    }
}
