# REEL MONSTER — USER GUIDE
## The Offline Mindful Scrolling Companion for Android

**Version:** 1.0.0  
**Release Date:** 2026-11-09  
**Platform:** Android 8.0+ (Optimized for Android 14, 15, 16 & 17)  

---

## 1. Welcome to Reel Monster

**Reel Monster** is a fun, mindful, and 100% private Android companion designed to help you build a healthier relationship with short-form video feeds like Instagram Reels and YouTube Shorts.

Instead of rigid app blockers that you immediately disable or bypass, Reel Monster gives you **Munchie**—a cute, responsive floating creature that perches unobtrusively on the edge of your screen while you scroll. Munchie "eats" every reel you watch, visually evolving across five distinct growth stages as your viewing time increases.

### Core Guarantees
- 🔒 **100% Offline:** Zero internet permission (`android.permission.INTERNET` is not even in the app manifest). No data ever leaves your device.
- 🚫 **No Account Required:** No sign-up, no email, no cloud syncing, no tracking trackers.
- 🧘 **Empowering, Not Punitive:** Reel Monster never forcibly locks your screen, crashes your apps, or shames you. You remain in complete control.

---

## 2. Getting Started & Initial Setup

When you first open Reel Monster, you will be guided through a simple 2-step setup:

### Step 1: Floating Overlay Permission (`SYSTEM_ALERT_WINDOW`)
To allow Munchie to hover as a friendly bubble above Instagram and YouTube, Android requires the "Display over other apps" permission.
1. Tap **Wake Up Your Monster**.
2. Tap **Grant Overlay Permission** when prompted.
3. Locate **Reel Monster** in your device's "Appear on top" settings list and toggle it **ON**.
4. Press the back arrow to return to Reel Monster.

### Step 2: Automatic Detection Setup (`AccessibilityService`)
Reel Monster uses Android's Accessibility Service solely to inspect video layout containers in Instagram and YouTube to automatically count new reels without requiring manual taps.
1. On the setup dialog, tap **Open Android Settings**.
2. Tap **Installed Services** (or **Downloaded Apps** depending on your Android OEM).
3. Tap **Reel Monster**.
4. Toggle the service **ON** and confirm Android's standard prompt.
5. *Privacy Note:* Reel Monster strictly inspects video player view hierarchies in Instagram and YouTube. It **never** reads personal messages, text fields, keystrokes, or passwords.
6. *Prefer not to use accessibility?* You can tap **Use Manual Mode** at any time to count reels with a single tap on the dashboard or floating overlay.

---

## 3. The Floating Companion (Munchie)

Once enabled, Munchie appears as an interactive floating bubble on your screen:

- **Move Munchie:** Drag Munchie anywhere on your screen. When released, Munchie smoothly docks to the nearest left or right screen edge so your video feed is never obscured.
- **Live Counter Badge:** A subtle pill above Munchie displays today's reel count in real time.
- **Tap Munchie:** Tap Munchie once to hear a supportive comment or view Munchie's current reaction.
- **Long-Press Munchie:** Long-press Munchie to open a quick-action menu (pause tracking, view today's tally, or open the full dashboard).
- **Haptic Crunch:** When enabled in Settings, Munchie produces a delightful subtle vibration feedback whenever a reel is logged or when an evolution occurs.

---

## 4. Munchie's 5 Evolutionary Stages

As your daily scrolling accumulates, Munchie grows and changes appearance:

| Stage | Name | Threshold | Description & Behavior |
|---|---|---|---|
| **1** | **Baby Munchie** | 0 – 5 reels | Tiny, wide-eyed, and full of gentle curiosity. Encourages mindful starts. |
| **2** | **Curious Munchie** | 6 – 14 reels | Grows little ears and winks. Friendly partner checking in on your feed. |
| **3** | **Active Munchie** | 15 – 29 reels | Energetic and bouncy. Gently reminds you that the session is adding up. |
| **4** | **Chubby Munchie** | 30 – 49 reels | Round and slightly sluggish. Suggests taking a hydration or stretch break. |
| **5** | **Overfed Munchie** | 50+ reels | Full, sleepy, and round. Reminds you that you've had a hearty meal of content. |

*Midnight Reset:* Every night at local midnight, Munchie peacefully resets to Baby Munchie for a fresh, clean day.

---

## 5. Daily Goals & Pacing

Reel Monster helps you set realistic daily boundaries:

- **Reel Count Goal:** E.g., limit yourself to 30 reels per day.
- **Viewing Time Goal:** E.g., limit yourself to 30 minutes of actual watched video per day.
- **Live Pacing:** The **Daily Goal Pacing** card on the dashboard shows:
  - Exact reels remaining before reaching your limit.
  - Exact minutes remaining based on actual observed video durations.
  - Pacing status: `On Track`, `Approaching Limit`, `Target Met`, or `Exceeded`.

---

## 6. Focus Mode & Gentle Interventions

Need to get work done or take an intentional social media break?

1. Tap **START ⏱️** on the **Focus Mode** card.
2. Select your focus duration (5 mins to 60 mins).
3. During an active focus block:
   - A small **Focus Badge** displays next to Munchie indicating the countdown.
   - If you open Instagram or YouTube, Munchie shows gentle, non-blocking check-in reminders (e.g., *"Hey there! You're in a 15-minute focus session. Still want to scroll?"*).
   - Reel Monster **never** forcibly terminates or locks your apps.
4. Completing a focus block awards you **+20 XP** toward your leveling progression!

---

## 7. Progression, XP & Mindful Streaks

Reel Monster features an offline progression system designed with strict ethical guidelines: **XP is awarded only for healthy self-management, never for scrolling or app usage.**

### How to Earn XP
- **Complete an eligible Focus Session:** +20 XP
- **Stay within daily Reel Count Goal:** +30 XP
- **Stay within daily Viewing Time Goal:** +30 XP
- **Dual Goal Bonus (meet both count and time goals):** +20 XP
- **Qualify for a Mindful Day (≥ 2 hours verified coverage within limits):** +25 XP
- **3-Day Streak Milestone:** +50 XP
- **7-Day Streak Milestone:** +100 XP
- **30-Day Streak Milestone:** +250 XP
- **Unlock an Achievement:** +30 to +150 XP

### Level Progression
Progress through 10 evolutionary levels from **Level 1 (Tiny Monster)** to **Level 10 (Master Zen Monster)**.

### Mindful Streaks
Keep your streak alive by maintaining mindful usage on consecutive days with verified monitoring coverage.

---

## 8. Statistics, History & Weekly Intelligence

Explore your habits with honest, accurate analytics:

- **Honest Viewing Time:** Reel Monster measures the actual duration videos play on your screen. If monitoring was inactive or unmeasured, it reports honest "Unavailable" rather than misleading approximations.
- **Platform Split:** See the exact division between Instagram Reels and YouTube Shorts.
- **Hourly Scrolling Pattern:** A 24-hour diurnal pattern chart normalized by verified coverage, revealing when you scroll most during your daily routine.
- **Weekly Executive Digest:** A week-over-week comparison showing trends in your scroll velocity, peak days, and mindful improvements.
- **Weekly Reflections:** Non-judgmental, encouraging reflections tailored to your habits.

---

## 9. Customization & Settings

Personalize your experience under the **Settings** tab:

- **Munchie Theme Colors:** Choose between **Mint** (Default Green), **Berry** (Purple), **Sunny** (Warm Orange), or **Ocean** (Cool Blue). Munchie updates instantly on the dashboard and floating overlay.
- **Sensory Toggles:** Turn on/off Tactile Munch Haptics, Sound FX, and Magnetic Edge Snapping.
- **Global Tracking Master Switch:** Instantly pause all monitoring and floating increments with a single master toggle.
- **Daily Reset Control:** If you wish to restart today's counts (up to 5 resets allowed per day), tap **Restart Today**. All historical records remain safe.

---

## 10. Offline Backup & Restore

Keep complete ownership of your data:

- **Create Backup:** Exports all your tracking, session history, goals, focus records, and progression XP into an offline, validated JSON backup file.
- **Restore from Backup (Merge):** Safely merges historical records from a backup file with your current data without overwriting new progress.
- **Restore (Replace All Data):** Atomically replaces all existing records after pre-flight checksum and schema validation. A safety snapshot is created before any destructive operation.

---

## 11. Frequently Asked Questions (FAQ)

**Q: Does Reel Monster consume a lot of battery?**  
A: No. Reel Monster uses ultra-lightweight accessibility event callbacks that trigger only when Instagram or YouTube is open in the foreground. Background battery usage is under 1%.

**Q: Can Reel Monster see my passwords or chats?**  
A: Absolutely not. The app has no internet permission, and its accessibility filter explicitly targets only the video player container classes of Instagram and YouTube.

**Q: What if the floating companion disappears after my phone reboots?**  
A: Reel Monster includes reboot-safe receivers. Ensure "Display over other apps" is granted, and open the app once after rebooting to ensure Android battery optimizers haven't killed the foreground service.

**Q: Why does my viewing time say "Unavailable"?**  
A: Reel Monster refuses to show fake numbers. If an accessibility session was interrupted or monitoring was paused, it reports "Unavailable" rather than guessing.
