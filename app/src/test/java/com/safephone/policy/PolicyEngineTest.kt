package com.safephone.policy

import com.safephone.data.FocusProfileEntity
import com.safephone.data.ScheduleWindowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class PolicyEngineTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val selfPkg = "com.safephone.focus"

    private fun scheduleAllDayForInstant(now: Instant, profileId: Long = 1L): List<ScheduleWindowEntity> {
        val dow = now.atZone(zone).dayOfWeek.value
        return listOf(
            ScheduleWindowEntity(0, profileId, dayOfWeek = dow, startMinuteOfDay = 0, endMinuteOfDay = 24 * 60),
        )
    }

    private fun baseInput(
        now: Instant,
        profile: FocusProfileEntity? = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS"),
        schedules: List<ScheduleWindowEntity>? = null,
        fg: String? = "com.other.app",
        webHost: String? = null,
        blocked: Set<String> = emptySet(),
        blockDomains: List<String> = emptyList(),
        breakEnd: Long? = null,
        usage: Map<String, Long> = emptyMap(),
        opens: Map<String, Int> = emptyMap(),
        budgets: Map<String, Int> = emptyMap(),
        budgetOpens: Map<String, Int> = emptyMap(),
        strictBrowser: Boolean = false,
        calendarStricter: Boolean = false,
        forcedUntil: Long? = null,
    ): PolicyInput {
        val sched = schedules ?: scheduleAllDayForInstant(now, profile?.id ?: 1L)
        return PolicyInput(
            now = now,
            zone = zone,
            selfPackageName = selfPkg,
            activeProfile = profile,
            schedules = sched,
            blockedPackages = blocked,
            domainBlockPatterns = blockDomains,
            breakEndMs = breakEnd,
            maxBreaksPerDay = 5,
            breaksUsedToday = 0,
            breakDayEpochDay = 0,
            usageMsTodayByPackage = usage,
            usageOpensTodayByPackage = opens,
            budgetMaxMinutesByPackage = budgets,
            budgetMaxOpensPerDayByPackage = budgetOpens,
            currentForegroundPackage = fg,
            currentWebHost = webHost,
            strictBrowserLock = strictBrowser,
            calendarStricterActive = calendarStricter,
            forcedEnforceUntilMs = forcedUntil,
        )
    }

    @Test
    fun schedule_overnight_span_same_day_start() {
        val monday11pm = ZonedDateTime.of(2026, 4, 6, 23, 0, 0, 0, zone)
        val windows = listOf(
            ScheduleWindowEntity(0, 1, dayOfWeek = 1, startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60),
        )
        assertTrue(PolicyEngine.isWithinSchedule(monday11pm, windows))
    }

    @Test
    fun schedule_simple_window() {
        val monday10am = ZonedDateTime.of(2026, 4, 6, 10, 0, 0, 0, zone)
        val windows = listOf(
            ScheduleWindowEntity(0, 1, dayOfWeek = 1, startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60),
        )
        assertTrue(PolicyEngine.isWithinSchedule(monday10am, windows))
        val monday8pm = ZonedDateTime.of(2026, 4, 6, 20, 0, 0, 0, zone)
        assertFalse(PolicyEngine.isWithinSchedule(monday8pm, windows))
    }

    @Test
    fun schedule_empty_always_false() {
        val monday10am = ZonedDateTime.of(2026, 4, 6, 10, 0, 0, 0, zone)
        assertFalse(PolicyEngine.isWithinSchedule(monday10am, emptyList()))
    }

    @Test
    fun domain_blocked_suffix_okta() {
        assertTrue(PolicyEngine.domainBlocked("login.okta.com", listOf(".okta.com")))
        assertTrue(PolicyEngine.domainBlocked("foo.oktapreview.com", listOf(".oktapreview.com")))
    }

    @Test
    fun domain_blocked_twitter() {
        assertTrue(PolicyEngine.domainBlocked("mobile.twitter.com", listOf("twitter.com")))
    }

    @Test
    fun domain_blocked_wildcard_subdomain() {
        assertTrue(PolicyEngine.domainBlocked("a.example.com", listOf("*.example.com")))
        assertFalse(PolicyEngine.domainBlocked("notexample.com", listOf("*.example.com")))
    }

    @Test
    fun normalizeHost_strips_www() {
        assertEquals("example.com", PolicyEngine.normalizeHost("WWW.EXAMPLE.COM"))
    }

    @Test
    fun isBrowserPackage_positive_and_negative() {
        assertTrue(PolicyEngine.isBrowserPackage("com.android.chrome"))
        assertTrue(PolicyEngine.isBrowserPackage("org.mozilla.firefox"))
        assertFalse(PolicyEngine.isBrowserPackage("com.android.settings"))
    }

    @Test
    fun blocked_app_enforced() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
    }

    @Test
    fun on_break_no_block() {
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val future = System.currentTimeMillis() + 60_000
        val input = baseInput(
            now = Instant.ofEpochMilli(System.currentTimeMillis()),
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
            breakEnd = future,
        )
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.blockApp)
    }

    @Test
    fun no_active_profile_idle() {
        val input = baseInput(now = Instant.now(), profile = null)
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.enforcing)
        assertFalse(d.blockApp)
        assertTrue(d.reason.contains("profile", ignoreCase = true))
    }

    @Test
    fun soft_enforcement_no_hard_block() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            softEnforcement = true,
        )
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
        )
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.enforcing)
        assertFalse(d.blockApp)
    }

    @Test
    fun forced_enforce_outside_schedule_window() {
        val monday10am = ZonedDateTime.of(2026, 4, 6, 10, 0, 0, 0, zone).toInstant()
        val windows = listOf(
            ScheduleWindowEntity(0, 1, dayOfWeek = 2, startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60),
        )
        val forcedUntil = monday10am.toEpochMilli() + 60_000
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = monday10am,
            profile = profile,
            schedules = windows,
            fg = "com.blocked.in.schedule",
            blocked = setOf("com.blocked.in.schedule"),
            forcedUntil = forcedUntil,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.withinSchedule)
        assertTrue(d.blockApp)
    }

    @Test
    fun daily_budget_exceeded_blocks() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val pkg = "com.budget.app"
        val input = baseInput(
            now = now,
            profile = profile,
            fg = pkg,
            budgets = mapOf(pkg to 30),
            usage = mapOf(pkg to 30 * 60_000L),
        )
        assertTrue(PolicyEngine.evaluate(input).blockApp)
    }

    @Test
    fun daily_open_limit_exceeded_blocks() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val pkg = "com.opens.app"
        val input = baseInput(
            now = now,
            profile = profile,
            fg = pkg,
            opens = mapOf(pkg to 4),
            budgetOpens = mapOf(pkg to 3),
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
        assertTrue(d.reason.contains("open", ignoreCase = true))
    }

    @Test
    fun daily_open_limit_at_cap_allows() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val pkg = "com.opens.app"
        val input = baseInput(
            now = now,
            profile = profile,
            fg = pkg,
            opens = mapOf(pkg to 3),
            budgetOpens = mapOf(pkg to 3),
        )
        assertFalse(PolicyEngine.evaluate(input).blockApp)
    }

    @Test
    fun strict_browser_lock_blocks_when_no_host() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            strictBrowserLock = true,
        )
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.android.chrome",
            webHost = null,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockWebOverlay)
    }

    @Test
    fun self_foreground_never_blocks_or_grayscale() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            enforceGrayscale = true,
        )
        val input = baseInput(
            now = now,
            profile = profile,
            fg = selfPkg,
            blocked = setOf(selfPkg),
        )
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.blockApp)
        assertFalse(d.applyGrayscale)
        assertTrue(d.reason.contains("SafePhone", ignoreCase = true))
    }

    @Test
    fun calendar_stricter_enforces_when_off_schedule() {
        val monday10am = ZonedDateTime.of(2026, 4, 6, 10, 0, 0, 0, zone).toInstant()
        val windows = listOf(
            ScheduleWindowEntity(0, 1, dayOfWeek = 2, startMinuteOfDay = 0, endMinuteOfDay = 24 * 60),
        )
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = monday10am,
            profile = profile,
            schedules = windows,
            fg = "com.blocked",
            blocked = setOf("com.blocked"),
            calendarStricter = true,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
    }

    @Test
    fun grayscale_when_profile_enforces_and_hard_enforce() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            enforceGrayscale = true,
        )
        val input = baseInput(now, profile, fg = "com.android.chrome", webHost = "allowed.com")
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.applyGrayscale)
    }

    @Test
    fun minutesUsedToday_lookup() {
        val map = mapOf("a" to 123L)
        assertEquals(123L, PolicyEngine.minutesUsedToday(map, "a", zone))
        assertEquals(0L, PolicyEngine.minutesUsedToday(map, "missing", zone))
    }
}
