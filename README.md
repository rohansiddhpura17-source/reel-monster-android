# 👾 REEL MONSTER — Native Android Application

**BSERC GenAI Internship — Week 2 | Project 06: Reel Monster**  
**Platform:** Native Android (Kotlin + Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app`  
**License:** Educational & Internship Evaluation  

---

## 🌟 Executive Overview

**REEL MONSTER** is an offline-first, native Android screen companion that helps smartphone users cultivate mindfulness around short-form video consumption (Instagram Reels and YouTube Shorts).

The application features **Munchie**, an adorable animated monster who floats as a compact bubble directly over video apps. As users scroll through videos:
- Users log or sense video consumption (+1 Chomp per reel).
- Munchie grows across 5 distinct evolution stages (from an innocent **Baby Munchie** to an overfed **Megachonk**).
- Witty, humorous feedback alerts users to their screen time drift.
- Comprehensive local-first analytics reveal daily pacing, platform distribution, and peak binge spikes.
- **Zero cloud telemetry, zero account requirement, and 100% offline functionality.**

---

## 🎨 Visual Design Reference

This implementation strictly adheres to the 12 Stitch-generated Android UI/UX screens stored in [design_reference/](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/design_reference):
- **Primary Color:** Mint Green (`#38C88A`), Forest Green (`#0F3E2E`), Mint Tint Surface (`#EDFAF4`).
- **Accent Colors:** Purple Accent (`#8B5CF6`) for Stage 3 & Instagram, YouTube Red (`#EF4444`).
- **Mascot Identity:** Munchie with sparkling kawaii eyes, customizable moods, and animated idle bounce.
- **HUD Interface:** Dual-layer floating interface consisting of a draggable compact bubble and an expandable bottom-sheet HUD.

---

## 🚀 Key Features

1. **Floating Companion (WindowManager Overlay):**
   - Stays docked on the side of the screen over social video feeds.
   - Non-intrusive, draggable, and touch-responsive.
2. **5-Stage Monster Evolution:**
   - **Stage 1: Baby Munchie (0–5 Reels)** — Low appetite, innocent, wide eyes.
   - **Stage 2: Curious Munchie (6–15 Reels)** — Moderate appetite, alert.
   - **Stage 3: Hungry Munchie (16–30 Reels)** — Heavy appetite, donut accessory, chomping mouth.
   - **Stage 4: Stuffed Munchie (31–50 Reels)** — Satiated, slouching, relaxed belly.
   - **Stage 5: Megachonk Overfed (51+ Reels)** — Maximum capacity, dopamine overload, bedtime warning.
3. **Dual-Tier Tracking Architecture:**
   - **Level 1 (Manual Tracking):** Quick 1-tap logging directly from the floating overlay or in-app button. Extremely battery-friendly with no accessibility overhead.
   - **Level 2 (Automatic Detection):** Experimental scroll sensing via Android `AccessibilityService` that identifies reel change events without ever reading private text or video content.
4. **Interactive Monster Mascot & Personalization:**
   - **5 Growth Stages:** Baby (0–5), Curious (6–15), Hungry (16–30), Stuffed (31–50), and Megachonk (51+).
   - **Reactive Animations:** Playful Bounce (`BOUNCE`), Squash & Stretch (`SQUASH_AND_STRETCH`), and Happy Wiggle (`WINK_AND_HAPPY`) with celebrating wink.
   - **Pause Expression:** Sleeping curved eyes (`︶ ︶`), relaxed smile, and slow breathing when tracking is paused.
   - **Personalization Themes:** Mint Classic, Berry Purple, Sunshine Orange, and Ocean Blue.
   - **Contextual Humor Engine:** Deterministic stage-aware funny quotes and priority states (Zero count, First reel, Paused).
   - **Sensory Feedback:** Opt-in tactile haptics and crunch sounds (disabled by default, zero extra permissions).
5. **Tracking History & Insights Engine:**
   - **5 Calendar Date Ranges:** Today, Last 7 Days, Last 30 Days, This Month (no future dates), and Previous Month.
   - **Attribution Consistency:** Guaranteed Total Reels = Instagram Reels + YouTube Shorts.
   - **Interactive Segmented Chart:** Dual-color stacked bars (Instagram Purple + YouTube Red) with zero-day stubs, tap-to-inspect, and horizontal scrolling for 30-day views.
   - **Daily Detail Drill-Down:** Inspect any calendar day to see exact timestamped Reel entries, platform badges, and time lost.
   - **Factual Insights:** Objective observations highlighting peak activity days, active-day velocity, platform split, and previous-period trend comparison.
6. **3D Visual Polish & Tactile Feedback (Phase 9 Step 1):**
   - **Spring-Physics Window Drag:** Underdamped coroutine spring edge-snapping throttled to $\le 30$ FPS; uses system gesture exclusion rects.
   - **Interactive 3D Perspective Tilt:** Dashboard cards subtly tilt toward touch with instant spring-to-flat reset.
   - **Zero Idle Rendering:** Squash & stretch animations are event-driven single-shot springs; continuous render loops are completely eliminated for 0 FPS while resting.
   - **Micro-Interactions:** 8–12 dot particle burst on chomp, rolling odometer counter digits, and radial stage-up celebration flash.
7. **Actual Viewing Time & Daily Reset Engine (Phase 11):**
   - **Monotonic Clock Measurement:** Replaces static estimations with precise `SystemClock.elapsedRealtime()` durations; handles app switching, screen-off, pause, and global OFF without time drift.
   - **Floating Live Elapsed Pill:** Displays active reel duration (e.g. `00:15`) in real-time on Munchie's compact bubble; shuts down ticker when resting for 0 FPS idle battery conservation.
   - **Five Daily Resets:** Users can restart today's count and viewing time up to 5 times per day (`MAX_DAILY_RESETS = 5`) via an accidental-click confirmation dialog; historical data is safely preserved; allowance renews at local midnight.
   - **Global Tracking Master Switch:** Prominently toggleable in Dashboard and Settings; halts all automatic detection and manual logging when OFF while preserving the user's selected tracking mode.
8. **Munchie Personality 2.0 Engine (Phase 10):**
   - **10 Emotional States with Deterministic Priority Resolution:** `IDLE`, `RESTING`, `HAPPY`, `CURIOUS`, `FOCUSING`, `SLEEPY`, `CONCERNED`, `SURPRISED`, `EXCITED`, `CELEBRATING`.
   - **Procedural Canvas Vectors:** Custom eyes (sleeping arcs, wide surprised ovals, focus lines, winking party eye), mouth variations, and expressive tilt angles (-7° to +6°).
   - **Contextual Anti-Repeat Dialogue:** 50+ lines of dialogue across 10 states with circular anti-repeat index memory.
   - **Zero-Stat Interactive Gestures:** Tapping Munchie alternates playfully between Curious and Surprised; long-pressing triggers Celebrating; guaranteed 0 change to reel counts or viewing durations.
9. **Daily Goals & Personal Intelligence (Phase 12):**
   - **Dual Target Boundaries:** Set intentional daily boundaries for short-form video consumption (Reel/Short count) and total observed viewing duration (minutes).
   - **Live Pacing Progress:** Interactive meters on the dashboard display real-time consumption vs boundaries with colored status badges (`Not Started`, `In Progress`, `Approaching Limit`, `At Limit`, `Exceeded`, `Paused`).
   - **Ceiling Alert Boundaries:** Reaching 100% of a daily limit triggers a compassionate `CONCERNED` alert boundary ("Daily limit reached! Let's pause and breathe 🌿"); it is never treated as a completion celebration.
   - **End-of-Day Boundary Finalization:** Daily goal outcomes for past calendar days are finalized immutably at the local-day boundary, awarding celebrations only when past days were kept within limits.
   - **Interactive Modal Sheet:** Quick-adjust steppers (`[-]` and `[+]`) and full-width clickable toggles for easy adjustments.
10. **Focus Mode & Gentle Interventions (Phase 13):**
   - **Optional User-Controlled Sessions:** Quick Break (5m/10m/15m), Deep Focus (25m/45m/60m), and Custom Focus sessions with monotonic `SystemClock.elapsedRealtime()` timing and zero bleed into Reel viewing time.
   - **Gentle Non-Blocking Check-Ins:** Configurable mindful reminders with cooldown periods and never forced app blocking.
11. **XP, Streaks & Achievements (Phase 14 & Remediation):**
   - **Healthy-Choice Progression:** 10 user levels (`0` to `6,000 XP`), 12 offline achievements, and lifetime-idempotent 3d/7d/30d streak milestones that reward focus completion, staying within daily boundaries, and mindful days—never scrolling or watching Reels.
   - **Estimated Monitoring Coverage (`>= 120m`):** Separates `service_connected_ms`, `tracking_enabled_ms`, `screen_on_ms`, and `estimated_verified_monitoring_ms`, validated by 15-minute continuity heartbeats and accompanied by transparent Android monitoring limitation disclosures.
   - **Timestamped Goal History & Snapshots:** Records goal creation, enablement, disablement, and target updates with timestamps; enforces `strictest_target` and full-day activation eligibility (`00:00:00` local time) across `COMBINED`, `INSTAGRAM`, and `YOUTUBE` scopes.
   - **Migration Cutover Correctness:** Dedicated `system_metadata` table, clean cutover timestamps (no backdating), migration day explicitly ineligible, and next local calendar day first eligible.
   - **Reboot-Safe Monitoring:** Stamped with Linux kernel `boot_session_id`, active interval SQLite persistence (`is_active = 1`), process restart recovery vs device reboot interruption, and zero cross-boot monotonic clock comparison.
12. **Persistent Local SQLite Engine (Schema v6):**
   - Survives device reboots and app kills without internet or server sync.

   - Computes daily scroll velocity, actual viewing duration, platform distribution, focus history, monitoring coverage, and progression history.

---

## 🛠 Technology Stack

- **Language:** Kotlin 2.3.20 (JVM Toolchain 21)
- **UI Toolkit:** Jetpack Compose with Material 3 Design System
- **Build System:** Gradle 9.5.0 + Android Gradle Plugin 9.0.1
- **Architecture:** MVVM + Clean Architecture with Repository Pattern & Kotlin StateFlow
- **Database:** Local Android SQLite (`SQLiteOpenHelper`)
- **System APIs:** Android WindowManager (`SYSTEM_ALERT_WINDOW`), Foreground Service, AccessibilityService

---

## 📁 Modular Package Structure

```
com.reelmonster.app
├── data/
│   ├── database/         # SQLite database schema, helpers & queries
│   ├── repository/       # ReelRepository with StateFlow streams
│   └── preferences/      # UserPreferencesManager
├── domain/
│   ├── model/            # MunchieStage, TrackingMode, TargetApp, ReelLogItem, DailySummary
│   └── usecase/          # Business logic for progression & metrics
├── presentation/
│   ├── components/       # MunchieMascot (Canvas vector), SpeechBubble, AppBars
│   ├── navigation/       # Navigation items (Home, Stats, History, Settings)
│   ├── onboarding/       # Screen 01 Welcome & Mascot Introduction
│   ├── dashboard/        # Screen 02 Main Dashboard & Quick Log
│   ├── statistics/       # Screens 09 & 10 Today's Stats & 7-Day Velocity
│   ├── history/          # Screen 11 Detailed Log History
│   └── settings/         # Screen 12 Preferences, Mascot Customization, Data Reset
├── service/
│   ├── overlay/          # Floating WindowManager overlay service
│   └── tracking/         # Accessibility tracking service
└── core/
    ├── design/           # Color tokens, typography, theme composables
    └── util/             # DateTimeUtils, formatting and math functions
```

---

## 📦 Official Production Release (v1.0.0)

- **Release Artifact:** `release/reel-monster-v1.0.0.apk` (13,372,830 bytes / 12.75 MB)
- **SHA-256 Checksum:** `d6d5bfc9b7cce825b30f578eafb200754ce9e8d10c4ff218beea5473ed6f7193`
- **Signing Scheme:** APK Signature Scheme v2 (Signed with dedicated 2048-bit RSA release keystore)
- **Target Hardware Tested:** Samsung Galaxy S24 Ultra (`SM-S928B`, Android 17 / API 37)
- **Security & Privacy:** 100% Offline (Zero `android.permission.INTERNET`, 0 network sockets, 0 telemetry)
- **Test Suite Status:** 340 / 340 Unit Tests Passing (100% pass rate)

---

## 📚 Complete Project Documentation (`docs/`)

The full engineering and user documentation suite is available in the [`docs/`](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs) directory:
- [Phase 18 Final Release Report](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/PHASE_18_RELEASE_REPORT.md) — Comprehensive release engineering & packaging report.
- [Final Regression Test Report](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/FINAL_TEST_REPORT.md) — Full audit of all 29 test classes and 340 automated tests.
- [Final Device Validation Report](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/FINAL_DEVICE_VALIDATION.md) — 15 live hardware test cases on Samsung Galaxy S24 Ultra.
- [End-User Guide](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/USER_GUIDE.md) — Complete user manual covering onboarding, permissions, and features.
- [Technical Documentation](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/TECHNICAL_DOCUMENTATION.md) — Architecture reference, SQLite schema v6, and service lifecycles.
- [Release Notes](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/RELEASE_NOTES.md) — v1.0.0 release notes and 18-phase changelog.
- [Installation Guide](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/INSTALLATION_GUIDE.md) — Deployment instructions via ADB and direct APK sideloading.
- [Build and Signing Guide](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/BUILD_AND_SIGNING_GUIDE.md) — Reproducible build instructions and keystore management.
- [Project Handover Document](file:///Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/docs/PROJECT_HANDOVER.md) — Final handover summary with 14 key metrics.

---

## 🧪 Testing & Verification

Run the full automated regression suite (340 tests across 29 test classes covering detection, deduplication, viewing time reconciliation, goals, focus mode, progression, weekly intelligence, and backup/restore):
```bash
./gradlew testDebugUnitTest
```

Assemble signed release APK:
```bash
./gradlew assembleRelease
```
The compiled release APK will be at:
- `release/reel-monster-v1.0.0.apk` (12.75 MB)

Install & run release APK on connected Android device via ADB:
```bash
adb install -r release/reel-monster-v1.0.0.apk
adb shell am start -n com.reelmonster.app/.MainActivity
```
\n
---

## 👨‍💻 Developer & Contact

**Rohan Siddhpura**  
*B.Tech in Information Technology — RK University*  
- **GitHub:** [@rohansiddhpura17-source](https://github.com/rohansiddhpura17-source)  
- **Email:** [rohansiddhpura17@gmail.com](mailto:rohansiddhpura17@gmail.com)  
- **Open for:** Software Engineering Internships, Graduate Roles & Open-Source Collaboration
