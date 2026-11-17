package com.reelmonster.app.domain.model

import androidx.compose.ui.graphics.Color

/**
 * MunchieTheme
 *
 * Visual color variations for Munchie mascot personalization.
 * Applied across all 5 evolution stages in the Dashboard and Floating Overlay.
 */
enum class MunchieTheme(
    val key: String,
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val hornColor: Color,
    val description: String
) {
    MINT_CLASSIC(
        key = "mint_classic",
        displayName = "Mint Munchie",
        primaryColor = Color(0xFF76D7A5),
        secondaryColor = Color(0xFFEDFAF4),
        hornColor = Color(0xFF247A58),
        description = "Canonical Stitch mint identity"
    ),
    BERRY_PURPLE(
        key = "berry_purple",
        displayName = "Berry Munchie",
        primaryColor = Color(0xFF8B5CF6),
        secondaryColor = Color(0xFFEDE9FE),
        hornColor = Color(0xFF5B21B6),
        description = "Vibrant grape dopamine berry"
    ),
    SUNSHINE_ORANGE(
        key = "sunshine_orange",
        displayName = "Sunny Munchie",
        primaryColor = Color(0xFFFF9800),
        secondaryColor = Color(0xFFFFF3E0),
        hornColor = Color(0xFFC2410C),
        description = "Warm energetic tangerine monster"
    ),
    OCEAN_BLUE(
        key = "ocean_blue",
        displayName = "Ocean Munchie",
        primaryColor = Color(0xFF0EA5E9),
        secondaryColor = Color(0xFFE0F2FE),
        hornColor = Color(0xFF0369A1),
        description = "Cool calming aqua monster"
    );

    fun getStageBodyColor(stage: MunchieStage): Color {
        return when (this) {
            MINT_CLASSIC -> when (stage) {
                MunchieStage.BABY -> Color(0xFF86EFAC)
                MunchieStage.CURIOUS -> Color(0xFF76D7A5)
                MunchieStage.HUNGRY -> Color(0xFF38C88A)
                MunchieStage.STUFFED -> Color(0xFF247A58)
                MunchieStage.MEGACHONK -> Color(0xFF0F3E2E)
            }
            BERRY_PURPLE -> when (stage) {
                MunchieStage.BABY -> Color(0xFFDDD6FE)
                MunchieStage.CURIOUS -> Color(0xFFA78BFA)
                MunchieStage.HUNGRY -> Color(0xFF8B5CF6)
                MunchieStage.STUFFED -> Color(0xFF6D28D9)
                MunchieStage.MEGACHONK -> Color(0xFF4C1D95)
            }
            SUNSHINE_ORANGE -> when (stage) {
                MunchieStage.BABY -> Color(0xFFFDE68A)
                MunchieStage.CURIOUS -> Color(0xFFFBBF24)
                MunchieStage.HUNGRY -> Color(0xFFF59E0B)
                MunchieStage.STUFFED -> Color(0xFFD97706)
                MunchieStage.MEGACHONK -> Color(0xFF92400E)
            }
            OCEAN_BLUE -> when (stage) {
                MunchieStage.BABY -> Color(0xFFBAE6FD)
                MunchieStage.CURIOUS -> Color(0xFF38BDF8)
                MunchieStage.HUNGRY -> Color(0xFF0EA5E9)
                MunchieStage.STUFFED -> Color(0xFF0284C7)
                MunchieStage.MEGACHONK -> Color(0xFF0369A1)
            }
        }
    }

    companion object {
        fun fromKey(key: String?): MunchieTheme {
            return entries.firstOrNull { it.key == key } ?: MINT_CLASSIC
        }
    }
}
