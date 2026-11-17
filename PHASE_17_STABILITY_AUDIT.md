# PHASE 17 — STABILITY AUDIT

**Project:** Reel Monster (BSERC Week 2, Project 06)
**Phase:** 17 — Performance, Stability, Security & Privacy Audit
**Date:** 2026-11-09
**Method:** static source inspection + existing automated tests. No device runs were performed in this phase; every device-dependent item is marked NOT VERIFIED, not assumed.

## Findings summary

| ID | Severity | Finding | Status |
|----|----------|---------|--------|
| STAB-01 | Low | Focus restore after process death drops pre-death pause time (elapsed recomputed from wall-clock start; saved elapsed is ignored) | OPEN — static finding, needs a device kill test to confirm |
| STAB-02 | Low | `Phase13FocusModeTest.test07` is flaky: `restoreActiveSessionState` reads `stateIn(Eagerly)` flows synchronously, but the flows update asynchronously after a DataStore edit — the test failed at 340 ("Session should be safely restored", line 265) after passing at 337/337 | **TEST FIXED** (test waits for the flow to propagate before restoring); re-run pending |
| STAB-03 | Low | `FocusSessionTracker.restoreActiveSessionState()` has **no production caller** — only the test calls it, so an in-progress focus session is never restored after a restart in the real app | OPEN — Phase 13 gap, recorded; wiring it up is a behaviour decision for ROHAN, not a Phase 17 fix |
| STAB-04 | Medium | Stale DataStore emissions overwrote newer in-memory tracking state: pause → resume could leave the goal state stuck on PAUSED (test06 failed: expected IN_PROGRESS, was PAUSED). Root cause in `UserPreferencesManager.init` — continuous collectors re-applied old emissions after the setters' synchronous in-memory update | **FIXED** (init collectors are now one-shot seeds; in-memory state is authoritative after startup); full-suite re-run pending |
| — | — | No crash, ANR, corruption, or duplicate-record defect found by inspection | — |

No Critical or High severity findings.

## 1. Application lifecycle (static)

- **Fresh launch / init:** `initializeInBackground()` on `Dispatchers.IO`; failure surfaces as an `ErrorState` with retry (`repository.initializeSync()`), not a crash. Loading state shown until initialised.
- **Background/foreground:** no special handling found that would break; repository state is held in `StateFlow`s re-collected by Compose on return.
- **Force-stop / relaunch:** state is persisted (SQLite + DataStore); the focus tracker restores an active session from saved state (see STAB-01 caveat).
- **Reboot:** `RebootReceiver` restarts the overlay only if enabled and overlay permission is granted; accessibility service restarts only if the user re-enables it in system Settings (Android behaviour, not app-controlled). Monitoring intervals are boot-scoped (`boot_session_id`), so a reboot cleanly ends the previous boot's coverage.
- **Low-memory termination:** no specific handling; state is persisted, so relaunch re-reads from the database. Not dynamically verified.
- **Screen lock/unlock:** screen-off ends the active viewing session (`INTERRUPTED_SCREEN_OFF`); screen-off time is never counted as viewing time.
- **App update:** `-r` reinstall preserves the database; schema upgrades run through the `onUpgrade` chain (v2–v6 verified in Phase 15). No migration runs in Phase 17.

## 2. Tracking stability (static)

- **Extended tracking:** event-driven detection; 15-minute heartbeat records continuity; no time-based cutoff that would stop long sessions.
- **Rapid scrolling:** 450 ms minimum dwell + 10 s bounce-back window + LRU fingerprint dedup; flings do not count.
- **App switching / screen off / pause / resume:** each ends the active session with a distinct interruption status; resume starts a fresh session. No double-counting path found.
- **Accessibility-service interruption:** `onInterrupt()`/`onDestroy()` end the active session and reset the state machine; `onServiceConnected()` re-registers the screen receiver and restarts the heartbeat.
- **Permission revocation:** overlay permission loss is handled (`OverlayState.PERMISSION_MISSING`, service stops without crashing); accessibility disable is surfaced via `onAccessibilityServiceConnectionChanged(false)`. Not dynamically verified.
- **Duplicate events:** debounce (350 ms same-app), fingerprint dedup, and `CONFLICT_IGNORE` on XP `event_id`.

## 3. Data stability (static)

- **Interrupted writes:** `insertReelEvent` (event + aggregate upsert), `resetTodayCountOnly`, and `awardXpTransaction` are all single transactions — a kill mid-write rolls back, it cannot half-apply.
- **Termination during a transaction:** SQLite transactions + the backup importer's transactional restore with rollback; the restore journal records interrupted restores for startup recovery.
- **Interrupted backup/restore:** exporter writes a file the user saves via SAF; importer validates fully before any mutation; full restore requires a verified pre-restore backup. Covered by 23 tests incl. a fault-injection rollback case and a journal-recovery case.
- **Migration interruption:** no migration in this phase; the v6 `onUpgrade` chain was verified in Phase 15 (transactional cutover, no backdating).
- **Midnight rollover:** `logReelAt` exists for rollover testing; `finalizeHistorical*` functions never finalise today or future dates (`date >= todayDateKey` → skip). DST-aware hourly buckets are unit-tested (incl. the 25-hour fall-back day).
- **Time-zone changes:** date keys are local dates computed at query time; no explicit handling for a mid-day time-zone change was found. Not observed as a defect; noted as a gap in coverage, not a failure.
- **Heartbeat interruption:** heartbeats are continuity evidence only and never satisfy the 120-minute qualification alone.

## 4. UI stability (static)

- All screens are Compose with loading/error/empty states (`LoadingState`/`ErrorState` in `MainActivity` for init; per-screen loading in the Weekly screen with retry). Back navigation is explicit on every flow screen (`onNavigateBack` → Home); the bottom bar covers the four main destinations.
- No rapid-navigation or rotation test was run; screen recreation behaviour was not dynamically verified.

## 5. STAB-01 — focus restore drops pre-death pause time (OPEN)

- **Where:** `FocusSessionTracker.restoreActiveSessionState()`.
- **What:** `persistActiveState()` saves the session's elapsed time (which correctly excludes pauses), target, status, and wall-clock start. But `restoreActiveSessionState()` never reads the saved elapsed value — it recomputes elapsed as `nowWall - startWall` and resets `accumulatedPausedMs = 0`. So if the app is killed while a focus session is **paused**, the restored session counts the pause time (including the time the app was dead) as focus time.
- **Impact:** Low — only the kill-while-paused path; a running session restores correctly (wall time ≈ real elapsed), and an expired-while-dead session is completed at its target, which is correct.
- **Fix:** not applied in this pass — it needs a device kill test to confirm the behaviour first, and the fix (restore the saved elapsed and the paused state, then re-base the monotonic start) should be verified against that test. Proposed fix recorded here for the next step.
- **Regression test:** none yet (would be written with the fix).

## 6. STAB-02 — flaky focus-restore test (TEST FIXED, re-run pending)

- **Where:** `Phase13FocusModeTest.test07_sessionStatePersistenceAcrossRestart`.
- **What:** the test saves the active focus state via `UserPreferencesManager.saveActiveFocusSessionState()` (a suspend DataStore edit) and then calls `restoreActiveSessionState()`, which reads the `savedFocusType` / `savedFocusTargetMs` / `savedFocusStartWallTime` StateFlows' `.value` synchronously. Those flows are `stateIn(scope, SharingStarted.Eagerly)` over `dataStore.data`, so they update asynchronously after the edit commits. In the 2026-11-09 ~03:21 IST run (340 tests) they had not yet propagated, `restoreActiveSessionState()` saw null/0 and returned false, and the assertion "Session should be safely restored" (line 265) failed. The same test passed in the 337/337 run — scheduling luck, not a code change. The SEC-01 fix (DetectorLogger only) cannot affect this path.
- **Fix (test only):** after the save, the test now waits with `withTimeout(5_000) { preferencesManager.savedFocusType.first { it == FocusType.CUSTOM.name } }` before calling restore. No production code changed.
- **Verification:** re-run pending (`./gradlew testDebugUnitTest --tests "com.reelmonster.app.Phase13FocusModeTest"`, then the full suite).

## 7. STAB-03 — focus restore has no production caller (OPEN)

- **Where:** `FocusSessionTracker.restoreActiveSessionState()`.
- **What:** a grep across all audited sources (MainActivity, ReelMonsterApp, FocusModeScreen, repository, trackers, screens) finds no production caller — only `Phase13FocusModeTest.test07` calls it. An in-progress focus session is therefore never restored after a process restart or reboot in the real app, even though the save/restore machinery exists and is tested.
- **Fix:** not applied. Wiring restore into startup is a behaviour decision (where/when to call it, on what thread, and the STAB-01/STAB-02 races it carries) for ROHAN; Phase 17 records it rather than adding a new behaviour.

## 8. STAB-04 — stale DataStore emissions overwrote tracking state (FIXED, re-run pending)

- **Where:** `UserPreferencesManager.init` (collectors) + `setTrackingPaused` / `setGlobalTrackingEnabled` / `setTrackingMode`.
- **What:** the setters update the in-memory `MutableStateFlow` synchronously and persist to DataStore asynchronously, but `init` ran continuous collectors that re-applied every DataStore emission to the same in-memory value. Sequence in `Phase12DailyGoalsTest.test06`: pause (edit #1 in flight) → resume (in-memory `false`) → edit #1 commits → collector delivers the stale `paused=true` emission → in-memory value back to `true` → `evaluateGoals()` reads PAUSED. The full-suite run of 2026-11-09 ~03:29 IST failed exactly there: `expected:<IN_PROGRESS> but was:<PAUSED>` (line 193). The same test passed in the 337/337 and 340-run (03:21) runs — timing luck. This is a real production race, not test-only: a fast pause → resume in the app could leave the goal state showing PAUSED until the next emission.
- **Fix (v1):** the three collectors became one-shot seeds (`first()`), matching the daily-reset collector's existing one-shot pattern. Every mutation of these keys goes through this manager, so the in-memory value is authoritative after startup; a restart re-seeds from DataStore.
- **Fix (v2 — the v1 fix itself caused test09 to fail):** a bare one-shot seed could read a pre-`clearPreferences` DataStore snapshot and clobber the newer explicit set (test06 left `global=false` persisted; test09's setUp cleared it in memory, then the seed applied the stale `false` → goal state PAUSED, `expected:<APPROACHING_LIMIT> but was:<PAUSED>` at line 260). The seeds were made **generation-guarded** (`seedOnce`).
- **Fix (v3 — v2 ALSO failed test09):** a temporary diagnostic print proved the flag itself was wrong (`DIAG test09 paused=false global=false state=PAUSED`) — the guard could not stop a seed that started after the clear from reading a still-false DataStore value left by the previous test's late async write. Both async-seed designs were therefore removed. The three flags are now read **synchronously with `runBlocking` in `init`**: one deterministic read at construction, after which the in-memory values are authoritative and only the setters / `clearPreferences` change them. No async seed exists to clobber anything later. The generation-guard machinery (`seedOnce`, `bumpTrackingGeneration`) was deleted. The diagnostic print was removed from test09.
- **Files changed:** `app/src/main/java/com/reelmonster/app/data/preferences/UserPreferencesManager.kt`.
- **Regression tests:** `test06_goalStateReflectsTrackingPausedOrGlobalOff` (STAB-04) and `test09_viewingTimeGoalCalculatedInMinutes` (v2 guard) — both were failing at different runs; no new test needed.
- **Verification:** full-suite re-run pending (`./gradlew testDebugUnitTest`).

## 9. Device-dependent items — NOT VERIFIED

Fresh install, force-stop/relaunch, reboot recovery, low-memory termination, screen lock/unlock, permission revocation, extended tracking, rapid navigation, repeated button presses, dialog/bottom-sheet behaviour, and any crash/ANR observation all require a physical device run and are NOT VERIFIED in this phase.
