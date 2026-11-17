# PHASE 17 — PERFORMANCE AUDIT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Method:** static source inspection. No device measurements were taken in this phase (see §1).

## 1. Headline (honest)

**No reproducible performance measurements exist for this app.** No cold/warm launch time, render time, memory, CPU, or battery numbers were recorded on the device — not in this phase and not in any earlier phase. This report contains no measured figures and invents none. The 1.2–2.4 ms figure from the Phase 15 proposal remains an **unverified estimate**.

Everything below is static analysis: what the code does, where work happens, and where a measurement would be needed before any optimisation.

## 2. Application performance (static)

- **Startup:** `ReelMonsterApp` loads initial state via `initializeInBackground()` on `Dispatchers.IO`, so `Application.onCreate()` never blocks the main thread. `initializeSync()` opens the database (running migrations if needed), initialises the coverage tracker, and runs one full `refresh()`. No measured launch time.
- **Per-reel work (PERF-01, observation — not a defect):** every `logReel()` call runs a full `refresh()`, which issues roughly 15 separate database reads (today count, platform breakdown, viewing times, averages, last-7-days, 7-day calendar series, recent events, period statistics) plus `evaluateGoals()`, `finalizeHistoricalGoalRecords()`, `refreshFocusStats()`, `finalizeHistoricalProgression()`, and `refreshProgressionState()`. The two `finalize*` calls re-scan the last 7 days on **every reel**. At human scrolling speed this is trivial for SQLite, and no slowness has been measured or reported — but it is repeated work that grows with usage, and it is the first place to measure if the dashboard ever feels slow. No refactor was applied without evidence.
- **Compose:** screens collect `StateFlow`s and load data in `LaunchedEffect` on `Dispatchers.IO` (verified in `StatisticsScreen`, `HistoryScreen`, `WeeklyIntelligenceScreen`). No recomposition measurement was taken.
- **Polling:** the accessibility service runs one 15-minute heartbeat coroutine; the viewing/focus trackers run a 500 ms ticker **only while a session is active**. No busy loops, no short-interval polling.

## 3. Memory (static)

- No measurement taken. No leak detected by inspection: the overlay service cancels its scope, jobs, and receiver in `onDestroy()` and removes the overlay view; the accessibility service cancels its heartbeat/dwell jobs and resets its state machine in `onDestroy()`/`onInterrupt()`. Whether any Compose or service reference is retained across process death was not dynamically verified.
- The in-memory dedup cache is an LRU-bounded map (fingerprints only), not unbounded growth.

## 4. CPU (static)

- Detection runs on accessibility events (event-driven, not polling). The detectors walk the view hierarchy only for the two target packages; dwell gating (450 ms) prevents counting flings.
- Analytics are computed on demand (screen load) or on `refresh()` (per reel, see PERF-01). No background analytics jobs exist.

## 5. Battery (static)

- No measurement taken. Contributors by design: foreground overlay service, accessibility service, 15-minute heartbeat. No wake locks were found in the audited files. No `WorkManager`/alarm-based polling exists.

## 6. Database (static)

- **Indexes:** 8 `CREATE INDEX IF NOT EXISTS` statements — on `monitoring_intervals` (date, boot_session_id), `monitoring_heartbeats` (date, boot_session_id), `supported_app_detection_events` (date), `daily_goal_history` (date, goal_id).
- **Observation (PERF-02, not a defect):** no index on `viewing_sessions(sessionDate)` or `reel_events(date)`, although analytics query them by date on every dashboard/stats/history load and the DEF-01 query adds up to ~14 extra per-date-per-platform queries per goal finalisation. At a single user's scale (hundreds of sessions) a full scan is trivial; no slowness measured. Adding `idx_viewing_sessions_date` (or `(sessionDate, targetApp)`) would be a small additive change if a measurement ever shows a problem; deliberately not added without evidence.
- **Transactions:** writes that must be atomic are transactional (`insertReelEvent` upsert, `resetTodayCountOnly`, `awardXpTransaction`, backup import). No transaction-overhead problem was found by inspection.
- **Database growth:** no measurement taken; the database holds one row per reel event plus sessions/intervals/heartbeats — growth is linear in usage, with no pruning policy. Not a problem at this scale; noted for a future phase if the database ever grows large.

## 7. Acceptance targets (§10) — status

| Target | Method | Result |
|--------|--------|--------|
| No reproducible crashes during the extended test | Device run | NOT VERIFIED |
| No unresolved critical/high security findings | Static audit | PASS (none found; SEC-01 Low fixed) |
| No database corruption | Static + tests | PASS (integrity checks in backup store; no corruption found) |
| No duplicate session records from event processing | Static + tests | PASS (dedupe engine + debounce + 5 integration tests) |
| No unexplained sustained memory growth | Device run | NOT VERIFIED |
| No unnecessary network dependency | Static audit | PASS (no network code or permission) |
| No broken tracking after supported lifecycle recovery | Static audit | PASS with one caveat (STAB-01, see stability audit) |
| No significant performance regression from Phase 16 | Comparison | NOT VERIFIED (no baseline measurements exist to compare against) |

## 8. Measurement method (for when it is done)

Record device model + Android version, dataset size (row counts per table), the operation timed, a monotonic clock, warm-up runs, ≥100 repetitions, median/p95/min/max, cold vs warm cache. Keep scripts and dataset with the report. Never quote a single-run or emulator number as a device result.
