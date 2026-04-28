// Parity tests for src/policy/domainMatcher.js. Cases are lifted from
// app/src/test/java/com/safephone/policy/PolicyEngineTest.kt so the same gist
// payload produces the same blocking decisions on phone and browser.
import { describe, it, expect } from "vitest";
import {
  domainBlocked,
  matchesPattern,
  normalizeHost,
  patternToRequestDomain,
} from "../src/policy/domainMatcher.js";
import { SOCIAL_MEDIA_DOMAINS } from "../src/policy/socialDomains.js";

describe("normalizeHost", () => {
  it("strips www. and lowercases", () => {
    expect(normalizeHost("WWW.EXAMPLE.COM")).toBe("example.com");
    expect(normalizeHost("  Foo.COM  ")).toBe("foo.com");
  });
});

describe("matchesPattern", () => {
  it("treats bare host as exact + suffix match", () => {
    expect(matchesPattern("mobile.twitter.com", "twitter.com")).toBe(true);
    expect(matchesPattern("twitter.com", "twitter.com")).toBe(true);
    expect(matchesPattern("nottwitter.com", "twitter.com")).toBe(false);
  });

  it("treats leading-dot pattern as suffix or exact match", () => {
    expect(matchesPattern("login.okta.com", ".okta.com")).toBe(true);
    expect(matchesPattern("foo.oktapreview.com", ".oktapreview.com")).toBe(true);
    expect(matchesPattern("okta.com", ".okta.com")).toBe(true);
  });

  it("treats *. as subdomain-only suffix match (NOT the bare host)", () => {
    // Mirrors PolicyEngine.matchesPattern: `*.foo.com` becomes `.foo.com` and
    // `host.endsWith('.foo.com')` is true only for subdomains.
    expect(matchesPattern("a.example.com", "*.example.com")).toBe(true);
    expect(matchesPattern("deep.a.example.com", "*.example.com")).toBe(true);
    expect(matchesPattern("example.com", "*.example.com")).toBe(false);
    expect(matchesPattern("notexample.com", "*.example.com")).toBe(false);
  });

  it("ignores blank patterns", () => {
    expect(matchesPattern("foo.com", "")).toBe(false);
    expect(matchesPattern("foo.com", "   ")).toBe(false);
  });
});

describe("domainBlocked", () => {
  it("returns true when any pattern matches the (normalised) host", () => {
    expect(domainBlocked("login.okta.com", [".okta.com"])).toBe(true);
    expect(domainBlocked("mobile.twitter.com", ["twitter.com"])).toBe(true);
  });

  it("returns false when no pattern matches", () => {
    expect(domainBlocked("example.com", ["twitter.com", ".okta.com"])).toBe(false);
  });

  it("blocks all 15 social-media domains via the bare host", () => {
    for (const d of SOCIAL_MEDIA_DOMAINS) {
      expect(domainBlocked("www." + d, SOCIAL_MEDIA_DOMAINS)).toBe(true);
      expect(domainBlocked("m." + d, SOCIAL_MEDIA_DOMAINS)).toBe(true);
      expect(domainBlocked(d, SOCIAL_MEDIA_DOMAINS)).toBe(true);
    }
  });

  it("does not block unrelated hosts via the social-media list", () => {
    expect(domainBlocked("example.com", SOCIAL_MEDIA_DOMAINS)).toBe(false);
    expect(domainBlocked("google.com", SOCIAL_MEDIA_DOMAINS)).toBe(false);
  });
});

describe("patternToRequestDomain", () => {
  it("collapses *. and . prefixes to the bare host", () => {
    expect(patternToRequestDomain("*.foo.com")).toBe("foo.com");
    expect(patternToRequestDomain(".foo.com")).toBe("foo.com");
    expect(patternToRequestDomain("foo.com")).toBe("foo.com");
    expect(patternToRequestDomain("FOO.com")).toBe("foo.com");
  });
});
