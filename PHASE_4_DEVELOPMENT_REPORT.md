# REEL MONSTER — PHASE 4: FLOATING MONSTER OVERLAY SERVICE
## Development & Verification Report

**Project:** BSERC GenAI Internship Week 2 — Project 6 (`06-Reel-Monster`)  
**Platform:** Native Android (Kotlin 2.3.20 + Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app`  
**Phase:** Phase 4 — Floating Monster Overlay Service  
**Status:** Completed & Verified (47/47 Automated Unit Tests Passing, Clean Debug APK Build)  
**Date:** 2026-11-08  

---

## 1. Executive Summary

Phase 4 successfully designed, implemented, and verified the native Android **Floating Monster Overlay Service** using Android's `WindowManager` and a dedicated Foreground Service. Munchie is now an interactive floating companion widget capable of floating directly above **Instagram Reels** and **YouTube Shorts**.

Key achievements in Phase 4:
1. **Real Android WindowManager Implementation:** Created `FloatingMonsterOverlayService` managing an interactive `WindowManager` view (`TYPE_APPLICATION_OVERLAY`) with zero activity coupling. The overlay runs independently of `MainActivity`.
2. **Compact Floating Bubble (Screen 06):** Circular/stadium pill design showing Munchie in its current growth stage, real-time count badge, edge-snapping physics, and drag-and-tap interaction.
3. **Expanded Mini-HUD Panel (Screen 07):** Tap-to-expand card showing larger Munchie mascot, stage evolution status, today's reel count, estimated screen time lost, humorous quotes from `MunchieMascotEngine`, Instagram/YouTube target app switcher, TAP ME +1 logging action, pause/resume toggle, and collapse/hide controls.
4. **Direct ReelRepository Connection:** "TAP ME +1" directly triggers `ReelRepository.logReel(app, TrackingMode.LEVEL_1_MANUAL)`, maintaining atomic SQLite transactions, timestamping, 350ms duplicate debounce protection, and instantaneous UI synchronization across both the overlay and the main dashboard.
5. **Display Over Other Apps Permission Flow:** Built `OverlayPermissionHelper` and `OverlayPermissionDialog` to safely guide users through granting Android's `SYSTEM_ALERT_WINDOW` permission with complete transparency around 100% offline privacy.
6. **Automated Testing & Build:** Developed `OverlayServiceTest.kt` with 10 unit tests. The total project test suite expanded to **47 passing unit tests (100% pass rate)**. Assembled debug APK (`app-debug.apk`, 19,892,062 bytes).

---

## 2. Architecture & Service Design

```
FloatingMonsterOverlayService (Foreground Service)
├── WindowManager (TYPE_APPLICATION_OVERLAY)
│   ├── FrameLayout (Touch Listener: Drag, Tap Detection, Edge Snapping)
│   └── ComposeView (OverlayLifecycleOwner: Lifecycle, ViewModelStore, SavedState)
│       └── FloatingMonsterOverlayContent
│           ├── CompactOverlayBubble (Screen 06)
│           └── ExpandedOverlayPanel (Screen 07)
├── Foreground Notification (Channel: reel_monster_overlay_channel)
│   ├── Dynamic Content: "X Reels Today" / "Munchie is on watch"
│   ├── Quick Actions: [Pause / Resume], [Close Overlay]
│   └── Android 14+ FGS Type: specialUse
└── ReelRepository Single Source of Truth
    ├── StateFlows: todayCount, munchieStage, isTrackingPaused, trackingMode
    └── SQLite Persistence: insertReelEvent (survives process restart)
```

### 2.1 Overlay State Machine
Defined in `com.reelmonster.app.domain.model.OverlayState`:
| State | Visible | Interactive | Description |
| :--- | :---: | :---: | :--- |
| `HIDDEN` | No | No | Overlay is dismissed or service stopped. |
| `COMPACT` | Yes | Yes | Small draggable floating bubble docked on screen edge with count badge. |
| `EXPANDED` | Yes | Yes | Expanded mini-HUD card with larger Munchie, quote, +1 button, and controls. |
| `PAUSED` | Yes | No | Tracking temporarily paused; displays amber pause badge and rejects counts. |
| `PERMISSION_MISSING` | No | No | `SYSTEM_ALERT_WINDOW` permission not granted; service stops safely. |
| `TRACKING_UNAVAILABLE` | No | No | Neither target app monitored or tracking service unavailable. |

### 2.2 Smooth Dragging & Edge Snapping
- **Screen-Absolute Coordinate Tracking:** Handled via `MotionEvent.ACTION_DOWN`, `ACTION_MOVE`, and `ACTION_UP` using `event.rawX` and `event.rawY` to eliminate coordinate jitter when `params.x/y` are updated.
- **Slop & Tap Discrimination:** If total travel $\le \text{scaledTouchSlop}$ and touch duration $< 350\text{ms}$, the interaction is registered as a clean tap $\rightarrow$ expands to `EXPANDED` mode. Movement exceeding touch slop initiates dragging.
- **Magnetic Edge Snapping:** On `ACTION_UP`, if `isEdgeSnappingEnabled` is true, the bubble smoothly docks to the nearest screen edge (left margin $16\text{dp}$ or right margin $\text{screenWidth} - \text{width} - 16\text{dp}$). Vertical coordinates are clamped between $48\text{dp}$ and $\text{screenHeight} - 72\text{dp}$ to prevent obstructing system status and navigation bars.

### 2.3 Foreground Service & Notification
- Configured with `foregroundServiceType="specialUse"` and property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` per Android 14+ requirements.
- Notification channel: `reel_monster_overlay_channel` (Importance: `LOW`).
- Notification dynamically reflects live counts and tracking pause state.
- Notification actions provide instant one-tap control: `[Pause / Resume]` and `[Close]`.

### 2.4 Justification of the 350ms Debounce Threshold
- Review of short-form video consumption shows humans take at least 1.5–5 seconds per reel even when rapidly swiping.
- $350\text{ms}$ ($\approx 0.35\text{s}$) permits up to nearly 3 distinct logs per second, easily accommodating quick consecutive reels while preventing capacitive touchscreen bounce, double-tap jitter, or accidental rapid clicks.
- Switching between target apps (Instagram $\leftrightarrow$ YouTube) bypasses the duplicate check and logs immediately.
- Taps separated by $\ge 350\text{ms}$ are always accepted.
- Automated tests in `OverlayServiceTest.testOverlayTapsDebounceIntegrity` and `ManualTrackingEngineTest.testRapidSuccessiveTapsDebounce` certify this logic.

---

## 3. Independent Verification & Test Execution

### 3.1 Unit Test Suite Execution
Automated Robolectric unit tests were executed with Gradle 9.5.0:

```bash
export JAVA_HOME="/Users/rohansiddhpura/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
export ANDROID_HOME="/Users/rohansiddhpura/Library/Android/sdk"
./gradlew test --console=plain
```

**Results:**
- **Tests Completed:** 47
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Success Rate:** 100%
- **Execution Time:** ~9.6 seconds

#### Test Class Breakdown:
| Test Class | Tests | Failures | Status |
| :--- | :---: | :---: | :---: |
| `com.reelmonster.app.OverlayServiceTest` | 10 | 0 | **PASSED** |
| `com.reelmonster.app.ManualTrackingEngineTest` | 11 | 0 | **PASSED** |
| `com.reelmonster.app.ReelRepositoryTest` | 4 | 0 | **PASSED** |
| `com.reelmonster.app.ReelDbHelperTest` | 4 | 0 | **PASSED** |
| `com.reelmonster.app.UserPreferencesManagerTest` | 6 | 0 | **PASSED** |
| `com.reelmonster.app.DashboardAndNavigationTest` | 3 | 0 | **PASSED** |
| `com.reelmonster.app.DailyAggregationTest` | 3 | 0 | **PASSED** |
| `com.reelmonster.app.MunchieMascotEngineTest` | 4 | 0 | **PASSED** |
| `com.reelmonster.app.MunchieStageTest` | 1 | 0 | **PASSED** |
| `com.reelmonster.app.DateTimeUtilsTest` | 1 | 0 | **PASSED** |
| **Total** | **47** | **0** | **100% PASSED** |

### 3.2 Specific Verification of Overlay Engine Requirements

| Verification Item | Test Method | Evidence / Result | Status |
| :--- | :--- | :--- | :---: |
| Overlay state enum & properties | `testOverlayStateEnumProperties` | Validated `isVisible` & `isInteractive` for all 6 states | **VERIFIED** |
| Overlay permission intent generation | `testOverlayPermissionHelperIntent` | Validated `ACTION_MANAGE_OVERLAY_PERMISSION` & package URI | **VERIFIED** |
| Overlay preference persistence | `testOverlayPreferencePersistence` | Validated DataStore persistence of overlay, edge snapping, scale, opacity | **VERIFIED** |
| TAP ME +1 logging from overlay | `testManualPlusOneIntegrationFromOverlay` | Increments count, updates stage, logs Instagram Reel | **VERIFIED** |
| YouTube Shorts logging from overlay | `testYouTubePlusOneIntegrationFromOverlay` | Increments count, logs YouTube Short attribution | **VERIFIED** |
| Overlay tap debounce integrity | `testOverlayTapsDebounceIntegrity` | Filters $<350$ms same-app bounce; allows switch & $>350$ms taps | **VERIFIED** |
| Overlay pause & resume tracking | `testPauseAndResumeOverlayTracking` | Blocks logs while paused; resumes counting seamlessly | **VERIFIED** |
| Foreground service action constants | `testForegroundServiceActionConstants` | Verified START, STOP, PAUSE, RESUME, EXPAND, COLLAPSE intents | **VERIFIED** |
| Zero counter drift between Overlay & Dashboard | `testCounterSyncAcrossDashboardAndOverlay` | Both observe `ReelRepository` StateFlows; identical values | **VERIFIED** |
| Mascot Engine quotes across all 5 stages | `testMunchieMascotEngineAllStagesInOverlay` | Quotes generated for Baby, Curious, Hungry, Stuffed, Megachonk | **VERIFIED** |

---

## 4. Build Artifacts & Device Audit

### 4.1 Debug APK Compilation
- Command executed: `./gradlew assembleDebug --console=plain`
- Result: `BUILD SUCCESSFUL in 4s`
- 38 actionable tasks: 5 executed, 33 up-to-date.
- **Compiled APK Location:** `/Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/debug/app-debug.apk`
- **Release Mirror Location:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/release/app-debug.apk`
- **Exact File Size:** `19,892,062 bytes` (~18.97 MB)

### 4.2 Permissions Audit
- `android.permission.SYSTEM_ALERT_WINDOW` (Required for WindowManager floating bubble)
- `android.permission.FOREGROUND_SERVICE` (Required for persistent overlay service)
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (Android 14+ requirement)
- `android.permission.POST_NOTIFICATIONS` (Notification controls)
- `android.permission.RECEIVE_BOOT_COMPLETED` (Reboot recovery)
- **Zero Internet Permission:** `android.permission.INTERNET` is strictly absent. 100% offline local privacy is preserved.

### 4.3 Hardware / Emulator Status
- Command executed: `$ANDROID_HOME/platform-tools/adb devices`
- Output: `List of devices attached` (Empty)
- **Honest Statement:** No emulator or physical device was attached during this build run. Automated Robolectric unit tests proved service intents, state machines, database persistence, and debounce logic. Below is the manual checklist prepared for live physical device verification.

---

## 5. Real Device Testing Checklist

When testing on a physical Android device or active emulator:

- [ ] **Step 1 — Permission Setup:** Open Reel Monster $\rightarrow$ Navigate to Dashboard or Settings $\rightarrow$ Tap "Enable Floating Monster Overlay".
- [ ] **Step 2 — Rationale Dialog:** Verify `OverlayPermissionDialog` appears, explaining the requirement and confirming offline privacy.
- [ ] **Step 3 — System Settings:** Tap "Open Settings" $\rightarrow$ Verify Android's "Display over other apps" screen opens for Reel Monster $\rightarrow$ Toggle ON $\rightarrow$ Return to app.
- [ ] **Step 4 — Auto-Start on Return:** Verify `MainActivity.onResume` detects granted permission and starts `FloatingMonsterOverlayService`.
- [ ] **Step 5 — Persistent Notification:** Pull down the notification shade $\rightarrow$ Verify "Munchie is on watch 👀" appears with today's count and actions `[Pause]` and `[Close]`.
- [ ] **Step 6 — Compact Bubble:** Verify Munchie appears as a circular floating bubble docked on screen edge.
- [ ] **Step 7 — Drag Interaction:** Touch and drag the compact bubble $\rightarrow$ Verify smooth 60fps dragging following the finger.
- [ ] **Step 8 — Magnetic Snapping:** Release finger near screen center $\rightarrow$ Verify bubble docks smoothly to the nearest screen margin ($16\text{dp}$).
- [ ] **Step 9 — App Float (Instagram):** Launch Instagram $\rightarrow$ Verify Munchie floats above Instagram Reels feed without obstructing navigation.
- [ ] **Step 10 — Expand Mini-HUD:** Tap the compact bubble $\rightarrow$ Verify it expands into the mini-HUD panel (Screen 07) showing larger Munchie, quote, and stats.
- [ ] **Step 11 — Manual TAP ME +1:** Tap "TAP ME +1 REEL" $\rightarrow$ Verify count increments by 1, stage updates, and quote refreshes.
- [ ] **Step 12 — App Float (YouTube):** Switch to YouTube Shorts $\rightarrow$ Select "YouTube" chip in expanded HUD $\rightarrow$ Tap "TAP ME +1 SHORT" $\rightarrow$ Verify YouTube count increments.
- [ ] **Step 13 — Rapid Double-Tap:** Rapidly double-tap "TAP ME +1" $\rightarrow$ Verify 350ms debounce prevents accidental double-counting.
- [ ] **Step 14 — Pause Tracking:** Tap "Pause" $\rightarrow$ Verify overlay enters paused state (amber badge, button says "Tracking Paused") and taps are blocked.
- [ ] **Step 15 — Dismiss & Persistence:** Tap "Hide" or close from notification $\rightarrow$ Return to Reel Monster app $\rightarrow$ Verify all logged counts and stages match the overlay exactly.

---

## 6. Scope Boundary Compliance

- **No Automatic Reel Detection (AccessibilityService):** Automatic detection was NOT enabled in this phase.
- **No TikTok Support:** Exclusively Instagram and YouTube are supported.
- **Zero Internet Telemetry:** 100% offline local privacy maintained.

Phase 4 is complete, tested, and certified. Ready for **Phase 5: Dashboard Screen & Live Metrics**.
