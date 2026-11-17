# PHASE 16 — CHANGELOG

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 16 — Final Integration & Quality Assurance
**Date:** 2026-11-09
**Status:** IN PROGRESS — audit, fix, and tests done. Clean build and device validation still open.

## Added

- `ReelDbHelper.getViewingTimeForDateByApp(dateStr, targetApp): Long?` — measured per-platform viewing time for a date; null when the platform has no sessions that day (never zero-filled), mirroring `getViewingTimeForDate`.
- `Phase16IntegrationTest` — 5 new Robolectric integration tests (viewing-time→goals, focus→viewing separation, XP idempotency, reset safety across days).
- Reports: `PHASE_16_IMPLEMENTATION_REPORT.md`, `PHASE_16_TEST_REPORT.md`, `PHASE_16_DEFECT_LOG.md`, `PHASE_16_PERFORMANCE_REPORT.md`, plus the owed Phase 15 reports (`PHASE_15_TEST_REPORT.md`, `PHASE_15_CHANGELOG.md`, `PHASE_15_DEVICE_VALIDATION.md`).

## Fixed

- **DEF-01 (Medium):** historical per-platform viewing-time goals were evaluated from a proportional estimate (reel-count share) instead of measured durations. `finalizeHistoricalGoalRecords()` now uses measured per-platform minutes. Regression tests: `Phase16IntegrationTest` test01 + test02 (test02 fails on the old code).

## Changed

- `ReelRepository.finalizeHistoricalGoalRecords()` — INSTAGRAM/YOUTUBE viewing-time goals now read measured durations; COMBINED goals unchanged (already measured).
- No other production code changed. No new product features. No Phase 17 work.

## Database

- **No schema change. Schema stays v6.** No migration. No user data reset or recreated.

## Tests

- **337 tests run, 337 passed, 0 failed, 0 skipped, 0 ignored** (`./gradlew testDebugUnitTest`, 2026-11-09 ~01:49 IST, BUILD SUCCESSFUL in 1m 10s). See `PHASE_16_TEST_REPORT.md`.
- No existing test was deleted, weakened, skipped, or rewritten.

## APK

- Built 2026-11-09 ~01:59 IST: `./gradlew assembleDebug` → **BUILD SUCCESSFUL in 10s**.
- Build output: `/Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/debug/app-debug.apk` (**20,658,312 bytes**).
- Copy: `~/Downloads/app-debug-phase16.apk` (same size).
- Install on the Samsung: pending (device validation step).
- Previous APK (Phase 15): `~/Downloads/app-debug-phase15.apk` (20,602,128 bytes), installed on the Samsung (Success, 2026-11-09 ~00:43 IST).

## Known limitations (honest)

- **DEF-02 (Low, OPEN):** zero-reel days get no historical goal records — awaiting ROHAN's decision on the business rule.
- **DEF-03 (Low, OPEN):** Android Auto Backup includes the full database — awaiting ROHAN's decision (exclude DB from backup rules vs `allowBackup=false`).
- Device validation pending (phone reconnected 2026-11-09 ~01:44 IST; install + on-phone checks outstanding).
- Performance: no reproducible measurements taken in this phase; the 1.2–2.4 ms figure remains an unverified estimate (see `PHASE_16_PERFORMANCE_REPORT.md`).
- No Git repo in the project; the pre-Phase-16 backup at `~/Downloads/reel-monster-pre-phase16-backup` is the only rollback point.
- Phase 15 device validation is NOT VERIFIED (checks never reported).
