// Pure-function port of PolicyEngine.normalizeHost / matchesPattern / domainBlocked
// from app/src/main/java/com/safephone/policy/PolicyEngine.kt. Keep the three
// helpers behaviour-identical to the Android side so a single gist payload
// produces the same blocking decisions on phone and browser.

export function normalizeHost(host) {
  let h = (host ?? "").toLowerCase().trim();
  if (h.startsWith("www.")) h = h.slice(4);
  return h;
}

export function matchesPattern(host, pattern) {
  const h = normalizeHost(host);
  const p = (pattern ?? "").toLowerCase().trim();
  if (p.length === 0) return false;
  if (p.startsWith("*.")) {
    return h.endsWith(p.slice(1));
  }
  if (p.startsWith(".")) {
    const trimmed = p.slice(1);
    return h.endsWith(trimmed) || h === trimmed;
  }
  return h === p || h.endsWith("." + p);
}

export function domainBlocked(host, patterns) {
  if (!patterns || patterns.length === 0) return false;
  const h = normalizeHost(host);
  return patterns.some((p) => matchesPattern(h, p));
}

// Reduce a user-typed pattern to the canonical bare host that
// declarativeNetRequest's `requestDomains` matches against — that field
// already covers "host == p || host.endsWith('.' + p)", so `.foo.com`,
// `*.foo.com`, and `foo.com` all collapse to `foo.com`.
export function patternToRequestDomain(pattern) {
  let p = (pattern ?? "").toLowerCase().trim();
  if (p.startsWith("*.")) p = p.slice(2);
  else if (p.startsWith(".")) p = p.slice(1);
  return p;
}
