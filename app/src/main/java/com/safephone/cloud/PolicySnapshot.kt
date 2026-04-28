package com.safephone.cloud

/**
 * Versioned, JSON-serializable representation of the user's full policy.
 *
 * Synced fields cover everything that defines *what* SafePhone enforces. Per-device runtime state
 * (onboarding flags, current break timer, daltonizer snapshot, block stats counters) is intentionally
 * excluded so devices don't fight over each other's transient state.
 *
 * Bump [CURRENT_SCHEMA_VERSION] (and migrate or refuse to apply on read) for any non-additive change.
 */
data class PolicySnapshot(
    val schemaVersion: Int,
    val deviceId: String,
    val updatedAtMs: Long,
    val profiles: List<ProfileDto>,
    val blockedApps: List<String>,
    val domainRules: List<DomainRuleDto>,
    val appBudgets: List<AppBudgetDto>,
    val breakPolicy: BreakPolicyDto,
    val calendarKeywords: List<String>,
    val prefs: PrefsDto,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

data class ProfileDto(
    val id: Long,
    val name: String,
    val preset: String,
    val useTierA: Boolean,
    val useTierB: Boolean,
    val useTierC: Boolean,
    val strictBrowserLock: Boolean,
    val enforceGrayscale: Boolean,
    val softEnforcement: Boolean,
)

data class DomainRuleDto(
    val id: Long,
    val pattern: String,
    val isAllowlist: Boolean,
)

data class AppBudgetDto(
    val packageName: String,
    val maxMinutesPerDay: Int,
    val maxOpensPerDay: Int,
)

data class BreakPolicyDto(
    val maxBreaksPerDay: Int,
    val breakDurationMinutes: Int,
    val minGapBetweenBreaksMinutes: Int,
)

data class PrefsDto(
    val enforcementEnabled: Boolean,
    val activeProfileId: Long?,
    val aggressivePoll: Boolean,
    val notificationHints: Boolean,
    val calendarAware: Boolean,
    val mindfulFrictionPackages: List<String>,
    val systemMonochromeAutomationEnabled: Boolean,
    val socialMediaCategoryBlocked: Boolean,
    val partnerBlockAlertEnabled: Boolean,
    val partnerBlockAlertThreshold: Int,
    val partnerAlertPhoneDigits: String,
    val activeDaysOfWeek: List<Int>,
    val scheduleStartHour: Int? = null,
    val scheduleEndHour: Int? = null,
)
