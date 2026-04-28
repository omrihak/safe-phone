# SafePhone Chrome extension

A read-only companion to the SafePhone Android app. Pulls the same policy JSON
the Android app pushes to its GitHub Gist and enforces the **browser-relevant**
slice locally (domain blocks + the social-media category, gated by the same
profile / `enforcementEnabled` / `activeDaysOfWeek` flags). Adds a local
"take a break" action that mirrors `BreakManager` semantics from the Android
side; break state is intentionally **not** synced back to the gist.

## What it enforces (and what it ignores)

| Synced policy field | Enforced in extension? |
|---|---|
| `domainRules` (entries with `isAllowlist = false`) | yes — redirect to `blocked.html` |
| `prefs.socialMediaCategoryBlocked` | yes — adds the 15 social domains |
| `prefs.enforcementEnabled` | yes — disables all blocks |
| `prefs.activeDaysOfWeek` | yes — disables on off-days |
| `activeProfile.softEnforcement` | yes — disables hard blocks |
| `breakPolicy.maxBreaksPerDay` / `breakDurationMinutes` | yes — local break flow |
| `blockedApps`, `appBudgets`, `calendarKeywords`, `mindfulFrictionPackages` | no — no browser equivalent |
| `activeProfile.strictBrowserLock`, `enforceGrayscale` | no — no browser equivalent |
| Partner-block alerts, system monochrome | no |

## Building locally

```bash
cd chrome-extension
npm install

# Bake the GitHub PAT + Gist id into src/generated/config.js (gitignored).
# The same env vars the Android workflow uses also feed this:
SAFEPHONE_CLOUD_SYNC_DEFAULT_GITHUB_TOKEN=github_pat_xxx \
SAFEPHONE_CLOUD_SYNC_DEFAULT_GIST_ID=abc123 \
  npm run build:config

npm test
npm run package    # produces dist/safephone-chrome-extension.zip
```

Then load the unpacked `chrome-extension/` folder via `chrome://extensions`
(Developer mode → Load unpacked) or install the zipped artifact.

## CI

`.github/workflows/chrome-extension-zip.yml` mirrors the Android branch APK
workflow: it consumes the same `CLOUD_SYNC_DEFAULT_GITHUB_TOKEN` and
`CLOUD_SYNC_DEFAULT_GIST_ID` repository secrets, runs `npm test`, builds the
config, packages the zip, and attaches it to the matching `internal-<branch>`
GitHub release alongside the APK.

## Testing

`npm test` runs vitest on three suites that mirror the Android unit tests:

- `domainMatcher.test.js` — same patterns asserted by `PolicyEngineTest`.
- `breakManager.test.js` — same boundary cases as `BreakManagerTest`.
- `ruleBuilder.test.js` — verifies the snapshot → declarativeNetRequest
  translation.
