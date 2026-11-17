package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.DailySummary
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DashboardAndLiveMetricsTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_dashboard_metrics.db")
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
    fun testTodayTotalCountReactivity() {
        assertEquals(0, repository.todayCount.value)

        // Log Instagram Reel
        val logged1 = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged1)
        assertEquals(1, repository.todayCount.value)

        // Log YouTube Short
        val logged2 = repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged2)
        assertEquals(2, repository.todayCount.value)
    }

    @Test
    fun testSeparateInstagramAndYouTubeMetrics() {
        repeat(4) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        repeat(3) { repository.logReel(TargetApp.YOUTUBE, force = true) }

        val (igCount, ytCount) = repository.platformBreakdown.value
        assertEquals(4, igCount)
        assertEquals(3, ytCount)
        assertEquals(7, repository.todayCount.value)

        // Separate estimated time lost
        val igTime = DateTimeUtils.estimateTimeMinutes(igCount)
        val ytTime = DateTimeUtils.estimateTimeMinutes(ytCount)
        assertEquals(6.0, igTime, 0.001)
        assertEquals(4.5, ytTime, 0.001)

        assertEquals("6.0 min", DateTimeUtils.formatEstimatedTime(igTime))
        assertEquals("4.5 min", DateTimeUtils.formatEstimatedTime(ytTime))
    }

    @Test
    fun testPlatformSumConsistency() {
        // Zero state
        var (ig, yt) = repository.platformBreakdown.value
        assertEquals(repository.todayCount.value, ig + yt)

        // 1 count
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        ig = repository.platformBreakdown.value.first
        yt = repository.platformBreakdown.value.second
        assertEquals(1, repository.todayCount.value)
        assertEquals(repository.todayCount.value, ig + yt)

        // Multiple mixed counts
        repeat(5) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        repeat(8) { repository.logReel(TargetApp.YOUTUBE, force = true) }
        ig = repository.platformBreakdown.value.first
        yt = repository.platformBreakdown.value.second
        assertEquals(14, repository.todayCount.value)
        assertEquals(repository.todayCount.value, ig + yt)
    }

    @Test
    fun testEstimatedTimeCalculationFormula() {
        // Zero count
        assertEquals(0.0, DateTimeUtils.estimateTimeMinutes(0), 0.001)
        assertEquals("0.0 min", DateTimeUtils.formatEstimatedTime(0.0))

        // Single reel -> 1.5 min
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(1), 0.001)
        assertEquals("1.5 min", DateTimeUtils.formatEstimatedTime(1.5))

        // 10 reels -> 15.0 min
        assertEquals(15.0, DateTimeUtils.estimateTimeMinutes(10), 0.001)
        assertEquals("15.0 min", DateTimeUtils.formatEstimatedTime(15.0))

        // 40 reels -> 60.0 min = 1h
        assertEquals(60.0, DateTimeUtils.estimateTimeMinutes(40), 0.001)
        assertEquals("1h", DateTimeUtils.formatEstimatedTime(60.0))

        // 54 reels -> 81.0 min = 1h 21m
        assertEquals(81.0, DateTimeUtils.estimateTimeMinutes(54), 0.001)
        assertEquals("1h 21m", DateTimeUtils.formatEstimatedTime(81.0))
    }

    @Test
    fun testSevenDayCalendarSeriesWithZeroActivityDays() {
        // Empty database: Must return exactly 7 calendar days with 0 counts
        val series = dbHelper.getSevenDayCalendarSeries()
        assertEquals(7, series.size)

        for (i in 0 until 6) {
            val day = series[i]
            assertEquals(0, day.totalReels)
            assertEquals(0, day.instagramCount)
            assertEquals(0, day.youtubeCount)
            assertEquals(0.0, day.estimatedMinutesLost, 0.001)
            assertFalse(day.isBingeSpike)
            assertFalse(day.isToday)
        }

        // The last item is today
        val today = series.last()
        assertEquals(0, today.totalReels)
        assertTrue(today.isToday)
        assertEquals(DateTimeUtils.getTodayDateString(), today.dateString)
    }

    @Test
    fun testSevenDayCalendarSeriesWithHistoricalData() {
        // Simulate activity 2 days ago ("2026-11-06") and today
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        cal.add(Calendar.DAY_OF_YEAR, -2)
        val twoDaysAgoStr = sdf.format(cal.time)

        // Log 12 reels 2 days ago
        repeat(12) {
            repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, cal.timeInMillis, twoDaysAgoStr)
        }

        // Log 5 reels today
        repeat(5) { repository.logReel(TargetApp.YOUTUBE, force = true) }

        val series = repository.sevenDayActivity.value
        assertEquals(7, series.size)

        // Find day from 2 days ago
        val pastDay = series.first { it.dateString == twoDaysAgoStr }
        assertEquals(12, pastDay.totalReels)
        assertEquals(12, pastDay.instagramCount)
        assertEquals(0, pastDay.youtubeCount)
        assertFalse(pastDay.isToday)

        // Find today
        val today = series.first { it.isToday }
        assertEquals(5, today.totalReels)
        assertEquals(0, today.instagramCount)
        assertEquals(5, today.youtubeCount)

        // Total across 7 days
        val total7Days = series.sumOf { it.totalReels }
        assertEquals(17, total7Days)
    }

    @Test
    fun testMidnightRolloverHistoricalPreservation() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        // Log 8 reels yesterday
        repeat(8) {
            repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, cal.timeInMillis, yesterdayStr)
        }

        // Log 3 reels today
        repeat(3) { repository.logReel(TargetApp.YOUTUBE, force = true) }

        // Today count only reflects today's 3 reels
        assertEquals(3, repository.todayCount.value)

        // Yesterday's record is preserved in 7-day activity
        val yesterdaySummary = repository.sevenDayActivity.value.first { it.dateString == yesterdayStr }
        assertEquals(8, yesterdaySummary.totalReels)
        assertFalse(yesterdaySummary.isToday)
    }

    @Test
    fun testPauseResumeStateReactivity() = runTest {
        assertFalse(repository.isTrackingPaused.value)
        assertTrue(repository.isTrackingActive)

        // Pause
        preferencesManager.updateTrackingPaused(true)
        assertTrue(repository.isTrackingPaused.first { it })
        assertFalse(repository.isTrackingActive)

        // Attempting to log while paused is rejected
        val loggedWhilePaused = repository.logReel(TargetApp.INSTAGRAM)
        assertFalse(loggedWhilePaused)
        assertEquals(0, repository.todayCount.value)

        // Resume
        preferencesManager.updateTrackingPaused(false)
        assertFalse(repository.isTrackingPaused.first { !it })
        assertTrue(repository.isTrackingActive)

        // Logging after resume succeeds
        val loggedAfterResume = repository.logReel(TargetApp.INSTAGRAM)
        assertTrue(loggedAfterResume)
        assertEquals(1, repository.todayCount.value)
    }

    @Test
    fun testMunchieMascotStageEvolutionReactivity() {
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Stage 1 -> 5 reels: BABY
        repeat(5) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        assertEquals(5, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // 6th reel -> CURIOUS
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        assertEquals(6, repository.todayCount.value)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)

        // 16 reels -> HUNGRY
        repeat(10) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        assertEquals(16, repository.todayCount.value)
        assertEquals(MunchieStage.HUNGRY, repository.munchieStage.value)

        // 31 reels -> STUFFED
        repeat(15) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        assertEquals(31, repository.todayCount.value)
        assertEquals(MunchieStage.STUFFED, repository.munchieStage.value)

        // 51 reels -> MEGACHONK
        repeat(20) { repository.logReel(TargetApp.INSTAGRAM, force = true) }
        assertEquals(51, repository.todayCount.value)
        assertEquals(MunchieStage.MEGACHONK, repository.munchieStage.value)

        val quote = MunchieMascotEngine.getMessage(repository.munchieStage.value, 51)
        assertNotNull(quote)
        assertTrue(quote.isNotBlank())
    }

    @Test
    fun testEmptyDatabaseEdgeCases() {
        assertEquals(0, repository.todayCount.value)
        assertEquals(Pair(0, 0), repository.platformBreakdown.value)
        assertEquals(0.0, DateTimeUtils.estimateTimeMinutes(repository.todayCount.value), 0.001)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)
        assertEquals(7, repository.sevenDayActivity.value.size)
        assertEquals(0, repository.recentEvents.value.size)
    }
}
