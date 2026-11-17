package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.ReactionStyle
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserPreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: UserPreferencesManager

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
    }

    @Test
    fun testTargetAppMapping() {
        assertEquals(TargetApp.INSTAGRAM, TargetApp.fromPackage("com.instagram.android"))
        assertEquals(TargetApp.YOUTUBE, TargetApp.fromPackage("com.google.android.youtube"))
        assertNull(TargetApp.fromPackage("com.example.randomapp"))
        assertNull(TargetApp.fromPackage("com.zhiliaoapp.musically")) // TikTok must be null / unsupported
    }

    @Test
    fun testTrackingModeLabels() {
        assertEquals("Level 1: Manual (+1 Chomp)", TrackingMode.LEVEL_1_MANUAL.label)
        assertEquals("Level 1 (Manual)", TrackingMode.LEVEL_1_MANUAL.shortLabel)
        assertEquals("Level 2: Automatic Detection", TrackingMode.LEVEL_2_AUTOMATIC.label)
        assertEquals("Level 2 (Auto)", TrackingMode.LEVEL_2_AUTOMATIC.shortLabel)
    }

    @Test
    fun testDefaultPreferences() {
        assertEquals(TrackingMode.LEVEL_1_MANUAL, preferencesManager.trackingMode.value)
        assertTrue(preferencesManager.monitorInstagram.value)
        assertTrue(preferencesManager.monitorYouTube.value)
        assertTrue(preferencesManager.isAppMonitored(TargetApp.INSTAGRAM))
        assertTrue(preferencesManager.isAppMonitored(TargetApp.YOUTUBE))
        assertTrue(preferencesManager.isOverlayEnabled.value)
        assertFalse(preferencesManager.isSoundFxEnabled.value)
        assertFalse(preferencesManager.isHapticEnabled.value)
        assertTrue(preferencesManager.isEdgeSnappingEnabled.value)
        assertEquals(MunchieTheme.MINT_CLASSIC, preferencesManager.monsterTheme.value)
        assertEquals(ReactionStyle.BOUNCE, preferencesManager.reactionStyle.value)
        assertTrue(preferencesManager.isMascotAnimationEnabled.value)
    }

    @Test
    fun testTrackingModePreferences() = runTest {
        preferencesManager.updateTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
        assertEquals(TrackingMode.LEVEL_2_AUTOMATIC, preferencesManager.trackingModeFlow.first())

        preferencesManager.updateTrackingMode(TrackingMode.LEVEL_1_MANUAL)
        assertEquals(TrackingMode.LEVEL_1_MANUAL, preferencesManager.trackingModeFlow.first())
    }

    @Test
    fun testSelectedAppPreferences() = runTest {
        // Toggle Instagram off
        preferencesManager.updateMonitorInstagram(false)
        assertFalse(preferencesManager.monitorInstagramFlow.first())
        assertTrue(preferencesManager.monitorYouTubeFlow.first())

        // Toggle Instagram back on
        preferencesManager.updateMonitorInstagram(true)
        assertTrue(preferencesManager.monitorInstagramFlow.first())
        assertTrue(preferencesManager.monitorYouTubeFlow.first())

        // Toggle YouTube off
        preferencesManager.updateMonitorYouTube(false)
        assertFalse(preferencesManager.monitorYouTubeFlow.first())
        assertTrue(preferencesManager.monitorInstagramFlow.first())

        // Restore
        preferencesManager.updateMonitorYouTube(true)
        assertTrue(preferencesManager.monitorYouTubeFlow.first())
    }

    @Test
    fun testSensoryPreferences() = runTest {
        // Default is false
        assertFalse(preferencesManager.isSoundFxEnabledFlow.first())
        assertFalse(preferencesManager.isHapticEnabledFlow.first())

        // Enable
        preferencesManager.updateSoundFxEnabled(true)
        preferencesManager.updateHapticEnabled(true)
        preferencesManager.updateEdgeSnappingEnabled(false)
        assertTrue(preferencesManager.isSoundFxEnabledFlow.first())
        assertTrue(preferencesManager.isHapticEnabledFlow.first())
        assertFalse(preferencesManager.isEdgeSnappingEnabledFlow.first())

        // Disable again
        preferencesManager.updateSoundFxEnabled(false)
        preferencesManager.updateHapticEnabled(false)
        preferencesManager.updateEdgeSnappingEnabled(true)
        assertFalse(preferencesManager.isSoundFxEnabledFlow.first())
        assertFalse(preferencesManager.isHapticEnabledFlow.first())
        assertTrue(preferencesManager.isEdgeSnappingEnabledFlow.first())
    }

    @Test
    fun testMonsterPersonalizationPreferences() = runTest {
        // Theme updates
        preferencesManager.updateMonsterTheme(MunchieTheme.BERRY_PURPLE)
        assertEquals(MunchieTheme.BERRY_PURPLE, preferencesManager.monsterThemeFlow.first())

        preferencesManager.updateMonsterTheme(MunchieTheme.SUNSHINE_ORANGE)
        assertEquals(MunchieTheme.SUNSHINE_ORANGE, preferencesManager.monsterThemeFlow.first())

        preferencesManager.updateMonsterTheme(MunchieTheme.OCEAN_BLUE)
        assertEquals(MunchieTheme.OCEAN_BLUE, preferencesManager.monsterThemeFlow.first())

        preferencesManager.updateMonsterTheme(MunchieTheme.MINT_CLASSIC)
        assertEquals(MunchieTheme.MINT_CLASSIC, preferencesManager.monsterThemeFlow.first())

        // Reaction style updates
        preferencesManager.updateReactionStyle(ReactionStyle.SQUASH_AND_STRETCH)
        assertEquals(ReactionStyle.SQUASH_AND_STRETCH, preferencesManager.reactionStyleFlow.first())

        preferencesManager.updateReactionStyle(ReactionStyle.WINK_AND_HAPPY)
        assertEquals(ReactionStyle.WINK_AND_HAPPY, preferencesManager.reactionStyleFlow.first())

        preferencesManager.updateReactionStyle(ReactionStyle.BOUNCE)
        assertEquals(ReactionStyle.BOUNCE, preferencesManager.reactionStyleFlow.first())

        // Mascot animation toggle
        preferencesManager.updateMascotAnimationEnabled(false)
        assertFalse(preferencesManager.isMascotAnimationEnabledFlow.first())

        preferencesManager.updateMascotAnimationEnabled(true)
        assertTrue(preferencesManager.isMascotAnimationEnabledFlow.first())
    }
}
