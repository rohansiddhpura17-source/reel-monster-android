# Phase 15 — Implementation Checklist

**Project:** Reel Monster (`/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/`)
**Spec:** `PHASE_15_PROPOSAL.md` (single source of truth)
**Baseline (verified 2026-11-08):** SQLite schema v6, Phase 14 cutover intact, 267 tests reported passing (not independently re-counted by TAN), APK `release/app-debug.apk` 20,454,672 bytes.
**Pre-Phase-15 backup:** `~/Downloads/app-debug-pre-phase15-20261108.apk` on the Mac (internal disk). NOTE (corrected 2026-11-09): copying the APK into `release/backups/` on the PortableSSD FAILED with a "not enough free space" error, but `df -h` on 2026-11-09 shows 372 GiB free on the SSD — so space was NOT the problem and no freeing is needed. The copy failure's real cause is unknown; the Downloads backup is the valid pre-Phase-15 copy.

## Stage 0 — Baseline verification ✅ DONE 2026-11-08
- [x] DATABASE_VERSION = 6 confirmed in `ReelDbHelper.kt:59`; onUpgrade chain v2 viewing_sessions / v3 goals / v4 focus / v5 Phase 14 tables / v6 boot_session_id + system_metadata + clean cutover (no backdating).
- [x] No `weekly_digests` table, no `night_drift_config` — must stay that way.
- [x] `estimateTimeMinutes = reelCount * 1.5` confirmed (`DateTimeUtils.kt:56-57`; used in `ReelDbHelper:927,967,1045`, `StatisticsScreen:175,195,225,244`, `HistoryScreen:204-205,431`).
- [x] Discrepancies recorded: (1) proposal header still says "Proposal for approval only" — ROHAN's pasted 2026-11-08 23:49 instruction is the approval; (2) instruction §2A says "Fully monitored", proposal §2 says "Well monitored" (>= 0.80) — proposal wins.
- [x] Phone: ROHAN says the phone is connected. Muse's device list shows only the MacBook Air — no phone paired to Muse. The Samsung S24 Ultra (RZCXA0ZAH6Z) is therefore connected to the Mac (USB/ADB), not to TAN. TAN cannot run Terminal/ADB on the Mac; build + device validation must run on the Mac.

## Stage 1 — Data correctness ✅ DONE (screens migrated; NOT COMPILED yet — needs Mac Gradle build)
- [x] `estimateTimeMinutes` marked `@Deprecated(WARNING)` with a pointer to actual session durations. Function body unchanged, so existing Phase 1–14 callers/tests still compile.
- [x] `ActualViewingTimeReconciler` added: actual measured durations only, dedupe by session id, untimed manual logs stay a count (no duration invented), average dwell denominator = measured sessions only.
- [x] `estimateTimeMinutes` USE removed from `StatisticsScreen.kt` (today card now shows actual viewing time + avg. dwell; platform cards show measured per-platform time or "unmeasured"; Last 7 Days rows show actual time or "unmeasured") and `HistoryScreen.kt` (period StatCard now "Viewing Time" with missing-day count; selected-day line shows actual time or "unmeasured"). Verified by grep on the Mac copies: zero remaining estimate usages in either screen. `ReelDbHelper.kt` (927, 967, 1045) intentionally left populating the `estimatedMinutesLost` model field for backward compatibility — no screen reads it any more.
- [x] Remaining display usages cleaned up 2026-11-09 after the green test run: `DashboardScreen.kt` dead `igEstMinutes`/`ytEstMinutes` values deleted (platform cards already showed actual viewing time); `SevenDayActivityChart.kt` "Estimated Time Lost" column replaced with "Active Days" (count of days with reels > 0). `HistoryAnalyticsUseCase.kt:36` still computes `estimatedMinutesLost` for the `PeriodStatistics` model field (existing tests assert it) — no screen displays it.

## Stage 2 — Analytics domain ✅ DONE (code + tests written, NOT RUN)
- [x] `domain/model/weekly/HourlyScrollingPattern.kt` — 24 DST-aware hour buckets, C(h)/R(h)/T(h), coverage ratios, Unmonitored / Insufficient / Partial / Well monitored, rates only when C(h) > 0, overlaps merged once, sessions split at hour/midnight boundaries.
- [x] `domain/model/weekly/WeeklyTrend.kt` — windows [D-7, D-1] vs [D-14, D-8], today excluded, adequate day >= 120 min, confidence 0–3 Insufficient / 4–5 Limited / 6–7 High, lower confidence governs, zero-baseline delta rules, weekday/weekend with coverage.
- [x] `domain/model/weekly/WeeklyDigest.kt` — local digest, no `weekly_digests` table, focus kept separate from viewing.
- [x] `domain/model/weekly/WeeklyReflectionEngine.kt` — deterministic, factual, max 2 reflections, no mood/addiction/shame language.
- [x] `domain/model/weekly/MunchieMastery.kt` — auras by level only (L3–4 sprout, L5–6 mint, L7–8 sparkle, L9–10 crown), streak emblem >= 7d. Growth stages untouched.
- [x] `domain/usecase/WeeklyIntelligenceUseCase.kt` — builds digest + hourly pattern from `ReelDbHelper` (schema v6 tables only). Call on `Dispatchers.IO`.
- [x] Tests: `Phase15HourlyAnalyticsTest` (14), `Phase15WeeklyTrendTest` (15), `Phase15ReflectionAndMasteryTest` (13) = **42 new tests**. Written, NOT RUN — no Java/Android SDK in TAN's VM and TAN cannot run Terminal on the Mac.

## Stage 3 — Weekly UI ✅ DONE (code written, NOT COMPILED — needs Mac Gradle build)
- [x] Dashboard compact Weekly Intelligence card (tap opens full report); `onNavigateToWeeklyIntelligence` wired through `MainActivity`.
- [x] `presentation/weekly/WeeklyIntelligenceScreen.kt` — confidence banner, current vs previous week (reels / viewing / focus + deltas or suppressed notice), weekday vs weekend (coverage-normalized rates), 24-hour pattern with per-hour coverage bar + status, digest + reflections, mastery. Loads via `WeeklyIntelligenceUseCase` on `Dispatchers.IO`, with loading + error/retry states.
- [x] Statistics/History migrated to actual durations (see Stage 1).
- [x] `Screen.WeeklyIntelligence` route added (flow screen, NOT a 5th bottom tab); wired in `MainActivity` (Home + fallback Dashboard calls, explicit branch, back to Home).

## Stage 4 — Digest / reflections in UI ✅ DONE (same screen as Stage 3; NOT COMPILED)
- [x] Digest + reflections surfaced through `WeeklyIntelligenceUseCase` on `Dispatchers.IO`.

## Stage 5 — Munchie cosmetics in UI ✅ DONE in Weekly Intelligence screen (NOT COMPILED)
- [x] `MunchieMastery` aura + streak emblem rendered in the Weekly Intelligence screen (level/streak only, no idle animation loop). NOT rendered in the floating `MunchieMascot` overlay — overlay service changes were out of scope for this pass; the overlay mascot is unchanged.

## Stage 6 — Backup / restore ✅ DONE (domain + tests written, NOT RUN; SAF UI entry points pending in Stage 3)
- [x] `BackupJson` (strict dependency-free JSON, canonical form), `BackupSchema` (all 17 schema-v6 tables, exact columns/PKs), `BackupModel` (manifest + SHA-256 over canonical tables JSON), `BackupValidator` (size/format/schema/counts/checksum/columns/types/ranges/enums/duplicate PKs/FKs — all before any mutation; newer schema -> UPGRADE_REQUIRED, older -> refused).
- [x] `RestorePlanner` (merge default: insert new / skip identical / conflict never silently overwritten — KEEP_EXISTING / USE_BACKUP / CANCEL; full restore plan reports deletions).
- [x] `BackupExporter` / `BackupImporter` / `BackupTableStore` (+ thin SQLite implementation): full restore requires a verified pre-restore backup first, runs in ONE transaction with count + integrity verification, rolls back on any failure; journal (`RestoreJournal`) in a file outside the DB, with startup recovery for interrupted restores.
- [x] Tests: `Phase15BackupRestoreTest` (23) — JSON canonical/strictness, checksum, exporter->validator round-trip, tamper/format/schema/count/duplicate/enum/FK/date rejections, planner policies, merge round-trip + idempotency, full restore + pre-backup requirement, rollback on injected failure, journal interrupted/committed recovery. Total Phase 15 tests now 65 (42 + 23).
- [x] Settings / Data Management entry points: `presentation/backup/BackupRestoreSection.kt` in Settings (Create Backup via SAF CreateDocument, Merge restore + Replace-All restore via OpenDocument, explicit conflict dialog KEEP_EXISTING / USE_BACKUP / CANCEL, full restore creates + verifies a pre-restore backup first, interrupted-restore notice on Settings open). NOT COMPILED yet.

## Stage 7 — Regression / device verification 🔶 TESTS GREEN, APK + DEVICE PENDING
- [x] On the Mac, `./gradlew testDebugUnitTest` — **332/332 PASSED** (2026-11-09 ~00:35 IST, BUILD SUCCESSFUL in 47s). Path there: 3 compile errors fixed first (BackupImporter qualifier, BackupTableStore when-exhaustiveness, test-file asObj/asStr helpers), then 330/332 with 2 failures — a wrong DST expectation in TAN's own test (repeated hour is 01:00, not 02:00) and a time-bombed Phase 12 test (hardcoded 'today' 2026-11-08) — both fixed, re-run green.
- [x] `./gradlew assembleDebug` — **BUILD SUCCESSFUL in 36s** (2026-11-09 ~00:37 IST). New APK: `/Users/rohansiddhpura/.cache/reelmonster-build/app/outputs/apk/debug/app-debug.apk`, 20,602,128 bytes (old Phase 14 APK was 20,454,672). Copied to `~/Downloads/app-debug-phase15.apk` (same size). NOTE: copying it into `release/app-debug.apk` on the PortableSSD FAILED with the same bogus "Not enough free space" error as the earlier backup copy, despite `df -h` showing 372 GiB free — copies onto this SSD fail that check while copies to the internal disk succeed. `release/app-debug.apk` still holds the Phase 14 build; the Phase 15 APK lives in Downloads + the build output folder. The estimate-deprecation warnings are GONE from the main compile after the Dashboard/chart cleanup; only pre-existing warnings remain (LocalLifecycleOwner, always-true conditions).
- [ ] Install on Samsung S24 Ultra (RZCXA0ZAH6Z) and validate per proposal §7: hourly analytics, partial/unmonitored periods, 7-day comparisons, confidence labels, reflections, backup creation, merge restore, full restore, recovery after interrupted restore, existing tracking/focus.
- [ ] Install on Samsung S24 Ultra (RZCXA0ZAH6Z) and validate per proposal §7: hourly analytics, partial/unmonitored periods, 7-day comparisons, confidence labels, reflections, backup creation, merge restore, full restore, recovery after interrupted restore, existing tracking/focus.
- [ ] Performance: the 1.2–2.4 ms figure is UNVERIFIED — label it an estimate unless the §9 protocol is run (100 runs, median/p95/min/max, cold + warm cache).
- [ ] Write `PHASE_15_TEST_REPORT.md`, `PHASE_15_DEVICE_VALIDATION.md`, `PHASE_15_CHANGELOG.md`; update `PROJECT_TRACKER.md` Phase 15 row only to what is actually true.

## Hard prohibitions (proposal + ROHAN's instructions)
- No Phase 16. No `weekly_digests` / `night_drift_config`. No schema change without a justified, tested, additive migration. No XP/streak rule changes. No cloud/accounts/internet. No TikTok. No estimated durations in reporting. No treating missing data as zero. No fabricated benchmarks. No device-verification claims without doing it. No deleting historical data.
