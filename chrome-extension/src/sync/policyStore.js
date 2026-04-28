// Thin wrapper around chrome.storage.local for the cached policy snapshot.
//
// Mirrors the dedupe behaviour of CloudSyncManager.lastUploadedJson: the
// extension stores both the parsed snapshot (used to derive rules + drive UI)
// and the raw JSON that produced it, so a periodic poll can short-circuit when
// the gist hasn't changed since the previous tick.

import { readGist, formatGistError } from "./gistClient.js";
import {
  CLOUD_SYNC_DEFAULT_GITHUB_TOKEN,
  CLOUD_SYNC_DEFAULT_GIST_ID,
} from "../generated/config.js";

const KEY_SNAPSHOT = "policySnapshot";
const KEY_LAST_JSON = "lastPulledJson";
const KEY_SYNC_STATUS = "lastSyncStatus";
const SUPPORTED_SCHEMA_VERSION = 1;

export class PolicyStore {
  constructor(storage) {
    this.storage = storage ?? (typeof chrome !== "undefined" ? chrome.storage.local : null);
  }

  async getSnapshot() {
    if (!this.storage) return null;
    const out = await this.storage.get(KEY_SNAPSHOT);
    return out?.[KEY_SNAPSHOT] ?? null;
  }

  async getLastJson() {
    if (!this.storage) return null;
    const out = await this.storage.get(KEY_LAST_JSON);
    return out?.[KEY_LAST_JSON] ?? null;
  }

  async getStatus() {
    if (!this.storage) return null;
    const out = await this.storage.get(KEY_SYNC_STATUS);
    return out?.[KEY_SYNC_STATUS] ?? null;
  }

  async writeStatus(status) {
    await this.storage.set({ [KEY_SYNC_STATUS]: status });
  }

  async writeSnapshot(snapshot, rawJson) {
    await this.storage.set({
      [KEY_SNAPSHOT]: snapshot,
      [KEY_LAST_JSON]: rawJson,
    });
  }
}

/**
 * Validate, accept-or-reject, and persist a freshly-fetched payload. Returns
 * the parsed snapshot on success so the caller can re-derive rules without a
 * follow-up storage round-trip.
 *
 * Mirrors PolicySnapshotRepository.fromJson + apply: refuses snapshots whose
 * schemaVersion is newer than what this build understands, and refuses
 * malformed JSON outright. Both failure modes leave the cached snapshot
 * untouched.
 *
 * @returns {{ok: true, snapshot: object} | {ok: false, reason: string}}
 */
export async function ingestPayload({ store, rawJson, nowMs }) {
  let parsed;
  try {
    parsed = JSON.parse(rawJson);
  } catch (_) {
    await store.writeStatus({
      direction: "pull",
      ok: false,
      message: "Remote payload was not valid JSON",
      atMs: nowMs,
    });
    return { ok: false, reason: "invalid-json" };
  }
  if (!isPolicySnapshotShape(parsed)) {
    await store.writeStatus({
      direction: "pull",
      ok: false,
      message: "Remote payload missing required fields",
      atMs: nowMs,
    });
    return { ok: false, reason: "invalid-shape" };
  }
  if (typeof parsed.schemaVersion === "number" && parsed.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
    await store.writeStatus({
      direction: "pull",
      ok: false,
      message: `Remote uses schema v${parsed.schemaVersion}; update SafePhone`,
      atMs: nowMs,
    });
    return { ok: false, reason: "schema-too-new" };
  }
  await store.writeSnapshot(parsed, rawJson);
  await store.writeStatus({
    direction: "pull",
    ok: true,
    message: "Pulled snapshot",
    atMs: nowMs,
  });
  return { ok: true, snapshot: parsed };
}

/**
 * Pull from the configured gist and write through to chrome.storage.local
 * when the payload differs from the cached one. Returns:
 *   - { ok: true, snapshot, changed }: snapshot reflects the latest content;
 *     `changed` tells the caller whether they need to rebuild dynamic rules.
 *   - { ok: false, message }: pull failed; the cached snapshot is untouched.
 */
export async function pullPolicy({
  store = new PolicyStore(),
  token = CLOUD_SYNC_DEFAULT_GITHUB_TOKEN,
  gistId = CLOUD_SYNC_DEFAULT_GIST_ID,
  nowMs = Date.now(),
} = {}) {
  await store.writeStatus({ direction: "pull", inProgress: true, atMs: nowMs });
  const res = await readGist({ token, gistId });
  if (!res.ok) {
    const msg = formatGistError("pull", res.httpCode, res.message);
    await store.writeStatus({ direction: "pull", ok: false, message: msg, atMs: nowMs });
    return { ok: false, message: msg };
  }
  const lastJson = await store.getLastJson();
  if (res.content === lastJson) {
    const cached = await store.getSnapshot();
    await store.writeStatus({
      direction: "pull",
      ok: true,
      message: "Already up to date",
      atMs: nowMs,
    });
    return { ok: true, snapshot: cached, changed: false };
  }
  const ingest = await ingestPayload({ store, rawJson: res.content, nowMs });
  if (!ingest.ok) {
    return { ok: false, message: `Could not apply remote snapshot (${ingest.reason})` };
  }
  return { ok: true, snapshot: ingest.snapshot, changed: true };
}

function isPolicySnapshotShape(obj) {
  if (!obj || typeof obj !== "object") return false;
  if (!Array.isArray(obj.profiles)) return false;
  if (!Array.isArray(obj.domainRules)) return false;
  if (!obj.prefs || typeof obj.prefs !== "object") return false;
  if (!obj.breakPolicy || typeof obj.breakPolicy !== "object") return false;
  return true;
}

export const PolicyStoreKeys = {
  SNAPSHOT: KEY_SNAPSHOT,
  LAST_JSON: KEY_LAST_JSON,
  SYNC_STATUS: KEY_SYNC_STATUS,
  SUPPORTED_SCHEMA_VERSION,
};
