package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.MunchiePersonalityEngine
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.service.tracking.DeduplicationEngine
import com.reelmonster.app.service.tracking.ReelAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase10PersonalityEngineTest
 *
 * Full test suite for Phase 10: Munchie Personality 2.0
 * Verifies all 16 required test scenarios:
 * 1. All ten personality states.
 * 2. Event-to-state mapping.
 * 3. Personality event priority.
 * 4. Growth-stage transitions.
 * 5. Dialogue selection.
 * 6. Consecutive message repetition prevention.
 * 7. Dashboard and overlay state synchronization.
 * 8. First Reel reaction.
 * 9. Long-session reaction.
 * 10. Pause/resume reactions.
 * 11. Mascot tap does not increment Reel count.
 * 12. Mascot long-press does not increment Reel count.
 * 13. Existing Phase 11 viewing-time functionality remains intact.
 * 14. Existing daily reset allowance remains intact.
 * 15. Global tracking ON/OFF remains intact.
 * 16. Existing automatic detection tests remain intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase10PersonalityEngineTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    private var fakeTime = 1_000_000L
    private lateinit var engine: MunchiePersonalityEngine

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reel_monster.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)

        fakeTime = 1_000_000L
        engine = MunchiePersonalityEngine(clock = { fakeTime })
        repository.personalityEngine.clock = { fakeTime }
        repository.personalityEngine.reset()
    }

    @After
    fun tearDown() = runBlocking {
        engine.reset()
        repository.personalityEngine.reset()
        preferencesManager.clearPreferences()
        dbHelper.close()
    }

    // =========================================================================
    // Test 1: All Ten Personality States
    // =========================================================================
    @Test
    fun test01_allTenPersonalityStatesExistAndHaveUniquePriorities() {
        val states = MunchiePersonalityState.entries
        assertEquals(10, states.size)

        val expectedStates = listOf(
            MunchiePersonalityState.IDLE,
            MunchiePersonalityState.HAPPY,
            MunchiePersonalityState.EXCITED,
            MunchiePersonalityState.SLEEPY,
            MunchiePersonalityState.CURIOUS,
            MunchiePersonalityState.SURPRISED,
            MunchiePersonalityState.CONCERNED,
            MunchiePersonalityState.CELEBRATING,
            MunchiePersonalityState.FOCUSING,
            MunchiePersonalityState.RESTING
        )
        assertTrue(states.containsAll(expectedStates))

        // Ensure priority levels are structured correctly
        assertTrue(MunchiePersonalityState.CELEBRATING.priority > MunchiePersonalityState.EXCITED.priority)
        assertTrue(MunchiePersonalityState.EXCITED.priority > MunchiePersonalityState.CONCERNED.priority)
        assertTrue(MunchiePersonalityState.CONCERNED.priority > MunchiePersonalityState.RESTING.priority)
        assertTrue(MunchiePersonalityState.RESTING.priority > MunchiePersonalityState.FOCUSING.priority)
        assertTrue(MunchiePersonalityState.FOCUSING.priority > MunchiePersonalityState.SURPRISED.priority)
        assertTrue(MunchiePersonalityState.SURPRISED.priority > MunchiePersonalityState.CURIOUS.priority)
        assertTrue(MunchiePersonalityState.CURIOUS.priority > MunchiePersonalityState.HAPPY.priority)
        assertTrue(MunchiePersonalityState.HAPPY.priority > MunchiePersonalityState.SLEEPY.priority)
        assertTrue(MunchiePersonalityState.SLEEPY.priority > MunchiePersonalityState.IDLE.priority)
    }

    // =========================================================================
    // Test 2: Event-to-State Mapping
    // =========================================================================
    @Test
    fun test02_eventToStateMapping() {
        // Tap -> CURIOUS or SURPRISED
        val tapState = engine.onMascotTapped()
        assertTrue(tapState == MunchiePersonalityState.CURIOUS || tapState == MunchiePersonalityState.SURPRISED)

        engine.reset()

        // Long press -> CELEBRATING
        val longPressState = engine.onMascotLongPressed()
        assertEquals(MunchiePersonalityState.CELEBRATING, longPressState)

        engine.reset()

        // Pause -> RESTING
        engine.onTrackingPaused()
        assertEquals(MunchiePersonalityState.RESTING, engine.currentState.value)

        // Resume -> HAPPY
        engine.reset()
        engine.onTrackingResumed()
        assertEquals(MunchiePersonalityState.HAPPY, engine.currentState.value)

        // Long session -> CONCERNED
        engine.reset()
        engine.onContinuousSessionThresholdReached(650_000L)
        assertEquals(MunchiePersonalityState.CONCERNED, engine.currentState.value)
    }

    // =========================================================================
    // Test 3: Personality Event Priority
    // =========================================================================
    @Test
    fun test03_personalityEventPriority() {
        // Activate CELEBRATING (Priority 10) as temporary state
        engine.transitionTo(MunchiePersonalityState.CELEBRATING, isTemporary = true, durationMs = 5_000L)
        assertEquals(MunchiePersonalityState.CELEBRATING, engine.currentState.value)

        // A lower priority state (e.g. HAPPY, Priority 3) should be REJECTED while temporary state is active
        val rejected = engine.transitionTo(MunchiePersonalityState.HAPPY, isTemporary = true)
        assertFalse("Lower priority state should be rejected while CELEBRATING is active", rejected)
        assertEquals(MunchiePersonalityState.CELEBRATING, engine.currentState.value)

        // After time advances past expiration, expiration check safely reverts
        fakeTime += 6_000L
        engine.checkExpiration(ambientState = MunchiePersonalityState.IDLE)
        assertEquals(MunchiePersonalityState.IDLE, engine.currentState.value)

        // Now HAPPY can be applied
        val accepted = engine.transitionTo(MunchiePersonalityState.HAPPY, isTemporary = true)
        assertTrue("HAPPY should now be accepted after celebration expired", accepted)
        assertEquals(MunchiePersonalityState.HAPPY, engine.currentState.value)
    }

    // =========================================================================
    // Test 4: Growth-Stage Transitions
    // =========================================================================
    @Test
    fun test04_growthStageTransitions() {
        // Stage 1: Baby (0-5 reels)
        for (i in 1..5) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        assertEquals(5, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Reel 6 crosses to Stage 2: Curious (6-15) -> Evolution should trigger EXCITED
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertEquals(6, repository.todayCount.value)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)
        assertEquals(MunchiePersonalityState.EXCITED, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 5: Dialogue Selection
    // =========================================================================
    @Test
    fun test05_dialogueSelection() {
        for (state in MunchiePersonalityState.entries) {
            val messages = MunchiePersonalityEngine.STATE_MESSAGES[state]
            assertNotNull("State $state must have dialogue messages defined", messages)
            assertTrue("State $state must have at least 3 messages", messages!!.size >= 3)
            val selected = engine.selectNextMessage(state)
            assertTrue("Selected message must be part of message bank", messages.contains(selected))
        }
    }

    // =========================================================================
    // Test 6: Consecutive Message Repetition Prevention
    // =========================================================================
    @Test
    fun test06_consecutiveMessageRepetitionPrevention() {
        val state = MunchiePersonalityState.HAPPY
        val msg1 = engine.selectNextMessage(state)
        val msg2 = engine.selectNextMessage(state)
        val msg3 = engine.selectNextMessage(state)

        assertNotEquals("Consecutive messages must never be identical", msg1, msg2)
        assertNotEquals("Consecutive messages must never be identical", msg2, msg3)
    }

    // =========================================================================
    // Test 7: Dashboard and Overlay State Synchronization
    // =========================================================================
    @Test
    fun test07_dashboardAndOverlayStateSynchronization() {
        // Repository provides the single authoritative source of truth
        repository.onMascotTapped()
        val state = repository.munchiePersonalityState.value
        val message = repository.munchiePersonalityMessage.value

        // Both dashboard and overlay read from repository StateFlows
        assertEquals(repository.personalityEngine.currentState.value, state)
        assertEquals(repository.personalityEngine.currentMessage.value, message)

        repository.onMascotLongPressed()
        assertEquals(MunchiePersonalityState.CELEBRATING, repository.munchiePersonalityState.value)
        assertEquals(repository.personalityEngine.currentMessage.value, repository.munchiePersonalityMessage.value)
    }

    // =========================================================================
    // Test 8: First Reel Reaction
    // =========================================================================
    @Test
    fun test08_firstReelReaction() {
        assertEquals(0, repository.todayCount.value)
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertEquals(1, repository.todayCount.value)
        assertEquals(MunchiePersonalityState.HAPPY, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 9: Long-Session Reaction
    // =========================================================================
    @Test
    fun test09_longSessionReaction() {
        val session = ViewingSession(
            targetApp = TargetApp.INSTAGRAM,
            startTimestamp = 1_000L,
            endTimestamp = 650_000L,
            durationMs = 649_000L, // > 10 minutes
            completionStatus = "COMPLETED",
            sessionDate = DateTimeUtils.getTodayDateString()
        )
        repository.recordViewingSession(session)
        assertEquals(MunchiePersonalityState.CONCERNED, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 10: Pause/Resume Reactions
    // =========================================================================
    @Test
    fun test10_pauseAndResumeReactions() {
        repository.pauseTracking()
        assertEquals(MunchiePersonalityState.RESTING, repository.munchiePersonalityState.value)

        repository.resumeTracking()
        assertEquals(MunchiePersonalityState.HAPPY, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 11: Mascot Tap Does Not Increment Reel Count
    // =========================================================================
    @Test
    fun test11_mascotTapDoesNotIncrementReelCount() {
        assertEquals(0, repository.todayCount.value)
        assertEquals(0L, repository.todayViewingTimeMs.value)

        repository.onMascotTapped()

        assertEquals("Mascot tap must NEVER increment today's count", 0, repository.todayCount.value)
        assertEquals("Mascot tap must NEVER alter viewing time", 0L, repository.todayViewingTimeMs.value)
        assertTrue(
            repository.munchiePersonalityState.value == MunchiePersonalityState.CURIOUS ||
                repository.munchiePersonalityState.value == MunchiePersonalityState.SURPRISED
        )
    }

    // =========================================================================
    // Test 12: Mascot Long-Press Does Not Increment Reel Count
    // =========================================================================
    @Test
    fun test12_mascotLongPressDoesNotIncrementReelCount() {
        assertEquals(0, repository.todayCount.value)
        assertEquals(0L, repository.todayViewingTimeMs.value)

        repository.onMascotLongPressed()

        assertEquals("Mascot long-press must NEVER increment today's count", 0, repository.todayCount.value)
        assertEquals("Mascot long-press must NEVER alter viewing time", 0L, repository.todayViewingTimeMs.value)
        assertEquals(MunchiePersonalityState.CELEBRATING, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 13: Existing Phase 11 Viewing-Time Functionality Remains Intact
    // =========================================================================
    @Test
    fun test13_existingPhase11ViewingTimeRemainsIntact() {
        val today = DateTimeUtils.getTodayDateString()
        val session = ViewingSession(
            targetApp = TargetApp.YOUTUBE,
            startTimestamp = 10_000L,
            endTimestamp = 35_000L,
            durationMs = 25_000L,
            completionStatus = "COMPLETED",
            sessionDate = today
        )
        repository.recordViewingSession(session)

        assertEquals(25_000L, repository.todayViewingTimeMs.value)
        assertEquals(25_000L, repository.todayPlatformViewingTimeMs.value.second)
    }

    // =========================================================================
    // Test 14: Existing Daily Reset Allowance Remains Intact
    // =========================================================================
    @Test
    fun test14_existingDailyResetAllowanceRemainsIntact() {
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertEquals(1, repository.todayCount.value)
        assertEquals(5, repository.remainingDailyResets.value)

        val result = repository.performDailyReset()
        assertTrue(result.success)
        assertEquals(4, result.remainingResets)
        assertEquals(0, repository.todayCount.value)
        assertEquals(4, repository.remainingDailyResets.value)
        assertEquals(MunchiePersonalityState.IDLE, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Test 15: Global Tracking ON/OFF Remains Intact
    // =========================================================================
    @Test
    fun test15_globalTrackingOnOffRemainsIntact() {
        preferencesManager.setGlobalTrackingEnabled(false)

        assertFalse("logReel should reject when global tracking is OFF", repository.logReel(TargetApp.INSTAGRAM))
        assertEquals(0, repository.todayCount.value)

        preferencesManager.setGlobalTrackingEnabled(true)
        assertTrue("logReel should succeed when global tracking is ON", repository.logReel(TargetApp.INSTAGRAM, force = true))
        assertEquals(1, repository.todayCount.value)
    }

    // =========================================================================
    // Test 16: Existing Automatic Detection Remains Intact
    // =========================================================================
    @Test
    fun test16_existingAutomaticDetectionRemainsIntact() {
        ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)
        val candidate = DetectionCandidate(
            targetApp = TargetApp.YOUTUBE,
            authorOrChannel = "@test_author",
            titleOrCaption = "Short Video Title",
            fingerprint = "fp_auto_test_10",
            timestamp = fakeTime
        )
        val decision1 = ReelAccessibilityService.dedupEngine.processCandidate(candidate, fakeTime)
        assertEquals(DeduplicationEngine.Decision.PENDING_DWELL, decision1)

        val decision2 = ReelAccessibilityService.dedupEngine.checkDwell(fakeTime + 650L)
        assertEquals(DeduplicationEngine.Decision.CONFIRMED, decision2)
    }
}
