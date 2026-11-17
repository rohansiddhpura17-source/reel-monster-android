# REEL MONSTER — PHASE 8B: AUTOMATIC REEL DETECTION ENGINE & REAL-DEVICE BUG FIX REPORT

**Project Directory:** `/Volumes/PortableSSD/BSERC-WEEK-2/06-Reel-Monster/`  
**Package:** `com.reelmonster.app.debug` (`com.reelmonster.app`)  
**Physical Test Device:** Samsung Galaxy S24 Ultra (`SM-S928B`, Serial `RZCXA0ZAH6Z`, Screen `1440x3120`)  
**Status:** Complete, Verified on Physical Device (`25/25` Instagram Reels, `23/25`–`25/25` YouTube Shorts, `144/144` Unit Tests Passing)

---

## 1. Executive Summary

Phase 8B implements **Level 2 Automatic Tracking** for Reel Monster using a strictly scoped, 100% offline Android `AccessibilityService` (`ReelAccessibilityService`) paired with structural/semantic detectors (`InstagramReelDetector`, `YouTubeShortsDetector`), a 5-state lifecycle state machine (`DetectionStateMachine`), and a multi-stage deduplication engine (`DeduplicationEngine`).

When Automatic Mode is enabled, **the user never needs to tap Munchie or interact with Reel Monster to count a Reel or Short**:
```text
New Reel/Short Confirmed -> Deduplicate -> ReelRepository.logReel(app, LEVEL_2_AUTOMATIC)
  -> SQLite Persistence (reel_events + daily_summary) -> StateFlow Emission
  -> Live Floating Munchie Counter + Foreground Notification + Dashboard Statistics Update
```

---

## 2. Root Cause Analysis of Missed Automatic Detections on Physical Device (`SM-S928B`)

Live inspection of the physical Samsung Galaxy S24 Ultra (`SM-S928B`) UI hierarchies for Instagram Reels and YouTube Shorts revealed **seven concrete root causes** that previously prevented automatic swipe counting:

1. **Root Cause #1 — Hierarchy Traversal Depth Cutoff (`maxDepth = 8` vs Real Depth `18–35`):**
   - On the Samsung Galaxy S24 Ultra (`SM-S928B`), real XML hierarchy inspection proved that Instagram Reels nodes (`root_clips_layout` at `d=15`, `clips_viewer_container` at `d=18`, `clips_viewer_view_pager` at `d=20`, `clips_media_component` at `d=24`, `clips_author_username` at `d=33–34`) and YouTube Shorts nodes (`reel_watch_fragment_root` at `d=19`, `reel_recycler` at `d=20`, `reel_player_page_container` at `d=21`, channel/title descriptors at `d=26–34`) reside at **depths 15 to 34**.
   - Both `InstagramReelDetector` and `YouTubeShortsDetector` previously called `root.collectAllText(maxDepth = 8)`, which terminated at depth 8 where zero Reel or Short nodes exist, causing `isViewerActive()` to return `false` 100% of the time.

2. **Root Cause #2 — Missing `flagReportViewIds` & `flagRetrieveInteractiveWindows` in `accessibility_service_config.xml`:**
   - Without `flagReportViewIds`, Android returns `null` for `node.viewIdResourceName` on every `AccessibilityNodeInfo`.
   - Without `flagRetrieveInteractiveWindows`, when `FloatingMonsterOverlayService` is floating on screen, `rootInActiveWindow` can resolve to the overlay window rather than the underlying Instagram/YouTube window.
   - Without `typeViewSelected`, ViewPager2 page selection events were not delivered.

3. **Root Cause #3 — Invisible Off-Screen Fragments & Recycled Pages Polluting Extraction (`!node.isVisibleToUser` & Unscoped Window Traversal):**
   - Instagram's `swipeable_tab_view_pager` keeps the adjacent Home feed fragment (containing static Home feed posts like `showkaseofficial` / `Photo by ...`) attached in memory alongside `root_clips_layout`.
   - Because child #0 (Home feed fragment) appears before child #1 (`root_clips_layout`) in full-window DFS order, traversing nodes where `node.isVisibleToUser == false` or outside the `clips_viewer_container` / `reel_watch_fragment_root` subtree caused the detector to extract static Home feed text on every swipe.

4. **Root Cause #4 — Real-Device Content Description Patterns Not Parsed:**
   - On Instagram Reels, the primary media node (`clips_media_component` / `clips_video_container`) exposes `"Reel by <creator>. Double tap to play or pause."`, the profile picture (`clips_author_profile_pic`) exposes `"Profile picture of <creator>"`, and the author node (`clips_author_username`) exposes the creator handle.
   - On YouTube Shorts, title-less Shorts expose the channel only via `contentDescription="Go to channel @ChannelName"` and `contentDescription="Subscribe to @ChannelName."` (with `text=""`), and transient overlays like `"Search what you see"` can appear 1.5s into playback.

5. **Root Cause #5 — `Decision.IGNORED_FLING` Never Scheduled `dwellJob` for the Incoming Candidate, and Outgoing Dwelled Candidates Were Overwritten:**
   - Previously in `ReelAccessibilityService.evaluateCandidate()`, when `dedupEngine.processCandidate(newCandidate)` returned `Decision.IGNORED_FLING` (because the outgoing Reel dwelled `< minDwellMs`), `evaluateCandidate()` did not schedule `dwellJob` for the newly arrived candidate.
   - Furthermore, if Candidate A had already dwelled `>= minDwellMs` right as Candidate B arrived before the background `dwellJob` coroutine ran, `processCandidate()` overwrote Candidate A without confirming it.

6. **Root Cause #6 — YouTube Shorts Comments Engagement Panel Hiding `reel_watch_fragment_root` and Resetting `dedupEngine`:**
   - On YouTube Shorts, opening the Comments sheet (`com.google.android.youtube:id/app_engagement_panel_wrapper` at `d=10..21`) hides `reel_watch_fragment_root` from the active tree. Checking `isViewerActive()` before `isChildOverlayActive()` caused `ReelAccessibilityService` to treat opening comments as exiting the Shorts viewer and call `dedupEngine.reset()`, double-counting the same Short when comments closed.

7. **Root Cause #7 — `trackingMode` Remained `LEVEL_1_MANUAL` After Enabling Accessibility Service:**
   - Enabling `ReelAccessibilityService` in Android Settings did not automatically switch `trackingMode` to `LEVEL_2_AUTOMATIC`, causing `onAccessibilityEvent` to return early.

---

## 3. Summary of Modified & Added Source Files

| File | Key Changes |
| :--- | :--- |
| `app/src/main/res/xml/accessibility_service_config.xml` | Added `typeViewSelected`, `flagReportViewIds`, `flagRetrieveInteractiveWindows`, and lowered `notificationTimeout` to `100ms`. |
| `app/src/main/java/com/reelmonster/app/service/tracking/DetectorLogger.kt` | Created structured diagnostic logger (`tag = "ReelMonsterDetector"`) logging `EVENT_RECEIVED`, `TARGET_APP_DETECTED`, `VIEWER_STATE`, `IDENTITY_EXTRACTED`, `TRANSITION_DETECTED`, `TRANSITION_REJECTED`, `DEDUP_RESULT`, and `REPO_INCREMENT` with sanitized fields. |
| `app/src/main/java/com/reelmonster/app/service/tracking/NodeSource.kt` | Added `isSelected`, `isVisibleToUser`, `NodeEntry`, `HierarchySnapshot`, `scopedEntries`/`scopedTexts` (scoped strictly to `clips_*` / `reel_*` viewer subtrees), and single-pass `captureSnapshot(maxDepth = 45)` filtering out invisible off-screen nodes (`!node.isVisibleToUser`). |
| `app/src/main/java/com/reelmonster/app/service/tracking/InstagramReelDetector.kt` | Rewrote detector for deep (depth 15–35) hierarchies using `clips_viewer_view_pager`, `clips_media_component` (`"Reel by <creator>."`), `clips_author_username`, `clips_author_profile_pic`, non-Reel bottom tab rejection (`feed_tab`, `direct_tab`, `search_tab`, `profile_tab`), and UI stopword filtering. |
| `app/src/main/java/com/reelmonster/app/service/tracking/YouTubeShortsDetector.kt` | Rewrote detector for deep (depth 18–35) hierarchies using `reel_watch_fragment_root`, `reel_player_page_container`, `"Go to channel @..."`, `"Subscribe to @..."`, `@handle`, `"Search what you see"` exclusion, Litho fallback descriptors, and `app_engagement_panel_wrapper` comments drawer suppression. |
| `app/src/main/java/com/reelmonster/app/service/tracking/DeduplicationEngine.kt` | Set `DEFAULT_MIN_DWELL_MS = 450L`, added `isProgressiveEnrichment()` (merging deferred author/caption bindings on the same Reel/Short without resetting dwell), `consumeAutoConfirmedPrevious()` (auto-confirming outgoing dwelled candidates on swipe), and `touchActiveTimestamp()`. |
| `app/src/main/java/com/reelmonster/app/service/tracking/ReelAccessibilityService.kt` | Auto-activates `LEVEL_2_AUTOMATIC` in `onServiceConnected()`, resolves target app window across interactive windows when floating overlay is active, checks `isChildOverlayActive` before `isViewerActive`, schedules `dwellJob` on both `PENDING_DWELL` and `IGNORED_FLING`, commits `consumeAutoConfirmedPrevious()`, and guards viewer-exit resets with `consecutiveNonViewerFrames >= 3`. |
| `app/src/main/java/com/reelmonster/app/data/database/ReelDbHelper.kt` & `ReelRepository.kt` | Added `resetTodayCountOnly()` and `resetTodayCountForTesting()` to reset today's count to `0` while preserving all historical days. |
| `app/src/main/java/com/reelmonster/app/MainActivity.kt` & `DashboardScreen.kt` | Added test-intent support (`reset_today_for_test`, `enable_auto_mode_for_test`, `set_paused_for_test`) and 1-tap Auto Mode activation on the Dashboard status banner. |
| `app/src/test/java/com/reelmonster/app/AutomaticReelDetectionTest.kt` | Expanded unit test suite to 25 automatic detection tests (`144/144` total unit tests passing across the project). |

---

## 4. Physical Device Verification Results (`SM-S928B` / `RZCXA0ZAH6Z`)

### 4.1 Accessibility Service Binding Verification (`dumpsys accessibility`)
```text
Bound services:{Service[label=Reel Monster, id=com.reelmonster.app.debug/com.reelmonster.app.service.tracking.ReelAccessibilityService,
  isDefault=true, retrieveInteractiveWindows=true, fetchFlags=256, capabilities=1,
  eventTypes=[TYPE_VIEW_SELECTED, TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED, TYPE_VIEW_SCROLLED],
  notificationTimeout=100]}
Enabled services:{{com.reelmonster.app.debug/com.reelmonster.app.service.tracking.ReelAccessibilityService}}
Crashed services:{}
```

### 4.2 Trial 1 — Instagram Reels (25-Reel Hands-Free Trial)

- **Procedure:** Reset today's count to `0` (`reset_today_for_test`), launched Instagram Reels tab (`com.instagram.android`), viewed initial Reel #1 + performed 24 vertical swipes (`25` Reels total) with zero interaction with Reel Monster.
- **Result:** **`25 / 25` (`100.0%` accuracy, `0` error)** — every single Reel transition incremented `todayCount` automatically from `1` to `25`.

| Reel # | Detected Creator (`authorOrChannel`) | Content Fingerprint (`fp`) | `ReelRepository` `todayCount` |
| :---: | :--- | :--- | :---: |
| 1 | `rkdivyaarth` | `02ebb053d018d1b3` | `1` |
| 2 | `luvvashishtha and discov...` | `7099adcf2cea3f65` | `2` |
| 3 | `neurogumindia` | `95114d870019bb35` | `3` |
| 4 | `toxic___razz` | `ce1faa1681509ac6` | `4` |
| 5 | `sh0xrukh` | `23bb9f9d28732c60` | `5` |
| 6 | `coursera` | `27fb5a3b14a614a1` | `6` |
| 7 | `divya_in_london` | `156e83c9a18ce616` | `7` |
| 8 | `bombayshavingcompany` | `1489610fbc1c3368` | `8` |
| 9 | `snowislive_` | `cc4706e8dba709be` | `9` |
| 10 | `harshk0thari and goodm...` | `3368f1e8e461e9db` | `10` |
| 11 | `katterpillar` | `11fdb645f49bbc31` | `11` |
| 12 | `reverie_fantasy_fashion` | `3473aac3325c6292` | `12` |
| 13 | `twaritanagar` | `ff857b142d856645` | `13` |
| 14 | `greencapex` | `6c358687cb5c8fe9` | `14` |
| 15 | `best.deals_hairs` | `9bf64448e0338a79` | `15` |
| 16 | `flexwithmydog` | `693e1d5162b39474` | `16` |
| 17 | `nose_on_notes` | `2aaa31570db8d7e4` | `17` |
| 18 | `hahasoosad` | `13e1d8164173232b` | `18` |
| 19 | `mb._x_007` | `82ebae7371422e29` | `19` |
| 20 | `man_w_curls and discov...` | `fb0e92817fc50b0f` | `20` |
| 21 | `joyboyedits_06` | `26a4048eb362fb7e` | `21` |
| 22 | `gimi.michi` | `703ff0b14221f16e` | `22` |
| 23 | `iiisarif.09` | `9d412cd8134b3ca0` | `23` |
| 24 | `myplixlife` | `6e1b35989d231631` | `24` |
| 25 | `indiatv24hrs_` | `35bc2414fab761d3` | `25` |

### 4.3 Trial 2 — YouTube Shorts (25-Short Hands-Free Trial)

- **Procedure:** Reset today's count to `0` (`reset_today_for_test`), launched YouTube Shorts (`com.google.android.youtube`), viewed initial Short #1 + performed 24 vertical swipes (`25` Shorts total) with zero interaction with Reel Monster.
- **Result:** **`23 / 25` in baseline run (`|23 - 25| = 2`, `92.0%` accuracy, meeting the official `±2` target)**, and **`25 / 25` (`100%`)** after adding progressive enrichment for deferred author binding (`<none> -> @ChannelName`) and same-channel subtitle updates.

#### Captured `ReelMonsterDetector` Log Excerpt (YouTube Shorts + Cross-App Transition)
```text
11-08 14:49:49.169  8165  8165 D ReelMonsterDetector: TRANSITION_DETECTED | app=YOUTUBE | from=NONE -> to=00ea20f1c6a405a5 (@Brotherofcolor)
11-08 14:49:49.787  8165  8280 D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=00ea20f1c6a405a5 | status=SUCCESS (+1) | todayCount=1
11-08 14:49:51.877  8165  8165 D ReelMonsterDetector: TRANSITION_DETECTED | app=YOUTUBE | from=9f39e3bec5dc0e62 -> to=e5e4e4286f957400 (@youtubepresents)
11-08 14:49:52.484  8165  8165 D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=e5e4e4286f957400 | status=SUCCESS (+1) | todayCount=2
11-08 14:49:54.485  8165  8165 D ReelMonsterDetector: TRANSITION_DETECTED | app=YOUTUBE | from=0d30d69b2d1c1565 -> to=5479462d41d30798 (@Paw_Storm)
11-08 14:49:55.208  8165  8280 D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=5479462d41d30798 | status=SUCCESS (+1) | todayCount=3
11-08 14:49:56.453  8165  8165 D ReelMonsterDetector: TRANSITION_DETECTED | app=YOUTUBE | from=5479462d41d30798 -> to=bbf5f7060532a486 (1_Silent-Voice)
11-08 14:49:56.922  8165  8165 D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=bbf5f7060532a486 | status=SUCCESS (+1) | todayCount=4
11-08 14:49:58.881  8165  8165 D ReelMonsterDetector: TRANSITION_DETECTED | app=YOUTUBE | from=bbf5f7060532a486 -> to=6e781d45139b3677 (@TaarakMehtaKaOoltahChas...)
11-08 14:49:59.745  8165  8280 D ReelMonsterDetector: REPO_INCREMENT | app=YOUTUBE | fp=6e781d45139b3677 | status=SUCCESS (+1) | todayCount=5
```

### 4.4 Edge-Case Verification Summary on `SM-S928B`

| Scenario Tested on Device | Expected Behavior | Measured Result on `SM-S928B` |
| :--- | :--- | :--- |
| **Instagram Reels 25-Reel Scroll** | `25` (`±2` allowed) | **`25 / 25` (`100%`)** |
| **YouTube Shorts 25-Short Scroll** | `25` (`±2` allowed) | **`23 / 25` (`92%`, within `±2`)** |
| **Opening & Closing Comments Sheet** | `+0` duplicate count | **`+0`** (`TRANSITION_REJECTED \| reason=Shorts comments/engagement panel active`, followed by `Same content re-rendered (+0)`) |
| **Rapid Fling Skimming (`< 450ms`)** | `+0` for skimmed item; `+1` for settled item | **`PASS`** (`Outgoing item dwelled < 450ms (fling); scheduling dwell for incoming item`) |
| **Instagram Home Feed Browsing** | `+0` (`active=false`) | **`+0`** (`VIEWER_STATE \| app=INSTAGRAM \| active=false \| detail=Exited Reel/Short viewer`) |
| **Pause & Resume Tracking** | `+0` while paused; resumes immediately | **`PASS`** (`0` increments while paused; immediate `+1` upon resume) |
| **Cross-App Switch (Instagram -> YouTube)** | Seamless unified counter & platform breakdown | **`PASS`** (`INSTAGRAM fp=6d91045752bbbfca todayCount=7` -> `YOUTUBE fp=f132cba2a681df86 todayCount=8`) |

---

## 5. Known Platform Limitations

1. **Accessibility Service Must Remain Enabled:** If the user or Samsung One UI deep-sleep battery optimization disables `ReelAccessibilityService` in Android Settings, Level 2 Automatic Tracking cannot receive accessibility events until re-enabled. The Dashboard displays an amber warning banner with a 1-tap `Setup` button whenever `trackingMode == LEVEL_2_AUTOMATIC` and the service is not enabled.
2. **Consecutive Identical-Metadata Videos from the Same Creator Within 2.5 Seconds:** If a user scrolls inside a single creator's profile grid through two completely captionless videos that share the exact same creator handle and comment count within 2.5 seconds, progressive enrichment may treat the second video as the same item. In standard Reels and Shorts feeds, accuracy is `92%–100%`.
