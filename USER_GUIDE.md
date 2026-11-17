# REEL MONSTER — USER GUIDE

**Version:** 1.0.0 · **Package:** `com.reelmonster.app` · **Platform:** Android (min SDK 26 / Android 8.0, target SDK 36)

Reel Monster is an offline-first screen companion for Instagram Reels and YouTube Shorts. It counts the reels you watch, measures how long you actually watch, and turns that into goals, focus sessions, and a little mascot — Munchie — that grows as you use the app. There is no account, no login, and no internet connection: everything stays on your phone.

## Installation

1. Copy `reel-monster-v1.0.0.apk` to your phone (or download it there).
2. Tap the file and allow installation from that source when Android asks. (This is a direct install, not a Play Store install.)
3. Open **Reel Monster** from your app drawer.

See `INSTALLATION_GUIDE.md` for the full steps, including installing over the debug build and verifying the APK.

## First-time setup

On first launch the app walks you through onboarding:

1. **Permissions.** Grant the permissions the app asks for, in order:
   - **Display over other apps** — lets Munchie float on top of Instagram and YouTube. Without it there is no overlay.
   - **Notifications** — the app uses notifications for the floating overlay's foreground service.
   - **Accessibility service** — enables automatic reel detection. You turn this on in Android Settings → Accessibility → Reel Monster. Without it, only manual logging works.
2. **Tracking mode.** Choose how reels are counted (automatic detection via the accessibility service, manual logging, or both).
3. That's it — the dashboard is your home screen.

## The dashboard

The dashboard shows today's reel count, actual measured viewing time, your current goal progress, Munchie's current stage, and quick cards for Focus Mode, Statistics, History, and Weekly Intelligence.

## Munchie

Munchie is the floating mascot. It appears as a small overlay while you scroll and grows through 5 stages as your total reel count grows: Baby (0–5), Curious (6–15), Hungry (16–30), Stuffed (31–50), Megachonk (51+).

- **Tap** Munchie — it reacts with a personality line.
- **Long-press** Munchie — a different reaction. Tapping and long-pressing never change your stats; they only affect Munchie's mood lines.
- **Mastery auras** (Weekly Intelligence): purely cosmetic decorations earned from your level — a sprout (levels 3–4), mint aura (5–6), sparkle (7–8), golden crown (9–10), plus an emblem if your streak is 7 days or more. They are never based on how many reels you watched.

## Tracking

- **Automatic detection** (accessibility service on): the app detects reels as you scroll Instagram and YouTube, counts them, and records the actual time spent watching each session. Switching apps or stopping ends the session.
- **Manual logging:** you can log a reel yourself from the overlay or the app.
- **Pause / resume:** pause tracking from the dashboard or overlay; resume the same way. While paused, reels are not counted and goals show as paused.
- **Tracking OFF:** turning tracking off globally stops all counting and detection until you turn it back on.
- Viewing time is **measured**, never estimated: the app records real session durations. Where no session was measured, the app says so ("unmeasured" / "Unavailable") instead of guessing.

## Daily goals

Set a daily goal from the Goals screen — either a reel-count goal or a viewing-time goal (in minutes). Progress updates live as you watch. A goal can be:

- **In progress** — you are under the limit.
- **Approaching limit** — you are close to the limit.
- **Completed** — you reached the goal.
- **Paused** — tracking is paused or off.

Goals reset at midnight. Yesterday's result is kept in your history.

## Focus Mode

Focus Mode runs a timed session that helps you step away from scrolling.

1. Open Focus Mode, pick a duration, and start.
2. A session timer runs; pausing keeps the elapsed time you have banked.
3. Finishing a session earns XP. Starting a session does not change your viewing statistics — focus time and viewing time are tracked separately.

Known limitation: if the app is force-stopped while a focus session is paused, the restored session may count the paused time as elapsed (see `PHASE_17_STABILITY_AUDIT.md`, STAB-01). This is a low-severity display issue, not data loss.

## XP, levels, achievements and streaks

- Every logged reel earns XP. XP is recorded once per event — re-logging the same reel never earns XP twice.
- Levelling up grows Munchie and can unlock mastery auras (see above).
- Achievements unlock for milestones (first reel, streaks, goals completed, and more).
- Your streak counts consecutive days with activity.

## Analytics

- **Statistics:** today's and recent totals — reels watched and actual viewing time, per app (Instagram / YouTube).
- **History:** per-day records you can scroll back through.
- **Hourly pattern:** a 24-hour view of when you scroll, based only on monitored time. Hours with no monitoring are labelled **Unmonitored**; thinly monitored hours are **Insufficient data** or **Partially monitored**; well-covered hours are **Well monitored**. The app never extrapolates from unmonitored time.
- **Weekly Intelligence:** a week-over-week comparison of your last 7 complete days against the previous 7 (today is always excluded). Each comparison carries a confidence label — **Insufficient** (percentages hidden), **Limited**, or **High** — based on how many of the 7 days had at least 2 hours of verified monitoring.
- **Weekly digest:** a short, plain-language summary of your week, computed on the device and never stored as a table.
- **Reflections:** short deterministic notes about your week. They never judge you and never guess your mood or health.

## Backup and restore

In **Settings → Backup & Restore** you can:

- **Create a backup** — saves a JSON file (your whole local database) wherever you choose. The file includes a checksum so a damaged backup is detected before anything is changed.
- **Restore a backup** — you choose per-table what to do when a record already exists: keep what's on the phone, use the backup's version, or cancel. A full restore first requires a verified backup of the current data, runs as one transaction, and can be rolled back if it is interrupted.

Backups are plain files you control. The app never uploads them anywhere.

## Privacy

- No account, no login, no cloud, no ads, no analytics SDKs, no internet permission.
- Your reel history lives in a local database on your phone.
- One honest exception: Android's Auto Backup may upload the app's data (including the database) to your Google account if backup is enabled on your device. If you want the data to stay only on the phone, turn off backup for this app in Android Settings → Backup, or ask for the app setting to be changed (see `PHASE_16_DEFECT_LOG.md`, DEF-03).

## Troubleshooting

| Problem | What to check |
|---|---|
| Munchie doesn't appear | Settings → Apps → Reel Monster → "Display over other apps" must be allowed; also make sure the overlay is enabled inside the app. |
| Reels aren't counted automatically | The accessibility service must be on (Settings → Accessibility → Reel Monster). Reels are only detected while Instagram or YouTube is in the foreground. |
| A reel was counted twice | The app de-duplicates rapid re-detections; if you see a duplicate, it is a counting display issue — report the time and app it happened in. |
| Goal stuck on "Paused" after resuming | Pause, then resume again. If it persists, restart the app. (A race that caused this was fixed in v1.0.0; see STAB-04.) |
| Statistics show "Unavailable" | That day has reel counts but no measured viewing sessions, so the app refuses to guess a time. It is honest, not broken. |
| App was force-stopped during a focus session | The restored session time may include time the app was closed (STAB-01). Note the numbers and tell the developer. |
| Backup won't restore | The file may be from a newer database version or damaged. The app validates before changing anything, so your current data is safe — try a different backup file. |

## Uninstalling

Uninstalling deletes the local database and all history with it. Make a backup first if you want to keep your data.
