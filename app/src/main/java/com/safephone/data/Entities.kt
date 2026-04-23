package com.safephone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class FocusProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** DEEP_WORK, WORK_HOURS, ON_CALL, SLEEP, CUSTOM */
    val preset: String,
    val useTierA: Boolean = true,
    val useTierB: Boolean = false,
    val useTierC: Boolean = false,
    val strictBrowserLock: Boolean = false,
    /** Unused by [com.safephone.policy.PolicyEngine]; grayscale follows hard enforcement. Kept for schema compatibility. */
    val enforceGrayscale: Boolean = false,
    val softEnforcement: Boolean = false,
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
)

@Entity(tableName = "domain_rules")
data class DomainRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Host or suffix like .okta.com */
    val pattern: String,
    val isAllowlist: Boolean,
)

@Entity(tableName = "app_budgets")
data class AppBudgetEntity(
    @PrimaryKey val packageName: String,
    /** Max minutes per calendar day while enforcing; 0 = no cap */
    val maxMinutesPerDay: Int,
    /** Max times the app may move to foreground per calendar day while enforcing; 0 = no cap */
    val maxOpensPerDay: Int = 0,
)

@Entity(tableName = "break_policy")
data class BreakPolicyEntity(
    @PrimaryKey val id: Int = 1,
    val maxBreaksPerDay: Int = 5,
    val breakDurationMinutes: Int = 10,
    /** Deprecated in logic; retained for schema compatibility with existing installs. */
    val minGapBetweenBreaksMinutes: Int = 30,
)

@Entity(tableName = "calendar_keywords")
data class CalendarKeywordEntity(
    @PrimaryKey val keyword: String,
)

/** Aggregated block-overlay sessions per calendar day (see FocusEnforcementService debouncing). */
@Entity(
    tableName = "block_stats",
    primaryKeys = ["dayEpochDay", "kind", "targetKey"],
)
data class BlockStatsEntity(
    val dayEpochDay: Long,
    /** `app`, `web`, or `browser_lock` */
    val kind: String,
    /** Package name, hostname, or browser package for strict lock */
    val targetKey: String,
    val count: Int,
)

/** Sum of [BlockStatsEntity.count] over a date range, grouped by kind and target (see [BlockStatsDao.observeAggregatedSince]). */
data class BlockStatsAggregateRow(
    val kind: String,
    val targetKey: String,
    val count: Int,
)
