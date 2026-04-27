package com.safephone.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safephone.data.AppBudgetEntity
import com.safephone.data.AppDatabase
import com.safephone.data.BlockedAppEntity
import com.safephone.data.BreakPolicyEntity
import com.safephone.data.CalendarKeywordEntity
import com.safephone.data.DomainRuleEntity
import com.safephone.data.FocusPreferences
import com.safephone.data.FocusProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolicySnapshotRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: FocusPreferences
    private lateinit var repo: PolicySnapshotRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
        prefs = FocusPreferences(context)
        repo = PolicySnapshotRepository(db, prefs)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun capture_includes_all_synced_fields() = runBlocking {
        seedAll()
        val snap = repo.capture(deviceId = "dev-A", nowMs = 1_700_000_000_000L)

        assertEquals(PolicySnapshot.CURRENT_SCHEMA_VERSION, snap.schemaVersion)
        assertEquals("dev-A", snap.deviceId)
        assertEquals(1_700_000_000_000L, snap.updatedAtMs)
        assertEquals(listOf("com.alpha", "com.zulu"), snap.blockedApps)
        assertEquals(2, snap.domainRules.size)
        assertEquals(listOf("focus", "review"), snap.calendarKeywords)
        assertEquals(15, snap.breakPolicy.breakDurationMinutes)
        assertEquals(3, snap.breakPolicy.maxBreaksPerDay)
        assertEquals(1, snap.appBudgets.size)
        assertEquals("com.budgeted", snap.appBudgets.first().packageName)
        assertEquals(true, snap.prefs.calendarAware)
        assertEquals(setOf(1, 2, 3, 4, 5).toList().sorted(), snap.prefs.activeDaysOfWeek)
        assertEquals(listOf("com.mindful"), snap.prefs.mindfulFrictionPackages)
    }

    @Test
    fun apply_replaces_local_state_with_snapshot() = runBlocking {
        seedAll()
        val captured = repo.capture(deviceId = "dev-A", nowMs = 1L)
        val json = repo.toJson(captured)
        assertNotNull(repo.fromJson(json))

        wipeAndSeedDifferent()
        val parsed = repo.fromJson(json)!!
        val applyResult = repo.apply(parsed)
        assertEquals(PolicySnapshotRepository.ApplyResult.Applied, applyResult)

        val recaptured = repo.capture(deviceId = "dev-A", nowMs = 2L)
        // Re-captured snapshot equals the original modulo timestamp/device fields.
        assertEquals(captured.copy(updatedAtMs = 2L), recaptured)
    }

    @Test
    fun apply_rejects_newer_schema() = runBlocking {
        val futureSnap = PolicySnapshot(
            schemaVersion = PolicySnapshot.CURRENT_SCHEMA_VERSION + 1,
            deviceId = "dev-future",
            updatedAtMs = 1L,
            profiles = emptyList(),
            blockedApps = emptyList(),
            domainRules = emptyList(),
            appBudgets = emptyList(),
            breakPolicy = BreakPolicyDto(5, 10, 30),
            calendarKeywords = emptyList(),
            prefs = PrefsDto(
                enforcementEnabled = true,
                activeProfileId = null,
                aggressivePoll = true,
                notificationHints = false,
                calendarAware = false,
                mindfulFrictionPackages = emptyList(),
                systemMonochromeAutomationEnabled = true,
                socialMediaCategoryBlocked = false,
                partnerBlockAlertEnabled = false,
                partnerBlockAlertThreshold = 5,
                partnerAlertPhoneDigits = "",
                activeDaysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            ),
        )
        val res = repo.apply(futureSnap)
        assertTrue(res is PolicySnapshotRepository.ApplyResult.SchemaTooNew)
    }

    @Test
    fun fromJson_returns_null_for_garbage() {
        assertEquals(null, repo.fromJson("not json"))
        assertEquals(null, repo.fromJson("{\"schemaVersion\": 1}"))
    }

    private suspend fun seedAll() {
        db.focusProfileDao().insert(
            FocusProfileEntity(
                id = 7L,
                name = "Deep work",
                preset = "DEEP_WORK",
                useTierA = true,
                useTierB = true,
                useTierC = false,
                strictBrowserLock = true,
                enforceGrayscale = false,
                softEnforcement = false,
            ),
        )
        db.blockedAppDao().upsert(BlockedAppEntity("com.alpha"))
        db.blockedAppDao().upsert(BlockedAppEntity("com.zulu"))
        db.domainRuleDao().upsert(DomainRuleEntity(id = 1, pattern = "twitter.com", isAllowlist = false))
        db.domainRuleDao().upsert(DomainRuleEntity(id = 2, pattern = ".docs.corp", isAllowlist = true))
        db.appBudgetDao().upsert(
            AppBudgetEntity(packageName = "com.budgeted", maxMinutesPerDay = 30, maxOpensPerDay = 5),
        )
        db.breakPolicyDao().upsert(
            BreakPolicyEntity(
                id = 1,
                maxBreaksPerDay = 3,
                breakDurationMinutes = 15,
                minGapBetweenBreaksMinutes = 45,
            ),
        )
        db.calendarKeywordDao().upsert(CalendarKeywordEntity("focus"))
        db.calendarKeywordDao().upsert(CalendarKeywordEntity("review"))

        prefs.setEnforcementEnabled(true)
        prefs.setActiveProfileId(7L)
        prefs.setAggressivePoll(false)
        prefs.setNotificationHints(true)
        prefs.setCalendarAware(true)
        prefs.setMindfulFrictionPackages(setOf("com.mindful"))
        prefs.setSystemMonochromeAutomationEnabled(false)
        prefs.setSocialMediaCategoryBlocked(true)
        prefs.setPartnerBlockAlertEnabled(true)
        prefs.setPartnerBlockAlertThreshold(7)
        prefs.setPartnerAlertPhoneDigits("+14155552671")
        prefs.setActiveDaysOfWeek(setOf(1, 2, 3, 4, 5))

        // Sanity check that prefs writes flushed.
        assertEquals(7L, prefs.activeProfileId.first())
    }

    private suspend fun wipeAndSeedDifferent() {
        db.focusProfileDao().deleteAll()
        db.blockedAppDao().deleteAll()
        db.domainRuleDao().deleteAll()
        db.appBudgetDao().deleteAll()
        db.calendarKeywordDao().deleteAll()
        db.breakPolicyDao().upsert(BreakPolicyEntity())
        db.blockedAppDao().upsert(BlockedAppEntity("com.unrelated"))
        prefs.setEnforcementEnabled(false)
        prefs.setActiveProfileId(null)
        prefs.setMindfulFrictionPackages(emptySet())
        prefs.setActiveDaysOfWeek((1..7).toSet())
    }
}
