package com.reelmonster.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.DailyGoalHistoryEvent
import com.reelmonster.app.domain.model.goal.GoalHistoryEventType
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.domain.model.monitoring.ContinuityStatus
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.EstimatedCoverageStatus
import com.reelmonster.app.domain.model.monitoring.MonitoringHeartbeat
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import com.reelmonster.app.domain.model.monitoring.SupportedAppDetectionEvent
import com.reelmonster.app.domain.model.progression.LevelCalculator
import com.reelmonster.app.domain.model.progression.StreakEvaluationStatus
import com.reelmonster.app.domain.model.progression.XpEventType
import com.reelmonster.app.domain.model.progression.XpTransaction
import com.reelmonster.app.service.focus.FocusSessionTracker
import com.reelmonster.app.service.tracking.MonitoringCoverageTracker
import kotlinx.coroutines.runBlocking
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
 * Phase14ProgressionTest
 *
 * Comprehensive test suite verifying Phase 14: XP, Streaks, Timestamped Goal History,
 * Estimated Monitoring Coverage & Achievements across all 32 required test scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase14ProgressionTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    private val pastDate = "2026-11-01"
    private val todayDate = "2026-11-08"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ReelDbHelper.DATABASE_NAME)
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        MonitoringCoverageTracker.instance.reset()
        FocusSessionTracker.instance.reset()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() = runBlocking {
        MonitoringCoverageTracker.instance.reset()
        FocusSessionTracker.instance.reset()
        preferencesManager.clearPreferences()
        repository.personalityEngine.reset()
        dbHelper.close()
    }

    // =========================================================================
    // Group A: Monitoring Coverage (Tests 1–12)
    // =========================================================================

    @Test
    fun test01_coverage_119MinutesEligibleMonitoring_failsQualification() {
        val duration119m = 119L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration119m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration119m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(duration119m, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(EstimatedCoverageStatus.ESTIMATED_INCOMPLETE, coverage.coverageStatus)
        assertFalse(coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertEquals("INSUFFICIENT_ESTIMATED_COVERAGE", eval.streakReason)
    }

    @Test
    fun test02_coverage_exactly120MinutesEligibleMonitoring_qualifies() {
        val duration120m = 120L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration120m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration120m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(duration120m, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(EstimatedCoverageStatus.ESTIMATED_VERIFIED, coverage.coverageStatus)
        assertTrue(coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.QUALIFIED, eval.streakStatus)
    }

    @Test
    fun test03_coverage_threeHeartbeatsUnder120Minutes_fails() {
        val duration45m = 45L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration45m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration45m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )
        repeat(3) { idx ->
            dbHelper.insertMonitoringHeartbeat(
                MonitoringHeartbeat(
                    timestamp = 1_000_000L + (idx * 900_000L),
                    monotonicMs = 1_000L + (idx * 900_000L),
                    dateKey = pastDate,
                    serviceConnected = true,
                    trackingEnabled = true,
                    screenOn = true
                )
            )
        }

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(3, coverage.heartbeatCount)
        assertEquals(duration45m, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(EstimatedCoverageStatus.ESTIMATED_INCOMPLETE, coverage.coverageStatus)
        assertFalse("Three heartbeats must never independently qualify a day", coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test04_coverage_moreThanThreeHeartbeatsUnder120Minutes_fails() {
        val duration90m = 90L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration90m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration90m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )
        repeat(8) { idx ->
            dbHelper.insertMonitoringHeartbeat(
                MonitoringHeartbeat(
                    timestamp = 1_000_000L + (idx * 600_000L),
                    monotonicMs = 1_000L + (idx * 600_000L),
                    dateKey = pastDate,
                    serviceConnected = true,
                    trackingEnabled = true,
                    screenOn = true
                )
            )
        }

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(8, coverage.heartbeatCount)
        assertFalse(coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test05_coverage_twoHoursServiceConnected_trackingDisabled_fails() {
        val duration150m = 150L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration150m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration150m,
                serviceConnected = true,
                trackingEnabled = false,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(duration150m, coverage.serviceConnectedMs)
        assertEquals(0L, coverage.trackingEnabledMs)
        assertEquals(duration150m, coverage.screenOnMs)
        assertEquals(0L, coverage.estimatedVerifiedMonitoringMs)
        assertFalse(coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test06_coverage_twoHoursServiceConnected_screenOff_fails() {
        val duration150m = 150L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration150m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration150m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = false,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(duration150m, coverage.serviceConnectedMs)
        assertEquals(duration150m, coverage.trackingEnabledMs)
        assertEquals(0L, coverage.screenOnMs)
        assertEquals(0L, coverage.estimatedVerifiedMonitoringMs)
        assertFalse(coverage.isQualifiedForProgression)
    }

    @Test
    fun test07_coverage_overlappingIntervals_notDoubleCounted() {
        val base = 10_000_000L
        val min90 = 90L * 60_000L
        val min30 = 30L * 60_000L
        val min120 = 120L * 60_000L

        // Interval 1: [0m .. 90m]
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base,
                endTimestamp = base + min90,
                monotonicStartMs = 0L,
                monotonicEndMs = min90,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )
        // Interval 2: [30m .. 120m] (overlaps by 60m with Interval 1)
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min30,
                endTimestamp = base + min120,
                monotonicStartMs = min30,
                monotonicEndMs = min120,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals("Overlapping 90m intervals spanning 120m must equal 120m, not 180m", min120, coverage.estimatedVerifiedMonitoringMs)
    }

    @Test
    fun test08_coverage_interruptionsExcludeIneligibleTime() {
        val base = 10_000_000L
        val min70 = 70L * 60_000L
        val min40 = 40L * 60_000L

        // 70m active
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base,
                endTimestamp = base + min70,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )
        // 40m interrupted (paused)
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min70,
                endTimestamp = base + min70 + min40,
                serviceConnected = true,
                trackingEnabled = false,
                screenOn = true,
                interruptionReason = "PAUSED",
                continuityStatus = ContinuityStatus.INTERRUPTED
            )
        )
        // 40m active after pause (total active = 110m < 120m)
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min70 + min40,
                endTimestamp = base + min70 + min40 + min40,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(110L * 60_000L, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(1, coverage.interruptionCount)
        assertFalse(coverage.isQualifiedForProgression)
    }

    @Test
    fun test09_coverage_recoveryResumesEligibleAccumulation() {
        val base = 10_000_000L
        val min80 = 80L * 60_000L
        val min20 = 20L * 60_000L
        val min50 = 50L * 60_000L

        // 80m active
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base,
                endTimestamp = base + min80,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )
        // 20m service disconnected interruption
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min80,
                endTimestamp = base + min80 + min20,
                serviceConnected = false,
                trackingEnabled = true,
                screenOn = true,
                interruptionReason = "SERVICE_DISCONNECTED",
                continuityStatus = ContinuityStatus.INTERRUPTED
            )
        )
        // 50m active after recovery (80m + 50m = 130m >= 120m)
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min80 + min20,
                endTimestamp = base + min80 + min20 + min50,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(130L * 60_000L, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(1, coverage.interruptionCount)
        assertEquals(1, coverage.recoveryCount)
        assertTrue(coverage.isQualifiedForProgression)
    }

    @Test
    fun test10_coverage_detectionEventsDoNotEstablishCoverage() {
        // 25 detections logged, but only 30 minutes of monitoring interval
        val min30 = 30L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + min30,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )
        repeat(25) { idx ->
            dbHelper.insertSupportedAppDetectionEvent(
                SupportedAppDetectionEvent(
                    timestamp = 1_000_000L + idx * 10_000L,
                    dateKey = pastDate,
                    platform = "INSTAGRAM",
                    eventType = "REEL_DETECTED",
                    contentFingerprint = "fp_$idx"
                )
            )
        }

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(25, coverage.detectionEventCount)
        assertEquals(min30, coverage.estimatedVerifiedMonitoringMs)
        assertFalse("Detection events must never substitute for monitoring duration", coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test11_coverage_zeroDetectionsQualifiesWhenCoverageSufficient() {
        seedVerifiedCoverage(pastDate, minutes = 140)
        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals(0, coverage.detectionEventCount)
        assertTrue(coverage.isQualifiedForProgression)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.QUALIFIED, eval.streakStatus)
        assertEquals("QUALIFIED_MINDFUL_ZERO_VIEWING_DAY", eval.streakReason)
    }

    @Test
    fun test12_coverage_uncertainContinuityIntervalsExcluded() {
        val base = 10_000_000L
        val min90 = 90L * 60_000L
        val min50 = 50L * 60_000L

        // 90m verified
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base,
                endTimestamp = base + min90,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )
        // 50m uncertain continuity (e.g., missing continuity heartbeats before crash)
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base + min90,
                endTimestamp = base + min90 + min50,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.UNCERTAIN
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(pastDate)
        assertEquals("Uncertain continuity interval must be excluded", min90, coverage.estimatedVerifiedMonitoringMs)
        assertFalse(coverage.isQualifiedForProgression)
    }

    // =========================================================================
    // Group B: Timestamped Goal History & Snapshots (Tests 13–22)
    // =========================================================================

    @Test
    fun test13_goalHistory_createdAtMidnight_isProgressionEligible() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_midnight_create",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "25:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )

        val snapshots = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true)
        assertEquals(1, snapshots.size)
        val snap = snapshots.first()
        assertTrue(snap.enabledAtMidnight)
        assertTrue(snap.progressionEligible)
        assertEquals(25, snap.strictestTarget)
    }

    @Test
    fun test14_goalHistory_enabledMidday_notProgressionEligibleThatDay() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)
        val middayMs = midnightMs + (10L * 3600_000L) // 10:00 AM

        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_midday_enable",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.ENABLED,
                previousValue = null,
                newValue = "25",
                effectiveTimestamp = middayMs,
                dateKey = pastDate
            )
        )

        val day1Snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertFalse("Goal enabled at 10:00 AM was not enabled at midnight", day1Snap.enabledAtMidnight)
        assertFalse("Goal enabled mid-day is not progression-eligible that day", day1Snap.progressionEligible)

        // Next complete calendar day ("2026-11-02") it IS progression-eligible!
        val nextDaySnap = dbHelper.getGoalSnapshotsForDate("2026-11-02", rebuildFromHistory = true).first()
        assertTrue("Goal remains enabled across next midnight", nextDaySnap.enabledAtMidnight)
        assertTrue("Goal is progression-eligible on next complete day", nextDaySnap.progressionEligible)
    }

    @Test
    fun test15_goalHistory_usageBeforeActivation_neverRetroactivelyAttributed() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)
        val morningMs = midnightMs + (9L * 3600_000L)
        val activationMs = midnightMs + (12L * 3600_000L)
        val afternoonMs = midnightMs + (15L * 3600_000L)

        // 10 reels before activation
        repeat(10) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, morningMs + idx * 1000L, pastDate)
        }
        // Activate goal at 12:00 PM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_activate_noon",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "25:true",
                effectiveTimestamp = activationMs,
                dateKey = pastDate
            )
        )
        // 4 reels after activation
        repeat(4) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, afternoonMs + idx * 1000L, pastDate)
        }

        val snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertEquals(14, snap.usageSinceMidnight)
        assertEquals("Usage before activation must never be retroactively attributed to usageSinceActivation", 4, snap.usageSinceActivation)
        assertFalse(snap.progressionEligible)
    }

    @Test
    fun test16_goalHistory_enabledLateInDay_cannotEarnSameDayGoalXp() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)
        val lateNightMs = midnightMs + (22L * 3600_000L) // 10:00 PM

        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_late_night",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "30:true",
                effectiveTimestamp = lateNightMs,
                dateKey = pastDate
            )
        )
        dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, lateNightMs + 60_000L, pastDate)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertFalse("Late-day activated goal must never earn same-day goal XP", eval.awardCountGoalXp)
        assertEquals("Mid-day/late-day goal alone cannot qualify the day", StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test17_goalHistory_targetReduction_doesNotErasePreviousViolation() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        // Goal of 20 active at midnight
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_create_20",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "20:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )
        // Watch 22 reels at 11:00 AM (violates 20)
        repeat(22) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 3600_000L + idx * 1000L, pastDate)
        }
        // Reduce target to 15 at 2:00 PM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_reduce_15",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.TARGET_CHANGED,
                previousValue = "20",
                newValue = "15",
                effectiveTimestamp = midnightMs + 14L * 3600_000L,
                dateKey = pastDate
            )
        )

        val snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertEquals(15, snap.strictestTarget)
        assertTrue(snap.wasViolated)
    }

    @Test
    fun test18_goalHistory_targetIncrease_doesNotErasePreviousViolation() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        // Goal of 20 active at midnight
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_create_20",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "20:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )
        // Watch 25 reels at 1:00 PM (violates 20)
        repeat(25) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 13L * 3600_000L + idx * 1000L, pastDate)
        }
        // Increase target to 50 at 9:00 PM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_increase_50",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.TARGET_CHANGED,
                previousValue = "20",
                newValue = "50",
                effectiveTimestamp = midnightMs + 21L * 3600_000L,
                dateKey = pastDate
            )
        )

        val snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertEquals("Strictest target must remain 20", 20, snap.strictestTarget)
        assertTrue("Violation must remain latched despite target increase", snap.wasViolated)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertFalse(eval.awardCountGoalXp)
    }

    @Test
    fun test19_goalHistory_disablingActiveGoal_doesNotEraseDayEvaluation() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_create_15",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "15:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )
        // Watch 20 reels (violates 15)
        repeat(20) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 10L * 3600_000L + idx * 1000L, pastDate)
        }
        // Disable goal at 6:00 PM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_disable",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.DISABLED,
                previousValue = "15",
                newValue = "15",
                effectiveTimestamp = midnightMs + 18L * 3600_000L,
                dateKey = pastDate
            )
        )

        val snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertTrue(snap.enabledAtMidnight)
        assertTrue(snap.wasDisabledDuringDay)
        assertTrue("Disabling an active goal must not erase a violation", snap.wasViolated)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertEquals("ACTIVE_GOAL_VIOLATED", eval.streakReason)
    }

    @Test
    fun test20_goalHistory_reenablingGoalMidday_doesNotRestoreEligibility() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_create_30",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "30:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )
        // Disable at 11:00 AM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_disable_11",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.DISABLED,
                previousValue = "30",
                newValue = "30",
                effectiveTimestamp = midnightMs + 11L * 3600_000L,
                dateKey = pastDate
            )
        )
        // Re-enable at 2:00 PM
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_reenable_14",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.ENABLED,
                previousValue = "30",
                newValue = "30",
                effectiveTimestamp = midnightMs + 14L * 3600_000L,
                dateKey = pastDate
            )
        )

        val snap = dbHelper.getGoalSnapshotsForDate(pastDate, rebuildFromHistory = true).first()
        assertTrue(snap.wasDisabledDuringDay)
        assertTrue(snap.wasReenabledMidday)
        assertFalse("Re-enabling a goal mid-day must not restore same-day progression eligibility", snap.progressionEligible)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertFalse(eval.awardCountGoalXp)
    }

    @Test
    fun test21_goalEvaluation_platformSpecificScopesEvaluatedIndependently() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        // Combined limit 30, Instagram limit 12
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_comb_30",
                goalId = 1L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.COMBINED,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "30:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )
        dbHelper.appendGoalHistoryEvent(
            DailyGoalHistoryEvent(
                eventId = "ev_ig_12",
                goalId = 2L,
                goalType = GoalType.REEL_COUNT,
                platformScope = GoalScope.INSTAGRAM,
                eventType = GoalHistoryEventType.CREATED,
                previousValue = null,
                newValue = "12:true",
                effectiveTimestamp = midnightMs,
                dateKey = pastDate
            )
        )

        // Watch 15 Instagram reels and 5 YouTube shorts (Combined = 20 <= 30, but IG = 15 > 12)
        repeat(15) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 3600_000L + idx * 1000L, pastDate)
        }
        repeat(5) { idx ->
            dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 7200_000L + idx * 1000L, pastDate)
        }

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertFalse("Count goal XP must not be awarded when Instagram scope failed", eval.awardCountGoalXp)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test22_goalEvaluation_xpAwardedOncePerGoalType_notPerScope() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        seedVerifiedCoverage(pastDate, minutes = 150)
        val midnightMs = dbHelper.getStartOfDayTimestamp(pastDate)

        // Enable COMBINED (30), INSTAGRAM (15), YOUTUBE (15) count goals at midnight
        listOf(
            Triple(1L, GoalScope.COMBINED, 30),
            Triple(2L, GoalScope.INSTAGRAM, 15),
            Triple(3L, GoalScope.YOUTUBE, 15)
        ).forEach { (id, scope, target) ->
            dbHelper.appendGoalHistoryEvent(
                DailyGoalHistoryEvent(
                    eventId = "ev_cnt_${scope.name}",
                    goalId = id,
                    goalType = GoalType.REEL_COUNT,
                    platformScope = scope,
                    eventType = GoalHistoryEventType.CREATED,
                    previousValue = null,
                    newValue = "$target:true",
                    effectiveTimestamp = midnightMs,
                    dateKey = pastDate
                )
            )
        }

        // Watch 5 IG and 5 YT (passes all 3 count scopes)
        repeat(5) { idx ->
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 3600_000L + idx * 1000L, pastDate)
            dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC, midnightMs + 7200_000L + idx * 1000L, pastDate)
        }

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertTrue(eval.awardCountGoalXp)

        val countTx = dbHelper.getXpTransactions().filter { it.eventType == XpEventType.DAILY_GOAL_COUNT_MET }
        assertEquals("XP must be awarded once per goal type, not once per scope", 1, countTx.size)
        assertEquals(30, countTx.first().xpAmount)
    }

    // =========================================================================
    // Group C: Progression & Integrity (Tests 23–32)
    // =========================================================================

    @Test
    fun test23_progression_focusOnlyDayWithInsufficientMonitoring_failsStreak() {
        // Only 45m monitoring coverage (< 120m)
        seedVerifiedCoverage(pastDate, minutes = 45)
        // Complete a 25m focus session on pastDate
        repository.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 25L * 60_000L,
                actualDurationMs = 25L * 60_000L,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + 25L * 60_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = pastDate
            )
        )

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals("Focus-only day with < 2h coverage must not qualify streak", StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertEquals("INSUFFICIENT_ESTIMATED_COVERAGE", eval.streakReason)
    }

    @Test
    fun test24_progression_focusXpAwardedIndependentlyWhenRequirementsPass() {
        // Low coverage day, yet focus session >= 80% target COMPLETED earns +20 XP
        seedVerifiedCoverage(pastDate, minutes = 10)
        val sessionId = repository.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 25L * 60_000L,
                actualDurationMs = 22L * 60_000L, // 88% >= 80%
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + 22L * 60_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = pastDate
            )
        )

        assertTrue(dbHelper.hasXpTransaction(XpTransaction.focusSessionKey(sessionId)))

        // Cancelled session or < 80% session earns 0 XP
        val shortSessionId = repository.insertFocusSession(
            FocusSession(
                sessionType = FocusType.DEEP_FOCUS,
                targetDurationMs = 25L * 60_000L,
                actualDurationMs = 15L * 60_000L, // 60% < 80%
                startTimestamp = 2_000_000L,
                endTimestamp = 2_000_000L + 15L * 60_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = pastDate
            )
        )
        assertFalse(dbHelper.hasXpTransaction(XpTransaction.focusSessionKey(shortSessionId)))
    }

    @Test
    fun test25_progression_resetInvalidatesDayGoalXpAndStreak() {
        seedVerifiedCoverage(pastDate, minutes = 150)
        // Earn Focus XP first
        repository.insertFocusSession(
            FocusSession(
                sessionType = FocusType.QUICK_BREAK,
                targetDurationMs = 10L * 60_000L,
                actualDurationMs = 10L * 60_000L,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + 10L * 60_000L,
                completionStatus = FocusSession.STATUS_COMPLETED,
                dateString = pastDate
            )
        )
        val xpBeforeReset = repository.userProgress.value.totalXp
        assertTrue(xpBeforeReset >= 20)

        // Perform daily reset on pastDate
        val resetResult = repository.performDailyReset(pastDate)
        assertTrue(resetResult.success)

        // Verify existing XP, user progress, achievements, goal history, and coverage were NOT deleted by reset!
        assertEquals("Reset Today must never delete XP or user progress", xpBeforeReset, repository.userProgress.value.totalXp)
        assertTrue(dbHelper.getDailyMonitoringCoverage(pastDate).estimatedVerifiedMonitoringMs >= 120L * 60_000L)

        // Verify reset used on pastDate invalidates that day's goal XP and streak qualification
        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertEquals("DAILY_RESET_USED", eval.streakReason)
        assertFalse(eval.awardCountGoalXp)
        assertFalse(eval.awardTimeGoalXp)
        assertFalse(eval.awardMindfulDayXp)
    }

    @Test
    fun test26_progression_mindfulZeroViewingDayQualifiesWithSufficientCoverage() {
        seedVerifiedCoverage(pastDate, minutes = 135)

        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.QUALIFIED, eval.streakStatus)
        assertEquals("QUALIFIED_MINDFUL_ZERO_VIEWING_DAY", eval.streakReason)
        assertTrue(eval.awardMindfulDayXp)
        assertTrue(dbHelper.hasXpTransaction(XpTransaction.mindfulDayKey(pastDate)))
    }

    @Test
    fun test27_progression_currentDayRemainsPending() {
        seedVerifiedCoverage(todayDate, minutes = 180)

        val eval = repository.evaluatePastDayProgression(todayDate, todayDate)
        assertEquals("Current local day must remain PENDING and never finalize early", StreakEvaluationStatus.PENDING, eval.streakStatus)
        assertNull(dbHelper.getStreakRecordForDate(todayDate))
        assertFalse(dbHelper.hasXpTransaction(XpTransaction.mindfulDayKey(todayDate)))
    }

    @Test
    fun test28_progression_duplicateCalculationsCannotAwardDuplicateXp() {
        seedVerifiedCoverage(pastDate, minutes = 140)

        repository.evaluatePastDayProgression(pastDate, todayDate)
        val xpAfterFirst = repository.userProgress.value.totalXp
        val txCountAfterFirst = dbHelper.getXpTransactions().size
        assertTrue(xpAfterFirst > 0)

        // Run evaluation 5 more times
        repeat(5) {
            repository.evaluatePastDayProgression(pastDate, todayDate)
        }

        assertEquals("Repeated evaluations must never award duplicate XP", xpAfterFirst, repository.userProgress.value.totalXp)
        assertEquals(txCountAfterFirst, dbHelper.getXpTransactions().size)
    }

    @Test
    fun test29_database_v4ToV5MigrationPreservesExistingRecords() {
        dbHelper.close()
        val dbFile = context.getDatabasePath(ReelDbHelper.DATABASE_NAME)
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        // Create a v4 SQLite database manually with existing records
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.version = 4
        rawDb.execSQL(
            """
            CREATE TABLE reel_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                target_app TEXT NOT NULL,
                tracking_mode TEXT NOT NULL,
                session_date TEXT NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE daily_aggregates (
                date_key TEXT PRIMARY KEY,
                total_reels INTEGER NOT NULL DEFAULT 0,
                ig_count INTEGER NOT NULL DEFAULT 0,
                yt_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE viewing_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target_app TEXT NOT NULL,
                fingerprint TEXT,
                start_timestamp INTEGER NOT NULL,
                end_timestamp INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                completion_status TEXT NOT NULL,
                session_date TEXT NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE daily_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                goal_type TEXT NOT NULL,
                platform_scope TEXT NOT NULL,
                target_value INTEGER NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(goal_type, platform_scope)
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE daily_goal_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_key TEXT NOT NULL,
                goal_type TEXT NOT NULL,
                platform_scope TEXT NOT NULL,
                target_value INTEGER NOT NULL,
                actual_value INTEGER NOT NULL,
                was_successful INTEGER NOT NULL,
                recorded_at INTEGER NOT NULL,
                UNIQUE(date_key, goal_type, platform_scope)
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_type TEXT NOT NULL,
                target_duration_ms INTEGER NOT NULL,
                actual_duration_ms INTEGER NOT NULL,
                start_timestamp INTEGER NOT NULL,
                end_timestamp INTEGER NOT NULL,
                completion_status TEXT NOT NULL,
                date_string TEXT NOT NULL
            )
            """.trimIndent()
        )

        rawDb.execSQL("INSERT INTO daily_aggregates VALUES ('2026-10-30', 12, 8, 4)")
        rawDb.execSQL("INSERT INTO daily_goals VALUES (1, 'REEL_COUNT', 'COMBINED', 25, 1, 100000, 100000)")
        rawDb.execSQL("INSERT INTO focus_sessions VALUES (1, 'DEEP_FOCUS', 1500000, 1500000, 1000, 1501000, 'COMPLETED', '2026-10-30')")
        rawDb.close()

        // Open with ReelDbHelper (triggers v4 -> v5 additive migration)
        dbHelper = ReelDbHelper(context)
        val goals = dbHelper.getAllGoals()
        assertTrue(goals.any { it.type == GoalType.REEL_COUNT && it.targetValue == 25 })
        assertEquals(1, dbHelper.getFocusSessionsForDate("2026-10-30").size)
        assertEquals(12, dbHelper.getDailyUsageMetrics("2026-10-30").totalReels)
        assertEquals(12, dbHelper.getAllAchievements().size)
        assertTrue(dbHelper.getGoalHistoryEvents().isNotEmpty())
    }

    @Test
    fun test30_reliability_repeatedAppRestartsDoNotDuplicateDurationOrXp() {
        val base = 10_000_000L
        val min120 = 120L * 60_000L

        // Insert interval and evaluate
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = pastDate,
                startTimestamp = base,
                endTimestamp = base + min120,
                monotonicStartMs = 0L,
                monotonicEndMs = min120,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true
            )
        )
        repository.evaluatePastDayProgression(pastDate, todayDate)
        val initialCoverage = dbHelper.getDailyMonitoringCoverage(pastDate).estimatedVerifiedMonitoringMs
        val initialXp = repository.userProgress.value.totalXp

        // Simulate 3 app restarts (re-instantiating ReelDbHelper & ReelRepository)
        repeat(3) {
            dbHelper.close()
            dbHelper = ReelDbHelper(context)
            repository = ReelRepository(dbHelper, preferencesManager)
            repository.evaluatePastDayProgression(pastDate, todayDate)
        }

        assertEquals(initialCoverage, dbHelper.getDailyMonitoringCoverage(pastDate).estimatedVerifiedMonitoringMs)
        assertEquals(initialXp, repository.userProgress.value.totalXp)
    }

    @Test
    fun test31_milestones_awardedOnlyAtExact3_7_30_lifetimeIdempotent() {
        dbHelper.clearGoalHistoryAndSnapshotsForTest()
        val days = listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04")
        days.forEach { d ->
            seedVerifiedCoverage(d, minutes = 130)
            repository.evaluatePastDayProgression(d, todayDate)
        }

        assertEquals(4, repository.userProgress.value.currentStreak)
        val milestone3Txs = dbHelper.getXpTransactions().filter { it.eventId == XpTransaction.streakMilestoneKey(3) }
        assertEquals("3-day milestone XP must be awarded once", 1, milestone3Txs.size)
        assertEquals(50, milestone3Txs.first().xpAmount)

        // Break streak on 2026-11-05 (insufficient coverage)
        seedVerifiedCoverage("2026-11-05", minutes = 10)
        repository.evaluatePastDayProgression("2026-11-05", "2026-11-15")
        assertEquals(0, repository.userProgress.value.currentStreak)
        assertEquals(4, repository.userProgress.value.longestStreak)

        // Rebuild streak to 3 days (Nov 6, 7, 8)
        listOf("2026-11-06", "2026-11-07", "2026-11-08").forEach { d ->
            seedVerifiedCoverage(d, minutes = 130)
            repository.evaluatePastDayProgression(d, "2026-11-15")
        }
        assertEquals(3, repository.userProgress.value.currentStreak)
        val milestone3TxsAfterRebuild = dbHelper.getXpTransactions().filter { it.eventId == XpTransaction.streakMilestoneKey(3) }
        assertEquals("Lifetime milestone 3 must never be re-awarded after breaking and rebuilding streak", 1, milestone3TxsAfterRebuild.size)
    }

    @Test
    fun test32_munchie_personalityReactionsFollowPriorityOrder() {
        val engine = repository.personalityEngine
        engine.reset()

        // 1. XP earned -> HAPPY (priority 3)
        engine.onXpEarned(20, "Focus Completed")
        assertEquals(MunchiePersonalityState.HAPPY, engine.currentState.value)

        // 2. Achievement unlocked -> EXCITED (priority 9 overrides priority 3)
        engine.onAchievementUnlocked("First Step into Zen")
        assertEquals(MunchiePersonalityState.EXCITED, engine.currentState.value)

        // 3. Lower priority HAPPY cannot override active EXCITED
        engine.onXpEarned(30, "Daily Goal Met")
        assertEquals(MunchiePersonalityState.EXCITED, engine.currentState.value)

        // 4. Level Up -> CELEBRATING (priority 10 overrides priority 9)
        engine.onLevelUp(2, LevelCalculator.titleForLevel(2))
        assertEquals(MunchiePersonalityState.CELEBRATING, engine.currentState.value)
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun seedVerifiedCoverage(dateKey: String, minutes: Int) {
        val durationMs = minutes.toLong() * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = dateKey,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + durationMs,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + durationMs,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED
            )
        )
    }
}
