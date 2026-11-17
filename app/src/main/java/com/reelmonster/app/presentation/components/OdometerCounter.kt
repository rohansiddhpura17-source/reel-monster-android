package com.reelmonster.app.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * OdometerCounter
 *
 * Smooth, per-increment vertical odometer roll for Reel count numbers.
 * - Prevents replaying animation on app startup/recreation.
 * - Optimized for both small overlay badges and large dashboard StatCards.
 * - Avoids unnecessary recomposition when count is steady.
 */
@Composable
fun OdometerCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    var hasInitialized by remember { mutableStateOf(false) }
    var displayedCount by remember { mutableIntStateOf(count) }

    LaunchedEffect(Unit) {
        hasInitialized = true
    }

    LaunchedEffect(count) {
        displayedCount = count
    }

    AnimatedContent(
        targetState = displayedCount,
        transitionSpec = {
            if (!hasInitialized) {
                // Instant load on startup without replaying animations
                fadeIn(initialAlpha = 1f) togetherWith fadeOut(targetAlpha = 1f)
            } else if (targetState > initialState) {
                // Upward chomp roll on increment
                (slideInVertically { height -> height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> -height } + fadeOut())
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
        label = "odometer_roll",
        modifier = modifier
    ) { targetNum ->
        Text(
            text = targetNum.toString(),
            style = style,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
