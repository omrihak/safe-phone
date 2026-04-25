package com.safephone.policy

import com.safephone.data.FocusProfileEntity
import com.safephone.data.SocialMediaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PolicyEngineTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val selfPkg = "com.safephone.focus"

    private fun baseInput(
        now: Instant,
        profile: FocusProfileEntity? = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS"),
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
    ): PolicyInput =
        PolicyInput(
            now = now,
            zone = zone,
            selfPackageName = selfPkg,
            activeProfile = profile,
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
        )

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
        assertTrue(d.withinSchedule)
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
    fun on_break_no_grayscale() {
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val future = System.currentTimeMillis() + 60_000
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.android.chrome",
            webHost = "example.com",
            breakEnd = future,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.onBreak)
        assertFalse(d.enforcing)
        assertFalse(d.applyGrayscale)
    }

    @Test
    fun grayscale_whole_focus_session_when_hard_enforcing_not_only_when_blocked() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.allowed.app",
            blocked = emptySet(),
            breakEnd = null,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.applyGrayscale)
        assertTrue(d.enforcing)
    }

    @Test
    fun soft_enforcement_no_grayscale() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            softEnforcement = true,
        )
        val input = baseInput(now = now, profile = profile, fg = "com.any.app")
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.applyGrayscale)
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
    fun self_foreground_never_blocks_but_keeps_grayscale_when_enforcing() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = selfPkg,
            blocked = setOf(selfPkg),
        )
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.blockApp)
        assertTrue(d.applyGrayscale)
        assertTrue(d.reason.contains("SafePhone", ignoreCase = true))
    }

    @Test
    fun self_foreground_no_grayscale_during_soft_enforcement() {
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
            fg = selfPkg,
        )
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.blockApp)
        assertFalse(d.applyGrayscale)
        assertTrue(d.reason.contains("SafePhone", ignoreCase = true))
    }

    @Test
    fun calendar_stricter_still_enforces_blocked_app() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.blocked",
            blocked = setOf("com.blocked"),
            calendarStricter = true,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
    }

    @Test
    fun grayscale_when_hard_enforcing_even_if_profile_flag_off() {
        val now = Instant.now()
        val profile = FocusProfileEntity(
            id = 1,
            name = "P",
            preset = "WORK_HOURS",
            enforceGrayscale = false,
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

    // --- Social Media Category ---

    @Test
    fun social_media_facebook_domain_blocked() {
        assertTrue(PolicyEngine.domainBlocked("www.facebook.com", SocialMediaCategory.domains))
        assertTrue(PolicyEngine.domainBlocked("m.facebook.com", SocialMediaCategory.domains))
        assertTrue(PolicyEngine.domainBlocked("facebook.com", SocialMediaCategory.domains))
    }

    @Test
    fun social_media_instagram_domain_blocked() {
        assertTrue(PolicyEngine.domainBlocked("instagram.com", SocialMediaCategory.domains))
        assertTrue(PolicyEngine.domainBlocked("www.instagram.com", SocialMediaCategory.domains))
    }

    @Test
    fun social_media_tiktok_domain_blocked() {
        assertTrue(PolicyEngine.domainBlocked("tiktok.com", SocialMediaCategory.domains))
        assertTrue(PolicyEngine.domainBlocked("www.tiktok.com", SocialMediaCategory.domains))
    }

    @Test
    fun social_media_category_domains_block_web_overlay() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.android.chrome",
            webHost = "www.instagram.com",
            blockDomains = SocialMediaCategory.domains,
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockWebOverlay)
        assertTrue(d.reason.contains("domain", ignoreCase = true))
    }

    @Test
    fun social_media_category_packages_block_app() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.facebook.katana",
            blocked = SocialMediaCategory.packages.toSet(),
        )
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
    }

    @Test
    fun social_media_non_social_domain_not_blocked_by_category() {
        assertFalse(PolicyEngine.domainBlocked("example.com", SocialMediaCategory.domains))
        assertFalse(PolicyEngine.domainBlocked("google.com", SocialMediaCategory.domains))
    }

    // --- Weekday schedule ---

    @Test
    fun off_day_returns_idle() {
        val now = Instant.now()
        val todayDow = now.atZone(zone).dayOfWeek.value
        // Build a set without today
        val activeDays = (1..7).filter { it != todayDow }.toSet()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
        ).copy(activeDaysOfWeek = activeDays)
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.enforcing)
        assertFalse(d.blockApp)
        assertTrue(d.reason.contains("Off day", ignoreCase = true))
    }

    @Test
    fun on_day_enforces_rules() {
        val now = Instant.now()
        val todayDow = now.atZone(zone).dayOfWeek.value
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
        ).copy(activeDaysOfWeek = setOf(todayDow))
        val d = PolicyEngine.evaluate(input)
        assertTrue(d.blockApp)
    }

    @Test
    fun empty_active_days_returns_idle() {
        val now = Instant.now()
        val profile = FocusProfileEntity(id = 1, name = "P", preset = "WORK_HOURS")
        val input = baseInput(
            now = now,
            profile = profile,
            fg = "com.bad.game",
            blocked = setOf("com.bad.game"),
        ).copy(activeDaysOfWeek = emptySet())
        val d = PolicyEngine.evaluate(input)
        assertFalse(d.enforcing)
        assertFalse(d.blockApp)
    }
}
