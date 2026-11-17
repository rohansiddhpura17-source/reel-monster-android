package com.reelmonster.app.domain.model

/**
 * PeriodStatistics
 *
 * Encapsulates computed historical metrics, platform breakdown,
 * and factual insights across a selected date range.
 */
data class PeriodStatistics(
    val interval: DateRangeInterval,
    val totalReels: Int,
    val instagramCount: Int,
    val youtubeCount: Int,
    val estimatedMinutesLost: Double,
    val dailyAverage: Double,
    val averagePerActiveDay: Double,
    val activeDaysCount: Int,
    val totalDaysCount: Int,
    val highestDay: DailySummary?,
    val lowestActiveDay: DailySummary?,
    val dailySeries: List<DailySummary>,
    val previousPeriodTotal: Int?,
    val percentageChange: Double?,
    val comparisonSummary: String,
    val insights: List<String>
) {
    init {
        // Enforce attribution consistency
        require(totalReels == instagramCount + youtubeCount) {
            "Total reels ($totalReels) must equal Instagram ($instagramCount) + YouTube ($youtubeCount)"
        }
    }

    val hasActivity: Boolean get() = totalReels > 0
    val instagramPercentage: Int get() = if (totalReels > 0) ((instagramCount * 100) / totalReels) else 0
    val youtubePercentage: Int get() = if (totalReels > 0) (100 - instagramPercentage) else 0
}
