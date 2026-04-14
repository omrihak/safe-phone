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

**Required for [.github/workflows/android-branch-apk.yml](../.github/workflows/android-branch-apk.yml):** all four signing secrets below must be set. The workflow fails fast if any are missing, so every published OTA APK uses the same stable release key (ephemeral CI debug keystores would change every run and break updates).

| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | Base64-encoded PKCS12 or JKS keystore file. |
| `KEYSTORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Signing key alias. |
| `KEY_PASSWORD` | Key password. |
| `COPILOT_ASSIGN_PAT` | Optional fine-grained PAT so [.github/workflows/vibe-coding-ota-hints.yml](../.github/workflows/vibe-coding-ota-hints.yml) can **assign Copilot coding agent** on new vibe issues (see [Automatic Copilot assignment](#automatic-copilot-assignment)). |
| `COPILOT_MERGE_PAT` | Optional fine-grained PAT used by [.github/workflows/auto-merge.yml](../.github/workflows/auto-merge.yml) to **auto-merge PRs** after Android CI passes (see [Automatic PR merge](#automatic-pr-merge)). If omitted the workflow falls back to `GITHUB_TOKEN`, which may be blocked by branch protection rules. |

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

1. Configure all four keystore secrets on GitHub (required for the branch APK workflow; see [Repository secrets](#repository-secrets)).
2. Push your branch; confirm the workflow creates or updates the **internal-…** prerelease under Releases.
3. Install **once** with the same signing key CI uses (download the release asset or the workflow artifact):

   ```bash
   adb install -r app-internal-release.apk
   ```

4. Later pushes with a higher `versionCode` (`github.run_number`) are picked up by the app (foreground download notification, then system install UI as required by Android).

**Private repository**: release asset URLs still require **read access** to the repo; the device must be able to fetch HTTPS as an anonymous user only if the repo is **public**, or you would need authenticated downloads (not implemented here).

## Troubleshooting: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

Android returns this when the downloaded APK is **signed with a different certificate** than the build currently installed (`com.safephone.focus`). The in-app updater only checks `versionCode` and SHA-256; it cannot detect this until the system installer runs.

**Common causes**

- The app on the device was installed from a **different source** than today’s OTA (e.g. local `internalRelease` with `keystore.properties` vs CI before secrets were configured, or `internalDebug` vs `internalRelease`).
- CI or your machine once used the **debug** fallback (no `upload` keystore) and later switched to a **stable** keystore (or the reverse).

**Fix (align device with the signing key you will keep using)**

1. Ensure GitHub Actions has all four signing secrets configured so every branch APK is signed the same way (see above).
2. **Uninstall** SafePhone from the device once (this clears the old signing lineage).
3. Install the latest `app-internal-release.apk` from the matching **`internal-…`** prerelease (or workflow artifact) for your branch.
4. Confirm future updates: `apksigner verify --print-certs` on the installed base APK and on the release asset should show the **same** signer certificates.

For local `assembleInternalRelease` / `installInternalRelease`, use the **same** `keystore.properties` (or env vars) as CI so your device never diverges from OTA binaries.

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

## Automatic Copilot assignment

The same workflow **assigns [GitHub Copilot coding agent](https://docs.github.com/en/copilot/using-github-copilot/coding-agent/using-copilot-to-work-on-an-issue)** when an issue is opened and either:

- the body contains `Submitted from SafePhone (app):`, or  
- the issue has the **`vibe-coding`** label (including the feature request template).

**Opt out** for a specific issue: add the label **`no-copilot`** (workflow skips assignment if that label is present).

### Repository secret `COPILOT_ASSIGN_PAT`

GitHub’s API often **does not** treat the default `GITHUB_TOKEN` as sufficient to start the coding agent. Create a **fine-grained personal access token** (your user → **Settings → Developer settings → Fine-grained tokens**):

| Permission        | Access            |
|-------------------|-------------------|
| Metadata          | Read              |
| Actions           | Read and write    |
| Contents          | Read and write    |
| Issues            | Read and write    |
| Pull requests     | Read and write    |

Restrict the token to this repository, then add it as repository secret **`COPILOT_ASSIGN_PAT`**.

The workflow uses that token (when non-empty) to call the REST API: assignee **`copilot-swe-agent[bot]`** plus `agent_assignment` targeting this repo and the **default branch** (usually `main`). Copilot opens a PR; the PR is then **automatically merged** by the auto-merge workflow once Android CI passes (see [Automatic PR merge](#automatic-pr-merge)).

If assignment fails (missing PAT, Copilot not enabled for the repo/org, etc.), the workflow posts a short comment with troubleshooting steps.

**Requirements on GitHub**: Copilot **coding agent** / cloud agent must be allowed for the repository and your plan. If assignees do not list Copilot, see GitHub’s docs for enabling the agent on the repo.

## Automatic PR merge

Workflow: [.github/workflows/auto-merge.yml](../.github/workflows/auto-merge.yml)

After the **Android CI** workflow completes successfully on a pull request, this workflow automatically **squash-merges** the PR if:

- The PR targets the default branch (`main` / `master`).
- The PR comes from the same repository (forks are skipped for safety).

This applies to **all** PRs — including those opened by the Copilot coding agent — so any PR that passes CI is merged without manual intervention.

> **Note**: Auto-merging skips human code review. For this repository that is an intentional trade-off (rapid iteration). If you ever need a manual review for a specific PR, temporarily disable this workflow or close and re-open the PR after disabling it.

### Repository secret `COPILOT_MERGE_PAT`

If your repository has **branch protection rules** that block merges (e.g. required reviewers or required status checks that `GITHUB_TOKEN` cannot bypass), create an additional **fine-grained personal access token**:

| Permission     | Access          |
|----------------|-----------------|
| Metadata       | Read            |
| Contents       | Read and write  |
| Pull requests  | Read and write  |

Restrict the token to this repository, then add it as repository secret **`COPILOT_MERGE_PAT`**. The workflow prefers this token over the default `GITHUB_TOKEN` when the secret is non-empty.
