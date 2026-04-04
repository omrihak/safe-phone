package com.safephone.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.AppDatabase
import com.safephone.data.BlockedAppEntity
import com.safephone.data.DomainRuleEntity
import com.safephone.data.FocusPreferences
import com.safephone.data.FocusProfileEntity
import com.safephone.policy.PolicyEngine
import com.safephone.service.PolicyAssembler
import com.safephone.test.FakeCalendarKeywordChecker
import com.safephone.test.FakeUsageStatsReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
/**
 * End-to-end policy pipeline: real Room + DataStore prefs + [PolicyAssembler] + [PolicyEngine].
 * Simulates foreground app / browser host via fakes (no UsageStats or real Chrome required).
 */
@RunWith(AndroidJUnit4::class)
class BlockingAllowlistPolicyE2EInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: FocusPreferences
    private val usage = FakeUsageStatsReader()
    private val calendar = FakeCalendarKeywordChecker()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
        prefs = FocusPreferences(context)
        calendar.focusKeywordsActive = false
        usage.usageSinceMidnight = emptyMap()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun assembler() = PolicyAssembler(db, prefs, usage, calendar)

    private suspend fun seedEnforcingProfile(
        preset: String = "WORK_HOURS",
        strictBrowserLock: Boolean = false,
    ): Long {
        val id = db.focusProfileDao().insert(
            FocusProfileEntity(
                name = "E2E",
                preset = preset,
                strictBrowserLock = strictBrowserLock,
            ),
        )
        prefs.setActiveProfileId(id)
        return id
    }

    private suspend fun evaluateFg(
        pkg: String?,
        host: String? = null,
    ) = PolicyEngine.evaluate(assembler().build(pkg, host))

    @Test
    fun e2e_blocked_app_is_blocked_when_enforcing() = runBlocking {
        seedEnforcingProfile()
        db.blockedAppDao().upsert(BlockedAppEntity("com.distractor.game"))
        usage.foregroundPackage = "com.distractor.game"
        val d = evaluateFg("com.distractor.game")
        assertTrue(d.enforcing)
        assertTrue(d.blockApp)
        assertTrue(d.reason.contains("Blocked", ignoreCase = true))
    }

    @Test
    fun e2e_unlisted_app_not_blocked_when_enforcing() = runBlocking {
        seedEnforcingProfile("WORK_HOURS")
        usage.foregroundPackage = "com.random.unlisted"
        val d = evaluateFg("com.random.unlisted")
        assertTrue(d.enforcing)
        assertFalse(d.blockApp)
    }

    @Test
    fun e2e_blocked_website_in_browser_shows_web_overlay() = runBlocking {
        seedEnforcingProfile()
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = "distraction.com", isAllowlist = false))
        usage.foregroundPackage = "com.android.chrome"
        val d = evaluateFg("com.android.chrome", "www.distraction.com")
        assertTrue(d.blockWebOverlay)
        assertTrue(d.reason.contains("domain", ignoreCase = true))
    }

    @Test
    fun e2e_unlisted_host_not_blocked_when_other_domains_blocked() = runBlocking {
        seedEnforcingProfile()
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = "evil.com", isAllowlist = false))
        usage.foregroundPackage = "com.android.chrome"
        val d = evaluateFg("com.android.chrome", "login.okta.com")
        assertFalse(d.blockWebOverlay)
    }

    @Test
    fun e2e_block_only_matches_listed_patterns() = runBlocking {
        seedEnforcingProfile()
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = "blocked.corp", isAllowlist = false))
        usage.foregroundPackage = "com.android.chrome"
        val internalHost = evaluateFg("com.android.chrome", "app.internal.corp")
        assertFalse(internalHost.blockWebOverlay)
        val blockedHost = evaluateFg("com.android.chrome", "www.blocked.corp")
        assertTrue(blockedHost.blockWebOverlay)
    }

    @Test
    fun e2e_non_browser_app_ignores_domain_rules_for_web_overlay() = runBlocking {
        seedEnforcingProfile()
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = "any.com", isAllowlist = false))
        usage.foregroundPackage = "com.android.settings"
        val d = evaluateFg("com.android.settings", "any.com")
        assertFalse(d.blockWebOverlay)
    }

    @Test
    fun e2e_soft_profile_does_not_block_listed_app() = runBlocking {
        val id = db.focusProfileDao().insert(
            FocusProfileEntity(name = "Soft", preset = "WORK_HOURS", softEnforcement = true),
        )
        prefs.setActiveProfileId(id)
        db.blockedAppDao().upsert(BlockedAppEntity("com.bad.app"))
        usage.foregroundPackage = "com.bad.app"
        val d = evaluateFg("com.bad.app")
        assertFalse(d.enforcing)
        assertFalse(d.blockApp)
    }

    @Test
    fun e2e_blocked_app_still_blocked_when_enforcing() = runBlocking {
        seedEnforcingProfile("WORK_HOURS")
        db.blockedAppDao().upsert(BlockedAppEntity("com.dual"))
        usage.foregroundPackage = "com.dual"
        val d = evaluateFg("com.dual")
        assertTrue(d.blockApp)
        assertTrue(d.reason.contains("Blocked", ignoreCase = true))
    }
}
