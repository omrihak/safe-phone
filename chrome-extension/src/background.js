// MV3 service worker. Drives the same lifecycle the Android CloudSyncManager
// does — initial pull, periodic auto-pull, dedupe-then-apply — and additionally
// owns the local break timer that the Android app keeps per-device.
//
// The translation pipeline is:
//   gist JSON -> PolicyStore (ingest+cache)
//                 -> PolicyEvaluator (gate by profile/day/break/soft)
//                 -> ruleBuilder (declarativeNetRequest dynamic rules)

import { PolicyStore, pullPolicy } from "./sync/policyStore.js";
import { evaluatePolicy } from "./policy/policyEvaluator.js";
import { buildDynamicRules } from "./rules/ruleBuilder.js";
import { domainBlocked } from "./policy/domainMatcher.js";
import {
  BreakStore,
  BreakConsts,
  computeEndBreakEarly,
  computeStartBreak,
  effectiveBreaksUsedToday,
  grantedBreaksByNow,
  isOnBreakNow,
  nextGrantEpochMs,
} from "./break/breakManager.js";

const ALARM_AUTO_PULL = "auto-pull";
const AUTO_PULL_PERIOD_MIN = 1; // 60s, matches DEFAULT_AUTO_PULL_INTERVAL_MS in CloudSyncManager.kt
const EXTENSION_BASE = (() => {
  // chrome.runtime is available in the service-worker; in tests we never load
  // background.js so the access path is safe to keep at module top level.
  try {
    return chrome.runtime.getURL("").replace(/\/$/, "");
  } catch (_) {
    return "";
  }
})();

const policyStore = new PolicyStore();
const breakStore = new BreakStore();

chrome.runtime.onInstalled.addListener(async () => {
  await scheduleAutoPull();
  await pullAndApply();
});

chrome.runtime.onStartup.addListener(async () => {
  await scheduleAutoPull();
  await pullAndApply();
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === ALARM_AUTO_PULL) {
    await pullAndApply();
  } else if (alarm.name === BreakConsts.ALARM_NAME) {
    await endBreakDueToAlarm();
  }
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  // Dispatch async work without blocking the message bus. Returning `true`
  // keeps the channel open for sendResponse.
  (async () => {
    try {
      switch (msg?.type) {
        case "pullNow": {
          const res = await pullAndApply();
          sendResponse({ ok: true, result: res });
          break;
        }
        case "getStatus": {
          sendResponse({ ok: true, result: await collectStatus() });
          break;
        }
        case "startBreak": {
          sendResponse({ ok: true, result: await startBreak() });
          break;
        }
        case "endBreak": {
          sendResponse({ ok: true, result: await endBreakEarly() });
          break;
        }
        default:
          sendResponse({ ok: false, message: `Unknown message ${msg?.type}` });
      }
    } catch (e) {
      sendResponse({ ok: false, message: e?.message ?? String(e) });
    }
  })();
  return true;
});

async function scheduleAutoPull() {
  const existing = await chrome.alarms.get(ALARM_AUTO_PULL);
  if (!existing) {
    chrome.alarms.create(ALARM_AUTO_PULL, { periodInMinutes: AUTO_PULL_PERIOD_MIN });
  }
}

async function pullAndApply() {
  const res = await pullPolicy({ store: policyStore, nowMs: Date.now() });
  await applyDecisionFromCache();
  return res;
}

async function applyDecisionFromCache() {
  const snapshot = await policyStore.getSnapshot();
  const breakState = await breakStore.get();
  const decision = evaluatePolicy({
    snapshot,
    breakState,
    now: new Date(),
  });
  await applyDynamicRules(decision.enforcing ? decision.blockedPatterns : []);
  return decision;
}

async function applyDynamicRules(patterns) {
  const newRules = buildDynamicRules(patterns, EXTENSION_BASE);
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    addRules: newRules,
    removeRuleIds: existing.map((r) => r.id),
  });
}

// After blocking rules are restored (break ended), reload any open tabs that
// are currently on a blocked domain so the declarativeNetRequest redirect takes
// effect immediately rather than waiting for the next navigation.
async function reloadBlockedTabs(patterns) {
  if (!patterns || patterns.length === 0) return;
  const tabs = await chrome.tabs.query({});
  const reloads = [];
  for (const tab of tabs) {
    if (!tab.url) continue;
    let url;
    try {
      url = new URL(tab.url);
    } catch {
      continue;
    }
    if (url.protocol !== "http:" && url.protocol !== "https:") continue;
    if (domainBlocked(url.hostname, patterns)) {
      reloads.push(chrome.tabs.reload(tab.id));
    }
  }
  await Promise.allSettled(reloads);
}

async function startBreak() {
  const snapshot = await policyStore.getSnapshot();
  if (!snapshot) {
    return { ok: false, message: "No policy pulled yet" };
  }
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const state = await breakStore.get();
  const next = computeStartBreak({
    state,
    breakPolicy: snapshot.breakPolicy ?? {},
    nowMs: Date.now(),
    tz,
  });
  if (!next) {
    return { ok: false, message: "No breaks remaining or already on a break" };
  }
  await breakStore.set(next);
  // Schedule the auto-end alarm. Re-using the alarm name overwrites any
  // previous one so we never have two end-of-break alarms in flight.
  chrome.alarms.create(BreakConsts.ALARM_NAME, { when: next.breakEndMs });
  await applyDecisionFromCache();
  return { ok: true, breakEndMs: next.breakEndMs };
}

async function endBreakEarly() {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const state = await breakStore.get();
  const next = computeEndBreakEarly({ state, nowMs: Date.now(), tz });
  await breakStore.set(next);
  await chrome.alarms.clear(BreakConsts.ALARM_NAME);
  const decision = await applyDecisionFromCache();
  await reloadBlockedTabs(decision?.enforcing ? decision.blockedPatterns : []);
  return { ok: true };
}

async function endBreakDueToAlarm() {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const state = await breakStore.get();
  if (!isOnBreakNow(state, Date.now())) {
    // Defensive: if state was already cleared (e.g. user ended early), just
    // re-derive rules and exit.
    await applyDecisionFromCache();
    return;
  }
  await breakStore.set({
    breakEndMs: null,
    breaksUsedToday: state.breaksUsedToday ?? 0,
    breakDayEpochDay: state.breakDayEpochDay ?? 0,
    lastBreakEndedEpochMs: Date.now(),
  });
  const decision = await applyDecisionFromCache();
  await reloadBlockedTabs(decision?.enforcing ? decision.blockedPatterns : []);
}

async function collectStatus() {
  const snapshot = await policyStore.getSnapshot();
  const lastSync = await policyStore.getStatus();
  const breakState = await breakStore.get();
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const now = new Date();
  const decision = evaluatePolicy({ snapshot, breakState, now });

  const breakPolicy = snapshot?.breakPolicy ?? { maxBreaksPerDay: 0, breakDurationMinutes: 0 };
  const granted = grantedBreaksByNow({
    maxBreaksPerDay: breakPolicy.maxBreaksPerDay,
    nowMs: now.getTime(),
    tz,
  });
  const used = effectiveBreaksUsedToday(breakState, now.getTime(), tz);
  const remaining = Math.max(0, granted - used);
  const minutesUntilNext = (() => {
    const max = Number(breakPolicy.maxBreaksPerDay) || 0;
    if (used >= max) return null;
    if (remaining > 0) return 0;
    const next = nextGrantEpochMs({
      maxBreaksPerDay: max,
      nowMs: now.getTime(),
      tz,
    });
    if (next == null) return null;
    return Math.max(1, Math.ceil((next - now.getTime()) / 60_000));
  })();

  return {
    decision,
    snapshotLoaded: !!snapshot,
    lastSync,
    breakState,
    breaksRemainingToday: remaining,
    minutesUntilNextBreak: minutesUntilNext,
    breakDurationMinutes: breakPolicy.breakDurationMinutes ?? 0,
    blockedPatternsCount: decision.blockedPatterns.length,
  };
}
