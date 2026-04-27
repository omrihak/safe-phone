package com.safephone.cloud

import com.safephone.data.AppDatabase
import com.safephone.data.CloudSyncPreferences
import com.safephone.data.FocusPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates manual and automatic cloud sync against a single GitHub Gist.
 *
 * Key behaviours:
 *  - [pullNow] / [pushNow] are user-initiated; both update [status] for UI feedback.
 *  - [start] launches two long-running jobs: an auto-push observer that watches every synced field
 *    and pushes a debounced snapshot, and a periodic auto-pull poller that detects changes made on
 *    other devices and applies them locally.
 *  - [pullOnLaunchIfEnabled] is meant to run once after process start, before the periodic poller.
 *  - All push paths skip a write when the captured snapshot is byte-identical to the last known
 *    remote payload, so noisy Flow emissions don't spam GitHub.
 *  - When applying a remote snapshot (manual pull or auto-pull) we set a watermark that suppresses
 *    the auto-push observer so the writes from [PolicySnapshotRepository.apply] don't immediately
 *    bounce back as a new push.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CloudSyncManager(
    db: AppDatabase,
    private val focusPrefs: FocusPreferences,
    private val cloudPrefs: CloudSyncPreferences,
    private val parentScope: CoroutineScope,
    private val snapshotRepo: PolicySnapshotRepository = PolicySnapshotRepository(db, focusPrefs),
    private val gistClientFactory: (String) -> GistClient = ::GistClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val autoPullPollIntervalMs: Long = DEFAULT_AUTO_PULL_INTERVAL_MS,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val pushMutex = Mutex()
    private val pullMutex = Mutex()

    @Volatile private var observerJob: Job? = null
    @Volatile private var autoPullJob: Job? = null
    @Volatile private var lastUploadedJson: String? = null
    @Volatile private var suppressAutoPushUntilMs: Long = 0L

    private val _status = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    private val daos = SyncedDaos(db)

    /** Starts the auto-push observer + periodic auto-pull poller. Safe to call multiple times. */
    fun start() {
        if (observerJob == null) {
            observerJob = scope.launch {
                // Combine every observable input that contributes to the policy. Any change emits.
                val sources: List<Flow<Any?>> = listOf(
                    daos.profiles,
                    daos.blockedApps,
                    daos.domainRules,
                    daos.appBudgets,
                    daos.breakPolicy,
                    daos.calendarKeywords,
                    focusPrefs.enforcementEnabled,
                    focusPrefs.activeProfileId,
                    focusPrefs.aggressivePoll,
                    focusPrefs.notificationHints,
                    focusPrefs.calendarAware,
                    focusPrefs.mindfulFrictionPackages,
                    focusPrefs.systemMonochromeAutomationEnabled,
                    focusPrefs.socialMediaCategoryBlocked,
                    focusPrefs.partnerBlockAlertEnabled,
                    focusPrefs.partnerBlockAlertThreshold,
                    focusPrefs.partnerAlertPhoneDigits,
                    focusPrefs.activeDaysOfWeek,
                )
                combine(sources) { _ -> Unit }
                    .drop(1) // skip the snapshot emitted on subscription
                    .debounce(AUTO_PUSH_DEBOUNCE_MS)
                    .mapLatest { _ ->
                        val snap = cloudPrefs.snapshot()
                        val autoPush = cloudPrefs.autoPushOnChange.first()
                        if (!autoPush || !snap.isConfigured) return@mapLatest
                        if (nowMs() < suppressAutoPushUntilMs) return@mapLatest
                        pushNowInternal(reason = SyncTrigger.Auto)
                    }
                    .collect { /* side effects only */ }
            }
        }
        if (autoPullJob == null) {
            autoPullJob = scope.launch {
                while (isActive) {
                    delay(autoPullPollIntervalMs)
                    try {
                        autoPullIfRemoteChanged()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Swallow transient network/parse errors; the next tick will retry.
                    }
                }
            }
        }
    }

    /** Cancels the auto-push observer + auto-pull poller (e.g. when tearing down in tests). */
    fun stop() {
        observerJob?.cancel()
        observerJob = null
        autoPullJob?.cancel()
        autoPullJob = null
    }

    suspend fun pullOnLaunchIfEnabled() {
        val snap = cloudPrefs.snapshot()
        if (!snap.isConfigured || !snap.autoPullOnLaunch) return
        if (snap.gistId.isBlank()) return
        pullNow()
    }

    /**
     * Reads the configured gist and applies it locally only when its serialized content differs
     * from [lastUploadedJson] (i.e. some other device has written a new snapshot since our last
     * push or pull). Quietly does nothing when sync is unconfigured, the read fails, or the
     * payload is unchanged — the caller polls this on a timer so any failure is implicitly retried.
     */
    private suspend fun autoPullIfRemoteChanged(): Unit = pullMutex.withLock {
        val snap = cloudPrefs.snapshot()
        if (!snap.isConfigured || snap.gistId.isBlank()) return@withLock
        val client = gistClientFactory(snap.gitHubToken)
        val res = client.readGist(snap.gistId)
        if (res !is GistClient.GistResult.Success) return@withLock
        val remoteContent = res.value.content
        if (remoteContent == lastUploadedJson) return@withLock
        val parsed = snapshotRepo.fromJson(remoteContent) ?: return@withLock
        // Suppress the auto-push observer so the writes from apply() don't bounce back as a push.
        suppressAutoPushUntilMs = nowMs() + APPLY_SUPPRESS_MS
        val applyResult = snapshotRepo.apply(parsed)
        if (applyResult is PolicySnapshotRepository.ApplyResult.SchemaTooNew) return@withLock
        lastUploadedJson = remoteContent
        cloudPrefs.recordSyncResult(nowMs(), DIR_PULL, "Auto-pulled snapshot")
    }

    suspend fun pullNow(): CloudSyncStatus = pullMutex.withLock {
        val snap = cloudPrefs.snapshot()
        if (!snap.isConfigured) {
            return@withLock setStatus(CloudSyncStatus.Failure("Add a GitHub token first"))
        }
        if (snap.gistId.isBlank()) {
            return@withLock setStatus(CloudSyncStatus.Failure("No gist id yet — push once to create one"))
        }
        setStatus(CloudSyncStatus.InProgress(SyncDirection.Pull))
        val client = gistClientFactory(snap.gitHubToken)
        when (val res = client.readGist(snap.gistId)) {
            is GistClient.GistResult.Failure -> {
                val msg = formatHttpError("pull", res.httpCode, res.message)
                cloudPrefs.recordSyncResult(nowMs(), DIR_PULL, "Failed: $msg")
                setStatus(CloudSyncStatus.Failure(msg))
            }
            is GistClient.GistResult.Success -> {
                val parsed = snapshotRepo.fromJson(res.value.content)
                if (parsed == null) {
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PULL, "Failed: invalid JSON")
                    return@withLock setStatus(CloudSyncStatus.Failure("Remote payload was not valid JSON"))
                }
                // Suppress the auto-push observer for a window covering the apply + DataStore commit
                // so we don't immediately re-push what we just downloaded.
                suppressAutoPushUntilMs = nowMs() + APPLY_SUPPRESS_MS
                val applyResult = snapshotRepo.apply(parsed)
                if (applyResult is PolicySnapshotRepository.ApplyResult.SchemaTooNew) {
                    val msg = "Remote uses schema v${applyResult.remoteVersion}; update SafePhone"
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PULL, "Failed: $msg")
                    return@withLock setStatus(CloudSyncStatus.Failure(msg))
                }
                lastUploadedJson = res.value.content
                cloudPrefs.recordSyncResult(nowMs(), DIR_PULL, "Pulled snapshot")
                setStatus(CloudSyncStatus.Success(SyncDirection.Pull, "Pulled snapshot"))
            }
        }
    }

    suspend fun pushNow(): CloudSyncStatus = pushNowInternal(SyncTrigger.Manual)

    private suspend fun pushNowInternal(reason: SyncTrigger): CloudSyncStatus = pushMutex.withLock {
        val snap = cloudPrefs.snapshot()
        if (!snap.isConfigured) {
            return@withLock if (reason == SyncTrigger.Manual) {
                setStatus(CloudSyncStatus.Failure("Add a GitHub token first"))
            } else {
                _status.value
            }
        }
        if (reason == SyncTrigger.Manual) {
            setStatus(CloudSyncStatus.InProgress(SyncDirection.Push))
        }
        val deviceId = cloudPrefs.deviceId()
        val snapshot = snapshotRepo.capture(deviceId, nowMs())
        val json = snapshotRepo.toJson(snapshot)

        // Avoid pointless writes when nothing actually changed since the last successful push/pull.
        if (json == lastUploadedJson && reason == SyncTrigger.Auto) return@withLock _status.value

        val client = gistClientFactory(snap.gitHubToken)
        val gistId = snap.gistId
        return@withLock if (gistId.isBlank()) {
            when (val res = client.createGist(json)) {
                is GistClient.GistResult.Failure -> {
                    val msg = formatHttpError("push", res.httpCode, res.message)
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PUSH, "Failed: $msg")
                    setStatus(CloudSyncStatus.Failure(msg))
                }
                is GistClient.GistResult.Success -> {
                    cloudPrefs.setGistId(res.value)
                    lastUploadedJson = json
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PUSH, "Created gist ${res.value.take(8)}…")
                    setStatus(CloudSyncStatus.Success(SyncDirection.Push, "Pushed (created gist)"))
                }
            }
        } else {
            when (val res = client.updateGist(gistId, json)) {
                is GistClient.GistResult.Failure -> {
                    val msg = formatHttpError("push", res.httpCode, res.message)
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PUSH, "Failed: $msg")
                    setStatus(CloudSyncStatus.Failure(msg))
                }
                is GistClient.GistResult.Success -> {
                    lastUploadedJson = json
                    cloudPrefs.recordSyncResult(nowMs(), DIR_PUSH, "Pushed snapshot")
                    setStatus(CloudSyncStatus.Success(SyncDirection.Push, "Pushed snapshot"))
                }
            }
        }
    }

    private fun setStatus(s: CloudSyncStatus): CloudSyncStatus {
        _status.value = s
        return s
    }

    private fun formatHttpError(action: String, code: Int, body: String): String = when (code) {
        -1 -> "Could not reach GitHub during $action: $body"
        401 -> "GitHub rejected the token (check the gist scope)"
        403 -> "GitHub rate-limit or permission error"
        404 -> "Gist not found — clear the gist id to create a new one"
        else -> "GitHub $action failed (HTTP $code): $body"
    }

    private enum class SyncTrigger { Manual, Auto }

    companion object {
        private const val AUTO_PUSH_DEBOUNCE_MS = 5_000L
        private const val APPLY_SUPPRESS_MS = 10_000L
        private const val DEFAULT_AUTO_PULL_INTERVAL_MS = 60_000L
        private const val DIR_PUSH = "push"
        private const val DIR_PULL = "pull"
    }
}

/** Direction of the most recent sync attempt, surfaced to the UI. */
enum class SyncDirection { Push, Pull }

/** State machine surfaced to UI; new states should remain additive. */
sealed class CloudSyncStatus {
    data object Idle : CloudSyncStatus()
    data class InProgress(val direction: SyncDirection) : CloudSyncStatus()
    data class Success(val direction: SyncDirection, val message: String) : CloudSyncStatus()
    data class Failure(val message: String) : CloudSyncStatus()
}

private class SyncedDaos(db: AppDatabase) {
    val profiles = db.focusProfileDao().observeAll()
    val blockedApps = db.blockedAppDao().observeAll()
    val domainRules = db.domainRuleDao().observeAll()
    val appBudgets = db.appBudgetDao().observeAll()
    val breakPolicy = db.breakPolicyDao().observe()
    val calendarKeywords = db.calendarKeywordDao().observeAll()
}
