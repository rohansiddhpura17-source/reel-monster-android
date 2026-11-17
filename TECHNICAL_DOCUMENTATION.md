# REEL MONSTER — TECHNICAL DOCUMENTATION

**Version:** 1.0.0 (version code 1) · **Package:** `com.reelmonster.app` · **Debug package:** `com.reelmonster.app.debug`
**Stack:** Kotlin 2.3.20 · Jetpack Compose + Material 3 (BOM 2026.03.01) · AGP 9.0.1 · Kotlin JVM toolchain 21 · Coroutines 1.10.2 · DataStore Preferences 1.1.3 · JUnit 4.13.2 · Robolectric 4.14.1 · **No networking, analytics, or JSON library in dependencies.**

## Architecture

Single-module Android app (`:app`), layered as presentation → repository → data:

- **Presentation** — Compose screens under `presentation/` (Dashboard, Statistics, History, Goals, Focus Mode, Weekly Intelligence, Settings, onboarding, tracking, progression, components). Navigation is state-based in `MainActivity.kt` (a remembered route, `Screen.fromRoute` falls back to Home, onboarding gated by `isOnboardingCompleted`).
- **Repository** — `ReelRepository` is the single coordination point: logging reels, refreshing dashboard state, evaluating goals, finalising history, and progression. It exposes the database helper publicly.
- **Data** — SQLite via `ReelDbHelper` (see below) and DataStore Preferences via `UserPreferencesManager`.
- **Services** — `ReelAccessibilityService` (detection), `FloatingMonsterOverlayService` (the Munchie overlay, foreground service, `exported=false`).
- **Domain** — pure-Kotlin logic under `domain/model/`: weekly intelligence (`weekly/`), backup/restore (`backup/`), goals, focus, progression, XP.

The app is offline-first by construction: there is no `INTERNET` permission, no HTTP client, no analytics SDK, and no account system. The only production logging is `DetectorLogger`, gated on `BuildConfig.DEBUG` (silent in release builds).

## Module / package structure

```text
app/src/main/java/com/reelmonster/app/
├── MainActivity.kt / ReelMonsterApp.kt / Screen.kt
├── data/
│   ├── db/            ReelDbHelper.kt (SQLite, schema v6)
│   ├── preferences/   UserPreferencesManager.kt (DataStore)
│   ├── repository/    ReelRepository.kt
│   └── backup/        BackupTableStore, BackupExporter, BackupImporter
├── domain/model/
│   ├── weekly/        HourlyScrollingPattern, WeeklyTrend, WeeklyDigest,
│   │                  WeeklyReflectionEngine, MunchieMastery, ActualViewingTime
│   └── backup/        BackupJson, BackupSchema, BackupModel, BackupValidator,
│                      RestorePlanner, RestoreJournal
├── service/
│   ├── tracking/      ReelAccessibilityService, detectors (Instagram/YouTube),
│   │                  DeduplicationEngine, MonitoringCoverageTracker,
│   │                  ViewingSessionTracker, FocusSessionTracker, DetectorLogger
│   └── overlay/      FloatingMonsterOverlayService, overlay content
└── presentation/      Dashboard, Statistics, History, Goals, Focus, Weekly
                       Intelligence, Settings, onboarding, components
```

## Database schema (v6)

`ReelDbHelper.kt` — database `reel_monster.db`, `DATABASE_VERSION = 6`. Upgrades are incremental (v2 viewing_sessions, v3 goals, v4 focus, v5 Phase 14 tables, v6 `boot_session_id` + `is_active` and `system_metadata`, with a clean cutover and no backdating). 17 tables:

| Table | Purpose |
|---|---|
| `reel_events` | One row per detected/logged reel (fingerprint, app, creator, title) |
| `daily_aggregates` | Per-day counts per app |
| `viewing_sessions` | Measured viewing sessions (start/end, duration, app) — the source of truth for viewing time |
| `daily_goals` / `daily_goal_records` | Goal definitions and per-day outcomes |
| `daily_goal_history` / `daily_goal_snapshots` | Historical goal evaluation |
| `focus_sessions` | Focus Mode sessions |
| `monitoring_intervals` / `monitoring_heartbeats` | Verified monitoring coverage |
| `daily_monitoring_coverage` | Per-day coverage totals; a day is "qualified" at ≥ 120 min verified coverage |
| `supported_app_detection_events` | Detection events per supported app |
| `xp_transactions` | XP ledger; `event_id` UNIQUE, inserted with CONFLICT_IGNORE (idempotent) |
| `user_progress` | Singleton row (id=1): level, XP, streak |
| `achievements` / `streak_records` | Unlocked achievements and streak history |
| `system_metadata` | App-level metadata (schema v6) |

Indexes exist on the monitoring and goal tables. There is **no index on `viewing_sessions(sessionDate)`** — recorded as a preventive observation (PERF-02), not fixed without measured evidence. `getViewingTimeForDate(dateStr): Long?` returns null when a day has no sessions (never zero-filled); `getViewingTimeForDateByApp(dateStr, targetApp)` is the measured per-platform variant (Phase 16, DEF-01 fix).

The legacy estimate `estimateTimeMinutes(reelCount) = reelCount × 1.5` is `@Deprecated` and is populated in the model only for backward compatibility — **no screen displays it** (Phase 15).

## Detection architecture

1. `ReelAccessibilityService` receives accessibility events while Instagram or YouTube is in the foreground.
2. Per-app detectors (`InstagramReelDetector`, `YouTubeShortsDetector`) extract the current reel's creator/title from the accessibility tree.
3. `DeduplicationEngine` suppresses repeats: a 450 ms dwell requirement and a 10 s bounce-back window, with LRU fingerprints.
4. A confirmed reel is logged through `ReelRepository.logReel()`, which runs a full `refresh()` (~15 DB queries plus goal evaluation, historical finalisation, focus/progression refresh). This is the known hot path (PERF-01, observation only).
5. `MonitoringCoverageTracker` records verified monitoring intervals and 15-minute heartbeats. **Heartbeats alone never satisfy the 120-minute qualification threshold** — only verified intervals count.
6. `ViewingSessionTracker` records measured sessions (minimum 100 ms) with distinct interruption statuses; sessions end on app-switch or service stop (`INTERRUPTED_SERVICE_STOP`).
7. All timing uses monotonic clocks (`SystemClock.elapsedRealtime`); wall-clock time is used only for dates and the 24-hour buckets.

## Overlay implementation

`FloatingMonsterOverlayService` is a foreground service (`FOREGROUND_SERVICE` + special-use type) that adds a `WindowManager` view for Munchie (requires `SYSTEM_ALERT_WINDOW`). Tap and long-press only change Munchie's personality lines — they have no side effects on statistics. Manual logging from the overlay is an explicit user action. `onDestroy` removes the view and stops the foreground state. `RebootReceiver` restarts the overlay only if it was enabled and the permission is still granted. All overlay `PendingIntent`s use `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`. The service is `exported=false`.

## Session tracking

- **Viewing sessions** are measured durations in `viewing_sessions`; a day with no sessions reports null, never zero.
- **Focus sessions** are managed by `FocusSessionTracker` (start/pause/resume/finish, persisted via DataStore). Restoring a session after a restart (`restoreActiveSessionState`) recomputes elapsed time from the wall-clock start and ignores the saved elapsed value (STAB-01, open), and it currently has **no production caller** — only the Phase 13 test calls it (STAB-03, open) — so a focus session is not actually restored after a restart in the released app.
- **Daily reset** (`resetTodayCountOnly`) deletes only today's rows inside a transaction and preserves history.

## Analytics engine (Phase 15)

- **Hourly pattern:** 24 local-hour buckets. Coverage per hour comes from eligible verified intervals only, clipped and merged per hour (ratio ≤ 1.0, DST-aware). States: Unmonitored (coverage 0), Insufficient data (ratio < 0.25), Partially monitored (0.25–0.80), Well monitored (≥ 0.80). Never extrapolates.
- **Week-over-week trends:** current window `[D-7, D-1]`, previous `[D-14, D-8]`; today is always excluded. A day is adequate at ≥ 120 min coverage. Confidence 0–3 = Insufficient (percentages suppressed), 4–5 = Limited, 6–7 = High; the lower confidence governs. Zero-baseline rules prevent NaN/infinity.
- **Actual viewing-time reconciliation:** all `count × 1.5` estimates were removed from reporting in Phase 15.
- **Weekly digest:** computed in memory; there is intentionally **no `weekly_digests` table**.
- **Mastery auras:** cosmetic only, derived from level (L3–4 sprout, L5–6 mint aura, L7–8 sparkle, L9–10 golden crown; streak ≥ 7 emblem). Never from reel count or duration.
- **Reflections:** deterministic, non-judgmental strings; tests assert banned (judgmental / mental-health) words are absent.

## XP and progression

XP is a ledger (`xp_transactions`, unique `event_id`, `CONFLICT_IGNORE` — replays are no-ops). Focus XP is keyed by `XpTransaction.focusSessionKey(session.id)`. Levels and streaks live in `user_progress` (singleton). Goal evaluation in `ReelRepository.evaluateGoals()` reads `isTrackingPaused` / `isGlobalTrackingEnabled` from `UserPreferencesManager`; a race in that path (STAB-04) was fixed in Phase 17 by reading the three persisted flags synchronously at construction (one deterministic `runBlocking` read; in-memory values are authoritative afterwards).

## Backup and restore (Phase 15)

- Hand-rolled strict JSON (no JSON dependency): a strict parser and a canonical serializer in `BackupJson.kt`.
- Checksum is SHA-256 over the canonical `tables` JSON only.
- `BackupValidator` validates the whole file **before any database mutation**; schema version > 6 is rejected (`UPGRADE_REQUIRED`), older versions are refused, never downgraded.
- Merge restore: per-table choice KEEP_EXISTING / USE_BACKUP / CANCEL (`RestorePlanner`).
- Full restore requires a verified pre-restore backup, runs in one transaction, and is journaled in a plain file in the app files directory (`RestoreJournal`) with startup recovery — the journal is not stored in the database.
- The backup/restore logic is tested with plain JUnit against an in-memory `BackupTableStore` (23 tests, no Robolectric needed for the logic).

## Permissions (with reasons)

| Permission | Reason |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Munchie floating overlay |
| `FOREGROUND_SERVICE` (+ special-use type) | Overlay service |
| `POST_NOTIFICATIONS` | Foreground-service notification |
| `RECEIVE_BOOT_COMPLETED` | Restart the overlay after reboot (only if enabled) |
| `BIND_ACCESSIBILITY_SERVICE` | Automatic reel detection |

No `INTERNET` permission. No accounts, no login, no analytics SDKs.

## Build configuration

`app/build.gradle.kts`: `applicationId com.reelmonster.app`, `namespace com.reelmonster.app`, `compileSdk 36`, `minSdk 26`, `targetSdk 36`, `versionCode 1`, `versionName "1.0.0"`. Release build type: `isMinifyEnabled = false` (R8/ProGuard files are referenced but minification is off). Debug build type: `applicationIdSuffix ".debug"`, `isDebuggable = true`. **The release and debug builds are therefore different packages and different apps on the phone — a release install is a fresh install and does not carry the debug app's data.** Build output is redirected to `~/.cache/reelmonster-build/app`. See `BUILD_AND_SIGNING_GUIDE.md` for the release signing setup.

## Testing

340 unit tests (JUnit 4 + Robolectric), **340/340 passing, 0 failures, 0 skipped** (final Phase 17 run, 2026-11-09). Test classes live in `app/src/test/java/com/reelmonster/app/`. No instrumented (on-device) tests are configured. Device validation is manual, on the Samsung Galaxy S24 Ultra, recorded in `FINAL_DEVICE_VALIDATION.md`.

## Known limitations (carried, all Low)

- **STAB-01:** focus restore drops pre-death pause time (needs a device kill test).
- **STAB-03:** focus restore has no production caller — sessions are not restored after restart.
- **DEF-02:** zero-reel days get no historical goal records, so a viewing-time goal day with 0 minutes (a success) is missing from success-rate stats.
- **DEF-03:** Android Auto Backup (`allowBackup=true`, backup rules include the full database) can upload reel history to the user's Google account — contradicts the "100% offline" claim until the owner chooses a fix.
- **PERF-01:** every `logReel()` runs a full refresh (~15 queries + 7-day finalisation).
- **PERF-02:** no index on `viewing_sessions(sessionDate)`.
- No performance measurements (launch/memory/CPU/battery) exist anywhere — none are claimed.
- Phase 16 and Phase 17 device validation were never performed on their debug builds; device validation for the release is recorded in `FINAL_DEVICE_VALIDATION.md`.
