# REEL MONSTER — PHASE 11: ACTUAL VIEWING TIME + DAILY RESET + GLOBAL TRACKING CONTROL
## Technical Development, Architecture & Verification Report

**Product:** REEL MONSTER  
**Platform:** Native Android (Kotlin + Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app` (Debug: `com.reelmonster.app.debug`)  
**Project Root:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster`  
**Phase:** Phase 11 — Actual Viewing Time, Daily Reset & Global Tracking Control  
**Date:** November 8, 2026  
**Status:** **100% COMPLETED & VERIFIED** (167 / 167 Automated Tests Passing, APK Assembled)  

---

## 1. Executive Summary

Phase 11 introduces three core interconnected functional capabilities to Reel Monster while strictly preserving the Level 2 Automatic Detection engine, the 5-stage monster evolution, the floating overlay, and the 100% offline architectural tenet:

1. **Actual Per-Reel Viewing Time Measurement:**
   - Replaced static 1.5-minute estimations with precise, monotonic clock-measured durations (`SystemClock.elapsedRealtime()`).
   - Implemented `ViewingSessionTracker` singleton managing active session lifecycles across Instagram Reels and YouTube Shorts.
   - Non-destructive SQLite schema upgrade (`DATABASE_VERSION = 2`) introducing `TABLE_VIEWING_SESSIONS`.
   - Comprehensive edge-case handling: app switching, screen off, pause/resume, global OFF, and duplicate scroll suppression. Zero drift during screen-off.
   - Live elapsed timer pill (e.g. `00:15`) rendered directly on the floating Munchie bubble during active viewing, with zero idle background ticker loops when inactive.

2. **Five Daily Resets of Today's Tracking Statistics:**
   - Capped at strictly 5 successful resets per local calendar day (`MAX_DAILY_RESETS = 5`).
   - Double-confirmation dialog preventing accidental resets:
     - Title: `"Restart today's tracking?"`
     - Body: `"Today's count and viewing-time totals will reset. Previous days' history will remain saved."`
     - Actions: `"Cancel"` and `"Restart Today"`.
   - Atomically clears today's counts, today's viewing session durations, and deduplication cache, while safely preserving all historical days.
   - Allowance automatically renews to 5 resets at local midnight (`YYYY-MM-DD` boundary) and persists across phone restarts.

3. **Global Tracking ON/OFF Master Switch:**
   - Prominently placed master switch in Dashboard and Settings.
   - **ON:** Automatic detection runs smoothly; new viewing sessions start; manual quick-log buttons enabled; floating monster shows active tracking badges.
   - **OFF:** Halts automatic reel detection immediately; safely closes active viewing sessions; disables manual quick log buttons; floating monster displays neutral `"OFF"` badge.
   - Preserves user's configured mode (`LEVEL_1_MANUAL` vs `LEVEL_2_AUTOMATIC`) across toggles and overrides pause/resume.

---

## 2. Architecture & Data Flow

```
                      +------------------------------------------+
                      |       Android AccessibilityService       |
                      |        (ReelAccessibilityService)        |
                      +------------------------------------------+
                                           |
                    [Reel Swiped / Confirmed Candidate Detected]
                                           |
                                           v
+------------------------+      +---------------------+      +------------------------+
| UserPreferencesManager |<---->| ReelRepository      |<---->| ViewingSessionTracker  |
| - Global Tracking (ON) |      | - todayCount (0)    |      | - Active candidate     |
| - Daily Resets (x/5)   |      | - todayViewingTime  |      | - Monotonic start time |
| - Pause / Resume       |      | - todayAvgDuration  |      | - Live timer ticker    |
+------------------------+      +---------------------+      +------------------------+
                                           |                               |
                       +-------------------+------------------+            |
                       |                                      |            |
                       v                                      v            v
             +--------------------+               +----------------------------------+
             | ReelDbHelper (v2)  |               | Floating Monster WindowManager   |
             | - reels_log        |               | Overlay (Draggable Bubble + HUD) |
             | - viewing_sessions |               | - Count Badge                    |
             +--------------------+               | - Live Timer Pill (00:15)        |
                                                  | - Global OFF / Pause Status      |
                                                  +----------------------------------+
```

---

## 3. Detailed Component Breakdown

### 3.1 Domain Models (`ViewingSession.kt`)
- `ViewingSession`: Immutable entity holding `id`, `targetApp` (`INSTAGRAM` | `YOUTUBE`), `startTimestamp`, `endTimestamp`, `durationMs`, `completionStatus` (`COMPLETED`, `INTERRUPTED_APP_SWITCH`, `INTERRUPTED_SCREEN_OFF`, `INTERRUPTED_PAUSED`, `INTERRUPTED_GLOBAL_OFF`, `INTERRUPTED_RESET`), and `sessionDate` (`YYYY-MM-DD`).
- `TodayViewingStats`: Consolidated read-only aggregate holding `totalDurationMs`, `instagramDurationMs`, `youtubeDurationMs`, and `averageDurationMs`.
- `DailyResetResult`: Immutable operation result containing `success: Boolean`, `remainingResets: Int`, and human-readable `message: String`.

### 3.2 Time Formatting Utilities (`DateTimeUtils.kt`)
- `formatDurationMs(durationMs: Long)`: Context-aware duration formatter producing clean strings:
  - `< 60s`: `"45s"`
  - `60s – 3599s`: `"2m 5s"`
  - `≥ 3600s`: `"1h 2m"`
  - `0s`: `"0s"`
- `formatTimerMmSs(durationMs: Long)`: Formats elapsed active viewing duration into clean digital clock format (`"00:15"`, `"02:45"`).

### 3.3 Database Migration & Repository (`ReelDbHelper.kt` & `ReelRepository.kt`)
- Non-destructive SQLite migration from `DATABASE_VERSION = 1` to `DATABASE_VERSION = 2`:
  ```sql
  CREATE TABLE viewing_sessions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      target_app TEXT NOT NULL,
      start_timestamp INTEGER NOT NULL,
      end_timestamp INTEGER NOT NULL,
      duration_ms INTEGER NOT NULL,
      completion_status TEXT NOT NULL,
      session_date TEXT NOT NULL
  );
  CREATE INDEX idx_viewing_sessions_date ON viewing_sessions(session_date);
  ```
- Fast indexed queries:
  - `getTodayViewingTimeMs()`: Sum of `duration_ms` for current date.
  - `getTodayPlatformViewingTimeMs()`: Pair of `(instagramMs, youtubeMs)`.
  - `getAverageViewingDurationMs()`: Arithmetic average of completed and interrupted sessions for current date.
  - `getViewingTimeForDate(dateStr)`: Historical lookup; returns `null` if no records exist (triggering the "Unavailable" state in the UI).
- Immediate, synchronized in-memory StateFlows backed by SQLite & DataStore:
  - `todayViewingTimeMs: StateFlow<Long>`
  - `todayPlatformViewingTimeMs: StateFlow<Pair<Long, Long>>`
  - `todayAverageDurationMs: StateFlow<Long>`
  - `remainingDailyResets: StateFlow<Int>`
  - `isGlobalTrackingEnabled: StateFlow<Boolean>`

### 3.4 Monotonic Session Tracker (`ViewingSessionTracker.kt`)
- Implements monotonic clock timing using `SystemClock.elapsedRealtime()` for duration delta measurement, completely eliminating clock skew, NTP sync jumps, and timezone adjustments.
- Wall-clock timestamps (`System.currentTimeMillis()`) are reserved exclusively for start/end record storage and calendar date mapping (`YYYY-MM-DD`).
- Configurable clock lambdas (`clockElapsedRealtime`, `clockWallTime`) ensure instantaneous, non-blocking unit testing without `Thread.sleep`.
- Session lifecycle handlers:
  - `onReelConfirmed(candidate, repo)`: Atomically closes and persists previous reel session as `COMPLETED`, then initializes new active session.
  - `onAppBackgrounded(repo)`: Closes session with `INTERRUPTED_APP_SWITCH`.
  - `onScreenOff(repo)`: Closes session with `INTERRUPTED_SCREEN_OFF`. Any monotonic time elapsed while the screen is off is strictly discarded.
  - `onTrackingPaused(repo)`: Closes session with `INTERRUPTED_PAUSED`.
  - `onGlobalTrackingDisabled(repo)`: Closes session with `INTERRUPTED_GLOBAL_OFF`.
  - `liveElapsedTimerMs`: Coroutine ticker running strictly while `isActive == true`, automatically stopping when idle to guarantee 0 FPS background battery consumption.

### 3.5 Daily Reset Engine (`UserPreferencesManager.kt` & `ReelRepository.kt`)
- Enforces strict cap of `MAX_DAILY_RESETS = 5` per calendar day.
- Midnight renewal: Stores `daily_reset_date` string alongside `daily_reset_count`. When queried with a new date string, returns 0 usage without requiring background workers or alarms.
- `performDailyReset()`:
  1. Verifies `remainingResets > 0`.
  2. Safely ends active viewing session via `ViewingSessionTracker`.
  3. Deletes today's records from `reels_log` and `viewing_sessions` tables.
  4. Flushes `DeduplicationEngine` candidate history to allow immediate re-tracking.
  5. Increments daily reset count in DataStore and local cache.
  6. Refreshes repository StateFlows (`todayCount` becomes 0, `todayViewingTimeMs` becomes 0).

### 3.6 Floating Monster Overlay & Dashboard Integration
- **Compact Floating Bubble:**
  - When Global Tracking is OFF: Displays `"OFF"` badge in place of reel count.
  - When actively viewing: Displays a glowing live timer pill (`00:15`) next to the monster.
  - When idle: Live timer pill is cleanly hidden; ticker job is cancelled.
- **Expanded Mini HUD Panel:**
  - Displays `"Actual Viewing Time"` alongside reel count.
  - Shows active reel duration timer and global tracking status badge.
- **Dashboard Screen (`DashboardScreen.kt`):**
  - **Global Tracking Master Card:** Prominent switch card with live status indicator ("Active & Monitoring" vs "All Tracking Disabled").
  - **Actual Viewing Time StatCard:** Features primary elapsed time (`"14m 30s"`), subtitle with average per-reel duration (`"Avg: 48s / reel"`), and 3D touch tilt.
  - **Platform Duration Cards:** Displays actual measured viewing time for Instagram Reels and YouTube Shorts separately.
  - **Daily Reset Allowance Card:** Displays remaining reset allowance (`"4 of 5 resets remaining today"`), informative caption, and "Restart Today" trigger button.
  - **Confirmation Dialog:** Clean Material 3 alert with destructive action style, clarifying that past history is preserved.

---

## 4. Automated Test Suite Results

The entire unit test suite was executed via Gradle with 100% success rate:
- **Total Tests Executed:** 167
- **Passed:** 167 (100%)
- **Failed:** 0
- **Skipped:** 0
- **Execution Time:** ~12.1 seconds

### Phase 11 Test Breakdown (`Phase11ViewingTimeAndResetTest.kt`):

| # | Test Name | Target Behavior | Result |
|---|:---|:---|:---:|
| 1 | `test01_initialTodayViewingTimeIsZero` | Verifies initial state returns 0ms total, (0,0) platforms, and 0ms avg. | **PASSED** |
| 2 | `test02_singleCompletedSessionDuration` | Monotonic clock correctly computes 15s session duration on complete. | **PASSED** |
| 3 | `test03_multipleConsecutiveSessionsAccumulateTime` | Transitions between reels automatically accumulate 10s + 20s + 15s = 45s. | **PASSED** |
| 4 | `test04_platformBreakdownSeparatesInstagramAndYouTube` | Separates Instagram (25s) and YouTube (40s) durations accurately. | **PASSED** |
| 5 | `test05_averageDurationCalculation` | Computes arithmetic mean across 3 sessions (10s, 20s, 30s -> 20s avg). | **PASSED** |
| 6 | `test06_appSwitchingClosesSessionWithInterruptedStatus` | App exit interrupts session with `INTERRUPTED_APP_SWITCH`. | **PASSED** |
| 7 | `test07_screenOffInterruptsViewingSession` | Screen off halts timer; monotonic elapsed time during sleep is discarded. | **PASSED** |
| 8 | `test08_trackingPauseInterruptsActiveSession` | User pause closes active session with `INTERRUPTED_PAUSED`. | **PASSED** |
| 9 | `test09_globalTrackingOffInterruptsActiveSession` | Disabling global switch halts active session with `INTERRUPTED_GLOBAL_OFF`. | **PASSED** |
| 10 | `test10_historicalDaysWithoutSessionsReturnUnavailableOrNull` | Past dates without session data return null / empty list. | **PASSED** |
| 11 | `test11_firstDailyResetSucceedsAndResetsCountAndDuration` | First reset clears count and viewing time to 0; decrements allowance to 4. | **PASSED** |
| 12 | `test12_fiveConsecutiveDailyResetsExhaustAllowance` | 5 resets in succession exhaust daily allowance to exactly 0. | **PASSED** |
| 13 | `test13_sixthDailyResetIsRejected` | 6th attempt is rejected with informative limit message. | **PASSED** |
| 14 | `test14_dailyResetPreservesPreviousDaysData` | Resetting today preserves yesterday's count, time, and session records. | **PASSED** |
| 15 | `test15_dailyResetEndsActiveViewingSessionSafely` | Reset terminates active viewing session without leaking memory or time. | **PASSED** |
| 16 | `test16_dailyResetAllowanceRenewsOnNextDate` | Calendar date progression automatically renews allowance to 5. | **PASSED** |
| 17 | `test17_dailyResetClearsDeduplicationCache` | Resetting flushes dedup cache so previously viewed reels can be re-counted. | **PASSED** |
| 18 | `test18_clearAllDataResetsDailyResetUsage` | Full database wipe restores daily reset allowance to full 5. | **PASSED** |
| 19 | `test19_globalSwitchOffRejectsAutomaticDetection` | Global switch OFF blocks automatic reel logging. | **PASSED** |
| 20 | `test20_globalSwitchOffRejectsManualLogging` | Global switch OFF blocks manual quick log button chomps. | **PASSED** |
| 21 | `test21_globalSwitchOverridesPauseResume` | Global switch OFF overrides pause/resume state (`isTrackingActive = false`). | **PASSED** |
| 22 | `test22_globalSwitchPreservesUserTrackingMode` | Toggling global switch preserves configured `LEVEL_2_AUTOMATIC` mode. | **PASSED** |
| 23 | `test23_reenablingGlobalSwitchResumesTracking` | Toggling global switch back ON instantly resumes logging. | **PASSED** |

---

## 5. Artifact & Build Verification

- **Debug APK Location:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/release/app-debug.apk`
- **File Size:** `19 MB` (`20,217,232 bytes`, MD5: `96a836a11549a4ce14b454fdc1d998f0`)
- **Compilation Toolchain:** Gradle 9.5.0, AGP 9.0.1, Kotlin 2.3.20, Java 21
- **Database Schema Version:** `2` (includes `TABLE_VIEWING_SESSIONS` and index)
- **Target OS:** Android 8.0+ (API 26 to API 36 / 37)

---

## 6. Physical-Device Testing Evidence on Samsung Galaxy S24 Ultra (`SM-S928B`)

- **Device Serial:** `RZCXA0ZAH6Z` (Targeted via USB)
- **OS Version:** Android 17 (SDK 37)
- **Package Installed:** `com.reelmonster.app.debug` (`lastUpdateTime=2026-11-08 16:36:39`)
- **Accessibility Service:** `com.reelmonster.app.debug/com.reelmonster.app.service.tracking.ReelAccessibilityService` (Active & Bound)
- **Overlay Permission (`SYSTEM_ALERT_WINDOW`):** `allow`

### Physical Verification Steps & Results:

1. **Clean Installation & Launch:**
   - Pre-phase APK backed up to `release/backups/app-debug-device-verified-phase8b.apk`.
   - New Phase 11 APK installed with `adb -s RZCXA0ZAH6Z install -r release/app-debug.apk`.
   - App launched cleanly via `am start`; zero fatal exceptions, zero ANRs.
2. **Dashboard UI Verification:**
   - Global Tracking Master Card rendered: `"Global Tracking Master Switch"` / `"Active • Monitoring enabled per mode"`.
   - Actual Viewing Time StatCard rendered: `"Actual Viewing Time"` / `"0s"` / `"Awaiting sessions"`.
   - Daily Reset Card rendered: `"Today's Tracking Reset"` / `"5 of 5 resets remaining today"` / `"Restart Today"`.
3. **Daily Reset Double-Confirmation Dialog:**
   - Tapped `"Restart Today"` on device; Material 3 confirmation dialog rendered immediately.
   - Title: `"Restart today's tracking?"`
   - Body: `"Today's count and viewing-time totals will reset. Previous days' history will remain saved."`
   - Tapped `"Restart Today"` button on dialog; today's counts reset to `0`, and allowance decremented to `"4 of 5 resets remaining today"`.
4. **Global Tracking Master Switch Toggle:**
   - Tapped switch at `(1273, 635)` to toggle OFF.
   - Subtitle changed to: `"Disabled • All tracking and timers stopped"`.
   - Status badge changed to: `"GLOBAL TRACKING OFF"`.
   - Mascot quote updated to: `"Munchie is resting calmly. Global tracking is turned OFF."`.
   - Quick log button disabled: `"Tracking Disabled (Turn ON Master Switch)"`.
   - Tapped switch again to re-enable; active monitoring resumed instantly.
5. **Level 2 Live YouTube Shorts Automatic Detection & Actual Viewing Time:**
   - Switched to YouTube Shorts (`com.google.android.youtube`); candidate detected by `ReelAccessibilityService`:
     ```text
     D ReelMonsterDetector: TARGET_APP_DETECTED | app=YOUTUBE (com.google.android.youtube)
     D ReelMonsterDetector: VIEWER_STATE | app=YOUTUBE | active=true | detail=ShortsViewer
     D ReelMonsterDetector: IDENTITY_EXTRACTED | app=YOUTUBE | fp=1077a25a1f7e7fc5 | author=@CheerfulVids
     D ReelMonsterDetector: DEDUP_RESULT | app=YOUTUBE | fp=1077a25a1f7e7fc5 | decision=CONFIRMED | dwellMs=665
     D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=1077a25a1f7e7fc5 | status=SUCCESS (+1) | todayCount=2
     ```
   - Switched back to Reel Monster dashboard:
     - Today's count automatically incremented to `2`.
     - Actual Viewing Time updated to measured duration: `"50s"` (`Avg: 25s/reel`).
     - Mascot quote advanced: `"A tiny reel for a tiny monster! 🍼"`.
   - Screenshot pulled to: `release/device_dashboard_phase11_verified.png`.

---

## 7. Conclusion

Phase 11 has been implemented and validated against all functional and performance constraints:
- Actual viewing time replaces arbitrary multipliers with monotonic-precision measurement.
- Daily reset is safe, double-confirmed, allowance-capped, and preserves historical data.
- Global tracking master switch provides complete user authority over background operations.
- Zero network permissions, 100% local SQLite storage, and 0 FPS battery conservation when idle.
