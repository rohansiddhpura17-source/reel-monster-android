package com.reelmonster.app.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Supported date ranges for historical tracking analysis.
 */
enum class HistoryDateRange(
    val key: String,
    val displayName: String,
    val shortLabel: String
) {
    TODAY(
        key = "today",
        displayName = "Today",
        shortLabel = "Today"
    ),
    LAST_7_DAYS(
        key = "last_7_days",
        displayName = "Last 7 Days",
        shortLabel = "7D"
    ),
    LAST_30_DAYS(
        key = "last_30_days",
        displayName = "Last 30 Days",
        shortLabel = "30D"
    ),
    THIS_MONTH(
        key = "this_month",
        displayName = "This Month",
        shortLabel = "Month"
    ),
    PREVIOUS_MONTH(
        key = "prev_month",
        displayName = "Previous Month",
        shortLabel = "Prev"
    );

    companion object {
        fun fromKey(key: String?): HistoryDateRange {
            return entries.firstOrNull { it.key == key } ?: LAST_7_DAYS
        }
    }
}

/**
 * Calendar interval defining the start, end, and label of a tracking analysis period.
 */
data class DateRangeInterval(
    val range: HistoryDateRange,
    val startDateStr: String, // YYYY-MM-DD
    val endDateStr: String,   // YYYY-MM-DD
    val formattedLabel: String, // e.g. "Nov 2 – Nov 8, 2026"
    val daysCount: Int,
    val previousStartStr: String? = null,
    val previousEndStr: String? = null,
    val previousPeriodLabel: String? = null
)
