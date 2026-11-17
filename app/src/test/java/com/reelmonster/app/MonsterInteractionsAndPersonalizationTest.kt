package com.reelmonster.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieMascotEngine
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.ReactionStyle
import com.reelmonster.app.domain.model.TargetApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying Phase 6 requirements:
 * 1. Five monster growth stages & exact boundary transitions.
 * 2. Contextual funny messages, stage tones, zero count, first reel, paused state.
 * 3. Munchie personalization themes & color variation.
 * 4. Reaction styles (Bounce, Squash & Stretch, Happy Wiggle).
 * 5. Mascot animation toggle.
 * 6. Sound & vibration opt-in defaults (both false).
 * 7. Shared mascot state across repository consumers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MonsterInteractionsAndPersonalizationTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = ReelDbHelper(context)
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        repository = ReelRepository(dbHelper, preferencesManager)
        repository.clearAllData()
    }

    // ==========================================
    // 1. FIVE MONSTER GROWTH STAGES & BOUNDARIES
    // ==========================================
    @Test
    fun testFiveGrowthStageBoundaries() {
        // Stage 1: Baby (0 - 5)
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(0))
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(1))
        assertEquals(MunchieStage.BABY, MunchieStage.fromCount(5))

        // Boundary: 5 -> 6 (Baby to Curious)
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(6))

        // Stage 2: Curious (6 - 15)
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(10))
        assertEquals(MunchieStage.CURIOUS, MunchieStage.fromCount(15))

        // Boundary: 15 -> 16 (Curious to Hungry)
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(16))

        // Stage 3: Hungry (16 - 30)
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(20))
        assertEquals(MunchieStage.HUNGRY, MunchieStage.fromCount(30))

        // Boundary: 30 -> 31 (Hungry to Stuffed)
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(31))

        // Stage 4: Stuffed (31 - 50)
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(40))
        assertEquals(MunchieStage.STUFFED, MunchieStage.fromCount(50))

        // Boundary: 50 -> 51 (Stuffed to Megachonk)
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(51))

        // Stage 5: Megachonk (51+)
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(75))
        assertEquals(MunchieStage.MEGACHONK, MunchieStage.fromCount(1000))
    }

    // ==========================================
    // 2. CONTEXTUAL FUNNY MESSAGES & STAGE TONES
    // ==========================================
    @Test
    fun testContextualMessagesSpecialStates() {
        // Zero count message
        val zeroMsg = MunchieMascotEngine.getMessage(MunchieStage.BABY, 0, isPaused = false)
        assertEquals(MunchieMascotEngine.MSG_ZERO_COUNT, zeroMsg)

        // First reel message
        val firstMsg = MunchieMascotEngine.getMessage(MunchieStage.BABY, 1, isPaused = false)
        assertEquals(MunchieMascotEngine.MSG_FIRST_REEL, firstMsg)

        // Paused state message (takes priority regardless of stage)
        val pausedBaby = MunchieMascotEngine.getMessage(MunchieStage.BABY, 2, isPaused = true)
        assertEquals(MunchieMascotEngine.MSG_PAUSED, pausedBaby)

        val pausedHungry = MunchieMascotEngine.getMessage(MunchieStage.HUNGRY, 20, isPaused = true)
        assertEquals(MunchieMascotEngine.MSG_PAUSED, pausedHungry)

        val pausedMegachonk = MunchieMascotEngine.getMessage(MunchieStage.MEGACHONK, 60, isPaused = true)
        assertEquals(MunchieMascotEngine.MSG_PAUSED, pausedMegachonk)
    }

    @Test
    fun testStageSpecificMessageTones() {
        // Baby Tone: "Just one? I'm still hungry!"
        val babyPool = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.BABY] ?: emptyList()
        assertTrue(babyPool.any { it.contains("Just one? I'm still hungry!") })
        assertTrue(babyPool.any { it.contains("Just one more?") })

        // Curious Tone: "What's this Reel thing?"
        val curiousPool = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.CURIOUS] ?: emptyList()
        assertTrue(curiousPool.any { it.contains("What's this Reel thing?") })
        assertTrue(curiousPool.any { it.contains("I can smell another Short!") })

        // Hungry Tone: "Feed me another!"
        val hungryPool = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.HUNGRY] ?: emptyList()
        assertTrue(hungryPool.any { it.contains("Feed me another!") })
        assertTrue(hungryPool.any { it.contains("Feed me another Reel!") })

        // Stuffed Tone: "I think we've watched enough..."
        val stuffedPool = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.STUFFED] ?: emptyList()
        assertTrue(stuffedPool.any { it.contains("I think we've watched enough...") })
        assertTrue(stuffedPool.any { it.contains("Burp! Your evening disappeared.") })

        // Megachonk Tone: "WE HAVE BECOME THE ALGORITHM!"
        val megachonkPool = MunchieMascotEngine.STAGE_MESSAGES[MunchieStage.MEGACHONK] ?: emptyList()
        assertTrue(megachonkPool.any { it.contains("WE HAVE BECOME THE ALGORITHM!") })
        assertTrue(megachonkPool.any { it.contains("I'm not a monster anymore... I'm a server rack!") })
    }

    @Test
    fun testDeterministicMessageSelection() {
        // Determinism: Same stage and count produces the exact same message
        val msg1 = MunchieMascotEngine.getMessage(MunchieStage.HUNGRY, 22, isPaused = false)
        val msg2 = MunchieMascotEngine.getMessage(MunchieStage.HUNGRY, 22, isPaused = false)
        assertEquals(msg1, msg2)

        // Safe bounds handling
        val negativeCountMsg = MunchieMascotEngine.getMessage(MunchieStage.CURIOUS, -5, isPaused = false)
        assertNotNull(negativeCountMsg)
        assertTrue(negativeCountMsg.isNotBlank())

        val highCountMsg = MunchieMascotEngine.getMessage(MunchieStage.MEGACHONK, 99999, isPaused = false)
        assertNotNull(highCountMsg)
        assertTrue(highCountMsg.isNotBlank())
    }

    // ==========================================
    // 3. MUNCHIE THEMES & COLOR VARIATIONS
    // ==========================================
    @Test
    fun testMunchieThemesEnumAndFallback() {
        assertEquals(4, MunchieTheme.entries.size)

        assertEquals(MunchieTheme.MINT_CLASSIC, MunchieTheme.fromKey("mint_classic"))
        assertEquals(MunchieTheme.BERRY_PURPLE, MunchieTheme.fromKey("berry_purple"))
        assertEquals(MunchieTheme.SUNSHINE_ORANGE, MunchieTheme.fromKey("sunshine_orange"))
        assertEquals(MunchieTheme.OCEAN_BLUE, MunchieTheme.fromKey("ocean_blue"))

        // Fallback for null or unknown keys
        assertEquals(MunchieTheme.MINT_CLASSIC, MunchieTheme.fromKey(null))
        assertEquals(MunchieTheme.MINT_CLASSIC, MunchieTheme.fromKey("unknown_theme"))
    }

    @Test
    fun testThemeBodyColorsPerStage() {
        MunchieTheme.entries.forEach { theme ->
            val babyColor = theme.getStageBodyColor(MunchieStage.BABY)
            val curiousColor = theme.getStageBodyColor(MunchieStage.CURIOUS)
            val hungryColor = theme.getStageBodyColor(MunchieStage.HUNGRY)
            val stuffedColor = theme.getStageBodyColor(MunchieStage.STUFFED)
            val megachonkColor = theme.getStageBodyColor(MunchieStage.MEGACHONK)

            // Colors must be distinct across stages
            assertNotEquals(babyColor, hungryColor)
            assertNotEquals(babyColor, megachonkColor)
            assertNotEquals(hungryColor, megachonkColor)
        }
    }

    @Test
    fun testThemePreferencePersistence() = runTest {
        // Default theme
        assertEquals(MunchieTheme.MINT_CLASSIC, preferencesManager.monsterThemeFlow.first())

        // Change to BERRY_PURPLE
        preferencesManager.updateMonsterTheme(MunchieTheme.BERRY_PURPLE)
        assertEquals(MunchieTheme.BERRY_PURPLE, preferencesManager.monsterThemeFlow.first())

        // Change to SUNSHINE_ORANGE
        preferencesManager.updateMonsterTheme(MunchieTheme.SUNSHINE_ORANGE)
        assertEquals(MunchieTheme.SUNSHINE_ORANGE, preferencesManager.monsterThemeFlow.first())

        // Change to OCEAN_BLUE
        preferencesManager.updateMonsterTheme(MunchieTheme.OCEAN_BLUE)
        assertEquals(MunchieTheme.OCEAN_BLUE, preferencesManager.monsterThemeFlow.first())

        // Restore to MINT_CLASSIC
        preferencesManager.updateMonsterTheme(MunchieTheme.MINT_CLASSIC)
        assertEquals(MunchieTheme.MINT_CLASSIC, preferencesManager.monsterThemeFlow.first())
    }

    // ==========================================
    // 4. REACTION STYLES
    // ==========================================
    @Test
    fun testReactionStylesEnumAndFallback() {
        assertEquals(3, ReactionStyle.entries.size)

        assertEquals(ReactionStyle.BOUNCE, ReactionStyle.fromKey("bounce"))
        assertEquals(ReactionStyle.SQUASH_AND_STRETCH, ReactionStyle.fromKey("squash"))
        assertEquals(ReactionStyle.WINK_AND_HAPPY, ReactionStyle.fromKey("wink"))

        // Fallback
        assertEquals(ReactionStyle.BOUNCE, ReactionStyle.fromKey(null))
        assertEquals(ReactionStyle.BOUNCE, ReactionStyle.fromKey("unknown"))
    }

    @Test
    fun testReactionStylePreferencePersistence() = runTest {
        // Default is BOUNCE
        assertEquals(ReactionStyle.BOUNCE, preferencesManager.reactionStyleFlow.first())

        // Update to SQUASH_AND_STRETCH
        preferencesManager.updateReactionStyle(ReactionStyle.SQUASH_AND_STRETCH)
        assertEquals(ReactionStyle.SQUASH_AND_STRETCH, preferencesManager.reactionStyleFlow.first())

        // Update to WINK_AND_HAPPY
        preferencesManager.updateReactionStyle(ReactionStyle.WINK_AND_HAPPY)
        assertEquals(ReactionStyle.WINK_AND_HAPPY, preferencesManager.reactionStyleFlow.first())

        // Restore to BOUNCE
        preferencesManager.updateReactionStyle(ReactionStyle.BOUNCE)
        assertEquals(ReactionStyle.BOUNCE, preferencesManager.reactionStyleFlow.first())
    }

    // ==========================================
    // 5. MASCOT ANIMATION TOGGLE
    // ==========================================
    @Test
    fun testMascotAnimationTogglePersistence() = runTest {
        // Default is enabled (true)
        assertTrue(preferencesManager.isMascotAnimationEnabledFlow.first())

        // Toggle off
        preferencesManager.updateMascotAnimationEnabled(false)
        assertFalse(preferencesManager.isMascotAnimationEnabledFlow.first())

        // Toggle back on
        preferencesManager.updateMascotAnimationEnabled(true)
        assertTrue(preferencesManager.isMascotAnimationEnabledFlow.first())
    }

    // ==========================================
    // 6. SOUND AND VIBRATION DEFAULTS (OFF)
    // ==========================================
    @Test
    fun testSoundAndVibrationDefaultsDisabled() = runTest {
        // Section 7 Requirement: Sound and vibration disabled by default
        assertFalse(preferencesManager.isSoundFxEnabledFlow.first())
        assertFalse(preferencesManager.isHapticEnabledFlow.first())

        // Opt-in enable
        preferencesManager.updateSoundFxEnabled(true)
        preferencesManager.updateHapticEnabled(true)
        assertTrue(preferencesManager.isSoundFxEnabledFlow.first())
        assertTrue(preferencesManager.isHapticEnabledFlow.first())

        // Opt-out disable
        preferencesManager.updateSoundFxEnabled(false)
        preferencesManager.updateHapticEnabled(false)
        assertFalse(preferencesManager.isSoundFxEnabledFlow.first())
        assertFalse(preferencesManager.isHapticEnabledFlow.first())
    }

    // ==========================================
    // 7. SHARED MASCOT STATE ACROSS CONSUMERS
    // ==========================================
    @Test
    fun testSharedMascotStateProgression() = runTest {
        // Initial state
        assertEquals(0, repository.todayCount.value)
        assertEquals(MunchieStage.BABY, repository.munchieStage.value)

        // Log 6 reels -> Advances to CURIOUS
        for (i in 1..6) {
            repository.logReel(TargetApp.INSTAGRAM, force = true)
        }
        assertEquals(6, repository.todayCount.value)
        assertEquals(MunchieStage.CURIOUS, repository.munchieStage.value)

        // Log 10 more reels (total 16) -> Advances to HUNGRY
        for (i in 7..16) {
            repository.logReel(TargetApp.YOUTUBE, force = true)
        }
        assertEquals(16, repository.todayCount.value)
        assertEquals(MunchieStage.HUNGRY, repository.munchieStage.value)

        // Both dashboard and overlay consume these exact StateFlows
        assertEquals(repository.todayCount.value, repository.todayCount.first())
        assertEquals(repository.munchieStage.value, repository.munchieStage.first())
    }
}
