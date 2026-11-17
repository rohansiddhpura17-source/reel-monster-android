package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.focus.FocusPeriodStats
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusStatus
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.service.focus.FocusSessionTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

/**
 * Phase13FocusModeTest
 *
 * Comprehensive test suite for Phase 13: Focus Mode & Gentle Interventions
 * Covering:
 * - Group 1: Session Controls & Countdown Mechanics (Tests 1–4)
 * - Group 2: Cancellation & Safe Session Persistence (Tests 5–7)
 * - Group 3: Strict Time Separation & Zero Bleed (Tests 8–10)
 * - Group 4: SQLite Database Migrations & Aggregations (Tests 11–13)
 * - Group 5: Gentle Interventions & Cooldown Throttling (Tests 14–16)
 * - Group 6: Munchie Personality & Reaction Integration (Tests 17–19)
 * - Group 7: Comprehensive Regression Preservation (Test 20)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase13FocusModeTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    // Controllable monotonic and wall clocks for deterministic testing
    private var simulatedElapsedRealtime: Long = 100_000L
    private var simulatedWallTime: Long = 1_700_000_000_000L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reel_monster.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)

        simulatedElapsedRealtime = 100_000L
        simulatedWallTime = 1_700_000_000_000L

        FocusSessionTracker.instance.reset()
        FocusSessionTracker.instance.clockElapsedRealtime = { simulatedElapsedRealtime }
        FocusSessionTracker.instance.clockWallTime = { simulatedWallTime }
    }

    @After
    fun tearDown() = runBlocking {
        FocusSessionTracker.instance.reset()
        preferencesManager.clearPreferences()
        repository.personalityEngine.reset()
    }

    // =========================================================================
    // Group 1: Session Controls & Countdown Mechanics
    // =========================================================================

    @Test
    fun test01_defaultIdleState() {
        val tracker = FocusSessionTracker.instance
        val progress = tracker.progress.value
        assertEquals("Initial status must be IDLE", FocusStatus.IDLE, progress.status)
        assertEquals("Initial elapsed must be 0", 0L, progress.elapsedMs)
        assertFalse("Progress must not be active", progress.status.isActive)
    }

    @Test
    fun test02_startQuickBreakAndDeepFocus() {
        val tracker = FocusSessionTracker.instance

        // Start 5 min quick break
        tracker.start(FocusType.QUICK_BREAK, 5, repository)
        var progress = tracker.progress.value
        assertEquals("Status must be RUNNING", FocusStatus.RUNNING, progress.status)
        assertEquals("Type must be QUICK_BREAK", FocusType.QUICK_BREAK, progress.type)
        assertEquals(300_000L, progress.targetDurationMs)
        assertTrue(progress.status.isActive)

        // Advance monotonic clock by 60 seconds
        simulatedElapsedRealtime += 60_000L
        simulatedWallTime += 60_000L
        tracker.tick()
        progress = tracker.progress.value

        assertEquals(60_000L, progress.elapsedMs)
        assertEquals(240_000L, progress.remainingMs)
        assertEquals("04:00", progress.formattedRemaining)
        assertEquals("01:00", progress.formattedElapsed)
        assertEquals(20, progress.progressPercentage)
    }

    @Test
    fun test03_pauseAndResumePreservesMonotonicDuration() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        // Run for 5 minutes
        simulatedElapsedRealtime += 300_000L
        simulatedWallTime += 300_000L
        tracker.tick()

        // Pause session
        tracker.pause(repository)
        var progress = tracker.progress.value
        assertEquals("Status must be PAUSED", FocusStatus.PAUSED, progress.status)
        assertEquals(300_000L, progress.elapsedMs)

        // While paused, wall clock and monotonic clock advance by 10 minutes
        simulatedElapsedRealtime += 600_000L
        simulatedWallTime += 600_000L
        tracker.tick()

        // Elapsed MUST NOT increase while paused
        progress = tracker.progress.value
        assertEquals("Elapsed must remain frozen while paused", 300_000L, progress.elapsedMs)

        // Resume session
        tracker.resume(repository)
        progress = tracker.progress.value
        assertEquals("Status must be RUNNING", FocusStatus.RUNNING, progress.status)

        // Run for 2 more minutes
        simulatedElapsedRealtime += 120_000L
        simulatedWallTime += 120_000L
        tracker.tick()

        progress = tracker.progress.value
        assertEquals("Elapsed should be 5m + 2m = 7m", 420_000L, progress.elapsedMs)
        assertEquals("18:00", progress.formattedRemaining)
    }

    @Test
    fun test04_completionPersistsToSQLiteAndResets() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.QUICK_BREAK, 5, repository)

        // Advance by full 5 minutes
        simulatedElapsedRealtime += 300_000L
        simulatedWallTime += 300_000L
        tracker.tick()

        // Complete session
        tracker.complete(repository)

        // Progress should now be COMPLETED
        val progress = tracker.progress.value
        assertEquals(FocusStatus.COMPLETED, progress.status)
        assertEquals(300_000L, progress.elapsedMs)

        // Verify SQLite persistence
        val recentSessions = repository.recentFocusSessions.value
        assertEquals("One session should be recorded", 1, recentSessions.size)
        val session = recentSessions[0]
        assertEquals(FocusType.QUICK_BREAK, session.sessionType)
        assertEquals(FocusSession.STATUS_COMPLETED, session.completionStatus)
        assertEquals(300_000L, session.actualDurationMs)
        assertEquals(300_000L, session.targetDurationMs)

        // Verify aggregates
        assertEquals(300_000L, repository.todayFocusTimeMs.value)
        assertEquals(1, repository.todayFocusCompletedCount.value)
    }

    // =========================================================================
    // Group 2: Cancellation & Safe Session Persistence
    // =========================================================================

    @Test
    fun test05_cancelShortSessionDoesNotPersist() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        // Run for only 5 seconds (< 10s threshold)
        simulatedElapsedRealtime += 5_000L
        simulatedWallTime += 5_000L
        tracker.tick()

        tracker.cancel(repository)
        val progress = tracker.progress.value
        assertEquals(FocusStatus.CANCELLED, progress.status)

        // No record should be stored for trivial cancellations
        val sessions = repository.recentFocusSessions.value
        assertTrue("Short cancellations under 10s must not pollute database", sessions.isEmpty())
        assertEquals(0L, repository.todayFocusTimeMs.value)
    }

    @Test
    fun test06_cancelSubstantialSessionPersistsCancelledStatus() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        // Run for 3 minutes (180s >= 10s)
        simulatedElapsedRealtime += 180_000L
        simulatedWallTime += 180_000L
        tracker.tick()

        tracker.cancel(repository)
        val progress = tracker.progress.value
        assertEquals(FocusStatus.CANCELLED, progress.status)

        val sessions = repository.recentFocusSessions.value
        assertEquals("Substantial cancellation must be recorded", 1, sessions.size)
        assertEquals(FocusSession.STATUS_CANCELLED, sessions[0].completionStatus)
        assertEquals(180_000L, sessions[0].actualDurationMs)
        // Completed count should remain 0
        assertEquals(0, repository.todayFocusCompletedCount.value)
    }

    @Test
    fun test07_sessionStatePersistenceAcrossRestart() = runBlocking {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.CUSTOM, 15, repository)

        simulatedElapsedRealtime += 120_000L
        simulatedWallTime += 120_000L
        tracker.tick()

        preferencesManager.saveActiveFocusSessionState(
            type = FocusType.CUSTOM.name,
            targetMs = 900_000L,
            elapsedMs = 120_000L,
            status = FocusStatus.RUNNING.name,
            startWallTime = simulatedWallTime - 120_000L
        )

        // Phase 17: wait for the DataStore-backed flows to propagate the save.
        // saveActiveFocusSessionState returns when the edit commits, but the
        // stateIn(Eagerly) saved* flows update asynchronously, and
        // restoreActiveSessionState reads their .value synchronously — without
        // this wait the test is racy (green at 337/337, then failed at 340).
        withTimeout(5_000) {
            preferencesManager.savedFocusType.first { it == FocusType.CUSTOM.name }
        }

        // Reset memory tracker (simulating process termination)
        tracker.reset()
        assertEquals(FocusStatus.IDLE, tracker.progress.value.status)

        // Restore simulated clocks for post-restart verification
        tracker.clockElapsedRealtime = { simulatedElapsedRealtime }
        tracker.clockWallTime = { simulatedWallTime }

        // Call restoreActiveSessionState with wall clock
        val restored = tracker.restoreActiveSessionState(repository)
        assertTrue("Session should be safely restored", restored)
        assertEquals(FocusStatus.RUNNING, tracker.progress.value.status)
        assertEquals(FocusType.CUSTOM, tracker.progress.value.type)
    }

    // =========================================================================
    // Group 3: Strict Time Separation & Zero Bleed
    // =========================================================================

    @Test
    fun test08_focusTimeDoesNotBleedIntoReelViewingTime() {
        val initialReelTime = repository.todayViewingTimeMs.value
        val initialReelCount = repository.todayCount.value
        assertEquals(0L, initialReelTime)
        assertEquals(0, initialReelCount)

        // Run a 25 min focus session
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)
        simulatedElapsedRealtime += 1_500_000L
        simulatedWallTime += 1_500_000L
        tracker.tick()
        tracker.complete(repository)

        // Verify focus time recorded
        assertEquals(1_500_000L, repository.todayFocusTimeMs.value)
        assertEquals(1, repository.todayFocusCompletedCount.value)

        // Verify ZERO bleed into reel metrics
        assertEquals("Reel viewing time must remain untouched (0ms)", 0L, repository.todayViewingTimeMs.value)
        assertEquals("Reel count must remain untouched (0)", 0, repository.todayCount.value)
    }

    @Test
    fun test09_reelViewingDuringActiveFocusMaintainsSeparation() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.QUICK_BREAK, 5, repository)

        // Log a reel and record viewing session
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        val reelSession = ViewingSession(
            targetApp = TargetApp.INSTAGRAM,
            startTimestamp = simulatedWallTime,
            endTimestamp = simulatedWallTime + 45_000L,
            durationMs = 45_000L,
            sessionDate = DateTimeUtils.getTodayDateString()
        )
        repository.recordViewingSession(reelSession)

        // Advance focus session by 5 minutes
        simulatedElapsedRealtime += 300_000L
        simulatedWallTime += 300_000L
        tracker.tick()
        tracker.complete(repository)

        // Focus metrics
        assertEquals(300_000L, repository.todayFocusTimeMs.value)
        assertEquals(1, repository.todayFocusCompletedCount.value)

        // Reel metrics
        assertEquals(1, repository.todayCount.value)
        assertEquals(45_000L, repository.todayViewingTimeMs.value)
    }

    @Test
    fun test10_dailyResetDoesNotEraseFocusSessions() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)
        simulatedElapsedRealtime += 1_500_000L
        simulatedWallTime += 1_500_000L
        tracker.tick()
        tracker.complete(repository)

        // Log a reel
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertEquals(1, repository.todayCount.value)
        assertEquals(1, repository.todayFocusCompletedCount.value)

        // Perform daily reset of tracking
        repository.performDailyReset()

        // Reel count reset to 0
        assertEquals(0, repository.todayCount.value)

        // Focus session history must PRESERVE completed records
        val sessions = repository.recentFocusSessions.value
        assertEquals("Focus sessions must survive daily tracking reset", 1, sessions.size)
        assertEquals(1, repository.todayFocusCompletedCount.value)
        assertEquals(1_500_000L, repository.todayFocusTimeMs.value)
    }

    // =========================================================================
    // Group 4: SQLite Database Migrations & Aggregations
    // =========================================================================

    @Test
    fun test11_databaseFocusSessionCrud() {
        val session1 = FocusSession(
            sessionType = FocusType.DEEP_FOCUS,
            targetDurationMs = 25 * 60_000L,
            actualDurationMs = 25 * 60_000L,
            startTimestamp = 1000L,
            endTimestamp = 1000L + 25 * 60_000L,
            completionStatus = FocusSession.STATUS_COMPLETED,
            dateString = DateTimeUtils.getTodayDateString()
        )
        val id1 = dbHelper.insertFocusSession(session1)
        assertTrue("Insert must return valid row id", id1 > 0)

        val sessions = dbHelper.getRecentFocusSessions(10)
        assertEquals(1, sessions.size)
        assertEquals(FocusType.DEEP_FOCUS, sessions[0].sessionType)
        assertEquals(FocusSession.STATUS_COMPLETED, sessions[0].completionStatus)
    }

    @Test
    fun test12_dailyFocusAggregations() {
        val today = DateTimeUtils.getTodayDateString()
        dbHelper.insertFocusSession(
            FocusSession(
                sessionType = FocusType.QUICK_BREAK,
                targetDurationMs = 300_000L,
                actualDurationMs = 300_000L,
                startTimestamp = 1000L,
                endTimestamp = 301_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = today
            )
        )
        dbHelper.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 1_500_000L,
                actualDurationMs = 1_500_000L,
                startTimestamp = 400_000L,
                endTimestamp = 1_900_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = today
            )
        )
        // Add a cancelled session
        dbHelper.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 1_500_000L,
                actualDurationMs = 200_000L,
                startTimestamp = 2_000_000L,
                endTimestamp = 2_200_000L,
                completionStatus = FocusSession.STATUS_CANCELLED,
                dateString = today
            )
        )

        val totalTime = dbHelper.getTodayFocusTimeMs(today)
        val completedCount = dbHelper.getTodayCompletedFocusCount(today)

        // Total time includes all completed & cancelled duration
        assertEquals(2_000_000L, totalTime)
        // Completed count only counts STATUS_COMPLETED
        assertEquals(2, completedCount)
    }

    @Test
    fun test13_weeklyFocusAggregations() {
        val today = DateTimeUtils.getTodayDateString()
        dbHelper.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 1_500_000L,
                actualDurationMs = 1_500_000L,
                startTimestamp = 1000L,
                endTimestamp = 1_501_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = today
            )
        )

        val interval = DateTimeUtils.calculateInterval(com.reelmonster.app.domain.model.HistoryDateRange.LAST_7_DAYS)
        val weeklyStats = dbHelper.getWeeklyFocusStats(interval.startDateStr, interval.endDateStr)

        assertEquals(1_500_000L, weeklyStats.totalFocusTimeMs)
        assertEquals(1, weeklyStats.completedSessionsCount)
    }

    // =========================================================================
    // Group 5: Gentle Interventions & Cooldown Throttling
    // =========================================================================

    @Test
    fun test14_gentleCheckinTriggersOnEnteringInstagramOrYouTube() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        // User enters Instagram
        val reminder = tracker.checkIntervention("com.instagram.android", repository = repository)
        assertNotNull("Intervention reminder should be triggered", reminder)
        assertEquals("Instagram", reminder?.appName)
        assertEquals(25 * 60_000L, reminder?.targetDurationMs)

        // Verify reminder state in repository
        val activeReminder = repository.focusInterventionReminder.value
        assertNotNull(activeReminder)
        assertEquals("Instagram", activeReminder?.appName)
    }

    @Test
    fun test15_cooldownThrottlingPreventsSpam() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        // First trigger
        val reminder1 = tracker.checkIntervention("com.instagram.android", repository = repository)
        assertNotNull(reminder1)

        // 30 seconds later, user returns to YouTube (default cooldown is 3 minutes)
        simulatedElapsedRealtime += 30_000L
        simulatedWallTime += 30_000L

        val reminder2 = tracker.checkIntervention("com.google.android.youtube", repository = repository)
        assertNull("Cooldown must throttle subsequent intervention reminders within 3 minutes", reminder2)

        // 3 minutes and 1 second later
        simulatedElapsedRealtime += 181_000L
        simulatedWallTime += 181_000L

        val reminder3 = tracker.checkIntervention("com.google.android.youtube", repository = repository)
        assertNotNull("After cooldown passes, next intervention may gently trigger", reminder3)
        assertEquals("YouTube", reminder3?.appName)
    }

    @Test
    fun test16_dismissGentleInterventionLeavesAppUnblocked() {
        val tracker = FocusSessionTracker.instance
        tracker.start(FocusType.DEEP_FOCUS, 25, repository)

        tracker.checkIntervention("com.instagram.android", repository = repository)
        assertNotNull(repository.focusInterventionReminder.value)

        // User dismisses reminder
        repository.dismissFocusIntervention()
        assertNull("Reminder should be dismissed", repository.focusInterventionReminder.value)

        // Focus session continues running uninterrupted
        assertEquals(FocusStatus.RUNNING, tracker.progress.value.status)
    }

    // =========================================================================
    // Group 6: Munchie Personality & Reaction Integration
    // =========================================================================

    @Test
    fun test17_startFocusTransitionsMunchieToFocusing() {
        repository.startFocusSession(FocusType.DEEP_FOCUS, 25)
        assertEquals("Munchie must enter FOCUSING personality state",
            MunchiePersonalityState.FOCUSING, repository.munchiePersonalityState.value)
        val msg = repository.munchiePersonalityMessage.value
        assertTrue(msg.contains("Focus", ignoreCase = true) || msg.contains("Munchie", ignoreCase = true))
    }

    @Test
    fun test18_pauseAndResumeMunchieTransitions() {
        repository.startFocusSession(FocusType.QUICK_BREAK, 5)
        assertEquals(MunchiePersonalityState.FOCUSING, repository.munchiePersonalityState.value)

        repository.pauseFocusSession()
        assertEquals("Munchie must enter RESTING state while focus is paused",
            MunchiePersonalityState.RESTING, repository.munchiePersonalityState.value)

        repository.resumeFocusSession()
        assertEquals("Munchie must return to FOCUSING state when resumed",
            MunchiePersonalityState.FOCUSING, repository.munchiePersonalityState.value)
    }

    @Test
    fun test19_completeFocusTransitionsMunchieToCelebrating() {
        repository.startFocusSession(FocusType.QUICK_BREAK, 5)
        simulatedElapsedRealtime += 300_000L
        simulatedWallTime += 300_000L
        FocusSessionTracker.instance.tick()

        repository.completeFocusSession()
        assertEquals("Munchie must celebrate focus completion",
            MunchiePersonalityState.CELEBRATING, repository.munchiePersonalityState.value)
    }

    // =========================================================================
    // Group 7: Comprehensive Regression Preservation
    // =========================================================================

    @Test
    fun test20_preservesPhases10_11_and_12() {
        // Phase 10: 10 personality states engine accessible
        assertEquals(10, MunchiePersonalityState.entries.size)

        // Phase 11: 5 Daily Reset allowances
        assertEquals(UserPreferencesManager.MAX_DAILY_RESETS, repository.remainingDailyResets.value)
        assertEquals(5, repository.remainingDailyResets.value)

        // Phase 11: Global tracking switch
        assertTrue(repository.isGlobalTrackingEnabled.value)
        repository.setGlobalTrackingEnabled(false)
        assertFalse(repository.isGlobalTrackingEnabled.value)
        repository.setGlobalTrackingEnabled(true)

        // Phase 12: Daily Goals engine active
        val goals = repository.activeGoals.value
        assertTrue("Phase 12 daily goals must remain active", goals.isNotEmpty())
    }
}
