package com.safephone.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseDaoInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = AppDatabase.createInMemory(ctx)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun blockedApp_roundTrip() = runBlocking {
        db.blockedAppDao().upsert(BlockedAppEntity("com.game"))
        assertEquals(listOf("com.game"), db.blockedAppDao().getAll().map { it.packageName })
    }

    @Test
    fun domainRule_two_block_entries() = runBlocking {
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = ".corp.com", isAllowlist = false))
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = "twitter.com", isAllowlist = false))
        val rules = db.domainRuleDao().getAll()
        assertEquals(2, rules.size)
        assertTrue(rules.all { !it.isAllowlist })
    }

    @Test
    fun scheduleWindow_forProfile() = runBlocking {
        val id = db.focusProfileDao().insert(FocusProfileEntity(name = "Work", preset = "WORK_HOURS"))
        db.scheduleWindowDao().upsert(
            ScheduleWindowEntity(profileId = id, dayOfWeek = 3, startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60),
        )
        val windows = db.scheduleWindowDao().getForProfile(id)
        assertEquals(1, windows.size)
        assertEquals(3, windows[0].dayOfWeek)
    }

    @Test
    fun appBudget_upsert() = runBlocking {
        db.appBudgetDao().upsert(AppBudgetEntity("com.slack", 45))
        assertEquals(45, db.appBudgetDao().getAll().first { it.packageName == "com.slack" }.maxMinutesPerDay)
    }

    @Test
    fun breakPolicy_upsert() = runBlocking {
        db.breakPolicyDao().upsert(BreakPolicyEntity(maxBreaksPerDay = 3, breakDurationMinutes = 15, minGapBetweenBreaksMinutes = 60))
        val p = db.breakPolicyDao().get()!!
        assertEquals(3, p.maxBreaksPerDay)
        assertEquals(15, p.breakDurationMinutes)
    }

    @Test
    fun calendarKeyword_upsert() = runBlocking {
        db.calendarKeywordDao().upsert(CalendarKeywordEntity("focus"))
        assertEquals(listOf("focus"), db.calendarKeywordDao().getAll().map { it.keyword })
    }
}
