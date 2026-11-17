package com.reelmonster.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryBright
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.domain.model.DailySummary

/**
 * SevenDayActivityChart
 *
 * Visual histogram displaying short-form video tracking velocity over the last 7 calendar days.
 * Displays daily counts, zero-activity days, today highlighting, and binge spike alerts.
 */
@Composable
fun SevenDayActivityChart(
    sevenDayActivity: List<DailySummary>,
    modifier: Modifier = Modifier
) {
    val totalReels = sevenDayActivity.sumOf { it.totalReels }
    // Phase 15: no count x 1.5 min estimates. This chart shows counts only;
    // actual viewing time is shown on the Stats screen, from recorded sessions.
    val activeDays = sevenDayActivity.count { it.totalReels > 0 }
    val maxCount = sevenDayActivity.maxOfOrNull { it.totalReels }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary metrics bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MintSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MintCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "7-Day Total",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "$totalReels Reels",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Active Days",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "$activeDays of 7 days",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (totalReels == 0) {
            // Friendly zero state when no reels have been recorded across all 7 days
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🌱", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No Reels Logged This Week",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    )
                    Text(
                        text = "Your calendar is completely clean. Munchie is calm and well-rested.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        } else {
            // Visual 7-day Bar Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                sevenDayActivity.forEach { daily ->
                    val fraction = if (daily.totalReels > 0) {
                        (daily.totalReels.toFloat() / maxCount).coerceIn(0.12f, 1.0f)
                    } else {
                        0.04f
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Count text above bar
                        Text(
                            text = if (daily.totalReels > 0) daily.totalReels.toString() else "—",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (daily.isToday) FontWeight.Bold else FontWeight.Medium,
                                color = if (daily.isToday) MintPrimaryDark else TextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Vertical bar container
                        Box(
                            modifier = Modifier
                                .height(95.dp)
                                .width(24.dp)
                                .background(
                                    color = if (daily.isToday) MintSurface.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val barColor = when {
                                daily.isBingeSpike -> YouTubeRed
                                daily.isToday -> MintPrimaryDark
                                daily.totalReels > 0 -> MintPrimary
                                else -> Color(0xFFE5E7EB)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(barColor)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day of week label
                        Text(
                            text = daily.dayOfWeek,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (daily.isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (daily.isToday) MintPrimaryDark else TextSecondary
                            )
                        )

                        // Indicator dot for today
                        if (daily.isToday) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MintPrimaryDark, CircleShape)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Today legend
                Box(modifier = Modifier.size(8.dp).background(MintPrimaryDark, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Today", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))

                Spacer(modifier = Modifier.width(16.dp))

                // Normal day legend
                Box(modifier = Modifier.size(8.dp).background(MintPrimary, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Regular", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))

                Spacer(modifier = Modifier.width(16.dp))

                // Binge spike legend
                Box(modifier = Modifier.size(8.dp).background(YouTubeRed, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Binge (≥40)", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
            }
        }
    }
}
