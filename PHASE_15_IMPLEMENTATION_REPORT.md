# Phase 15 — Implementation Report (PARTIAL: Stages 0–2)

**Project:** Reel Monster — BSERC Week 2, Project 06
**Phase:** 15 — Weekly Intelligence & Mindful Mastery
**Status:** IN PROGRESS — Stages 0–6 code done. Only Stage 7 (test run, APK, device validation) remains. NOT COMPLETE.
**Update 2026-11-09 ~00:10 IST:** Stage 6 backup/restore implemented (see §6 below). Total new tests: 65 (42 analytics + 23 backup/restore). Still NOT RUN, no APK, no device validation.
**Update 2026-11-09 ~00:20 IST:** Stage 1 presentation migration + Stages 3–5 UI implemented (see Stages 1, 3–5 section below). All Phase 15 code is now written; nothing has been compiled yet — the Mac Gradle build is the next gate.
**Date:** 2026-11-08
**Spec:** `PHASE_15_PROPOSAL.md` (single source of truth)
**Baseline:** SQLite schema v6, Phase 14 signed off, 267 tests reported passing.

## 1. What happened before this report

ROHAN approved Phase 15 implementation on 2026-11-08 23:49 IST with pasted
Antigravity instructions. A first background helper attempt was stopped
immediately by a safety policy (2026-11-08 23:51 IST); no code was written
then. ROHAN then insisted in a later turn ("u have to do", phone connected)
that TAN do it. Per the refusal note — a fresh try is allowed when the user
asks again in a later turn — TAN resumed the work directly (no subagent),
starting with Stage 0 verification and Stages 1–2.

## 2. Stage 0 — Baseline verification (verified on the Mac)

- `DATABASE_VERSION = 6` (`ReelDbHelper.kt:59`); onUpgrade chain v2
  viewing_sessions / v3 goals / v4 focus / v5 Phase 14 tables / v6
  `boot_session_id` + `system_metadata` + clean cutover (never backdated).
- No `weekly_digests` table, no `night_drift_config` present.
- `estimateTimeMinutes = reelCount * 1.5` confirmed in
  `DateTimeUtils.kt:56-57`, used in `ReelDbHelper:927,967,1045`,
  `StatisticsScreen:175,195,225,244`, `HistoryScreen:204-205,431`.
- Build: compileSdk 36, minSdk 26, targetSdk 36, Kotlin toolchain 21,
  Compose BOM, DataStore, Robolectric 4.14.1. Existing APK
  `release/app-debug.apk`, 20,454,672 bytes.
- Discrepancies: (1) proposal header still says "Proposal for approval
  only" — ROHAN's instruction is treated as the approval; (2) instruction
  §2A says "Fully monitored", proposal §2 says "Well monitored" (>= 0.80)
  — proposal wording used.
- Phone: Muse's device list shows only the MacBook Air; no phone is
  paired to Muse. The Samsung S24 Ultra is connected to the Mac, not to
  TAN. TAN cannot run Terminal/ADB on the Mac.

## 3. Pre-Phase-15 backup

- `release/app-debug.apk` copied to
  `/Users/rohansiddhpura/Downloads/app-debug-pre-phase15-20261108.apk`
  (20,454,672 bytes, verified copy OK).
- Copying into `release/backups/` on the PortableSSD FAILED with a "not
  enough free space" error. CORRECTED 2026-11-09: `df -h` shows 372 GiB
  free on the SSD, so space was not the problem and nothing needs
  freeing; the copy failure's real cause is unknown.

## 4. Files created (all verified on the Mac by size + spot-read)

New package `domain/model/weekly/`:

| File | Bytes | Contents |
|---|---|---|
| `HourlyScrollingPattern.kt` | 11,503 | 24 DST-aware hour buckets; C(h)/R(h)/T(h); coverage ratios; Unmonitored / Insufficient / Partial / Well monitored; rates only when C(h) > 0; overlaps merged once; sessions split at hour/midnight boundaries |
| `WeeklyTrend.kt` | 10,917 | Windows [D-7, D-1] vs [D-14, D-8]; today excluded; adequate day >= 120 min; confidence Insufficient/Limited/High; lower confidence governs; zero-baseline delta rules; weekday/weekend |
| `WeeklyDigest.kt` | 2,220 | Local digest; no `weekly_digests` table; focus separate from viewing |
| `WeeklyReflectionEngine.kt` | 3,257 | Deterministic factual reflections, max 2, no mood/addiction/shame language |
| `MunchieMastery.kt` | 1,772 | Auras by level only (L3–4 sprout, L5–6 mint, L7–8 sparkle, L9–10 crown); streak emblem >= 7d |
| `ActualViewingTime.kt` | 2,104 | Actual measured durations only; dedupe by session id; untimed logs stay a count; average dwell denominator = measured sessions |

New use case:

| File | Bytes | Contents |
|---|---|---|
| `domain/usecase/WeeklyIntelligenceUseCase.kt` | 4,229 | Builds digest + hourly pattern from `ReelDbHelper` (schema v6 only); call on `Dispatchers.IO` |

New tests (42 total, written, NOT RUN):

| File | Tests | Covers |
|---|---|---|
| `Phase15HourlyAnalyticsTest.kt` | 14 | 24 buckets, unmonitored ≠ zero, normalization, insufficient/partial/well thresholds, overlap merge, ineligible = 0, dedupe, hour/midnight splits, DST 23h + 25h days |
| `Phase15WeeklyTrendTest.kt` | 15 | Exact windows, today excluded, 0–3/4–5/6–7 confidence, lower-confidence rule, zero baselines, missing days ≠ zero, weekday/weekend rates, threshold boundary, attribution consistency |
| `Phase15ReflectionAndMasteryTest.kt` | 13 | Insufficient/lower/higher/weekend reflections, max 2, no psychological language, auras by level, streak emblem, reconciler sums/dedupe/untimed/average |

## 5. Files changed

| File | Change |
|---|---|
| `core/util/DateTimeUtils.kt` (12,712 → 13,325 bytes) | `estimateTimeMinutes` marked `@Deprecated(WARNING)` with a pointer to actual session durations. Body unchanged; existing Phase 1–14 callers/tests still compile. This is Stage 1 step 1 only — the call sites in `StatisticsScreen`, `HistoryScreen`, and `ReelDbHelper` presentation paths are NOT yet migrated. |

No other existing file was modified. No schema change. No XP/streak rule change.
No `weekly_digests` / `night_drift_config`. No new dependency. No network code.

## 6. Design decisions worth reviewing

- **Coverage merging:** overlapping wall-clock time is merged once per
  hour (same anchoring as Phase 14 `CoverageCalculator`:
  wall start + monotonic duration). An earlier draft summed per-boot
  coverage, which could have made coverage exceed available time; it
  was corrected before transfer, with a test asserting
  `coverageRatio <= 1.0`.
- **DST:** hour ranges are derived from `ZoneRules.getValidOffsets` —
  a skipped hour has no ranges (available = 0), a repeated hour has
  two (available = 2h). Tested for America/New_York 2026-03-08 (23h)
  and 2026-11-01 (25h).
- **Missing vs zero:** a day with no input row contributes no reels,
  no viewing time, and no coverage, and is not an adequate day.
  `actualViewingMs = null` means unavailable, never zero-filled.
- **Reflections:** at most 2, fixed priority order, so output is
  reproducible for the same digest.

## 6. Stage 6 — Backup / restore (implemented 2026-11-09, NOT RUN)

New package `domain/model/backup/` (6 files) + `data/backup/` (3 files):

| File | Bytes | Contents |
|---|---|---|
| `BackupJson.kt` | 10,331 | Strict dependency-free JSON (canonical sorted-key form; duplicate keys, trailing data, deep nesting rejected) |
| `BackupSchema.kt` | 13,796 | All 17 schema-v6 tables, exact column sets/PKs, enum value sets |
| `BackupModel.kt` | 2,927 | Manifest (schema version, export time/timezone, per-table counts, SHA-256 over canonical tables JSON) |
| `BackupValidator.kt` | 17,915 | Full validation BEFORE any mutation: size, format, schema (newer -> UPGRADE_REQUIRED, older -> refused), counts, checksum, exact columns, types, ranges, enums, duplicate PKs, goal FKs, unique event_ids |
| `RestorePlanner.kt` | 6,057 | Merge default (insert new / skip identical / conflicts never silently overwritten: KEEP_EXISTING / USE_BACKUP / CANCEL); full-restore deletion report |
| `RestoreJournal.kt` | 5,102 | File-based journal outside the DB; STARTED -> PRE_BACKUP_DONE -> COMMITTED / ROLLED_BACK / INTERRUPTED; startup recovery |
| `data/backup/BackupTableStore.kt` | 5,931 | Store interface + thin SQLite implementation (Cursor/ContentValues, transactions, integrity_check) |
| `data/backup/BackupExporter.kt` | 2,541 | Exports all 17 tables in schema order, PK-ordered rows, manifest + checksum |
| `data/backup/BackupImporter.kt` | 8,100 | Merge + full restore; full restore requires a verified pre-restore backup first, one transaction with count + integrity verification, rollback on failure |

Tests: `Phase15BackupRestoreTest.kt` (23 tests) — JSON canonical/strictness, checksum determinism, exporter->validator round-trip, tamper/format/schema/count/duplicate/enum/FK/date rejections, planner policies, merge round-trip + idempotency, full restore + pre-backup requirement, rollback on injected failure, journal recovery. The exporter/importer run against an in-memory store in tests (no Robolectric exists in this project — no test uses it), so the thin SQLite store is covered only by the Mac Gradle build + device validation.

Design notes: no new dependency was added (no kotlinx.serialization) — the JSON layer is hand-rolled pure Kotlin. The journal lives in a file, not the database, so a full restore can never delete its own journal.

## Stages 1, 3–5 — Presentation migration + UI (implemented 2026-11-09, NOT COMPILED)

**Stage 1 — estimates removed from reporting.** `StatisticsScreen.kt`: subtitle now says "actual observed viewing time"; the Today card shows actual viewing time (from `getViewingTimeForDate`, null = "Unavailable") and average dwell instead of "Estimated Drift" / "1.5m / reel"; platform cards show measured per-platform time or "unmeasured"; Last 7 Days rows show actual time or "unmeasured". `HistoryScreen.kt`: the period StatCard is now "Viewing Time" with a missing-day count ("Unavailable" when no day in the range has measured data); the selected-day line shows actual time or "unmeasured". Verified by grep on the Mac copies: zero remaining `estimateTimeMinutes` / `1.5m` usages in either screen. `ReelDbHelper.kt` lines 927/967/1045 intentionally still populate the `estimatedMinutesLost` model field for backward compatibility — no screen reads it any more.

**Stages 3–5 — Weekly Intelligence UI.** New `presentation/weekly/WeeklyIntelligenceScreen.kt` (15,516 B): confidence banner (lower-confidence label + adequate-days count + suppressed-percentages notice), current vs previous week (reels / viewing / focus, with deltas or the suppression notice), weekday vs weekend (coverage-normalized rates), 24-hour pattern (reels + viewing per hour with coverage bar and Unmonitored / Insufficient / Partial / Well monitored label), digest + reflections, mastery card (level, aura, streak emblem; level/streak only). Loads via `WeeklyIntelligenceUseCase` on `Dispatchers.IO`, with loading and error/retry states. New route `Screen.WeeklyIntelligence` (flow screen, NOT a bottom tab), wired in `MainActivity` with back to Home. Dashboard gained a compact Weekly Intelligence card (`onNavigateToWeeklyIntelligence`).

**Stage 6 UI — backup/restore entry points.** New `presentation/backup/BackupRestoreSection.kt` (15,566 B) in Settings: Create Backup (SAF CreateDocument), Merge restore and Replace-All restore (SAF OpenDocument), explicit conflict dialog (Keep Mine / Use Backup / Cancel), full restore creates and verifies a pre-restore backup first, and an interrupted restore is reported when Settings opens. All API references in the new screens were checked against the domain models and `DateTimeUtils.formatDurationMs` (exists, line 86) — but nothing has been compiled yet; the Mac Gradle build is the proof.

**Deliberately not done:** mastery cosmetics are NOT rendered in the floating `MunchieMascot` overlay — overlay-service changes were out of scope for this pass; the overlay mascot is unchanged.

## 7. What is NOT verified (honest limits)

- **Tests NOT RUN.** TAN's VM has no Java/Android SDK and TAN cannot
  run Terminal on the Mac. The 42 new tests and the 267 existing tests
  have not been executed. Expected total if green: 309. Do NOT treat
  this report as a passing test run.
- **No APK built.** The `release/app-debug.apk` on the Mac is still the
  Phase 14 build.
- **No device validation.** Nothing has been installed or tested on the
  Samsung S24 Ultra.
- **Kotlin was not compiled.** Files were checked for brace balance and
  import/field existence against the uploaded sources, but only a real
  Gradle build can prove they compile. The most likely compile risks
  are small API mismatches in the new use case / tests (e.g. an enum
  name) — the Mac build will surface them.
- **1.2–2.4 ms figure:** still UNVERIFIED; treated as an estimate only.

## 8. Remaining work (Stages 3–7)

Only Stage 7 remains, per `PHASE_15_IMPLEMENTATION_CHECKLIST.md`:
`./gradlew testDebugUnitTest` (expect 267 existing + 65 new = 332,
0 failures), `./gradlew assembleDebug`, Samsung device validation,
and the remaining three reports (`PHASE_15_TEST_REPORT.md`,
`PHASE_15_DEVICE_VALIDATION.md`, `PHASE_15_CHANGELOG.md`) plus the
`PROJECT_TRACKER.md` Phase 15 row. All Phase 15 code (Stages 0–6)
is written; the Mac Gradle build is the first proof it compiles.

## 9. Next command for ROHAN (on the Mac)

In the project folder (no space-freeing needed — `df -h` shows 372 GiB free):

```bash
./gradlew testDebugUnitTest
```

That is the gate for everything in this report: if it is green
(267 existing + 65 new = 332 tests), Stages 0–2 and 6 are proven;
if it fails, paste the failure and TAN will fix it. Do NOT install
an APK from this state — none has been built from the new code.
