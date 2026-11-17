#!/usr/bin/env bash
# scripts/device_install.sh
# Safely backs up currently working APK from device and installs the new build

set -euo pipefail

SERIAL="RZCXA0ZAH6Z"
PACKAGE="com.reelmonster.app.debug"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_SRC="$PROJECT_ROOT/release/app-debug.apk"
BACKUP_DIR="$PROJECT_ROOT/release/backups"

echo "=========================================================="
echo " Reel Monster — Safe APK Backup & Deployment"
echo " Target Serial: $SERIAL"
echo "=========================================================="

mkdir -p "$BACKUP_DIR"

# 1. Backup existing installed APK if present
if adb -s "$SERIAL" shell pm list packages --user 0 | grep -q "$PACKAGE"; then
    REMOTE_PATH=$(adb -s "$SERIAL" shell pm path "$PACKAGE" --user 0 | head -n 1 | tr -d '\r' | cut -d: -f2)
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    BACKUP_FILE="$BACKUP_DIR/app-debug-backup-$TIMESTAMP.apk"
    echo "📦 Backing up existing device APK to: $BACKUP_FILE"
    adb -s "$SERIAL" pull "$REMOTE_PATH" "$BACKUP_FILE" >/dev/null
    echo "✅ Backup preserved."
fi

# 2. Check source APK
if [ ! -f "$APK_SRC" ]; then
    echo "❌ ERROR: Target APK not found at $APK_SRC"
    echo "Run './gradlew assembleDebug && cp ... $APK_SRC' first."
    exit 1
fi

FILE_SIZE=$(ls -lh "$APK_SRC" | awk '{print $5}')
echo "📲 Installing fresh APK ($FILE_SIZE)..."

# 3. Streamed install with -r (reinstall preserving data)
adb -s "$SERIAL" install -r "$APK_SRC"
echo "✅ Installation complete."

# 4. Launch main activity
echo "🚀 Launching Reel Monster on device..."
adb -s "$SERIAL" shell am start -n "$PACKAGE/com.reelmonster.app.MainActivity"

echo "=========================================================="
echo " Reel Monster is live on Samsung S24 Ultra."
echo "=========================================================="
