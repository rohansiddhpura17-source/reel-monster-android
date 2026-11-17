package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.DailyGoalRecord
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalState
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.service.tracking.ReelAccessibilityService
import com.reelmonster.app.service.tracking.ViewingSessionTracker
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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

/**
 * Phase12DailyGoalsTest
 *
 * Dedicated test suite for Phase 12: Daily Goals & Personal Intelligence
 * Covering:
 * - Group 1: Configuration, Defaults, and Local Persistence (Tests 1–3)
 * - Group 2: Pacing State Transitions & At-Limit Distinction (Tests 4–6)
 * - Group 3: Multi-Platform Scope Calculation (Tests 7–9)
 * - Group 4: Local-Day Boundary Finalization & Reset Safety (Tests 10–12)
 * - Group 5: Munchie Personality & Behavioral Pacing Feedback (Tests 13–15)
 * - Group 6: Full Phase 10 & 11 Preservations (Test 16)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase12DailyGoalsTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reel_monster.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() = runBlocking {
        repository.personalityEngine.reset()
    }

    // =========================================================================
    // Group 1: Configuration, Defaults, and Local Persistence
    // =========================================================================

    @Test
    fun test01_defaultGoalsInitializedCorrectly() {
        val goals = repository.activeGoals.value
        assertTrue("Goals should be populated with defaults", goals.isNotEmpty())

        val combinedCount = goals.firstOrNull { it.type == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
        assertNotNull("Default combined count goal must exist", combinedCount)
        assertEquals(30, combinedCount?.targetValue)
        assertTrue(combinedCount?.isEnabled == true)

        val combinedTime = goals.firstOrNull { it.type == GoalType.VIEWING_TIME_MINUTES && it.scope == GoalScope.COMBINED }
        assertNotNull("Default combined time goal must exist", combinedTime)
        assertEquals(30, combinedTime?.targetValue)
        assertTrue(combinedTime?.isEnabled == true)
    }

    @Test
    fun test02_customGoalConfigurationPersistsInSQLite() {
        // User changes combined count to 45 and time to 25 mins
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 45, true)
        repository.setDailyGoal(GoalType.VIEWING_TIME_MINUTES, GoalScope.COMBINED, 25, true)

        val updatedGoals = dbHelper.getAllGoals()
        val countGoal = updatedGoals.first { it.type == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
        val timeGoal = updatedGoals.first { it.type == GoalType.VIEWING_TIME_MINUTES && it.scope == GoalScope.COMBINED }

        assertEquals(45, countGoal.targetValue)
        assertEquals(25, timeGoal.targetValue)
        assertEquals(45, repository.combinedCountProgress.value?.goal?.targetValue)
        assertEquals(25, repository.combinedTimeProgress.value?.goal?.targetValue)
    }

    @Test
    fun test03_goalsSurviveDatabaseReopen() {
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 50, true)

        // Simulate app restart by instantiating new helper & repository
        val newDbHelper = ReelDbHelper(context)
        val newRepo = ReelRepository(newDbHelper, preferencesManager)

        val countGoal = newRepo.activeGoals.value.first { it.type == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
        assertEquals(50, countGoal.targetValue)
    }

    // =========================================================================
    // Group 2: Pacing State Transitions & At-Limit Distinction
    // =========================================================================

    @Test
    fun test04_goalPacingStatesEvaluatedAccurately() {
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 20, true)

        // 0 reels -> NOT_STARTED
        assertEquals(GoalState.NOT_STARTED, repository.combinedCountProgress.value?.state)
        assertEquals(20, repository.combinedCountProgress.value?.remainingValue)

        // 10 reels (50%) -> IN_PROGRESS
        for (i in 1..10) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        assertEquals(GoalState.IN_PROGRESS, repository.combinedCountProgress.value?.state)
        assertEquals(10, repository.combinedCountProgress.value?.remainingValue)
        assertEquals(50, repository.combinedCountProgress.value?.usagePercentage)

        // 15 reels (75%) -> APPROACHING_LIMIT
        for (i in 1..5) {
            repository.logReel(TargetApp.YOUTUBE, force = true)
        }
        assertEquals(GoalState.APPROACHING_LIMIT, repository.combinedCountProgress.value?.state)
        assertEquals(5, repository.combinedCountProgress.value?.remainingValue)
        assertEquals(75, repository.combinedCountProgress.value?.usagePercentage)

        // 20 reels (100%) -> AT_LIMIT
        for (i in 1..5) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        assertEquals(GoalState.AT_LIMIT, repository.combinedCountProgress.value?.state)
        assertEquals(0, repository.combinedCountProgress.value?.remainingValue)
        assertEquals(100, repository.combinedCountProgress.value?.usagePercentage)

        // 21 reels (105%) -> EXCEEDED
        repository.logReel(TargetApp.YOUTUBE, force = true)
        assertEquals(GoalState.EXCEEDED, repository.combinedCountProgress.value?.state)
        assertEquals(0, repository.combinedCountProgress.value?.remainingValue)
        assertTrue(repository.combinedCountProgress.value?.isLimitBreached == true)
    }

    @Test
    fun test05_atLimitDistinctFromCompletionCelebration() {
        // CORRECTION 2: Clearly distinguish reaching a maximum usage limit from
        // successfully completing a day within that limit.
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 10, true)

        // Log up to 10 reels
        for (i in 1..10) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }

        val prog = repository.combinedCountProgress.value
        assertNotNull(prog)
        assertEquals(GoalState.AT_LIMIT, prog?.state)

        // Reaching 100% of the limit must NOT award a CELEBRATING state!
        // It triggers an alert / boundary reminder, not a victory lap.
        assertFalse("Reaching limit must not celebrate excessive usage",
            repository.munchiePersonalityState.value == MunchiePersonalityState.CELEBRATING
        )
    }

    @Test
    fun test06_goalStateReflectsTrackingPausedOrGlobalOff() {
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 30, true)
        repository.logReel(TargetApp.INSTAGRAM, force = true)

        repository.pauseTracking()
        assertEquals(GoalState.PAUSED, repository.combinedCountProgress.value?.state)

        repository.resumeTracking()
        assertEquals(GoalState.IN_PROGRESS, repository.combinedCountProgress.value?.state)

        repository.setGlobalTrackingEnabled(false)
        assertEquals(GoalState.PAUSED, repository.combinedCountProgress.value?.state)
    }

    // =========================================================================
    // Group 3: Multi-Platform Scope Calculation
    // =========================================================================

    @Test
    fun test07_platformScopeIsolation() {
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.INSTAGRAM, 10, true)
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.YOUTUBE, 10, true)

        // Log 8 Instagram Reels
        for (i in 1..8) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        // Log 3 YouTube Shorts
        for (i in 1..3) {
            repository.logReel(TargetApp.YOUTUBE, force = true)
        }

        val igProg = repository.goalProgressList.value.first { it.goal.scope == GoalScope.INSTAGRAM && it.goal.type == GoalType.REEL_COUNT }
        val ytProg = repository.goalProgressList.value.first { it.goal.scope == GoalScope.YOUTUBE && it.goal.type == GoalType.REEL_COUNT }

        assertEquals(8, igProg.currentValue)
        assertEquals(GoalState.APPROACHING_LIMIT, igProg.state)

        assertEquals(3, ytProg.currentValue)
        assertEquals(GoalState.IN_PROGRESS, ytProg.state)
    }

    @Test
    fun test08_combinedTotalMatchesPlatformSum() {
        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 50, true)

        for (i in 1..12) repository.logReel(TargetApp.INSTAGRAM, force = true)
        for (i in 1..18) repository.logReel(TargetApp.YOUTUBE, force = true)

        val combined = repository.combinedCountProgress.value
        assertEquals(30, combined?.currentValue)
        assertEquals(12, repository.platformBreakdown.value.first)
        assertEquals(18, repository.platformBreakdown.value.second)
    }

    @Test
    fun test09_viewingTimeGoalCalculatedInMinutes() {
        repository.setDailyGoal(GoalType.VIEWING_TIME_MINUTES, GoalScope.COMBINED, 20, true)

        // Record viewing session of 15 minutes (900_000 ms)
        val session = ViewingSession(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = "test_fp_time",
            startTimestamp = 1_000L,
            endTimestamp = 901_000L,
            durationMs = 900_000L,
            completionStatus = "COMPLETED",
            sessionDate = DateTimeUtils.getTodayDateString()
        )
        repository.recordViewingSession(session)

        val timeProg = repository.combinedTimeProgress.value
        assertNotNull(timeProg)
        assertEquals(15, timeProg?.currentValue)
        assertEquals(5, timeProg?.remainingValue)
        assertEquals(GoalState.APPROACHING_LIMIT, timeProg?.state)
    }

    // =========================================================================
    // Group 4: Local-Day Boundary Finalization & Reset Safety
    // =========================================================================

    @Test
    fun test10_historicalGoalRecordFinalizationOnlyPastDays() {
        // CORRECTION 3: Finalize historical daily goal records at local-day boundary.
        // Incomplete days must not be marked as successful.
        val pastDate = "2026-11-05"
        val todayDate = "2026-11-08"

        // Log 15 reels on past date (under the 30-reel limit)
        for (i in 1..15) {
            repository.logReelAt(TargetApp.INSTAGRAM, timestamp = 1000L * i, dateStr = pastDate)
        }

        // Finalize using today's date boundary
        repository.finalizeHistoricalGoalRecords(todayDateKey = todayDate)

        val pastRecords = repository.getGoalRecordsForDate(pastDate)
        assertTrue("Past date should be finalized", pastRecords.isNotEmpty())
        val countRec = pastRecords.first { it.goalType == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
        assertEquals(15, countRec.actualValue)
        assertTrue("User stayed under limit -> wasSuccessful = true", countRec.wasSuccessful)

        // Verify today is NOT finalized yet
        val todayRecords = repository.getGoalRecordsForDate(todayDate)
        assertTrue("Today must remain active and unfinalized", todayRecords.isEmpty())
    }

    @Test
    fun test11_pastDayExceededMarkedUnsuccessful() {
        val pastDate = "2026-11-06"
        val todayDate = "2026-11-08"

        repository.setDailyGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, 20, true)

        // Log 25 reels on past date (exceeded limit)
        for (i in 1..25) {
            repository.logReelAt(TargetApp.YOUTUBE, timestamp = 1000L * i, dateStr = pastDate)
        }

        repository.finalizeHistoricalGoalRecords(todayDateKey = todayDate)

        val pastRecords = repository.getGoalRecordsForDate(pastDate)
        val countRec = pastRecords.first { it.goalType == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
        assertEquals(25, countRec.actualValue)
        assertFalse("Past date exceeded limit -> wasSuccessful = false", countRec.wasSuccessful)
    }

    @Test
    fun test12_dailyResetDoesNotEraseHistoricalGoalRecords() {
        // CORRECTION 3: Daily resets must never erase historical records.
        // Dates are computed from the real today: the test used to hardcode
        // 2026-11-08 as "today", so once the calendar moved past that date
        // logReel() logged under the real today while performDailyReset()
        // was given yesterday — the reset then (correctly) left today's
        // count at 5 and the test failed for the wrong reason.
        val todayDate = DateTimeUtils.getTodayDateString()
        val pastDate = LocalDate.parse(todayDate).minusDays(1).toString()

        repository.logReelAt(TargetApp.INSTAGRAM, timestamp = 5000L, dateStr = pastDate)
        repository.finalizeHistoricalGoalRecords(todayDateKey = todayDate)

        // Log today's reels
        for (i in 1..5) repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertEquals(5, repository.todayCount.value)

        // Perform daily reset for today
        repository.performDailyReset(todayDate)

        // Today's count is reset
        assertEquals(0, repository.todayCount.value)
        assertEquals(GoalState.NOT_STARTED, repository.combinedCountProgress.value?.state)

        // Past date's historical goal records are completely intact!
        val pastRecords = repository.getGoalRecordsForDate(pastDate)
        assertTrue("Historical goal records must survive daily resets", pastRecords.isNotEmpty())
    }

    // =========================================================================
    // Group 5: Munchie Personality & Behavioral Pacing Feedback
    // =========================================================================

    @Test
    fun test13_munchieReactsToPacingMilestoneAtHalfway() {
        repository.personalityEngine.reset()
        repository.personalityEngine.onGoalPacingMilestone(50)

        assertEquals(MunchiePersonalityState.HAPPY, repository.personalityEngine.currentState.value)
        assertTrue(repository.personalityEngine.currentMessage.value.contains("Halfway"))
    }

    @Test
    fun test14_munchieReactsToApproachingLimitGently() {
        repository.personalityEngine.reset()
        repository.personalityEngine.onGoalApproachingLimit(5, "reels")

        assertEquals(MunchiePersonalityState.CURIOUS, repository.personalityEngine.currentState.value)
        assertTrue(repository.personalityEngine.currentMessage.value.contains("boundary"))
    }

    @Test
    fun test15_munchieReactsToLimitReachedAndExceededCompassionately() {
        repository.personalityEngine.reset()
        repository.personalityEngine.onGoalLimitReached()
        assertEquals(MunchiePersonalityState.CONCERNED, repository.personalityEngine.currentState.value)
        assertTrue(repository.personalityEngine.currentMessage.value.contains("limit has been reached"))

        repository.personalityEngine.onGoalExceeded(5, "reels")
        assertEquals(MunchiePersonalityState.CONCERNED, repository.personalityEngine.currentState.value)
        assertTrue(repository.personalityEngine.currentMessage.value.contains("passed today's limit"))
        assertFalse("Must never insult the user", repository.personalityEngine.currentMessage.value.contains("stupid"))
    }

    // =========================================================================
    // Group 6: Full Phase 10 & 11 Regressions Intact
    // =========================================================================

    @Test
    fun test16_allPhase10And11RegressionsPass() {
        // Phase 10: Mascot tap guarantees zero count change
        val countBefore = repository.todayCount.value
        repository.onMascotTapped()
        assertEquals(countBefore, repository.todayCount.value)

        // Phase 11: Daily resets allowance decrements accurately
        val res = repository.performDailyReset()
        assertTrue(res.success)
        assertEquals(4, repository.remainingDailyResets.value)

        // Level 2 Automatic Detection dwell confirmation remains functional
        val now = 200_000L
        val candidate = DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            authorOrChannel = "test_creator",
            titleOrCaption = "Mindful Reel",
            fingerprint = "p12_test_candidate",
            timestamp = now
        )
        ReelAccessibilityService.dedupEngine.reset(true)
        val r1 = ReelAccessibilityService.dedupEngine.processCandidate(candidate, now)
        assertEquals(com.reelmonster.app.service.tracking.DeduplicationEngine.Decision.PENDING_DWELL, r1)

        val r2 = ReelAccessibilityService.dedupEngine.checkDwell(now + 650L)
        assertEquals(com.reelmonster.app.service.tracking.DeduplicationEngine.Decision.CONFIRMED, r2)
    }
}
