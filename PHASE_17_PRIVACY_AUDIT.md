# PHASE 17 — PRIVACY AUDIT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Method:** static source inspection (manifest, dependencies, audited sources listed in `PHASE_17_SECURITY_AUDIT.md` §intro). No device run.

## Verdict (evidence-based)

The app is offline-first by construction, not just by claim: it holds **no `INTERNET` permission**, contains **no networking code** (no `HttpURLConnection`, OkHttp, Retrofit, `java.net.URL`, sockets, or WebView in the audited sources), and depends on **no analytics, tracking, or advertising SDK** (AndroidX + coroutines only). No account, login, or cloud service exists anywhere in the code. Two gaps are recorded below; neither is hidden.

## Checklist (brief §6)

| # | Claim | Evidence | Result |
|---|-------|----------|--------|
| 1 | No mandatory account or login | No auth code, no account APIs, no login UI in audited sources | PASS |
| 2 | No cloud-based usage tracking | No analytics/tracking SDK in dependencies; no network calls in audited sources | PASS |
| 3 | No unnecessary network communication | No `INTERNET` permission; no networking APIs found | PASS |
| 4 | No undisclosed analytics collection | Nothing collected or transmitted; the only telemetry-like output was logcat diagnostics (SEC-01, now release-gated) | PASS (with SEC-01 noted) |
| 5 | No unnecessary personal-data collection | Data collected is the app's own purpose: reel counts, viewing durations, goals, focus sessions, XP/streaks — all user-generated in-app | PASS |
| 6 | No external transmission of Reel viewing history | No transmission path exists in the audited sources | PASS |
| 7 | No unnecessary retention of sensitive information | History is retained by design (the app is a history/statistics companion); no passwords, contacts, location, or message content is collected | PASS |
| 8 | User data remains locally stored | SQLite + DataStore in the app-private directory | PASS (with DEF-03 noted) |
| 9 | Backup and restore remain user-controlled | SAF create/open pickers; user chooses the file and the mode (merge/full); full restore requires a verified pre-restore backup | PASS |
| 10 | Privacy-sensitive information is not exposed through logs or notifications | Logs: SEC-01 fixed (release builds silent). Notifications: foreground-service notification shows tracking state/counts only in the audited code; no titles, creators, or history in notification text found | PASS (with SEC-01 noted) |

## Permissions and why each is required

| Permission | Why |
|------------|-----|
| `SYSTEM_ALERT_WINDOW` | Floating Munchie overlay drawn over other apps |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Overlay service running in the foreground so Android does not kill it |
| `POST_NOTIFICATIONS` | Foreground-service notification (required on Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Restart the overlay after a device reboot (only if the user enabled it and the permission is granted) |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`, user-enabled in system Settings) | Automatic Reel/Shorts detection from the view hierarchy |

No location, contacts, camera, microphone, storage, or network permissions are requested.

## Offline operation (by construction)

Tracking (manual + accessibility), dashboard, statistics, history, goals, focus, XP/streaks, weekly intelligence, and backup/restore all operate on the local SQLite database and DataStore; no code path in the audited sources requires connectivity. Physical offline verification on the device (e.g. with Wi-Fi/mobile data off) is NOT VERIFIED and belongs to device validation.

## Gaps (honest)

1. **DEF-03 (Low, OPEN, from Phase 16):** `allowBackup=true` and the backup rules include the full database, so Android Auto Backup can upload the user's reel history to their Google account when device backup is enabled. This is the one channel by which data can leave the device without the user explicitly exporting it. Fix not applied; ROHAN to choose (exclude the database from backup rules vs `allowBackup=false`).
2. **SEC-01 (Low, FIXED in Phase 17):** detection diagnostics (apps, creators, titles, counts) were written to logcat in all builds. Now gated on `BuildConfig.DEBUG`; release builds stay silent. Debug builds keep diagnostics.

## What is NOT claimed

- Complete privacy is not claimed: the two gaps above are documented, and dynamic verification (runtime network capture, on-device logcat inspection, backup-file inspection) was not performed in this phase.
- No security certification is claimed.
