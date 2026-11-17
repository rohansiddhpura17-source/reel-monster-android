package com.reelmonster.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.PurpleDark
import com.reelmonster.app.core.design.PurpleLight
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.HistoryDateRange
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.presentation.components.AppCard
import com.reelmonster.app.presentation.components.EmptyState
import com.reelmonster.app.presentation.components.HistoricalActivityChart
import com.reelmonster.app.presentation.components.ReelMonsterTopBar
import com.reelmonster.app.presentation.components.StatCard
import java.util.Locale

@Composable
fun HistoryScreen(
    repository: ReelRepository,
    modifier: Modifier = Modifier
) {
    val selectedRange by repository.selectedHistoryRange.collectAsState()
    val stats by repository.historyPeriodStats.collectAsState()
    val selectedDayDate by repository.selectedDayDate.collectAsState()
    val selectedDayEvents by repository.selectedDayEvents.collectAsState()

    val scrollState = rememberScrollState()
    val rangeChipsScrollState = rememberScrollState()

    // Phase 15: actual measured viewing time (proposal §5). getViewingTimeForDate
    // returns null when a day has no recorded sessions — never zero-filled.
    var periodViewing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var selectedDayActualMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(stats) {
        periodViewing = withContext(Dispatchers.IO) {
            var total = 0L
            var missing = 0
            stats.dailySeries.forEach { day ->
                val ms = repository.dbHelper.getViewingTimeForDate(day.dateString)
                if (ms == null) missing++ else total += ms
            }
            total to missing
        }
    }
    LaunchedEffect(selectedDayDate) {
        selectedDayActualMs = withContext(Dispatchers.IO) {
            selectedDayDate?.let { repository.dbHelper.getViewingTimeForDate(it) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        ReelMonsterTopBar(title = "History")

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Header Pill & Title
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "• TRACKING HISTORY • LOCAL DATABASE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tracking History",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Explore historical Reel velocity, platform split, and actual observed viewing time.",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // DATE RANGE SELECTOR (Horizontal filter chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rangeChipsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryDateRange.entries.forEach { range ->
                    val isSelected = range == selectedRange
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MintPrimary else Color.White)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MintPrimaryDark else Color(0xFFE5E7EB),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { repository.selectHistoryDateRange(range) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Current Range Subheader Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MintPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stats.interval.formattedLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${stats.totalDaysCount} calendar days",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // STATS ROW 1: Total Reels & Viewing Time (Phase 15: actual measured time)
            val pv = periodViewing
            val periodViewingText = when {
                pv == null -> "Loading…"
                pv.second >= stats.totalDaysCount -> "Unavailable"
                else -> DateTimeUtils.formatDurationMs(pv.first)
            }
            val periodViewingSubtitle = when {
                pv == null -> "Measured viewing time"
                pv.second == 0 -> "Measured viewing time"
                else -> "${pv.second} of ${stats.totalDaysCount} day(s) unmeasured"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Total Reels",
                    value = stats.totalReels.toString(),
                    subtitle = "${stats.instagramCount} IG · ${stats.youtubeCount} YT",
                    icon = Icons.Default.PlayArrow,
                    iconTint = MintPrimaryDark,
                    iconBackground = MintSurface,
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    title = "Viewing Time",
                    value = periodViewingText,
                    subtitle = periodViewingSubtitle,
                    icon = Icons.Default.Schedule,
                    iconTint = PurpleAccent,
                    iconBackground = PurpleLight,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // STATS ROW 2: Daily Average & Peak Activity Day
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val dailyAvgStr = String.format(Locale.US, "%.1f", stats.dailyAverage)
                StatCard(
                    title = "Daily Average",
                    value = "$dailyAvgStr / day",
                    subtitle = "${stats.activeDaysCount} active days",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = Color(0xFF0284C7),
                    iconBackground = Color(0xFFE0F2FE),
                    modifier = Modifier.weight(1f)
                )

                val highestDayStr = stats.highestDay?.let {
                    "${it.totalReels} (${DateTimeUtils.formatDisplayDate(it.dateString)})"
                } ?: "None"

                StatCard(
                    title = "Peak Day",
                    value = stats.highestDay?.totalReels?.toString() ?: "0",
                    subtitle = stats.highestDay?.let { DateTimeUtils.formatDisplayDate(it.dateString) } ?: "No reels logged",
                    icon = Icons.Default.Insights,
                    iconTint = Color(0xFFD97706),
                    iconBackground = Color(0xFFFEF3C7),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PLATFORM BREAKDOWN CARD
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Platform Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )

                        Text(
                            text = if (stats.totalReels > 0) {
                                "${stats.instagramPercentage}% IG / ${stats.youtubePercentage}% YT"
                            } else {
                                "No Activity"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MintPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dual platform progress bar
                    if (stats.totalReels > 0) {
                        val igProgress = (stats.instagramCount.toFloat() / stats.totalReels).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(YouTubeRed)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(igProgress)
                                    .height(10.dp)
                                    .background(PurpleAccent)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFE5E7EB))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PurpleAccent))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Instagram Reels: ${stats.instagramCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(YouTubeRed))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YouTube Shorts: ${stats.youtubeCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // HISTORICAL ACTIVITY CHART CARD
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Activity Volume",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap any day to inspect individual recorded timestamps.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (stats.totalReels == 0) {
                        EmptyState(
                            title = "No Activity Recorded",
                            description = "No reels logged during this period. Zero drift detected!",
                            showMunchie = true,
                            stage = MunchieStage.BABY
                        )
                    } else {
                        HistoricalActivityChart(
                            dailySeries = stats.dailySeries,
                            selectedDate = selectedDayDate,
                            onSelectDay = { repository.selectDay(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DAILY DETAIL INSPECTION VIEW
            if (selectedDayDate != null) {
                val selectedDaySummary = stats.dailySeries.firstOrNull { it.dateString == selectedDayDate }
                AppCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = DateTimeUtils.formatFullDate(selectedDayDate!!),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (selectedDaySummary?.isToday == true) "Today's log" else "Past session record",
                                    fontSize = 11.sp,
                                    color = MintPrimaryDark
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MintSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${selectedDaySummary?.totalReels ?: 0} Reels",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Daily sub-stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📷 IG: ${selectedDaySummary?.instagramCount ?: 0}",
                                fontSize = 12.sp,
                                color = PurpleDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "▶️ YT: ${selectedDaySummary?.youtubeCount ?: 0}",
                                fontSize = 12.sp,
                                color = YouTubeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "⏱️ ${selectedDayActualMs?.let { DateTimeUtils.formatDurationMs(it) + " watched" } ?: "unmeasured"}",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Individual Recorded Entries (${selectedDayEvents.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedDayEvents.isEmpty()) {
                            Text(
                                text = "No individual timestamps recorded for this day.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedDayEvents.forEach { event ->
                                    val isIg = event.targetApp == TargetApp.INSTAGRAM
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isIg) PurpleLight.copy(alpha = 0.4f) else YouTubeRedLight.copy(alpha = 0.4f))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = if (isIg) "📷" else "▶️", fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "${event.targetApp.displayName} ${event.targetApp.mediaType}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = DateTimeUtils.formatTime(event.timestamp),
                                                    fontSize = 10.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        Text(
                                            text = "+1 Chomp",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isIg) PurpleDark else YouTubeRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // INSIGHTS SECTION (Factual, objective observations)
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = null,
                                tint = MintPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Factual Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.insights.forEach { insight ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = insight,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
