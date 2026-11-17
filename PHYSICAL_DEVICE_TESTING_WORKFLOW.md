# REEL MONSTER — PHYSICAL DEVICE TESTING WORKFLOW & PROTOCOL

**Product:** REEL MONSTER  
**Target Hardware:** Samsung Galaxy S24 Ultra (`SM-S928B`)  
**Explicit USB Serial:** `RZCXA0ZAH6Z`  
**Platform:** Native Android (Kotlin + Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app` (Debug package: `com.reelmonster.app.debug`)  
**Project Root:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/`  
**Adopted:** November 8, 2026  

---

## 1. Device Connection Architecture & Rules

All deployment, debugging, and verification commands **MUST** explicitly target the USB connection serial `RZCXA0ZAH6Z`:

```bash
# Verify USB connection status
adb devices -l
# Expected output:
# RZCXA0ZAH6Z device usb:1122304X product:e3qxins model:SM_S928B device:e3q transport_id:36
```

### Critical Rules:
1. **Explicit Target Serial:** Always prepend `adb -s RZCXA0ZAH6Z` to every command. Never use naked `adb` commands when multiple devices/transports could exist.
2. **Prioritize USB Over Wi-Fi:** If both USB (`RZCXA0ZAH6Z`) and Wi-Fi (`192.168.131.116:5555`) transports appear, strictly route through the USB transport (`-s RZCXA0ZAH6Z`).
3. **No Unsolicited Disconnects:** Never run `adb disconnect` or wipe device configurations unnecessarily.
4. **Data Preservation:** Never execute `adb uninstall` or `pm clear` unless a test specifically tests zero-state installation and data recovery has been confirmed.
5. **Multi-User Isolation:** Samsung Knox / Dual Messenger uses `user 150`. Always target primary personal user `user 0` (`pm list packages --user 0`, `am start --user 0`).

---

## 2. Standard 7-Step Development & Verification Cycle

For every phase (Phase 10, Phase 11, Phase 12, Phase 13, Phase 14, Phase 15, Phase 16):

```
+-----------------------------------------------------------------------------------+
| STEP A: AUDIT                                                                     |
| - Inspect existing code, previous phase report, and dependencies.                 |
| - Identify regressions and confirm preservation of all 10 core tenets.            |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP B: IMPLEMENT                                                                 |
| - Implement only the current phase requirements.                                  |
| - Modular architecture, zero internet permission, 100% offline-first.             |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP C: AUTOMATED TESTING                                                         |
| - Run `./gradlew testDebugUnitTest` (all tests must pass, 0 failures, 0 skipped). |
| - Run `./gradlew assembleDebug` (must compile cleanly).                           |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP D: PRE-PHASE APK BACKUP & INSTALLATION                                       |
| - Copy previous working APK to `release/backups/app-debug-phase<N-1>-working.apk` |
| - Copy fresh APK to `release/app-debug.apk`                                       |
| - Install via `adb -s RZCXA0ZAH6Z install -r release/app-debug.apk`               |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP E: PHYSICAL-DEVICE TESTING                                                   |
| - Launch on Samsung Galaxy S24 Ultra (`SM-S928B`).                                |
| - Verify new feature behavior live on screen.                                     |
| - Check overlay window, background accessibility service, and memory consumption. |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP F: REGRESSION TESTING                                                        |
| - Verify Level 1 Manual Tracking (+1 chomp).                                      |
| - Verify Level 2 Automatic Tracking on Instagram Reels.                           |
| - Verify Level 2 Automatic Tracking on YouTube Shorts.                            |
| - Verify SQLite history and DataStore preferences survive restarts.               |
| - Confirm zero crashes and zero ANRs.                                             |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| STEP G: DOCUMENTATION & TRACKER UPDATE                                            |
| - Create `PHASE_<N>_<TITLE>_REPORT.md` with logs, screenshots, and test metrics.  |
| - Update `PROJECT_TRACKER.md` (mark COMPLETE only if device test passed).         |
| - Update `PROJECT_PLAN.md` and `README.md`.                                       |
+-----------------------------------------------------------------------------------+
```

---

## 3. Physical-Device Testing Schedule & Exit Gates

| Phase | Title | Physical-Device Testing Requirement | Exit Gate Criteria |
| :---: | :---| :---: | :---|
| **Phase 10** | Munchie Personality | **REQUIRED** | Dynamic mood shifts, witty voice reactions, stage expressions, and custom quotes confirmed on `SM-S928B`. |
| **Phase 11** | Actual Viewing Time & Daily Reset | **REQUIRED** | Monotonic viewing timer, live `00:15` pill on overlay, 5-reset allowance, confirmation dialog, and global switch verified on `SM-S928B`. |
| **Phase 12** | Daily Goals & Mindful Limits | **REQUIRED** | Goal setup, progress meters, gentle intervention notifications, and overage alerts tested on `SM-S928B`. |
| **Phase 13** | Focus Mode & Doomscroll Interventions | **REQUIRED** | Scheduled focus sessions, overlay intervention nudges, and pause overrides tested live over Instagram & YouTube. |
| **Phase 14** | XP, Streaks & Achievement Badges | **REQUIRED** | Mindful streak calculation, badge unlock animations, and persistent SQLite XP rewards tested on `SM-S928B`. |
| **Phase 15** | Weekly Intelligence & Binge Diagnostics | **REQUIRED** | 7-day trend analysis, peak binge hour identification, and platform consumption balance audited on `SM-S928B`. |
| **Phase 16** | Digital Wellbeing Calendar | **REQUIRED** | Month-at-a-glance heatmaps, day inspection drawers, and offline calendar reports verified on `SM-S928B`. |

> [!IMPORTANT]
> **Exit Gate Rule:** If physical-device verification is unavailable or cannot be executed at milestone completion, the phase **MUST** be marked as `PENDING PHYSICAL-DEVICE VERIFICATION` in `PROJECT_TRACKER.md`, rather than claiming full completion.

---

## 4. Preservation Tenets Checklist

Before signing off on any phase, execute this 10-point preservation audit:

- [ ] **1. Level 1 Manual Tracking:** Floating overlay and dashboard quick log (+1 Reel) increments count with 350ms debounce.
- [ ] **2. Level 2 Automatic Tracking:** `ReelAccessibilityService` correctly parses Instagram Reels and YouTube Shorts view hierarchies without manual intervention.
- [ ] **3. Instagram Reels Detection:** Layout fingerprinting detects video transitions in `com.instagram.android`.
- [ ] **4. YouTube Shorts Detection:** Deep hierarchy scanner (`d=15–35`) identifies Shorts changes in `com.google.android.youtube`.
- [ ] **5. Floating WindowManager Overlay:** Draggable bubble snaps smoothly to screen edges, animates under 30 FPS, and consumes 0 FPS when idle.
- [ ] **6. SQLite Data Integrity:** Tables (`reels_log`, `viewing_sessions`) preserve previous days' data across phone reboots and app updates.
- [ ] **7. DataStore Preferences:** Tracking mode, themes, haptic settings, and daily reset allowances persist without corruption.
- [ ] **8. 5-Stage Evolution & Expressions:** Baby, Curious, Hungry, Stuffed, and Megachonk correctly evolve at thresholds (0, 6, 16, 31, 51+).
- [ ] **9. 100% Offline Architecture:** Zero network requests, zero `android.permission.INTERNET`, zero analytics SDKs, zero cloud logins.
- [ ] **10. UI & Design System Integrity:** Strict adherence to mint-green palette (`#38C88A`), 12 Stitch screen layouts, and Material 3 design tokens.

---

## 5. Standard Device Automation Scripts

### A. Pre-Flight Device Check
```bash
adb -s RZCXA0ZAH6Z shell getprop ro.product.model
# Output: SM-S928B
adb -s RZCXA0ZAH6Z shell dumpsys display | grep mScreenState
# Output: mScreenState=ON
adb -s RZCXA0ZAH6Z shell settings get secure enabled_accessibility_services | grep reelmonster
# Confirms AccessibilityService is active
adb -s RZCXA0ZAH6Z shell appops get com.reelmonster.app.debug SYSTEM_ALERT_WINDOW
# Confirms WindowManager overlay permission is granted
```

### B. Pre-Phase Backup & Safe Install
```bash
# 1. Backup current working APK from device before installing new build
mkdir -p release/backups
adb -s RZCXA0ZAH6Z pull $(adb -s RZCXA0ZAH6Z shell pm path com.reelmonster.app.debug --user 0 | cut -d: -f2) release/backups/app-debug-previous-device.apk

# 2. Install new APK with -r (reinstall preserving existing SQLite database and DataStore preferences)
adb -s RZCXA0ZAH6Z install -r release/app-debug.apk

# 3. Launch Reel Monster main activity
adb -s RZCXA0ZAH6Z shell am start -n com.reelmonster.app.debug/com.reelmonster.app.MainActivity
```

### C. Live Detection Logcat Streaming
```bash
adb -s RZCXA0ZAH6Z logcat -c
adb -s RZCXA0ZAH6Z logcat -s ReelMonsterDetector:D ViewingSessionTracker:D ReelRepository:D
```

### D. Screenshot & UI Dump Capture
```bash
adb -s RZCXA0ZAH6Z shell screencap -p /sdcard/device_verify.png
adb -s RZCXA0ZAH6Z pull /sdcard/device_verify.png release/device_verify.png
adb -s RZCXA0ZAH6Z shell uiautomator dump /sdcard/ui.xml
adb -s RZCXA0ZAH6Z pull /sdcard/ui.xml /tmp/ui.xml
```
