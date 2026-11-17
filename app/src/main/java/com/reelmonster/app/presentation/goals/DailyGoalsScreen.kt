package com.reelmonster.app.presentation.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.AppBackground
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.goal.DailyGoalProgress
import com.reelmonster.app.domain.model.goal.GoalState
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.presentation.components.AppCard
import com.reelmonster.app.presentation.components.PrimaryButton
import com.reelmonster.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalsScreen(
    repository: ReelRepository,
    onNavigateBack: () -> Unit
) {
    val activeGoals by repository.activeGoals.collectAsState()
    val combinedCountProg by repository.combinedCountProgress.collectAsState()
    val combinedTimeProg by repository.combinedTimeProgress.collectAsState()
    val breakdown by repository.platformBreakdown.collectAsState()
    val platformTime by repository.todayPlatformViewingTimeMs.collectAsState()
    val successStats by repository.historicalGoalSuccessRate.collectAsState()

    var showConfigSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Daily Goals",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure Goals",
                            tint = MintPrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Overview Banner & Historical Compliance
            AppCard(containerColor = MintSurface, borderColor = MintCardBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MintPrimaryDark.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MintPrimaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mindful Daily Boundaries",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                        val successDays = successStats.first
                        val totalDays = successStats.second
                        Text(
                            text = if (totalDays > 0) {
                                "Boundary respected on $successDays of the last $totalDays evaluated days."
                            } else {
                                "Set intentional limits to keep your scroll time balanced."
                            },
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Combined Reel Count Goal
            SectionHeader(title = "Reel Count Pacing", subtitle = "Combined daily limit across all apps")
            if (combinedCountProg != null) {
                GoalProgressCard(progress = combinedCountProg!!)
            } else {
                EmptyGoalCard(title = "Reel count goal is currently disabled", onConfigure = { showConfigSheet = true })
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Combined Viewing Time Goal
            SectionHeader(title = "Observed Viewing Duration", subtitle = "Measured active time spent in feeds")
            if (combinedTimeProg != null) {
                GoalProgressCard(progress = combinedTimeProg!!)
            } else {
                EmptyGoalCard(title = "Viewing time goal is currently disabled", onConfigure = { showConfigSheet = true })
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Platform Breakdown
            SectionHeader(title = "Platform Distribution", subtitle = "Where today's count and time occurred")
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PlatformGoalRow(
                        platform = "Instagram Reels",
                        count = breakdown.first,
                        timeMs = platformTime.first,
                        accentColor = PurpleAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PlatformGoalRow(
                        platform = "YouTube Shorts",
                        count = breakdown.second,
                        timeMs = platformTime.second,
                        accentColor = YouTubeRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Edit Daily Goals",
                onClick = { showConfigSheet = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showConfigSheet) {
        GoalConfigurationSheet(
            activeGoals = activeGoals,
            onSaveGoal = { type, scope, target, enabled ->
                repository.setDailyGoal(type, scope, target, enabled)
            },
            onDismiss = { showConfigSheet = false }
        )
    }
}

@Composable
fun GoalProgressCard(progress: DailyGoalProgress) {
    AppCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.currentValue} / ${progress.goal.targetValue} ${progress.goal.type.unit}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                GoalStateBadge(state = progress.state)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            val progressColor = when (progress.state) {
                GoalState.EXCEEDED -> YouTubeRed
                GoalState.AT_LIMIT -> WarningAmber
                GoalState.APPROACHING_LIMIT -> Color(0xFFF59E0B)
                GoalState.PAUSED -> Color.LightGray
                else -> MintPrimary
            }

            LinearProgressIndicator(
                progress = { progress.usageRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = Color(0xFFE5E7EB),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.usagePercentage}% of daily boundary",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = progress.formattedRemaining,
                    fontSize = 12.sp,
                    color = if (progress.isLimitBreached) YouTubeRed else TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun GoalStateBadge(state: GoalState) {
    val (bgColor, textColor, icon) = when (state) {
        GoalState.NOT_STARTED -> Triple(Color(0xFFF3F4F6), Color(0xFF4B5563), Icons.Default.Info)
        GoalState.IN_PROGRESS -> Triple(MintSurface, MintPrimaryDark, Icons.Default.CheckCircle)
        GoalState.APPROACHING_LIMIT -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), Icons.Default.Warning)
        GoalState.AT_LIMIT -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), Icons.Default.Warning)
        GoalState.EXCEEDED -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), Icons.Default.Warning)
        GoalState.PAUSED -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), Icons.Default.PauseCircle)
    }

    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = state.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun PlatformGoalRow(
    platform: String,
    count: Int,
    timeMs: Long,
    accentColor: Color
) {
    val mins = (timeMs / 60_000L).toInt()
    val secs = ((timeMs % 60_000L) / 1000L).toInt()
    val timeLabel = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accentColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = platform,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        Text(
            text = "$count reels • $timeLabel",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
    }
}

@Composable
private fun EmptyGoalCard(title: String, onConfigure: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 13.sp, color = TextMuted)
            Text(
                text = "Enable",
                fontSize = 13.sp,
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
