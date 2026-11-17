package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReelDbHelperTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use a test-specific database or clean prior runs
        context.deleteDatabase("test_reel_monster.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testDatabaseInsertionAndRetrieval() {
        // Initial count should be 0
        assertEquals(0, dbHelper.getTodayCount())

        // Insert first reel event (Instagram, Manual)
        val rowId1 = dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue("Row ID should be valid positive integer", rowId1 > 0)

        // Today count should now be 1
        assertEquals(1, dbHelper.getTodayCount())

        // Platform breakdown should show 1 IG, 0 YT
        val (ig1, yt1) = dbHelper.getTodayPlatformBreakdown()
        assertEquals(1, ig1)
        assertEquals(0, yt1)

        // Insert second reel event (YouTube, Automatic)
        val rowId2 = dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_2_AUTOMATIC)
        assertTrue("Second Row ID should be greater than first", rowId2 > rowId1)

        // Today count should now be 2
        assertEquals(2, dbHelper.getTodayCount())

        // Platform breakdown should show 1 IG, 1 YT
        val (ig2, yt2) = dbHelper.getTodayPlatformBreakdown()
        assertEquals(1, ig2)
        assertEquals(1, yt2)
    }

    @Test
    fun testDailyAggregationAndUpdates() {
        // Log 5 reels for Instagram and 3 for YouTube
        repeat(5) {
            dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        }
        repeat(3) {
            dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)
        }

        assertEquals(8, dbHelper.getTodayCount())
        val (ig, yt) = dbHelper.getTodayPlatformBreakdown()
        assertEquals(5, ig)
        assertEquals(3, yt)

        // Verify last 7 days contains today's aggregate
        val last7 = dbHelper.getLast7Days()
        assertEquals(1, last7.size)
        val todaySummary = last7.first()
        assertEquals(8, todaySummary.totalReels)
        assertEquals(5, todaySummary.instagramCount)
        assertEquals(3, todaySummary.youtubeCount)
        assertTrue(todaySummary.isToday)
        assertEquals(12.0, todaySummary.estimatedMinutesLost, 0.001) // 8 * 1.5 = 12.0
    }

    @Test
    fun testDuplicateAndSequentialEventHandling() {
        // Simulate rapid sequential logging (e.g. fast scrolling or double taps)
        for (i in 1..25) {
            val app = if (i % 2 == 0) TargetApp.INSTAGRAM else TargetApp.YOUTUBE
            dbHelper.insertReelEvent(app, TrackingMode.LEVEL_1_MANUAL)
        }

        assertEquals(25, dbHelper.getTodayCount())
        val events = dbHelper.getRecentEvents(50)
        assertEquals(25, events.size)

        // Verify descending chronological order of event IDs
        for (i in 0 until events.size - 1) {
            assertTrue(
                "Events must be returned in descending order",
                events[i].id > events[i + 1].id
            )
        }
    }

    @Test
    fun testClearAllData() {
        dbHelper.insertReelEvent(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        dbHelper.insertReelEvent(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)
        assertEquals(2, dbHelper.getTodayCount())

        dbHelper.clearAllData()

        assertEquals(0, dbHelper.getTodayCount())
        val (ig, yt) = dbHelper.getTodayPlatformBreakdown()
        assertEquals(0, ig)
        assertEquals(0, yt)
        assertTrue(dbHelper.getRecentEvents().isEmpty())
        assertTrue(dbHelper.getLast7Days().isEmpty())
    }
}
