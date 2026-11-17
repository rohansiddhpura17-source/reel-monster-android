package com.reelmonster.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryBright
import com.reelmonster.app.core.design.OrangeAccent
import com.reelmonster.app.core.design.PurpleAccent
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class BurstParticle(
    val angle: Double,
    val speed: Float,
    val radius: Float,
    val color: Color
)

/**
 * ParticleBurst
 *
 * Lightweight Compose Canvas particle effect for Munchie's chomp reaction.
 * - Spawns 8–12 colored circles with radial velocity and parabolic gravity.
 * - Lifetime: approximately 400ms.
 * - Releases resources immediately upon completion; zero continuous redraw when idle.
 */
@Composable
fun ParticleBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    particleCount: Int = 10,
    burstColors: List<Color> = listOf(MintPrimary, MintPrimaryBright, PurpleAccent, OrangeAccent)
) {
    var lastTrigger by remember { mutableIntStateOf(trigger) }
    var particles by remember { mutableStateOf<List<BurstParticle>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger > 0 && trigger != lastTrigger) {
            lastTrigger = trigger
            val rnd = Random(trigger)
            particles = List(particleCount.coerceIn(8, 12)) {
                val angle = rnd.nextDouble(0.0, Math.PI * 2)
                val speed = rnd.nextFloat() * 65f + 35f
                val radius = rnd.nextFloat() * 2.5f + 2.5f
                val color = burstColors[rnd.nextInt(burstColors.size)]
                BurstParticle(angle, speed, radius, color)
            }
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = LinearOutSlowInEasing)
            )
            particles = emptyList() // Clean release
        }
    }

    if (particles.isNotEmpty() && progress.value < 1f) {
        val p = progress.value
        Canvas(modifier = modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val gravity = 35f * p * p

            for (particle in particles) {
                val dist = particle.speed * p
                val px = cx + (cos(particle.angle) * dist).toFloat()
                val py = cy + (sin(particle.angle) * dist).toFloat() + gravity
                val alpha = (1f - p).coerceIn(0f, 1f)

                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = (particle.radius * (1f - p * 0.3f)).coerceAtLeast(0.5f),
                    center = Offset(px, py)
                )
            }
        }
    }
}
