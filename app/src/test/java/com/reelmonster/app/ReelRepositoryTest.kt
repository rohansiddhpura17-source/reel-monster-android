package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReelRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_repo.db")
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        preferencesManager = UserPreferencesManager(context)
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testInitialRepositoryState() {
        assertEquals(0, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)
        assertEquals(Pair(0, 0), repository.platformBreakdown.value)
        assertTrue(repository.last7Days.value.isEmpty())
        assertTrue(repository.recentEvents.value.isEmpty())
    }

    @Test
    fun testLogReelFlowEmissions() {
        repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL, force = true)

        assertEquals(1, repository.todayCount.value)
        assertEquals(Pair(1, 0), repository.platformBreakdown.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)
        assertEquals(1, repository.recentEvents.value.size)

        repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL, force = true)

        assertEquals(2, repository.todayCount.value)
        assertEquals(Pair(1, 1), repository.platformBreakdown.value)
        assertEquals(2, repository.recentEvents.value.size)
    }

    @Test
    fun testMonsterEvolutionProgression() {
        // Stage 1: Baby (0 - 5)
        repeat(5) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        assertEquals(5, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Stage 2: Curious (6 - 15)
        repository.logReel(TargetApp.INSTAGRAM, force = true) // 6th reel
        assertEquals(6, repository.todayCount.value)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)

        // Stage 3: Hungry (16 - 30)
        repeat(10) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 16 reels
        assertEquals(16, repository.todayCount.value)
        assertEquals(MunchieStage.HUNGRY, repository.munchieStage.value)

        // Stage 4: Stuffed (31 - 50)
        repeat(15) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 31 reels
        assertEquals(31, repository.todayCount.value)
        assertEquals(MunchieStage.STUFFED, repository.munchieStage.value)

        // Stage 5: Megachonk Overfed (51+)
        repeat(20) { repository.logReel(TargetApp.INSTAGRAM, force = true) } // 51 reels
        assertEquals(51, repository.todayCount.value)
        assertEquals(MunchieStage.MEGACHONK, repository.munchieStage.value)
    }

    @Test
    fun testClearAllDataFlows() {
        repository.logReel(TargetApp.INSTAGRAM, force = true)
        repository.logReel(TargetApp.YOUTUBE, force = true)
        assertEquals(2, repository.todayCount.value)

        repository.clearAllData()

        assertEquals(0, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)
        assertEquals(Pair(0, 0), repository.platformBreakdown.value)
        assertTrue(repository.recentEvents.value.isEmpty())
        assertTrue(repository.last7Days.value.isEmpty())
    }
}
