package com.safephone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun seedDatabaseIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
    if (db.focusProfileDao().getAll().isNotEmpty()) return@withContext
    val defaultProfile = FocusProfileEntity(
        name = "Default",
        preset = "WORK_HOURS",
        useTierA = true,
        useTierB = true,
        useTierC = false,
    )
    db.focusProfileDao().insert(defaultProfile)
    db.breakPolicyDao().upsert(BreakPolicyEntity())
    db.domainRuleDao().upsert(DomainRuleEntity(pattern = "twitter.com", isAllowlist = false))
    db.domainRuleDao().upsert(DomainRuleEntity(pattern = "x.com", isAllowlist = false))
}
