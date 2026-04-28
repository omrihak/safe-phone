// Local-only port of com.safephone.service.BreakManager. Keeps the same
// gradual-grant arithmetic the Android side uses so the "breaks remaining"
// counter behaves identically across phone and browser; the actual timer is
// per-device and is intentionally never written back to the gist (matches the
// contract documented at the top of PolicySnapshot.kt).

const STATE_KEY = "breakState";
const ALARM_NAME = "break-end";
const MS_PER_DAY = 24 * 60 * 60 * 1000;

function emptyState() {
  return {
    breakEndMs: null,
    breaksUsedToday: 0,
    breakDayEpochDay: 0,
    lastBreakEndedEpochMs: null,
  };
}

/**
 * UTC epoch-millis of local midnight in `tz` for the wall-clock day that
 * `nowMs` falls into. Uses Intl to read the local Y/M/D and back-projects
 * through Date.UTC. DST-safe because the only consumer (localDayBoundaries)
 * always probes ~25 hours later to find the next day's midnight.
 */
function startOfLocalDayUtc(nowMs, tz) {
  const fmt = new Intl.DateTimeFormat("en-US", {
    timeZone: tz || "UTC",
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = Object.fromEntries(
    fmt.formatToParts(new Date(nowMs)).map((p) => [p.type, p.value]),
  );
  return Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day));
}

function localDayBoundaries(nowMs, tz) {
  const dayStartMs = startOfLocalDayUtc(nowMs, tz);
  // Probe 25h ahead so we land safely inside the *next* local day even across
  // DST spring-forward / fall-back boundaries.
  const nextDayStartMs = startOfLocalDayUtc(dayStartMs + MS_PER_DAY + 60 * 60 * 1000, tz);
  const dayLengthMs = Math.max(1, nextDayStartMs - dayStartMs);
  return { dayStartMs, nextDayStartMs, dayLengthMs };
}

function clamp(v, lo, hi) {
  return v < lo ? lo : v > hi ? hi : v;
}

/**
 * Mirror of BreakManager.grantedBreaksByNow:
 *   granted = floor(elapsedMs * maxBreaks / dayLengthMs) + 1, capped at maxBreaks.
 */
export function grantedBreaksByNow({ maxBreaksPerDay, nowMs, tz }) {
  const max = Math.max(0, Number(maxBreaksPerDay) || 0);
  if (max === 0) return 0;
  const { dayStartMs, dayLengthMs } = localDayBoundaries(nowMs, tz);
  const elapsed = clamp(nowMs - dayStartMs, 0, dayLengthMs - 1);
  const granted = Math.floor((elapsed * max) / dayLengthMs) + 1;
  return Math.min(granted, max);
}

/**
 * Mirror of BreakManager.nextGrantEpochMs. Returns null once every break for
 * the current local day has already been unlocked.
 */
export function nextGrantEpochMs({ maxBreaksPerDay, nowMs, tz }) {
  const max = Math.max(0, Number(maxBreaksPerDay) || 0);
  if (max <= 0) return null;
  const { dayStartMs, dayLengthMs } = localDayBoundaries(nowMs, tz);
  const elapsed = Math.max(0, nowMs - dayStartMs);
  const nextIndex = Math.floor((elapsed * max) / dayLengthMs) + 1;
  if (nextIndex >= max) return null;
  return dayStartMs + Math.floor((nextIndex * dayLengthMs) / max);
}

/**
 * Local epoch-day index (days since 1970-01-01 in tz). Mirrors
 * LocalDate.now(zone).toEpochDay() used throughout BreakManager.
 */
export function localEpochDay(nowMs, tz) {
  return Math.floor(startOfLocalDayUtc(nowMs, tz) / MS_PER_DAY);
}

/**
 * Resolve "breaks used today", resetting to 0 when the stored day no longer
 * matches the local day. Mirrors BreakManager.effectiveBreaksUsedToday.
 */
export function effectiveBreaksUsedToday(state, nowMs, tz) {
  const today = localEpochDay(nowMs, tz);
  if (state.breakDayEpochDay !== today) return 0;
  return state.breaksUsedToday ?? 0;
}

export function isOnBreakNow(state, nowMs) {
  return !!(state.breakEndMs && state.breakEndMs > nowMs);
}

/**
 * Pure version of BreakManager.startBreak. Returns the new state, or null when
 * the user is already on a break or has no breaks left.
 */
export function computeStartBreak({ state, breakPolicy, nowMs, tz }) {
  if (isOnBreakNow(state, nowMs)) return null;
  const granted = grantedBreaksByNow({
    maxBreaksPerDay: breakPolicy.maxBreaksPerDay ?? 0,
    nowMs,
    tz,
  });
  const used = effectiveBreaksUsedToday(state, nowMs, tz);
  if (granted - used <= 0) return null;
  const minutes = Math.max(1, Number(breakPolicy.breakDurationMinutes) || 0);
  return {
    breakEndMs: nowMs + minutes * 60 * 1000,
    breaksUsedToday: used + 1,
    breakDayEpochDay: localEpochDay(nowMs, tz),
    lastBreakEndedEpochMs: state.lastBreakEndedEpochMs ?? null,
  };
}

/**
 * Pure version of BreakManager.endBreakEarly. Clears the timer but preserves
 * the breaksUsedToday counter — ending early still costs the user that break.
 */
export function computeEndBreakEarly({ state, nowMs, tz }) {
  return {
    breakEndMs: null,
    breaksUsedToday: state.breaksUsedToday ?? 0,
    breakDayEpochDay: localEpochDay(nowMs, tz),
    lastBreakEndedEpochMs: nowMs,
  };
}

// Thin wrapper around chrome.storage.local. Pulled out so the background
// service worker and the popup share one canonical reader/writer.
export class BreakStore {
  constructor(storage) {
    this.storage = storage ?? (typeof chrome !== "undefined" ? chrome.storage.local : null);
  }

  async get() {
    if (!this.storage) return emptyState();
    const out = await this.storage.get(STATE_KEY);
    return { ...emptyState(), ...(out?.[STATE_KEY] ?? {}) };
  }

  async set(state) {
    await this.storage.set({ [STATE_KEY]: state });
  }

  async clear() {
    await this.set(emptyState());
  }
}

export const BreakConsts = {
  STATE_KEY,
  ALARM_NAME,
  emptyState,
};
