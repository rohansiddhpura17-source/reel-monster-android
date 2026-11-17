# REEL MONSTER — Project Plan & Development Roadmap

**Product:** REEL MONSTER  
**Platform:** Native Android (Kotlin + Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app`  
**Project:** BSERC GenAI Internship — Week 2 | Project 06: Reel Monster  
**Project Root:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster`  
**Phase:** Phase 14 Completed & Remediated — XP, Streaks & Achievements  
**Status:** Implemented, Tested & Verified (267/267 Unit Tests Passing, APK Assembled & Verified on SM-S928B with Database Version 6)  

---

## 1. Product Mission & Scope

REEL MONSTER is a cute floating screen companion ("Munchie") that hovers over Instagram Reels and YouTube Shorts. It visualizes the user's short-form video consumption habits in real-time, helping users become mindful of digital drift through delightful visual evolution, witty quotes, and accurate local-first analytics.

### Core Non-Negotiable Tenets:
1. **100% Offline & Private:** Zero network requests, zero analytics telemetry, zero login, zero cloud accounts.
2. **Persistent Across Reboots:** All session counts, daily records, and monster growth stages are saved in a local SQLite database that survives phone restart.
3. **Strict Platform Focus:** Native Android only (no web, React Native, or Flutter).
4. **Honest Incrementality:** Level 1 manual tracking is implemented and perfected before Level 2 automatic detection is enabled. No fabricated test results.
5. **Faithful Visual Execution:** Strict preservation of the 12 Stitch AI reference screens (mint-green visual identity `#38C88A`, mascot Munchie, custom cards, HUD bottom sheet).

---

## 2. 10-Phase Incremental Roadmap

| Phase | Title | Main Deliverables | Verification Exit Gate | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Phase 1** | **Android Project Foundation** | Gradle 9 / AGP 9 setup, Kotlin 2.3.20, Compose BOM, modular architecture, design system, Munchie vector component, navigation shell, unit tests, README & plan | Successful compilation, passing unit tests, APK assembled | **DONE** |
| **Phase 1.5** | **Audit Remediation** | DataStore migration, SQLite API 26+ compatibility fix, Boot recovery receiver, 19 Robolectric unit tests, Offline/privacy audit | 19/19 tests passing, clean assembleDebug, APK verified | **DONE** |
| **Phase 2** | **Design System & Munchie Engine** | Full 5-stage vector mascot drawing (`Baby`, `Curious`, `Hungry`, `Stuffed`, `Megachonk`), animated idle bounce, speech bubble component, funny quote selector, 12 design system components, Dashboard implementation | Preview rendering, 26 unit tests passing, clean assembleDebug | **DONE** |
| **Phase 3** | **Local SQLite Engine & Level 1 Tracking** | `ReelDbHelper`, atomic transactions for daily totals and session logs, repository StateFlows, Level 1 Quick Log button, 350ms debounce | 37/37 unit tests passing, process restart persistence verified | **DONE** |
| **Phase 4** | **Floating WindowManager Overlay** | Android Foreground Service, draggable compact bubble on screen, touch edge snapping, expanded HUD mini-panel (+1 Chomp, stats summary, pause) | 47/47 unit tests passing, clean assembleDebug APK | **DONE** |
| **Phase 5** | **Dashboard Screen & Live Metrics** | Screen 02 implementation: Hero card, progress bar to next stage, +1 Reel Quick Log, today's reels card, Estimated Time Lost card (~1.5m/reel), separate Instagram/YouTube cards, 7-day continuous activity chart | 57/57 unit tests passing, clean assembleDebug APK | **DONE** |
| **Phase 6** | **Monster Interactions & Personalization** | 5-stage dynamic expressions, sleeping pause state (`︶ ︶`), winking celebrations, 3 reaction styles (Bounce, Squash & Stretch, Happy Wiggle), 4 theme variants (Mint, Berry, Sunny, Ocean), contextual speech engine with priority overrides, opt-in haptics & sound | 70/70 unit tests passing, clean assembleDebug APK | **DONE** |
| **Phase 7** | **Tracking History & Insights** | Screen 11/History implementation: 5 date ranges (Today, 7D, 30D, This Month, Prev Month), continuous zero-filled calendar series, interactive segmented bar chart (IG/YT split), selected day detail view with timestamped event feed, factual insights & period comparisons | 86/86 unit tests passing, clean assembleDebug APK | **DONE** |
| **Phase 8A** | **Automatic Detection Feasibility Audit** | Technical investigation of Android detection mechanisms (`UsageStatsManager`, `AccessibilityService`, UI hierarchy vs OCR), Instagram Reels vs YouTube Shorts compatibility, multi-stage deduplication strategy, privacy compliance, accuracy test protocol | Feasibility report published; no code modifications | **DONE** |
| **Phase 8B** | **Level 2 Automatic Detection & Settings** | Dedicated `AccessibilityService` implementation with structural layout fingerprinting, 5-stage deduplication, user disclosure dialog, and real-device accuracy validation protocol | 144/144 unit tests passing, APK assembled | **DONE** |
| **Phase 9 (Step 1)** | **Visual Polish: 3D Effects (1A–1G)** | Native 3D visual effects & haptics: spring-physics drag & edge snapping (30fps throttle), 3D perspective card tilt, event-driven squash & stretch (0 idle fps), safe haptic ticks, particle burst, odometer digit roll, stage-up radial flash & dialog | 144/144 unit tests passing, clean assembleDebug APK | **DONE (STEP 1)** |
| **Phase 11** | **Actual Viewing Time, Daily Reset & Global Control** | Monotonic session measurement (`SystemClock.elapsedRealtime()`), `viewing_sessions` SQLite migration (v2), live elapsed timer bubble (00:15), 5-daily-reset allowance with midnight renewal & confirmation dialog, global tracking master switch | 167/167 unit tests passing, clean assembleDebug APK | **DONE** |
| **Phase 10** | **Munchie Personality 2.0** | 10 canonical emotional states with priority arbitration (1..10), custom Canvas procedural vector expressions (eyes, mouth, posture, tilt), 50+ line anti-repeat contextual dialogue engine, interactive tap & long-press reactions with guaranteed 0 count/duration modification | 183/183 unit tests passing, physically verified on SM-S928B | **DONE** |
| **Phase 12** | **Daily Goals & Mindful Limits** | Dual target boundaries (Reel count + viewing minutes), SQLite v3 schema (`daily_goals`, `daily_goal_records`), live pacing progress meters on dashboard, interactive configuration bottom sheet with steppers, ceiling alert boundaries (never celebrate 100%), historical day boundary finalization | 199/199 unit tests passing, physically verified on SM-S928B | **DONE** |
| **Phase 13** | **Focus Mode & Gentle Interventions** | Quick Break & Deep Focus sessions, monotonic timer (`SystemClock.elapsedRealtime()`), SQLite v4 schema (`focus_sessions`), zero bleed into short-form viewing metrics, non-blocking mindful check-ins with cooldowns, Munchie `FOCUSING` & `CELEBRATING` reactions | 219/219 unit tests passing, physically verified on SM-S928B | **DONE** |
| **Phase 14** | **XP, Streaks & Achievements (Remediation)** | Idempotent XP transactions, 10 user levels, 120m Estimated Monitoring Coverage with boot-safe continuity heartbeats, timestamped daily-goal history & strictest-target snapshots, multi-scope goal evaluation, 12 achievements, Munchie progression celebrations, DB v6, Linux kernel `boot_session_id`, active SQLite interval persistence | 267/267 unit tests passing, physically verified on SM-S928B with device reboot | **DONE** |
| **Phase 15** | **Weekly Intelligence** | Weekly habit insights, trend comparisons, and personal patterns | Scheduled | **NEXT** |

---

## 3. Modular Architecture Breakdown

```
com.reelmonster.app
├── data/
│   ├── database/         # Local SQLite database helper & schema
│   ├── repository/       # ReelRepository connecting DB & StateFlows
│   └── preferences/      # UserPreferencesManager (DataStore/SharedPreferences)
├── domain/
│   ├── model/            # MunchieStage, TrackingMode, TargetApp, ReelLogItem, DailySummary
│   └── usecase/          # Business rules (Stage calculation, Drift estimation)
├── presentation/
│   ├── components/       # MunchieMascot, MunchieSpeechBubble, TopBar, BottomBar
│   ├── navigation/       # Screen destinations & bottom nav routing
│   ├── onboarding/       # Screen 01 Welcome & Onboarding
│   ├── dashboard/        # Screen 02 Main Dashboard & Hero Card
│   ├── statistics/       # Screens 09 & 10 Today's Stats & 7-Day Analytics
│   ├── history/          # Screen 11 Timestamped Reel Chomps
│   └── settings/         # Screen 12 Settings, Privacy, DB Status
├── service/
│   ├── overlay/          # WindowManager Floating Bubble Service
│   └── tracking/         # AccessibilityService for Level 2 Automatic Tracking
└── core/
    ├── design/           # Color, Theme, Type, Shape
    └── util/             # DateTimeUtils, Math & Formatting Helpers
```

---

## 4. Acceptance Criteria for Phase 1

1. Android native project initialized in `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster`.
2. Package namespace set to `com.reelmonster.app`.
3. Java 21 toolchain and Gradle 9.5.0 configured.
4. Jetpack Compose and Material 3 configured with the mint-green palette.
5. All 12 Stitch reference screens unzipped and preserved in `design_reference/`.
6. Modular package architecture created.
7. Mascot placeholder component (`MunchieMascot.kt`) implemented with multi-stage support.
8. Core unit test suite (`MunchieStageTest`, `DateTimeUtilsTest`) passing.
9. Debug APK assembled and verified on Android emulator.
10. `README.md` and `PROJECT_PLAN.md` completed.
