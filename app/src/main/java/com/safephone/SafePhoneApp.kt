package com.safephone

import android.app.Application
import com.safephone.cloud.CloudSyncManager
import com.safephone.data.AppDatabase
import com.safephone.data.CloudSyncPreferences
import com.safephone.data.FocusPreferences
import com.safephone.data.seedDatabaseIfEmpty
import com.safephone.service.CalendarWatcher
import com.safephone.service.PolicyAssembler
import com.safephone.service.UsageStatsRepository
import com.safephone.update.InternalUpdateScheduler
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
    lateinit var cloudSyncPrefs: CloudSyncPreferences
        private set
    lateinit var usageStatsRepository: UsageStatsRepository
        private set
    lateinit var calendarWatcher: CalendarWatcher
        private set
    lateinit var policyAssembler: PolicyAssembler
        private set
    lateinit var cloudSyncManager: CloudSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        prefs = FocusPreferences(this)
        cloudSyncPrefs = CloudSyncPreferences(this)
        usageStatsRepository = UsageStatsRepository(this)
        calendarWatcher = CalendarWatcher(this)
        policyAssembler = PolicyAssembler(database, prefs, usageStatsRepository, calendarWatcher)
        cloudSyncManager = CloudSyncManager(database, prefs, cloudSyncPrefs, appScope)
        appScope.launch(Dispatchers.IO) {
            seedDatabaseIfEmpty(database)
            // Pull first so the change-observer's first emission already reflects remote state
            // and we don't immediately push back what we just downloaded.
            cloudSyncManager.pullOnLaunchIfEnabled()
            cloudSyncManager.start()
        }
        InternalUpdateScheduler.scheduleIfNeeded(this)
    }
}
