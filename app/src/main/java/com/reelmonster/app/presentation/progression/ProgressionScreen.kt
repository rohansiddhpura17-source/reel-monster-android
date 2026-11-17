package com.reelmonster.app.presentation.progression

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
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
import com.reelmonster.app.core.design.MintBackground
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.PurpleAccent
import com.reelmonster.app.core.design.PurpleLight
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.WarningAmberLight
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.monitoring.EstimatedCoverageStatus
import com.reelmonster.app.domain.model.progression.AchievementCategory
import com.reelmonster.app.domain.model.progression.LevelCalculator
import com.reelmonster.app.presentation.components.AppCard
import com.reelmonster.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionScreen(
    repository: ReelRepository,
    onNavigateBack: () -> Unit
) {
    val userProgress by repository.userProgress.collectAsState()
    val achievements by repository.achievements.collectAsState()
    val recentXp by repository.recentXpTransactions.collectAsState()
    val streakRecords by repository.streakRecords.collectAsState()
    val todayCoverage by repository.todayMonitoringCoverage.collectAsState()

    var selectedCategory by remember { mutableStateOf<AchievementCategory?>(null) }
    var showLimitationsNote by remember { mutableStateOf(false) }

    val unlockedCount = achievements.count { it.isUnlocked }
    val filteredAchievements = remember(achievements, selectedCategory) {
        if (selectedCategory == null) achievements
        else achievements.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Progression & Streaks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        },
        containerColor = AppBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. Level & XP Header Card
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(PurpleLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stars,
                                    contentDescription = null,
                                    tint = PurpleAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Level ${userProgress.currentLevel}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurpleAccent
                                )
                                Text(
                                    text = userProgress.levelTitle,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MintBackground)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${userProgress.totalXp} XP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MintPrimaryDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { userProgress.levelProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MintPrimaryDark,
                        trackColor = MintSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val nextXp = userProgress.nextLevelThresholdXp
                        Text(
                            text = if (nextXp != null) {
                                "${userProgress.xpRemainingToNextLevel} XP to Level ${userProgress.currentLevel + 1}"
                            } else {
                                "Max Level 10 Reached!"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = "${userProgress.levelProgressPercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "XP rewards healthy choices (focus sessions, staying within daily limits, and mindful days) — never scrolling.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Mindful Streaks Card
            SectionHeader(title = "Mindful Streak")
            Spacer(modifier = Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmberLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${userProgress.currentStreak} Day Streak",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Longest streak: ${userProgress.longestStreak} days",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Milestone pills
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3 to "+50", 7 to "+100", 30 to "+250").forEach { (days, reward) ->
                                val reached = userProgress.longestStreak >= days
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (reached) MintBackground else MintSurface)
                                        .border(
                                            width = 1.dp,
                                            color = if (reached) MintPrimaryDark else MintCardBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${days}d $reward",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reached) MintPrimaryDark else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    if (streakRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recent Finalized Days",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        streakRecords.takeLast(5).reversed().forEach { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rec.localDate,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (rec.isQualified) "Qualified (${rec.qualifyingReason})" else "Disqualified (${rec.qualifyingReason})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (rec.isQualified) MintPrimaryDark else TextMuted
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Past calendar days are evaluated after local midnight once ≥ 2h of Estimated Monitoring Coverage is completed.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Estimated Monitoring Coverage Card (Section 3.5)
            SectionHeader(title = "Estimated Monitoring Coverage")
            Spacer(modifier = Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (todayCoverage.isQualifiedForProgression) MintPrimaryDark else PurpleAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = todayCoverage.coverageStatus.badgeLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "${todayCoverage.estimatedVerifiedMinutes} / 120 mins",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { todayCoverage.qualificationProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MintPrimaryDark,
                        trackColor = MintSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CoverageMetricItem(
                            label = "Service Connected",
                            value = DateTimeUtils.formatDurationMs(todayCoverage.serviceConnectedMs)
                        )
                        CoverageMetricItem(
                            label = "Tracking Enabled",
                            value = DateTimeUtils.formatDurationMs(todayCoverage.trackingEnabledMs)
                        )
                        CoverageMetricItem(
                            label = "Screen-On",
                            value = DateTimeUtils.formatDurationMs(todayCoverage.screenOnMs)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLimitationsNote = !showLimitationsNote }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showLimitationsNote) "Hide Android Coverage Notes" else "About Estimated Monitoring Coverage & Android Limitations",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PurpleAccent
                        )
                    }

                    if (showLimitationsNote) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MintSurface)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "• A day qualifies when accumulated Estimated Monitoring Coverage is at least 2 hours (120 minutes).\n" +
                                    "• Heartbeats validate continuity only and never replace the 2-hour requirement.\n" +
                                    "• Android may interrupt or restrict background AccessibilityService execution.\n" +
                                    "• Service connection does not guarantee that every supported-app screen transition was detected.\n" +
                                    "• App updates, UI changes, or delayed screen-state events may affect detection.\n" +
                                    "• Monitoring coverage is an estimate of eligible observation time, not proof of complete activity capture.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Achievements Gallery
            SectionHeader(title = "Achievements ($unlockedCount / ${achievements.size})")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryFilterChip(
                    label = "All",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
                AchievementCategory.entries.forEach { cat ->
                    CategoryFilterChip(
                        label = cat.displayName,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            filteredAchievements.forEach { ach ->
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (ach.isUnlocked) MintBackground else MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ach.isUnlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (ach.isUnlocked) MintPrimaryDark else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ach.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "+${ach.xpReward} XP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ach.isUnlocked) MintPrimaryDark else PurpleAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = ach.description,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    progress = { ach.progressFraction },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (ach.isUnlocked) MintPrimaryDark else PurpleAccent,
                                    trackColor = MintSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${ach.currentProgress}/${ach.targetProgress}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Level Roadmap Overview
            SectionHeader(title = "Level Roadmap")
            Spacer(modifier = Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LevelCalculator.TIERS.forEach { tier ->
                        val reached = userProgress.totalXp >= tier.minXp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (reached) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (reached) MintPrimaryDark else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lv. ${tier.level} — ${tier.title}",
                                    fontSize = 13.sp,
                                    fontWeight = if (userProgress.currentLevel == tier.level) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (reached) TextPrimary else TextMuted
                                )
                            }
                            Text(
                                text = "${tier.minXp} XP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (reached) MintPrimaryDark else TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Recent XP Transactions Log
            SectionHeader(title = "Recent XP Activity")
            Spacer(modifier = Modifier.height(8.dp))
            AppCard {
                if (recentXp.isEmpty()) {
                    Text(
                        text = "No XP earned yet. Complete a focus session or finish a monitored day within your daily goals to earn your first XP!",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        recentXp.take(15).forEach { tx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.description,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = tx.localDate,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = "+${tx.xpAmount} XP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MintPrimaryDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CoverageMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MintPrimaryDark else MintSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}
