package com.reelmonster.app.data.repository

import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.domain.model.DailyResetResult
import com.reelmonster.app.domain.model.DailySummary
import com.reelmonster.app.domain.model.DateRangeInterval
import com.reelmonster.app.domain.model.HistoryDateRange
import com.reelmonster.app.domain.model.MunchiePersonalityEngine
import com.reelmonster.app.domain.model.MunchiePersonalityState
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.PeriodStatistics
import com.reelmonster.app.domain.model.ReelLogItem
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.focus.FocusInterventionReminder
import com.reelmonster.app.domain.model.focus.FocusPeriodStats
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusSessionProgress
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.DailyGoalProgress
import com.reelmonster.app.domain.model.goal.DailyGoalRecord
import com.reelmonster.app.domain.model.goal.DailyGoalSnapshot
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalState
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.domain.model.monitoring.DailyMonitoringCoverage
import com.reelmonster.app.domain.model.progression.Achievement
import com.reelmonster.app.domain.model.progression.DailyProgressionEvaluation
import com.reelmonster.app.domain.model.progression.StreakRecord
import com.reelmonster.app.domain.model.progression.UserProgress
import com.reelmonster.app.domain.model.progression.XpEventType
import com.reelmonster.app.domain.model.progression.XpTransaction
import com.reelmonster.app.domain.usecase.HistoryAnalyticsUseCase
import com.reelmonster.app.service.focus.FocusSessionTracker
import com.reelmonster.app.service.tracking.DeduplicationEngine
import com.reelmonster.app.service.tracking.MonitoringCoverageTracker
import com.reelmonster.app.service.tracking.ReelAccessibilityService
import com.reelmonster.app.service.tracking.ViewingSessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ReelRepository
 *
 * Single source of truth for Reel Monster tracking state.
 * Connects SQLite persistence, DataStore preferences, and reactive UI StateFlow streams.
 * Includes debounce filtering to prevent accidental double-tap counts while supporting
 * legitimate consecutive video tracking.
 */
class ReelRepository(
    val dbHelper: ReelDbHelper,
    val preferencesManager: UserPreferencesManager,
    preloadOnInit: Boolean = true
) {
    companion object {
        /**
         * Accidental double-tap debounce window in milliseconds.
         * Interactions faster than 350ms on the same app target are filtered out.
         */
        const val DEBOUNCE_THRESHOLD_MS = 350L

        private fun createEmptyPeriodStatistics(): PeriodStatistics {
            val today = DateTimeUtils.getTodayDateString()
            return PeriodStatistics(
                interval = DateRangeInterval(
                    range = HistoryDateRange.LAST_7_DAYS,
                    startDateStr = today,
                    endDateStr = today,
                    formattedLabel = "Last 7 Days",
                    daysCount = 7
                ),
                totalReels = 0,
                instagramCount = 0,
                youtubeCount = 0,
                estimatedMinutesLost = 0.0,
                dailyAverage = 0.0,
                averagePerActiveDay = 0.0,
                activeDaysCount = 0,
                totalDaysCount = 7,
                highestDay = null,
                lowestActiveDay = null,
                dailySeries = emptyList(),
                previousPeriodTotal = 0,
                percentageChange = null,
                comparisonSummary = "No activity recorded yet",
                insights = emptyList()
            )
        }
    }

    private var lastLogTimestamp = 0L
    private var lastLogApp: TargetApp? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    private val _munchieStage = MutableStateFlow(MunchieStage.BABY)
    val munchieStage: StateFlow<MunchieStage> = _munchieStage.asStateFlow()

    private val _platformBreakdown = MutableStateFlow(Pair(0, 0))
    val platformBreakdown: StateFlow<Pair<Int, Int>> = _platformBreakdown.asStateFlow()

    private val _last7Days = MutableStateFlow<List<DailySummary>>(emptyList())
    val last7Days: StateFlow<List<DailySummary>> = _last7Days.asStateFlow()

    private val _sevenDayActivity = MutableStateFlow<List<DailySummary>>(emptyList())
    val sevenDayActivity: StateFlow<List<DailySummary>> = _sevenDayActivity.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<ReelLogItem>>(emptyList())
    val recentEvents: StateFlow<List<ReelLogItem>> = _recentEvents.asStateFlow()

    private val historyUseCase = HistoryAnalyticsUseCase(dbHelper)

    private val _selectedHistoryRange = MutableStateFlow(HistoryDateRange.LAST_7_DAYS)
    val selectedHistoryRange: StateFlow<HistoryDateRange> = _selectedHistoryRange.asStateFlow()

    private val _historyPeriodStats = MutableStateFlow(createEmptyPeriodStatistics())
    val historyPeriodStats: StateFlow<PeriodStatistics> = _historyPeriodStats.asStateFlow()

    private val _selectedDayDate = MutableStateFlow<String?>(DateTimeUtils.getTodayDateString())
    val selectedDayDate: StateFlow<String?> = _selectedDayDate.asStateFlow()

    private val _selectedDayEvents = MutableStateFlow<List<ReelLogItem>>(emptyList())
    val selectedDayEvents: StateFlow<List<ReelLogItem>> = _selectedDayEvents.asStateFlow()

    private val _todayViewingTimeMs = MutableStateFlow(0L)
    val todayViewingTimeMs: StateFlow<Long> = _todayViewingTimeMs.asStateFlow()

    private val _todayPlatformViewingTimeMs = MutableStateFlow(Pair(0L, 0L))
    val todayPlatformViewingTimeMs: StateFlow<Pair<Long, Long>> = _todayPlatformViewingTimeMs.asStateFlow()

    private val _todayAverageDurationMs = MutableStateFlow(0L)
    val todayAverageDurationMs: StateFlow<Long> = _todayAverageDurationMs.asStateFlow()

    private val _remainingDailyResets = MutableStateFlow(UserPreferencesManager.MAX_DAILY_RESETS)
    val remainingDailyResets: StateFlow<Int> = _remainingDailyResets.asStateFlow()

    val isTrackingPaused: StateFlow<Boolean> get() = preferencesManager.isTrackingPaused
    val isTrackingActive: Boolean get() = preferencesManager.isTrackingActive
    val isGlobalTrackingEnabled: StateFlow<Boolean> get() = preferencesManager.isGlobalTrackingEnabled

    // Phase 12: Daily Goals & Pacing StateFlows
    private val _activeGoals = MutableStateFlow<List<DailyGoal>>(emptyList())
    val activeGoals: StateFlow<List<DailyGoal>> = _activeGoals.asStateFlow()

    private val _goalProgressList = MutableStateFlow<List<DailyGoalProgress>>(emptyList())
    val goalProgressList: StateFlow<List<DailyGoalProgress>> = _goalProgressList.asStateFlow()

    private val _combinedCountProgress = MutableStateFlow<DailyGoalProgress?>(null)
    val combinedCountProgress: StateFlow<DailyGoalProgress?> = _combinedCountProgress.asStateFlow()

    private val _combinedTimeProgress = MutableStateFlow<DailyGoalProgress?>(null)
    val combinedTimeProgress: StateFlow<DailyGoalProgress?> = _combinedTimeProgress.asStateFlow()

    private val _historicalGoalSuccessRate = MutableStateFlow(Pair(0, 0))
    val historicalGoalSuccessRate: StateFlow<Pair<Int, Int>> = _historicalGoalSuccessRate.asStateFlow()

    private var lastNotifiedGoalState: GoalState = GoalState.NOT_STARTED

    // Phase 13: Focus Mode StateFlows & Tracking
    val focusSessionTracker: FocusSessionTracker = FocusSessionTracker.instance
    val activeFocusProgress: StateFlow<FocusSessionProgress> get() = focusSessionTracker.progress
    val focusInterventionReminder: StateFlow<FocusInterventionReminder?> get() = focusSessionTracker.interventionReminder

    private val _todayFocusTimeMs = MutableStateFlow(0L)
    val todayFocusTimeMs: StateFlow<Long> = _todayFocusTimeMs.asStateFlow()

    private val _todayFocusCompletedCount = MutableStateFlow(0)
    val todayFocusCompletedCount: StateFlow<Int> = _todayFocusCompletedCount.asStateFlow()

    private val _weeklyFocusStats = MutableStateFlow(FocusPeriodStats.empty())
    val weeklyFocusStats: StateFlow<FocusPeriodStats> = _weeklyFocusStats.asStateFlow()

    private val _recentFocusSessions = MutableStateFlow<List<FocusSession>>(emptyList())
    val recentFocusSessions: StateFlow<List<FocusSession>> = _recentFocusSessions.asStateFlow()

    // Phase 14: XP, Streaks, Monitoring Coverage & Achievements StateFlows
    val monitoringCoverageTracker: MonitoringCoverageTracker = MonitoringCoverageTracker.instance

    private val _userProgress = MutableStateFlow(UserProgress())
    val userProgress: StateFlow<UserProgress> = _userProgress.asStateFlow()

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _recentXpTransactions = MutableStateFlow<List<XpTransaction>>(emptyList())
    val recentXpTransactions: StateFlow<List<XpTransaction>> = _recentXpTransactions.asStateFlow()

    private val _streakRecords = MutableStateFlow<List<StreakRecord>>(emptyList())
    val streakRecords: StateFlow<List<StreakRecord>> = _streakRecords.asStateFlow()

    private val _todayMonitoringCoverage = MutableStateFlow(DailyMonitoringCoverage(dateKey = DateTimeUtils.getTodayDateString()))
    val todayMonitoringCoverage: StateFlow<DailyMonitoringCoverage> = _todayMonitoringCoverage.asStateFlow()

    private val _todayGoalSnapshots = MutableStateFlow<List<DailyGoalSnapshot>>(emptyList())
    val todayGoalSnapshots: StateFlow<List<DailyGoalSnapshot>> = _todayGoalSnapshots.asStateFlow()

    // Phase 10: Shared Personality 2.0 Engine & StateFlows
    val personalityEngine: MunchiePersonalityEngine = MunchiePersonalityEngine.instance
    val munchiePersonalityState: StateFlow<MunchiePersonalityState> get() = personalityEngine.currentState
    val munchiePersonalityMessage: StateFlow<String> get() = personalityEngine.currentMessage
    val mascotReactionTrigger: StateFlow<Int> get() = personalityEngine.reactionTrigger

    init {
        if (preloadOnInit) {
            initializeSync()
        }
    }

    /**
     * Synchronously loads initial SQLite state and marks the repository as initialized.
     * Safe to invoke from background IO threads or unit tests.
     */
    @Synchronized
    fun initializeSync() {
        try {
            _initError.value = null
            monitoringCoverageTracker.initialize(context = null, dbHelper = dbHelper)
            refresh()
            _isInitialized.value = true
        } catch (e: Exception) {
            _initError.value = e.message ?: "Failed to initialize local tracking database."
            _isInitialized.value = true
        }
    }

    /**
     * Loads initial SQLite state on Dispatchers.IO so Application.onCreate() never blocks the main thread.
     */
    fun initializeInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            initializeSync()
        }
    }

    /**
     * Records a Reel or Short watched.
     *
     * @param app The target application (Instagram or YouTube).
     * @param mode Tracking mode used (defaults to Level 1 Manual).
     * @param force Bypasses pause and debounce checks (used for tests or administrative logs).
     * @return true if event was successfully logged and persisted; false if rejected (paused or debounced).
     */
    @Synchronized
    fun logReel(
        app: TargetApp,
        mode: TrackingMode = preferencesManager.trackingMode.value,
        force: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()

        // 1. Check if global tracking is enabled
        if (!preferencesManager.isGlobalTrackingEnabled.value && !force) {
            return false
        }

        // 2. Check if tracking is paused
        if (preferencesManager.isTrackingPaused.value && !force) {
            return false
        }

        // 3. Prevent accidental duplicate counting caused by rapid double taps on the same app
        if (!force && (now - lastLogTimestamp) < DEBOUNCE_THRESHOLD_MS && lastLogApp == app) {
            return false
        }

        lastLogTimestamp = now
        lastLogApp = app

        val oldCount = _todayCount.value
        val oldStage = _munchieStage.value

        // 4. Persist event and atomic daily aggregate to SQLite
        val rowId = dbHelper.insertReelEvent(app, mode, now)
        if (rowId > 0) {
            refresh()
            val newCount = _todayCount.value
            val newStage = _munchieStage.value
            personalityEngine.onReelLogged(
                newCount = newCount,
                oldCount = oldCount,
                isFirstReelToday = (oldCount == 0 && newCount > 0),
                stageEvolutionOccurred = (newStage.level > oldStage.level)
            )
            return true
        }
        return false
    }

    /**
     * Helper to log an event at an explicit timestamp and date key.
     * Used for midnight rollover testing and historical replay.
     */
    @Synchronized
    fun logReelAt(
        app: TargetApp,
        mode: TrackingMode = preferencesManager.trackingMode.value,
        timestamp: Long,
        dateStr: String
    ): Boolean {
        val oldCount = _todayCount.value
        val oldStage = _munchieStage.value

        val rowId = dbHelper.insertReelEvent(app, mode, timestamp, dateStr)
        if (rowId > 0) {
            refresh()
            val newCount = _todayCount.value
            val newStage = _munchieStage.value
            personalityEngine.onReelLogged(
                newCount = newCount,
                oldCount = oldCount,
                isFirstReelToday = (oldCount == 0 && newCount > 0),
                stageEvolutionOccurred = (newStage.level > oldStage.level)
            )
            return true
        }
        return false
    }

    fun pauseTracking() {
        preferencesManager.pauseTracking()
        monitoringCoverageTracker.onStateChanged(
            dbHelper = dbHelper,
            newTrackingEnabled = false,
            interruptionReason = "TRACKING_PAUSED"
        )
        personalityEngine.onTrackingPaused()
        evaluateGoals()
        refreshProgressionState()
    }

    fun resumeTracking() {
        preferencesManager.resumeTracking()
        monitoringCoverageTracker.onStateChanged(
            dbHelper = dbHelper,
            newTrackingEnabled = preferencesManager.isGlobalTrackingEnabled.value,
            interruptionReason = null
        )
        personalityEngine.onTrackingResumed()
        evaluateGoals()
        refreshProgressionState()
    }

    fun setGlobalTrackingEnabled(enabled: Boolean) {
        preferencesManager.setGlobalTrackingEnabled(enabled)
        monitoringCoverageTracker.onStateChanged(
            dbHelper = dbHelper,
            newTrackingEnabled = enabled && !preferencesManager.isTrackingPaused.value,
            interruptionReason = if (!enabled) "GLOBAL_TRACKING_DISABLED" else null
        )
        if (enabled) {
            personalityEngine.onTrackingResumed()
        } else {
            personalityEngine.onGlobalTrackingDisabled()
        }
        evaluateGoals()
        refreshProgressionState()
    }

    /**
     * Handles user tapping Munchie mascot (guaranteed zero count/duration increment).
     */
    fun onMascotTapped(): MunchiePersonalityState {
        return personalityEngine.onMascotTapped()
    }

    /**
     * Handles user long-pressing Munchie mascot (guaranteed zero count/duration increment).
     */
    fun onMascotLongPressed(): MunchiePersonalityState {
        return personalityEngine.onMascotLongPressed()
    }

    /**
     * Handles dashboard screen view entry.
     */
    fun onDashboardOpened() {
        personalityEngine.onDashboardOpened(
            isPaused = preferencesManager.isTrackingPaused.value,
            isGlobalEnabled = preferencesManager.isGlobalTrackingEnabled.value,
            todayCount = _todayCount.value
        )
    }

    fun selectHistoryDateRange(range: HistoryDateRange) {
        _selectedHistoryRange.value = range
        val stats = historyUseCase.computePeriodStatistics(range)
        _historyPeriodStats.value = stats

        // Select latest day in the series if current selected day is not present
        val currentDay = _selectedDayDate.value
        if (currentDay == null || stats.dailySeries.none { it.dateString == currentDay }) {
            val defaultDay = stats.dailySeries.lastOrNull()?.dateString
            selectDay(defaultDay)
        }
    }

    fun selectDay(dateStr: String?) {
        _selectedDayDate.value = dateStr
        _selectedDayEvents.value = if (dateStr != null) {
            dbHelper.getEventsForDate(dateStr)
        } else {
            emptyList()
        }
    }

    fun getPeriodStatistics(range: HistoryDateRange, referenceDate: Date = Date()): PeriodStatistics {
        return historyUseCase.computePeriodStatistics(range, referenceDate)
    }

    fun getEventsForDate(dateStr: String): List<ReelLogItem> {
        return dbHelper.getEventsForDate(dateStr)
    }

    /**
     * Records a completed or interrupted viewing session to SQLite and updates UI streams.
     */
    @Synchronized
    fun recordViewingSession(session: ViewingSession): Long {
        val rowId = dbHelper.insertViewingSession(session)
        if (rowId > 0) {
            refresh()
            personalityEngine.onContinuousSessionThresholdReached(session.durationMs)
        }
        return rowId
    }

    /**
     * Resets today's tracked counts and viewing sessions.
     * Allowed up to MAX_DAILY_RESETS (5) times per local calendar day.
     *
     * @param todayDateStr Calendar date string (YYYY-MM-DD). Defaults to today.
     * @return DailyResetResult indicating success or remaining allowance error.
     */
    @Synchronized
    fun performDailyReset(todayDateStr: String = DateTimeUtils.getTodayDateString()): DailyResetResult {
        val remaining = preferencesManager.getRemainingDailyResets(todayDateStr)
        if (remaining <= 0) {
            return DailyResetResult(
                success = false,
                message = "Daily reset limit reached (${UserPreferencesManager.MAX_DAILY_RESETS} of ${UserPreferencesManager.MAX_DAILY_RESETS} used today). Resets renew at midnight.",
                remainingResets = 0
            )
        }

        // 1. Safely end active viewing session if currently watching a video
        ViewingSessionTracker.instance.endActiveSession(this, "INTERRUPTED_RESET")

        // 2. Clear today's database events and viewing sessions
        dbHelper.resetTodayCountOnly(todayDateStr)

        // 3. Clear deduplication engine cache
        ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)

        // 4. Update daily reset usage counter in DataStore
        val used = preferencesManager.getDailyResetUsage(todayDateStr)
        val newUsed = used + 1
        preferencesManager.setDailyResetUsage(todayDateStr, newUsed)

        // 5. Reset local debounce tracking
        lastLogTimestamp = 0L
        lastLogApp = null
        lastNotifiedGoalState = GoalState.NOT_STARTED

        // 6. Refresh reactive StateFlows
        refresh()
        personalityEngine.reset()

        val newRemaining = (UserPreferencesManager.MAX_DAILY_RESETS - newUsed).coerceAtLeast(0)
        return DailyResetResult(
            success = true,
            message = "Today's tracking has been reset. ($newRemaining resets remaining today)",
            remainingResets = newRemaining
        )
    }

    fun refresh() {
        val count = dbHelper.getTodayCount()
        _todayCount.value = count
        _munchieStage.value = MunchieStage.fromCount(count)
        _platformBreakdown.value = dbHelper.getTodayPlatformBreakdown()
        _todayViewingTimeMs.value = dbHelper.getTodayViewingTimeMs()
        _todayPlatformViewingTimeMs.value = dbHelper.getTodayPlatformViewingTimeMs()
        _todayAverageDurationMs.value = dbHelper.getAverageViewingDurationMs()
        _remainingDailyResets.value = preferencesManager.getRemainingDailyResets(DateTimeUtils.getTodayDateString())
        _last7Days.value = dbHelper.getLast7Days()
        _sevenDayActivity.value = dbHelper.getSevenDayCalendarSeries()
        _recentEvents.value = dbHelper.getRecentEvents()
        _historyPeriodStats.value = historyUseCase.computePeriodStatistics(_selectedHistoryRange.value)
        _selectedDayEvents.value = _selectedDayDate.value?.let { dbHelper.getEventsForDate(it) } ?: emptyList()
        evaluateGoals()
        finalizeHistoricalGoalRecords()
        refreshFocusStats()
        finalizeHistoricalProgression()
        refreshProgressionState()
    }

    fun clearAllData() {
        ViewingSessionTracker.instance.endActiveSession(this, "INTERRUPTED_RESET")
        focusSessionTracker.reset()
        dbHelper.clearAllData()
        ReelAccessibilityService.dedupEngine.reset(clearRecentHistory = true)
        preferencesManager.setDailyResetUsage(DateTimeUtils.getTodayDateString(), 0)
        lastLogTimestamp = 0L
        lastLogApp = null
        lastNotifiedGoalState = GoalState.NOT_STARTED
        refresh()
        personalityEngine.reset()
    }

    /**
     * Resets only today's test count and deduplication state without deleting historical data.
     */
    @Synchronized
    fun resetTodayCountForTesting(todayDateStr: String = DateTimeUtils.getTodayDateString()) {
        dbHelper.resetTodayCountOnly(todayDateStr)
        lastLogTimestamp = 0L
        lastLogApp = null
        lastNotifiedGoalState = GoalState.NOT_STARTED
        refresh()
    }

    // =========================================================================
    // Phase 12: Daily Goals & Pacing Architecture
    // =========================================================================

    @Synchronized
    fun evaluateGoals() {
        val goals = dbHelper.getAllGoals()
        _activeGoals.value = goals

        val todayCount = _todayCount.value
        val todayViewingMs = _todayViewingTimeMs.value
        val todayViewingMins = (todayViewingMs / 60_000L).toInt()
        val breakdown = _platformBreakdown.value
        val igCount = breakdown.first
        val ytCount = breakdown.second
        val platformTime = _todayPlatformViewingTimeMs.value
        val igMins = (platformTime.first / 60_000L).toInt()
        val ytMins = (platformTime.second / 60_000L).toInt()

        val isPaused = preferencesManager.isTrackingPaused.value || !preferencesManager.isGlobalTrackingEnabled.value

        val progressList = mutableListOf<DailyGoalProgress>()
        var combinedCountProg: DailyGoalProgress? = null
        var combinedTimeProg: DailyGoalProgress? = null

        for (goal in goals) {
            if (!goal.isEnabled) continue

            val current = when (goal.type) {
                GoalType.REEL_COUNT -> when (goal.scope) {
                    GoalScope.COMBINED -> todayCount
                    GoalScope.INSTAGRAM -> igCount
                    GoalScope.YOUTUBE -> ytCount
                }
                GoalType.VIEWING_TIME_MINUTES -> when (goal.scope) {
                    GoalScope.COMBINED -> todayViewingMins
                    GoalScope.INSTAGRAM -> igMins
                    GoalScope.YOUTUBE -> ytMins
                }
            }

            val remaining = (goal.targetValue - current).coerceAtLeast(0)
            val ratio = if (goal.targetValue > 0) current.toFloat() / goal.targetValue.toFloat() else 0f

            val state = when {
                isPaused -> GoalState.PAUSED
                current == 0 -> GoalState.NOT_STARTED
                current > goal.targetValue -> GoalState.EXCEEDED
                current == goal.targetValue || ratio >= 0.90f -> GoalState.AT_LIMIT
                ratio >= 0.70f -> GoalState.APPROACHING_LIMIT
                else -> GoalState.IN_PROGRESS
            }

            val prog = DailyGoalProgress(
                goal = goal,
                currentValue = current,
                remainingValue = remaining,
                usageRatio = ratio,
                state = state
            )
            progressList.add(prog)

            if (goal.scope == GoalScope.COMBINED) {
                if (goal.type == GoalType.REEL_COUNT) combinedCountProg = prog
                if (goal.type == GoalType.VIEWING_TIME_MINUTES) combinedTimeProg = prog
            }
        }

        _goalProgressList.value = progressList
        _combinedCountProgress.value = combinedCountProg
        _combinedTimeProgress.value = combinedTimeProg

        // Personality reaction on boundary events (clearly distinguishing limit reach from celebration)
        combinedCountProg?.let { prog ->
            if (prog.state != lastNotifiedGoalState) {
                when (prog.state) {
                    GoalState.AT_LIMIT -> personalityEngine.onGoalLimitReached()
                    GoalState.EXCEEDED -> personalityEngine.onGoalExceeded(
                        prog.currentValue - prog.goal.targetValue,
                        prog.goal.type.unit
                    )
                    GoalState.APPROACHING_LIMIT -> personalityEngine.onGoalApproachingLimit(
                        prog.remainingValue,
                        prog.goal.type.unit
                    )
                    GoalState.IN_PROGRESS -> {
                        if (prog.usageRatio in 0.48f..0.52f) {
                            personalityEngine.onGoalPacingMilestone(prog.usagePercentage)
                        }
                    }
                    else -> {}
                }
                lastNotifiedGoalState = prog.state
            }
        }
    }

    @Synchronized
    fun setDailyGoal(
        type: GoalType,
        scope: GoalScope,
        targetValue: Int,
        isEnabled: Boolean,
        effectiveTimestamp: Long = System.currentTimeMillis(),
        dateKey: String = DateTimeUtils.formatDate(effectiveTimestamp)
    ) {
        val goal = DailyGoal(
            type = type,
            scope = scope,
            targetValue = targetValue.coerceAtLeast(1),
            isEnabled = isEnabled
        )
        dbHelper.insertOrUpdateGoal(
            goal = goal,
            effectiveTimestamp = effectiveTimestamp,
            dateKey = dateKey
        )
        evaluateGoals()
        refreshProgressionState(dateKey)
    }

    /**
     * Finalizes historical daily goal records at the local-day boundary.
     * Guaranteed: Never marks the current in-progress day as finalized/successful,
     * and daily resets never erase historical records.
     */
    @Synchronized
    fun finalizeHistoricalGoalRecords(todayDateKey: String = DateTimeUtils.getTodayDateString()) {
        val goals = dbHelper.getAllGoals().filter { it.isEnabled }
        if (goals.isEmpty()) return

        val last7 = dbHelper.getLast7Days()
        for (summary in last7) {
            val date = summary.dateString
            if (date >= todayDateKey) continue // Never evaluate today or future dates as finalized!

            if (summary.totalReels > 0) {
                val viewingMs = dbHelper.getViewingTimeForDate(date) ?: 0L
                val viewingMins = (viewingMs / 60_000L).toInt()

                for (goal in goals) {
                    val actual = when (goal.type) {
                        GoalType.REEL_COUNT -> when (goal.scope) {
                            GoalScope.COMBINED -> summary.totalReels
                            GoalScope.INSTAGRAM -> summary.instagramCount
                            GoalScope.YOUTUBE -> summary.youtubeCount
                        }
                        GoalType.VIEWING_TIME_MINUTES -> when (goal.scope) {
                            GoalScope.COMBINED -> viewingMins
                            // Phase 16 (DEF-01): measured per-platform durations.
                            // The old proportional split estimated platform minutes
                            // from reel counts, which is wrong when dwell differs
                            // across platforms (e.g. 9 IG reels at 6 min vs 1 YT
                            // reel at 54 min: old code reported 54 IG minutes).
                            GoalScope.INSTAGRAM -> ((dbHelper.getViewingTimeForDateByApp(date, TargetApp.INSTAGRAM) ?: 0L) / 60_000L).toInt()
                            GoalScope.YOUTUBE -> ((dbHelper.getViewingTimeForDateByApp(date, TargetApp.YOUTUBE) ?: 0L) / 60_000L).toInt()
                        }
                    }
                    val wasSuccessful = actual <= goal.targetValue
                    dbHelper.insertOrUpdateGoalRecord(
                        DailyGoalRecord(
                            dateKey = date,
                            goalType = goal.type,
                            scope = goal.scope,
                            targetValue = goal.targetValue,
                            actualValue = actual,
                            wasSuccessful = wasSuccessful
                        )
                    )
                }
            }
        }
        _historicalGoalSuccessRate.value = dbHelper.getGoalSuccessStats()
    }

    @Synchronized
    fun getGoalRecordsForDate(dateKey: String): List<DailyGoalRecord> {
        return dbHelper.getGoalRecordsForDate(dateKey)
    }

    @Synchronized
    fun getAllGoalRecords(): List<DailyGoalRecord> {
        return dbHelper.getAllGoalRecords()
    }

    // =========================================================================
    // Phase 13: Focus Mode Operations
    // =========================================================================

    @Synchronized
    fun insertFocusSession(session: FocusSession): Long {
        val prevLevel = _userProgress.value.currentLevel
        val rowId = dbHelper.insertFocusSession(session)
        if (rowId > 0L) {
            val persisted = session.copy(id = rowId)
            val xpAwarded = dbHelper.evaluateAndAwardFocusSessionXp(persisted)
            refreshProgressionState(session.dateString)
            if (xpAwarded) {
                val updatedProgress = _userProgress.value
                if (updatedProgress.currentLevel > prevLevel) {
                    personalityEngine.onLevelUp(updatedProgress.currentLevel, updatedProgress.levelTitle)
                } else {
                    personalityEngine.onXpEarned(
                        XpEventType.FOCUS_SESSION_COMPLETED.defaultXp,
                        "Completed ${session.sessionType.displayName}"
                    )
                }
            }
        }
        return rowId
    }

    @Synchronized
    fun refreshFocusStats() {
        val todayStr = DateTimeUtils.getTodayDateString()
        _todayFocusTimeMs.value = dbHelper.getTodayFocusTimeMs(todayStr)
        _todayFocusCompletedCount.value = dbHelper.getTodayCompletedFocusCount(todayStr)
        val interval = DateTimeUtils.calculateInterval(HistoryDateRange.LAST_7_DAYS)
        _weeklyFocusStats.value = dbHelper.getWeeklyFocusStats(interval.startDateStr, interval.endDateStr)
        _recentFocusSessions.value = dbHelper.getRecentFocusSessions(20)
    }

    fun startFocusSession(type: FocusType, durationMinutes: Int) {
        focusSessionTracker.start(type, durationMinutes, this)
    }

    fun pauseFocusSession() {
        focusSessionTracker.pause(this)
    }

    fun resumeFocusSession() {
        focusSessionTracker.resume(this)
    }

    fun completeFocusSession() {
        focusSessionTracker.complete(this, FocusSession.STATUS_COMPLETED)
    }

    fun cancelFocusSession() {
        focusSessionTracker.cancel(this)
    }

    fun dismissFocusIntervention() {
        focusSessionTracker.dismissIntervention()
    }

    // =========================================================================
    // Phase 14: XP, Streaks, Monitoring Coverage & Achievements Operations
    // =========================================================================

    @Synchronized
    fun onAccessibilityServiceConnectionChanged(connected: Boolean) {
        monitoringCoverageTracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = connected,
            newTrackingEnabled = preferencesManager.isGlobalTrackingEnabled.value && !preferencesManager.isTrackingPaused.value,
            interruptionReason = if (!connected) "SERVICE_DISCONNECTED" else null
        )
        refreshProgressionState()
    }

    @Synchronized
    fun onScreenStateChanged(screenOn: Boolean) {
        monitoringCoverageTracker.onStateChanged(
            dbHelper = dbHelper,
            newScreenOn = screenOn,
            interruptionReason = if (!screenOn) "SCREEN_OFF" else null
        )
        refreshProgressionState()
    }

    @Synchronized
    fun recordMonitoringHeartbeat() {
        monitoringCoverageTracker.recordHeartbeat(dbHelper)
        refreshProgressionState()
    }

    @Synchronized
    fun recordSupportedAppDetection(
        app: TargetApp,
        eventType: String,
        fingerprint: String? = null
    ) {
        monitoringCoverageTracker.recordSupportedAppDetection(
            dbHelper = dbHelper,
            platform = app.name,
            eventType = eventType,
            detectionStatus = "CONFIRMED",
            contentFingerprint = fingerprint
        )
        refreshProgressionState()
    }

    /**
     * Evaluates past calendar days for streak qualification, goal XP, and mindful-day XP.
     * Guaranteed: Never evaluates or finalizes todayDateKey early.
     */
    @Synchronized
    fun finalizeHistoricalProgression(todayDateKey: String = DateTimeUtils.getTodayDateString()) {
        val last7 = dbHelper.getLast7Days()
        for (summary in last7) {
            val date = summary.dateString
            if (date >= todayDateKey) continue
            val resetsUsed = preferencesManager.getDailyResetUsage(date)
            dbHelper.evaluateAndFinalizePastDay(
                dateKey = date,
                todayDateKey = todayDateKey,
                dailyResetCount = resetsUsed
            )
        }
    }

    @Synchronized
    fun evaluatePastDayProgression(
        dateKey: String,
        todayDateKey: String = DateTimeUtils.getTodayDateString(),
        evaluationTimestamp: Long = System.currentTimeMillis()
    ): DailyProgressionEvaluation {
        val prevLevel = _userProgress.value.currentLevel
        val prevStreak = _userProgress.value.currentStreak
        val resetsUsed = preferencesManager.getDailyResetUsage(dateKey)
        val eval = dbHelper.evaluateAndFinalizePastDay(
            dateKey = dateKey,
            todayDateKey = todayDateKey,
            dailyResetCount = resetsUsed,
            evaluationTimestamp = evaluationTimestamp
        )
        refreshProgressionState(todayDateKey)
        val updatedProgress = _userProgress.value
        if (updatedProgress.currentLevel > prevLevel) {
            personalityEngine.onLevelUp(updatedProgress.currentLevel, updatedProgress.levelTitle)
        } else if (updatedProgress.currentStreak > prevStreak && updatedProgress.currentStreak in listOf(3, 7, 30)) {
            personalityEngine.onStreakMilestoneReached(updatedProgress.currentStreak)
        }
        return eval
    }

    @Synchronized
    fun refreshProgressionState(todayDateKey: String = DateTimeUtils.getTodayDateString()) {
        _userProgress.value = dbHelper.getUserProgress()
        _achievements.value = dbHelper.getAllAchievements()
        _recentXpTransactions.value = dbHelper.getXpTransactions(limit = 50)
        _streakRecords.value = dbHelper.getAllStreakRecords()
        _todayMonitoringCoverage.value = dbHelper.recalculateAndSaveDailyCoverage(todayDateKey)
        _todayGoalSnapshots.value = dbHelper.getGoalSnapshotsForDate(todayDateKey, rebuildFromHistory = true)
    }
}
