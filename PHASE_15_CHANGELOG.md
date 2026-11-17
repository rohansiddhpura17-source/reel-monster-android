# PHASE 15 — CHANGELOG

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 15 — Weekly Intelligence & Mindful Mastery
**Date:** 2026-11-08/09
**Status:** Code complete, tests green (332/332). Device validation NOT VERIFIED (see `PHASE_15_DEVICE_VALIDATION.md`).

## Added

### Analytics domain (new files, `domain/model/weekly/`)
- `HourlyScrollingPattern.kt` — 24 local-hour buckets; DST-aware hour ranges (skipped/repeated hours); coverage from eligible verified intervals only, merged once per hour; rates null when coverage is zero; states Unmonitored / Insufficient / Partially monitored / Well monitored.
- `WeeklyTrend.kt` — seven-complete-day windows `[D-7, D-1]` vs `[D-14, D-8]`; adequate-day threshold 120 min verified coverage; confidence 0–3 / 4–5 / 6–7; lower confidence governs; percentage suppression when either week is Insufficient; zero-baseline rules; weekday/weekend groups with coverage-normalised rates.
- `WeeklyDigest.kt` — digest computed in memory from the current window; no `weekly_digests` table.
- `WeeklyReflectionEngine.kt` — deterministic, factual reflections (max 2, fixed priority); no mood/addiction/shame language.
- `MunchieMastery.kt` — mastery auras from level only (L3–4 sprout, L5–6 mint aura, L7–8 sparkle, L9–10 golden crown; streak ≥7 emblem). Growth stages untouched.
- `ActualViewingTime.kt` — reconciler: sums measured session durations only; untimed manual logs stay a count; average dwell denominator = measured sessions only.
- `WeeklyIntelligenceUseCase.kt` — `buildDayInputs`, `buildWeeklyDigest`, `buildHourlyPattern` over `[start of D-7, start of D)`.

### Backup and restore (new files, `domain/model/backup/` + `data/backup/`)
- Hand-rolled strict JSON (`BackupJson.kt` — no serialisation dependency); canonical serialiser (sorted keys); SHA-256 over the canonical `tables` JSON only.
- `BackupSchema.kt` — all 17 tables with exact columns/types, PKs, parent-first insert order; schema version 6.
- `BackupValidator.kt` — full pre-mutation validation (format/version, schema compatibility, checksums/counts, column sets, types/ranges, enums, dates, duplicate PKs, FKs, unique `event_id`s); collects all errors; newer schema → UPGRADE_REQUIRED, older → refused, never downgrades.
- `RestorePlanner.kt` — merge (default) / full restore; conflicts never silently resolved (KEEP_EXISTING / USE_BACKUP / CANCEL).
- `RestoreJournal.kt` — file-based journal (STARTED → PRE_BACKUP_DONE → COMMITTED | ROLLED_BACK) with startup recovery.
- `BackupTableStore.kt` / `BackupExporter.kt` / `BackupImporter.kt` — SQLite store with integrity + FK checks; transactional merge and full restore; full restore requires a verified pre-restore backup first.

### UI
- `presentation/weekly/WeeklyIntelligenceScreen.kt` — confidence banner, week-vs-week comparison, weekday vs weekend, 24-hour pattern, reflections, mastery card. Reached from a new "Weekly Intelligence" card on the Dashboard; route `weekly_intelligence` (flow screen, not a bottom tab).
- `presentation/backup/BackupRestoreSection.kt` — Create Backup (SAF create document), Merge restore and Replace-All restore (SAF open document), conflict dialog, destructive-restore confirmation, interrupted-restore notice. Inserted into Settings before the "Local SQLite Engine" card.

### Estimates removed (Stage 1)
- `StatisticsScreen.kt` and `HistoryScreen.kt` now show actual measured viewing time ("Unavailable" / "unmeasured" where no sessions exist) instead of `count × 1.5 minutes`.
- `DateTimeUtils.estimateTimeMinutes` marked `@Deprecated(WARNING)`; body unchanged for Phase 1–14 compatibility. `ReelDbHelper` (lines 927/967/1045) and `HistoryAnalyticsUseCase.kt:36` still populate the `estimatedMinutesLost` model field for backward compatibility — **no screen displays it**.
- Post-green cleanup: dead `igEstMinutes`/`ytEstMinutes` removed from `DashboardScreen`; `SevenDayActivityChart` "Estimated Time Lost" replaced with "Active Days".

## Changed
- `Screen.kt` — added `WeeklyIntelligence` flow screen + `allScreens` entry.
- `MainActivity.kt` — `WeeklyIntelligence` branch + dashboard callback on both `DashboardScreen` calls.
- `DashboardScreen.kt` — new `onNavigateToWeeklyIntelligence` param + Weekly Intelligence card.
- `SettingsScreen.kt` — Backup & Restore section inserted.

## Database
- **No schema change. Schema stays v6.** No `weekly_digests` table, no `night_drift_config`. No migration. No user data reset or recreated.

## Tests
- 65 new tests (42 analytics + 23 backup/restore). Full suite 332/332 green (see `PHASE_15_TEST_REPORT.md`).
- Two test-expectation bugs found and fixed during the runs (DST repeated hour; hardcoded date in Phase 12 test12). No production code was changed to satisfy a test.

## APK
- Built 2026-11-09 ~00:37 IST: `BUILD SUCCESSFUL in 36s`.
- Build output: `/Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/debug/app-debug.apk` (20,602,128 bytes).
- Copy: `~/Downloads/app-debug-phase15.apk` (same size).
- Installed on Samsung Galaxy S24 Ultra (RZCXA0ZAH6Z) 2026-11-09 ~00:43 IST (`adb install -r` → Success).
- **Note:** copying the APK into `release/app-debug.apk` on the PortableSSD fails with a bogus "not enough free space" error despite 372 GiB free (`df -h` verified 2026-11-09). Copies to the internal disk succeed. `release/app-debug.apk` still holds the Phase 14 build.
- Pre-Phase-15 backup: `~/Downloads/app-debug-pre-phase15-20261108.apk` (20,454,672 bytes).

## Known limitations (honest)
- Device validation NOT VERIFIED: the APK was installed, but no on-phone checks were reported (data intact, weekly screen, Stats/History times, backup creation, old tracking).
- The 1.2–2.4 ms performance figure from the proposal remains an **estimate**; the §9 benchmark protocol was not run.
- Mastery cosmetics are NOT rendered in the floating Munchie overlay (out of scope for this phase); they appear only in the Weekly Intelligence screen.
- Restore-journal recovery is surfaced in Settings, not wired into app-startup initialisation.
- Positive detection of a real person by the Phase 3 YOLO model, the Mac docker compose run, and the joined upload→worker→results round-trip remain unverified from Phase 3/4 (unchanged by this phase).
