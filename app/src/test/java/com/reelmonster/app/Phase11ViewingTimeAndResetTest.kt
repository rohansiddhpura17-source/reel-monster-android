package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.service.tracking.ReelAccessibilityService
import com.reelmonster.app.service.tracking.ViewingSessionTracker
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
 * Phase11ViewingTimeAndResetTest
 *
 * Full regression test suite for Phase 11:
 * - Group 1: Viewing Timer & Monotonic Session Measurement (Tests 1–10)
 * - Group 2: Daily Reset Engine & Allowance (Tests 11–18)
 * - Group 3: Global Tracking Master Switch (Tests 19–23)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase11ViewingTimeAndResetTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    private var fakeMonoTime = 10_000L
    private var fakeWallTime = System.currentTimeMillis()

    private lateinit var tracker: ViewingSessionTracker

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reel_monster.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)

        fakeMonoTime = 10_000L
        fakeWallTime = System.currentTimeMillis()

        tracker = ViewingSessionTracker(
            clockElapsedRealtime = { fakeMonoTime },
            clockWallTime = { fakeWallTime }
        )
        // Also plug into the singleton for integration paths
        ViewingSessionTracker.instance.clockElapsedRealtime = { fakeMonoTime }
        ViewingSessionTracker.instance.clockWallTime = { fakeWallTime }
    }

    @After
    fun tearDown() = runBlocking {
        tracker.endActiveSession(repository, "INTERRUPTED_RESET")
        ViewingSessionTracker.instance.endActiveSession(repository, "INTERRUPTED_RESET")
        preferencesManager.clearPreferences()
        dbHelper.close()
    }

    private fun createCandidate(app: TargetApp, fp: String): DetectionCandidate {
        return DetectionCandidate(
            targetApp = app,
            authorOrChannel = "creator_$fp",
            titleOrCaption = "caption_$fp",
            fingerprint = fp,
            timestamp = fakeWallTime
        )
    }

    // =========================================================================
    // GROUP 1: Viewing Timer & Monotonic Session Measurement (Tests 1–10)
    // =========================================================================

    @Test
    fun test01_initialTodayViewingTimeIsZero() {
        assertEquals(0L, repository.todayViewingTimeMs.value)
        assertEquals(Pair(0L, 0L), repository.todayPlatformViewingTimeMs.value)
        assertEquals(0L, repository.todayAverageDurationMs.value)
    }

    @Test
    fun test02_singleCompletedSessionDuration() {
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_test2")
        tracker.onReelConfirmed(candidate, repository)

        fakeMonoTime += 15_000L
        fakeWallTime += 15_000L

        val session = tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED)
        assertNotNull(session)
        assertEquals(15_000L, session!!.durationMs)
        assertEquals(ViewingSessionTracker.STATUS_COMPLETED, session.completionStatus)

        assertEquals(15_000L, repository.todayViewingTimeMs.value)
        assertEquals(15_000L, repository.todayPlatformViewingTimeMs.value.first)
        assertEquals(0L, repository.todayPlatformViewingTimeMs.value.second)
    }

    @Test
    fun test03_multipleConsecutiveSessionsAccumulateTime() {
        val c1 = createCandidate(TargetApp.INSTAGRAM, "fp_1")
        tracker.onReelConfirmed(c1, repository)
        fakeMonoTime += 10_000L
        fakeWallTime += 10_000L

        val c2 = createCandidate(TargetApp.INSTAGRAM, "fp_2")
        tracker.onReelConfirmed(c2, repository) // Closes c1 with 10s
        fakeMonoTime += 20_000L
        fakeWallTime += 20_000L

        val c3 = createCandidate(TargetApp.YOUTUBE, "fp_3")
        tracker.onReelConfirmed(c3, repository) // Closes c2 with 20s
        fakeMonoTime += 15_000L
        fakeWallTime += 15_000L

        tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED) // Closes c3 with 15s

        assertEquals(45_000L, repository.todayViewingTimeMs.value)
    }

    @Test
    fun test04_platformBreakdownSeparatesInstagramAndYouTube() {
        val igCandidate = createCandidate(TargetApp.INSTAGRAM, "ig_reel_1")
        tracker.onReelConfirmed(igCandidate, repository)
        fakeMonoTime += 25_000L
        fakeWallTime += 25_000L
        tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED)

        val ytCandidate = createCandidate(TargetApp.YOUTUBE, "yt_short_1")
        tracker.onReelConfirmed(ytCandidate, repository)
        fakeMonoTime += 40_000L
        fakeWallTime += 40_000L
        tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED)

        assertEquals(Pair(25_000L, 40_000L), repository.todayPlatformViewingTimeMs.value)
        assertEquals(65_000L, repository.todayViewingTimeMs.value)
    }

    @Test
    fun test05_averageDurationCalculation() {
        // 3 sessions: 10s, 20s, 30s -> total 60s -> avg 20s
        val c1 = createCandidate(TargetApp.INSTAGRAM, "fp_a1")
        tracker.onReelConfirmed(c1, repository)
        fakeMonoTime += 10_000L
        fakeWallTime += 10_000L

        val c2 = createCandidate(TargetApp.INSTAGRAM, "fp_a2")
        tracker.onReelConfirmed(c2, repository)
        fakeMonoTime += 20_000L
        fakeWallTime += 20_000L

        val c3 = createCandidate(TargetApp.INSTAGRAM, "fp_a3")
        tracker.onReelConfirmed(c3, repository)
        fakeMonoTime += 30_000L
        fakeWallTime += 30_000L
        tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED)

        assertEquals(20_000L, repository.todayAverageDurationMs.value)
    }

    @Test
    fun test06_appSwitchingClosesSessionWithInterruptedStatus() {
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_switch")
        tracker.onReelConfirmed(candidate, repository)
        fakeMonoTime += 12_000L
        fakeWallTime += 12_000L

        tracker.onAppBackgrounded(repository)

        assertFalse(tracker.isActive)
        val sessions = dbHelper.getViewingSessionsForDate(DateTimeUtils.getTodayDateString())
        assertEquals(1, sessions.size)
        assertEquals(ViewingSessionTracker.STATUS_INTERRUPTED_APP_SWITCH, sessions[0].completionStatus)
        assertEquals(12_000L, sessions[0].durationMs)
    }

    @Test
    fun test07_screenOffInterruptsViewingSession() {
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_screenoff")
        tracker.onReelConfirmed(candidate, repository)
        fakeMonoTime += 8_000L
        fakeWallTime += 8_000L

        tracker.onScreenOff(repository)

        assertFalse(tracker.isActive)
        val sessions = dbHelper.getViewingSessionsForDate(DateTimeUtils.getTodayDateString())
        assertEquals(1, sessions.size)
        assertEquals(ViewingSessionTracker.STATUS_INTERRUPTED_SCREEN_OFF, sessions[0].completionStatus)
        assertEquals(8_000L, sessions[0].durationMs)

        // Advance monotonic clock while screen is off
        fakeMonoTime += 60_000L
        fakeWallTime += 60_000L

        // Viewing time must still be exactly 8,000L
        assertEquals(8_000L, repository.todayViewingTimeMs.value)
    }

    @Test
    fun test08_trackingPauseInterruptsActiveSession() {
        val candidate = createCandidate(TargetApp.YOUTUBE, "fp_paused")
        tracker.onReelConfirmed(candidate, repository)
        fakeMonoTime += 5_000L
        fakeWallTime += 5_000L

        tracker.onTrackingPaused(repository)

        assertFalse(tracker.isActive)
        val sessions = dbHelper.getViewingSessionsForDate(DateTimeUtils.getTodayDateString())
        assertEquals(1, sessions.size)
        assertEquals(ViewingSessionTracker.STATUS_INTERRUPTED_PAUSED, sessions[0].completionStatus)
        assertEquals(5_000L, sessions[0].durationMs)
    }

    @Test
    fun test09_globalTrackingOffInterruptsActiveSession() {
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_globaloff")
        tracker.onReelConfirmed(candidate, repository)
        fakeMonoTime += 7_000L
        fakeWallTime += 7_000L

        tracker.onGlobalTrackingDisabled(repository)

        assertFalse(tracker.isActive)
        val sessions = dbHelper.getViewingSessionsForDate(DateTimeUtils.getTodayDateString())
        assertEquals(1, sessions.size)
        assertEquals(ViewingSessionTracker.STATUS_INTERRUPTED_GLOBAL_OFF, sessions[0].completionStatus)
        assertEquals(7_000L, sessions[0].durationMs)
    }

    @Test
    fun test10_historicalDaysWithoutSessionsReturnUnavailableOrNull() {
        val dummyPastDate = "2026-01-01"
        assertNull(dbHelper.getViewingTimeForDate(dummyPastDate))
        assertTrue(dbHelper.getViewingSessionsForDate(dummyPastDate).isEmpty())
    }

    // =========================================================================
    // GROUP 2: Daily Reset Engine (Tests 11–18)
    // =========================================================================

    @Test
    fun test11_firstDailyResetSucceedsAndResetsCountAndDuration() {
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.YOUTUBE, force = true)
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_reset1")
        tracker.onReelConfirmed(candidate, repository)
        fakeMonoTime += 30_000L
        fakeWallTime += 30_000L
        tracker.endActiveSession(repository, ViewingSessionTracker.STATUS_COMPLETED)

        assertEquals(2, repository.todayCount.value)
        assertEquals(30_000L, repository.todayViewingTimeMs.value)
        assertEquals(5, repository.remainingDailyResets.value)

        val result = repository.performDailyReset()
        assertTrue(result.success)
        assertEquals(4, result.remainingResets)
        assertEquals(0, repository.todayCount.value)
        assertEquals(0L, repository.todayViewingTimeMs.value)
        assertEquals(4, repository.remainingDailyResets.value)
    }

    @Test
    fun test12_fiveConsecutiveDailyResetsExhaustAllowance() {
        val today = DateTimeUtils.getTodayDateString()
        preferencesManager.setDailyResetUsage(today, 0)
        for (i in 1..5) {
            val result = repository.performDailyReset(today)
            assertTrue("Reset $i should succeed", result.success)
            assertEquals(5 - i, result.remainingResets)
        }
        assertEquals(0, repository.remainingDailyResets.value)
    }

    @Test
    fun test13_sixthDailyResetIsRejected() {
        val today = DateTimeUtils.getTodayDateString()
        preferencesManager.setDailyResetUsage(today, 0)
        for (i in 1..5) {
            repository.performDailyReset(today)
        }
        val sixthResult = repository.performDailyReset(today)
        assertFalse(sixthResult.success)
        assertEquals(0, sixthResult.remainingResets)
        assertTrue(sixthResult.message.contains("limit reached"))
    }

    @Test
    fun test14_dailyResetPreservesPreviousDaysData() {
        val yesterday = "2026-11-07"
        val today = DateTimeUtils.getTodayDateString()

        // Insert historical data for yesterday
        repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, 1000L, yesterday)
        val pastSession = ViewingSession(
            targetApp = TargetApp.INSTAGRAM,
            startTimestamp = 1000L,
            endTimestamp = 25000L,
            durationMs = 24000L,
            completionStatus = "COMPLETED",
            sessionDate = yesterday
        )
        dbHelper.insertViewingSession(pastSession)

        // Today's activity
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        val todaySession = ViewingSession(
            targetApp = TargetApp.INSTAGRAM,
            startTimestamp = 50000L,
            endTimestamp = 65000L,
            durationMs = 15000L,
            completionStatus = "COMPLETED",
            sessionDate = today
        )
        dbHelper.insertViewingSession(todaySession)
        repository.refresh()

        assertEquals(1, repository.todayCount.value)
        assertEquals(15000L, repository.todayViewingTimeMs.value)

        // Perform daily reset for today
        val resetResult = repository.performDailyReset(today)
        assertTrue(resetResult.success)

        // Today is cleared
        assertEquals(0, repository.todayCount.value)
        assertEquals(0L, repository.todayViewingTimeMs.value)

        // Yesterday remains intact
        assertEquals(24000L, dbHelper.getViewingTimeForDate(yesterday))
        assertEquals(1, dbHelper.getViewingSessionsForDate(yesterday).size)
        assertEquals(1, dbHelper.getEventsForDate(yesterday).size)
    }

    @Test
    fun test15_dailyResetEndsActiveViewingSessionSafely() {
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_active_reset")
        ViewingSessionTracker.instance.onReelConfirmed(candidate, repository)
        assertTrue(ViewingSessionTracker.instance.isActive)

        repository.performDailyReset()

        assertFalse(ViewingSessionTracker.instance.isActive)
        assertEquals(0L, repository.todayViewingTimeMs.value)
    }

    @Test
    fun test16_dailyResetAllowanceRenewsOnNextDate() {
        val day1 = "2026-11-20"
        val day2 = "2026-11-21"

        // Exhaust all 5 resets on day 1
        for (i in 1..5) {
            repository.performDailyReset(day1)
        }
        assertEquals(0, preferencesManager.getRemainingDailyResets(day1))

        // When day 2 rolls around at local midnight, usage for day 2 is automatically 0
        assertEquals(0, preferencesManager.getDailyResetUsage(day2))
        assertEquals(5, preferencesManager.getRemainingDailyResets(day2))
    }

    @Test
    fun test17_dailyResetClearsDeduplicationCache() {
        ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)
        val candidate = createCandidate(TargetApp.INSTAGRAM, "fp_dedup_test")
        ReelAccessibilityService.dedupEngine.processCandidate(candidate)
        ReelAccessibilityService.dedupEngine.checkDwell(fakeWallTime + 600L)

        // Daily reset clears dedup engine cache
        repository.performDailyReset()

        // Processing same candidate after reset is treated as fresh
        val decision = ReelAccessibilityService.dedupEngine.processCandidate(candidate)
        assertTrue(
            decision == com.reelmonster.app.service.tracking.DeduplicationEngine.Decision.PENDING_DWELL ||
                decision == com.reelmonster.app.service.tracking.DeduplicationEngine.Decision.CONFIRMED
        )
    }

    @Test
    fun test18_clearAllDataResetsDailyResetUsage() {
        val today = DateTimeUtils.getTodayDateString()
        repository.performDailyReset(today)
        repository.performDailyReset(today)
        assertEquals(3, repository.remainingDailyResets.value)

        repository.clearAllData()

        assertEquals(5, repository.remainingDailyResets.value)
    }

    // =========================================================================
    // GROUP 3: Global Tracking Master Switch (Tests 19–23)
    // =========================================================================

    @Test
    fun test19_globalSwitchOffRejectsAutomaticDetection() {
        preferencesManager.setGlobalTrackingEnabled(false)

        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_2_AUTOMATIC)
        assertFalse("logReel should reject when global switch is OFF", logged)
        assertEquals(0, repository.todayCount.value)
    }

    @Test
    fun test20_globalSwitchOffRejectsManualLogging() {
        preferencesManager.setGlobalTrackingEnabled(false)

        val logged = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertFalse("Manual logReel should reject when global switch is OFF", logged)
        assertEquals(0, repository.todayCount.value)
    }

    @Test
    fun test21_globalSwitchOverridesPauseResume() {
        preferencesManager.setGlobalTrackingEnabled(false)
        repository.resumeTracking()

        assertFalse("isTrackingActive must be false when global tracking is OFF", repository.isTrackingActive)
    }

    @Test
    fun test22_globalSwitchPreservesUserTrackingMode() {
        preferencesManager.setTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
        assertEquals(TrackingMode.LEVEL_2_AUTOMATIC, preferencesManager.trackingMode.value)

        preferencesManager.setGlobalTrackingEnabled(false)
        assertEquals(TrackingMode.LEVEL_2_AUTOMATIC, preferencesManager.trackingMode.value)

        preferencesManager.setGlobalTrackingEnabled(true)
        assertEquals(TrackingMode.LEVEL_2_AUTOMATIC, preferencesManager.trackingMode.value)
    }

    @Test
    fun test23_reenablingGlobalSwitchResumesTracking() {
        preferencesManager.setGlobalTrackingEnabled(false)
        assertFalse(repository.logReel(TargetApp.INSTAGRAM))

        preferencesManager.setGlobalTrackingEnabled(true)
        val logged = repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertTrue(logged)
        assertEquals(1, repository.todayCount.value)
    }
}
