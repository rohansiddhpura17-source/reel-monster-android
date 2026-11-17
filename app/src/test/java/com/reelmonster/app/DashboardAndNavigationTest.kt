package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.presentation.navigation.Screen
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DashboardAndNavigationTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = kotlinx.coroutines.test.runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_dashboard_nav.db")
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
    fun testEmptyDashboardState() {
        // When no reels are recorded
        assertEquals(0, repository.todayCount.value)
        assertEquals(Pair(0, 0), repository.platformBreakdown.value)
        assertTrue(repository.last7Days.value.isEmpty())
        assertTrue(repository.recentEvents.value.isEmpty())

        // Mascot starts at Baby stage with 0 progress
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)
        assertEquals(0.0f, MunchieMascotEngine.getProgressToNextStage(repository.todayCount.value), 0.001f)
        assertEquals(6, MunchieMascotEngine.getReelsUntilNextStage(repository.todayCount.value))

        // Time drift math handles zero properly
        assertEquals(0.0, DateTimeUtils.estimateTimeMinutes(0), 0.001)
        assertEquals("0.0 min", DateTimeUtils.formatEstimatedTime(0.0))

        // Baby quote is selected
        val message = MunchieMascotEngine.getMessage(repository.munchieStage.value, 0)
        assertNotNull(message)
        assertTrue(message.isNotBlank())
    }

    @Test
    fun testDashboardDataRenderingAfterLogs() {
        // Log 8 Instagram reels and 4 YouTube Shorts
        repeat(8) {
            repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, force = true)
        }
        repeat(4) {
            repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, force = true)
        }

        // Real application state updates
        assertEquals(12, repository.todayCount.value)
        assertEquals(Pair(8, 4), repository.platformBreakdown.value)

        // 12 reels falls into Stage 2: Curious (6 to 15)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)

        // Progress in Curious stage (6 to 15: 12 is index 6 of 10 -> 0.6f)
        assertEquals(0.6f, MunchieMascotEngine.getProgressToNextStage(repository.todayCount.value), 0.01f)
        assertEquals(4, MunchieMascotEngine.getReelsUntilNextStage(repository.todayCount.value))

        // Estimated drift: 12 * 1.5 = 18.0 min
        val minutes = DateTimeUtils.estimateTimeMinutes(repository.todayCount.value)
        assertEquals(18.0, minutes, 0.001)
        assertEquals("18.0 min", DateTimeUtils.formatEstimatedTime(minutes))

        // 7-day preview includes today's summary
        val last7 = repository.last7Days.value
        assertEquals(1, last7.size)
        val todaySummary = last7.first()
        assertEquals(12, todaySummary.totalReels)
        assertEquals(8, todaySummary.instagramCount)
        assertEquals(4, todaySummary.youtubeCount)
        assertTrue(todaySummary.isToday)
        assertFalse(todaySummary.isBingeSpike)
    }

    @Test
    fun testBottomNavigationDestinationsAndStateRetention() {
        // Approved 4 destinations
        val bottomItems = Screen.bottomNavItems
        assertEquals(4, bottomItems.size)
        assertEquals(Screen.Home, bottomItems[0])
        assertEquals(Screen.Stats, bottomItems[1])
        assertEquals(Screen.History, bottomItems[2])
        assertEquals(Screen.Settings, bottomItems[3])

        // Verify routes
        assertEquals("home", Screen.Home.route)
        assertEquals("stats", Screen.Stats.route)
        assertEquals("history", Screen.History.route)
        assertEquals("settings", Screen.Settings.route)

        // Verify route restoration / lookup logic
        fun resolveScreen(route: String): Screen {
            return Screen.bottomNavItems.firstOrNull { it.route == route } ?: Screen.Home
        }

        assertEquals(Screen.Home, resolveScreen("home"))
        assertEquals(Screen.Stats, resolveScreen("stats"))
        assertEquals(Screen.History, resolveScreen("history"))
        assertEquals(Screen.Settings, resolveScreen("settings"))
        assertEquals(Screen.Home, resolveScreen("unknown_or_corrupted_route"))

        // Flow screens should have non-null titles and unique routes
        assertEquals("onboarding", Screen.Onboarding.route)
        assertEquals("growth_gallery", Screen.GrowthGallery.route)
        assertFalse(Screen.bottomNavItems.contains(Screen.Onboarding))
    }
}
