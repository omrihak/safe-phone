package com.safephone.data

import androidx.room.Entity
import androidx.room.Index
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
    val enforceGrayscale: Boolean = false,
    val softEnforcement: Boolean = false,
)

@Entity(
    tableName = "schedule_windows",
    indices = [Index("profileId")],
)
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** java.time.DayOfWeek value 1-7 (Monday=1 .. Sunday=7) */
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
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
    val minGapBetweenBreaksMinutes: Int = 30,
)

@Entity(tableName = "calendar_keywords")
data class CalendarKeywordEntity(
    @PrimaryKey val keyword: String,
)
