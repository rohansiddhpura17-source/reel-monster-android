# PHASE 17 — TEST REPORT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Runner:** ROHAN on his Mac (`./gradlew testDebugUnitTest`, Gradle 9.5.0)

## Result — GREEN

| Run | Time (IST) | Tests | Passed | Failed | Skipped |
|-----|------------|-------|--------|--------|---------|
| Phase 16 baseline | 2026-11-09 ~01:49 | 337 | 337 | 0 | 0 |
| Phase 17 run 1 | 2026-11-09 ~03:21 | 340 | 339 | 1 | 0 |
| Phase 17 run 2 (filtered, Phase13 only) | 2026-11-09 ~03:26 | 20 | 20 | 0 | 0 |
| Phase 17 run 3 | 2026-11-09 ~03:29 | 340 | 339 | 1 | 0 |
| Phase 17 run 4 | 2026-11-09 ~03:32 | 340 | 339 | 1 | 0 |
| Phase 17 run 5 (filtered, Phase12 only) | 2026-11-09 ~03:54 | 16 | 15 | 1 | 0 |
| **Phase 17 final** | **2026-11-09 ~04:00** | **340** | **340** | **0** | **0** |

**Final: 340/340 green, 0 failures, 0 skipped.** Verified from the Gradle `BUILD SUCCESSFUL` result plus the per-class XML reports (`Phase12DailyGoalsTest` 16/16, `Phase17SecurityAuditTest` 3/3). No existing test was deleted, weakened, skipped, or rewritten (one test, test07, was made deterministic — see below).

## New tests (3)

`Phase17SecurityAuditTest` (Robolectric, 3 tests) for SEC-01: disabled logger writes nothing to logcat; enabled logger writes with the `ReelMonsterDetector` tag; debug build keeps diagnostics enabled by default.

## Failures found and fixed during this phase

All three failures were in the tracking-flags / focus-restore area, and all three were real races, not test bugs:

| # | Test | Symptom | Root cause | Fix |
|---|------|---------|------------|-----|
| 1 | `Phase13FocusModeTest.test07` (STAB-02) | "Session should be safely restored" — restore returned false | `restoreActiveSessionState` reads `stateIn(Eagerly)` flows synchronously, but they update asynchronously after a DataStore edit | Test-only fix: wait for the flow to propagate before restoring |
| 2 | `Phase12DailyGoalsTest.test06` (STAB-04) | Expected IN_PROGRESS, was PAUSED after resume | Continuous DataStore collectors re-applied stale emissions and undid a pause → resume | v1: one-shot seeds; v2: generation-guarded seeds; v3: synchronous `runBlocking` read at construction (see below) |
| 3 | `Phase12DailyGoalsTest.test09` (STAB-04) | Expected APPROACHING_LIMIT, was PAUSED | Same family: a one-shot seed applied a previous test's persisted `global=false` after a clear (diagnostic print proved `global=false`) | v3: synchronous read at construction; both async-seed designs removed |

**STAB-04 v3 (final fix):** `UserPreferencesManager` now reads the three persisted tracking flags (`isGlobalTrackingEnabled`, `isTrackingPaused`, `trackingMode`) synchronously with `runBlocking` in `init` — one deterministic read at construction, after which the in-memory values are authoritative and only the setters / `clearPreferences` change them. The generation-guard machinery and the diagnostic print were removed. This also fixes the underlying production race (a fast pause → resume could previously leave the goal state stuck on PAUSED).

## Warnings (noise, not failures)

- The `estimateTimeMinutes` deprecation warnings in the build output are the intentional Phase 15 ones (the function is kept for backward compatibility; no screen displays it).
- Robolectric `System.logW` CloseGuard warnings (SQLiteDatabase opened in test `setUp` without `close()`) are pre-existing test noise; they do not fail anything.
- The Gradle native-access warning (`java.lang.System::load`, `--enable-native-access`) is a JDK/Gradle environment warning, not an app issue.

## What was NOT tested

- No device run in this phase (no on-phone checks yet) — see `PHASE_17_DEVICE_VALIDATION.md` (still to be written after device validation).
- No performance measurements (launch/memory/CPU/battery) — none exist; see `PHASE_17_PERFORMANCE_AUDIT.md`.
- STAB-01 (focus restore drops pre-death pause time) is still open and needs a device kill test; STAB-03 (focus restore has no production caller) is still open.
