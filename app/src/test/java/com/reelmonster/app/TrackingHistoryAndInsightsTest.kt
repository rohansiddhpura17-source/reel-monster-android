package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.HistoryDateRange
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.usecase.HistoryAnalyticsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingHistoryAndInsightsTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository
    private lateinit var historyUseCase: HistoryAnalyticsUseCase

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = ReelDbHelper(context)
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)
        repository.clearAllData()
        historyUseCase = HistoryAnalyticsUseCase(dbHelper)
    }

    // 1. Empty database handling & safe defaults
    @Test
    fun testEmptyDatabaseHandlingAndSafeDefaults() {
        HistoryDateRange.entries.forEach { range ->
            val stats = historyUseCase.computePeriodStatistics(range)
            assertEquals(0, stats.totalReels)
            assertEquals(0, stats.instagramCount)
            assertEquals(0, stats.youtubeCount)
            assertEquals(0.0, stats.estimatedMinutesLost, 0.001)
            assertEquals(0.0, stats.dailyAverage, 0.001)
            assertEquals(0.0, stats.averagePerActiveDay, 0.001)
            assertEquals(0, stats.activeDaysCount)
            assertNull(stats.highestDay)
            assertNull(stats.lowestActiveDay)
            assertFalse(stats.hasActivity)
            assertNotNull(stats.dailySeries)
            assertTrue(stats.dailySeries.isNotEmpty())
            assertTrue(stats.dailySeries.all { it.totalReels == 0 })
        }
    }

    // 2. Today aggregation & total consistency
    @Test
    fun testTodayAggregationAndTotalConsistency() = runTest {
        val todayStr = DateTimeUtils.getTodayDateString()

        // Log 3 Instagram Reels and 2 YouTube Shorts today
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.YOUTUBE, force = true)
        repository.logReel(TargetApp.YOUTUBE, force = true)

        val stats = repository.getPeriodStatistics(HistoryDateRange.TODAY)
        assertEquals(5, stats.totalReels)
        assertEquals(3, stats.instagramCount)
        assertEquals(2, stats.youtubeCount)
        assertEquals(stats.totalReels, stats.instagramCount + stats.youtubeCount)
        assertEquals(1, stats.totalDaysCount)
        assertEquals(5.0, stats.dailyAverage, 0.01)
        assertEquals(7.5, stats.estimatedMinutesLost, 0.01) // 5 * 1.5 min
        assertEquals(1, stats.activeDaysCount)
        assertNotNull(stats.highestDay)
        assertEquals(todayStr, stats.highestDay?.dateString)
        assertEquals(5, stats.highestDay?.totalReels)
    }

    // 3. Seven-day aggregation & zero-activity days
    @Test
    fun testSevenDayAggregationWithZeroActivityDays() = runTest {
        val today = Date()
        val cal = Calendar.getInstance().apply { time = today }

        cal.add(Calendar.DAY_OF_YEAR, -5)
        val dayMinus5Str = dateFormat.format(cal.time)

        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val dayMinus2Str = dateFormat.format(cal.time)

        // Insert historical events
        repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), dayMinus5Str)
        repository.logReelAt(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), dayMinus5Str)
        repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), dayMinus2Str)

        val stats = repository.getPeriodStatistics(HistoryDateRange.LAST_7_DAYS, referenceDate = today)
        assertEquals(7, stats.dailySeries.size)
        assertEquals(3, stats.totalReels)
        assertEquals(2, stats.instagramCount)
        assertEquals(1, stats.youtubeCount)
        assertEquals(stats.totalReels, stats.instagramCount + stats.youtubeCount)

        // Check active vs zero-activity days
        val activeDays = stats.dailySeries.filter { it.totalReels > 0 }
        assertEquals(2, activeDays.size)
        val zeroDays = stats.dailySeries.filter { it.totalReels == 0 }
        assertEquals(5, zeroDays.size)
    }

    // 4. Thirty-day aggregation
    @Test
    fun testThirtyDayAggregation() {
        val today = Date()
        val stats = historyUseCase.computePeriodStatistics(HistoryDateRange.LAST_30_DAYS, referenceDate = today)
        assertEquals(30, stats.dailySeries.size)
        assertEquals(30, stats.totalDaysCount)
    }

    // 5. Current month aggregation (1st to today, no future dates)
    @Test
    fun testCurrentMonthAggregation() {
        val today = Date()
        val currentDayOfMonth = Calendar.getInstance().apply { time = today }.get(Calendar.DAY_OF_MONTH)

        val stats = historyUseCase.computePeriodStatistics(HistoryDateRange.THIS_MONTH, referenceDate = today)
        assertEquals(currentDayOfMonth, stats.dailySeries.size)
        assertEquals(currentDayOfMonth, stats.totalDaysCount)

        val firstDayStr = stats.dailySeries.first().dateString
        assertTrue(firstDayStr.endsWith("-01"))
        val lastDayStr = stats.dailySeries.last().dateString
        assertEquals(DateTimeUtils.getTodayDateString(today), lastDayStr)
    }

    // 6. Previous month aggregation
    @Test
    fun testPreviousMonthAggregation() {
        val today = Date()
        val cal = Calendar.getInstance().apply {
            time = today
            add(Calendar.MONTH, -1)
        }
        val expectedDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val stats = historyUseCase.computePeriodStatistics(HistoryDateRange.PREVIOUS_MONTH, referenceDate = today)
        assertEquals(expectedDays, stats.dailySeries.size)
        assertEquals(expectedDays, stats.totalDaysCount)

        assertTrue(stats.dailySeries.first().dateString.endsWith("-01"))
        assertTrue(stats.dailySeries.last().dateString.endsWith(String.format(Locale.US, "-%02d", expectedDays)))
    }

    // 7. Month & year boundary handling
    @Test
    fun testMonthAndYearBoundaryHandling() {
        // Reference date: Jan 3, 2027
        val refDate = dateFormat.parse("2027-01-03")!!

        val interval7D = DateTimeUtils.calculateInterval(HistoryDateRange.LAST_7_DAYS, refDate)
        assertEquals("2026-12-28", interval7D.startDateStr)
        assertEquals("2027-01-03", interval7D.endDateStr)
        assertEquals(7, interval7D.daysCount)

        val series7D = DateTimeUtils.buildCalendarSeries(interval7D.startDateStr, interval7D.endDateStr, emptyMap(), refDate)
        assertEquals(7, series7D.size)
        assertEquals("2026-12-28", series7D[0].dateString)
        assertEquals("2026-12-29", series7D[1].dateString)
        assertEquals("2026-12-30", series7D[2].dateString)
        assertEquals("2026-12-31", series7D[3].dateString)
        assertEquals("2027-01-01", series7D[4].dateString) // Crosses year!
        assertEquals("2027-01-02", series7D[5].dateString)
        assertEquals("2027-01-03", series7D[6].dateString)

        // Previous month from Jan 2027 must be December 2026
        val intervalPrevMonth = DateTimeUtils.calculateInterval(HistoryDateRange.PREVIOUS_MONTH, refDate)
        assertEquals("2026-12-01", intervalPrevMonth.startDateStr)
        assertEquals("2026-12-31", intervalPrevMonth.endDateStr)
        assertEquals(31, intervalPrevMonth.daysCount)
    }

    // 8. Instagram and YouTube breakdown percentages
    @Test
    fun testPlatformBreakdownPercentages() = runTest {
        val todayStr = DateTimeUtils.getTodayDateString()

        // 6 Instagram, 4 YouTube = 10 total
        for (i in 1..6) repository.logReel(TargetApp.INSTAGRAM, force = true)
        for (i in 1..4) repository.logReel(TargetApp.YOUTUBE, force = true)

        val stats = repository.getPeriodStatistics(HistoryDateRange.TODAY)
        assertEquals(10, stats.totalReels)
        assertEquals(6, stats.instagramCount)
        assertEquals(4, stats.youtubeCount)
        assertEquals(60, stats.instagramPercentage)
        assertEquals(40, stats.youtubePercentage)
    }

    // 9. Estimated time lost formula
    @Test
    fun testEstimatedTimeLostFormula() {
        assertEquals(0.0, DateTimeUtils.estimateTimeMinutes(0), 0.001)
        assertEquals(1.5, DateTimeUtils.estimateTimeMinutes(1), 0.001)
        assertEquals(15.0, DateTimeUtils.estimateTimeMinutes(10), 0.001)
        assertEquals(75.0, DateTimeUtils.estimateTimeMinutes(50), 0.001)

        assertEquals("0.0 min", DateTimeUtils.formatEstimatedTime(0.0))
        assertEquals("15.0 min", DateTimeUtils.formatEstimatedTime(15.0))
        assertEquals("1h 15m", DateTimeUtils.formatEstimatedTime(75.0))
        assertEquals("2h", DateTimeUtils.formatEstimatedTime(120.0))
    }

    // 10. Daily average and active day average
    @Test
    fun testDailyAverageAndActiveDayAverage() = runTest {
        val today = Date()
        val cal = Calendar.getInstance().apply { time = today }

        cal.add(Calendar.DAY_OF_YEAR, -3)
        val d1 = dateFormat.format(cal.time)
        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val d2 = dateFormat.format(cal.time)

        // 8 reels on d1, 6 reels on d2 (Total: 14 reels across 2 active days in a 7-day period)
        for (i in 1..8) repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), d1)
        for (i in 1..6) repository.logReelAt(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), d2)

        val stats = repository.getPeriodStatistics(HistoryDateRange.LAST_7_DAYS, referenceDate = today)
        assertEquals(14, stats.totalReels)
        assertEquals(2, stats.activeDaysCount)
        assertEquals(7, stats.totalDaysCount)
        assertEquals(2.0, stats.dailyAverage, 0.01) // 14 / 7
        assertEquals(7.0, stats.averagePerActiveDay, 0.01) // 14 / 2
    }

    // 11. Highest-activity day and lowest-activity active day
    @Test
    fun testHighestAndLowestActiveDays() = runTest {
        val today = Date()
        val cal = Calendar.getInstance().apply { time = today }

        cal.add(Calendar.DAY_OF_YEAR, -4)
        val d1 = dateFormat.format(cal.time)
        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val d2 = dateFormat.format(cal.time)
        cal.time = today
        val d3 = dateFormat.format(cal.time)

        // d1: 2 reels, d2: 12 reels, d3: 5 reels
        for (i in 1..2) repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), d1)
        for (i in 1..12) repository.logReelAt(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), d2)
        for (i in 1..5) repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), d3)

        val stats = repository.getPeriodStatistics(HistoryDateRange.LAST_7_DAYS, referenceDate = today)
        assertEquals(12, stats.highestDay?.totalReels)
        assertEquals(d2, stats.highestDay?.dateString)

        assertEquals(2, stats.lowestActiveDay?.totalReels)
        assertEquals(d1, stats.lowestActiveDay?.dateString)
    }

    // 12. Period-over-period comparison
    @Test
    fun testPreviousPeriodComparison() = runTest {
        val today = Date()
        val cal = Calendar.getInstance().apply { time = today }

        // Current 7-day period (e.g. today - 2): log 10 reels
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val currentPeriodDay = dateFormat.format(cal.time)
        for (i in 1..10) repository.logReelAt(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), currentPeriodDay)

        // Previous 7-day period (e.g. today - 9): log 5 reels
        cal.time = today
        cal.add(Calendar.DAY_OF_YEAR, -9)
        val prevPeriodDay = dateFormat.format(cal.time)
        for (i in 1..5) repository.logReelAt(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, System.currentTimeMillis(), prevPeriodDay)

        val stats = repository.getPeriodStatistics(HistoryDateRange.LAST_7_DAYS, referenceDate = today)
        assertEquals(10, stats.totalReels)
        assertEquals(5, stats.previousPeriodTotal)
        assertNotNull(stats.percentageChange)
        assertEquals(100.0, stats.percentageChange!!, 0.01) // +100% growth
        assertTrue(stats.comparisonSummary.contains("+5 reels"))
        assertTrue(stats.comparisonSummary.contains("+100.0%"))
    }

    // 13. Historical persistence across repository instances
    @Test
    fun testHistoricalPersistenceAcrossRepositoryInstances() = runTest {
        val todayStr = DateTimeUtils.getTodayDateString()
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.YOUTUBE, force = true)

        // Create new repository instance pointing to the same database
        val newDbHelper = ReelDbHelper(context)
        val newRepository = ReelRepository(newDbHelper, preferencesManager)

        val stats = newRepository.getPeriodStatistics(HistoryDateRange.TODAY)
        assertEquals(2, stats.totalReels)
        assertEquals(1, stats.instagramCount)
        assertEquals(1, stats.youtubeCount)
        assertEquals(stats.totalReels, stats.instagramCount + stats.youtubeCount)
    }

    // 14. Selected-day detailed entry inspection
    @Test
    fun testSelectedDayDetailsInspection() = runTest {
        val todayStr = DateTimeUtils.getTodayDateString()
        val now = System.currentTimeMillis()

        dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, now, todayStr)
        dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, now + 1000, todayStr)

        val events = repository.getEventsForDate(todayStr)
        assertEquals(2, events.size)
        // Most recent first
        assertEquals(TargetApp.YOUTUBE, events[0].targetApp)
        assertEquals(TargetApp.INSTAGRAM, events[1].targetApp)
    }

    // 15. Invalid date string safety
    @Test
    fun testInvalidDateStringSafety() {
        assertNull(DateTimeUtils.parseDate("invalid-date"))
        assertNull(DateTimeUtils.parseDate(""))
        val emptySeries = DateTimeUtils.buildCalendarSeries("2026-11-10", "2026-11-05", emptyMap())
        assertTrue(emptySeries.isEmpty()) // start after end returns empty safely
    }

    // 16. Date range selection state in repository
    @Test
    fun testDateRangeSelectionInRepository() = runTest {
        assertEquals(HistoryDateRange.LAST_7_DAYS, repository.selectedHistoryRange.value)

        repository.selectHistoryDateRange(HistoryDateRange.LAST_30_DAYS)
        assertEquals(HistoryDateRange.LAST_30_DAYS, repository.selectedHistoryRange.value)
        assertEquals(30, repository.historyPeriodStats.value.totalDaysCount)

        repository.selectHistoryDateRange(HistoryDateRange.TODAY)
        assertEquals(HistoryDateRange.TODAY, repository.selectedHistoryRange.value)
        assertEquals(1, repository.historyPeriodStats.value.totalDaysCount)
    }
}
