// Parity tests for src/break/breakManager.js — cases lifted from
// app/src/test/java/com/safephone/service/BreakManagerTest.kt. The Android
// helpers operate in `LocalDate.now(zone).toEpochDay()` arithmetic; we use the
// same UTC fixture day so the JS port produces identical numbers.
import { describe, it, expect } from "vitest";
import {
  computeEndBreakEarly,
  computeStartBreak,
  effectiveBreaksUsedToday,
  grantedBreaksByNow,
  isOnBreakNow,
  localEpochDay,
  nextGrantEpochMs,
} from "../src/break/breakManager.js";

const TZ = "UTC";
const dayStartMs = Date.UTC(2026, 0, 1); // 2026-01-01T00:00:00Z
const HOUR = 60 * 60 * 1000;
const MINUTE = 60 * 1000;

describe("grantedBreaksByNow", () => {
  it("unlocks evenly across the day", () => {
    expect(grantedBreaksByNow({ maxBreaksPerDay: 8, nowMs: dayStartMs, tz: TZ })).toBe(1);
    expect(grantedBreaksByNow({ maxBreaksPerDay: 8, nowMs: dayStartMs + 3 * HOUR, tz: TZ })).toBe(2);
    expect(
      grantedBreaksByNow({
        maxBreaksPerDay: 8,
        nowMs: dayStartMs + 23 * HOUR + 59 * MINUTE,
        tz: TZ,
      }),
    ).toBe(8);
  });

  it("returns 0 when maxBreaksPerDay is 0", () => {
    expect(grantedBreaksByNow({ maxBreaksPerDay: 0, nowMs: dayStartMs, tz: TZ })).toBe(0);
  });

  it("never exceeds maxBreaksPerDay even at the very last millisecond", () => {
    const lastMs = dayStartMs + 24 * HOUR - 1;
    expect(grantedBreaksByNow({ maxBreaksPerDay: 4, nowMs: lastMs, tz: TZ })).toBe(4);
  });
});

describe("nextGrantEpochMs", () => {
  it("returns the next slot when more breaks remain", () => {
    expect(nextGrantEpochMs({ maxBreaksPerDay: 8, nowMs: dayStartMs + 1, tz: TZ })).toBe(
      dayStartMs + 3 * HOUR,
    );
  });

  it("returns null once every break is unlocked", () => {
    expect(
      nextGrantEpochMs({
        maxBreaksPerDay: 8,
        nowMs: dayStartMs + 23 * HOUR + 59 * MINUTE,
        tz: TZ,
      }),
    ).toBeNull();
  });
});

describe("localEpochDay + effectiveBreaksUsedToday", () => {
  it("returns the days-since-epoch index for the local day", () => {
    expect(localEpochDay(dayStartMs, TZ)).toBe(Math.floor(dayStartMs / (24 * HOUR)));
  });

  it("resets the counter when the stored day no longer matches", () => {
    const today = localEpochDay(dayStartMs, TZ);
    expect(
      effectiveBreaksUsedToday({ breaksUsedToday: 3, breakDayEpochDay: today - 1 }, dayStartMs, TZ),
    ).toBe(0);
    expect(
      effectiveBreaksUsedToday({ breaksUsedToday: 3, breakDayEpochDay: today }, dayStartMs, TZ),
    ).toBe(3);
  });
});

describe("computeStartBreak", () => {
  const breakPolicy = { maxBreaksPerDay: 8, breakDurationMinutes: 5 };

  it("starts a break and increments the counter", () => {
    const state = { breakEndMs: null, breaksUsedToday: 0, breakDayEpochDay: 0 };
    const next = computeStartBreak({ state, breakPolicy, nowMs: dayStartMs, tz: TZ });
    expect(next).not.toBeNull();
    expect(next.breakEndMs).toBe(dayStartMs + 5 * MINUTE);
    expect(next.breaksUsedToday).toBe(1);
    expect(next.breakDayEpochDay).toBe(localEpochDay(dayStartMs, TZ));
  });

  it("refuses when already on a break", () => {
    const state = { breakEndMs: dayStartMs + 60 * MINUTE, breaksUsedToday: 1, breakDayEpochDay: localEpochDay(dayStartMs, TZ) };
    expect(computeStartBreak({ state, breakPolicy, nowMs: dayStartMs + MINUTE, tz: TZ })).toBeNull();
  });

  it("refuses when the daily quota is exhausted at the current grant rate", () => {
    // At day start, only 1 break is granted. Used=1 means 0 remaining.
    const state = { breakEndMs: null, breaksUsedToday: 1, breakDayEpochDay: localEpochDay(dayStartMs, TZ) };
    expect(computeStartBreak({ state, breakPolicy, nowMs: dayStartMs, tz: TZ })).toBeNull();
  });
});

describe("computeEndBreakEarly", () => {
  it("clears the timer but keeps the counter", () => {
    const state = {
      breakEndMs: dayStartMs + 30 * MINUTE,
      breaksUsedToday: 2,
      breakDayEpochDay: localEpochDay(dayStartMs, TZ),
      lastBreakEndedEpochMs: null,
    };
    const next = computeEndBreakEarly({ state, nowMs: dayStartMs + 5 * MINUTE, tz: TZ });
    expect(next.breakEndMs).toBeNull();
    expect(next.breaksUsedToday).toBe(2);
    expect(next.lastBreakEndedEpochMs).toBe(dayStartMs + 5 * MINUTE);
  });
});

describe("isOnBreakNow", () => {
  it("returns true while breakEndMs is in the future", () => {
    expect(isOnBreakNow({ breakEndMs: 100 }, 50)).toBe(true);
    expect(isOnBreakNow({ breakEndMs: 100 }, 100)).toBe(false);
    expect(isOnBreakNow({ breakEndMs: null }, 50)).toBe(false);
  });
});
