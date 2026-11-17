package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManualTrackingEngineTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_manual_tracking.db")
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testOneTapIncrement() {
        val initialCount = repository.todayCount.value
        assertEquals(0, initialCount)

        // Record a single tap for Instagram Reel
        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue("Single tap must be accepted", logged)

        // 1. Total count increased by exactly 1
        assertEquals(1, repository.todayCount.value)

        // 2. Timestamp recorded in recent events
        val events = repository.recentEvents.value
        assertEquals(1, events.size)
        val event = events.first()
        assertTrue("Timestamp must be valid positive timestamp", event.timestamp > 0)

        // 3. Application recorded correctly
        assertEquals(TargetApp.INSTAGRAM, event.targetApp)
        assertEquals("Instagram", event.targetApp.displayName)
        assertEquals("Reels", event.targetApp.mediaType)
        assertEquals("com.instagram.android", event.targetApp.packageName)

        // 4. Mode recorded
        assertEquals(TrackingMode.LEVEL_1_MANUAL, event.trackingMode)

        // 5. Daily platform breakdown updated
        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(0, yt)

        // 6. Estimated time lost updated
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(repository.todayCount.value), 0.001)

        // 7. Monster stage verified
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // 8. Funny message available
        val quote = MunchieMascotEngine.getMessage(repository.munchieStage.value, repository.todayCount.value)
        assertNotNull(quote)
        assertTrue(quote.isNotBlank())
    }

    @Test
    fun testRapidSuccessiveTapsDebounce() {
        // First tap: accepted
        val tap1 = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue("First tap must be accepted", tap1)
        assertEquals(1, repository.todayCount.value)

        // Second immediate tap on same app (sub-millisecond without delay): debounced
        val tap2 = repository.logReel(TargetApp.INSTAGRAM)
        assertFalse("Rapid successive tap within 350ms must be rejected as accidental bounce", tap2)

        // Count must still be 1, not 2
        assertEquals(1, repository.todayCount.value)
        assertEquals(1, repository.recentEvents.value.size)
    }

    @Test
    fun testLegitimateConsecutiveTapsWithDelay() {
        // First tap
        val tap1 = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue(tap1)
        assertEquals(1, repository.todayCount.value)

        // Simulate legitimate pause between consecutive videos (exceeding debounce threshold)
        Thread.sleep(ReelRepository.DEBOUNCE_THRESHOLD_MS + 20)

        // Second tap
        val tap2 = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue("Consecutive tap after debounce delay must be accepted", tap2)
        assertEquals(2, repository.todayCount.value)
        assertEquals(2, repository.recentEvents.value.size)
    }

    @Test
    fun testDifferentAppsSuccessiveTaps() {
        // Tapping Instagram then immediately tapping YouTube (different target) is intentional and allowed
        val tapIg = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue(tapIg)

        val tapYt = repository.logReel(TargetApp.YOUTUBE)
        assertTrue("Switching apps between taps is accepted", tapYt)

        assertEquals(2, repository.todayCount.value)
        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(1, yt)
    }

    @Test
    fun testInstagramEventRecording() {
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)

        val events = repository.recentEvents.value
        val event = events.first()
        assertEquals(TargetApp.INSTAGRAM, event.targetApp)
        assertEquals("Instagram", event.targetApp.displayName)
        assertEquals("Reels", event.targetApp.mediaType)
        assertEquals("com.instagram.android", event.targetApp.packageName)

        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(0, yt)
    }

    @Test
    fun testYouTubeEventRecording() {
        repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)

        val events = repository.recentEvents.value
        val event = events.first()
        assertEquals(TargetApp.YOUTUBE, event.targetApp)
        assertEquals("YouTube", event.targetApp.displayName)
        assertEquals("Shorts", event.targetApp.mediaType)
        assertEquals("com.google.android.youtube", event.targetApp.packageName)

        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(0, ig)
        assertEquals(1, yt)
    }

    @Test
    fun testPauseAndResumeTracking() = runTest {
        // Initial active state
        assertFalse(repository.isTrackingPaused.value)
        assertTrue(repository.isTrackingActive)

        // Pause tracking
        preferencesManager.updateTrackingPaused(true)
        assertTrue(repository.isTrackingPaused.first { it })
        assertFalse(repository.isTrackingActive)

        // Tapping while paused must be rejected
        val loggedWhilePaused = repository.logReel(TargetApp.INSTAGRAM)
        assertFalse("Logging must be blocked while tracking is paused", loggedWhilePaused)
        assertEquals(0, repository.todayCount.value)

        // Resume tracking
        preferencesManager.updateTrackingPaused(false)
        assertFalse(repository.isTrackingPaused.first { !it })
        assertTrue(repository.isTrackingActive)

        // Tapping after resume must be accepted
        val loggedAfterResume = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue("Logging must succeed after tracking is resumed", loggedAfterResume)
        assertEquals(1, repository.todayCount.value)
    }

    @Test
    fun testDatabasePersistenceAcrossRestart() {
        // Log 3 Instagram reels and 2 YouTube Shorts
        repeat(3) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        repeat(2) { repository.logReel(TargetApp.YOUTUBE, force = true) }
        assertEquals(5, repository.todayCount.value)

        // Simulate app kill / restart by creating a new repository instance pointing to same SQLite database
        val newDbHelper = ReelDbHelper(context)
        val newRepository = ReelRepository(newDbHelper, preferencesManager)

        // Verify count and history persisted completely in SQLite
        assertEquals(5, newRepository.todayCount.value)
        assertEquals(Pair(3, 2), newRepository.platformBreakdown.value)
        assertEquals(5, newRepository.recentEvents.value.size)
        assertEquals(MunchieStage.BABY, newRepository.munchieStage.value)

        newDbHelper.close()
    }

    @Test
    fun testMidnightTransitionHistoricalPreservation() {
        // Simulate yesterday's session ("2026-11-07") with 10 reels
        val yesterdayTs = System.currentTimeMillis() - 86400000L
        repeat(10) {
            repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, yesterdayTs, "2026-11-07")
        }

        // Today's session logged via standard API
        repeat(4) {
            repository.logReel(TargetApp.YOUTUBE, force = true)
        }

        // Today count should only count today's events (4, not 14)
        assertEquals(4, repository.todayCount.value)

        // 7-day history must preserve both yesterday and today without data loss
        val last7 = repository.last7Days.value
        assertEquals(2, last7.size)

        val yesterdaySummary = last7.first { it.dateString == "2026-11-07" }
        assertEquals(10, yesterdaySummary.totalReels)
        assertFalse(yesterdaySummary.isToday)

        val todaySummary = last7.first { it.isToday }
        assertEquals(4, todaySummary.totalReels)
        assertTrue(todaySummary.isToday)
    }

    @Test
    fun testTimeEstimationConsistency() {
        // 0 reels -> 0.0m
        assertEquals(0.0, DateTimeUtils.estimateTimeMinutes(0), 0.001)
        assertEquals("0.0 min", DateTimeUtils.formatEstimatedTime(0.0))

        // 1 reel -> 1.5m
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(1), 0.001)
        assertEquals("1.5 min", DateTimeUtils.formatEstimatedTime(1.5))

        // 20 reels -> 30.0m
        assertEquals(30.0, DateTimeUtils.estimateTimeMinutes(20), 0.001)
        assertEquals("30.0 min", DateTimeUtils.formatEstimatedTime(30.0))

        // 40 reels -> 60.0m (1h)
        assertEquals(60.0, DateTimeUtils.estimateTimeMinutes(40), 0.001)
        assertEquals("1h", DateTimeUtils.formatEstimatedTime(60.0))

        // 54 reels -> 81.0m (1h 21m)
        assertEquals(81.0, DateTimeUtils.estimateTimeMinutes(54), 0.001)
        assertEquals("1h 21m", DateTimeUtils.formatEstimatedTime(81.0))
    }

    @Test
    fun testMonsterGrowthAfterCounting() {
        // Stage 1: Baby (0–5 reels)
        repeat(5) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        assertEquals(5, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Stage 2: Curious (6–15 reels)
        repository.logReel(TargetApp.INSTAGRAM, force = true) // 6th reel
        assertEquals(6, repository.todayCount.value)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)

        // Stage 3: Hungry (16–30 reels)
        repeat(10) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 16 reels
        assertEquals(16, repository.todayCount.value)
        assertEquals(MunchieStage.HUNGRY, repository.munchieStage.value)

        // Stage 4: Stuffed (31–50 reels)
        repeat(15) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 31 reels
        assertEquals(31, repository.todayCount.value)
        assertEquals(MunchieStage.STUFFED, repository.munchieStage.value)

        // Stage 5: Megachonk Overfed (51+ reels)
        repeat(20) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 51 reels
        assertEquals(51, repository.todayCount.value)
        assertEquals(MunchieStage.MEGACHONK, repository.munchieStage.value)

        // Verify quote engine reflects Megachonk
        val quote = MunchieMascotEngine.getMessage(repository.munchieStage.value, 51)
        assertTrue(quote.contains("50+") || quote.contains("mom") || quote.contains("grass") || quote.contains("overflow"))
    }
}
