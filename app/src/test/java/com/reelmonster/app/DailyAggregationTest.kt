package com.reelmonster.app

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.DailySummary
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DailyAggregationTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_daily_agg.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testBingeSpikeThreshold() {
        val nonSpike = DailySummary(
            dateString = "2026-11-07",
            dayOfWeek = "07",
            totalReels = 39,
            instagramCount = 20,
            youtubeCount = 19,
            estimatedMinutesLost = 58.5,
            isBingeSpike = 39 >= 40,
            isToday = false
        )
        assertFalse("39 reels must not trigger binge spike", nonSpike.isBingeSpike)

        val spike = DailySummary(
            dateString = "2026-11-08",
            dayOfWeek = "08",
            totalReels = 40,
            instagramCount = 25,
            youtubeCount = 15,
            estimatedMinutesLost = 60.0,
            isBingeSpike = 40 >= 40,
            isToday = true
        )
        assertTrue("40 reels must trigger binge spike alert", spike.isBingeSpike)

        val heavySpike = DailySummary(
            dateString = "2026-11-09",
            dayOfWeek = "09",
            totalReels = 54,
            instagramCount = 35,
            youtubeCount = 19,
            estimatedMinutesLost = 81.0,
            isBingeSpike = 54 >= 40,
            isToday = false
        )
        assertTrue("54 reels must trigger binge spike alert", heavySpike.isBingeSpike)
    }

    @Test
    fun testDateBoundaryAndMidnightIsolation() {
        val db = dbHelper.writableDatabase

        // Directly insert historical day aggregates simulating midnight transition
        val day1Values = ContentValues().apply {
            put(ReelDbHelper.COL_DAILY_DATE, "2026-11-06")
            put(ReelDbHelper.COL_DAILY_TOTAL, 15)
            put(ReelDbHelper.COL_DAILY_IG, 10)
            put(ReelDbHelper.COL_DAILY_YT, 5)
        }
        db.insert(ReelDbHelper.TABLE_DAILY, null, day1Values)

        val day2Values = ContentValues().apply {
            put(ReelDbHelper.COL_DAILY_DATE, "2026-11-07")
            put(ReelDbHelper.COL_DAILY_TOTAL, 45)
            put(ReelDbHelper.COL_DAILY_IG, 30)
            put(ReelDbHelper.COL_DAILY_YT, 15)
        }
        db.insert(ReelDbHelper.TABLE_DAILY, null, day2Values)

        // Today's event logged via API
        dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)

        val last7Days = dbHelper.getLast7Days()
        assertEquals(3, last7Days.size)

        // Verify ordering: oldest to newest after reversal
        assertEquals("2026-11-06", last7Days[0].dateString)
        assertEquals(15, last7Days[0].totalReels)
        assertFalse(last7Days[0].isBingeSpike)
        assertFalse(last7Days[0].isToday)

        assertEquals("2026-11-07", last7Days[1].dateString)
        assertEquals(45, last7Days[1].totalReels)
        assertTrue(last7Days[1].isBingeSpike)
        assertFalse(last7Days[1].isToday)

        assertEquals(DateTimeUtils.getTodayDateString(), last7Days[2].dateString)
        assertEquals(1, last7Days[2].totalReels)
        assertTrue(last7Days[2].isToday)
    }

    @Test
    fun testTimeFormattingBoundaries() {
        assertEquals("0.0 min", DateTimeUtils.formatEstimatedTime(0.0))
        assertEquals("45.0 min", DateTimeUtils.formatEstimatedTime(45.0))
        assertEquals("1h", DateTimeUtils.formatEstimatedTime(60.0))
        assertEquals("1h 5m", DateTimeUtils.formatEstimatedTime(65.0))
        assertEquals("2h 15m", DateTimeUtils.formatEstimatedTime(135.0))
    }
}
