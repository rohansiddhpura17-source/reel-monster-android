package com.reelmonster.app.core.util

import com.reelmonster.app.domain.model.DailySummary
import com.reelmonster.app.domain.model.DateRangeInterval
import com.reelmonster.app.domain.model.HistoryDateRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)
    private val shortMonthDayFormat = SimpleDateFormat("MMM d", Locale.US)
    private val fullDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)

    fun getTodayDateString(referenceDate: Date = Date()): String {
        return dateFormat.format(referenceDate)
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun getDayOfWeek(timestamp: Long): String {
        return dayOfWeekFormat.format(Date(timestamp))
    }

    fun parseDate(dateStr: String): Date? {
        return try {
            dateFormat.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    fun formatDisplayDate(dateStr: String): String {
        val parsed = parseDate(dateStr) ?: return dateStr
        return shortMonthDayFormat.format(parsed)
    }

    fun formatFullDate(dateStr: String): String {
        val parsed = parseDate(dateStr) ?: return dateStr
        return fullDateFormat.format(parsed)
    }

    /**
     * Estimates time lost based on ~1.5 minutes per Reel/Short
     * as indicated in Stitch reference design.
     *
     * Phase 15: DEPRECATED. Static count x 1.5 min estimates must not
     * be used in reporting. Use actual persisted viewing_sessions
     * durations instead (ReelDbHelper.getViewingTimeForDate /
     * getViewingSessionsForDate, and the Phase 15
     * ActualViewingTimeReconciler). Kept only so existing Phase 1-14
     * callers and tests keep compiling until their presentation code
     * is migrated; new code must not call this.
     */
    @Deprecated(
        message = "Phase 15: use actual viewing_sessions durations, not count x 1.5 min estimates.",
        level = DeprecationLevel.WARNING
    )
    fun estimateTimeMinutes(reelCount: Int): Double {
        return reelCount * 1.5
    }

    fun formatEstimatedTime(minutes: Double): String {
        return if (minutes >= 60) {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).toInt()
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        } else {
            String.format(Locale.US, "%.1f min", minutes)
        }
    }

    /**
     * Formats actual viewing duration in milliseconds into a concise, human-readable string.
     * Examples: 0L -> "0s", 45_000L -> "45s", 125_000L -> "2m 5s", 3_720_000L -> "1h 2m"
     */
    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "0s"
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            minutes > 0 -> if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /**
     * Formats elapsed timer in mm:ss format for live overlay display (e.g. "00:15", "01:42").
     */
    fun formatTimerMmSs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = (totalSeconds / 60).coerceAtMost(99)
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Constructs a continuous 7-calendar-day sequence spanning [referenceDate - 6 days, ..., referenceDate].
     * Days with no recorded activity are explicitly populated with 0 counts.
     * Guaranteed chronological order (oldest to newest/today).
     */
    fun buildSevenDaySeries(
        persistedRecords: Map<String, DailySummary>,
        referenceDate: Date = Date()
    ): List<DailySummary> {
        val interval = calculateInterval(HistoryDateRange.LAST_7_DAYS, referenceDate)
        return buildCalendarSeries(interval.startDateStr, interval.endDateStr, persistedRecords, referenceDate)
    }

    /**
     * Calculates the exact start, end, label, and previous comparison interval
     * for any HistoryDateRange without including future dates.
     */
    fun calculateInterval(
        range: HistoryDateRange,
        referenceDate: Date = Date()
    ): DateRangeInterval {
        val todayStr = dateFormat.format(referenceDate)

        return when (range) {
            HistoryDateRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayStr = dateFormat.format(cal.time)
                DateRangeInterval(
                    range = range,
                    startDateStr = todayStr,
                    endDateStr = todayStr,
                    formattedLabel = formatDisplayDate(todayStr),
                    daysCount = 1,
                    previousStartStr = yesterdayStr,
                    previousEndStr = yesterdayStr,
                    previousPeriodLabel = "Yesterday"
                )
            }
            HistoryDateRange.LAST_7_DAYS -> {
                val calStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val startStr = dateFormat.format(calStart.time)

                val calPrevStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -13)
                }
                val calPrevEnd = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -7)
                }

                DateRangeInterval(
                    range = range,
                    startDateStr = startStr,
                    endDateStr = todayStr,
                    formattedLabel = "${formatDisplayDate(startStr)} – ${formatDisplayDate(todayStr)}",
                    daysCount = 7,
                    previousStartStr = dateFormat.format(calPrevStart.time),
                    previousEndStr = dateFormat.format(calPrevEnd.time),
                    previousPeriodLabel = "Previous 7 Days"
                )
            }
            HistoryDateRange.LAST_30_DAYS -> {
                val calStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -29)
                }
                val startStr = dateFormat.format(calStart.time)

                val calPrevStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -59)
                }
                val calPrevEnd = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -30)
                }

                DateRangeInterval(
                    range = range,
                    startDateStr = startStr,
                    endDateStr = todayStr,
                    formattedLabel = "${formatDisplayDate(startStr)} – ${formatDisplayDate(todayStr)}",
                    daysCount = 30,
                    previousStartStr = dateFormat.format(calPrevStart.time),
                    previousEndStr = dateFormat.format(calPrevEnd.time),
                    previousPeriodLabel = "Previous 30 Days"
                )
            }
            HistoryDateRange.THIS_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val startStr = dateFormat.format(calStart.time)
                val currentDayOfMonth = Calendar.getInstance().apply { time = referenceDate }.get(Calendar.DAY_OF_MONTH)

                // Previous month comparison: same elapsed days of previous month
                val calPrevStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val maxPrevDays = calPrevStart.getActualMaximum(Calendar.DAY_OF_MONTH)
                val prevTargetDay = currentDayOfMonth.coerceAtMost(maxPrevDays)
                val calPrevEnd = Calendar.getInstance().apply {
                    time = calPrevStart.time
                    set(Calendar.DAY_OF_MONTH, prevTargetDay)
                }

                DateRangeInterval(
                    range = range,
                    startDateStr = startStr,
                    endDateStr = todayStr,
                    formattedLabel = "${formatDisplayDate(startStr)} – ${formatDisplayDate(todayStr)}",
                    daysCount = currentDayOfMonth,
                    previousStartStr = dateFormat.format(calPrevStart.time),
                    previousEndStr = dateFormat.format(calPrevEnd.time),
                    previousPeriodLabel = "Same period last month"
                )
            }
            HistoryDateRange.PREVIOUS_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val maxDays = calStart.getActualMaximum(Calendar.DAY_OF_MONTH)
                val calEnd = Calendar.getInstance().apply {
                    time = calStart.time
                    set(Calendar.DAY_OF_MONTH, maxDays)
                }
                val startStr = dateFormat.format(calStart.time)
                val endStr = dateFormat.format(calEnd.time)

                // Comparison is the month before previous
                val calPrevStart = Calendar.getInstance().apply {
                    time = calStart.time
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val maxPrevDays = calPrevStart.getActualMaximum(Calendar.DAY_OF_MONTH)
                val calPrevEnd = Calendar.getInstance().apply {
                    time = calPrevStart.time
                    set(Calendar.DAY_OF_MONTH, maxPrevDays)
                }

                DateRangeInterval(
                    range = range,
                    startDateStr = startStr,
                    endDateStr = endStr,
                    formattedLabel = "${formatDisplayDate(startStr)} – ${formatDisplayDate(endStr)}",
                    daysCount = maxDays,
                    previousStartStr = dateFormat.format(calPrevStart.time),
                    previousEndStr = dateFormat.format(calPrevEnd.time),
                    previousPeriodLabel = "Month prior"
                )
            }
        }
    }

    /**
     * Builds a strictly contiguous, chronological sequence of calendar days from startDateStr to endDateStr (inclusive).
     * Missing days from persistedRecords are populated with 0 counts.
     * Never generates duplicate dates.
     */
    fun buildCalendarSeries(
        startDateStr: String,
        endDateStr: String,
        persistedRecords: Map<String, DailySummary>,
        referenceDate: Date = Date()
    ): List<DailySummary> {
        val startDate = parseDate(startDateStr) ?: return emptyList()
        val endDate = parseDate(endDateStr) ?: return emptyList()
        if (startDate.after(endDate)) return emptyList()

        val todayStr = dateFormat.format(referenceDate)
        val result = mutableListOf<DailySummary>()

        val cal = Calendar.getInstance().apply { time = startDate }
        val endCal = Calendar.getInstance().apply { time = endDate }

        while (!cal.after(endCal)) {
            val dateStr = dateFormat.format(cal.time)
            val dayName = dayOfWeekFormat.format(cal.time)
            val existing = persistedRecords[dateStr]

            if (existing != null) {
                result.add(
                    existing.copy(
                        dayOfWeek = dayName,
                        isToday = (dateStr == todayStr)
                    )
                )
            } else {
                result.add(
                    DailySummary(
                        dateString = dateStr,
                        dayOfWeek = dayName,
                        totalReels = 0,
                        instagramCount = 0,
                        youtubeCount = 0,
                        estimatedMinutesLost = 0.0,
                        isBingeSpike = false,
                        isToday = (dateStr == todayStr)
                    )
                )
            }

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return result
    }
}
