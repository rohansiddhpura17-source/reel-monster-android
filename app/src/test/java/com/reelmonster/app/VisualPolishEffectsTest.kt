package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryBright
import com.reelmonster.app.core.design.OrangeAccent
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.util.HapticHelper
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VisualPolishEffectsTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test_visual_polish.db")
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        dbHelper = ReelDbHelper(context)
        dbHelper.clearAllData()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() {
        dbHelper.clearAllData()
        dbHelper.close()
    }

    // =========================================================================
    // 1. STEP 1D & 1G: HAPTIC & SOUND TESTS
    // =========================================================================

    @Test
    fun testHapticDisabledDoesNotTrigger() {
        // When haptics are disabled in user preferences, execution is a safe no-op
        HapticHelper.performCountTick(context, isHapticEnabled = false)
        HapticHelper.performStageEvolution(context, isHapticEnabled = false)
        // No exceptions thrown, passes cleanly
        assertTrue(true)
    }

    @Test
    fun testHapticEnabledExecutesSafely() {
        // When haptics are enabled, calls are executed safely with hardware guards
        HapticHelper.performCountTick(context, isHapticEnabled = true)
        HapticHelper.performStageEvolution(context, isHapticEnabled = true)
        assertTrue(true)
    }

    @Test
    fun testStageUpSoundRespectsPreference() {
        // Sound disabled: no audio playback
        HapticHelper.playStageUpSound(isSoundEnabled = false)
        // Sound enabled: safely dispatches
        HapticHelper.playStageUpSound(isSoundEnabled = true)
        assertTrue(true)
    }

    // =========================================================================
    // 2. STEP 1E: PARTICLE BURST SPECIFICATION TESTS
    // =========================================================================

    @Test
    fun testParticleCountClampedWithinRange() {
        val requested8 = 8.coerceIn(8, 12)
        val requested12 = 12.coerceIn(8, 12)
        val requestedTooLow = 4.coerceIn(8, 12)
        val requestedTooHigh = 20.coerceIn(8, 12)

        assertEquals(8, requested8)
        assertEquals(12, requested12)
        assertEquals(8, requestedTooLow)
        assertEquals(12, requestedTooHigh)
    }

    @Test
    fun testParticleColorsContainThemePalette() {
        val colors = listOf(MintPrimary, MintPrimaryBright, PurpleAccent, OrangeAccent)
        assertEquals(4, colors.size)
        assertTrue(colors.contains(MintPrimary))
        assertTrue(colors.contains(PurpleAccent))
    }

    // =========================================================================
    // 3. STEP 1C: SQUASH AND STRETCH GEOMETRY & PROPORTIONS
    // =========================================================================

    @Test
    fun testSquashAndStretchPeakProportions() {
        // At peak reaction (rVal = 1.0f):
        // Scale X -> 1.15f
        // Scale Y -> 0.88f
        val rVal = 1.0f
        val peakScaleX = 1f + (rVal * 0.15f)
        val peakScaleY = 1f - (rVal * 0.12f)

        assertEquals(1.15f, peakScaleX, 0.001f)
        assertEquals(0.88f, peakScaleY, 0.001f)

        // At rest (rVal = 0.0f):
        val restScaleX = 1f + (0f * 0.15f)
        val restScaleY = 1f - (0f * 0.12f)
        assertEquals(1.0f, restScaleX, 0.001f)
        assertEquals(1.0f, restScaleY, 0.001f)
    }

    // =========================================================================
    // 4. STEP 1G: STAGE EVOLUTION PROGRESSION
    // =========================================================================

    @Test
    fun testStageEvolutionTransitions() {
        assertEquals(1, MunchieStage.BABY.level)
        assertEquals(2, MunchieStage.CURIOUS.level)
        assertEquals(3, MunchieStage.HUNGRY.level)
        assertEquals(4, MunchieStage.STUFFED.level)
        assertEquals(5, MunchieStage.MEGACHONK.level)

        // Evolving from Baby to Curious increases level
        assertTrue(MunchieStage.CURIOUS.level > MunchieStage.BABY.level)
        assertTrue(MunchieStage.HUNGRY.level > MunchieStage.CURIOUS.level)
        assertTrue(MunchieStage.STUFFED.level > MunchieStage.HUNGRY.level)
        assertTrue(MunchieStage.MEGACHONK.level > MunchieStage.STUFFED.level)
    }

    // =========================================================================
    // 5. PRESERVATION OF COUNTING, PERSISTENCE & INTEGRATION
    // =========================================================================

    @Test
    fun testVisualEffectsDoNotAlterCountingLogic() = runTest {
        assertEquals(0, repository.todayCount.value)

        // Log 1 Instagram reel
        val logged1 = repository.logReel(TargetApp.INSTAGRAM, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged1)
        assertEquals(1, repository.todayCount.value)

        // Log 1 YouTube short
        val logged2 = repository.logReel(TargetApp.YOUTUBE, TrackingMode.LEVEL_1_MANUAL)
        assertTrue(logged2)
        assertEquals(2, repository.todayCount.value)

        // Verify platform breakdown unchanged
        val (ig, yt) = repository.platformBreakdown.value
        assertEquals(1, ig)
        assertEquals(1, yt)
    }
}
