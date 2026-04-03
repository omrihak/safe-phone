package com.safephone.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safephone.data.AppDatabase
import com.safephone.data.BlockedAppEntity
import com.safephone.data.CalendarKeywordEntity
import com.safephone.data.DomainRuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RulesExporterTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportJson_empty_database() = runBlocking {
        val json = RulesExporter(db).exportJson()
        assertEquals(
            """{"blockedPackages":[],"domainRules":[],"calendarKeywords":[]}""",
            json,
        )
    }

    @Test
    fun exportJson_roundTrip_shape() = runBlocking {
        db.blockedAppDao().upsert(BlockedAppEntity("com.game"))
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = ".corp.com", isAllowlist = false))
        db.calendarKeywordDao().upsert(CalendarKeywordEntity("deepwork"))
        val json = RulesExporter(db).exportJson()
        assertTrue(json.contains("com.game"))
        assertTrue(json.contains(".corp.com"))
        assertTrue(json.contains("deepwork"))
        assertTrue(json.contains("\"allowlist\":false"))
    }

    @Test
    fun exportJson_matches_golden_file() = runBlocking {
        db.blockedAppDao().upsert(BlockedAppEntity("com.blocked.app"))
        db.domainRuleDao().upsert(DomainRuleEntity(pattern = ".okta.com", isAllowlist = false))
        db.calendarKeywordDao().upsert(CalendarKeywordEntity("focus"))
        val json = RulesExporter(db).exportJson()
        val golden = javaClass.getResourceAsStream("/rules_export_golden.json")!!.bufferedReader().readText().trim()
        assertEquals(golden, json)
    }
}
