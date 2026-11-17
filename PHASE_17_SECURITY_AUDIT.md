# PHASE 17 — SECURITY AUDIT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Method:** static source inspection (no device run). Files audited: `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `ReelDbHelper.kt`, `ReelRepository.kt`, `DetectorLogger.kt`, `ReelAccessibilityService.kt`, `MonitoringCoverageTracker.kt`, `InstagramReelDetector.kt`, `YouTubeShortsDetector.kt`, `DeduplicationEngine.kt`, `DetectionStateMachine.kt`, `NodeSource.kt`, `ViewingSessionTracker.kt`, `FocusSessionTracker.kt`, `MunchiePersonalityEngine.kt`, `FloatingMonsterOverlayService.kt`, `FloatingMonsterOverlayContent.kt`, `RebootReceiver.kt`, `MainActivity.kt`, `Screen.kt`, `UserPreferencesManager.kt`, `ReelMonsterApp.kt`, `DateTimeUtils.kt`, `HistoryAnalyticsUseCase.kt`, `WeeklyIntelligenceUseCase.kt`, `DashboardScreen.kt`, `SettingsScreen.kt`, `StatisticsScreen.kt`, `HistoryScreen.kt`, and the Phase 15 backup/restore sources. Presentation sub-screens not individually listed were not line-audited for logging; see §5.

## Findings summary

| ID | Severity | Finding | Status |
|----|----------|---------|--------|
| SEC-01 | Low | Verbose detection logging writes viewing activity (apps, creators, titles, counts) to logcat, always on | **FIXED** (gated on `BuildConfig.DEBUG`) |
| DEF-03 (Phase 16) | Low | Android Auto Backup includes the full database | OPEN — ROHAN's decision (see §4) |
| — | — | No other security finding | — |

No Critical or High severity findings.

## 1. Android components (manifest)

- **MainActivity** — `exported="true"`, launcher intent only. Correct: it must be exported to launch.
- **FloatingMonsterOverlayService** — `exported="false"`, foreground service type `specialUse` with the required property. Not reachable by other apps. (Consequence: the test-only `ACTION_RESET_TODAY_FOR_TEST` intent can only be sent by the app itself.)
- **ReelAccessibilityService** — `exported="false"`, guarded by `BIND_ACCESSIBILITY_SERVICE`. Correct.
- **RebootReceiver** — `exported="true"` with intent-filter for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` only. These are protected system broadcasts; no app-defined actions are accepted. Correct.
- **Content providers:** none declared. **PendingIntents:** three, all in the overlay foreground notification (`openApp`, pause/resume, close) — all built with `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` and explicit targets (own activity / own service). Correct; no mutable PendingIntent exists. (`RebootReceiver.kt` has an unused `PendingIntent` import; harmless.)
- **Permissions declared:** `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. No `INTERNET` permission. No location, contacts, camera, microphone, or storage permissions.

## 2. Local storage

- SQLite database and DataStore preferences live in the app-private directory (default `SQLiteOpenHelper` / DataStore locations); no world-readable files, no external-storage writes found in the audited files.
- Backup files are created only where the user points the Storage Access Framework picker; no backup is written to a shared or world-readable location by the app itself.
- No temporary-file creation/cleanup code was found in the audited files; no sensitive temp files were identified.

## 3. Logging (SEC-01 — FIXED)

- The only production logging in the audited sources is `DetectorLogger` (tag `ReelMonsterDetector`), called from `ReelAccessibilityService` on every accessibility event and from `MainActivity`.
- **What it logged:** package names, event types, target app, viewer state, creator/channel names (truncated to 24 chars), reel titles/captions (truncated to 28 chars), content fingerprints, dedup decisions, and daily counts. The `sanitize()` helper truncates but does not remove content — creator names and video titles are viewing-activity data.
- **Problem:** `isEnabled` defaulted to `true` with no release gating and nothing ever disabled it, so detailed viewing activity would be written to logcat in every build, including any future release build. (On a non-rooted device logcat is app-private, so the exposure is via adb/bug reports, not other apps — hence Low, not higher.)
- **Fix applied:** `DetectorLogger.isEnabled` now defaults to `BuildConfig.DEBUG`. Debug builds — including every build ROHAN runs — keep full diagnostics; release builds stay silent. The flag remains runtime-togglable for a live debugging session.
- **Regression tests:** `Phase17SecurityAuditTest` (3 tests): disabled logger writes nothing to logcat (via `ShadowLog`); enabled logger writes with the `ReelMonsterDetector` tag; debug build keeps diagnostics enabled by default.
- **Files changed:** `app/src/main/java/com/reelmonster/app/service/tracking/DetectorLogger.kt`; `app/src/test/java/com/reelmonster/app/Phase17SecurityAuditTest.kt` (new).

## 4. Backup security (carried from Phase 15, re-verified by reading the code)

- Exporter writes canonical JSON with a manifest (format id/version, schema version, export time/timezone, record counts) and SHA-256 over the canonical `tables` payload.
- Importer validates fully **before any database mutation** (strict parse, format/version, schema compatibility, checksums/counts, column sets, types/ranges, enums, dates, duplicate PKs, FKs, unique `event_id`s) and collects all errors.
- Newer schema → `UPGRADE_REQUIRED`; older → refused; never downgrades.
- Merge never silently overwrites (KEEP_EXISTING / USE_BACKUP / CANCEL); full restore requires a verified pre-restore backup and runs in one transaction with per-table count + integrity verification and rollback on failure; file-based restore journal with startup recovery.
- Covered by 23 tests (`Phase15BackupRestoreTest`), all passing.
- **Open item (DEF-03, Phase 16):** `allowBackup=true` + `backup_rules.xml` includes the whole database and `data_extraction_rules.xml` includes root for cloud-backup — Android Auto Backup can upload the database to the user's Google account. This is a privacy finding, not a flaw in the app's own backup feature. Fix not applied; ROHAN to choose (exclude DB from backup rules vs `allowBackup=false`).

## 5. Dependency audit

- **Libraries:** AndroidX only (core-ktx 1.18.0, lifecycle 2.10.0, activity 1.13.0, Compose BOM 2026.03.01, Material 3, DataStore Preferences 1.1.3), Kotlin coroutines 1.10.2, JUnit 4.13.2, Robolectric 4.14.1 (test only), AndroidX Test/Espresso (test only). Kotlin 2.3.20, AGP 9.0.1.
- **No third-party (non-AndroidX/JetBrains) libraries. No networking library, no analytics/tracking SDK, no image-loading or serialization library.**
- No outdated dependency was identified from the catalog; no major upgrade was performed (per the brief, no blind upgrades).
- `isMinifyEnabled = false` for release (proguard file referenced but minification off) — noted, not changed; ROHAN's builds are debug builds.

## 6. Scope limits (honest)

- Static inspection only. No dynamic testing (no rooted-device check, no runtime logcat capture, no backup-file inspection on device) was performed in this phase; those items are NOT VERIFIED and belong to device validation.
- Presentation sub-screens (goals/focus/progression/components/onboarding/tracking) were not individually line-audited for logging; the audited set above covers all services, data, domain, and the main screens. A full-tree `Log.` grep across all 104 files was not possible from the VM (the Mac file-search tool does not reliably search contents); the audit relies on the files listed above.
- No security certification is claimed.
