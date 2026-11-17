package com.reelmonster.app.presentation.focus

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusStatus
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.presentation.components.AppCard
import com.reelmonster.app.presentation.components.PrimaryButton
import com.reelmonster.app.presentation.components.SecondaryButton
import com.reelmonster.app.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(
    repository: ReelRepository,
    onNavigateBack: () -> Unit
) {
    val progress by repository.activeFocusProgress.collectAsState()
    val todayFocusMs by repository.todayFocusTimeMs.collectAsState()
    val todayCompletedCount by repository.todayFocusCompletedCount.collectAsState()
    val weeklyStats by repository.weeklyFocusStats.collectAsState()
    val recentSessions by repository.recentFocusSessions.collectAsState()
    val isFocusOverlayEnabled by repository.preferencesManager.isFocusOverlayEnabled.collectAsState()
    val reminderCooldownMins by repository.preferencesManager.focusReminderCooldownMins.collectAsState()
    val interventionReminder by repository.focusInterventionReminder.collectAsState()

    var selectedType by remember { mutableStateOf(FocusType.DEEP_FOCUS) }
    var selectedMinutes by remember { mutableIntStateOf(25) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Focus Mode",
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

            // 1. Mindful Focus Hero Banner
            AppCard(containerColor = PurpleLight.copy(alpha = 0.5f), borderColor = PurpleAccent.copy(alpha = 0.3f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(PurpleLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mindful Productivity",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Set intentional focus blocks to stay present and protect your deep attention.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Optional Gentle Intervention Alert
            if (interventionReminder != null) {
                Spacer(modifier = Modifier.height(14.dp))
                AppCard(containerColor = WarningAmberLight, borderColor = WarningAmber) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = interventionReminder!!.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = interventionReminder!!.message,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { repository.dismissFocusIntervention() }) {
                                Text("Dismiss", color = MintPrimaryDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Active Focus Timer Card
            SectionHeader(title = "Focus Timer", subtitle = "Current session status & countdown")
            AppCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (progress.status.isActive) progress.type.label else selectedType.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        FocusStatusBadge(status = progress.status)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Countdown Display
                    val displayTime = if (progress.status.isActive) {
                        progress.formattedRemaining
                    } else {
                        String.format("%02d:00", selectedMinutes)
                    }

                    Text(
                        text = displayTime,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (progress.status == FocusStatus.PAUSED) WarningAmber else MintPrimaryDark,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = if (progress.status.isActive) {
                            "Target: ${progress.targetDurationMs / 60_000L} mins • ${progress.progressPercentage}% elapsed"
                        } else {
                            "Ready to begin • $selectedMinutes mins target"
                        },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = {
                            if (progress.status.isActive) {
                                (progress.progressPercentage / 100f).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (progress.status == FocusStatus.PAUSED) WarningAmber else MintPrimary,
                        trackColor = Color(0xFFE5E7EB)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Interactive Controls
                    if (!progress.status.isActive) {
                        // Preset Selector
                        Text(
                            text = "Choose Session Length",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "5m Break",
                                selected = selectedType == FocusType.QUICK_BREAK && selectedMinutes == 5,
                                onClick = {
                                    selectedType = FocusType.QUICK_BREAK
                                    selectedMinutes = 5
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PresetChip(
                                label = "15m Break",
                                selected = selectedType == FocusType.QUICK_BREAK && selectedMinutes == 15,
                                onClick = {
                                    selectedType = FocusType.QUICK_BREAK
                                    selectedMinutes = 15
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PresetChip(
                                label = "25m Focus",
                                selected = selectedType == FocusType.DEEP_FOCUS && selectedMinutes == 25,
                                onClick = {
                                    selectedType = FocusType.DEEP_FOCUS
                                    selectedMinutes = 25
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PresetChip(
                                label = "45m Focus",
                                selected = selectedType == FocusType.DEEP_FOCUS && selectedMinutes == 45,
                                onClick = {
                                    selectedType = FocusType.DEEP_FOCUS
                                    selectedMinutes = 45
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Stepper
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MintSurface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Duration",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        selectedType = FocusType.CUSTOM
                                        selectedMinutes = (selectedMinutes - 5).coerceAtLeast(5)
                                    },
                                    enabled = selectedMinutes > 5
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MintPrimaryDark)
                                }
                                Text(
                                    text = "$selectedMinutes mins",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = {
                                        selectedType = FocusType.CUSTOM
                                        selectedMinutes = (selectedMinutes + 5).coerceAtMost(180)
                                    },
                                    enabled = selectedMinutes < 180
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = MintPrimaryDark)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        PrimaryButton(
                            text = "Start ${selectedType.label}",
                            onClick = { repository.startFocusSession(selectedType, selectedMinutes) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (progress.status == FocusStatus.RUNNING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SecondaryButton(
                                text = "Pause",
                                onClick = { repository.pauseFocusSession() },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryButton(
                                text = "Complete",
                                onClick = { repository.completeFocusSession() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { repository.cancelFocusSession() }) {
                            Text("Cancel Session", color = TextMuted, fontSize = 12.sp)
                        }
                    } else if (progress.status == FocusStatus.PAUSED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryButton(
                                text = "Resume",
                                onClick = { repository.resumeFocusSession() },
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                text = "Finish",
                                onClick = { repository.completeFocusSession() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { repository.cancelFocusSession() }) {
                            Text("Cancel Session", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Gentle Reminders & Interventions Configuration
            SectionHeader(title = "Gentle Interventions", subtitle = "Non-blocking guidance while focus is active")
            AppCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Floating Badge Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repository.preferencesManager.setFocusOverlayEnabled(!isFocusOverlayEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Focus Badge",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Display small focus pill next to Munchie's overlay",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isFocusOverlayEnabled,
                            onCheckedChange = { repository.preferencesManager.setFocusOverlayEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MintPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cooldown Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder Cooldown",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Minimum wait between gentle feed check-ins",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    repository.preferencesManager.setFocusReminderCooldownMins((reminderCooldownMins - 1).coerceAtLeast(1))
                                },
                                enabled = reminderCooldownMins > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MintPrimaryDark)
                            }
                            Text(
                                text = "${reminderCooldownMins}m",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimaryDark
                            )
                            IconButton(
                                onClick = {
                                    repository.preferencesManager.setFocusReminderCooldownMins((reminderCooldownMins + 1).coerceAtMost(15))
                                },
                                enabled = reminderCooldownMins < 15
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = MintPrimaryDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🔒 Reel Monster never forcibly locks or terminates social apps. Reminders are mindful, supportive, and 100% user-controlled.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Focus Statistics & History
            SectionHeader(title = "Focus Insights & History", subtitle = "Your mindful attention track record")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val todayMins = (todayFocusMs / 60_000L).toInt()
                StatBox(
                    title = "Today's Focus",
                    value = if (todayMins > 0) "${todayMins}m" else "0m",
                    subtitle = "$todayCompletedCount completed",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    title = "Past 7 Days",
                    value = weeklyStats.formattedTotalTime,
                    subtitle = "${weeklyStats.completedSessionsCount} sessions (${weeklyStats.completionRatePercentage}%)",
                    modifier = Modifier.weight(1f)
                )
            }

            if (recentSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                AppCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Recent Sessions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        recentSessions.take(5).forEachIndexed { index, session ->
                            RecentSessionRow(session = session)
                            if (index < recentSessions.take(5).size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FocusStatusBadge(status: FocusStatus) {
    val (bgColor, textColor, label) = when (status) {
        FocusStatus.IDLE -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "Ready")
        FocusStatus.RUNNING -> Triple(MintSurface, MintPrimaryDark, "Running")
        FocusStatus.PAUSED -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Paused")
        FocusStatus.COMPLETED -> Triple(MintSurface, MintPrimaryDark, "Completed")
        FocusStatus.CANCELLED -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "Cancelled")
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MintPrimary else Color.White)
            .border(1.dp, if (selected) MintPrimaryDark else MintCardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextPrimary
        )
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column {
            Text(text = title, fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun RecentSessionRow(session: FocusSession) {
    val durationMins = (session.actualDurationMs / 60_000L).toInt()
    val isSuccess = session.completionStatus == FocusSession.STATUS_COMPLETED

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isSuccess) MintPrimary else Color.Gray, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "${session.sessionType.label} ($durationMins mins)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = session.dateString,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
        Text(
            text = session.completionStatus,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSuccess) MintPrimaryDark else TextMuted
        )
    }
}
