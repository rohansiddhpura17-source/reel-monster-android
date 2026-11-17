# TECHNICAL DOCUMENTATION
## Reel Monster | Architectural Specification & Engineering Reference

**Version:** 1.0.0 (Version Code 1)  
**Package:** `com.reelmonster.app`  
**Toolchain:** Kotlin 2.3.20 · AGP 9.0.1 · Java 21 Toolchain  
**UI Toolkit:** Jetpack Compose (Compose BOM 2026.03.01) + Material 3  
**Data Storage:** Local SQLite (`PRAGMA user_version = 6`) + DataStore Preferences  
**Security Footprint:** 100% Offline (Zero `android.permission.INTERNET`, 0 HTTP/Socket libraries)  

---

## 1. Architectural Principles

Reel Monster is engineered according to **Clean Architecture** and **Unidirectional Data Flow (MVI/MVVM)** patterns. The system is strictly decoupled into distinct functional layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│    Jetpack Compose UI · ViewModels · Screen StateFlows      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                      Domain Layer                           │
│  Pure Kotlin Business Logic · Pacing Models · XP Algorithms │
│    Weekly Intelligence Engines · Backup & Restore Planner   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                       Data Layer                            │
│ ReelRepository · ReelDbHelper (SQLite v6) · DataStore Prefs │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Background Services                      │
│ ReelAccessibilityService · FloatingMonsterOverlayService    │
│ MonitoringCoverageTracker · ViewingSessionTracker           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Package Structure

```text
app/src/main/java/com/reelmonster/app/
├── MainActivity.kt                      // Single-activity host, root Compose navigation
├── ReelMonsterApp.kt                    // Application lifecycle entrypoint
├── Screen.kt                            // Navigation routes and destinations
├── data/
│   ├── database/
│   │   ├── ReelDbHelper.kt              // SQLiteOpenHelper (Schema v6, 17 tables)
│   │   └── MigrationEngine.kt           // Safe incremental migrations (v1 → v6)
│   ├── preferences/
│   │   └── UserPreferencesManager.kt    // Jetpack DataStore preferences wrapper
│   ├── repository/
│   │   └── ReelRepository.kt            // Single source of truth & data orchestration
│   └── backup/
│       ├── BackupExporter.kt            // Offline JSON backup serialization
│       ├── BackupImporter.kt            // Pre-flight validation & atomic restore engine
│       └── BackupTableStore.kt          // 17-table relational serializer/deserializer
├── domain/
│   ├── model/
│   │   ├── ReelEvent.kt                 // Logged reel event data model
│   │   ├── GoalModels.kt                // Goal limits, outcomes, and daily pacing
│   │   ├── FocusModels.kt               // Focus session state and countdowns
│   │   ├── ProgressionModels.kt         // XP transaction ledger, levels, streaks, badges
│   │   ├── MonitoringModels.kt          // Verified monitoring intervals, boot sessions
│   │   ├── weekly/
│   │   │   ├── HourlyScrollingPattern.kt// 24-hour diurnal pattern normalized by coverage
│   │   │   ├── WeeklyTrend.kt           // Week-over-week comparisons ([D-7, D-1] vs [D-14, D-8])
│   │   │   ├── WeeklyDigest.kt          // In-memory weekly executive summary
│   │   │   ├── WeeklyReflectionEngine.kt// Mindful, supportive reflection generator
│   │   │   └── MunchieMastery.kt        // Level-derived cosmetic aura calculations
│   │   └── backup/
│   │       ├── BackupSchema.kt          // Canonical JSON backup payload model
│   │       ├── BackupValidator.kt       // Pre-flight schema & SHA-256 payload checksum validation
│   │       └── RestorePlanner.kt        // Merge plan vs Replace All transaction plan
│   └── usecase/
│       ├── DailyGoalsUseCase.kt         // Goal evaluation and midnight boundary logic
│       ├── FocusModeUseCase.kt          // Non-blocking focus sessions and interventions
│       └── ProgressionUseCase.kt        // XP calculation and achievement unlocks
├── service/
│   ├── tracking/
│   │   ├── ReelAccessibilityService.kt  // Android AccessibilityService implementation
│   │   ├── InstagramReelDetector.kt     // Instagram View hierarchy extractor
│   │   ├── YouTubeShortsDetector.kt     // YouTube Shorts View hierarchy extractor
│   │   ├── DeduplicationEngine.kt       // 450ms dwell + 10s bounce-back LRU cache
│   │   ├── MonitoringCoverageTracker.kt // Reboot-safe interval and heartbeat logger
│   │   ├── BootSessionProvider.kt       // Monotonic boot session identifier
│   │   ├── ViewingSessionTracker.kt     // Measured session duration clock
│   │   ├── FocusSessionTracker.kt       // Focus block timer and gentle check-in dispatcher
│   │   └── DetectorLogger.kt            // Debug-only logging (silent in release builds)
│   ├── overlay/
│   │   ├── FloatingMonsterOverlayService.kt // Foreground service managing WindowManager view
│   │   ├── OverlayTouchListener.kt      // Drag gestures and magnetic edge snapping
│   │   └── OverlayBubbleView.kt         // Munchie face, speech bubble, and live counter badge
│   └── receiver/
│       └── RebootReceiver.kt            // Safely resumes monitoring after device boot
└── presentation/
    ├── dashboard/                       // Home dashboard, live counter, master switch, cards
    ├── statistics/                      // Honest measured viewing time, daily breakdown
    ├── history/                         // 7-day calendar navigation, platform breakdown
    ├── goals/                           // Daily goal setup and limit configuration sheet
    ├── focus/                           // Focus session timer and gentle intervention settings
    ├── weekly/                          // Hourly scrolling pattern and weekly digest screen
    ├── progression/                     // Level roadmap, streak milestones, achievement grid
    ├── settings/                        // Theme selection, sensory toggles, backup & restore
    └── components/                      // Reusable Compose UI elements (MunchieFace, PacingCard)
```

---

## 3. Database Schema (Schema Version 6)

The local SQLite database (`reel_monster.db`) operates with `syncMode=FULL` and `journalMode=TRUNCATE`. Upgrades from v1 through v6 are strictly sequential and non-destructive:

```
v1 (Initial reels table)
 └── v2 (Added viewing_sessions for actual measured durations)
      └── v3 (Added daily_goals, daily_goal_records, daily_goal_snapshots)
           └── v4 (Added focus_sessions)
                └── v5 (Added xp_transactions, user_progress, achievements, streak_records)
                     └── v6 (Added boot_session_id to monitoring_intervals & heartbeats, system_metadata)
```

### Relational Table Dictionary

1. **`reels`** — Logged reel instances (`id`, `app_name`, `reel_id`, `dwell_duration_ms`, `creator_id`, `created_at`, `timestamp`).
2. **`sessions`** — Daily aggregates per platform (`id`, `session_date`, `reel_count`, `app_name`, `updated_at`).
3. **`viewing_sessions`** — Measured continuous video viewing intervals (`id`, `app_name`, `start_time_monotonic`, `end_time_monotonic`, `duration_ms`, `session_date`, `interruption_reason`).
4. **`daily_goals`** — User goal definitions (`id`, `goal_type`, `target_value`, `is_active`, `created_at`).
5. **`daily_goal_records`** — Daily evaluation results (`id`, `goal_id`, `record_date`, `achieved_value`, `is_met`, `evaluated_at`).
6. **`daily_goal_snapshots`** — Mid-day pacing snapshots for trend calculations.
7. **`daily_goal_history`** — Historical goal performance archives.
8. **`focus_sessions`** — Mindful focus intervals (`id`, `duration_seconds`, `started_at`, `completed_at`, `status`, `intervention_count`).
9. **`monitoring_intervals`** — Verified monitoring coverage blocks (`id`, `boot_session_id`, `start_elapsed_ms`, `end_elapsed_ms`, `duration_ms`, `coverage_date`, `status`).
10. **`monitoring_heartbeats`** — 15-minute active service heartbeats (`id`, `boot_session_id`, `timestamp_elapsed_ms`, `heartbeat_date`).
11. **`daily_monitoring_coverage`** — Daily coverage totals; marks a day qualified when verified coverage ≥ 120 minutes.
12. **`supported_app_detection_events`** — Low-level detection triggers per package.
13. **`xp_transactions`** — Immutable append-only XP ledger (`id`, `event_id` UNIQUE, `action_type`, `xp_amount`, `awarded_at`).
14. **`user_progress`** — Singleton user level state (`id=1`, `total_xp`, `current_level`, `current_streak_days`, `longest_streak_days`).
15. **`achievements`** — Badges unlocked by user (`id`, `achievement_key` UNIQUE, `unlocked_at`, `xp_rewarded`).
16. **`streak_records`** — Historical streak milestones and freeze logs.
17. **`system_metadata`** — App version, schema migrations, and cutover timestamps (`key` PRIMARY KEY, `value`, `updated_at`).

---

## 4. Detection & Deduplication Engine

### Accessibility Event Filtering
`ReelAccessibilityService` monitors `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_SCROLLED` strictly when the foreground package is:
- `com.instagram.android`
- `com.google.android.youtube`

### Deduplication Pipeline
```
Accessibility Event
       │
       ▼
Extract Target View Node (Video Container / Creator / Title)
       │
       ▼
Compute Content Fingerprint: Hash(App + Creator + Title + Sibling IDs)
       │
       ▼
DeduplicationEngine Check:
  ├── Is fingerprint in LRU cache (recent 50 entries)?
  │     └── If yes and within 10s bounce-back window: DROP
  ├── Did user dwell for ≥ 450 ms on this reel?
  │     └── If no (rapid swipe): DROP
  └── User stopped and watched:
        ├── Add to LRU Cache
        ├── Log to ViewingSessionTracker (start monotonic clock)
        └── Dispatch ReelEvent to ReelRepository
```

---

## 5. Reboot-Safe Monitoring Coverage

To ensure streak and mindful day evaluations cannot be spoofed or corrupted across device reboots:
1. Every monitoring interval is bound to a unique `boot_session_id` obtained from `/proc/sys/kernel/random/boot_id` and verified against `SystemClock.elapsedRealtime()`.
2. Monotonic timestamps are compared **only** within the same boot session.
3. If the device reboots while an interval is open, the startup routine marks the previous open interval as `INTERRUPTED_DEVICE_REBOOT` and begins a fresh interval with the new boot ID.
4. Heartbeats alone never qualify a day; a calendar day must accrue ≥ 120 minutes of verified continuous intervals within midnight boundaries.

---

## 6. Offline Security & Privacy Guarantees

- **Zero Network Sockets:** The application declares no network capabilities in its `AndroidManifest.xml`. Attempts to open network connections will result in a runtime `SecurityException`.
- **Log Sanitation:** All debugging logs (`DetectorLogger`) are gated behind `BuildConfig.DEBUG` and completely stripped in release builds.
- **Process Isolation:** Services are declared `exported="false"` (except `ReelAccessibilityService` which is protected by the system's `android.permission.BIND_ACCESSIBILITY_SERVICE`).
- **Cryptographic Backup Validation:** Exported JSON backups include a SHA-256 payload checksum. During import, the schema and checksum are validated before SQLite executes any write transaction. Destructive imports take an atomic safety backup before modifying data.
