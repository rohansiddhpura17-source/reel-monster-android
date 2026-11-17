package com.reelmonster.app.domain.usecase

import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.DailySummary
import com.reelmonster.app.domain.model.DateRangeInterval
import com.reelmonster.app.domain.model.HistoryDateRange
import com.reelmonster.app.domain.model.PeriodStatistics
import java.util.Date
import java.util.Locale

/**
 * HistoryAnalyticsUseCase
 *
 * Computes factual statistics, platform distributions, zero-filled series,
 * and objective period-over-period comparisons for Tracking History.
 */
class HistoryAnalyticsUseCase(private val dbHelper: ReelDbHelper) {

    fun computePeriodStatistics(
        range: HistoryDateRange,
        referenceDate: Date = Date()
    ): PeriodStatistics {
        val interval = DateTimeUtils.calculateInterval(range, referenceDate)
        val rawRecords = dbHelper.getDailySummariesInRange(interval.startDateStr, interval.endDateStr)
        val dailySeries = DateTimeUtils.buildCalendarSeries(
            startDateStr = interval.startDateStr,
            endDateStr = interval.endDateStr,
            persistedRecords = rawRecords,
            referenceDate = referenceDate
        )

        val totalReels = dailySeries.sumOf { it.totalReels }
        val instagramCount = dailySeries.sumOf { it.instagramCount }
        val youtubeCount = dailySeries.sumOf { it.youtubeCount }
        val estimatedMinutes = DateTimeUtils.estimateTimeMinutes(totalReels)

        val totalDays = dailySeries.size.coerceAtLeast(1)
        val dailyAverage = totalReels.toDouble() / totalDays

        val activeDays = dailySeries.filter { it.totalReels > 0 }
        val activeDaysCount = activeDays.size
        val averagePerActiveDay = if (activeDaysCount > 0) {
            totalReels.toDouble() / activeDaysCount
        } else {
            0.0
        }

        val highestDay = dailySeries.maxByOrNull { it.totalReels }?.takeIf { it.totalReels > 0 }
        val lowestActiveDay = activeDays.minByOrNull { it.totalReels }

        // Comparison with equivalent previous period
        var previousPeriodTotal: Int? = null
        var percentageChange: Double? = null
        var comparisonSummary = "No previous period comparison available."

        if (interval.previousStartStr != null && interval.previousEndStr != null) {
            val prevRecords = dbHelper.getDailySummariesInRange(interval.previousStartStr, interval.previousEndStr)
            val prevTotal = prevRecords.values.sumOf { it.totalReels }
            previousPeriodTotal = prevTotal

            val label = interval.previousPeriodLabel ?: "previous period"
            if (prevTotal > 0) {
                val diff = totalReels - prevTotal
                val pct = (diff.toDouble() / prevTotal) * 100.0
                percentageChange = pct
                val sign = if (diff >= 0) "+" else ""
                val pctFormatted = String.format(Locale.US, "%s%.1f%%", sign, pct)
                val diffFormatted = String.format(Locale.US, "%s%d reels", sign, diff)
                comparisonSummary = "$diffFormatted ($pctFormatted) compared to $label ($prevTotal reels)."
            } else if (totalReels > 0) {
                comparisonSummary = "$totalReels reels recorded compared to 0 reels in $label."
            } else {
                comparisonSummary = "0 reels recorded in both current and $label."
            }
        }

        // Factual, descriptive observations
        val insights = mutableListOf<String>()
        if (totalReels > 0) {
            // Peak day
            highestDay?.let {
                val formattedDate = DateTimeUtils.formatDisplayDate(it.dateString)
                insights.add("Highest activity: $formattedDate with ${it.totalReels} reels (${it.instagramCount} IG, ${it.youtubeCount} YT).")
            }

            // Active day average
            val avgActiveStr = String.format(Locale.US, "%.1f", averagePerActiveDay)
            val avgDailyStr = String.format(Locale.US, "%.1f", dailyAverage)
            insights.add("Active day pace: $avgActiveStr reels/active day ($activeDaysCount of $totalDays days active; $avgDailyStr/day overall).")

            // Platform breakdown
            val igPct = ((instagramCount * 100) / totalReels)
            val ytPct = 100 - igPct
            insights.add("Platform distribution: $igPct% Instagram Reels ($instagramCount) vs $ytPct% YouTube Shorts ($youtubeCount).")

            // Period comparison
            insights.add("Trend: $comparisonSummary")
        } else {
            insights.add("No Reel or Short activity recorded during this ${interval.formattedLabel} period.")
            insights.add("Your device was calm and free from short-form video drift.")
        }

        return PeriodStatistics(
            interval = interval,
            totalReels = totalReels,
            instagramCount = instagramCount,
            youtubeCount = youtubeCount,
            estimatedMinutesLost = estimatedMinutes,
            dailyAverage = dailyAverage,
            averagePerActiveDay = averagePerActiveDay,
            activeDaysCount = activeDaysCount,
            totalDaysCount = totalDays,
            highestDay = highestDay,
            lowestActiveDay = lowestActiveDay,
            dailySeries = dailySeries,
            previousPeriodTotal = previousPeriodTotal,
            percentageChange = percentageChange,
            comparisonSummary = comparisonSummary,
            insights = insights
        )
    }
}
