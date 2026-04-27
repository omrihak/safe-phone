package com.safephone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safephone.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.cloudSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud_sync_prefs")

/**
 * Persistent settings + state for the cloud-sync feature.
 *
 * Stored in a dedicated DataStore (separate from [FocusPreferences]) so that policy snapshots
 * captured for sync never include the credentials that would gate sync itself.
 */
class CloudSyncPreferences(
    private val context: Context,
    /**
     * Build-time default applied when the user hasn't entered their own token. Wired to
     * [BuildConfig.CLOUD_SYNC_DEFAULT_GITHUB_TOKEN] in production; tests can override.
     */
    private val defaultGitHubToken: String = BuildConfig.CLOUD_SYNC_DEFAULT_GITHUB_TOKEN,
    /**
     * Build-time default gist id so a fresh install knows which remote document to pull from before
     * the user has pushed once. Wired to [BuildConfig.CLOUD_SYNC_DEFAULT_GIST_ID] in production.
     */
    private val defaultGistId: String = BuildConfig.CLOUD_SYNC_DEFAULT_GIST_ID,
) {

    /** User-entered token or, if blank, the baked-in build default. The pref is the source of truth when set. */
    val gitHubToken: Flow<String> = context.cloudSyncDataStore.data.map { prefs ->
        prefs[KEY_GH_TOKEN].orEmpty().ifEmpty { defaultGitHubToken }
    }
    /** User-entered gist id or, if blank, the baked-in build default. */
    val gistId: Flow<String> = context.cloudSyncDataStore.data.map { prefs ->
        prefs[KEY_GIST_ID].orEmpty().ifEmpty { defaultGistId }
    }
    /** Token the user explicitly typed in this device (no default fallback) — used to drive the password field UI. */
    val userEnteredGitHubToken: Flow<String> =
        context.cloudSyncDataStore.data.map { it[KEY_GH_TOKEN].orEmpty() }
    /** Gist id the user explicitly typed in this device (no default fallback). */
    val userEnteredGistId: Flow<String> =
        context.cloudSyncDataStore.data.map { it[KEY_GIST_ID].orEmpty() }
    val autoPullOnLaunch: Flow<Boolean> =
        context.cloudSyncDataStore.data.map { it[KEY_AUTO_PULL] ?: true }
    val autoPushOnChange: Flow<Boolean> =
        context.cloudSyncDataStore.data.map { it[KEY_AUTO_PUSH] ?: true }
    val lastSyncEpochMs: Flow<Long> = context.cloudSyncDataStore.data.map { it[KEY_LAST_SYNC] ?: 0L }
    val lastSyncDirection: Flow<String> =
        context.cloudSyncDataStore.data.map { it[KEY_LAST_SYNC_DIR].orEmpty() }
    val lastSyncMessage: Flow<String> =
        context.cloudSyncDataStore.data.map { it[KEY_LAST_SYNC_MSG].orEmpty() }

    /** True when [BuildConfig.CLOUD_SYNC_DEFAULT_GITHUB_TOKEN] was set at build time. UI uses this as a hint. */
    val hasDefaultGitHubToken: Boolean get() = defaultGitHubToken.isNotEmpty()

    /** True when [BuildConfig.CLOUD_SYNC_DEFAULT_GIST_ID] was set at build time. UI uses this as a hint. */
    val hasDefaultGistId: Boolean get() = defaultGistId.isNotEmpty()

    /** Stable per-install identifier embedded into snapshots so each device knows which writes were its own. */
    suspend fun deviceId(): String {
        val existing = context.cloudSyncDataStore.data.map { it[KEY_DEVICE_ID].orEmpty() }.first()
        if (existing.isNotEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        context.cloudSyncDataStore.edit { it[KEY_DEVICE_ID] = fresh }
        return fresh
    }

    suspend fun snapshot(): Snapshot {
        val data = context.cloudSyncDataStore.data.first()
        return Snapshot(
            gitHubToken = data[KEY_GH_TOKEN].orEmpty().ifEmpty { defaultGitHubToken },
            gistId = data[KEY_GIST_ID].orEmpty().ifEmpty { defaultGistId },
            autoPullOnLaunch = data[KEY_AUTO_PULL] ?: true,
            autoPushOnChange = data[KEY_AUTO_PUSH] ?: true,
        )
    }

    suspend fun setGitHubToken(token: String) {
        context.cloudSyncDataStore.edit { prefs ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) prefs.remove(KEY_GH_TOKEN) else prefs[KEY_GH_TOKEN] = trimmed
        }
    }

    suspend fun setGistId(id: String) {
        context.cloudSyncDataStore.edit { prefs ->
            val trimmed = id.trim()
            if (trimmed.isEmpty()) prefs.remove(KEY_GIST_ID) else prefs[KEY_GIST_ID] = trimmed
        }
    }

    suspend fun setAutoPullOnLaunch(value: Boolean) {
        context.cloudSyncDataStore.edit { it[KEY_AUTO_PULL] = value }
    }

    suspend fun setAutoPushOnChange(value: Boolean) {
        context.cloudSyncDataStore.edit { it[KEY_AUTO_PUSH] = value }
    }

    suspend fun recordSyncResult(epochMs: Long, direction: String, message: String) {
        context.cloudSyncDataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = epochMs
            prefs[KEY_LAST_SYNC_DIR] = direction
            prefs[KEY_LAST_SYNC_MSG] = message
        }
    }

    data class Snapshot(
        val gitHubToken: String,
        val gistId: String,
        val autoPullOnLaunch: Boolean,
        val autoPushOnChange: Boolean,
    ) {
        val isConfigured: Boolean get() = gitHubToken.isNotEmpty()
    }

    companion object {
        private val KEY_GH_TOKEN = stringPreferencesKey("github_token")
        private val KEY_GIST_ID = stringPreferencesKey("gist_id")
        private val KEY_AUTO_PULL = booleanPreferencesKey("auto_pull_on_launch")
        private val KEY_AUTO_PUSH = booleanPreferencesKey("auto_push_on_change")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
        private val KEY_LAST_SYNC_DIR = stringPreferencesKey("last_sync_direction")
        private val KEY_LAST_SYNC_MSG = stringPreferencesKey("last_sync_message")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
