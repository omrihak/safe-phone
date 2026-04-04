#!/usr/bin/env bash
# Run before Maestro / full enforcement E2E on an emulator or device.
# Usage: ./scripts/e2e-adb-prep.sh [serial]
set -euo pipefail
PKG="com.safephone.focus"
ADB=(adb)
if [[ -n "${1:-}" ]]; then
  ADB=(adb -s "$1")
fi

echo "Granting usage access (appops) for $PKG..."
"${ADB[@]}" shell appops set "$PKG" GET_USAGE_STATS allow || true

echo "Attempting overlay grant (may fail on user builds)..."
"${ADB[@]}" shell pm grant "$PKG" android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null || true

echo "Done. Enable Accessibility for SafePhone manually if testing overlays in a full user journey."
