package com.reelmonster.app.presentation.statistics

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.presentation.components.ReelMonsterTopBar

@Composable
fun StatisticsScreen(
    repository: ReelRepository,
    modifier: Modifier = Modifier
) {
    val count by repository.todayCount.collectAsState()
    val stage by repository.munchieStage.collectAsState()
    val platformBreakdown by repository.platformBreakdown.collectAsState()
    val last7Days by repository.last7Days.collectAsState()
    val todayAverageDurationMs by repository.todayAverageDurationMs.collectAsState()
    val todayPlatformViewingTimeMs by repository.todayPlatformViewingTimeMs.collectAsState()

    // Phase 15: actual measured viewing time (proposal §5). getViewingTimeForDate
    // returns null when a day has no recorded sessions — never zero-filled.
    var todayActualViewingMs by remember { mutableStateOf<Long?>(null) }
    var last7DaysActualMs by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    LaunchedEffect(count) {
        todayActualViewingMs = withContext(Dispatchers.IO) {
            repository.dbHelper.getViewingTimeForDate(DateTimeUtils.getTodayDateString())
        }
    }
    LaunchedEffect(last7Days) {
        last7DaysActualMs = withContext(Dispatchers.IO) {
            last7Days.associate { it.dateString to repository.dbHelper.getViewingTimeForDate(it.dateString) }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        ReelMonsterTopBar(title = "Stats")

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Header Pill & Title
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "• DAILY REPORT • LOCAL FIRST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Today's Statistics",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Honest reel breakdown and actual observed viewing time.",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs: Today vs Last 7 Days
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = MintPrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Today's Breakdown", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Last 7 Days", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // TODAY'S BREAKDOWN CARDS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stage.stageName} (${stage.appetiteLabel})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MintSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Stage ${stage.level}/5",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stage.defaultQuote,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Total Watched", fontSize = 11.sp, color = TextMuted)
                                Text(text = "$count reels", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column {
                                Text(text = "Viewing Time", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = todayActualViewingMs?.let { DateTimeUtils.formatDurationMs(it) } ?: "Unavailable",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YouTubeRed
                                )
                            }
                            Column {
                                Text(text = "Avg. Dwell", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = if (todayAverageDurationMs > 0L) DateTimeUtils.formatDurationMs(todayAverageDurationMs) else "—",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Platform specific cards
                val (igCount, ytCount) = platformBreakdown

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PurpleLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📷", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Instagram Reels", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "$igCount watched (${platformViewingText(igCount, todayPlatformViewingTimeMs.first)})", fontSize = 12.sp, color = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeRedLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "▶️", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "YouTube Shorts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "$ytCount watched (${platformViewingText(ytCount, todayPlatformViewingTimeMs.second)})", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }
            } else {
                // LAST 7 DAYS VIEW
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Scroll Velocity",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Historical session records preserved across reboots.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (last7Days.isEmpty()) {
                            com.reelmonster.app.presentation.components.EmptyState(
                                title = "No 7-Day History Yet",
                                description = "Start scrolling with Munchie to populate 7-day velocity analytics!",
                                showMunchie = false,
                                icon = Icons.Default.CalendarToday
                            )
                        } else {
                            last7Days.forEach { summary ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (summary.isToday) "${summary.dateString} (Today)" else summary.dateString,
                                        fontSize = 13.sp,
                                        fontWeight = if (summary.isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (summary.isToday) MintPrimaryDark else TextPrimary
                                    )
                                    Text(
                                        text = "${summary.totalReels} reels (${last7DaysActualMs[summary.dateString]?.let { DateTimeUtils.formatDurationMs(it) } ?: "unmeasured"})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (summary.isBingeSpike) YouTubeRed else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Phase 15: measured per-platform viewing time; unmeasured days are labelled, never zero-filled. */
private fun platformViewingText(count: Int, viewingMs: Long): String {
    if (count == 0) return "—"
    return if (viewingMs > 0L) DateTimeUtils.formatDurationMs(viewingMs) else "unmeasured"
}
