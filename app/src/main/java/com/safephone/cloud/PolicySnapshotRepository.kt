package com.safephone.cloud

import com.safephone.data.AppBudgetEntity
import com.safephone.data.AppDatabase
import com.safephone.data.BlockedAppEntity
import com.safephone.data.BreakPolicyEntity
import com.safephone.data.CalendarKeywordEntity
import com.safephone.data.DomainRuleEntity
import com.safephone.data.FocusPreferences
import com.safephone.data.FocusProfileEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Captures the local policy into a [PolicySnapshot] (for upload) and applies a remote snapshot back
 * onto the local database + preferences (for download).
 *
 * `apply()` uses replace-all semantics so the device ends up byte-identical to the remote snapshot
 * for synced fields. This intentionally clobbers local edits — call sites are expected to confirm
 * with the user (or to suppress observers during apply, see [com.safephone.cloud.CloudSyncManager]).
 */
class PolicySnapshotRepository(
    private val db: AppDatabase,
    private val prefs: FocusPreferences,
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter: JsonAdapter<PolicySnapshot> =
        moshi.adapter(PolicySnapshot::class.java).indent("  ")

    fun toJson(snapshot: PolicySnapshot): String = adapter.toJson(snapshot)

    /** Returns null when the JSON is malformed or omits required fields. */
    fun fromJson(json: String): PolicySnapshot? = try {
        adapter.fromJson(json)
    } catch (_: Exception) {
        null
    }

    suspend fun capture(deviceId: String, nowMs: Long): PolicySnapshot = withContext(Dispatchers.IO) {
        val profiles = db.focusProfileDao().getAll().map { it.toDto() }
        val blocked = db.blockedAppDao().getAll().map { it.packageName }.sorted()
        val domains = db.domainRuleDao().getAll().map { it.toDto() }
        val budgets = db.appBudgetDao().getAll().map { it.toDto() }
            .sortedBy { it.packageName }
        val breakPolicy = (db.breakPolicyDao().get() ?: BreakPolicyEntity()).toDto()
        val keywords = db.calendarKeywordDao().getAll().map { it.keyword }.sorted()

        val prefsDto = PrefsDto(
            enforcementEnabled = prefs.enforcementEnabled.first(),
            activeProfileId = prefs.activeProfileId.first(),
            aggressivePoll = prefs.aggressivePoll.first(),
            notificationHints = prefs.notificationHints.first(),
            calendarAware = prefs.calendarAware.first(),
            mindfulFrictionPackages = prefs.mindfulFrictionPackages.first().sorted(),
            systemMonochromeAutomationEnabled = prefs.systemMonochromeAutomationEnabled.first(),
            socialMediaCategoryBlocked = prefs.socialMediaCategoryBlocked.first(),
            partnerBlockAlertEnabled = prefs.partnerBlockAlertEnabled.first(),
            partnerBlockAlertThreshold = prefs.partnerBlockAlertThreshold.first(),
            partnerAlertPhoneDigits = prefs.partnerAlertPhoneDigits.first(),
            activeDaysOfWeek = prefs.activeDaysOfWeek.first().sorted(),
        )

        PolicySnapshot(
            schemaVersion = PolicySnapshot.CURRENT_SCHEMA_VERSION,
            deviceId = deviceId,
            updatedAtMs = nowMs,
            profiles = profiles,
            blockedApps = blocked,
            domainRules = domains,
            appBudgets = budgets,
            breakPolicy = breakPolicy,
            calendarKeywords = keywords,
            prefs = prefsDto,
        )
    }

    suspend fun apply(snapshot: PolicySnapshot): ApplyResult = withContext(Dispatchers.IO) {
        if (snapshot.schemaVersion > PolicySnapshot.CURRENT_SCHEMA_VERSION) {
            return@withContext ApplyResult.SchemaTooNew(snapshot.schemaVersion)
        }

        db.focusProfileDao().deleteAll()
        snapshot.profiles.forEach { dto -> db.focusProfileDao().insert(dto.toEntity()) }

        db.blockedAppDao().deleteAll()
        snapshot.blockedApps.forEach { pkg -> db.blockedAppDao().upsert(BlockedAppEntity(pkg)) }

        db.domainRuleDao().deleteAll()
        snapshot.domainRules.forEach { dto -> db.domainRuleDao().upsert(dto.toEntity()) }

        db.appBudgetDao().deleteAll()
        snapshot.appBudgets.forEach { dto -> db.appBudgetDao().upsert(dto.toEntity()) }

        db.breakPolicyDao().upsert(snapshot.breakPolicy.toEntity())

        db.calendarKeywordDao().deleteAll()
        snapshot.calendarKeywords.forEach { kw ->
            db.calendarKeywordDao().upsert(CalendarKeywordEntity(kw))
        }

        val p = snapshot.prefs
        prefs.setEnforcementEnabled(p.enforcementEnabled)
        prefs.setActiveProfileId(p.activeProfileId)
        prefs.setAggressivePoll(p.aggressivePoll)
        prefs.setNotificationHints(p.notificationHints)
        prefs.setCalendarAware(p.calendarAware)
        prefs.setMindfulFrictionPackages(p.mindfulFrictionPackages.toSet())
        prefs.setSystemMonochromeAutomationEnabled(p.systemMonochromeAutomationEnabled)
        prefs.setSocialMediaCategoryBlocked(p.socialMediaCategoryBlocked)
        prefs.setPartnerBlockAlertEnabled(p.partnerBlockAlertEnabled)
        prefs.setPartnerBlockAlertThreshold(p.partnerBlockAlertThreshold)
        prefs.setPartnerAlertPhoneDigits(p.partnerAlertPhoneDigits)
        prefs.setActiveDaysOfWeek(p.activeDaysOfWeek.toSet())

        ApplyResult.Applied
    }

    sealed class ApplyResult {
        data object Applied : ApplyResult()
        data class SchemaTooNew(val remoteVersion: Int) : ApplyResult()
    }
}

private fun FocusProfileEntity.toDto() = ProfileDto(
    id = id,
    name = name,
    preset = preset,
    useTierA = useTierA,
    useTierB = useTierB,
    useTierC = useTierC,
    strictBrowserLock = strictBrowserLock,
    enforceGrayscale = enforceGrayscale,
    softEnforcement = softEnforcement,
)

private fun ProfileDto.toEntity() = FocusProfileEntity(
    id = id,
    name = name,
    preset = preset,
    useTierA = useTierA,
    useTierB = useTierB,
    useTierC = useTierC,
    strictBrowserLock = strictBrowserLock,
    enforceGrayscale = enforceGrayscale,
    softEnforcement = softEnforcement,
)

private fun DomainRuleEntity.toDto() = DomainRuleDto(
    id = id,
    pattern = pattern,
    isAllowlist = isAllowlist,
)

private fun DomainRuleDto.toEntity() = DomainRuleEntity(
    id = id,
    pattern = pattern,
    isAllowlist = isAllowlist,
)

private fun AppBudgetEntity.toDto() = AppBudgetDto(
    packageName = packageName,
    maxMinutesPerDay = maxMinutesPerDay,
    maxOpensPerDay = maxOpensPerDay,
)

private fun AppBudgetDto.toEntity() = AppBudgetEntity(
    packageName = packageName,
    maxMinutesPerDay = maxMinutesPerDay,
    maxOpensPerDay = maxOpensPerDay,
)

private fun BreakPolicyEntity.toDto() = BreakPolicyDto(
    maxBreaksPerDay = maxBreaksPerDay,
    breakDurationMinutes = breakDurationMinutes,
    minGapBetweenBreaksMinutes = minGapBetweenBreaksMinutes,
)

private fun BreakPolicyDto.toEntity() = BreakPolicyEntity(
    id = 1,
    maxBreaksPerDay = maxBreaksPerDay,
    breakDurationMinutes = breakDurationMinutes,
    minGapBetweenBreaksMinutes = minGapBetweenBreaksMinutes,
)
