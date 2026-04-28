// Browser-relevant subset of com.safephone.policy.PolicyEngine.evaluate.
//
// Drops everything that has no browser equivalent: app-level blocks, app
// budgets, calendar-keyword stricter mode, grayscale, partner alerts, and
// strictBrowserLock. What remains is the gating logic that decides whether
// to apply the domain block list at all.

import { SOCIAL_MEDIA_DOMAINS } from "./socialDomains.js";

/**
 * @param {object} args
 * @param {object|null} args.snapshot         Parsed PolicySnapshot (or null when not yet pulled).
 * @param {object} args.breakState           Local break state from chrome.storage.local.
 * @param {Date}   args.now
 * @returns {{
 *   enforcing: boolean,
 *   onBreak: boolean,
 *   reason: string,
 *   blockedPatterns: string[],
 *   activeProfileName: string|null,
 * }}
 */
export function evaluatePolicy({ snapshot, breakState, now }) {
  if (!snapshot) {
    return idle("No policy pulled yet");
  }

  const activeProfileId = snapshot.prefs?.activeProfileId ?? null;
  const profiles = snapshot.profiles ?? [];
  const profile =
    (activeProfileId != null && profiles.find((p) => p.id === activeProfileId)) ||
    profiles[0] ||
    null;

  if (!profile) {
    return idle("No active profile");
  }

  // 1 = Mon … 7 = Sun, matching java.time.DayOfWeek.value used by PolicyEngine.
  const todayDow = isoDayOfWeek(now);
  const activeDays = snapshot.prefs?.activeDaysOfWeek ?? [1, 2, 3, 4, 5, 6, 7];
  if (activeDays.length === 0 || !activeDays.includes(todayDow)) {
    return idle("Off day – no rules scheduled", profile.name);
  }

  const currentHour = now.getHours();
  const scheduleStartHour = snapshot.prefs?.scheduleStartHour ?? 0;
  const scheduleEndHour = snapshot.prefs?.scheduleEndHour ?? 24;
  if (currentHour < scheduleStartHour || currentHour >= scheduleEndHour) {
    return idle("Outside schedule hours", profile.name);
  }
  const onBreak = !!(breakState?.breakEndMs && breakState.breakEndMs > now.getTime());
  if (onBreak) {
    return {
      enforcing: false,
      onBreak: true,
      reason: "On break",
      blockedPatterns: [],
      activeProfileName: profile.name,
    };
  }

  if (snapshot.prefs?.enforcementEnabled === false) {
    return {
      enforcing: false,
      onBreak: false,
      reason: "Enforcement disabled",
      blockedPatterns: [],
      activeProfileName: profile.name,
    };
  }

  if (profile.softEnforcement === true) {
    return {
      enforcing: false,
      onBreak: false,
      reason: "Soft enforcement",
      blockedPatterns: [],
      activeProfileName: profile.name,
    };
  }

  return {
    enforcing: true,
    onBreak: false,
    reason: "Enforcing",
    blockedPatterns: effectiveBlockPatterns(snapshot),
    activeProfileName: profile.name,
  };
}

/**
 * Replicates PolicyAssembler's filter (denylist only) plus the optional
 * social-media category union. Returned patterns are deduplicated and
 * lower-cased so the rule builder doesn't emit duplicate dynamic rules.
 */
export function effectiveBlockPatterns(snapshot) {
  const denyRules = (snapshot.domainRules ?? [])
    .filter((r) => !r.isAllowlist)
    .map((r) => r.pattern);

  const all = [...denyRules];
  if (snapshot.prefs?.socialMediaCategoryBlocked === true) {
    all.push(...SOCIAL_MEDIA_DOMAINS);
  }

  const seen = new Set();
  const unique = [];
  for (const raw of all) {
    if (raw == null) continue;
    const p = String(raw).toLowerCase().trim();
    if (!p) continue;
    if (seen.has(p)) continue;
    seen.add(p);
    unique.push(p);
  }
  return unique;
}

function idle(reason, activeProfileName = null) {
  return {
    enforcing: false,
    onBreak: false,
    reason,
    blockedPatterns: [],
    activeProfileName,
  };
}

// JS Date.getDay() returns 0=Sun..6=Sat; java.time.DayOfWeek uses 1=Mon..7=Sun.
function isoDayOfWeek(d) {
  const js = d.getDay();
  return js === 0 ? 7 : js;
}
