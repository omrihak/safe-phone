// Verifies the snapshot -> declarativeNetRequest translation and the
// policyEvaluator gating on top of it.
import { describe, it, expect } from "vitest";
import { buildDynamicRules } from "../src/rules/ruleBuilder.js";
import { effectiveBlockPatterns, evaluatePolicy } from "../src/policy/policyEvaluator.js";
import { SOCIAL_MEDIA_DOMAINS } from "../src/policy/socialDomains.js";

const EXT_BASE = "chrome-extension://abc123";

function snapshotFixture(overrides = {}) {
  return {
    schemaVersion: 1,
    deviceId: "test",
    updatedAtMs: 0,
    profiles: [
      {
        id: 1,
        name: "Work",
        preset: "WORK_HOURS",
        useTierA: true,
        useTierB: false,
        useTierC: false,
        strictBrowserLock: false,
        enforceGrayscale: false,
        softEnforcement: false,
      },
    ],
    blockedApps: [],
    domainRules: [
      { id: 1, pattern: "twitter.com", isAllowlist: false },
      { id: 2, pattern: ".docs.corp", isAllowlist: true },
      { id: 3, pattern: "*.example.com", isAllowlist: false },
    ],
    appBudgets: [],
    breakPolicy: { maxBreaksPerDay: 8, breakDurationMinutes: 5, minGapBetweenBreaksMinutes: 0 },
    calendarKeywords: [],
    prefs: {
      enforcementEnabled: true,
      activeProfileId: 1,
      aggressivePoll: false,
      notificationHints: false,
      calendarAware: false,
      mindfulFrictionPackages: [],
      systemMonochromeAutomationEnabled: false,
      socialMediaCategoryBlocked: false,
      partnerBlockAlertEnabled: false,
      partnerBlockAlertThreshold: 0,
      partnerAlertPhoneDigits: "",
      activeDaysOfWeek: [1, 2, 3, 4, 5, 6, 7],
    },
    ...overrides,
  };
}

describe("effectiveBlockPatterns", () => {
  it("includes denylist domain rules and excludes allowlist entries", () => {
    const patterns = effectiveBlockPatterns(snapshotFixture());
    expect(patterns).toContain("twitter.com");
    expect(patterns).toContain("*.example.com");
    expect(patterns).not.toContain(".docs.corp");
  });

  it("adds the social-media category when toggled on", () => {
    const patterns = effectiveBlockPatterns(
      snapshotFixture({
        prefs: { ...snapshotFixture().prefs, socialMediaCategoryBlocked: true },
      }),
    );
    for (const d of SOCIAL_MEDIA_DOMAINS) {
      expect(patterns).toContain(d);
    }
  });

  it("dedupes overlapping entries", () => {
    const patterns = effectiveBlockPatterns(
      snapshotFixture({
        domainRules: [
          { id: 1, pattern: "twitter.com", isAllowlist: false },
          { id: 2, pattern: "TWITTER.com", isAllowlist: false },
        ],
      }),
    );
    expect(patterns.filter((p) => p === "twitter.com").length).toBe(1);
  });
});

describe("buildDynamicRules", () => {
  it("emits one redirect rule per unique bare host", () => {
    const rules = buildDynamicRules(
      ["twitter.com", "*.example.com", ".okta.com", "twitter.com"],
      EXT_BASE,
    );
    expect(rules).toHaveLength(3);
    const domains = rules.flatMap((r) => r.condition.requestDomains);
    expect(domains).toEqual(["twitter.com", "example.com", "okta.com"]);
  });

  it("redirects only main_frame and embeds the host in the URL", () => {
    const [rule] = buildDynamicRules(["twitter.com"], EXT_BASE);
    expect(rule.condition.resourceTypes).toEqual(["main_frame"]);
    expect(rule.action.type).toBe("redirect");
    expect(rule.action.redirect.url).toContain(`${EXT_BASE}/src/ui/blocked.html`);
    expect(rule.action.redirect.url).toContain("host=twitter.com");
    expect(rule.action.redirect.url).toContain("pattern=twitter.com");
  });

  it("returns no rules for an empty input list", () => {
    expect(buildDynamicRules([], EXT_BASE)).toEqual([]);
  });

  it("skips invalid domains gracefully", () => {
    const rules = buildDynamicRules([" ", "invalid", "twitter.com"], EXT_BASE);
    const domains = rules.flatMap((r) => r.condition.requestDomains);
    expect(domains).toEqual(["twitter.com"]);
  });
});

describe("evaluatePolicy gating", () => {
  const baseArgs = {
    breakState: { breakEndMs: null, breaksUsedToday: 0, breakDayEpochDay: 0 },
    now: new Date(Date.UTC(2026, 0, 5, 12, 0, 0)), // Monday
  };

  it("returns enforcing when all gates are open", () => {
    const out = evaluatePolicy({ ...baseArgs, snapshot: snapshotFixture() });
    expect(out.enforcing).toBe(true);
    expect(out.activeProfileName).toBe("Work");
    expect(out.blockedPatterns).toContain("twitter.com");
  });

  it("returns idle on off-days", () => {
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture({
        prefs: { ...snapshotFixture().prefs, activeDaysOfWeek: [6, 7] }, // weekend only
      }),
    });
    expect(out.enforcing).toBe(false);
    expect(out.reason.toLowerCase()).toContain("off day");
  });

  it("returns onBreak when local break is active", () => {
    const futureMs = baseArgs.now.getTime() + 60_000;
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture(),
      breakState: { breakEndMs: futureMs, breaksUsedToday: 1, breakDayEpochDay: 0 },
    });
    expect(out.enforcing).toBe(false);
    expect(out.onBreak).toBe(true);
  });

  it("returns soft when the active profile flags softEnforcement", () => {
    const snap = snapshotFixture();
    snap.profiles[0].softEnforcement = true;
    const out = evaluatePolicy({ ...baseArgs, snapshot: snap });
    expect(out.enforcing).toBe(false);
    expect(out.reason.toLowerCase()).toContain("soft");
  });

  it("returns disabled when prefs.enforcementEnabled is false", () => {
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture({
        prefs: { ...snapshotFixture().prefs, enforcementEnabled: false },
      }),
    });
    expect(out.enforcing).toBe(false);
    expect(out.reason.toLowerCase()).toContain("disabled");
  });

  it("returns idle outside schedule hours", () => {
    // baseArgs.now is Monday 12:00 UTC; set schedule 14–17 so noon is before start
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture({
        prefs: { ...snapshotFixture().prefs, scheduleStartHour: 14, scheduleEndHour: 17 },
      }),
    });
    expect(out.enforcing).toBe(false);
    expect(out.reason.toLowerCase()).toContain("outside schedule hours");
  });

  it("enforces within schedule hours", () => {
    // baseArgs.now is Monday 12:00 UTC; set schedule 9–17 so noon is within range
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture({
        prefs: { ...snapshotFixture().prefs, scheduleStartHour: 9, scheduleEndHour: 17 },
      }),
    });
    expect(out.enforcing).toBe(true);
  });

  it("defaults to all-day when scheduleStartHour/scheduleEndHour are absent", () => {
    const snap = snapshotFixture();
    delete snap.prefs.scheduleStartHour;
    delete snap.prefs.scheduleEndHour;
    const out = evaluatePolicy({ ...baseArgs, snapshot: snap });
    expect(out.enforcing).toBe(true);
  });

  it("falls back to the first profile when activeProfileId is unknown", () => {
    const out = evaluatePolicy({
      ...baseArgs,
      snapshot: snapshotFixture({
        prefs: { ...snapshotFixture().prefs, activeProfileId: 999 },
      }),
    });
    expect(out.activeProfileName).toBe("Work");
    expect(out.enforcing).toBe(true);
  });
});
