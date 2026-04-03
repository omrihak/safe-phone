package com.safephone.export

import com.safephone.data.AppDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DomainExportRow(val pattern: String, val allowlist: Boolean)

data class RulesExportV1(
    val blockedPackages: List<String>,
    val domainRules: List<DomainExportRow>,
    val calendarKeywords: List<String>,
)

class RulesExporter(private val db: AppDatabase) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(RulesExportV1::class.java)

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val payload = RulesExportV1(
            blockedPackages = db.blockedAppDao().getAll().map { it.packageName },
            domainRules = db.domainRuleDao().getAll().map { DomainExportRow(it.pattern, it.isAllowlist) },
            calendarKeywords = db.calendarKeywordDao().getAll().map { it.keyword },
        )
        adapter.toJson(payload)
    }
}
