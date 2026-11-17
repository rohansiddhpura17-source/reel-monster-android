package com.reelmonster.app.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = MintPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSurface,
    onPrimaryContainer = MintPrimaryDark,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = PurpleLight,
    onSecondaryContainer = PurpleDark,
    background = MintBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = MintSurface,
    onSurfaceVariant = TextSecondary,
    outline = MintCardBorder,
    error = YouTubeRed,
    onError = Color.White
)

@Composable
fun ReelMonsterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The Stitch design system is predominantly mint-light aesthetic
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ReelMonsterTypography,
        content = content
    )
}
