package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalType
import java.time.LocalDate
import java.time.ZoneId
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
 * Phase16IntegrationTest
 *
 * Phase 16 cross-feature integration tests. Each test exercises a seam between
 * two features and asserts they agree, instead of testing one feature alone.
 *
 * - Tests 1-2: viewing-time-to-goals integration (DEF-01: historical per-platform
 *   viewing-time goals must use measured durations, not a proportional estimate).
 * - Test 3: focus-to-viewing separation (focus time must never bleed into viewing time).
 * - Test 4: XP idempotency (same focus session must never award XP twice).
 * - Test 5: reset safety across days (Reset Today must preserve other days' data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase16IntegrationTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    private val yesterday: String get() = LocalDate.now().minusDays(1).toString()
    private val today: String get() = DateTimeUtils.getTodayDateString()

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

    private fun epochMs(date: String, hour: Int): Long {
        return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
            hour * 3_600_000L
    }

    private fun session(app: TargetApp, date: String, durationMs: Long, startHour: Int = 12): ViewingSession {
        val start = epochMs(date, startHour)
        return ViewingSession(
            targetApp = app,
            startTimestamp = start,
            endTimestamp = start + durationMs,
            durationMs = durationMs,
            completionStatus = "COMPLETED",
            sessionDate = date
        )
    }

    private fun completedFocusSession(minutes: Long): FocusSession {
        val now = System.currentTimeMillis()
        return FocusSession(
            sessionType = FocusType.DEEP_FOCUS,
            targetDurationMs = minutes * 60_000L,
            actualDurationMs = minutes * 60_000L,
            startTimestamp = now - minutes * 60_000L,
            endTimestamp = now,
            completionStatus = FocusSession.STATUS_COMPLETED,
            dateString = today
        )
    }

    // =========================================================================
    // Viewing time -> goals (DEF-01)
    // =========================================================================

    @Test
    fun test01_perPlatformViewingTimeQueryReturnsMeasuredSplit() {
        dbHelper.insertViewingSession(session(TargetApp.INSTAGRAM, yesterday, 360_000L, 12))
        dbHelper.insertViewingSession(session(TargetApp.YOUTUBE, yesterday, 3_240_000L, 14))

        assertEquals(360_000L, dbHelper.getViewingTimeForDateByApp(yesterday, TargetApp.INSTAGRAM))
        assertEquals(3_240_000L, dbHelper.getViewingTimeForDateByApp(yesterday, TargetApp.YOUTUBE))
        assertEquals(3_600_000L, dbHelper.getViewingTimeForDate(yesterday))
        assertNull(dbHelper.getViewingTimeForDateByApp(today, TargetApp.INSTAGRAM))
        assertNull(dbHelper.getViewingTimeForDateByApp(LocalDate.now().minusDays(3).toString(), TargetApp.YOUTUBE))
    }

    @Test
    fun test02_historicalInstagramViewingGoalUsesMeasuredNotProportional() {
        // 9 IG reels + 1 YT reel yesterday, but dwell is the opposite:
        // IG 6 minutes, YT 54 minutes (total 60 minutes).
        repeat(9) {
            dbHelper.insertReelEvent(
                TargetApp.INSTAGRAM,
                TrackingMode.LEVEL_1_MANUAL,
                epochMs(yesterday, 12),
                yesterday
            )
        }
        dbHelper.insertReelEvent(
            TargetApp.YOUTUBE,
            TrackingMode.LEVEL_1_MANUAL,
            epochMs(yesterday, 13),
            yesterday
        )
        dbHelper.insertViewingSession(session(TargetApp.INSTAGRAM, yesterday, 360_000L, 12))
        dbHelper.insertViewingSession(session(TargetApp.YOUTUBE, yesterday, 3_240_000L, 14))

        dbHelper.insertOrUpdateGoal(
            DailyGoal(
                type = GoalType.VIEWING_TIME_MINUTES,
                scope = GoalScope.INSTAGRAM,
                targetValue = 30
            )
        )

        repository.finalizeHistoricalGoalRecords()

        val records = repository.getGoalRecordsForDate(yesterday)
        val igTime = records.firstOrNull {
            it.goalType == GoalType.VIEWING_TIME_MINUTES && it.scope == GoalScope.INSTAGRAM
        }
        assertNotNull("IG viewing-time record should exist for yesterday", igTime)
        // Measured IG viewing is 6 minutes. The old proportional estimate
        // would have reported 54 minutes (9/10 of the 60-minute total).
        assertEquals(6, igTime!!.actualValue)
        assertTrue("6 min is within the 30 min limit", igTime.wasSuccessful)
    }

    // =========================================================================
    // Focus -> viewing separation
    // =========================================================================

    @Test
    fun test03_focusSessionDoesNotChangeViewingTime() {
        dbHelper.insertViewingSession(session(TargetApp.INSTAGRAM, today, 600_000L, 10))
        assertEquals(600_000L, dbHelper.getTodayViewingTimeMs())

        val rowId = repository.insertFocusSession(completedFocusSession(25))
        assertTrue("Focus session should persist", rowId > 0L)

        assertEquals(600_000L, dbHelper.getTodayViewingTimeMs())
        assertEquals(600_000L, dbHelper.getViewingTimeForDate(today))
    }

    // =========================================================================
    // XP idempotency
    // =========================================================================

    @Test
    fun test04_duplicateFocusXpAwardIsNoOp() {
        val rowId = dbHelper.insertFocusSession(completedFocusSession(25))
        assertTrue("Focus session should persist", rowId > 0L)
        val persisted = completedFocusSession(25).copy(id = rowId)

        val xpBefore = dbHelper.getUserProgress().totalXp
        assertTrue("First award should succeed", dbHelper.evaluateAndAwardFocusSessionXp(persisted))
        val xpAfterFirst = dbHelper.getUserProgress().totalXp
        assertTrue("XP should increase after the first award", xpAfterFirst > xpBefore)

        assertFalse("Second award of the same session must be a no-op", dbHelper.evaluateAndAwardFocusSessionXp(persisted))
        assertEquals(xpAfterFirst, dbHelper.getUserProgress().totalXp)
    }

    // =========================================================================
    // Reset safety across days
    // =========================================================================

    @Test
    fun test05_dailyResetPreservesOtherDaysViewingSessions() {
        dbHelper.insertReelEvent(
            TargetApp.INSTAGRAM,
            TrackingMode.LEVEL_1_MANUAL,
            epochMs(yesterday, 12),
            yesterday
        )
        dbHelper.insertViewingSession(session(TargetApp.INSTAGRAM, yesterday, 600_000L, 12))
        dbHelper.insertReelEvent(
            TargetApp.INSTAGRAM,
            TrackingMode.LEVEL_1_MANUAL,
            epochMs(today, 10),
            today
        )
        dbHelper.insertViewingSession(session(TargetApp.YOUTUBE, today, 300_000L, 10))

        assertEquals(1, dbHelper.getEventsForDate(yesterday).size)

        val result = repository.performDailyReset(today)
        assertTrue("Reset should succeed", result.success)

        assertEquals(0, dbHelper.getTodayCount())
        assertEquals(600_000L, dbHelper.getViewingTimeForDate(yesterday))
        assertEquals(1, dbHelper.getEventsForDate(yesterday).size)
        assertNull(dbHelper.getViewingTimeForDate(today))
    }
}
