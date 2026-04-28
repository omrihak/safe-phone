// Translate effective block patterns into chrome.declarativeNetRequest dynamic
// rules. Each pattern collapses to a bare host and emits one redirect rule
// matching `host == p || host.endsWith('.' + p)` via the requestDomains
// targeting — the same reach PolicyEngine.matchesPattern gives the Android
// side for bare/leading-dot/star-dot patterns.

import { patternToRequestDomain } from "../policy/domainMatcher.js";

const RULE_BASE_ID = 1000;
const BLOCKED_PAGE_PATH = "/src/ui/blocked.html";

/**
 * @param {string[]} patterns          Effective block list (already deduped).
 * @param {string}   extensionUrlBase  e.g. chrome-extension://<id>
 * @returns {Array<chrome.declarativeNetRequest.Rule>}
 */
export function buildDynamicRules(patterns, extensionUrlBase) {
  if (!patterns || patterns.length === 0) return [];

  const rules = [];
  let id = RULE_BASE_ID;
  const seenDomains = new Set();

  for (const raw of patterns) {
    const domain = patternToRequestDomain(raw);
    if (!domain || !isValidDomain(domain)) continue;
    if (seenDomains.has(domain)) continue;
    seenDomains.add(domain);

    const blockedUrl =
      `${extensionUrlBase}${BLOCKED_PAGE_PATH}` +
      `?host=${encodeURIComponent(domain)}&pattern=${encodeURIComponent(String(raw))}`;

    rules.push({
      id: id++,
      priority: 1,
      action: {
        type: "redirect",
        redirect: { url: blockedUrl },
      },
      condition: {
        requestDomains: [domain],
        resourceTypes: ["main_frame"],
      },
    });
  }
  return rules;
}

/**
 * Computes the `addRules` / `removeRuleIds` payload for
 * chrome.declarativeNetRequest.updateDynamicRules.
 */
export function diffRules(existingRuleIds, newRules) {
  return {
    addRules: newRules,
    removeRuleIds: Array.from(existingRuleIds ?? []),
  };
}

function isValidDomain(d) {
  // declarativeNetRequest's requestDomains rejects entries with characters
  // outside the host charset. Be conservative: ASCII letters/digits/dots/hyphens.
  return /^[a-z0-9.-]+\.[a-z0-9.-]+$/.test(d);
}

export const RuleBuilderConsts = {
  RULE_BASE_ID,
  BLOCKED_PAGE_PATH,
};
