package com.safephone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_prefs")

class FocusPreferences(private val context: Context) {

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING] ?: false }
    /** Retained for migration/tests; enforcement is always active in the foreground service. */
    val enforcementEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENFORCEMENT] ?: true }
    val activeProfileId: Flow<Long?> = context.dataStore.data.map { it[KEY_PROFILE_ID] }
    val aggressivePoll: Flow<Boolean> = context.dataStore.data.map { it[KEY_AGGRESSIVE_POLL] ?: true }
    val notificationHints: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_HINTS] ?: false }
    val calendarAware: Flow<Boolean> = context.dataStore.data.map { it[KEY_CALENDAR] ?: false }
    val mindfulFrictionPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_MINDFUL]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    val breakEndEpochMs: Flow<Long?> = context.dataStore.data.map { it[KEY_BREAK_END] }
    val breaksUsedToday: Flow<Int> = context.dataStore.data.map { it[KEY_BREAKS_USED] ?: 0 }
    val breakDayEpochDay: Flow<Long> = context.dataStore.data.map { it[KEY_BREAK_DAY] ?: 0L }
    val lastBreakEndedEpochMs: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_BREAK_END] ?: 0L }

    /**
     * When true (default), focus enforcement toggles system color correction (daltonizer) if
     * [android.Manifest.permission.WRITE_SECURE_SETTINGS] is granted.
     */
    val systemMonochromeAutomationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SYSTEM_MONOCHROME_AUTO] ?: true }

    val partnerBlockAlertEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PARTNER_BLOCK_ALERT] ?: false }
    val partnerBlockAlertThreshold: Flow<Int> =
        context.dataStore.data.map { it[KEY_PARTNER_BLOCK_THRESHOLD] ?: 5 }
    val partnerAlertPhoneDigits: Flow<String> =
        context.dataStore.data.map { it[KEY_PARTNER_ALERT_PHONE] ?: "" }

    suspend fun hasDaltonizerSnapshot(): Boolean =
        context.dataStore.data.map { it[KEY_DALTONIZER_SNAP] == true }.first()

    suspend fun setOnboardingCompleted(v: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING] = v }
    }

    suspend fun setEnforcementEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_ENFORCEMENT] = v }
    }

    suspend fun setActiveProfileId(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(KEY_PROFILE_ID) else it[KEY_PROFILE_ID] = id
        }
    }

    suspend fun setAggressivePoll(v: Boolean) {
        context.dataStore.edit { it[KEY_AGGRESSIVE_POLL] = v }
    }

    suspend fun setNotificationHints(v: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_HINTS] = v }
    }

    suspend fun setCalendarAware(v: Boolean) {
        context.dataStore.edit { it[KEY_CALENDAR] = v }
    }

    suspend fun setMindfulFrictionPackages(packages: Set<String>) {
        context.dataStore.edit {
            it[KEY_MINDFUL] = packages.joinToString(",")
        }
    }

    suspend fun setBreakState(endEpochMs: Long?, breaksUsed: Int, dayEpochDay: Long, lastBreakEnd: Long) {
        context.dataStore.edit { prefs ->
            if (endEpochMs == null) prefs.remove(KEY_BREAK_END) else prefs[KEY_BREAK_END] = endEpochMs
            prefs[KEY_BREAKS_USED] = breaksUsed
            prefs[KEY_BREAK_DAY] = dayEpochDay
            prefs[KEY_LAST_BREAK_END] = lastBreakEnd
        }
    }

    suspend fun clearBreakTimer() {
        context.dataStore.edit { it.remove(KEY_BREAK_END) }
    }

    suspend fun setSystemMonochromeAutomationEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_SYSTEM_MONOCHROME_AUTO] = v }
    }

    suspend fun setPartnerBlockAlertEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_PARTNER_BLOCK_ALERT] = v }
    }

    suspend fun setPartnerBlockAlertThreshold(v: Int) {
        context.dataStore.edit { it[KEY_PARTNER_BLOCK_THRESHOLD] = v }
    }

    suspend fun setPartnerAlertPhoneDigits(digits: String) {
        context.dataStore.edit { it[KEY_PARTNER_ALERT_PHONE] = digits }
    }

    /**
     * Returns true the first time we alert for [kind]/[targetKey] on [dayEpochDay]; false on repeats.
     */
    suspend fun claimPartnerAlertForTarget(dayEpochDay: Long, kind: String, targetKey: String): Boolean {
        val composite = "$kind\u0000$targetKey"
        var claimed = false
        context.dataStore.edit { prefs ->
            val storedDay = prefs[KEY_PARTNER_CLAIM_DAY] ?: -1L
            var set = prefs[KEY_PARTNER_CLAIM_SET] ?: emptySet()
            if (storedDay != dayEpochDay) {
                set = emptySet()
                prefs[KEY_PARTNER_CLAIM_DAY] = dayEpochDay
            }
            if (composite in set) {
                claimed = false
                prefs[KEY_PARTNER_CLAIM_SET] = set
            } else {
                claimed = true
                prefs[KEY_PARTNER_CLAIM_SET] = set + composite
            }
        }
        return claimed
    }

    suspend fun saveDaltonizerSnapshot(snapshot: DaltonizerSnapshot) {
        context.dataStore.edit { p ->
            p[KEY_DALTONIZER_SNAP] = true
            p[KEY_DALTONIZER_PREV_ENABLED] = snapshot.enabled
            p[KEY_DALTONIZER_PREV_MODE] = snapshot.mode
        }
    }

    suspend fun takeDaltonizerSnapshotIfPresent(): DaltonizerSnapshot? {
        val data = context.dataStore.data.first()
        if (data[KEY_DALTONIZER_SNAP] != true) return null
        val snap = DaltonizerSnapshot(
            enabled = data[KEY_DALTONIZER_PREV_ENABLED] ?: 0,
            mode = data[KEY_DALTONIZER_PREV_MODE] ?: DaltonizerSnapshot.MODE_DISABLED,
        )
        context.dataStore.edit { p ->
            p.remove(KEY_DALTONIZER_SNAP)
            p.remove(KEY_DALTONIZER_PREV_ENABLED)
            p.remove(KEY_DALTONIZER_PREV_MODE)
        }
        return snap
    }

    suspend fun clearDaltonizerSnapshot() {
        context.dataStore.edit { p ->
            p.remove(KEY_DALTONIZER_SNAP)
            p.remove(KEY_DALTONIZER_PREV_ENABLED)
            p.remove(KEY_DALTONIZER_PREV_MODE)
        }
    }

    companion object {
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        private val KEY_ENFORCEMENT = booleanPreferencesKey("enforcement")
        private val KEY_PROFILE_ID = longPreferencesKey("profile_id")
        private val KEY_AGGRESSIVE_POLL = booleanPreferencesKey("aggressive_poll")
        private val KEY_NOTIF_HINTS = booleanPreferencesKey("notif_hints")
        private val KEY_CALENDAR = booleanPreferencesKey("calendar_aware")
        private val KEY_MINDFUL = stringPreferencesKey("mindful_pkgs")
        private val KEY_BREAK_END = longPreferencesKey("break_end_ms")
        private val KEY_BREAKS_USED = intPreferencesKey("breaks_used")
        private val KEY_BREAK_DAY = longPreferencesKey("break_day")
        private val KEY_LAST_BREAK_END = longPreferencesKey("last_break_end_ms")
        private val KEY_SYSTEM_MONOCHROME_AUTO = booleanPreferencesKey("system_monochrome_auto")
        private val KEY_DALTONIZER_SNAP = booleanPreferencesKey("daltonizer_snapshot")
        private val KEY_DALTONIZER_PREV_ENABLED = intPreferencesKey("daltonizer_prev_enabled")
        private val KEY_DALTONIZER_PREV_MODE = intPreferencesKey("daltonizer_prev_mode")
        private val KEY_PARTNER_BLOCK_ALERT = booleanPreferencesKey("partner_block_alert")
        private val KEY_PARTNER_BLOCK_THRESHOLD = intPreferencesKey("partner_block_threshold")
        private val KEY_PARTNER_ALERT_PHONE = stringPreferencesKey("partner_alert_phone")
        private val KEY_PARTNER_CLAIM_DAY = longPreferencesKey("partner_claim_day")
        private val KEY_PARTNER_CLAIM_SET = stringSetPreferencesKey("partner_claim_keys")
    }
}
