# PHASE 17 — OPTIMIZATION REPORT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Status:** IN PROGRESS — one fix applied; the rest are recorded decisions, not changes. Test run still pending.

## Rule followed

Per the brief §7: apply only changes supported by measured evidence or reproducible defects. No architecture rewrite, no detection-logic replacement, no business-rule change, no new feature, no privacy weakening.

## Fixes applied

### SEC-01 — detection logging release-gated (Low, security)

- **Problem:** `DetectorLogger` wrote detailed viewing activity (apps, creators, titles, counts) to logcat on every reel, in all builds.
- **Fix:** `isEnabled` now defaults to `BuildConfig.DEBUG`. Debug builds keep full diagnostics; release builds stay silent. Runtime toggle preserved for live debugging.
- **Files:** `app/src/main/java/com/reelmonster/app/service/tracking/DetectorLogger.kt`.
- **Regression tests:** `Phase17SecurityAuditTest` (3 tests: disabled logger writes nothing; enabled logger writes with the `ReelMonsterDetector` tag; debug build keeps diagnostics enabled by default).
- **Verification:** test run pending (`./gradlew testDebugUnitTest`).

## Deliberately NOT changed (with reasons)

### PERF-01 — `refresh()` on every reel (observation, not a defect)

`logReel()` runs a full `refresh()` (~15 database reads + goal/focus/progression finalisation, including 7-day re-scans) on every counted reel. No slowness has been measured or reported, and at human scrolling speed the work is trivial for SQLite. Optimising it (debouncing, splitting finalisation out of the per-reel path) without a measurement would be a blind change. Recorded in `PHASE_17_PERFORMANCE_AUDIT.md` as the first place to measure if the dashboard ever feels slow.

### PERF-02 — no index on `viewing_sessions(sessionDate)` (observation, not a defect)

Analytics query `viewing_sessions` and `reel_events` by date on every dashboard/stats/history load, with no index on those columns (8 indexes exist, all on monitoring/goal tables). At a single user's scale a full scan is trivial and nothing is measured slow. Adding `idx_viewing_sessions_date` (or `(sessionDate, targetApp)`) is a small additive change reserved for when a measurement shows a problem.

### STAB-01 — focus restore drops pre-death pause time (Low, open)

`FocusSessionTracker.restoreActiveSessionState()` ignores the saved elapsed value and recomputes from the wall-clock start, so a kill-while-paused focus session over-counts pause time. Fix deferred until a device kill test confirms the behaviour — the fix (restore saved elapsed + paused state, re-base the monotonic start) should be verified against that test, not applied blind.

### DEF-02 (Phase 16, Low, open)

Zero-reel days get no historical goal records. Changing goal-history semantics is a business-rule decision for ROHAN; no fix applied.

### DEF-03 (Phase 16, Low, open)

Android Auto Backup includes the full database. Fix (exclude DB from backup rules vs `allowBackup=false`) is ROHAN's decision; no fix applied.

## Dependency changes

None. No upgrades, no additions, no removals. (Audit: AndroidX + coroutines only; no third-party libraries; versions current per `gradle/libs.versions.toml`.)

## Before / after measurements

None exist — no performance measurements were taken in this phase or any earlier phase, so there is no baseline to compare against and no after-figure to report. The one applied change (SEC-01) is a logging default, not a performance optimisation; its effect is silence in release logcat, not speed.
