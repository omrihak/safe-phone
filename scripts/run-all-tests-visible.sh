#!/usr/bin/env bash
# Run JVM unit tests + instrumented tests on a windowed emulator (no -no-window).
# Stops any running emulator first so a new visible window is opened.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$SDK/emulator:$PATH"

AVD="${ANDROID_AVD_NAME:-SafePhoneTestAvd}"

echo "=== JVM unit tests (Robolectric / Paparazzi / PolicyEngine) ==="
./gradlew :app:testDebugUnitTest --no-daemon

echo ""
echo "=== Windowed emulator + instrumented tests ==="
if adb devices 2>/dev/null | grep -q "emulator.*device"; then
  echo "Stopping existing emulator so we can start one with a visible window..."
  adb emu kill 2>/dev/null || true
  sleep 3
fi

echo "Starting visible AVD: $AVD (look for the Emulator window)"
emulator -avd "$AVD" -no-snapshot -no-boot-anim &
adb wait-for-device
echo "Waiting for boot..."
for _ in $(seq 1 120); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [[ "$boot" == "1" ]]; then break; fi
  sleep 2
done
adb shell getprop sys.boot_completed | grep -q 1 || { echo "Emulator did not finish booting"; exit 1; }

echo "Running connectedDebugAndroidTest (watch the emulator)..."
./gradlew :app:connectedDebugAndroidTest --no-daemon

echo ""
echo "All tests finished."
