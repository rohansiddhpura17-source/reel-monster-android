package com.reelmonster.app.presentation.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.weekly.HourlyBucket
import com.reelmonster.app.domain.model.weekly.HourlyCoverageStatus
import com.reelmonster.app.domain.model.weekly.MunchieMastery
import com.reelmonster.app.domain.model.weekly.MunchieMasteryCalculator
import com.reelmonster.app.domain.model.weekly.WeeklyDigest
import com.reelmonster.app.domain.usecase.WeeklyIntelligenceUseCase
import com.reelmonster.app.presentation.components.ErrorState
import com.reelmonster.app.presentation.components.LoadingState
import com.reelmonster.app.presentation.components.ReelMonsterTopBar
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 15 — Weekly Intelligence screen (proposal §§2–7).
 *
 * Shows, all computed locally from recorded data:
 * - The weekly digest: current vs previous seven complete days, with
 *   confidence and coverage-normalized comparisons.
 * - Weekday vs weekend comparison.
 * - The 24-hour scrolling pattern, with monitoring coverage shown
 *   next to every hour. Missing data is labelled, never shown as zero.
 * - Factual reflections (at most two).
 * - Munchie mastery cosmetics (level / streak only — never reel count).
 *
 * All database reads run on Dispatchers.IO (proposal §6).
 */
@Composable
fun WeeklyIntelligenceScreen(
    repository: ReelRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var digest by remember { mutableStateOf<WeeklyDigest?>(null) }
    var hourly by remember { mutableStateOf<List<HourlyBucket>>(emptyList()) }
    var mastery by remember { mutableStateOf<MunchieMastery?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        loadError = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val useCase = WeeklyIntelligenceUseCase(repository.dbHelper)
                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()
                val loadedDigest = useCase.buildWeeklyDigest(today)
                val loadedHourly = useCase.buildHourlyPattern(today, zone)
                val progress = repository.dbHelper.getUserProgress()
                val loadedMastery = MunchieMasteryCalculator.masteryFor(
                    level = progress.currentLevel,
                    streakDays = progress.currentStreak
                )
                Triple(loadedDigest, loadedHourly, loadedMastery)
            }
            digest = loaded.first
            hourly = loaded.second
            mastery = loaded.third
        } catch (e: Exception) {
            loadError = "Could not load weekly intelligence: ${e.message ?: "unknown error"}"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ReelMonsterTopBar(title = "Weekly Intelligence")

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onNavigateBack) {
                Text(text = "← Back to Home", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MintPrimaryDark)
            }

            Text(
                text = "Your week, observed",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Local-only analytics from recorded sessions. Missing data is shown as missing, never as zero.",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    LoadingState(
                        message = "Reading your week...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                loadError != null -> {
                    ErrorState(
                        message = loadError ?: "Failed to load weekly intelligence.",
                        onRetry = { reloadKey++ },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                digest != null -> {
                    WeeklyDigestContent(digest = digest!!, hourly = hourly, mastery = mastery)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WeeklyDigestContent(
    digest: WeeklyDigest,
    hourly: List<HourlyBucket>,
    mastery: MunchieMastery?
) {
    val current = digest.currentWindow
    val comparison = digest.comparison

    // Confidence banner
    SectionCard(title = "How complete is this week?") {
        Text(
            text = comparison.comparisonConfidence.displayLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "${current.adequateDays} of 7 days had enough monitoring data to compare.",
            fontSize = 13.sp,
            color = TextSecondary
        )
        if (comparison.percentagesSuppressed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Percentage comparisons are hidden until monitoring improves — totals below are still observed, not estimated.",
                fontSize = 12.sp,
                color = TextMuted
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Current vs previous week
    SectionCard(title = "This week vs last week") {
        ComparisonRow(
            label = "Reels",
            current = "${current.reelCount}",
            previous = "${digest.previousWindow.reelCount}",
            delta = comparison.reelRateDeltaPercent?.let { formatDelta(it) }
        )
        ComparisonRow(
            label = "Viewing time",
            current = DateTimeUtils.formatDurationMs(current.actualViewingMs),
            previous = DateTimeUtils.formatDurationMs(digest.previousWindow.actualViewingMs),
            delta = comparison.viewRateDeltaPercent?.let { formatDelta(it) }
        )
        ComparisonRow(
            label = "Focus time",
            current = DateTimeUtils.formatDurationMs(digest.focusMs),
            previous = DateTimeUtils.formatDurationMs(digest.previousWindow.focusMs),
            delta = null
        )
        if (current.missingViewingDays > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Viewing time unavailable for ${current.missingViewingDays} day(s) — shown as missing, not zero.",
                fontSize = 12.sp,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = comparison.summary, fontSize = 13.sp, color = TextSecondary)
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Weekday vs weekend
    SectionCard(title = "Weekday vs weekend") {
        val weekday = digest.weekdayWeekend.weekday
        val weekend = digest.weekdayWeekend.weekend
        ComparisonRow(
            label = "Weekday (${weekday.dayCount} days)",
            current = "${weekday.reelCount} reels",
            previous = weekday.reelsPerMonitoredHour?.let { "%.1f / monitored h".format(it) } ?: "Unmonitored",
            delta = null
        )
        ComparisonRow(
            label = "Weekend (${weekend.dayCount} days)",
            current = "${weekend.reelCount} reels",
            previous = weekend.reelsPerMonitoredHour?.let { "%.1f / monitored h".format(it) } ?: "Unmonitored",
            delta = null
        )
        Text(
            text = "Rates are per monitored hour, so a busier-looking day with little monitoring is not mistaken for heavier use.",
            fontSize = 12.sp,
            color = TextMuted
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Hourly pattern
    SectionCard(title = "Hourly scrolling pattern") {
        Text(
            text = "Reels and viewing time per hour, with monitoring coverage. Hours with no coverage are labelled Unmonitored.",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (hourly.isEmpty()) {
            Text(text = "No hourly data yet.", fontSize = 13.sp, color = TextMuted)
        } else {
            hourly.forEach { bucket ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%02d:00".format(bucket.hour),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${bucket.reelCount} reels · ${DateTimeUtils.formatDurationMs(bucket.actualViewingMs)}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = bucket.status.displayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (bucket.status == HourlyCoverageStatus.UNMONITORED) YouTubeRed else MintPrimaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { bucket.coverageRatio.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MintPrimaryDark,
                            trackColor = MintSurface
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Reflections
    SectionCard(title = "Reflections") {
        if (digest.reflections.isEmpty()) {
            Text(text = "Nothing to reflect on yet.", fontSize = 13.sp, color = TextMuted)
        } else {
            digest.reflections.forEach { reflection ->
                Text(text = "• $reflection", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    // Mastery (level / streak only)
    mastery?.let {
        Spacer(modifier = Modifier.height(14.dp))
        SectionCard(title = "Munchie mastery") {
            Text(
                text = "Level ${it.level} • ${it.aura.displayLabel}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = if (it.streakEmblemVisible) {
                    "Streak emblem earned — ${it.streakDays} day streak."
                } else {
                    "Streak: ${it.streakDays} day(s). Emblem at 7 days."
                },
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = "Cosmetics come from level and streaks only. Scrolling more never earns them.",
                fontSize = 12.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ComparisonRow(label: String, current: String, previous: String, delta: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(
            text = buildString {
                append(current)
                append("  (was $previous)")
                if (delta != null) append("  $delta")
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

private fun formatDelta(percent: Double): String {
    val sign = if (percent >= 0.0) "+" else ""
    return "$sign${"%.1f".format(percent)}%"
}
