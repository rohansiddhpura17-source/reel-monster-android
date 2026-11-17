package com.reelmonster.app.domain.model.weekly

/**
 * Phase 15 — Contextual Weekly Reflection Dialogues (proposal §7).
 *
 * Deterministic, non-judgmental, and traceable to computed facts only.
 * Reflections are generated from the digest's own numbers — never from
 * inferred mood, vulnerability, intent, attention, addiction, or
 * mental-health status. No reflection ever rewards scrolling, and no
 * language is shame-based. At most two reflections are returned
 * (proposal §6), in fixed priority order so output is reproducible.
 */
object WeeklyReflectionEngine {

    fun reflect(digest: WeeklyDigest): List<String> {
        val reflections = mutableListOf<String>()
        val comparison = digest.comparison
        val current = digest.currentWindow

        // 1. Trend / comparability — highest priority.
        if (comparison.percentagesSuppressed) {
            reflections.add(
                "There is not enough monitoring data to compare these periods reliably."
            )
        } else if (
            (current.reelsPerVerifiedHour ?: 0.0) == 0.0 &&
            (comparison.previous.reelsPerVerifiedHour ?: 0.0) == 0.0 &&
            (current.viewingMsPerVerifiedHour ?: 0.0) == 0.0 &&
            (comparison.previous.viewingMsPerVerifiedHour ?: 0.0) == 0.0
        ) {
            reflections.add("No observed activity in either period.")
        } else {
            val viewDelta = comparison.viewRateDeltaPercent
            when {
                viewDelta != null && viewDelta > 0.0 ->
                    reflections.add(
                        "Your observed viewing time was higher than the previous period."
                    )
                viewDelta != null && viewDelta < 0.0 ->
                    reflections.add(
                        "Your observed viewing time was lower than the previous period."
                    )
                viewDelta != null ->
                    reflections.add(
                        "Your observed viewing time was about the same as the previous period."
                    )
                else ->
                    reflections.add("New observed activity in the current period.")
            }
        }

        // 2. Weekday vs weekend — only when both groups were monitored.
        val weekdayRate = digest.weekdayWeekend.weekday.viewingMinutesPerMonitoredHour
        val weekendRate = digest.weekdayWeekend.weekend.viewingMinutesPerMonitoredHour
        if (weekdayRate != null && weekendRate != null && weekdayRate != weekendRate) {
            if (weekendRate > weekdayRate) {
                reflections.add(
                    "Weekend viewing time per monitored hour was higher than weekday viewing time."
                )
            } else {
                reflections.add(
                    "Weekend viewing time per monitored hour was lower than weekday viewing time."
                )
            }
        }

        // 3. Monitoring completeness — factual, no judgement.
        if (reflections.size < 2 && current.adequateDays < 7) {
            reflections.add(
                "${current.adequateDays} of 7 days had enough monitoring data to compare."
            )
        }

        return reflections.take(2)
    }
}
