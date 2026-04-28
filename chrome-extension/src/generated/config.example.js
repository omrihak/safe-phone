// Example of the file that scripts/build-config.js produces. The real
// config.js is gitignored and overwritten on every build:
//
//   SAFEPHONE_CLOUD_SYNC_DEFAULT_GITHUB_TOKEN=github_pat_xxx \
//   SAFEPHONE_CLOUD_SYNC_DEFAULT_GIST_ID=abc123 \
//     npm run build:config
//
// gistClient.js imports the two constants below directly. Without a real
// config.js the extension installs but every pull fails with "no token", which
// matches the Android app's behaviour when the BuildConfig fields are empty.
export const CLOUD_SYNC_DEFAULT_GITHUB_TOKEN = "";
export const CLOUD_SYNC_DEFAULT_GIST_ID = "";
