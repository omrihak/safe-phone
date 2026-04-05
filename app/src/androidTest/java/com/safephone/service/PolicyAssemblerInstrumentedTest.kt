package com.safephone.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safephone.data.AppDatabase
import com.safephone.data.FocusPreferences
import com.safephone.data.FocusProfileEntity
import com.safephone.data.AppBudgetEntity
import com.safephone.data.BlockedAppEntity
import com.safephone.policy.PolicyEngine
import com.safephone.test.FakeCalendarKeywordChecker
import com.safephone.test.FakeUsageStatsReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PolicyAssemblerInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: FocusPreferences
    private val usage = FakeUsageStatsReader()
    private val calendar = FakeCalendarKeywordChecker()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = AppDatabase.createInMemory(ctx)
        prefs = FocusPreferences(ctx)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun build_withFakeUsage_blocksWhenBlockedAndEnforcing() = runBlocking {
        val profileId = db.focusProfileDao().insert(FocusProfileEntity(name = "P", preset = "DEEP_WORK"))
        prefs.setActiveProfileId(profileId)
        db.blockedAppDao().upsert(BlockedAppEntity("com.social"))
        usage.foregroundPackage = "com.social"
        usage.usageSinceMidnight = emptyMap()
        calendar.focusKeywordsActive = false
        val assembler = PolicyAssembler(db, prefs, usage, calendar)
        val input = assembler.build(usage.foregroundPackage, null)
        val decision = PolicyEngine.evaluate(input)
        assertTrue(decision.blockApp)
    }

    @Test
    fun build_usesLiveForegroundUsage_whenStatsLagSoBudgetBlocksInSession() = runBlocking {
        val profileId = db.focusProfileDao().insert(FocusProfileEntity(name = "P", preset = "DEEP_WORK"))
        prefs.setActiveProfileId(profileId)
        db.appBudgetDao().upsert(
            AppBudgetEntity(packageName = "com.social", maxMinutesPerDay = 1, maxOpensPerDay = 0),
        )
        usage.foregroundPackage = "com.social"
        usage.usageSinceMidnight = mapOf("com.social" to 0L)
        usage.foregroundTodayByPackage = mapOf("com.social" to 90_000L)
        calendar.focusKeywordsActive = false
        val assembler = PolicyAssembler(db, prefs, usage, calendar)
        val decision = PolicyEngine.evaluate(assembler.build(usage.foregroundPackage, null))
        assertTrue(decision.blockApp)
        assertEquals("Daily budget exceeded", decision.reason)
    }
}
