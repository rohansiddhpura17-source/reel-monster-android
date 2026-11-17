# PHASE 16 — DEFECT LOG

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 16 — Final Integration & Quality Assurance
**Started:** 2026-11-09
**Status:** In progress. Audit by code inspection; device validation pending.

Severity: Critical > High > Medium > Low. Every defect lists reproduction, expected/actual, root cause, fix, regression test, and verification. No defect is hidden; OPEN items are explicit.

---

## DEF-01 — Historical per-platform viewing-time goals used a proportional estimate (FIXED)

- **Severity:** Medium
- **Feature:** Daily Goals → historical finalisation (`finalizeHistoricalGoalRecords`)
- **Found:** 2026-11-09, by code inspection during the Phase 16 integration audit.
- **Reproduction:**
  1. On a past day, log 9 Instagram reels and 1 YouTube reel.
  2. Record measured viewing sessions for that day: Instagram 6 minutes, YouTube 54 minutes (total 60).
  3. Set an Instagram viewing-time goal (e.g. 30 min) and run `finalizeHistoricalGoalRecords()`.
  4. Read the goal record for that day.
- **Expected:** Instagram actual = **6 minutes** (measured), goal successful (6 ≤ 30).
- **Actual (before fix):** Instagram actual = **54 minutes** — the code computed `viewingMins × (instagramCount / totalReels)` = 60 × 9/10. The record said the goal was failed (54 > 30). The estimate is wrong whenever dwell differs across platforms, which is the normal case (long YouTube Shorts sessions vs quick Instagram scrolls).
- **Root cause:** `ReelRepository.finalizeHistoricalGoalRecords()` had no per-platform historical viewing-time query. It reused the combined-day total and split it by reel-count share. (Today's live goals already used measured per-platform minutes via `getTodayPlatformViewingTimeMs()` — only the historical path estimated.)
- **Fix applied:**
  - `ReelDbHelper.getViewingTimeForDateByApp(dateStr, targetApp): Long?` — measured `SUM(duration)` per platform per date; null when the platform has no sessions that day (never zero-filled), mirroring `getViewingTimeForDate`.
  - `ReelRepository.finalizeHistoricalGoalRecords()` now uses the measured per-platform minutes for INSTAGRAM/YOUTUBE viewing-time goals. COMBINED goals unchanged (they already used the measured total).
- **Files changed:** `app/src/main/java/com/reelmonster/app/data/database/ReelDbHelper.kt`, `app/src/main/java/com/reelmonster/app/data/repository/ReelRepository.kt`.
- **Regression tests:** `Phase16IntegrationTest.test01_perPlatformViewingTimeQueryReturnsMeasuredSplit`, `test02_historicalInstagramViewingGoalUsesMeasuredNotProportional` (the latter fails on the old code — it would report 54 — and passes on the fix).
- **Verification:** pending Mac test run (`./gradlew testDebugUnitTest`).

---

## DEF-03 — Android Auto Backup includes the full database (OPEN)

- **Severity:** Low
- **Feature:** Privacy / backup (AndroidManifest, backup rules)
- **Found:** 2026-11-09, by manifest + backup-rules inspection during the Phase 16 integration audit.
- **Reproduction:** read `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml/backup_rules.xml` / `data_extraction_rules.xml`.
- **Expected:** the app presents itself as "100% Offline & Private" (manifest comment: "Zero Internet Permission: 100% Offline & Private"). A user reading that expects their reel history to stay on the device (apart from the app's own offline JSON backup, which the user explicitly creates).
- **Actual:** `android:allowBackup="true"` and `backup_rules.xml` explicitly `<include domain="database" path="."/>` plus `<include domain="sharedpref" path="."/>`; `data_extraction_rules.xml` includes `domain="root"` for both `cloud-backup` and `device-transfer`. On a device with Google backup enabled, Android Auto Backup will upload the full SQLite database (all reel history, goals, focus sessions, XP, streaks) to the user's Google account.
- **Root cause:** the backup rules were written to include everything, with no exclusion for the database.
- **Fix:** **not applied — decision needed.** Options: (a) exclude the database (and the restore journal) from `backup_rules.xml` / `data_extraction_rules.xml` so Auto Backup never copies history; (b) set `allowBackup="false"` and rely only on the app's own offline JSON backup. Either changes restore behaviour on a new device, so ROHAN should choose. Note: Auto Backup uses the system backup transport, not the app's INTERNET permission, so the "no INTERNET permission" claim is still true — the issue is only that data leaves the device through the system backup channel.
- **Regression test:** none yet (would be a manifest/rules review check, not a unit test).
- **Verification:** awaiting ROHAN's decision.

---

## DEF-02 — Zero-reel days get no historical goal records (OPEN)

- **Severity:** Low
- **Feature:** Daily Goals → historical finalisation (`finalizeHistoricalGoalRecords`)
- **Found:** 2026-11-09, by code inspection during the Phase 16 integration audit.
- **Reproduction:**
  1. Have a day with 0 reels logged (e.g. a day the user did not scroll at all).
  2. A viewing-time goal is enabled (e.g. 60 min).
  3. Run `finalizeHistoricalGoalRecords()`.
  4. Read the goal records for that day.
- **Expected (arguable):** a record showing 0 minutes, successful (0 ≤ 60) — the user stayed within the limit.
- **Actual:** no record at all. `finalizeHistoricalGoalRecords()` skips every day with `totalReels == 0`, so zero-activity days are missing from goal success-rate statistics.
- **Root cause:** the finaliser's loop only evaluates days with `summary.totalReels > 0`.
- **Fix:** **not applied.** Creating records for zero-activity days changes goal-history semantics (is a day with no data a success, or no data?). That is a business-rule decision for ROHAN, not a silent fix. Proposed fix if approved: evaluate zero-reel days too, recording actual 0 for viewing-time goals (and 0 reels for count goals) as successful when within the limit.
- **Regression test:** none yet (would be written with the fix).
- **Verification:** awaiting ROHAN's decision.

---

## Audit findings with NO defect (verified sound)

Recorded so the audit is evidence, not just the defects:

- **Navigation:** every `Screen` in `allScreens` (incl. Phase 15 `WeeklyIntelligence`) resolves via `fromRoute`; unknown routes fall back to Home. Onboarding is gated by `isOnboardingCompleted` before the main `Scaffold`.
- **XP idempotency:** `awardXpTransaction` uses `CONFLICT_IGNORE` on the UNIQUE `event_id`; a duplicate award returns false and total XP is unchanged. Focus XP keyed by `XpTransaction.focusSessionKey(session.id)`.
- **Reset safety:** `resetTodayCountOnly` is transactional and deletes only today's rows from `reel_events`, `daily_aggregates`, `viewing_sessions`. Goal records, history, XP, achievements, focus sessions untouched (also covered by Phase 12 test12).
- **Focus/viewing separation:** `FocusSessionTracker` documents "zero bleed into viewing_sessions"; focus and viewing use separate tables and separate trackers; both use `SystemClock.elapsedRealtime()` (monotonic).
- **Viewing sessions:** minimum 100 ms recordable duration; sessions end on app switch / screen off / pause / global off / reset with distinct interruption statuses; screen-off time is never counted.
- **Deduplication:** `DeduplicationEngine` — 450 ms minimum dwell, 10 s bounce-back window, LRU fingerprint cache, progressive-enrichment handling for the same creator.
- **Accessibility service:** `onServiceConnected` registers the screen receiver, starts a 15-minute heartbeat, and enables Level 2 Automatic mode; `onInterrupt`/`onDestroy` end the active session (`INTERRUPTED_SERVICE_STOP`) and reset the state machine.
- **Personality engine:** priority-driven state machine over 10 states; `selectNextMessage` is round-robin and never repeats a message consecutively.
- **Coverage/streaks:** qualification threshold 7,200,000 ms (120 min) of estimated verified monitoring; heartbeats are continuity evidence only and never satisfy the threshold alone.
- **Goals (live):** today's per-platform goal progress uses measured per-platform minutes (`getTodayPlatformViewingTimeMs`); thresholds 50/80/100% and EXCEEDED/AT_LIMIT states evaluated in `evaluateGoals`.
- **Backup/restore (Phase 15):** full pre-mutation validation, merge/full modes, transactional restore, journal with startup recovery — covered by 23 tests.

---

## Historical note (Phase 15 test runs, for traceability)

Two failures found and fixed during Phase 15's own test runs (see `PHASE_15_TEST_REPORT.md`): a DST test expectation that named the wrong repeated hour (01:00, not 02:00), and Phase 12 test12's hardcoded date (2026-11-08) becoming stale on 2026-11-09. Both were test-side defects, not production defects; they are recorded here only so the count of "defects found" is not confused with Phase 16 findings.
