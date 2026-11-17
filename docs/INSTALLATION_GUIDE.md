# INSTALLATION GUIDE
## Reel Monster v1.0.0 — Android Deployment & Setup Manual

**Target Audience:** End Users, QA Engineers, Android Developers  
**Release Artifact:** `release/reel-monster-v1.0.0.apk`  
**Package:** `com.reelmonster.app`  
**Supported OS:** Android 8.0 (API 26) through Android 17 (API 37)  

---

## 1. Release Artifact Verification

Before installing, verify that your release APK is authentic and uncorrupted by comparing its SHA-256 hash against the official release record:

```bash
# On macOS / Linux:
shasum -a 256 release/reel-monster-v1.0.0.apk

# Expected Output:
d6d5bfc9b7cce825b30f578eafb200754ce9e8d10c4ff218beea5473ed6f7193  reel-monster-v1.0.0.apk
```

---

## 2. Installation Methods

### Method A: ADB Command Line (Recommended for Developers & QA)

1. Connect your Android device to your computer via USB.
2. Ensure **USB Debugging** is enabled on the device under *Settings → Developer Options*.
3. Verify connection:
   ```bash
   adb devices
   ```
4. Install the release APK:
   ```bash
   adb install -r release/reel-monster-v1.0.0.apk
   ```
   *(Expected output: `Performing Streamed Install` followed by `Success`)*
5. Launch the application:
   ```bash
   adb shell am start -n com.reelmonster.app/.MainActivity
   ```

### Method B: Direct APK Sideloading (Recommended for End Users)

1. Copy `reel-monster-v1.0.0.apk` to your phone (via USB cable, local SD card, or local Wi-Fi transfer).
2. Open your device's built-in **Files** or **My Files** app.
3. Locate `reel-monster-v1.0.0.apk` in your *Downloads* folder and tap it.
4. If prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings** on the prompt.
   - Toggle **Allow from this source** to **ON**.
   - Tap the back button to return to the installer.
5. Tap **Install** and wait for completion.
6. Tap **Open** to launch Reel Monster.

---

## 3. First-Run System Permissions Configuration

To enable the floating companion (Munchie) and automatic reel counting, configure the following three system permissions:

### 1. Appear on Top (`SYSTEM_ALERT_WINDOW`)
*Allows Munchie to float as an interactive companion over Instagram and YouTube.*
- On first launch, tap **Wake Up Your Monster**.
- When prompted, tap **Grant Overlay Permission**.
- In the Android system list, locate **Reel Monster** and toggle **Allow display over other apps** to **ON**.
- Press Back to return to Reel Monster.

### 2. Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)
*Allows Reel Monster to detect newly watched reels and shorts without manual taps.*
- Tap **Open Android Settings** on the setup dialog (or go to *Device Settings → Accessibility*).
- Tap **Installed apps** (or **Installed Services** on Samsung devices).
- Tap **Reel Monster**.
- Toggle the main switch **ON** and tap **Allow** on the Android system confirmation dialog.
- *Privacy Reminder:* Reel Monster is 100% offline. It has zero network capability and strictly inspects video player layout classes.

### 3. Notification Permission (`POST_NOTIFICATIONS`)
*Used strictly for Focus Mode completion alerts and gentle intervention check-ins.*
- Tap **Allow** when Android 13+ prompts for notification permissions upon starting your first focus block.

---

## 4. Recommended Battery Optimization Setting

Modern Android systems (particularly Samsung One UI, Xiaomi HyperOS, and OnePlus OxygenOS) aggressively sleep background services. To ensure continuous, accurate monitoring intervals and prevent Munchie from being killed:

1. Open your device's **Settings → Apps → Reel Monster**.
2. Tap **Battery**.
3. Select **Unrestricted** (or disable "Put app to sleep").
4. Under *Permissions*, verify that **Pause app activity if unused** is turned **OFF**.

---

## 5. In-Place Upgrades & Data Preservation

Reel Monster stores all data locally in an SQLite database (`reel_monster.db`). 

- Installing future APK updates via `adb install -r <new-apk>` or sideloading will preserve your reel history, daily goal pacing, focus sessions, and earned XP.
- Database migrations execute automatically and idempotently during application startup without user intervention.
- **Wiping Data:** If you ever wish to completely reset the application, you can either tap **Wipe All History & Reset Monster** in the app's *Settings* tab or clear app storage via Android settings.

---

## 6. Uninstalling Reel Monster

1. (Optional) Turn off the Accessibility Service under *Settings → Accessibility → Installed Apps → Reel Monster*.
2. Long-press the **Reel Monster** icon on your home screen or app drawer.
3. Tap **Uninstall** and confirm.
4. All local SQLite database files and preferences are cleanly deleted by Android upon uninstallation.
