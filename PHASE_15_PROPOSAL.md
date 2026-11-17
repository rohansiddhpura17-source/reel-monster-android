# Reel Monster --- Phase 15 Final Architectural Proposal

## Weekly Intelligence & Mindful Mastery

**Status:** Proposal for approval only. Implementation is not authorized.  
**Baseline:** Phase 14 signed off; SQLite schema v6; 267 tests reported passing.

## 1. Executive summary

Phase 15 adds factual, coverage-aware weekly analytics and safe offline data portability. It preserves the offline-first product, Instagram Reels and YouTube Shorts scope, all completed Phase 1--14 functionality, and Munchie's five existing growth stages.

Proposed features:
1. Hourly Scrolling Pattern.
2. Weekday vs. Weekend Analytics.
3. Current seven-complete-day vs. previous-seven-complete-day trends.
4. Actual Viewing-Time Reconciliation.
5. Weekly Executive Digest.
6. Munchie Mastery Auras.
7. Contextual Weekly Reflection Dialogues.
8. Offline JSON Backup and Restore.

No attention, vulnerability, addiction, mood, or psychological claims may be inferred from scrolling data. No XP or cosmetic progression may reward increased scrolling.

## 2. Hourly Scrolling Pattern

Use 24 local clock-hour buckets. For each hour `h`, calculate:
- `C(h)`: eligible verified monitoring coverage overlapping that hour across the reporting window.
- `R(h)`: distinct deduplicated Reel events attributed to that hour.
- `T(h)`: actual observed viewing-session duration allocated to that hour.

Coverage-normalized metrics:
- `reels_per_monitored_hour(h) = R(h) / (C(h) / 3,600,000)`, when `C(h) > 0`.
- `viewing_minutes_per_monitored_hour(h) = (T(h) / 60,000) / (C(h) / 3,600,000)`, when `C(h) > 0`.

Show raw totals and normalized rates separately. Do not extrapolate into unmonitored time.

For each hour, compute `available(h)` as the real elapsed duration belonging to that local hour within the reporting window, including DST-short and DST-long days. Then `coverage_ratio(h) = C(h) / available(h)`.

Display states:
- **Unmonitored:** `C(h) = 0`.
- **Insufficient data:** `0 < coverage_ratio(h) < 0.25`.
- **Partially monitored:** `0.25 <= coverage_ratio(h) < 0.80`.
- **Well monitored:** `coverage_ratio(h) >= 0.80`.

These are display-quality labels, not proof of complete detection. Always show coverage duration or percentage. Suppress strong ranking claims for low-coverage buckets.

Derive coverage only from persisted intervals eligible under Phase 14 rules. Clip intervals to local-hour boundaries, merge overlaps, and exclude interrupted, uncertain, disconnected, or otherwise ineligible intervals. Heartbeats alone do not prove coverage. Split sessions at every hour and local-midnight boundary; never assign an entire cross-boundary session to its start hour.

## 3. Weekday vs. Weekend Analytics

For the selected seven-complete-day period, group Monday--Friday and Saturday--Sunday. Show:
- Number of calendar days in each group.
- Adequately monitored days.
- Total eligible monitoring coverage.
- Observed Reel count and actual viewing duration.
- Per-monitored-hour rates for Reel count and viewing duration.

Never compare raw totals without showing day counts and coverage. Missing monitoring is not zero usage.

## 4. Weekly windows, confidence, and trend math

Let `D` be today's date in the current device timezone:
- Current window: `[D-7, D-1]` inclusive.
- Previous window: `[D-14, D-8]` inclusive.
- Today `D` is displayed separately and excluded from both windows.

A day is adequately monitored for weekly comparison when it has at least 120 minutes of eligible verified coverage, consistent with Phase 14. This is a minimum eligibility threshold, not proof of perfect detection.

Apply confidence independently to each window:

| Adequately monitored days | Confidence | Display |
|---|---|---|
| 0--3 | Insufficient | Suppress percentage comparisons; show observed totals with incomplete-data notice. |
| 4--5 | Limited | Show totals and coverage-normalized rates; label Limited data; avoid strong directional language. |
| 6--7 | High | Show totals, normalized rates, and percentage deltas, with coverage visible. |

If windows differ in confidence, use the lower confidence for the combined comparison. If either window has 0--3 adequate days, suppress percentage deltas. Never fill missing days with zero.

Calculate separately:
- `current_reel_rate = current_R / current_verified_hours`
- `previous_reel_rate = previous_R / previous_verified_hours`
- `current_view_rate = current_T_ms / current_verified_hours`
- `previous_view_rate = previous_T_ms / previous_verified_hours`

For a positive previous rate:
`delta_percent = ((current_rate - previous_rate) / previous_rate) * 100`.

Zero baseline:
- Both rates zero: "No observed activity in either period"; no percentage.
- Previous zero, current positive: "New observed activity" with absolute rate difference; no infinity or percentage.
- Previous positive, current zero: -100% only if both windows meet the confidence requirement.
- Insufficient or missing coverage: no percentage.

Use neutral language such as "observed viewing rate increased/decreased." Do not frame increases as personal failure or decreases as clinical improvement.

## 5. Actual Viewing-Time Reconciliation

Remove all `count * 1.5 minutes` or equivalent static estimates from `StatisticsScreen.kt`, `HistoryScreen.kt`, and all other reporting/presentation code.

Use actual persisted `viewing_sessions` durations. Distinguish:
- Actual measured viewing duration.
- Untimed manual Reel logs.
- Missing or unavailable duration records.

Never invent durations for untimed manual logs. Average dwell per Reel must state its denominator and include only valid measured sessions associated with valid Reel records. Verify deduplication prevents duplicate event/session counting.

## 6. Weekly Executive Digest

Calculate locally from the current seven-complete-day window:
- Observed Reel count.
- Actual observed viewing duration.
- Focus duration, separately from viewing duration.
- Adequately monitored days out of seven.
- Weekday/weekend comparison when eligible.
- Current vs. previous period trend when eligible.
- One or two factual reflections.

Compute dynamically on `Dispatchers.IO` and expose through the existing reactive architecture. Do not add a `weekly_digests` table unless reproducible profiling shows unacceptable query cost. Avoid stale cache results after resets or data changes.

## 7. Munchie Mastery and reflections

Preserve BABY, CURIOUS, HUNGRY, STUFFED, and MEGACHONK exactly. Add lightweight native Canvas cosmetics based only on XP, level, and streaks:
- Level 3--4: small green sprout.
- Level 5--6: subtle mint aura.
- Level 7--8: restrained sparkle flecks.
- Level 9--10: golden leafy crown.
- Streak >= 7 days: optional streak emblem.

No cosmetic depends on Reel count, viewing duration, or increased scrolling. Avoid continuous animation while idle.

Weekly reflection dialogue must be deterministic, non-judgmental, and traceable to computed facts. Examples:
- "Your observed viewing time was lower than the previous period."
- "Weekend viewing time per monitored hour was higher than weekday viewing time."
- "There is not enough monitoring data to compare these periods reliably."

Never infer mood, vulnerability, intent, attention, addiction, or mental-health status.

## 8. Offline JSON Backup and Restore

Use Android Storage Access Framework. No network access.

Backup manifest must include format identifier/version, source schema version, export timestamp/timezone, user-owned tables and relationships, stable identifiers, record counts, and deterministic SHA-256 checksums over a canonical payload. Exclude transient runtime state that cannot be safely restored and document exclusions.

Before any database mutation:
1. Parse the entire file with strict size/structure limits.
2. Validate format and supported backup version.
3. Validate schema compatibility.
4. Verify checksums and record counts.
5. Validate required fields, types, ranges, and enums.
6. Validate foreign keys and cross-table references.
7. Detect duplicate primary/business identifiers within the backup.
8. Reject unsupported future schema versions.
9. Build a restore plan and show mode/conflict summary.

No live writes before validation passes.

### Merge restore (default)
- Preserve existing records.
- Insert non-conflicting records.
- Treat identical immutable identifiers/content as already present.
- If an identifier matches but content differs, do not overwrite silently. Offer keep-existing, use-backup, or cancel.
- Mutable settings/goals require an explicit per-category policy.
- Restore parents before dependent records.
- Do not use `INSERT OR REPLACE` as a universal strategy.

### Full restore (destructive)
- Explain that current app data will be replaced.
- Create and verify a separate pre-restore backup first.
- Fully validate the incoming backup before beginning.
- Restore in a transaction where SQLite constraints permit.
- Run integrity and foreign-key checks before commit.
- On failure, roll back completely and retain the pre-restore backup.

### Compatibility and interruption recovery
- Current schema v6 backups are supported.
- Older backups are supported only with explicit tested migrations into the current schema.
- Reject newer schema versions with an upgrade-required message; never downgrade.
- Record source schema and applied migration path.
- Use a durable restore journal/state marker. On startup, detect incomplete restore attempts and determine whether the transaction committed or rolled back. Never expose partial state. If consistency cannot be proven, preserve the pre-restore backup and require recovery.

## 9. Performance benchmark

The previously mentioned 1.2--2.4 ms figure is **unverified** unless reproducible evidence is supplied. Do not call it a verified benchmark.

Required protocol:
- Exact device model, Android version, and runtime/storage conditions.
- Exact row counts for `reel_events`, `viewing_sessions`, and `monitoring_intervals`.
- Representative 14-day and larger stress datasets.
- Exact SQL/Kotlin aggregation and indexes.
- At least 100 measured runs after warm-up.
- Report median, p95, min, and max.
- Use a monotonic high-resolution clock.
- Separate query time from UI rendering.
- Report cold-cache and warm-cache results separately.
- Preserve scripts, dataset-generation steps, and raw results.

Until then, call 1.2--2.4 ms an estimate, not verified performance.

## 10. Screen changes

- **Dashboard:** compact Weekly Intelligence card with reporting window and monitoring completeness; tap opens full report.
- **Statistics:** Hourly Scrolling Pattern, weekday/weekend comparison, and complete-week trends with coverage labels.
- **History:** actual measured session durations; untimed manual logs shown separately.
- **Munchie/Progression:** mastery cosmetics only; preserve growth stages and XP rules.
- **Settings/Data Management:** export/import entry points, explicit restore mode, validation and conflict summaries.

## 11. Technical architecture

Retain Kotlin, Jetpack Compose, MVVM, repository, Coroutines/Flow, and SQLite. Suggested logical components, subject to existing conventions:
- `HourlyScrollingPatternCalculator`
- `WeeklyHabitTrendCalculator`
- `WeeklyDigestRepository`
- `ActualViewingTimeRepository`
- `MunchieMasteryAppearance`
- `WeeklyReflectionEngine`
- `OfflineBackupExporter`
- `OfflineBackupImporter`
- `BackupValidator`
- `RestorePlanner`
- `RestoreJournal`

Run aggregation and serialization on `Dispatchers.IO`. Keep calculations deterministic and unit-testable. No cloud services or unnecessary dependencies.

## 12. Database design

Keep schema v6. Use existing `reel_events`, `viewing_sessions`, `monitoring_intervals`, `daily_monitoring_coverage`, goals, focus sessions, and progression records. Verify existing indexes before proposing any new ones. Add an index only when query-plan and benchmark evidence demonstrates benefit. Do not create `weekly_digests` or `night_drift_config` in this scope. Any future schema change requires a separate migration plan and explicit approval.

## 13. Test strategy and acceptance criteria

### Hourly analytics
1. Correct local-hour bucketing.
2. Correct verified-coverage clipping.
3. Overlapping intervals merged, not double-counted.
4. Ineligible intervals contribute zero coverage.
5. Unmonitored hours never appear as zero usage.
6. Insufficient coverage suppresses ranking claims.
7. Sessions split at hour boundaries.
8. Midnight-crossing sessions split across local dates.
9. DST 23-hour and 25-hour days work.
10. Duplicate Reel events do not inflate counts.

### Weekly trends
11. Exact complete-day windows.
12. Today excluded.
13. 0--3 monitored days suppress percentage comparisons.
14. 4--5 show limited confidence.
15. 6--7 show high confidence.
16. Different period confidence uses lower confidence.
17. Zero baseline never creates NaN/infinity.
18. Missing days are not zero.
19. Weekday/weekend rates account for coverage and day counts.

### Actual viewing time
20. No static count-times-1.5-minute estimate remains.
21. Valid session durations sum correctly.
22. Untimed manual logs remain untimed.
23. Average dwell denominator is correct.
24. Duplicate sessions/events do not inflate metrics.

### Munchie and reflections
25. Five growth stages unchanged.
26. Cosmetics depend only on XP, level, streak.
27. Scrolling volume never grants XP/cosmetics.
28. Reflections use supported computed facts only.

### Backup and restore
29. Valid v6 backup exports/imports.
30. Future schema rejected before mutation.
31. Corrupt checksum/payload rejected before mutation.
32. Invalid foreign-key references rejected.
33. Duplicate immutable records handled idempotently.
34. Conflicts require explicit policy.
35. Merge preserves existing records.
36. Full restore creates verified pre-restore backup.
37. Failed restore rolls back without corrupting existing records.
38. Process termination during restore recovers safely.
39. Restored DB passes integrity and foreign-key checks.
40. No network access introduced.

### Regression and release
41. Existing Phase 1--14 tests pass.
42. Existing user data/progression remain intact.
43. Instagram and YouTube Shorts detection remain functional.
44. Viewing time, goals, resets, Focus Mode, XP, streaks, and reboot recovery remain functional.
45. App remains offline.

Target at least 40 new focused tests plus the full regression suite. Test count alone is not acceptance; assertions must verify outcomes.

## 14. Implementation sequence (only after approval)

1. **Stage 0 --- Baseline:** archive Phase 14 APK/database; record test baseline; inspect query/index design.
2. **Stage 1 --- Data correctness:** remove legacy estimates; validate session/event semantics.
3. **Stage 2 --- Analytics domain:** hourly buckets, coverage normalization, weekday/weekend, weekly comparisons.
4. **Stage 3 --- Weekly UI:** Statistics and Dashboard with coverage labels.
5. **Stage 4 --- Digest/reflections:** deterministic local summary and factual dialogue.
6. **Stage 5 --- Munchie cosmetics:** visual-only effects; no growth-stage or XP changes.
7. **Stage 6 --- Backup/restore:** validation, restore planner, merge/full modes, journaling, recovery tests.
8. **Stage 7 --- Regression/device verification:** full tests, offline permission audit, Samsung verification, evidence archive.

Each stage requires a review checkpoint before the next begins. No silent scope expansion.

## 15. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Incomplete monitoring distorts patterns | Normalize by eligible coverage; show confidence |
| DST/timezone changes distort buckets | Use `ZoneId` and real elapsed interval clipping |
| Duplicate events inflate totals | Stable identifiers and existing deduplication |
| Legacy estimates mislead | Remove all static multipliers |
| Restore overwrites data | Validate first, explicit conflict policy, pre-restore backup, transaction |
| Large backup causes memory pressure | Bounded/streaming parsing and file limits |
| Analytics slow on older devices | Profile before adding caches/indexes |
| UI regresses existing features | Preserve Phase 14 checkpoint and run regression suite |

## 16. Approval gate

Phase 15 is **proposed, not authorized for implementation**.

Until explicit user approval:
- Do not modify source code.
- Do not modify the database.
- Do not install dependencies.
- Do not change UI/navigation.
- Do not generate an APK.
- Do not alter completed Phase 1--14 work.

**Final scope:** Weekly Intelligence & Mindful Mastery using schema v6, offline calculations, coverage-aware comparisons, accurate observed-duration reporting, and safe local backup/restore.