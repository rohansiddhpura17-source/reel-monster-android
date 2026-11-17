# PHASE 17 — CHANGELOG

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Status:** IN PROGRESS — audits and one fix done; test run, build, and device validation still open.

## Added

- `Phase17SecurityAuditTest` — 3 new Robolectric tests for diagnostic-logging gating (SEC-01).
- Reports: `PHASE_17_SECURITY_AUDIT.md`, `PHASE_17_PRIVACY_AUDIT.md`, `PHASE_17_PERFORMANCE_AUDIT.md`, `PHASE_17_STABILITY_AUDIT.md`, `PHASE_17_OPTIMIZATION_REPORT.md` (this changelog; test/device-validation reports still to come).

## Fixed

- **SEC-01 (Low):** `DetectorLogger.isEnabled` now defaults to `BuildConfig.DEBUG` — release builds no longer write viewing activity to logcat. Debug builds keep full diagnostics.

## Changed

- `DetectorLogger.kt` only (one default value + comment + import). No other production code changed. No new features. No business-rule changes. No schema change.

## Database

- **No schema change. Schema stays v6.** No migration. No user data reset or recreated.

## Dependencies

- None changed. No upgrades, additions, or removals.

## Tests — GREEN

- Final run 2026-11-09 ~04:00 IST: **340/340 passed, 0 failed, 0 skipped** (verified via XMLs: Phase12 16/16, Phase17SecurityAuditTest 3/3). Three failures were found and fixed along the way (STAB-02 test race, STAB-04 production race incl. test06/test09) — see `PHASE_17_TEST_REPORT.md`.
- No existing test was deleted, weakened, or skipped. One test (test07) was made deterministic (waits for the DataStore flow to propagate before restoring).

## APK — BUILT, NOT INSTALLED

- Phase 17 APK: `~/Downloads/app-debug-phase17.apk` (20,602,128 bytes, clean + `assembleDebug` BUILD SUCCESSFUL 2026-11-09 ~04:02 IST). **Never installed; on-phone checks never reported** — see `PHASE_17_DEVICE_VALIDATION.md` (all items NOT VERIFIED, superseded by Phase 18 release validation).
- Last installed APK on the phone is the Phase 16 one: `~/Downloads/app-debug-phase16.apk` (20,658,312 bytes, built 2026-11-09 ~01:59 IST), installed on the Samsung (Success, ~02:01 IST).

## Known limitations (honest)

- **STAB-01 (Low, OPEN):** focus restore drops pre-death pause time — fix deferred until a device kill test confirms it.
- **DEF-02 (Low, OPEN, Phase 16):** zero-reel days get no historical goal records — awaiting ROHAN's decision.
- **DEF-03 (Low, OPEN, Phase 16):** Android Auto Backup includes the full database — awaiting ROHAN's decision.
- **Phase 16 device validation is still OPEN** (on-phone checks never reported); carried into Phase 17 as an open item, not claimed complete.
- **No performance measurements exist** (no launch/memory/CPU/battery numbers); the 1.2–2.4 ms figure remains an unverified estimate.
- No Git repo in the project; the pre-Phase-17 backup at `~/Downloads/reel-monster-pre-phase17-backup` is the only rollback point.
