package com.reelmonster.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.OverlayState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService
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
class OverlayServiceTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_overlay_tracking.db")
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
    fun testOverlayStateEnumProperties() {
        // 1. HIDDEN
        assertFalse(OverlayState.HIDDEN.isVisible)
        assertFalse(OverlayState.HIDDEN.isInteractive)

        // 2. COMPACT
        assertTrue(OverlayState.COMPACT.isVisible)
        assertTrue(OverlayState.COMPACT.isInteractive)

        // 3. EXPANDED
        assertTrue(OverlayState.EXPANDED.isVisible)
        assertTrue(OverlayState.EXPANDED.isInteractive)

        // 4. PAUSED
        assertTrue(OverlayState.PAUSED.isVisible)
        assertFalse(OverlayState.PAUSED.isInteractive)

        // 5. PERMISSION_MISSING
        assertFalse(OverlayState.PERMISSION_MISSING.isVisible)
        assertFalse(OverlayState.PERMISSION_MISSING.isInteractive)

        // 6. TRACKING_UNAVAILABLE
        assertFalse(OverlayState.TRACKING_UNAVAILABLE.isVisible)
        assertFalse(OverlayState.TRACKING_UNAVAILABLE.isInteractive)
    }

    @Test
    fun testOverlayPermissionHelperIntent() {
        val intent = OverlayPermissionHelper.createRequestOverlayPermissionIntent(context)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertNotNull(intent.data)
        assertEquals("package:${context.packageName}", intent.data.toString())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun testOverlayPreferencePersistence() = runTest {
        // Initial defaults
        assertTrue(preferencesManager.isOverlayEnabled.value)
        assertTrue(preferencesManager.isEdgeSnappingEnabled.value)

        // Toggle overlay disabled
        preferencesManager.updateOverlayEnabled(false)
        assertFalse(preferencesManager.isOverlayEnabledFlow.first())

        // Toggle overlay enabled
        preferencesManager.updateOverlayEnabled(true)
        assertTrue(preferencesManager.isOverlayEnabledFlow.first())

        // Toggle edge snapping
        preferencesManager.updateEdgeSnappingEnabled(false)
        assertFalse(preferencesManager.isEdgeSnappingEnabledFlow.first())

        preferencesManager.updateEdgeSnappingEnabled(true)
        assertTrue(preferencesManager.isEdgeSnappingEnabledFlow.first())

        // Test scale and opacity
        preferencesManager.updateBubbleScale(1.2f)
        assertEquals(1.2f, preferencesManager.bubbleScaleFlow.first(), 0.01f)

        preferencesManager.updateBubbleOpacity(0.85f)
        assertEquals(0.85f, preferencesManager.bubbleOpacityFlow.first(), 0.01f)
    }

    @Test
    fun testManualPlusOneIntegrationFromOverlay() {
        assertEquals(0, repository.todayCount.value)

        // Simulate TAP ME +1 on overlay for Instagram
        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue("Tap from overlay must log event successfully", logged)

        // Counter must increment by exactly 1
        assertEquals(1, repository.todayCount.value)

        // Platform breakdown updated
        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(0, yt)

        // Mascot stage updated
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Time estimation updated (1 reel = 1.5 min)
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(repository.todayCount.value), 0.001)

        // Funny quote generated
        val quote = MunchieMascotEngine.getMessage(repository.munchieStage.value, repository.todayCount.value)
        assertNotNull(quote)
        assertTrue(quote.isNotBlank())
    }

    @Test
    fun testYouTubePlusOneIntegrationFromOverlay() {
        assertEquals(0, repository.todayCount.value)

        // Simulate TAP ME +1 on overlay for YouTube Shorts
        val logged = repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)
        assertTrue("YouTube Shorts tap must be accepted", logged)

        assertEquals(1, repository.todayCount.value)

        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(0, ig)
        assertEquals(1, yt)

        val event = repository.recentEvents.value.first()
        assertEquals(TargetApp.YOUTUBE, event.targetApp)
        assertEquals("YouTube", event.targetApp.displayName)
        assertEquals("Shorts", event.targetApp.mediaType)
    }

    @Test
    fun testOverlayTapsDebounceIntegrity() {
        // First tap: accepted
        val tap1 = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue("First tap must be accepted", tap1)
        assertEquals(1, repository.todayCount.value)

        // Immediate second tap on same app (sub-millisecond without delay): debounced
        val tap2 = repository.logReel(TargetApp.INSTAGRAM)
        assertFalse("Rapid successive tap within 350ms on same target must be rejected", tap2)
        assertEquals(1, repository.todayCount.value)

        // Switching target app to YouTube is accepted immediately
        val tapSwitch = repository.logReel(TargetApp.YOUTUBE)
        assertTrue("Switching apps between taps is accepted immediately", tapSwitch)
        assertEquals(2, repository.todayCount.value)

        // Consecutive tap on YouTube after debounce interval is accepted
        Thread.sleep(ReelRepository.DEBOUNCE_THRESHOLD_MS + 20)
        val tapAfterDelay = repository.logReel(TargetApp.YOUTUBE)
        assertTrue("Tap after debounce delay is accepted", tapAfterDelay)
        assertEquals(3, repository.todayCount.value)
    }

    @Test
    fun testPauseAndResumeOverlayTracking() = runTest {
        // Ensure active state
        assertFalse(repository.isTrackingPaused.value)

        // Pause tracking
        preferencesManager.updateTrackingPaused(true)
        assertTrue(repository.isTrackingPaused.first { it })

        // Tap while paused is rejected
        val tapWhilePaused = repository.logReel(TargetApp.INSTAGRAM)
        assertFalse("Tapping +1 while overlay is paused must be blocked", tapWhilePaused)
        assertEquals(0, repository.todayCount.value)

        // Resume tracking
        preferencesManager.updateTrackingPaused(false)
        assertFalse(repository.isTrackingPaused.first { !it })

        // Tap after resume succeeds
        val tapAfterResume = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue("Tapping +1 after resume must succeed", tapAfterResume)
        assertEquals(1, repository.todayCount.value)
    }

    @Test
    fun testForegroundServiceActionConstants() {
        assertEquals("com.reelmonster.app.action.START_OVERLAY", FloatingMonsterOverlayService.ACTION_START)
        assertEquals("com.reelmonster.app.action.STOP_OVERLAY", FloatingMonsterOverlayService.ACTION_STOP)
        assertEquals("com.reelmonster.app.action.PAUSE_TRACKING", FloatingMonsterOverlayService.ACTION_PAUSE)
        assertEquals("com.reelmonster.app.action.RESUME_TRACKING", FloatingMonsterOverlayService.ACTION_RESUME)
        assertEquals("com.reelmonster.app.action.EXPAND_OVERLAY", FloatingMonsterOverlayService.ACTION_EXPAND)
        assertEquals("com.reelmonster.app.action.COLLAPSE_OVERLAY", FloatingMonsterOverlayService.ACTION_COLLAPSE)
        assertEquals("com.reelmonster.app.action.LOG_REEL", FloatingMonsterOverlayService.ACTION_LOG_REEL)
    }

    @Test
    fun testCounterSyncAcrossDashboardAndOverlay() {
        // Simulate 3 chomps from overlay and 2 chomps from dashboard
        repeat(3) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        repeat(2) { repository.logReel(TargetApp.YOUTUBE, force = true) }

        // Both components observe the single source of truth in ReelRepository
        assertEquals(5, repository.todayCount.value)
        assertEquals(Pair(3, 2), repository.platformBreakdown.value)
        assertEquals(5, repository.recentEvents.value.size)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Verify time lost calculation is identical
        assertEquals(7.5, DateTimeUtils.estimateTimeMinutes(repository.todayCount.value), 0.001)
        assertEquals("7.5 min", DateTimeUtils.formatEstimatedTime(7.5))
    }

    @Test
    fun testMunchieMascotEngineAllStagesInOverlay() {
        val stages = listOf(
            Pair(0, MunchieStage.BABY),
            Pair(5, MunchieStage.BABY),
            Pair(6, MunchieStage.CURIOUS),
            Pair(15, MunchieStage.CURIOUS),
            Pair(16, MunchieStage.HUNGRY),
            Pair(30, MunchieStage.HUNGRY),
            Pair(31, MunchieStage.STUFFED),
            Pair(50, MunchieStage.STUFFED),
            Pair(51, MunchieStage.MEGACHONK),
            Pair(100, MunchieStage.MEGACHONK)
        )

        for ((count, expectedStage) in stages) {
            val stage = MunchieStage.fromCount(count)
            assertEquals("Count $count must correspond to $expectedStage", expectedStage, stage)
            val quote = MunchieMascotEngine.getMessage(stage, count)
            assertNotNull(quote)
            assertTrue("Quote for $stage must not be blank", quote.isNotBlank())
        }
    }
}
