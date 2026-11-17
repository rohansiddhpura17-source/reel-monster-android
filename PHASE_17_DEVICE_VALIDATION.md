# PHASE 17 — DEVICE VALIDATION

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Device:** Samsung Galaxy S24 Ultra (RZCXA0ZAH6Z)

## Result — NOT VERIFIED (superseded by Phase 18)

The Phase 17 debug APK was built (`~/Downloads/app-debug-phase17.apk`, 20,602,128 bytes, clean + `assembleDebug` BUILD SUCCESSFUL 2026-11-09 ~04:02 IST) but **was never installed on the phone and no on-phone checks were reported** before Phase 18 was approved. Every item below is therefore NOT VERIFIED — not failed, not passed, simply not performed.

Because the release build (Phase 18) carries the same code under a different package (`com.reelmonster.app` vs the debug build's `com.reelmonster.app.debug`), device validation is performed once, on the Phase 18 release APK, and recorded in `FINAL_DEVICE_VALIDATION.md`. The Phase 17 checks below are folded into that validation rather than repeated on the debug build.

| # | Check | Result |
|---|-------|--------|
| 1 | Data intact after reinstall (count, goals, streaks, history) | NOT VERIFIED |
| 2 | Pause → resume returns goal to active promptly (STAB-04 fix) | NOT VERIFIED |
| 3 | Weekly Intelligence screen loads and shows the week | NOT VERIFIED |
| 4 | Statistics / History show actual measured times | NOT VERIFIED |
| 5 | Backup creation from Settings | NOT VERIFIED |
| 6 | Focus kill test: pause a focus session, force-stop, reopen, check elapsed time (STAB-01) | NOT VERIFIED |
| 7 | Launch time / memory / CPU / battery (any measurement) | NOT VERIFIED — no measurements exist |
| 8 | Fresh install, reboot recovery, overlay permission, accessibility detection on device | NOT VERIFIED |

## What IS verified (not on device)

- Full unit-test suite: **340/340 green, 0 failures, 0 skipped** (2026-11-09 ~04:00 IST) — see `PHASE_17_TEST_REPORT.md`.
- Security audit: no Critical/High findings; SEC-01 fixed (see `PHASE_17_SECURITY_AUDIT.md`).
- Privacy audit: offline-first confirmed by inspection; DEF-03 still open (see `PHASE_17_PRIVACY_AUDIT.md`).
- Stability: STAB-02 fixed (test), STAB-04 fixed (production, proven by test06/test09); STAB-01 and STAB-03 still open (see `PHASE_17_STABILITY_AUDIT.md`).
- Performance: no measurements taken; PERF-01/PERF-02 recorded as observations only (see `PHASE_17_PERFORMANCE_AUDIT.md`).

## Carried into Phase 18

- STAB-01 (Low, open): focus restore drops pre-death pause time — needs the kill test in §6 above.
- STAB-03 (Low, open): focus restore has no production caller.
- DEF-02 (Low, open, Phase 16): zero-reel days get no historical goal records.
- DEF-03 (Low, open, Phase 16): Android Auto Backup includes the full database.
- Phase 16 device validation is also still open (its on-phone checks were never reported either).
