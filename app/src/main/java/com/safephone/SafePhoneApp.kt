package com.safephone

import android.app.Application
import com.safephone.data.AppDatabase
import com.safephone.data.FocusPreferences
import com.safephone.data.seedDatabaseIfEmpty
import com.safephone.service.CalendarWatcher
import com.safephone.service.PolicyAssembler
import com.safephone.service.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafePhoneApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set
    lateinit var prefs: FocusPreferences
        private set
    lateinit var usageStatsRepository: UsageStatsRepository
        private set
    lateinit var calendarWatcher: CalendarWatcher
        private set
    lateinit var policyAssembler: PolicyAssembler
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        prefs = FocusPreferences(this)
        usageStatsRepository = UsageStatsRepository(this)
        calendarWatcher = CalendarWatcher(this)
        policyAssembler = PolicyAssembler(database, prefs, usageStatsRepository, calendarWatcher)
        appScope.launch(Dispatchers.IO) {
            seedDatabaseIfEmpty(database)
        }
    }
}
