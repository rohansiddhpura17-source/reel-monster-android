#!/usr/bin/env bash
# scripts/device_check.sh
# Verifies connection, state, and permissions on Samsung Galaxy S24 Ultra (RZCXA0ZAH6Z)

set -euo pipefail

SERIAL="RZCXA0ZAH6Z"
PACKAGE="com.reelmonster.app.debug"

echo "=========================================================="
echo " Reel Monster — Samsung S24 Ultra Pre-Flight Check"
echo " Target Serial: $SERIAL"
echo "=========================================================="

# 1. Verify ADB connection
if ! adb devices -l | grep -q "$SERIAL"; then
    echo "❌ ERROR: Device $SERIAL not found in 'adb devices -l'."
    adb devices -l
    exit 1
fi
echo "✅ USB Connection verified: $SERIAL is attached."

# 2. Check Device Model and OS
MODEL=$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')
ANDROID_VER=$(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')
SDK_VER=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
echo "✅ Hardware: $MODEL (Android $ANDROID_VER, SDK $SDK_VER)"

# 3. Check Screen State
SCREEN_ON=$(adb -s "$SERIAL" shell dumpsys display | grep -c "mScreenState=ON" || true)
if [ "$SCREEN_ON" -ge 1 ]; then
    echo "✅ Display is awake (mScreenState=ON)."
else
    echo "⚠️ WARNING: Display appears to be off or asleep."
fi

# 4. Check Package Installation
if adb -s "$SERIAL" shell pm list packages --user 0 | grep -q "$PACKAGE"; then
    LAST_UPDATE=$(adb -s "$SERIAL" shell dumpsys package "$PACKAGE" | grep "lastUpdateTime" | head -n 1 | tr -d '\r')
    echo "✅ Package $PACKAGE is installed ($LAST_UPDATE)."
else
    echo "⚠️ NOTICE: Package $PACKAGE is not currently installed on device."
fi

# 5. Check Accessibility Service
A11Y=$(adb -s "$SERIAL" shell settings get secure enabled_accessibility_services | tr -d '\r')
if echo "$A11Y" | grep -q "ReelAccessibilityService"; then
    echo "✅ AccessibilityService is ENABLED."
else
    echo "⚠️ WARNING: ReelAccessibilityService is not enabled in Android Settings."
fi

# 6. Check Overlay Permission
OVERLAY=$(adb -s "$SERIAL" shell appops get "$PACKAGE" SYSTEM_ALERT_WINDOW 2>/dev/null | tr -d '\r' || true)
if echo "$OVERLAY" | grep -q "allow"; then
    echo "✅ SYSTEM_ALERT_WINDOW (Overlay) permission is GRANTED."
else
    echo "⚠️ WARNING: SYSTEM_ALERT_WINDOW is not yet granted."
fi

echo "=========================================================="
echo " Device is ready for Reel Monster workflow."
echo "=========================================================="
