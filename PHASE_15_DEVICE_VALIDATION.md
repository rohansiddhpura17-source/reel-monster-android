# PHASE 15 — DEVICE VALIDATION

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 15 — Weekly Intelligence & Mindful Mastery
**Target device:** Samsung Galaxy S24 Ultra (SM-S928B), Android 17 / API 37, ADB serial `RZCXA0ZAH6Z`
**APK:** `~/Downloads/app-debug-phase15.apk` (20,602,128 bytes, built 2026-11-09 ~00:37 IST)
**Date:** 2026-11-09

## What actually happened on the device

- `adb devices` listed `RZCXA0ZAH6Z device` (2026-11-09 ~00:43 IST).
- `adb install -r ~/Downloads/app-debug-phase15.apk` → **Success** ("Performing Streamed Install", 2026-11-09 ~00:43 IST).
- The install used `-r` (reinstall, keep data), so the existing on-device database was preserved by design.

That is the full extent of the physical-device evidence for Phase 15.

## Validation checklist — all NOT VERIFIED

ROHAN was given five on-phone checks after the install. **No results were reported for any of them**, so every item is recorded honestly as NOT VERIFIED. Nothing below was observed on the phone, and nothing is claimed.

| # | Check | Evidence | Result |
|---|-------|----------|--------|
| 1 | Existing data intact (reel count, Munchie stage, goals, streak) after the reinstall | None reported | NOT VERIFIED |
| 2 | Dashboard → Weekly Intelligence card opens the new screen (week comparison, weekday vs weekend, 24-hour pattern, reflections) | None reported | NOT VERIFIED |
| 3 | Stats and History show real measured viewing times, no "1.5 min" estimates | None reported | NOT VERIFIED |
| 4 | Settings → Backup & Restore → Create Backup saves a file | None reported | NOT VERIFIED |
| 5 | Existing tracking still works (log a reel, count increases, overlay if used) | None reported | NOT VERIFIED |

Additional Phase 15 device items never attempted on the phone:

| Item | Result |
|------|--------|
| Merge restore from a created backup | NOT VERIFIED |
| Full (replace-all) restore with pre-restore backup | NOT VERIFIED |
| Interrupted-restore recovery notice in Settings | NOT VERIFIED |
| Hourly pattern shows Unmonitored / Insufficient / Partially monitored / Well monitored states on real data | NOT VERIFIED |
| Weekly comparison confidence labels on real data | NOT VERIFIED |
| Mastery aura rendering in the Weekly Intelligence screen | NOT VERIFIED |

## What this means

- Phase 15 is verified at the **logic level only**: 332/332 unit tests green, APK builds and installs. See `PHASE_15_TEST_REPORT.md`.
- **No Phase 15 feature is proven on the physical device.** The weekly screen, backup creation, and estimate-free Stats/History displays are code-verified and unit-tested, not device-verified.
- The on-phone checks above are carried into Phase 16's device validation (the phone was reconnected for Phase 16 on 2026-11-09 ~01:44 IST). If they are performed there, this report should be updated with the actual results and the NOT VERIFIED marks replaced by PASS/FAIL — not back-filled from the unit tests.

## Evidence classification used

- PASS — physically verified on the device.
- FAIL — tested on the device and failed.
- NOT VERIFIED — not physically tested.
- BLOCKED — testing prevented by a technical issue.

Automated tests are never presented as physical-device verification.
