package com.reelmonster.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.PurpleLight
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.domain.model.DailySummary

/**
 * HistoricalActivityChart
 *
 * Interactive segmented bar chart visualizing short-form video consumption.
 * Displays stacked Instagram vs YouTube activity per day, zero-activity days,
 * and highlights the selected day with click-to-inspect support.
 */
@Composable
fun HistoricalActivityChart(
    dailySeries: List<DailySummary>,
    selectedDate: String?,
    onSelectDay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxCount = dailySeries.maxOfOrNull { it.totalReels }?.coerceAtLeast(1) ?: 1
    val isScrollable = dailySeries.size > 14
    val scrollState = rememberScrollState()

    // Auto-scroll to end (most recent day) when series changes
    LaunchedEffect(dailySeries.size) {
        if (isScrollable) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Volume (${dailySeries.size} days)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PurpleAccent))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Instagram", fontSize = 11.sp, color = TextMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(YouTubeRed))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "YouTube", fontSize = 11.sp, color = TextMuted)
                }
            }
        }

        // Bar Chart Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (isScrollable) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth()),
                horizontalArrangement = if (isScrollable) Arrangement.spacedBy(6.dp) else Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dailySeries.forEachIndexed { index, daily ->
                    val isSelected = daily.dateString == selectedDate
                    val dayNum = daily.dateString.takeLast(2).toIntOrNull() ?: (index + 1)
                    val showLabel = if (dailySeries.size <= 7) {
                        daily.dayOfWeek
                    } else if (dailySeries.size <= 14) {
                        "$dayNum"
                    } else {
                        // For 30 days: show every 5th day, 1st day, or selected day
                        if (index == 0 || index == dailySeries.lastIndex || dayNum % 5 == 0 || isSelected) {
                            "$dayNum"
                        } else {
                            ""
                        }
                    }

                    HistoricalBarItem(
                        daily = daily,
                        maxCount = maxCount,
                        isSelected = isSelected,
                        label = showLabel,
                        onClick = { onSelectDay(daily.dateString) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoricalBarItem(
    daily: DailySummary,
    maxCount: Int,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeightFraction = if (daily.totalReels > 0) {
        (daily.totalReels.toFloat() / maxCount).coerceIn(0.12f, 1.0f)
    } else {
        0.04f
    }

    val igFraction = if (daily.totalReels > 0) {
        daily.instagramCount.toFloat() / daily.totalReels
    } else 0f

    val ytFraction = if (daily.totalReels > 0) {
        daily.youtubeCount.toFloat() / daily.totalReels
    } else 0f

    Column(
        modifier = modifier
            .width(if (isSelected) 26.dp else 22.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Count bubble on selected bar
        if (isSelected && daily.totalReels > 0) {
            Text(
                text = "${daily.totalReels}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Stacked Bar Container
        Box(
            modifier = Modifier
                .width(if (isSelected) 14.dp else 10.dp)
                .fillMaxHeight(barHeightFraction)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(if (daily.totalReels == 0) Color(0xFFE5E7EB) else Color.Transparent)
                .then(
                    if (isSelected) {
                        Modifier.border(1.5.dp, MintPrimaryDark, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    } else Modifier
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (daily.totalReels > 0) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    // YouTube segment on top
                    if (ytFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(ytFraction)
                                .background(YouTubeRed)
                        )
                    }
                    // Instagram segment on bottom
                    if (igFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(igFraction)
                                .background(PurpleAccent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Axis Day Label
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected || daily.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MintPrimaryDark else if (daily.isToday) TextPrimary else TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
