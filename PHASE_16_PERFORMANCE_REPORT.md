# PHASE 16 — PERFORMANCE REPORT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 16 — Final Integration & Quality Assurance
**Date:** 2026-11-09
**Device:** Samsung Galaxy S24 Ultra (SM-S928B), Android 17 / API 37, ADB serial `RZCXA0ZAH6Z` (reconnected 2026-11-09 ~01:44 IST)

## Headline (honest)

**No reproducible performance measurements were taken in this phase.** No launch-time, render-time, memory, CPU, battery, or query-latency numbers were recorded on the device, so this report contains no measured figures and invents none.

The 1.2–2.4 ms figure mentioned in the Phase 15 proposal remains an **unverified estimate**. The proposal's §9 benchmark protocol (device, row counts, ≥100 runs after warm-up, median/p95/min/max, monotonic clock, cold vs warm cache, preserved scripts/datasets) was not run.

## Static assessment (from source inspection, not measurement)

- **Indexes:** 8 `CREATE INDEX IF NOT EXISTS` statements exist in `ReelDbHelper` — on `monitoring_intervals` (date, boot_session_id), `monitoring_heartbeats` (date, boot_session_id), `supported_app_detection_events` (date), and `daily_goal_history` (date, goal_id). The monitoring/coverage queries — the ones that scan the most rows — are indexed.
- **Observation (not a defect, no fix applied):** there is **no index on `viewing_sessions(sessionDate)`** or on `reel_events(date)`, although the analytics paths query them by date on every dashboard/stats/history load (`getViewingTimeForDate*`, `getTodayViewingTimeMs`, `getEventsForDate`) and the new DEF-01 query adds up to ~14 extra per-date-per-platform queries per goal finalisation (7 days × 2 platforms). At a single user's data scale (hundreds of sessions) a full scan is trivial, so this is not a verified problem — only a preventive note. Adding `idx_viewing_sessions_date` (or `(sessionDate, targetApp)`) would be a small additive change if a measurement ever shows a problem; it was deliberately not added without evidence.
- **Work done on the IO thread:** Phase 15's weekly digest/hourly computation runs on `Dispatchers.IO` from the UI (`WeeklyIntelligenceScreen` loads in a `LaunchedEffect` on `Dispatchers.IO`); backup export/import runs through the SAF pickers and the transactional importer. No main-thread database access was found in the audited paths.
- **Test-run timing (VM/Mac only, not device performance):** full unit suite 337 tests in ~1m 10s wall time on the Mac (`./gradlew testDebugUnitTest`, 2026-11-09 ~01:49 IST); Phase 15 APK build 36 s. These are build-machine timings, not app performance.

## What was NOT measured (and why)

| Metric | Status | Reason |
|--------|--------|--------|
| App launch time | NOT MEASURED | No device run with timing in this phase |
| Dashboard / Stats / Weekly screen render time | NOT MEASURED | No device run with timing |
| Database query latency (incl. the new per-platform query) | NOT MEASURED | No benchmark protocol run |
| Memory / CPU / battery during tracking | NOT MEASURED | No profiler session |
| Overlay responsiveness | NOT MEASURED | No device interaction timing |
| Long-running tracking stability | NOT MEASURED | No soak test |

## Measurement method (for when it is done)

To make any future number reproducible, record: device model + Android version, exact dataset (row counts per table, from the on-device database), the operation timed, the clock used (monotonic), warm-up runs, ≥100 repetitions, median/p95/min/max, and cold vs warm cache. Keep the scripts and dataset with the report. Do not quote a single-run or emulator number as a device result.

## Verdict

No verified performance problem was found, and no performance fix was made. The only action is the index observation above, recorded for a future measured check — not acted on without evidence.
