#!/usr/bin/env bash
# scripts/device_capture.sh
# Captures screenshot and UI hierarchy dump from Samsung Galaxy S24 Ultra (RZCXA0ZAH6Z)

set -euo pipefail

SERIAL="RZCXA0ZAH6Z"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAME="${1:-device_capture}"
PNG_DEST="$PROJECT_ROOT/release/${NAME}.png"
XML_DEST="/tmp/${NAME}.xml"

echo "📸 Capturing screen from $SERIAL..."
adb -s "$SERIAL" shell screencap -p /sdcard/capture_temp.png
adb -s "$SERIAL" pull /sdcard/capture_temp.png "$PNG_DEST" >/dev/null
echo "✅ Screenshot saved to $PNG_DEST"

echo "🔍 Dumping UI hierarchy..."
adb -s "$SERIAL" shell uiautomator dump /sdcard/ui_temp.xml >/dev/null
adb -s "$SERIAL" pull /sdcard/ui_temp.xml "$XML_DEST" >/dev/null
echo "✅ UI Hierarchy saved to $XML_DEST"
