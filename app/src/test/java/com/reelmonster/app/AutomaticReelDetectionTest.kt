package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.DetectionState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.service.tracking.DeduplicationEngine
import com.reelmonster.app.service.tracking.DetectionStateMachine
import com.reelmonster.app.service.tracking.InstagramReelDetector
import com.reelmonster.app.service.tracking.MockNodeSource
import com.reelmonster.app.service.tracking.YouTubeShortsDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomaticReelDetectionTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_auto_tracking.db")
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() {
        dbHelper.clearAllData()
        dbHelper.close()
    }

    // =========================================================================
    // 1. DETECTION STATE MACHINE TESTS
    // =========================================================================

    @Test
    fun testInitialStateIsIdle() {
        val stateMachine = DetectionStateMachine()
        assertEquals(DetectionState.IDLE, stateMachine.currentState.value)
        assertNull(stateMachine.currentApp.value)
    }

    @Test
    fun testTargetAppForegroundTransitions() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onTargetAppForeground(TargetApp.INSTAGRAM)

        assertEquals(DetectionState.TARGET_APP_ACTIVE, stateMachine.currentState.value)
        assertEquals(TargetApp.INSTAGRAM, stateMachine.currentApp.value)

        stateMachine.onTargetAppForeground(TargetApp.YOUTUBE)
        assertEquals(DetectionState.TARGET_APP_ACTIVE, stateMachine.currentState.value)
        assertEquals(TargetApp.YOUTUBE, stateMachine.currentApp.value)
    }

    @Test
    fun testViewerEnteredTransitions() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onTargetAppForeground(TargetApp.INSTAGRAM)
        stateMachine.onViewerEntered(TargetApp.INSTAGRAM)

        assertEquals(DetectionState.REEL_VIEWER_ACTIVE, stateMachine.currentState.value)
        assertEquals(TargetApp.INSTAGRAM, stateMachine.currentApp.value)
    }

    @Test
    fun testScrollTransitionLifecycle() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onViewerEntered(TargetApp.INSTAGRAM)
        stateMachine.onScrollTransitionStarted()

        assertEquals(DetectionState.CONTENT_TRANSITION_PENDING, stateMachine.currentState.value)

        val candidate = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "hash_ig_123",
            authorOrChannel = "@creator"
        )
        stateMachine.onContentConfirmed(candidate)
        assertEquals(DetectionState.NEW_CONTENT_CONFIRMED, stateMachine.currentState.value)

        stateMachine.onContentStabilized()
        assertEquals(DetectionState.REEL_VIEWER_ACTIVE, stateMachine.currentState.value)
    }

    @Test
    fun testViewerExitedTransitionsToTargetAppActive() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onViewerEntered(TargetApp.INSTAGRAM)
        stateMachine.onViewerExited()

        assertEquals(DetectionState.TARGET_APP_ACTIVE, stateMachine.currentState.value)
        assertEquals(TargetApp.INSTAGRAM, stateMachine.currentApp.value)
    }

    @Test
    fun testAppBackgroundedResetsToIdle() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onViewerEntered(TargetApp.YOUTUBE)
        stateMachine.onAppBackgrounded()

        assertEquals(DetectionState.IDLE, stateMachine.currentState.value)
        assertNull(stateMachine.currentApp.value)
    }

    @Test
    fun testPauseStateFreezesTransitions() {
        val stateMachine = DetectionStateMachine()
        stateMachine.onViewerEntered(TargetApp.INSTAGRAM)
        stateMachine.onPauseStateChanged(true)

        assertEquals(DetectionState.PAUSED, stateMachine.currentState.value)

        // Events ignored while paused
        stateMachine.onScrollTransitionStarted()
        assertEquals(DetectionState.PAUSED, stateMachine.currentState.value)

        // Resuming restores state
        stateMachine.onPauseStateChanged(false)
        assertEquals(DetectionState.TARGET_APP_ACTIVE, stateMachine.currentState.value)
    }

    // =========================================================================
    // 2. MULTI-STAGE DEDUPLICATION ENGINE TESTS
    // =========================================================================

    @Test
    fun testFirstCandidatePendingDwell() {
        val dedup = DeduplicationEngine(minDwellMs = 800L)
        val candidate = DetectionCandidate(TargetApp.INSTAGRAM, "fp_1", "@cooluser")

        val decision = dedup.processCandidate(candidate, timestamp = 1000L)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, decision)
        assertFalse(dedup.isConfirmed)
    }

    @Test
    fun testDwellFloorSatisfiedConfirmsContent() {
        val dedup = DeduplicationEngine(minDwellMs = 800L)
        val candidate = DetectionCandidate(TargetApp.INSTAGRAM, "fp_1", "@cooluser")

        dedup.processCandidate(candidate, timestamp = 1000L)

        // Less than 800ms
        val earlyDecision = dedup.checkDwell(timestamp = 1500L) // 500ms dwell
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, earlyDecision)
        assertFalse(dedup.isConfirmed)

        // 800ms threshold satisfied
        val confirmedDecision = dedup.checkDwell(timestamp = 1850L) // 850ms dwell
        assertEquals(DeduplicationEngine.Decision.CONFIRMED, confirmedDecision)
        assertTrue(dedup.isConfirmed)
    }

    @Test
    fun testRapidFlingSkimmingRejectedAsFling() {
        val dedup = DeduplicationEngine(minDwellMs = 800L)
        val candidate1 = DetectionCandidate(TargetApp.INSTAGRAM, "fp_1")
        val candidate2 = DetectionCandidate(TargetApp.INSTAGRAM, "fp_2")

        // User lands on candidate 1 at t=1000
        dedup.processCandidate(candidate1, timestamp = 1000L)

        // User flings to candidate 2 after only 200ms at t=1200
        val decision = dedup.processCandidate(candidate2, timestamp = 1200L)
        assertEquals(DeduplicationEngine.Decision.IGNORED_FLING, decision)

        // Now user stays on candidate 2 for 900ms
        val confirmed = dedup.checkDwell(timestamp = 2100L)
        assertEquals(DeduplicationEngine.Decision.CONFIRMED, confirmed)
    }

    @Test
    fun testRepeatedPlaybackEventsIgnoredSameContent() {
        val dedup = DeduplicationEngine(minDwellMs = 800L)
        val candidate = DetectionCandidate(TargetApp.YOUTUBE, "yt_short_1", "TechChannel")

        dedup.processCandidate(candidate, timestamp = 1000L)
        dedup.checkDwell(timestamp = 1850L) // Confirmed

        // Subsequent playback ticks / animated hearts / progress bar updates with same fingerprint
        val repeat1 = dedup.processCandidate(candidate, timestamp = 2500L)
        val repeat2 = dedup.processCandidate(candidate, timestamp = 3500L)
        val repeat3 = dedup.processCandidate(candidate, timestamp = 4500L)

        assertEquals(DeduplicationEngine.Decision.IGNORED_SAME_CONTENT, repeat1)
        assertEquals(DeduplicationEngine.Decision.IGNORED_SAME_CONTENT, repeat2)
        assertEquals(DeduplicationEngine.Decision.IGNORED_SAME_CONTENT, repeat3)
    }

    @Test
    fun testRapidBounceBackWithinWindowSuppressed() {
        val dedup = DeduplicationEngine(minDwellMs = 800L, bounceBackWindowMs = 10_000L)
        val reelA = DetectionCandidate(TargetApp.INSTAGRAM, "fp_A", "@alice")
        val reelB = DetectionCandidate(TargetApp.INSTAGRAM, "fp_B", "@bob")

        // Watch Reel A from t=1000 to t=2000 (confirmed)
        dedup.processCandidate(reelA, timestamp = 1000L)
        dedup.checkDwell(timestamp = 2000L)

        // Swipe to Reel B from t=2500 to t=4000 (confirmed)
        dedup.processCandidate(reelB, timestamp = 2500L)
        dedup.checkDwell(timestamp = 4000L)

        // Swipe back to Reel A at t=6000 (only 4000ms since Reel A confirmed, within 10s bounce-back)
        val bounceDecision = dedup.processCandidate(reelA, timestamp = 6000L)
        assertEquals(DeduplicationEngine.Decision.IGNORED_RECENT_BOUNCE_BACK, bounceDecision)
    }

    @Test
    fun testBounceBackAfterWindowAllowed() {
        val dedup = DeduplicationEngine(minDwellMs = 800L, bounceBackWindowMs = 10_000L)
        val reelA = DetectionCandidate(TargetApp.INSTAGRAM, "fp_A", "@alice")
        val reelB = DetectionCandidate(TargetApp.INSTAGRAM, "fp_B", "@bob")

        // Watch Reel A at t=1000
        dedup.processCandidate(reelA, timestamp = 1000L)
        dedup.checkDwell(timestamp = 2000L)

        // Navigate away to Reel B at t=2500
        dedup.processCandidate(reelB, timestamp = 2500L)
        dedup.checkDwell(timestamp = 3500L)

        // Return to Reel A at t=15000 (13 seconds after Reel A was confirmed, outside 10s window)
        val returnDecision = dedup.processCandidate(reelA, timestamp = 15000L)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, returnDecision)

        val confirmDecision = dedup.checkDwell(timestamp = 16000L)
        assertEquals(DeduplicationEngine.Decision.CONFIRMED, confirmDecision)
    }

    // =========================================================================
    // 3. INSTAGRAM REEL DETECTOR TESTS
    // =========================================================================

    @Test
    fun testMatchesInstagramPackageOnly() {
        val detector = InstagramReelDetector()
        assertTrue(detector.matchesPackage("com.instagram.android"))
        assertFalse(detector.matchesPackage("com.google.android.youtube"))
        assertFalse(detector.matchesPackage("com.android.settings"))
    }

    @Test
    fun testRejectsHomeFeedHierarchy() {
        val detector = InstagramReelDetector()
        val homeFeedHierarchy = MockNodeSource(
            packageName = "com.instagram.android",
            children = listOf(
                MockNodeSource(text = "Direct messages"),
                MockNodeSource(text = "Search and explore"),
                MockNodeSource(text = "Camera")
            )
        )
        assertFalse(detector.isViewerActive(homeFeedHierarchy))
        assertNull(detector.extractCandidate(homeFeedHierarchy))
    }

    @Test
    fun testIdentifiesReelsViewerHierarchy() {
        val detector = InstagramReelDetector()
        val reelsHierarchy = MockNodeSource(
            packageName = "com.instagram.android",
            children = listOf(
                MockNodeSource(text = "@creator_daily"),
                MockNodeSource(text = "Original audio - Chill Beats"),
                MockNodeSource(text = "Watching the sunset! #reels"),
                MockNodeSource(contentDescription = "Like"),
                MockNodeSource(contentDescription = "Comment"),
                MockNodeSource(contentDescription = "Share")
            )
        )
        assertTrue(detector.isViewerActive(reelsHierarchy))
        assertFalse(detector.isChildOverlayActive(reelsHierarchy))

        val candidate = detector.extractCandidate(reelsHierarchy)
        assertNotNull(candidate)
        assertEquals(TargetApp.INSTAGRAM, candidate?.targetApp)
        assertEquals("@creator_daily", candidate?.authorOrChannel)
        assertTrue(candidate?.fingerprint?.isNotEmpty() == true)
    }

    @Test
    fun testChildCommentSheetSuppressionForInstagram() {
        val detector = InstagramReelDetector()
        val commentsOverlayHierarchy = MockNodeSource(
            packageName = "com.instagram.android",
            children = listOf(
                MockNodeSource(text = "Comments"),
                MockNodeSource(text = "Add a comment..."),
                MockNodeSource(contentDescription = "Like"),
                MockNodeSource(contentDescription = "Comment")
            )
        )
        assertTrue(detector.isChildOverlayActive(commentsOverlayHierarchy))
        assertNull(detector.extractCandidate(commentsOverlayHierarchy))
    }

    // =========================================================================
    // 4. YOUTUBE SHORTS DETECTOR TESTS
    // =========================================================================

    @Test
    fun testMatchesYouTubePackageOnly() {
        val detector = YouTubeShortsDetector()
        assertTrue(detector.matchesPackage("com.google.android.youtube"))
        assertFalse(detector.matchesPackage("com.instagram.android"))
    }

    @Test
    fun testRejectsStandard16x9PlayerHierarchy() {
        val detector = YouTubeShortsDetector()
        val standardPlayerHierarchy = MockNodeSource(
            packageName = "com.google.android.youtube",
            children = listOf(
                MockNodeSource(text = "Play in full screen"),
                MockNodeSource(text = "Up next"),
                MockNodeSource(text = "Save to playlist")
            )
        )
        assertFalse(detector.isViewerActive(standardPlayerHierarchy))
        assertNull(detector.extractCandidate(standardPlayerHierarchy))
    }

    @Test
    fun testIdentifiesShortsViewerHierarchy() {
        val detector = YouTubeShortsDetector()
        val shortsHierarchy = MockNodeSource(
            packageName = "com.google.android.youtube",
            children = listOf(
                MockNodeSource(text = "Awesome Science Experiment"),
                MockNodeSource(text = "SciShow"),
                MockNodeSource(text = "Subscribe"),
                MockNodeSource(contentDescription = "Dislike"),
                MockNodeSource(contentDescription = "Remix"),
                MockNodeSource(contentDescription = "Share this video")
            )
        )
        assertTrue(detector.isViewerActive(shortsHierarchy))
        assertFalse(detector.isChildOverlayActive(shortsHierarchy))

        val candidate = detector.extractCandidate(shortsHierarchy)
        assertNotNull(candidate)
        assertEquals(TargetApp.YOUTUBE, candidate?.targetApp)
        assertEquals("SciShow", candidate?.authorOrChannel)
        assertTrue(candidate?.fingerprint?.isNotEmpty() == true)
    }

    @Test
    fun testChildCommentDrawerSuppressionForYouTube() {
        val detector = YouTubeShortsDetector()
        val commentsDrawerHierarchy = MockNodeSource(
            packageName = "com.google.android.youtube",
            children = listOf(
                MockNodeSource(text = "Comments"),
                MockNodeSource(text = "Top comments"),
                MockNodeSource(text = "Add a comment..."),
                MockNodeSource(contentDescription = "Dislike"),
                MockNodeSource(contentDescription = "Remix")
            )
        )
        assertTrue(detector.isChildOverlayActive(commentsDrawerHierarchy))
        assertNull(detector.extractCandidate(commentsDrawerHierarchy))
    }

    // =========================================================================
    // 5. REPOSITORY AUTOMATIC LOGGING & PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun testLogReelWithLevel2Automatic() = runTest {
        preferencesManager.updateTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)

        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC)
        assertTrue(logged)
        assertEquals(1, repository.todayCount.value)

        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(0, yt)

        // Log YouTube Short
        val loggedYt = repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC)
        assertTrue(loggedYt)
        assertEquals(2, repository.todayCount.value)

        val (ig2, yt2) = repository.platformBreakdown.value
        assertEquals(1, ig2)
        assertEquals(1, yt2)
    }

    @Test
    fun testPausedTrackingRejectsAutomaticLogs() = runTest {
        preferencesManager.updateTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
        preferencesManager.updateTrackingPaused(true)
        assertTrue(repository.isTrackingPaused.first { it })

        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC)
        assertFalse(logged)
        assertEquals(0, repository.todayCount.value)

        // Forced log (e.g. user override) succeeds
        val forceLogged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, force = true)
        assertTrue(forceLogged)
        assertEquals(1, repository.todayCount.value)
    }

    @Test
    fun testEventsPersistedWithAutomaticTrackingMode() = runTest {
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC)

        val events = repository.recentEvents.value
        assertTrue(events.isNotEmpty())
        assertEquals(TrackingMode.LEVEL_2_AUTOMATIC, events.first().trackingMode)
        assertEquals(TargetApp.INSTAGRAM, events.first().targetApp)
    }

    // =========================================================================
    // 6. LEVEL 1 MANUAL INDEPENDENCE TESTS
    // =========================================================================

    @Test
    fun testManualTrackingFunctionsIndependentlyOfAutomaticMode() = runTest {
        preferencesManager.setTrackingMode(TrackingMode.LEVEL_1_MANUAL)

        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged)
        assertEquals(1, repository.todayCount.value)

        val events = repository.recentEvents.value
        assertEquals(TrackingMode.LEVEL_1_MANUAL, events.first().trackingMode)
    }

    // =========================================================================
    // 7. REAL-DEVICE DEEP HIERARCHY & PHASE 8B REGRESSION TESTS (SM-S928B)
    // =========================================================================

    private fun wrapAtDepth(depth: Int, pkg: String, leafChildren: List<MockNodeSource>): MockNodeSource {
        var current = MockNodeSource(packageName = pkg, children = leafChildren)
        for (i in 1 until depth) {
            current = MockNodeSource(
                packageName = pkg,
                className = "android.widget.FrameLayout",
                children = listOf(current)
            )
        }
        return current
    }

    @Test
    fun testDeepInstagramHierarchyTransitionBetweenConsecutiveReels() {
        val detector = InstagramReelDetector()

        // Simulate real SM-S928B Instagram hierarchy at depth 24 (including a cached invisible Home feed sibling)
        val reel1DeepRoot = wrapAtDepth(
            depth = 20,
            pkg = "com.instagram.android",
            leafChildren = listOf(
                // Cached off-screen Home feed fragment in swipeable_tab_view_pager (should be ignored!)
                MockNodeSource(
                    viewIdResourceName = "com.instagram.android:id/row_feed_profile_header",
                    text = "showkaseofficial",
                    contentDescription = "Photo by Heliosempire Pvt Ltd"
                ),
                // Active Clips viewer container
                MockNodeSource(
                    viewIdResourceName = "com.instagram.android:id/clips_viewer_view_pager",
                    className = "androidx.viewpager2.widget.ViewPager2",
                    children = listOf(
                        MockNodeSource(
                            viewIdResourceName = "com.instagram.android:id/clips_media_component",
                            contentDescription = "Reel by kuldeep_singh_6233. Double tap to play or pause."
                        ),
                        MockNodeSource(
                            viewIdResourceName = "com.instagram.android:id/clips_author_username",
                            text = "kuldeep_singh_6233",
                            contentDescription = "kuldeep_singh_6233"
                        ),
                        MockNodeSource(contentDescription = "Like"),
                        MockNodeSource(contentDescription = "Comment")
                    )
                )
            )
        )

        val reel2DeepRoot = wrapAtDepth(
            depth = 20,
            pkg = "com.instagram.android",
            leafChildren = listOf(
                // Cached off-screen Home feed fragment in swipeable_tab_view_pager (should be ignored!)
                MockNodeSource(
                    viewIdResourceName = "com.instagram.android:id/row_feed_profile_header",
                    text = "showkaseofficial",
                    contentDescription = "Photo by Heliosempire Pvt Ltd"
                ),
                // Active Clips viewer container
                MockNodeSource(
                    viewIdResourceName = "com.instagram.android:id/clips_viewer_view_pager",
                    className = "androidx.viewpager2.widget.ViewPager2",
                    children = listOf(
                        MockNodeSource(
                            viewIdResourceName = "com.instagram.android:id/clips_media_component",
                            contentDescription = "Reel by neurogumindia. Double tap to play or pause."
                        ),
                        MockNodeSource(
                            viewIdResourceName = "com.instagram.android:id/clips_author_username",
                            text = "neurogumindia",
                            contentDescription = "neurogumindia"
                        ),
                        MockNodeSource(
                            text = "Most people wait for focus to show up."
                        )
                    )
                )
            )
        )

        assertTrue(detector.isViewerActive(reel1DeepRoot))
        assertTrue(detector.isViewerActive(reel2DeepRoot))

        val candidate1 = detector.extractCandidate(reel1DeepRoot)
        val candidate2 = detector.extractCandidate(reel2DeepRoot)

        assertNotNull(candidate1)
        assertNotNull(candidate2)
        assertEquals("kuldeep_singh_6233", candidate1?.authorOrChannel)
        assertEquals("neurogumindia", candidate2?.authorOrChannel)
        assertTrue(candidate1?.fingerprint != candidate2?.fingerprint)
    }

    @Test
    fun testDeepYouTubeShortsHierarchyTransitionBetweenConsecutiveShorts() {
        val detector = YouTubeShortsDetector()

        // Simulate real SM-S928B YouTube Shorts hierarchy at depth 26
        val short1DeepRoot = wrapAtDepth(
            depth = 22,
            pkg = "com.google.android.youtube",
            leafChildren = listOf(
                MockNodeSource(
                    viewIdResourceName = "com.google.android.youtube:id/reel_watch_fragment_root",
                    children = listOf(
                        MockNodeSource(
                            viewIdResourceName = "com.google.android.youtube:id/reel_player_page_container"
                        ),
                        MockNodeSource(
                            contentDescription = "Go to channel @AmazingMomentsShort"
                        ),
                        MockNodeSource(
                            contentDescription = "Subscribe to @AmazingMomentsShort."
                        ),
                        MockNodeSource(
                            contentDescription = "Like this video along with 324 other people"
                        ),
                        MockNodeSource(
                            contentDescription = "Dislike this video"
                        )
                    )
                )
            )
        )

        val short2DeepRoot = wrapAtDepth(
            depth = 22,
            pkg = "com.google.android.youtube",
            leafChildren = listOf(
                MockNodeSource(
                    viewIdResourceName = "com.google.android.youtube:id/reel_watch_fragment_root",
                    children = listOf(
                        MockNodeSource(
                            viewIdResourceName = "com.google.android.youtube:id/reel_player_page_container"
                        ),
                        MockNodeSource(
                            text = "@1anirank1"
                        ),
                        MockNodeSource(
                            contentDescription = "Subscribe to @1anirank1"
                        ),
                        MockNodeSource(
                            text = "Mom is on next level #shorts #funny"
                        ),
                        MockNodeSource(
                            contentDescription = "Remix this Short"
                        )
                    )
                )
            )
        )

        assertTrue(detector.isViewerActive(short1DeepRoot))
        assertTrue(detector.isViewerActive(short2DeepRoot))

        val candidate1 = detector.extractCandidate(short1DeepRoot)
        val candidate2 = detector.extractCandidate(short2DeepRoot)

        assertNotNull(candidate1)
        assertNotNull(candidate2)
        assertEquals("@AmazingMomentsShort", candidate1?.authorOrChannel)
        assertEquals("@1anirank1", candidate2?.authorOrChannel)
        assertTrue(candidate1?.fingerprint != candidate2?.fingerprint)
    }

    @Test
    fun testYouTubeEngagementPanelWrapperDetectedAsChildOverlayEvenWhenPlayerHidden() {
        val detector = YouTubeShortsDetector()

        // On real SM-S928B, opening YouTube Shorts comments hides reel_watch_fragment_root
        // and shows app_engagement_panel_wrapper with Comments header at depth 14.
        val commentsHierarchy = wrapAtDepth(
            depth = 14,
            pkg = "com.google.android.youtube",
            leafChildren = listOf(
                MockNodeSource(
                    viewIdResourceName = "com.google.android.youtube:id/app_engagement_panel_wrapper"
                ),
                MockNodeSource(
                    text = "Comments"
                ),
                MockNodeSource(
                    contentDescription = "Top comments"
                ),
                MockNodeSource(
                    text = "Add a comment..."
                )
            )
        )

        assertTrue(detector.isChildOverlayActive(commentsHierarchy))
        assertNull(detector.extractCandidate(commentsHierarchy))
    }

    @Test
    fun testProgressiveMetadataEnrichmentDoesNotDoubleCountOrResetDwell() {
        val dedup = DeduplicationEngine(minDwellMs = 450L)

        // Frame 1 at t=1000ms: only author has bound, caption is still empty
        val initialFrame = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "instagram|neurogumindia|",
            authorOrChannel = "neurogumindia",
            titleOrCaption = ""
        )
        val d1 = dedup.processCandidate(initialFrame, timestamp = 1000L)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, d1)

        // Frame 2 at t=1200ms: caption binds on the same Reel (merged without resetting dwell)
        val enrichedFrame = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "instagram|neurogumindia|most people wait for focus",
            authorOrChannel = "neurogumindia",
            titleOrCaption = "Most people wait for focus"
        )
        val d2 = dedup.processCandidate(enrichedFrame, timestamp = 1200L)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, d2)
        assertEquals(200L, dedup.currentDwellElapsedMs(1200L))

        // At t=1460ms (460ms since initialFrame at t=1000ms), dwell is satisfied!
        val dwellDecision = dedup.checkDwell(timestamp = 1460L)
        assertEquals(DeduplicationEngine.Decision.CONFIRMED, dwellDecision)
        assertTrue(dedup.isConfirmed)

        // Subsequent frames with enriched or unenriched metadata for the same creator are ignored
        val d3 = dedup.processCandidate(enrichedFrame, timestamp = 1800L)
        assertEquals(DeduplicationEngine.Decision.IGNORED_SAME_CONTENT, d3)
    }

    @Test
    fun testAutoConfirmsOutgoingDwelledCandidateWhenNextCandidateArrives() {
        val dedup = DeduplicationEngine(minDwellMs = 450L)
        val reel1 = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "instagram|creator_one|caption_one",
            authorOrChannel = "creator_one",
            titleOrCaption = "caption_one"
        )
        val reel2 = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "instagram|creator_two|caption_two",
            authorOrChannel = "creator_two",
            titleOrCaption = "caption_two"
        )

        // User lands on reel1 at t=1000ms
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, dedup.processCandidate(reel1, timestamp = 1000L))

        // User watches reel1 for 600ms (>= 450ms) and swipes to reel2 at t=1600ms
        // before the background coroutine checked dwell for reel1
        val d2 = dedup.processCandidate(reel2, timestamp = 1600L)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, d2)

        // reel1 should have been auto-confirmed instead of lost!
        val autoConfirmed = dedup.consumeAutoConfirmedPrevious()
        assertNotNull(autoConfirmed)
        assertEquals("creator_one", autoConfirmed?.authorOrChannel)
        assertNull(dedup.consumeAutoConfirmedPrevious())
    }

    @Test
    fun testResetTodayCountForTestingPreservesHistoricalDays() = runTest {
        // Insert historical logs on previous dates
        dbHelper.insertReelEvent(
            app = TargetApp.INSTAGRAM,
            mode = TrackingMode.LEVEL_2_AUTOMATIC,
            timestamp = 1700000000000L,
            dateStr = "2026-11-06"
        )
        dbHelper.insertReelEvent(
            app = TargetApp.YOUTUBE,
            mode = TrackingMode.LEVEL_2_AUTOMATIC,
            timestamp = 1700086400000L,
            dateStr = "2026-11-07"
        )

        // Log 3 reels today
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, force = true)
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, force = true)
        repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC, force = true)
        assertEquals(3, repository.todayCount.value)

        // Reset today's count only
        repository.resetTodayCountForTesting()

        assertEquals(0, repository.todayCount.value)
        assertEquals(Pair(0, 0), repository.platformBreakdown.value)

        // Verify historical dates are still intact
        val history = dbHelper.getLast7Days()
        assertEquals(1, history.firstOrNull { it.dateString == "2026-11-06" }?.totalReels)
        assertEquals(1, history.firstOrNull { it.dateString == "2026-11-07" }?.totalReels)
    }
}
