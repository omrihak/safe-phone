#!/usr/bin/env bash
# Starts a visible Android emulator (if none running), waits for boot, runs instrumented tests.
# Watch the emulator window while Gradle drives the UI.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$SDK/emulator:$PATH"

AVD="${ANDROID_AVD_NAME:-SafePhoneTestAvd}"

if ! adb devices 2>/dev/null | grep -q "emulator.*device"; then
  echo "No emulator online — starting visible AVD: $AVD"
  emulator -avd "$AVD" -no-snapshot -no-boot-anim &
  adb wait-for-device
  echo "Waiting for boot..."
  for _ in $(seq 1 120); do
    boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [[ "$boot" == "1" ]]; then break; fi
    sleep 2
  done
  adb shell getprop sys.boot_completed | grep -q 1 || { echo "Emulator did not finish booting"; exit 1; }
else
  echo "Using already-running emulator."
fi

echo "Running connected tests (UI will animate on the emulator)..."
./gradlew :app:connectedStandardDebugAndroidTest --no-daemon
