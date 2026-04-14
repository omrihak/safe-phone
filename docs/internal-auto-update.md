# Internal branch APK builds and over-the-air updates

This project can publish a signed **internal** release APK on every branch push and have the **internal** app flavor check for updates in the background.

## GitHub Actions

Workflow: [.github/workflows/android-branch-apk.yml](../.github/workflows/android-branch-apk.yml)

- **Trigger**: push to any branch (except `dependabot/**`).
- **Workflow artifacts**: each run uploads `app-internal-release.apk` and `manifest.json` for debugging.
- **GitHub Releases**: each branch gets a **prerelease** with a stable tag `internal-<sanitized-branch-name>`. Every push **replaces** the APK and `manifest.json` on that release (`gh release upload --clobber`).

The app downloads the manifest from:

`https://github.com/<owner>/<repo>/releases/download/internal-<sanitized>/manifest.json`

(`INTERNAL_UPDATE_BASE_URL` is `https://github.com/<owner>/<repo>`, embedded at build time from CI.)

## Repository secrets

| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | Base64-encoded PKCS12 or JKS keystore file. If omitted, the APK is signed with the debug keystore (not suitable for in-place upgrades on a device). |
| `KEYSTORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Signing key alias. |
| `KEY_PASSWORD` | Key password. |

No object-storage or `INTERNAL_UPDATE_PUBLIC_BASE_URL` secret is required when using GitHub Releases.

**Permissions**: the workflow uses `permissions: contents: write` so `github.token` can create/update releases.

## Gradle flavors

- **`standard`** (default): no auto-update client (`BuildConfig.ENABLE_INTERNAL_AUTO_UPDATE = false`).
- **`internal`**: embeds `INTERNAL_UPDATE_BASE_URL` and `INTERNAL_UPDATE_TRACK_REF` from Gradle properties; enables WorkManager checks and install flow.

CI passes:

- `-PappVersionCode=<github.run_number>` — must increase on every installable build.
- `-PappVersionName=...`
- `-Psafephone.internalUpdateBaseUrl=https://github.com/<owner>/<repo>` (set automatically in the workflow).
- `-Psafephone.internalUpdateTrackRef=<sanitized branch>` — must match the release tag suffix (`internal-<this value>`).

Local **internal** builds against your repo:

```bash
./gradlew :app:installInternalRelease \
  -Psafephone.internalUpdateBaseUrl="https://github.com/YOUR_ORG/safe-phone" \
  -Psafephone.internalUpdateTrackRef="your-sanitized-branch"
```

Use the same **sanitized** string the workflow uses for your branch (letters, digits, `.`, `_`, `-`; other characters become `-`, max 120 chars).

Release keystore locally: create `keystore.properties` in the **repository root** (gitignored) with:

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## First install on your phone

1. Configure keystore secrets on GitHub (recommended).
2. Push your branch; confirm the workflow creates or updates the **internal-…** prerelease under Releases.
3. Install **once** with the same signing key CI uses (download the release asset or the workflow artifact):

   ```bash
   adb install -r app-internal-release.apk
   ```

4. Later pushes with a higher `versionCode` (`github.run_number`) are picked up by the app (foreground download notification, then system install UI as required by Android).

**Private repository**: release asset URLs still require **read access** to the repo; the device must be able to fetch HTTPS as an anonymous user only if the repo is **public**, or you would need authenticated downloads (not implemented here).

## Standard CI tasks

After adding flavors, local and CI commands use the **standard** variant, for example:

- `./gradlew :app:testStandardDebugUnitTest`
- `./gradlew :app:connectedStandardDebugAndroidTest`
- `./gradlew :app:installStandardDebug`

## Vibe coding (app → GitHub → OTA)

The **internal** home screen can show a **Vibe coding** card when `BuildConfig` knows your GitHub repo:

- **CI internal builds**: `safephone.internalUpdateBaseUrl` is set to `https://github.com/<owner>/<repo>` in [.github/workflows/android-branch-apk.yml](../.github/workflows/android-branch-apk.yml), so `GITHUB_ISSUES_OWNER` / `GITHUB_ISSUES_REPO` are derived automatically and the card appears.
- **Local standard builds**: set `safephone.githubIssueOwner` and `safephone.githubIssueRepo` in `gradle.properties` (see comments there), or use an **internal** Gradle command that already passes `safephone.internalUpdateBaseUrl`.

Submitting **Open in GitHub** creates a labeled issue; [.github/workflows/vibe-coding-ota-hints.yml](../.github/workflows/vibe-coding-ota-hints.yml) comments with OTA steps. Implement the feature (e.g. Copilot coding agent or locally), then **push to the same branch** encoded in `-Psafephone.internalUpdateTrackRef=…` for your install so the next APK lands on the matching `internal-…` release and the in-app updater can install it.
