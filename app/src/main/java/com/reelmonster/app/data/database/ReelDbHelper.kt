package com.reelmonster.app.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.domain.model.DailySummary
import com.reelmonster.app.domain.model.ReelLogItem
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.domain.model.ViewingSession
import com.reelmonster.app.domain.model.focus.FocusPeriodStats
import com.reelmonster.app.domain.model.focus.FocusSession
import com.reelmonster.app.domain.model.focus.FocusType
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.DailyGoalHistoryEvent
import com.reelmonster.app.domain.model.goal.DailyGoalRecord
import com.reelmonster.app.domain.model.goal.DailyGoalSnapshot
import com.reelmonster.app.domain.model.goal.DailyUsageMetrics
import com.reelmonster.app.domain.model.goal.GoalEligibilityEvaluator
import com.reelmonster.app.domain.model.goal.GoalHistoryEventType
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.domain.model.monitoring.ContinuityStatus
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.DailyMonitoringCoverage
import com.reelmonster.app.domain.model.monitoring.EstimatedCoverageStatus
import com.reelmonster.app.domain.model.monitoring.MonitoringHeartbeat
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import com.reelmonster.app.domain.model.monitoring.SupportedAppDetectionEvent
import com.reelmonster.app.domain.model.progression.Achievement
import com.reelmonster.app.domain.model.progression.AchievementCatalog
import com.reelmonster.app.domain.model.progression.AchievementCategory
import com.reelmonster.app.domain.model.progression.DailyProgressionEvaluation
import com.reelmonster.app.domain.model.progression.LevelCalculator
import com.reelmonster.app.domain.model.progression.ProgressionDecisionEngine
import com.reelmonster.app.domain.model.progression.StreakEvaluationStatus
import com.reelmonster.app.domain.model.progression.StreakRecord
import com.reelmonster.app.domain.model.progression.UserProgress
import com.reelmonster.app.domain.model.progression.XpEventType
import com.reelmonster.app.domain.model.progression.XpTransaction
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID

/**
 * ReelDbHelper
 *
 * Local-first persistent SQLite engine for Reel Monster.
 * Compatible with Android API 26+ (minSdk 26).
 * Uses Android SQLite transactions instead of SQLite 3.24+ UPSERT syntax.
 */
class ReelDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "reel_monster.db"
        const val DATABASE_VERSION = 6

        const val TABLE_EVENTS = "reel_events"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_APP = "target_app"
        const val COL_MODE = "tracking_mode"
        const val COL_DATE = "session_date"

        const val TABLE_DAILY = "daily_aggregates"
        const val COL_DAILY_DATE = "date_key"
        const val COL_DAILY_TOTAL = "total_reels"
        const val COL_DAILY_IG = "ig_count"
        const val COL_DAILY_YT = "yt_count"

        const val TABLE_VIEWING_SESSIONS = "viewing_sessions"
        const val COL_SESSION_ID = "id"
        const val COL_SESSION_APP = "target_app"
        const val COL_SESSION_FP = "fingerprint"
        const val COL_SESSION_START = "start_timestamp"
        const val COL_SESSION_END = "end_timestamp"
        const val COL_SESSION_DURATION = "duration_ms"
        const val COL_SESSION_STATUS = "completion_status"
        const val COL_SESSION_DATE = "session_date"

        // Phase 12: Daily Goals & Goal History Tables
        const val TABLE_GOALS = "daily_goals"
        const val COL_GOAL_ID = "id"
        const val COL_GOAL_TYPE = "goal_type"
        const val COL_GOAL_SCOPE = "platform_scope"
        const val COL_GOAL_TARGET = "target_value"
        const val COL_GOAL_ENABLED = "is_enabled"
        const val COL_GOAL_CREATED_AT = "created_at"
        const val COL_GOAL_UPDATED_AT = "updated_at"

        const val TABLE_GOAL_RECORDS = "daily_goal_records"
        const val COL_REC_ID = "id"
        const val COL_REC_DATE = "date_key"
        const val COL_REC_TYPE = "goal_type"
        const val COL_REC_SCOPE = "platform_scope"
        const val COL_REC_TARGET = "target_value"
        const val COL_REC_ACTUAL = "actual_value"
        const val COL_REC_SUCCESS = "was_successful"
        const val COL_REC_TIMESTAMP = "recorded_at"

        // Phase 13: Focus Mode Sessions Table
        const val TABLE_FOCUS_SESSIONS = "focus_sessions"
        const val COL_FOCUS_ID = "id"
        const val COL_FOCUS_TYPE = "session_type"
        const val COL_FOCUS_TARGET_DURATION = "target_duration_ms"
        const val COL_FOCUS_ACTUAL_DURATION = "actual_duration_ms"
        const val COL_FOCUS_START = "start_timestamp"
        const val COL_FOCUS_END = "end_timestamp"
        const val COL_FOCUS_STATUS = "completion_status"
        const val COL_FOCUS_DATE = "date_string"

        // Phase 14 (8.1): Monitoring Intervals
        const val TABLE_MONITORING_INTERVALS = "monitoring_intervals"
        const val COL_INT_ID = "id"
        const val COL_INT_DATE = "date_key"
        const val COL_INT_START = "start_timestamp"
        const val COL_INT_END = "end_timestamp"
        const val COL_INT_MONO_START = "monotonic_start_ms"
        const val COL_INT_MONO_END = "monotonic_end_ms"
        const val COL_INT_SERVICE_CONN = "service_connected"
        const val COL_INT_TRACKING_EN = "tracking_enabled"
        const val COL_INT_SCREEN_ON = "screen_on"
        const val COL_INT_INTERRUPTION = "interruption_reason"
        const val COL_INT_VERIFIED_DUR = "estimated_verified_duration_ms"
        const val COL_INT_CONTINUITY = "continuity_status"
        const val COL_INT_BOOT_ID = "boot_session_id"
        const val COL_INT_IS_ACTIVE = "is_active"

        // Phase 14 (8.2): Monitoring Heartbeats
        const val TABLE_MONITORING_HEARTBEATS = "monitoring_heartbeats"
        const val COL_HB_ID = "id"
        const val COL_HB_TIMESTAMP = "timestamp"
        const val COL_HB_MONO_MS = "monotonic_ms"
        const val COL_HB_DATE = "date_key"
        const val COL_HB_SERVICE_CONN = "service_connected"
        const val COL_HB_TRACKING_EN = "tracking_enabled"
        const val COL_HB_SCREEN_ON = "screen_on"
        const val COL_HB_CONTINUITY = "continuity_status"
        const val COL_HB_BOOT_ID = "boot_session_id"

        // Phase 14 Fix 1: System Metadata Table for Migration Cutover
        const val TABLE_SYSTEM_METADATA = "system_metadata"
        const val COL_META_KEY = "meta_key"
        const val COL_META_VALUE = "meta_value"
        const val COL_META_UPDATED_AT = "updated_at"

        const val KEY_PHASE14_MIGRATION_TS = "phase14_migration_timestamp"
        const val KEY_PHASE14_MIGRATION_TZ = "phase14_migration_timezone"
        const val KEY_PHASE14_MIGRATION_DATE = "phase14_migration_date"
        const val KEY_PHASE14_FIRST_ELIGIBLE_DATE = "phase14_first_eligible_date"

        // Phase 14 (8.3): Supported-App Detection Events
        const val TABLE_DETECTION_EVENTS = "supported_app_detection_events"
        const val COL_DET_ID = "id"
        const val COL_DET_TIMESTAMP = "timestamp"
        const val COL_DET_DATE = "date_key"
        const val COL_DET_PLATFORM = "platform"
        const val COL_DET_EVENT_TYPE = "event_type"
        const val COL_DET_STATUS = "detection_status"
        const val COL_DET_FINGERPRINT = "content_fingerprint"

        // Phase 14 (8.4): Daily Monitoring Coverage
        const val TABLE_DAILY_COVERAGE = "daily_monitoring_coverage"
        const val COL_COV_DATE = "date_key"
        const val COL_COV_SERVICE_MS = "service_connected_ms"
        const val COL_COV_TRACKING_MS = "tracking_enabled_ms"
        const val COL_COV_SCREEN_MS = "screen_on_ms"
        const val COL_COV_VERIFIED_MS = "estimated_verified_monitoring_ms"
        const val COL_COV_HB_COUNT = "heartbeat_count"
        const val COL_COV_DET_COUNT = "detection_event_count"
        const val COL_COV_INT_COUNT = "interruption_count"
        const val COL_COV_REC_COUNT = "recovery_count"
        const val COL_COV_STATUS = "coverage_status"
        const val COL_COV_CALC_AT = "calculated_at"

        // Phase 14 (8.5): Append-Only Timestamped Daily Goal History
        const val TABLE_GOAL_HISTORY = "daily_goal_history"
        const val COL_GH_ID = "id"
        const val COL_GH_EVENT_ID = "event_id"
        const val COL_GH_GOAL_ID = "goal_id"
        const val COL_GH_GOAL_TYPE = "goal_type"
        const val COL_GH_SCOPE = "platform_scope"
        const val COL_GH_EVENT_TYPE = "event_type"
        const val COL_GH_PREV_VAL = "previous_value"
        const val COL_GH_NEW_VAL = "new_value"
        const val COL_GH_EFFECTIVE_TS = "effective_timestamp"
        const val COL_GH_DATE = "date_key"
        const val COL_GH_TIMEZONE = "timezone_id"
        const val COL_GH_CREATED_AT = "created_at"

        // Phase 14 (8.6): Daily Goal Snapshots
        const val TABLE_GOAL_SNAPSHOTS = "daily_goal_snapshots"
        const val COL_GS_ID = "id"
        const val COL_GS_DATE = "date_key"
        const val COL_GS_GOAL_ID = "goal_id"
        const val COL_GS_GOAL_TYPE = "goal_type"
        const val COL_GS_SCOPE = "platform_scope"
        const val COL_GS_ENABLED_MIDNIGHT = "enabled_at_midnight"
        const val COL_GS_TARGET_MIDNIGHT = "target_at_midnight"
        const val COL_GS_STRICTEST_TARGET = "strictest_target"
        const val COL_GS_FIRST_ACT_TS = "first_activation_timestamp"
        const val COL_GS_DISABLED_DAY = "was_disabled_during_day"
        const val COL_GS_REENABLED_DAY = "was_reenabled_midday"
        const val COL_GS_VIOLATED = "was_violated"
        const val COL_GS_PROG_ELIGIBLE = "progression_eligible"
        const val COL_GS_USAGE_MIDNIGHT = "usage_since_midnight"
        const val COL_GS_USAGE_ACTIVATION = "usage_since_activation"
        const val COL_GS_LAST_HIST_ID = "last_history_event_id"
        const val COL_GS_UPDATED_AT = "updated_at"

        // Phase 14 (8.6): XP Transactions
        const val TABLE_XP_TRANSACTIONS = "xp_transactions"
        const val COL_XP_ID = "id"
        const val COL_XP_EVENT_ID = "event_id"
        const val COL_XP_EVENT_TYPE = "event_type"
        const val COL_XP_AMOUNT = "xp_amount"
        const val COL_XP_DESC = "description"
        const val COL_XP_CREATED_AT = "created_at"
        const val COL_XP_LOCAL_DATE = "local_date"

        // Phase 14 (8.6): Singleton User Progress
        const val TABLE_USER_PROGRESS = "user_progress"
        const val COL_UP_ID = "id"
        const val COL_UP_TOTAL_XP = "total_xp"
        const val COL_UP_LEVEL = "current_level"
        const val COL_UP_CURR_STREAK = "current_streak"
        const val COL_UP_LONG_STREAK = "longest_streak"
        const val COL_UP_LAST_QUAL_DATE = "last_qualified_date"
        const val COL_UP_UPDATED_AT = "updated_at"

        // Phase 14 (8.6): Achievements
        const val TABLE_ACHIEVEMENTS = "achievements"
        const val COL_ACH_ID = "achievement_id"
        const val COL_ACH_CATEGORY = "category"
        const val COL_ACH_TITLE = "title"
        const val COL_ACH_DESC = "description"
        const val COL_ACH_TARGET = "target_progress"
        const val COL_ACH_CURRENT = "current_progress"
        const val COL_ACH_UNLOCKED = "is_unlocked"
        const val COL_ACH_UNLOCKED_AT = "unlocked_at"
        const val COL_ACH_XP_REWARD = "xp_reward"

        // Phase 14 (8.6): Streak Records
        const val TABLE_STREAK_RECORDS = "streak_records"
        const val COL_SR_DATE = "local_date"
        const val COL_SR_QUALIFIED = "is_qualified"
        const val COL_SR_REASON = "qualifying_reason"
        const val COL_SR_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_APP TEXT NOT NULL,
                $COL_MODE TEXT NOT NULL,
                $COL_DATE TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_DAILY (
                $COL_DAILY_DATE TEXT PRIMARY KEY,
                $COL_DAILY_TOTAL INTEGER NOT NULL DEFAULT 0,
                $COL_DAILY_IG INTEGER NOT NULL DEFAULT 0,
                $COL_DAILY_YT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_VIEWING_SESSIONS (
                $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_APP TEXT NOT NULL,
                $COL_SESSION_FP TEXT,
                $COL_SESSION_START INTEGER NOT NULL,
                $COL_SESSION_END INTEGER NOT NULL,
                $COL_SESSION_DURATION INTEGER NOT NULL,
                $COL_SESSION_STATUS TEXT NOT NULL,
                $COL_SESSION_DATE TEXT NOT NULL
            )
            """.trimIndent()
        )

        createGoalTables(db)
        insertDefaultGoals(db)
        createFocusTables(db)
        createPhase14Tables(db)
        seedPhase14Defaults(db)
        performPhase14CutoverInternal(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_VIEWING_SESSIONS (
                    $COL_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_SESSION_APP TEXT NOT NULL,
                    $COL_SESSION_FP TEXT,
                    $COL_SESSION_START INTEGER NOT NULL,
                    $COL_SESSION_END INTEGER NOT NULL,
                    $COL_SESSION_DURATION INTEGER NOT NULL,
                    $COL_SESSION_STATUS TEXT NOT NULL,
                    $COL_SESSION_DATE TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            createGoalTables(db)
            insertDefaultGoals(db)
        }
        if (oldVersion < 4) {
            createFocusTables(db)
        }
        if (oldVersion < 5) {
            createPhase14Tables(db)
            seedPhase14Defaults(db)
        }
        if (oldVersion < 6) {
            upgradeToVersion6(db)
        }
    }

    private fun upgradeToVersion6(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            addColumnIfNotExists(db, TABLE_MONITORING_INTERVALS, COL_INT_BOOT_ID, "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, TABLE_MONITORING_INTERVALS, COL_INT_IS_ACTIVE, "INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_intervals_boot ON $TABLE_MONITORING_INTERVALS($COL_INT_BOOT_ID)")

            addColumnIfNotExists(db, TABLE_MONITORING_HEARTBEATS, COL_HB_BOOT_ID, "TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_heartbeats_boot ON $TABLE_MONITORING_HEARTBEATS($COL_HB_BOOT_ID)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SYSTEM_METADATA (
                    $COL_META_KEY TEXT PRIMARY KEY,
                    $COL_META_VALUE TEXT NOT NULL,
                    $COL_META_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )

            performPhase14CutoverInternal(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun addColumnIfNotExists(db: SQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        var exists = false
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1)
                if (name.equals(columnName, ignoreCase = true)) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDef")
        }
    }

    private fun createPhase14Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MONITORING_INTERVALS (
                $COL_INT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_INT_DATE TEXT NOT NULL,
                $COL_INT_START INTEGER NOT NULL,
                $COL_INT_END INTEGER NOT NULL,
                $COL_INT_MONO_START INTEGER NOT NULL,
                $COL_INT_MONO_END INTEGER NOT NULL,
                $COL_INT_SERVICE_CONN INTEGER NOT NULL,
                $COL_INT_TRACKING_EN INTEGER NOT NULL,
                $COL_INT_SCREEN_ON INTEGER NOT NULL,
                $COL_INT_INTERRUPTION TEXT,
                $COL_INT_VERIFIED_DUR INTEGER NOT NULL DEFAULT 0,
                $COL_INT_CONTINUITY TEXT NOT NULL,
                $COL_INT_BOOT_ID TEXT NOT NULL DEFAULT '',
                $COL_INT_IS_ACTIVE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_intervals_date ON $TABLE_MONITORING_INTERVALS($COL_INT_DATE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_intervals_boot ON $TABLE_MONITORING_INTERVALS($COL_INT_BOOT_ID)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MONITORING_HEARTBEATS (
                $COL_HB_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HB_TIMESTAMP INTEGER NOT NULL,
                $COL_HB_MONO_MS INTEGER NOT NULL,
                $COL_HB_DATE TEXT NOT NULL,
                $COL_HB_SERVICE_CONN INTEGER NOT NULL,
                $COL_HB_TRACKING_EN INTEGER NOT NULL,
                $COL_HB_SCREEN_ON INTEGER NOT NULL,
                $COL_HB_CONTINUITY TEXT NOT NULL,
                $COL_HB_BOOT_ID TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_heartbeats_date ON $TABLE_MONITORING_HEARTBEATS($COL_HB_DATE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_monitoring_heartbeats_boot ON $TABLE_MONITORING_HEARTBEATS($COL_HB_BOOT_ID)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DETECTION_EVENTS (
                $COL_DET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DET_TIMESTAMP INTEGER NOT NULL,
                $COL_DET_DATE TEXT NOT NULL,
                $COL_DET_PLATFORM TEXT NOT NULL,
                $COL_DET_EVENT_TYPE TEXT NOT NULL,
                $COL_DET_STATUS TEXT NOT NULL,
                $COL_DET_FINGERPRINT TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_detection_events_date ON $TABLE_DETECTION_EVENTS($COL_DET_DATE)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DAILY_COVERAGE (
                $COL_COV_DATE TEXT PRIMARY KEY,
                $COL_COV_SERVICE_MS INTEGER NOT NULL DEFAULT 0,
                $COL_COV_TRACKING_MS INTEGER NOT NULL DEFAULT 0,
                $COL_COV_SCREEN_MS INTEGER NOT NULL DEFAULT 0,
                $COL_COV_VERIFIED_MS INTEGER NOT NULL DEFAULT 0,
                $COL_COV_HB_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_COV_DET_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_COV_INT_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_COV_REC_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_COV_STATUS TEXT NOT NULL DEFAULT 'UNVERIFIED',
                $COL_COV_CALC_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GOAL_HISTORY (
                $COL_GH_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GH_EVENT_ID TEXT UNIQUE NOT NULL,
                $COL_GH_GOAL_ID INTEGER NOT NULL,
                $COL_GH_GOAL_TYPE TEXT NOT NULL,
                $COL_GH_SCOPE TEXT NOT NULL,
                $COL_GH_EVENT_TYPE TEXT NOT NULL,
                $COL_GH_PREV_VAL TEXT,
                $COL_GH_NEW_VAL TEXT NOT NULL,
                $COL_GH_EFFECTIVE_TS INTEGER NOT NULL,
                $COL_GH_DATE TEXT NOT NULL,
                $COL_GH_TIMEZONE TEXT NOT NULL,
                $COL_GH_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_goal_history_date ON $TABLE_GOAL_HISTORY($COL_GH_DATE, $COL_GH_GOAL_ID)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GOAL_SNAPSHOTS (
                $COL_GS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GS_DATE TEXT NOT NULL,
                $COL_GS_GOAL_ID INTEGER NOT NULL,
                $COL_GS_GOAL_TYPE TEXT NOT NULL,
                $COL_GS_SCOPE TEXT NOT NULL,
                $COL_GS_ENABLED_MIDNIGHT INTEGER NOT NULL,
                $COL_GS_TARGET_MIDNIGHT INTEGER,
                $COL_GS_STRICTEST_TARGET INTEGER NOT NULL,
                $COL_GS_FIRST_ACT_TS INTEGER,
                $COL_GS_DISABLED_DAY INTEGER NOT NULL DEFAULT 0,
                $COL_GS_REENABLED_DAY INTEGER NOT NULL DEFAULT 0,
                $COL_GS_VIOLATED INTEGER NOT NULL DEFAULT 0,
                $COL_GS_PROG_ELIGIBLE INTEGER NOT NULL DEFAULT 0,
                $COL_GS_USAGE_MIDNIGHT INTEGER NOT NULL DEFAULT 0,
                $COL_GS_USAGE_ACTIVATION INTEGER NOT NULL DEFAULT 0,
                $COL_GS_LAST_HIST_ID INTEGER,
                $COL_GS_UPDATED_AT INTEGER NOT NULL,
                UNIQUE($COL_GS_DATE, $COL_GS_GOAL_TYPE, $COL_GS_SCOPE)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_XP_TRANSACTIONS (
                $COL_XP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_XP_EVENT_ID TEXT UNIQUE NOT NULL,
                $COL_XP_EVENT_TYPE TEXT NOT NULL,
                $COL_XP_AMOUNT INTEGER NOT NULL,
                $COL_XP_DESC TEXT NOT NULL,
                $COL_XP_CREATED_AT INTEGER NOT NULL,
                $COL_XP_LOCAL_DATE TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USER_PROGRESS (
                $COL_UP_ID INTEGER PRIMARY KEY CHECK ($COL_UP_ID = 1),
                $COL_UP_TOTAL_XP INTEGER NOT NULL DEFAULT 0,
                $COL_UP_LEVEL INTEGER NOT NULL DEFAULT 1,
                $COL_UP_CURR_STREAK INTEGER NOT NULL DEFAULT 0,
                $COL_UP_LONG_STREAK INTEGER NOT NULL DEFAULT 0,
                $COL_UP_LAST_QUAL_DATE TEXT,
                $COL_UP_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ACHIEVEMENTS (
                $COL_ACH_ID TEXT PRIMARY KEY,
                $COL_ACH_CATEGORY TEXT NOT NULL,
                $COL_ACH_TITLE TEXT NOT NULL,
                $COL_ACH_DESC TEXT NOT NULL,
                $COL_ACH_TARGET INTEGER NOT NULL,
                $COL_ACH_CURRENT INTEGER NOT NULL DEFAULT 0,
                $COL_ACH_UNLOCKED INTEGER NOT NULL DEFAULT 0,
                $COL_ACH_UNLOCKED_AT INTEGER,
                $COL_ACH_XP_REWARD INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STREAK_RECORDS (
                $COL_SR_DATE TEXT PRIMARY KEY,
                $COL_SR_QUALIFIED INTEGER NOT NULL,
                $COL_SR_REASON TEXT NOT NULL,
                $COL_SR_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SYSTEM_METADATA (
                $COL_META_KEY TEXT PRIMARY KEY,
                $COL_META_VALUE TEXT NOT NULL,
                $COL_META_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun seedPhase14Defaults(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val upCv = ContentValues().apply {
            put(COL_UP_ID, 1)
            put(COL_UP_TOTAL_XP, 0)
            put(COL_UP_LEVEL, 1)
            put(COL_UP_CURR_STREAK, 0)
            put(COL_UP_LONG_STREAK, 0)
            putNull(COL_UP_LAST_QUAL_DATE)
            put(COL_UP_UPDATED_AT, now)
        }
        db.insertWithOnConflict(TABLE_USER_PROGRESS, null, upCv, SQLiteDatabase.CONFLICT_IGNORE)

        for (ach in AchievementCatalog.DEFAULT_ACHIEVEMENTS) {
            val achCv = ContentValues().apply {
                put(COL_ACH_ID, ach.achievementId)
                put(COL_ACH_CATEGORY, ach.category.name)
                put(COL_ACH_TITLE, ach.title)
                put(COL_ACH_DESC, ach.description)
                put(COL_ACH_TARGET, ach.targetProgress)
                put(COL_ACH_CURRENT, ach.currentProgress)
                put(COL_ACH_UNLOCKED, if (ach.isUnlocked) 1 else 0)
                if (ach.unlockedAt != null) put(COL_ACH_UNLOCKED_AT, ach.unlockedAt) else putNull(COL_ACH_UNLOCKED_AT)
                put(COL_ACH_XP_REWARD, ach.xpReward)
            }
            db.insertWithOnConflict(TABLE_ACHIEVEMENTS, null, achCv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getSystemMetadata(key: String): String? {
        return getSystemMetadataInternal(readableDatabase, key)
    }

    fun setSystemMetadata(key: String, value: String): Boolean {
        val db = writableDatabase
        return setSystemMetadataInternal(db, key, value, System.currentTimeMillis())
    }

    private fun getSystemMetadataInternal(db: SQLiteDatabase, key: String): String? {
        val cursor = db.rawQuery(
            "SELECT $COL_META_VALUE FROM $TABLE_SYSTEM_METADATA WHERE $COL_META_KEY = ?",
            arrayOf(key)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun setSystemMetadataInternal(db: SQLiteDatabase, key: String, value: String, updatedAt: Long): Boolean {
        val cv = ContentValues().apply {
            put(COL_META_KEY, key)
            put(COL_META_VALUE, value)
            put(COL_META_UPDATED_AT, updatedAt)
        }
        val rowId = db.insertWithOnConflict(TABLE_SYSTEM_METADATA, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    private fun performPhase14CutoverInternal(db: SQLiteDatabase) {
        val nowMs = System.currentTimeMillis()
        val tzId = TimeZone.getDefault().id
        val todayKey = DateTimeUtils.getTodayDateString(Date(nowMs))
        val tomorrowCal = Calendar.getInstance().apply {
            time = Date(nowMs)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowKey = DateTimeUtils.getTodayDateString(tomorrowCal.time)

        val existingCutoverTs = getSystemMetadataInternal(db, KEY_PHASE14_MIGRATION_TS)
        val migrationTs: Long
        val migrationDateKey: String
        val firstEligibleDateKey: String

        if (existingCutoverTs != null) {
            migrationTs = existingCutoverTs.toLongOrNull() ?: nowMs
            migrationDateKey = getSystemMetadataInternal(db, KEY_PHASE14_MIGRATION_DATE) ?: todayKey
            firstEligibleDateKey = getSystemMetadataInternal(db, KEY_PHASE14_FIRST_ELIGIBLE_DATE) ?: tomorrowKey
        } else {
            migrationTs = nowMs
            migrationDateKey = todayKey
            firstEligibleDateKey = tomorrowKey
            setSystemMetadataInternal(db, KEY_PHASE14_MIGRATION_TS, migrationTs.toString(), nowMs)
            setSystemMetadataInternal(db, KEY_PHASE14_MIGRATION_TZ, tzId, nowMs)
            setSystemMetadataInternal(db, KEY_PHASE14_MIGRATION_DATE, migrationDateKey, nowMs)
            setSystemMetadataInternal(db, KEY_PHASE14_FIRST_ELIGIBLE_DATE, firstEligibleDateKey, nowMs)
        }

        // 1. Sanitize old seed_goal_% events that may have had backdated timestamps
        db.execSQL("DELETE FROM $TABLE_GOAL_HISTORY WHERE $COL_GH_EVENT_ID LIKE 'seed_goal_%'")

        // 2. Remove snapshots built for dates prior to migrationDateKey
        db.execSQL("DELETE FROM $TABLE_GOAL_SNAPSHOTS WHERE $COL_GS_DATE < ?", arrayOf(migrationDateKey))

        // 3. Insert clean cutover event for each goal in daily_goals using true migrationTs (never backdated)
        val cursor = db.rawQuery(
            "SELECT $COL_GOAL_ID, $COL_GOAL_TYPE, $COL_GOAL_SCOPE, $COL_GOAL_TARGET, $COL_GOAL_ENABLED FROM $TABLE_GOALS",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val gId = it.getLong(0)
                val gType = it.getString(1)
                val gScope = it.getString(2)
                val gTarget = it.getInt(3)
                val gEnabled = it.getInt(4) == 1
                val eventId = "phase14_cutover_${gType}_${gScope}"

                val histCv = ContentValues().apply {
                    put(COL_GH_EVENT_ID, eventId)
                    put(COL_GH_GOAL_ID, gId)
                    put(COL_GH_GOAL_TYPE, gType)
                    put(COL_GH_SCOPE, gScope)
                    put(COL_GH_EVENT_TYPE, GoalHistoryEventType.CREATED.name)
                    putNull(COL_GH_PREV_VAL)
                    put(COL_GH_NEW_VAL, "$gTarget:$gEnabled")
                    put(COL_GH_EFFECTIVE_TS, migrationTs)
                    put(COL_GH_DATE, migrationDateKey)
                    put(COL_GH_TIMEZONE, tzId)
                    put(COL_GH_CREATED_AT, migrationTs)
                }
                val histRowId = db.insertWithOnConflict(TABLE_GOAL_HISTORY, null, histCv, SQLiteDatabase.CONFLICT_REPLACE)

                // Requirement 5: Migration day snapshot is explicitly NOT progression-eligible
                val snapCv = ContentValues().apply {
                    put(COL_GS_DATE, migrationDateKey)
                    put(COL_GS_GOAL_ID, gId)
                    put(COL_GS_GOAL_TYPE, gType)
                    put(COL_GS_SCOPE, gScope)
                    put(COL_GS_ENABLED_MIDNIGHT, 0) // Goal was not enabled at midnight of migration day
                    putNull(COL_GS_TARGET_MIDNIGHT)
                    put(COL_GS_STRICTEST_TARGET, gTarget)
                    if (gEnabled) put(COL_GS_FIRST_ACT_TS, migrationTs) else putNull(COL_GS_FIRST_ACT_TS)
                    put(COL_GS_DISABLED_DAY, 0)
                    put(COL_GS_REENABLED_DAY, 0)
                    put(COL_GS_VIOLATED, 0)
                    put(COL_GS_PROG_ELIGIBLE, 0) // Explicitly false for migration day!
                    put(COL_GS_USAGE_MIDNIGHT, 0)
                    put(COL_GS_USAGE_ACTIVATION, 0)
                    if (histRowId != -1L) put(COL_GS_LAST_HIST_ID, histRowId)
                    put(COL_GS_UPDATED_AT, migrationTs)
                }
                db.insertWithOnConflict(TABLE_GOAL_SNAPSHOTS, null, snapCv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun getStartOfDayTimestamp(dateKey: String): Long {
        val parsed = DateTimeUtils.parseDate(dateKey) ?: return 0L
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun createFocusTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FOCUS_SESSIONS (
                $COL_FOCUS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FOCUS_TYPE TEXT NOT NULL,
                $COL_FOCUS_TARGET_DURATION INTEGER NOT NULL,
                $COL_FOCUS_ACTUAL_DURATION INTEGER NOT NULL,
                $COL_FOCUS_START INTEGER NOT NULL,
                $COL_FOCUS_END INTEGER NOT NULL,
                $COL_FOCUS_STATUS TEXT NOT NULL,
                $COL_FOCUS_DATE TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createGoalTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GOALS (
                $COL_GOAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_GOAL_TYPE TEXT NOT NULL,
                $COL_GOAL_SCOPE TEXT NOT NULL,
                $COL_GOAL_TARGET INTEGER NOT NULL,
                $COL_GOAL_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_GOAL_CREATED_AT INTEGER NOT NULL,
                $COL_GOAL_UPDATED_AT INTEGER NOT NULL,
                UNIQUE($COL_GOAL_TYPE, $COL_GOAL_SCOPE)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GOAL_RECORDS (
                $COL_REC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_REC_DATE TEXT NOT NULL,
                $COL_REC_TYPE TEXT NOT NULL,
                $COL_REC_SCOPE TEXT NOT NULL,
                $COL_REC_TARGET INTEGER NOT NULL,
                $COL_REC_ACTUAL INTEGER NOT NULL,
                $COL_REC_SUCCESS INTEGER NOT NULL,
                $COL_REC_TIMESTAMP INTEGER NOT NULL,
                UNIQUE($COL_REC_DATE, $COL_REC_TYPE, $COL_REC_SCOPE)
            )
            """.trimIndent()
        )
    }

    private fun insertDefaultGoals(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val defaults = listOf(
            DailyGoal(type = GoalType.REEL_COUNT, scope = GoalScope.COMBINED, targetValue = 30, isEnabled = true),
            DailyGoal(type = GoalType.VIEWING_TIME_MINUTES, scope = GoalScope.COMBINED, targetValue = 30, isEnabled = true),
            DailyGoal(type = GoalType.REEL_COUNT, scope = GoalScope.INSTAGRAM, targetValue = 15, isEnabled = false),
            DailyGoal(type = GoalType.REEL_COUNT, scope = GoalScope.YOUTUBE, targetValue = 15, isEnabled = false),
            DailyGoal(type = GoalType.VIEWING_TIME_MINUTES, scope = GoalScope.INSTAGRAM, targetValue = 15, isEnabled = false),
            DailyGoal(type = GoalType.VIEWING_TIME_MINUTES, scope = GoalScope.YOUTUBE, targetValue = 15, isEnabled = false)
        )
        for (g in defaults) {
            val cv = ContentValues().apply {
                put(COL_GOAL_TYPE, g.type.name)
                put(COL_GOAL_SCOPE, g.scope.name)
                put(COL_GOAL_TARGET, g.targetValue)
                put(COL_GOAL_ENABLED, if (g.isEnabled) 1 else 0)
                put(COL_GOAL_CREATED_AT, now)
                put(COL_GOAL_UPDATED_AT, now)
            }
            db.insertWithOnConflict(TABLE_GOALS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    @Synchronized
    fun insertReelEvent(
        app: TargetApp,
        mode: TrackingMode,
        timestamp: Long = System.currentTimeMillis(),
        dateStr: String = DateTimeUtils.getTodayDateString()
    ): Long {
        val db = writableDatabase
        var insertedRowId = -1L

        db.beginTransaction()
        try {
            // 1. Insert individual event record
            val eventValues = ContentValues().apply {
                put(COL_TIMESTAMP, timestamp)
                put(COL_APP, app.name)
                put(COL_MODE, mode.name)
                put(COL_DATE, dateStr)
            }
            insertedRowId = db.insert(TABLE_EVENTS, null, eventValues)

            // 2. Safely upsert daily aggregate across all SQLite / Android versions (API 26+)
            val isIg = app == TargetApp.INSTAGRAM
            val isYt = app == TargetApp.YOUTUBE

            val cursor = db.rawQuery(
                "SELECT $COL_DAILY_TOTAL, $COL_DAILY_IG, $COL_DAILY_YT FROM $TABLE_DAILY WHERE $COL_DAILY_DATE = ?",
                arrayOf(dateStr)
            )

            val rowExists = cursor.moveToFirst()
            if (rowExists) {
                val currentTotal = cursor.getInt(0)
                val currentIg = cursor.getInt(1)
                val currentYt = cursor.getInt(2)
                cursor.close()

                val updateValues = ContentValues().apply {
                    put(COL_DAILY_TOTAL, currentTotal + 1)
                    put(COL_DAILY_IG, currentIg + (if (isIg) 1 else 0))
                    put(COL_DAILY_YT, currentYt + (if (isYt) 1 else 0))
                }
                db.update(TABLE_DAILY, updateValues, "$COL_DAILY_DATE = ?", arrayOf(dateStr))
            } else {
                cursor.close()

                val insertValues = ContentValues().apply {
                    put(COL_DAILY_DATE, dateStr)
                    put(COL_DAILY_TOTAL, 1)
                    put(COL_DAILY_IG, if (isIg) 1 else 0)
                    put(COL_DAILY_YT, if (isYt) 1 else 0)
                }
                db.insert(TABLE_DAILY, null, insertValues)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return insertedRowId
    }

    @Synchronized
    fun getTodayCount(): Int {
        val db = readableDatabase
        val todayStr = DateTimeUtils.getTodayDateString()
        val cursor = db.rawQuery(
            "SELECT $COL_DAILY_TOTAL FROM $TABLE_DAILY WHERE $COL_DAILY_DATE = ?",
            arrayOf(todayStr)
        )
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }

    @Synchronized
    fun getTodayPlatformBreakdown(): Pair<Int, Int> {
        val db = readableDatabase
        val todayStr = DateTimeUtils.getTodayDateString()
        val cursor = db.rawQuery(
            "SELECT $COL_DAILY_IG, $COL_DAILY_YT FROM $TABLE_DAILY WHERE $COL_DAILY_DATE = ?",
            arrayOf(todayStr)
        )
        var ig = 0
        var yt = 0
        cursor.use {
            if (it.moveToFirst()) {
                ig = it.getInt(0)
                yt = it.getInt(1)
            }
        }
        return Pair(ig, yt)
    }

    @Synchronized
    fun getLast7Days(): List<DailySummary> {
        val db = readableDatabase
        val list = mutableListOf<DailySummary>()
        val todayStr = DateTimeUtils.getTodayDateString()

        val cursor = db.rawQuery(
            """
            SELECT $COL_DAILY_DATE, $COL_DAILY_TOTAL, $COL_DAILY_IG, $COL_DAILY_YT
            FROM $TABLE_DAILY
            ORDER BY $COL_DAILY_DATE DESC
            LIMIT 7
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                val dateStr = it.getString(0)
                val total = it.getInt(1)
                val ig = it.getInt(2)
                val yt = it.getInt(3)
                val isToday = dateStr == todayStr

                list.add(
                    DailySummary(
                        dateString = dateStr,
                        dayOfWeek = dateStr.takeLast(2),
                        totalReels = total,
                        instagramCount = ig,
                        youtubeCount = yt,
                        estimatedMinutesLost = DateTimeUtils.estimateTimeMinutes(total),
                        isBingeSpike = total >= 40,
                        isToday = isToday
                    )
                )
            }
        }

        return list.reversed()
    }

    /**
     * Retrieves a complete 7-calendar-day sequence [today - 6 days, ..., today].
     * Unrecorded days are automatically populated with zero counts.
     */
    @Synchronized
    fun getSevenDayCalendarSeries(): List<DailySummary> {
        val db = readableDatabase
        val rawRecords = mutableMapOf<String, DailySummary>()
        val todayStr = DateTimeUtils.getTodayDateString()

        val cursor = db.rawQuery(
            """
            SELECT $COL_DAILY_DATE, $COL_DAILY_TOTAL, $COL_DAILY_IG, $COL_DAILY_YT
            FROM $TABLE_DAILY
            """.trimIndent(),
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val dateStr = it.getString(0)
                val total = it.getInt(1)
                val ig = it.getInt(2)
                val yt = it.getInt(3)
                rawRecords[dateStr] = DailySummary(
                    dateString = dateStr,
                    dayOfWeek = "",
                    totalReels = total,
                    instagramCount = ig,
                    youtubeCount = yt,
                    estimatedMinutesLost = DateTimeUtils.estimateTimeMinutes(total),
                    isBingeSpike = total >= 40,
                    isToday = (dateStr == todayStr)
                )
            }
        }

        return DateTimeUtils.buildSevenDaySeries(rawRecords)
    }

    @Synchronized
    fun getRecentEvents(limit: Int = 20): List<ReelLogItem> {
        val db = readableDatabase
        val list = mutableListOf<ReelLogItem>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_ID, $COL_TIMESTAMP, $COL_APP, $COL_MODE, $COL_DATE
            FROM $TABLE_EVENTS
            ORDER BY $COL_TIMESTAMP DESC, $COL_ID DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val ts = it.getLong(1)
                val appStr = it.getString(2)
                val modeStr = it.getString(3)
                val dateStr = it.getString(4)

                val app = try { TargetApp.valueOf(appStr) } catch (_: Exception) { TargetApp.INSTAGRAM }
                val mode = try { TrackingMode.valueOf(modeStr) } catch (_: Exception) { TrackingMode.LEVEL_1_MANUAL }

                list.add(
                    ReelLogItem(
                        id = id,
                        timestamp = ts,
                        targetApp = app,
                        trackingMode = mode,
                        sessionDate = dateStr
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getDailySummariesInRange(startDateStr: String, endDateStr: String): Map<String, DailySummary> {
        val db = readableDatabase
        val map = mutableMapOf<String, DailySummary>()
        val todayStr = DateTimeUtils.getTodayDateString()

        val cursor = db.rawQuery(
            """
            SELECT $COL_DAILY_DATE, $COL_DAILY_TOTAL, $COL_DAILY_IG, $COL_DAILY_YT
            FROM $TABLE_DAILY
            WHERE $COL_DAILY_DATE >= ? AND $COL_DAILY_DATE <= ?
            ORDER BY $COL_DAILY_DATE ASC
            """.trimIndent(),
            arrayOf(startDateStr, endDateStr)
        )

        cursor.use {
            while (it.moveToNext()) {
                val dateStr = it.getString(0)
                val total = it.getInt(1)
                val ig = it.getInt(2)
                val yt = it.getInt(3)

                map[dateStr] = DailySummary(
                    dateString = dateStr,
                    dayOfWeek = "",
                    totalReels = total,
                    instagramCount = ig,
                    youtubeCount = yt,
                    estimatedMinutesLost = DateTimeUtils.estimateTimeMinutes(total),
                    isBingeSpike = total >= 40,
                    isToday = (dateStr == todayStr)
                )
            }
        }
        return map
    }

    @Synchronized
    fun getEventsForDate(dateStr: String): List<ReelLogItem> {
        val db = readableDatabase
        val list = mutableListOf<ReelLogItem>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_ID, $COL_TIMESTAMP, $COL_APP, $COL_MODE, $COL_DATE
            FROM $TABLE_EVENTS
            WHERE $COL_DATE = ?
            ORDER BY $COL_TIMESTAMP DESC, $COL_ID DESC
            """.trimIndent(),
            arrayOf(dateStr)
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val ts = it.getLong(1)
                val appStr = it.getString(2)
                val modeStr = it.getString(3)
                val date = it.getString(4)

                val app = try { TargetApp.valueOf(appStr) } catch (_: Exception) { TargetApp.INSTAGRAM }
                val mode = try { TrackingMode.valueOf(modeStr) } catch (_: Exception) { TrackingMode.LEVEL_1_MANUAL }

                list.add(
                    ReelLogItem(
                        id = id,
                        timestamp = ts,
                        targetApp = app,
                        trackingMode = mode,
                        sessionDate = date
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun clearAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE_EVENTS")
            db.execSQL("DELETE FROM $TABLE_DAILY")
            db.execSQL("DELETE FROM $TABLE_VIEWING_SESSIONS")
            db.execSQL("DELETE FROM $TABLE_FOCUS_SESSIONS")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Resets today's events, daily aggregate, and viewing sessions to 0
     * without deleting historical data from previous days.
     */
    @Synchronized
    fun resetTodayCountOnly(todayDateStr: String = DateTimeUtils.getTodayDateString()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_EVENTS, "$COL_DATE = ?", arrayOf(todayDateStr))
            db.delete(TABLE_DAILY, "$COL_DAILY_DATE = ?", arrayOf(todayDateStr))
            db.delete(TABLE_VIEWING_SESSIONS, "$COL_SESSION_DATE = ?", arrayOf(todayDateStr))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // =========================================================================
    // VIEWING SESSION QUERIES (PHASE 11: ACTUAL VIEWING TIME)
    // =========================================================================

    @Synchronized
    fun insertViewingSession(session: ViewingSession): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SESSION_APP, session.targetApp.name)
            put(COL_SESSION_FP, session.fingerprint)
            put(COL_SESSION_START, session.startTimestamp)
            put(COL_SESSION_END, session.endTimestamp)
            put(COL_SESSION_DURATION, session.durationMs)
            put(COL_SESSION_STATUS, session.completionStatus)
            put(COL_SESSION_DATE, session.sessionDate)
        }
        return db.insert(TABLE_VIEWING_SESSIONS, null, values)
    }

    @Synchronized
    fun getTodayViewingTimeMs(dateStr: String = DateTimeUtils.getTodayDateString()): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_SESSION_DURATION) FROM $TABLE_VIEWING_SESSIONS WHERE $COL_SESSION_DATE = ?",
            arrayOf(dateStr)
        )
        var totalMs = 0L
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                totalMs = it.getLong(0)
            }
        }
        return totalMs
    }

    @Synchronized
    fun getTodayPlatformViewingTimeMs(dateStr: String = DateTimeUtils.getTodayDateString()): Pair<Long, Long> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT $COL_SESSION_APP, SUM($COL_SESSION_DURATION)
            FROM $TABLE_VIEWING_SESSIONS
            WHERE $COL_SESSION_DATE = ?
            GROUP BY $COL_SESSION_APP
            """.trimIndent(),
            arrayOf(dateStr)
        )
        var igMs = 0L
        var ytMs = 0L
        cursor.use {
            while (it.moveToNext()) {
                val app = it.getString(0)
                val sum = it.getLong(1)
                if (app == TargetApp.INSTAGRAM.name) {
                    igMs = sum
                } else if (app == TargetApp.YOUTUBE.name) {
                    ytMs = sum
                }
            }
        }
        return Pair(igMs, ytMs)
    }

    @Synchronized
    fun getTodaySessionCount(dateStr: String = DateTimeUtils.getTodayDateString()): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_VIEWING_SESSIONS WHERE $COL_SESSION_DATE = ?",
            arrayOf(dateStr)
        )
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }

    @Synchronized
    fun getAverageViewingDurationMs(dateStr: String = DateTimeUtils.getTodayDateString()): Long {
        val count = getTodayCount() // average across total reels watched today
        val sessionCount = getTodaySessionCount(dateStr)
        val divisor = count.coerceAtLeast(sessionCount)
        if (divisor <= 0) return 0L
        val totalMs = getTodayViewingTimeMs(dateStr)
        return totalMs / divisor
    }

    @Synchronized
    fun getViewingSessionsForDate(dateStr: String): List<ViewingSession> {
        val db = readableDatabase
        val list = mutableListOf<ViewingSession>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_SESSION_ID, $COL_SESSION_APP, $COL_SESSION_FP,
                   $COL_SESSION_START, $COL_SESSION_END, $COL_SESSION_DURATION,
                   $COL_SESSION_STATUS, $COL_SESSION_DATE
            FROM $TABLE_VIEWING_SESSIONS
            WHERE $COL_SESSION_DATE = ?
            ORDER BY $COL_SESSION_START DESC, $COL_SESSION_ID DESC
            """.trimIndent(),
            arrayOf(dateStr)
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val appStr = it.getString(1)
                val fp = it.getString(2)
                val start = it.getLong(3)
                val end = it.getLong(4)
                val duration = it.getLong(5)
                val status = it.getString(6)
                val date = it.getString(7)

                val app = try { TargetApp.valueOf(appStr) } catch (_: Exception) { TargetApp.INSTAGRAM }
                list.add(
                    ViewingSession(
                        id = id,
                        targetApp = app,
                        fingerprint = fp,
                        startTimestamp = start,
                        endTimestamp = end,
                        durationMs = duration,
                        completionStatus = status,
                        sessionDate = date
                    )
                )
            }
        }
        return list
    }

    /**
     * Retrieves recorded viewing duration for a given historical date.
     * Returns null if no viewing sessions were recorded on that date,
     * allowing the UI to display "Unavailable" rather than fabricating a duration.
     */
    @Synchronized
    fun getViewingTimeForDate(dateStr: String): Long? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*), SUM($COL_SESSION_DURATION) FROM $TABLE_VIEWING_SESSIONS WHERE $COL_SESSION_DATE = ?",
            arrayOf(dateStr)
        )
        var count = 0
        var totalMs = 0L
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
                if (!it.isNull(1)) {
                    totalMs = it.getLong(1)
                }
            }
        }
        return if (count > 0) totalMs else null
    }

    /**
     * Phase 16 (DEF-01): measured per-platform viewing time for a date.
     * Returns null when the platform has no sessions that day (never zero-filled),
     * mirroring [getViewingTimeForDate]. Historical per-platform viewing-time
     * goals must use this, not a proportional split of the total by reel counts.
     */
    @Synchronized
    fun getViewingTimeForDateByApp(dateStr: String, targetApp: TargetApp): Long? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*), SUM($COL_SESSION_DURATION) FROM $TABLE_VIEWING_SESSIONS WHERE $COL_SESSION_DATE = ? AND $COL_SESSION_APP = ?",
            arrayOf(dateStr, targetApp.name)
        )
        var count = 0
        var totalMs = 0L
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
                if (!it.isNull(1)) {
                    totalMs = it.getLong(1)
                }
            }
        }
        return if (count > 0) totalMs else null
    }

    // =========================================================================
    // Phase 12: Daily Goals & Historical Goal Records CRUD
    // =========================================================================

    @Synchronized
    fun getAllGoals(): List<DailyGoal> {
        val db = readableDatabase
        val list = mutableListOf<DailyGoal>()
        val cursor = db.rawQuery(
            "SELECT $COL_GOAL_ID, $COL_GOAL_TYPE, $COL_GOAL_SCOPE, $COL_GOAL_TARGET, $COL_GOAL_ENABLED FROM $TABLE_GOALS",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val type = try { GoalType.valueOf(it.getString(1)) } catch (_: Exception) { GoalType.REEL_COUNT }
                val scope = try { GoalScope.valueOf(it.getString(2)) } catch (_: Exception) { GoalScope.COMBINED }
                val target = it.getInt(3)
                val enabled = it.getInt(4) == 1
                list.add(DailyGoal(id, type, scope, target, enabled))
            }
        }
        if (list.isEmpty()) {
            val writable = writableDatabase
            insertDefaultGoals(writable)
            return getAllGoals()
        }
        return list
    }

    @Synchronized
    fun insertOrUpdateGoal(
        goal: DailyGoal,
        effectiveTimestamp: Long = System.currentTimeMillis(),
        dateKey: String = DateTimeUtils.formatDate(effectiveTimestamp)
    ): Long {
        val db = writableDatabase
        // Read previous goal state if it exists
        var existingId = goal.id
        var prevTarget: Int? = null
        var prevEnabled: Boolean? = null

        val cursor = db.rawQuery(
            "SELECT $COL_GOAL_ID, $COL_GOAL_TARGET, $COL_GOAL_ENABLED FROM $TABLE_GOALS WHERE $COL_GOAL_TYPE = ? AND $COL_GOAL_SCOPE = ?",
            arrayOf(goal.type.name, goal.scope.name)
        )
        cursor.use {
            if (it.moveToFirst()) {
                existingId = it.getLong(0)
                prevTarget = it.getInt(1)
                prevEnabled = it.getInt(2) == 1
            }
        }

        val cv = ContentValues().apply {
            put(COL_GOAL_TYPE, goal.type.name)
            put(COL_GOAL_SCOPE, goal.scope.name)
            put(COL_GOAL_TARGET, goal.targetValue)
            put(COL_GOAL_ENABLED, if (goal.isEnabled) 1 else 0)
            put(COL_GOAL_UPDATED_AT, effectiveTimestamp)
        }
        val rows = db.update(
            TABLE_GOALS,
            cv,
            "$COL_GOAL_TYPE = ? AND $COL_GOAL_SCOPE = ?",
            arrayOf(goal.type.name, goal.scope.name)
        )
        val finalGoalId = if (rows > 0) {
            existingId
        } else {
            cv.put(COL_GOAL_CREATED_AT, effectiveTimestamp)
            db.insert(TABLE_GOALS, null, cv)
        }

        // Record append-only timestamped history events
        val tzId = TimeZone.getDefault().id
        if (prevTarget == null || prevEnabled == null) {
            appendGoalHistoryEventInternal(
                db = db,
                event = DailyGoalHistoryEvent(
                    eventId = "goal_${finalGoalId}_created_${effectiveTimestamp}_${UUID.randomUUID()}",
                    goalId = finalGoalId,
                    goalType = goal.type,
                    platformScope = goal.scope,
                    eventType = GoalHistoryEventType.CREATED,
                    previousValue = null,
                    newValue = "${goal.targetValue}:${goal.isEnabled}",
                    effectiveTimestamp = effectiveTimestamp,
                    dateKey = dateKey,
                    timezoneId = tzId,
                    createdAt = effectiveTimestamp
                )
            )
        } else {
            if (prevEnabled != goal.isEnabled) {
                val evType = if (goal.isEnabled) GoalHistoryEventType.ENABLED else GoalHistoryEventType.DISABLED
                appendGoalHistoryEventInternal(
                    db = db,
                    event = DailyGoalHistoryEvent(
                        eventId = "goal_${finalGoalId}_${evType.name.lowercase()}_${effectiveTimestamp}_${UUID.randomUUID()}",
                        goalId = finalGoalId,
                        goalType = goal.type,
                        platformScope = goal.scope,
                        eventType = evType,
                        previousValue = prevTarget.toString(),
                        newValue = goal.targetValue.toString(),
                        effectiveTimestamp = effectiveTimestamp,
                        dateKey = dateKey,
                        timezoneId = tzId,
                        createdAt = effectiveTimestamp
                    )
                )
            }
            if (prevTarget != goal.targetValue) {
                appendGoalHistoryEventInternal(
                    db = db,
                    event = DailyGoalHistoryEvent(
                        eventId = "goal_${finalGoalId}_target_${effectiveTimestamp}_${UUID.randomUUID()}",
                        goalId = finalGoalId,
                        goalType = goal.type,
                        platformScope = goal.scope,
                        eventType = GoalHistoryEventType.TARGET_CHANGED,
                        previousValue = prevTarget.toString(),
                        newValue = goal.targetValue.toString(),
                        effectiveTimestamp = effectiveTimestamp,
                        dateKey = dateKey,
                        timezoneId = tzId,
                        createdAt = effectiveTimestamp
                    )
                )
            }
        }

        rebuildGoalSnapshotForDateInternal(
            db = db,
            dateKey = dateKey,
            goalId = finalGoalId,
            goalType = goal.type,
            platformScope = goal.scope
        )

        return finalGoalId
    }

    @Synchronized
    fun insertOrUpdateGoalRecord(record: DailyGoalRecord): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_REC_DATE, record.dateKey)
            put(COL_REC_TYPE, record.goalType.name)
            put(COL_REC_SCOPE, record.scope.name)
            put(COL_REC_TARGET, record.targetValue)
            put(COL_REC_ACTUAL, record.actualValue)
            put(COL_REC_SUCCESS, if (record.wasSuccessful) 1 else 0)
            put(COL_REC_TIMESTAMP, record.recordedAt)
        }
        return db.insertWithOnConflict(TABLE_GOAL_RECORDS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getGoalRecordsForDate(dateKey: String): List<DailyGoalRecord> {
        val db = readableDatabase
        val list = mutableListOf<DailyGoalRecord>()
        val cursor = db.rawQuery(
            "SELECT $COL_REC_ID, $COL_REC_DATE, $COL_REC_TYPE, $COL_REC_SCOPE, $COL_REC_TARGET, $COL_REC_ACTUAL, $COL_REC_SUCCESS, $COL_REC_TIMESTAMP FROM $TABLE_GOAL_RECORDS WHERE $COL_REC_DATE = ?",
            arrayOf(dateKey)
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val date = it.getString(1)
                val type = try { GoalType.valueOf(it.getString(2)) } catch (_: Exception) { GoalType.REEL_COUNT }
                val scope = try { GoalScope.valueOf(it.getString(3)) } catch (_: Exception) { GoalScope.COMBINED }
                val target = it.getInt(4)
                val actual = it.getInt(5)
                val success = it.getInt(6) == 1
                val ts = it.getLong(7)
                list.add(DailyGoalRecord(id, date, type, scope, target, actual, success, ts))
            }
        }
        return list
    }

    @Synchronized
    fun getAllGoalRecords(): List<DailyGoalRecord> {
        val db = readableDatabase
        val list = mutableListOf<DailyGoalRecord>()
        val cursor = db.rawQuery(
            "SELECT $COL_REC_ID, $COL_REC_DATE, $COL_REC_TYPE, $COL_REC_SCOPE, $COL_REC_TARGET, $COL_REC_ACTUAL, $COL_REC_SUCCESS, $COL_REC_TIMESTAMP FROM $TABLE_GOAL_RECORDS ORDER BY $COL_REC_DATE DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val date = it.getString(1)
                val type = try { GoalType.valueOf(it.getString(2)) } catch (_: Exception) { GoalType.REEL_COUNT }
                val scope = try { GoalScope.valueOf(it.getString(3)) } catch (_: Exception) { GoalScope.COMBINED }
                val target = it.getInt(4)
                val actual = it.getInt(5)
                val success = it.getInt(6) == 1
                val ts = it.getLong(7)
                list.add(DailyGoalRecord(id, date, type, scope, target, actual, success, ts))
            }
        }
        return list
    }

    @Synchronized
    fun getGoalSuccessStats(daysLimit: Int = 30): Pair<Int, Int> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(DISTINCT $COL_REC_DATE), SUM(CASE WHEN $COL_REC_SUCCESS = 1 THEN 1 ELSE 0 END) FROM $TABLE_GOAL_RECORDS WHERE $COL_REC_TYPE = 'REEL_COUNT' AND $COL_REC_SCOPE = 'COMBINED'",
            null
        )
        var totalDays = 0
        var successDays = 0
        cursor.use {
            if (it.moveToFirst()) {
                totalDays = it.getInt(0)
                successDays = it.getInt(1)
            }
        }
        return Pair(successDays, totalDays)
    }

    // =========================================================================
    // Phase 13: Focus Mode Operations
    // =========================================================================

    @Synchronized
    fun insertFocusSession(session: FocusSession): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_FOCUS_TYPE, session.sessionType.name)
            put(COL_FOCUS_TARGET_DURATION, session.targetDurationMs)
            put(COL_FOCUS_ACTUAL_DURATION, session.actualDurationMs)
            put(COL_FOCUS_START, session.startTimestamp)
            put(COL_FOCUS_END, session.endTimestamp)
            put(COL_FOCUS_STATUS, session.completionStatus)
            put(COL_FOCUS_DATE, session.dateString)
        }
        return db.insert(TABLE_FOCUS_SESSIONS, null, cv)
    }

    @Synchronized
    fun getTodayFocusTimeMs(dateString: String = DateTimeUtils.getTodayDateString()): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_FOCUS_ACTUAL_DURATION) FROM $TABLE_FOCUS_SESSIONS WHERE $COL_FOCUS_DATE = ?",
            arrayOf(dateString)
        )
        var totalMs = 0L
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                totalMs = it.getLong(0)
            }
        }
        return totalMs
    }

    @Synchronized
    fun getTodayCompletedFocusCount(dateString: String = DateTimeUtils.getTodayDateString()): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_FOCUS_SESSIONS WHERE $COL_FOCUS_DATE = ? AND $COL_FOCUS_STATUS = ?",
            arrayOf(dateString, FocusSession.STATUS_COMPLETED)
        )
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }

    @Synchronized
    fun getWeeklyFocusStats(startDate: String, endDate: String): FocusPeriodStats {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 
                COALESCE(SUM($COL_FOCUS_ACTUAL_DURATION), 0),
                COALESCE(SUM(CASE WHEN $COL_FOCUS_STATUS = '${FocusSession.STATUS_COMPLETED}' THEN 1 ELSE 0 END), 0),
                COUNT(*),
                COALESCE(AVG($COL_FOCUS_ACTUAL_DURATION), 0)
            FROM $TABLE_FOCUS_SESSIONS
            WHERE $COL_FOCUS_DATE >= ? AND $COL_FOCUS_DATE <= ?
            """.trimIndent(),
            arrayOf(startDate, endDate)
        )
        var stats = FocusPeriodStats.empty()
        cursor.use {
            if (it.moveToFirst()) {
                stats = FocusPeriodStats(
                    totalFocusTimeMs = it.getLong(0),
                    completedSessionsCount = it.getInt(1),
                    totalSessionsCount = it.getInt(2),
                    averageDurationMs = it.getDouble(3).toLong()
                )
            }
        }
        return stats
    }

    @Synchronized
    fun getRecentFocusSessions(limit: Int = 20): List<FocusSession> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT $COL_FOCUS_ID, $COL_FOCUS_TYPE, $COL_FOCUS_TARGET_DURATION,
                   $COL_FOCUS_ACTUAL_DURATION, $COL_FOCUS_START, $COL_FOCUS_END,
                   $COL_FOCUS_STATUS, $COL_FOCUS_DATE
            FROM $TABLE_FOCUS_SESSIONS
            ORDER BY $COL_FOCUS_START DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        )
        val sessions = mutableListOf<FocusSession>()
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(
                    FocusSession(
                        id = it.getLong(0),
                        sessionType = FocusType.fromName(it.getString(1)),
                        targetDurationMs = it.getLong(2),
                        actualDurationMs = it.getLong(3),
                        startTimestamp = it.getLong(4),
                        endTimestamp = it.getLong(5),
                        completionStatus = it.getString(6),
                        dateString = it.getString(7)
                    )
                )
            }
        }
        return sessions
    }

    @Synchronized
    fun getFocusSessionsForDate(dateKey: String): List<FocusSession> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT $COL_FOCUS_ID, $COL_FOCUS_TYPE, $COL_FOCUS_TARGET_DURATION,
                   $COL_FOCUS_ACTUAL_DURATION, $COL_FOCUS_START, $COL_FOCUS_END,
                   $COL_FOCUS_STATUS, $COL_FOCUS_DATE
            FROM $TABLE_FOCUS_SESSIONS
            WHERE $COL_FOCUS_DATE = ?
            ORDER BY $COL_FOCUS_START ASC
            """.trimIndent(),
            arrayOf(dateKey)
        )
        val sessions = mutableListOf<FocusSession>()
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(
                    FocusSession(
                        id = it.getLong(0),
                        sessionType = FocusType.fromName(it.getString(1)),
                        targetDurationMs = it.getLong(2),
                        actualDurationMs = it.getLong(3),
                        startTimestamp = it.getLong(4),
                        endTimestamp = it.getLong(5),
                        completionStatus = it.getString(6),
                        dateString = it.getString(7)
                    )
                )
            }
        }
        return sessions
    }

    // =========================================================================
    // Phase 14 (Section 3 & 8.1-8.4): Monitoring Coverage & Detection Events
    // =========================================================================

    @Synchronized
    fun insertMonitoringInterval(interval: MonitoringInterval): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_INT_DATE, interval.dateKey)
            put(COL_INT_START, interval.startTimestamp)
            put(COL_INT_END, interval.endTimestamp)
            put(COL_INT_MONO_START, interval.monotonicStartMs)
            put(COL_INT_MONO_END, interval.monotonicEndMs)
            put(COL_INT_SERVICE_CONN, if (interval.serviceConnected) 1 else 0)
            put(COL_INT_TRACKING_EN, if (interval.trackingEnabled) 1 else 0)
            put(COL_INT_SCREEN_ON, if (interval.screenOn) 1 else 0)
            if (interval.interruptionReason != null) {
                put(COL_INT_INTERRUPTION, interval.interruptionReason)
            } else {
                putNull(COL_INT_INTERRUPTION)
            }
            put(COL_INT_VERIFIED_DUR, interval.estimatedVerifiedDurationMs)
            put(COL_INT_CONTINUITY, interval.continuityStatus.name)
            put(COL_INT_BOOT_ID, interval.bootSessionId)
            put(COL_INT_IS_ACTIVE, if (interval.isActive) 1 else 0)
        }
        val rowId = db.insert(TABLE_MONITORING_INTERVALS, null, cv)
        recalculateAndSaveDailyCoverageInternal(db, interval.dateKey, interval.endTimestamp)
        return rowId
    }

    @Synchronized
    fun getUnclosedMonitoringIntervals(): List<MonitoringInterval> {
        val db = readableDatabase
        val list = mutableListOf<MonitoringInterval>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_INT_ID, $COL_INT_DATE, $COL_INT_START, $COL_INT_END,
                   $COL_INT_MONO_START, $COL_INT_MONO_END, $COL_INT_SERVICE_CONN,
                   $COL_INT_TRACKING_EN, $COL_INT_SCREEN_ON, $COL_INT_INTERRUPTION,
                   $COL_INT_VERIFIED_DUR, $COL_INT_CONTINUITY, $COL_INT_BOOT_ID, $COL_INT_IS_ACTIVE
            FROM $TABLE_MONITORING_INTERVALS
            WHERE $COL_INT_IS_ACTIVE = 1
            ORDER BY $COL_INT_START ASC, $COL_INT_ID ASC
            """.trimIndent(),
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MonitoringInterval(
                        id = it.getLong(0),
                        dateKey = it.getString(1),
                        startTimestamp = it.getLong(2),
                        endTimestamp = it.getLong(3),
                        monotonicStartMs = it.getLong(4),
                        monotonicEndMs = it.getLong(5),
                        serviceConnected = it.getInt(6) == 1,
                        trackingEnabled = it.getInt(7) == 1,
                        screenOn = it.getInt(8) == 1,
                        interruptionReason = if (it.isNull(9)) null else it.getString(9),
                        estimatedVerifiedDurationMs = it.getLong(10),
                        continuityStatus = ContinuityStatus.fromName(it.getString(11)),
                        bootSessionId = if (it.isNull(12)) "" else it.getString(12),
                        isActive = it.getInt(13) == 1
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun closeInterruptedMonitoringInterval(
        intervalId: Long,
        interruptionReason: String,
        continuityStatus: ContinuityStatus = ContinuityStatus.INTERRUPTED,
        endMonoMs: Long,
        endWallTs: Long
    ): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_INT_IS_ACTIVE, 0)
            put(COL_INT_INTERRUPTION, interruptionReason)
            put(COL_INT_CONTINUITY, continuityStatus.name)
            put(COL_INT_END, endWallTs)
            put(COL_INT_MONO_END, endMonoMs)
            put(COL_INT_VERIFIED_DUR, 0L)
        }
        val rows = db.update(TABLE_MONITORING_INTERVALS, cv, "$COL_INT_ID = ?", arrayOf(intervalId.toString()))
        return rows > 0
    }

    @Synchronized
    fun updateActiveMonitoringIntervalProgress(
        intervalId: Long,
        nowMonoMs: Long,
        nowWallTs: Long
    ): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_INT_MONO_END, nowMonoMs)
            put(COL_INT_END, nowWallTs)
        }
        val rows = db.update(
            TABLE_MONITORING_INTERVALS,
            cv,
            "$COL_INT_ID = ? AND $COL_INT_IS_ACTIVE = 1",
            arrayOf(intervalId.toString())
        )
        return rows > 0
    }

    @Synchronized
    fun updateMonitoringInterval(interval: MonitoringInterval): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_INT_START, interval.startTimestamp)
            put(COL_INT_END, interval.endTimestamp)
            put(COL_INT_MONO_START, interval.monotonicStartMs)
            put(COL_INT_MONO_END, interval.monotonicEndMs)
            put(COL_INT_SERVICE_CONN, if (interval.serviceConnected) 1 else 0)
            put(COL_INT_TRACKING_EN, if (interval.trackingEnabled) 1 else 0)
            put(COL_INT_SCREEN_ON, if (interval.screenOn) 1 else 0)
            if (interval.interruptionReason != null) {
                put(COL_INT_INTERRUPTION, interval.interruptionReason)
            } else {
                putNull(COL_INT_INTERRUPTION)
            }
            put(COL_INT_VERIFIED_DUR, interval.estimatedVerifiedDurationMs)
            put(COL_INT_CONTINUITY, interval.continuityStatus.name)
            put(COL_INT_BOOT_ID, interval.bootSessionId)
            put(COL_INT_IS_ACTIVE, if (interval.isActive) 1 else 0)
        }
        val rows = db.update(TABLE_MONITORING_INTERVALS, cv, "$COL_INT_ID = ?", arrayOf(interval.id.toString()))
        recalculateAndSaveDailyCoverageInternal(db, interval.dateKey, interval.endTimestamp)
        return rows > 0
    }

    @Synchronized
    fun getMonitoringIntervalsForDate(dateKey: String): List<MonitoringInterval> {
        return getMonitoringIntervalsForDateInternal(readableDatabase, dateKey)
    }

    private fun getMonitoringIntervalsForDateInternal(db: SQLiteDatabase, dateKey: String): List<MonitoringInterval> {
        val list = mutableListOf<MonitoringInterval>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_INT_ID, $COL_INT_DATE, $COL_INT_START, $COL_INT_END,
                   $COL_INT_MONO_START, $COL_INT_MONO_END, $COL_INT_SERVICE_CONN,
                   $COL_INT_TRACKING_EN, $COL_INT_SCREEN_ON, $COL_INT_INTERRUPTION,
                   $COL_INT_VERIFIED_DUR, $COL_INT_CONTINUITY, $COL_INT_BOOT_ID, $COL_INT_IS_ACTIVE
            FROM $TABLE_MONITORING_INTERVALS
            WHERE $COL_INT_DATE = ?
            ORDER BY $COL_INT_START ASC, $COL_INT_ID ASC
            """.trimIndent(),
            arrayOf(dateKey)
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MonitoringInterval(
                        id = it.getLong(0),
                        dateKey = it.getString(1),
                        startTimestamp = it.getLong(2),
                        endTimestamp = it.getLong(3),
                        monotonicStartMs = it.getLong(4),
                        monotonicEndMs = it.getLong(5),
                        serviceConnected = it.getInt(6) == 1,
                        trackingEnabled = it.getInt(7) == 1,
                        screenOn = it.getInt(8) == 1,
                        interruptionReason = if (it.isNull(9)) null else it.getString(9),
                        estimatedVerifiedDurationMs = it.getLong(10),
                        continuityStatus = ContinuityStatus.fromName(it.getString(11)),
                        bootSessionId = if (it.isNull(12)) "" else it.getString(12),
                        isActive = it.getInt(13) == 1
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun insertMonitoringHeartbeat(heartbeat: MonitoringHeartbeat): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_HB_TIMESTAMP, heartbeat.timestamp)
            put(COL_HB_MONO_MS, heartbeat.monotonicMs)
            put(COL_HB_DATE, heartbeat.dateKey)
            put(COL_HB_SERVICE_CONN, if (heartbeat.serviceConnected) 1 else 0)
            put(COL_HB_TRACKING_EN, if (heartbeat.trackingEnabled) 1 else 0)
            put(COL_HB_SCREEN_ON, if (heartbeat.screenOn) 1 else 0)
            put(COL_HB_CONTINUITY, heartbeat.continuityStatus.name)
            put(COL_HB_BOOT_ID, heartbeat.bootSessionId)
        }
        val rowId = db.insert(TABLE_MONITORING_HEARTBEATS, null, cv)
        recalculateAndSaveDailyCoverageInternal(db, heartbeat.dateKey, heartbeat.timestamp)
        return rowId
    }

    @Synchronized
    fun getMonitoringHeartbeatsForDate(dateKey: String): List<MonitoringHeartbeat> {
        return getMonitoringHeartbeatsForDateInternal(readableDatabase, dateKey)
    }

    private fun getMonitoringHeartbeatsForDateInternal(db: SQLiteDatabase, dateKey: String): List<MonitoringHeartbeat> {
        val list = mutableListOf<MonitoringHeartbeat>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_HB_ID, $COL_HB_TIMESTAMP, $COL_HB_MONO_MS, $COL_HB_DATE,
                   $COL_HB_SERVICE_CONN, $COL_HB_TRACKING_EN, $COL_HB_SCREEN_ON, $COL_HB_CONTINUITY,
                   $COL_HB_BOOT_ID
            FROM $TABLE_MONITORING_HEARTBEATS
            WHERE $COL_HB_DATE = ?
            ORDER BY $COL_HB_TIMESTAMP ASC, $COL_HB_ID ASC
            """.trimIndent(),
            arrayOf(dateKey)
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MonitoringHeartbeat(
                        id = it.getLong(0),
                        timestamp = it.getLong(1),
                        monotonicMs = it.getLong(2),
                        dateKey = it.getString(3),
                        serviceConnected = it.getInt(4) == 1,
                        trackingEnabled = it.getInt(5) == 1,
                        screenOn = it.getInt(6) == 1,
                        continuityStatus = ContinuityStatus.fromName(it.getString(7)),
                        bootSessionId = if (it.isNull(8)) "" else it.getString(8)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun insertSupportedAppDetectionEvent(event: SupportedAppDetectionEvent): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_DET_TIMESTAMP, event.timestamp)
            put(COL_DET_DATE, event.dateKey)
            put(COL_DET_PLATFORM, event.platform)
            put(COL_DET_EVENT_TYPE, event.eventType)
            put(COL_DET_STATUS, event.detectionStatus)
            if (event.contentFingerprint != null) {
                put(COL_DET_FINGERPRINT, event.contentFingerprint)
            } else {
                putNull(COL_DET_FINGERPRINT)
            }
        }
        val rowId = db.insert(TABLE_DETECTION_EVENTS, null, cv)
        recalculateAndSaveDailyCoverageInternal(db, event.dateKey, event.timestamp)
        return rowId
    }

    @Synchronized
    fun getSupportedAppDetectionEventsForDate(dateKey: String): List<SupportedAppDetectionEvent> {
        val db = readableDatabase
        val list = mutableListOf<SupportedAppDetectionEvent>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_DET_ID, $COL_DET_TIMESTAMP, $COL_DET_DATE, $COL_DET_PLATFORM,
                   $COL_DET_EVENT_TYPE, $COL_DET_STATUS, $COL_DET_FINGERPRINT
            FROM $TABLE_DETECTION_EVENTS
            WHERE $COL_DET_DATE = ?
            ORDER BY $COL_DET_TIMESTAMP ASC, $COL_DET_ID ASC
            """.trimIndent(),
            arrayOf(dateKey)
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SupportedAppDetectionEvent(
                        id = it.getLong(0),
                        timestamp = it.getLong(1),
                        dateKey = it.getString(2),
                        platform = it.getString(3),
                        eventType = it.getString(4),
                        detectionStatus = it.getString(5),
                        contentFingerprint = if (it.isNull(6)) null else it.getString(6)
                    )
                )
            }
        }
        return list
    }

    private fun getSupportedAppDetectionCountInternal(db: SQLiteDatabase, dateKey: String): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_DETECTION_EVENTS WHERE $COL_DET_DATE = ?",
            arrayOf(dateKey)
        )
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }

    @Synchronized
    fun recalculateAndSaveDailyCoverage(
        dateKey: String,
        calculatedAt: Long = System.currentTimeMillis()
    ): DailyMonitoringCoverage {
        return recalculateAndSaveDailyCoverageInternal(writableDatabase, dateKey, calculatedAt)
    }

    private fun recalculateAndSaveDailyCoverageInternal(
        db: SQLiteDatabase,
        dateKey: String,
        calculatedAt: Long
    ): DailyMonitoringCoverage {
        val intervals = getMonitoringIntervalsForDateInternal(db, dateKey)
        val heartbeats = getMonitoringHeartbeatsForDateInternal(db, dateKey)
        val detCount = getSupportedAppDetectionCountInternal(db, dateKey)

        val coverage = CoverageCalculator.calculateDailyCoverage(
            dateKey = dateKey,
            intervals = intervals,
            heartbeats = heartbeats,
            detectionEventCount = detCount,
            calculatedAt = calculatedAt
        )

        val cv = ContentValues().apply {
            put(COL_COV_DATE, coverage.dateKey)
            put(COL_COV_SERVICE_MS, coverage.serviceConnectedMs)
            put(COL_COV_TRACKING_MS, coverage.trackingEnabledMs)
            put(COL_COV_SCREEN_MS, coverage.screenOnMs)
            put(COL_COV_VERIFIED_MS, coverage.estimatedVerifiedMonitoringMs)
            put(COL_COV_HB_COUNT, coverage.heartbeatCount)
            put(COL_COV_DET_COUNT, coverage.detectionEventCount)
            put(COL_COV_INT_COUNT, coverage.interruptionCount)
            put(COL_COV_REC_COUNT, coverage.recoveryCount)
            put(COL_COV_STATUS, coverage.coverageStatus.name)
            put(COL_COV_CALC_AT, coverage.calculatedAt)
        }
        db.insertWithOnConflict(TABLE_DAILY_COVERAGE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return coverage
    }

    @Synchronized
    fun getDailyMonitoringCoverage(dateKey: String): DailyMonitoringCoverage {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT $COL_COV_DATE, $COL_COV_SERVICE_MS, $COL_COV_TRACKING_MS, $COL_COV_SCREEN_MS,
                   $COL_COV_VERIFIED_MS, $COL_COV_HB_COUNT, $COL_COV_DET_COUNT,
                   $COL_COV_INT_COUNT, $COL_COV_REC_COUNT, $COL_COV_STATUS, $COL_COV_CALC_AT
            FROM $TABLE_DAILY_COVERAGE
            WHERE $COL_COV_DATE = ?
            """.trimIndent(),
            arrayOf(dateKey)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return DailyMonitoringCoverage(
                    dateKey = it.getString(0),
                    serviceConnectedMs = it.getLong(1),
                    trackingEnabledMs = it.getLong(2),
                    screenOnMs = it.getLong(3),
                    estimatedVerifiedMonitoringMs = it.getLong(4),
                    heartbeatCount = it.getInt(5),
                    detectionEventCount = it.getInt(6),
                    interruptionCount = it.getInt(7),
                    recoveryCount = it.getInt(8),
                    coverageStatus = EstimatedCoverageStatus.fromName(it.getString(9)),
                    calculatedAt = it.getLong(10)
                )
            }
        }
        return DailyMonitoringCoverage(dateKey = dateKey)
    }

    // =========================================================================
    // Phase 14 (Section 4 & 8.5-8.6): Timestamped Daily Goal History & Snapshots
    // =========================================================================

    @Synchronized
    fun appendGoalHistoryEvent(event: DailyGoalHistoryEvent): Long {
        val db = writableDatabase
        val rowId = appendGoalHistoryEventInternal(db, event)
        rebuildGoalSnapshotForDateInternal(
            db = db,
            dateKey = event.dateKey,
            goalId = event.goalId,
            goalType = event.goalType,
            platformScope = event.platformScope
        )
        return rowId
    }

    private fun appendGoalHistoryEventInternal(
        db: SQLiteDatabase,
        event: DailyGoalHistoryEvent
    ): Long {
        val cv = ContentValues().apply {
            put(COL_GH_EVENT_ID, event.eventId)
            put(COL_GH_GOAL_ID, event.goalId)
            put(COL_GH_GOAL_TYPE, event.goalType.name)
            put(COL_GH_SCOPE, event.platformScope.name)
            put(COL_GH_EVENT_TYPE, event.eventType.name)
            if (event.previousValue != null) {
                put(COL_GH_PREV_VAL, event.previousValue)
            } else {
                putNull(COL_GH_PREV_VAL)
            }
            put(COL_GH_NEW_VAL, event.newValue)
            put(COL_GH_EFFECTIVE_TS, event.effectiveTimestamp)
            put(COL_GH_DATE, event.dateKey)
            put(COL_GH_TIMEZONE, event.timezoneId)
            put(COL_GH_CREATED_AT, event.createdAt)
        }
        return db.insertWithOnConflict(TABLE_GOAL_HISTORY, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun getGoalHistoryEvents(
        goalType: GoalType? = null,
        platformScope: GoalScope? = null,
        upToDateKey: String? = null
    ): List<DailyGoalHistoryEvent> {
        return getGoalHistoryEventsInternal(readableDatabase, goalType, platformScope, upToDateKey)
    }

    private fun getGoalHistoryEventsInternal(
        db: SQLiteDatabase,
        goalType: GoalType? = null,
        platformScope: GoalScope? = null,
        upToDateKey: String? = null
    ): List<DailyGoalHistoryEvent> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (goalType != null) {
            clauses.add("$COL_GH_GOAL_TYPE = ?")
            args.add(goalType.name)
        }
        if (platformScope != null) {
            clauses.add("$COL_GH_SCOPE = ?")
            args.add(platformScope.name)
        }
        if (upToDateKey != null) {
            clauses.add("$COL_GH_DATE <= ?")
            args.add(upToDateKey)
        }
        val whereSql = if (clauses.isNotEmpty()) "WHERE ${clauses.joinToString(" AND ")}" else ""

        val list = mutableListOf<DailyGoalHistoryEvent>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_GH_ID, $COL_GH_EVENT_ID, $COL_GH_GOAL_ID, $COL_GH_GOAL_TYPE,
                   $COL_GH_SCOPE, $COL_GH_EVENT_TYPE, $COL_GH_PREV_VAL, $COL_GH_NEW_VAL,
                   $COL_GH_EFFECTIVE_TS, $COL_GH_DATE, $COL_GH_TIMEZONE, $COL_GH_CREATED_AT
            FROM $TABLE_GOAL_HISTORY
            $whereSql
            ORDER BY $COL_GH_EFFECTIVE_TS ASC, $COL_GH_ID ASC
            """.trimIndent(),
            args.toTypedArray()
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DailyGoalHistoryEvent(
                        id = it.getLong(0),
                        eventId = it.getString(1),
                        goalId = it.getLong(2),
                        goalType = try { GoalType.valueOf(it.getString(3)) } catch (_: Exception) { GoalType.REEL_COUNT },
                        platformScope = try { GoalScope.valueOf(it.getString(4)) } catch (_: Exception) { GoalScope.COMBINED },
                        eventType = GoalHistoryEventType.fromName(it.getString(5)),
                        previousValue = if (it.isNull(6)) null else it.getString(6),
                        newValue = it.getString(7),
                        effectiveTimestamp = it.getLong(8),
                        dateKey = it.getString(9),
                        timezoneId = it.getString(10),
                        createdAt = it.getLong(11)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun clearGoalHistoryAndSnapshotsForTest() {
        val db = writableDatabase
        db.delete(TABLE_GOAL_HISTORY, null, null)
        db.delete(TABLE_GOAL_SNAPSHOTS, null, null)
    }

    @Synchronized
    fun rebuildGoalSnapshotForDate(
        dateKey: String,
        goalId: Long,
        goalType: GoalType,
        platformScope: GoalScope,
        dayStartMidnightMs: Long = getStartOfDayTimestamp(dateKey)
    ): DailyGoalSnapshot? {
        return rebuildGoalSnapshotForDateInternal(
            db = writableDatabase,
            dateKey = dateKey,
            goalId = goalId,
            goalType = goalType,
            platformScope = platformScope,
            dayStartMidnightMs = dayStartMidnightMs
        )
    }

    private fun rebuildGoalSnapshotForDateInternal(
        db: SQLiteDatabase,
        dateKey: String,
        goalId: Long,
        goalType: GoalType,
        platformScope: GoalScope,
        dayStartMidnightMs: Long = getStartOfDayTimestamp(dateKey)
    ): DailyGoalSnapshot? {
        val events = getGoalHistoryEventsInternal(
            db = db,
            goalType = goalType,
            platformScope = platformScope,
            upToDateKey = dateKey
        )
        if (events.isEmpty()) return null

        val usageCheckpoints = getTimestampedUsageCheckpointsInternal(db, dateKey, goalType, platformScope)
        val usageMetrics = getDailyUsageMetricsInternal(db, dateKey)
        val fullDayUsage = usageMetrics.usageFor(goalType, platformScope)

        val snapshot = GoalEligibilityEvaluator.buildSnapshotFromHistory(
            dateKey = dateKey,
            dayStartMidnightMs = dayStartMidnightMs,
            goalId = goalId,
            goalType = goalType,
            platformScope = platformScope,
            events = events,
            timestampedUsage = usageCheckpoints,
            finalFullDayUsage = fullDayUsage
        ) ?: return null

        upsertDailyGoalSnapshotInternal(db, snapshot)
        return snapshot
    }

    private fun getTimestampedUsageCheckpointsInternal(
        db: SQLiteDatabase,
        dateKey: String,
        goalType: GoalType,
        platformScope: GoalScope
    ): List<Pair<Long, Int>> {
        val result = mutableListOf<Pair<Long, Int>>()
        when (goalType) {
            GoalType.REEL_COUNT -> {
                val appClause = when (platformScope) {
                    GoalScope.COMBINED -> ""
                    GoalScope.INSTAGRAM -> "AND $COL_APP = '${TargetApp.INSTAGRAM.name}'"
                    GoalScope.YOUTUBE -> "AND $COL_APP = '${TargetApp.YOUTUBE.name}'"
                }
                val cursor = db.rawQuery(
                    "SELECT $COL_TIMESTAMP FROM $TABLE_EVENTS WHERE $COL_DATE = ? $appClause ORDER BY $COL_TIMESTAMP ASC",
                    arrayOf(dateKey)
                )
                var running = 0
                cursor.use {
                    while (it.moveToNext()) {
                        running++
                        result.add(it.getLong(0) to running)
                    }
                }
            }
            GoalType.VIEWING_TIME_MINUTES -> {
                val appClause = when (platformScope) {
                    GoalScope.COMBINED -> ""
                    GoalScope.INSTAGRAM -> "AND $COL_SESSION_APP = '${TargetApp.INSTAGRAM.name}'"
                    GoalScope.YOUTUBE -> "AND $COL_SESSION_APP = '${TargetApp.YOUTUBE.name}'"
                }
                val cursor = db.rawQuery(
                    "SELECT $COL_SESSION_END, $COL_SESSION_DURATION FROM $TABLE_VIEWING_SESSIONS WHERE $COL_SESSION_DATE = ? $appClause ORDER BY $COL_SESSION_END ASC",
                    arrayOf(dateKey)
                )
                var runningMs = 0L
                cursor.use {
                    while (it.moveToNext()) {
                        val endTs = it.getLong(0)
                        runningMs += it.getLong(1)
                        result.add(endTs to (runningMs / 60_000L).toInt())
                    }
                }
            }
        }
        return result
    }

    @Synchronized
    fun upsertDailyGoalSnapshot(snapshot: DailyGoalSnapshot): Long {
        return upsertDailyGoalSnapshotInternal(writableDatabase, snapshot)
    }

    private fun upsertDailyGoalSnapshotInternal(db: SQLiteDatabase, snapshot: DailyGoalSnapshot): Long {
        val cv = ContentValues().apply {
            put(COL_GS_DATE, snapshot.dateKey)
            put(COL_GS_GOAL_ID, snapshot.goalId)
            put(COL_GS_GOAL_TYPE, snapshot.goalType.name)
            put(COL_GS_SCOPE, snapshot.platformScope.name)
            put(COL_GS_ENABLED_MIDNIGHT, if (snapshot.enabledAtMidnight) 1 else 0)
            if (snapshot.targetAtMidnight != null) {
                put(COL_GS_TARGET_MIDNIGHT, snapshot.targetAtMidnight)
            } else {
                putNull(COL_GS_TARGET_MIDNIGHT)
            }
            put(COL_GS_STRICTEST_TARGET, snapshot.strictestTarget)
            if (snapshot.firstActivationTimestamp != null) {
                put(COL_GS_FIRST_ACT_TS, snapshot.firstActivationTimestamp)
            } else {
                putNull(COL_GS_FIRST_ACT_TS)
            }
            put(COL_GS_DISABLED_DAY, if (snapshot.wasDisabledDuringDay) 1 else 0)
            put(COL_GS_REENABLED_DAY, if (snapshot.wasReenabledMidday) 1 else 0)
            put(COL_GS_VIOLATED, if (snapshot.wasViolated) 1 else 0)
            put(COL_GS_PROG_ELIGIBLE, if (snapshot.progressionEligible) 1 else 0)
            put(COL_GS_USAGE_MIDNIGHT, snapshot.usageSinceMidnight)
            put(COL_GS_USAGE_ACTIVATION, snapshot.usageSinceActivation)
            if (snapshot.lastHistoryEventId != null) {
                put(COL_GS_LAST_HIST_ID, snapshot.lastHistoryEventId)
            } else {
                putNull(COL_GS_LAST_HIST_ID)
            }
            put(COL_GS_UPDATED_AT, snapshot.updatedAt)
        }
        return db.insertWithOnConflict(TABLE_GOAL_SNAPSHOTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getGoalSnapshotsForDate(dateKey: String, rebuildFromHistory: Boolean = true): List<DailyGoalSnapshot> {
        val db = writableDatabase
        if (rebuildFromHistory) {
            val distinctPairs = mutableSetOf<Triple<Long, GoalType, GoalScope>>()
            val cursor = db.rawQuery(
                "SELECT DISTINCT $COL_GH_GOAL_ID, $COL_GH_GOAL_TYPE, $COL_GH_SCOPE FROM $TABLE_GOAL_HISTORY WHERE $COL_GH_DATE <= ?",
                arrayOf(dateKey)
            )
            cursor.use {
                while (it.moveToNext()) {
                    val gId = it.getLong(0)
                    val gType = try { GoalType.valueOf(it.getString(1)) } catch (_: Exception) { GoalType.REEL_COUNT }
                    val gScope = try { GoalScope.valueOf(it.getString(2)) } catch (_: Exception) { GoalScope.COMBINED }
                    distinctPairs.add(Triple(gId, gType, gScope))
                }
            }
            for ((gId, gType, gScope) in distinctPairs) {
                rebuildGoalSnapshotForDateInternal(db, dateKey, gId, gType, gScope)
            }
        }

        val list = mutableListOf<DailyGoalSnapshot>()
        val snapCursor = db.rawQuery(
            """
            SELECT $COL_GS_ID, $COL_GS_DATE, $COL_GS_GOAL_ID, $COL_GS_GOAL_TYPE, $COL_GS_SCOPE,
                   $COL_GS_ENABLED_MIDNIGHT, $COL_GS_TARGET_MIDNIGHT, $COL_GS_STRICTEST_TARGET,
                   $COL_GS_FIRST_ACT_TS, $COL_GS_DISABLED_DAY, $COL_GS_REENABLED_DAY,
                   $COL_GS_VIOLATED, $COL_GS_PROG_ELIGIBLE, $COL_GS_USAGE_MIDNIGHT,
                   $COL_GS_USAGE_ACTIVATION, $COL_GS_LAST_HIST_ID, $COL_GS_UPDATED_AT
            FROM $TABLE_GOAL_SNAPSHOTS
            WHERE $COL_GS_DATE = ?
            """.trimIndent(),
            arrayOf(dateKey)
        )
        snapCursor.use {
            while (it.moveToNext()) {
                list.add(
                    DailyGoalSnapshot(
                        id = it.getLong(0),
                        dateKey = it.getString(1),
                        goalId = it.getLong(2),
                        goalType = try { GoalType.valueOf(it.getString(3)) } catch (_: Exception) { GoalType.REEL_COUNT },
                        platformScope = try { GoalScope.valueOf(it.getString(4)) } catch (_: Exception) { GoalScope.COMBINED },
                        enabledAtMidnight = it.getInt(5) == 1,
                        targetAtMidnight = if (it.isNull(6)) null else it.getInt(6),
                        strictestTarget = it.getInt(7),
                        firstActivationTimestamp = if (it.isNull(8)) null else it.getLong(8),
                        wasDisabledDuringDay = it.getInt(9) == 1,
                        wasReenabledMidday = it.getInt(10) == 1,
                        wasViolated = it.getInt(11) == 1,
                        progressionEligible = it.getInt(12) == 1,
                        usageSinceMidnight = it.getInt(13),
                        usageSinceActivation = it.getInt(14),
                        lastHistoryEventId = if (it.isNull(15)) null else it.getLong(15),
                        updatedAt = it.getLong(16)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getDailyUsageMetrics(dateKey: String): DailyUsageMetrics {
        return getDailyUsageMetricsInternal(readableDatabase, dateKey)
    }

    private fun getDailyUsageMetricsInternal(db: SQLiteDatabase, dateKey: String): DailyUsageMetrics {
        var totalReels = 0
        var igReels = 0
        var ytReels = 0
        val dailyCursor = db.rawQuery(
            "SELECT $COL_DAILY_TOTAL, $COL_DAILY_IG, $COL_DAILY_YT FROM $TABLE_DAILY WHERE $COL_DAILY_DATE = ?",
            arrayOf(dateKey)
        )
        dailyCursor.use {
            if (it.moveToFirst()) {
                totalReels = it.getInt(0)
                igReels = it.getInt(1)
                ytReels = it.getInt(2)
            }
        }

        var igViewingMs = 0L
        var ytViewingMs = 0L
        val sessionCursor = db.rawQuery(
            """
            SELECT $COL_SESSION_APP, SUM($COL_SESSION_DURATION)
            FROM $TABLE_VIEWING_SESSIONS
            WHERE $COL_SESSION_DATE = ?
            GROUP BY $COL_SESSION_APP
            """.trimIndent(),
            arrayOf(dateKey)
        )
        sessionCursor.use {
            while (it.moveToNext()) {
                val app = it.getString(0)
                val sum = it.getLong(1)
                if (app == TargetApp.INSTAGRAM.name) {
                    igViewingMs = sum
                } else if (app == TargetApp.YOUTUBE.name) {
                    ytViewingMs = sum
                }
            }
        }
        val totalViewingMs = igViewingMs + ytViewingMs

        return DailyUsageMetrics(
            totalReels = totalReels,
            instagramReels = igReels,
            youtubeReels = ytReels,
            totalViewingMs = totalViewingMs,
            instagramViewingMs = igViewingMs,
            youtubeViewingMs = ytViewingMs
        )
    }

    // =========================================================================
    // Phase 14 (Section 2, 6, 7 & 8.6): XP, User Progress, Achievements & Streaks
    // =========================================================================

    /**
     * Awards XP idempotently using [XpTransaction.eventId] as a unique transaction key.
     * Returns true if XP was newly awarded; false if [eventId] was already recorded.
     */
    @Synchronized
    fun awardXpTransaction(transaction: XpTransaction): Boolean {
        val db = writableDatabase
        var awarded = false
        db.beginTransaction()
        try {
            awarded = awardXpTransactionInternal(db, transaction)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return awarded
    }

    private fun awardXpTransactionInternal(db: SQLiteDatabase, transaction: XpTransaction): Boolean {
        if (transaction.xpAmount <= 0) return false

        val cv = ContentValues().apply {
            put(COL_XP_EVENT_ID, transaction.eventId)
            put(COL_XP_EVENT_TYPE, transaction.eventType.name)
            put(COL_XP_AMOUNT, transaction.xpAmount)
            put(COL_XP_DESC, transaction.description)
            put(COL_XP_CREATED_AT, transaction.createdAt)
            put(COL_XP_LOCAL_DATE, transaction.localDate)
        }
        val rowId = db.insertWithOnConflict(TABLE_XP_TRANSACTIONS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (rowId == -1L) {
            return false
        }

        // Update total XP and level in user_progress singleton
        val currentProgress = getUserProgressInternal(db)
        val newTotalXp = currentProgress.totalXp + transaction.xpAmount
        val newLevel = LevelCalculator.levelForXp(newTotalXp)

        val upCv = ContentValues().apply {
            put(COL_UP_ID, 1)
            put(COL_UP_TOTAL_XP, newTotalXp)
            put(COL_UP_LEVEL, newLevel)
            put(COL_UP_CURR_STREAK, currentProgress.currentStreak)
            put(COL_UP_LONG_STREAK, currentProgress.longestStreak)
            if (currentProgress.lastQualifiedDate != null) {
                put(COL_UP_LAST_QUAL_DATE, currentProgress.lastQualifiedDate)
            } else {
                putNull(COL_UP_LAST_QUAL_DATE)
            }
            put(COL_UP_UPDATED_AT, transaction.createdAt)
        }
        db.insertWithOnConflict(TABLE_USER_PROGRESS, null, upCv, SQLiteDatabase.CONFLICT_REPLACE)

        // Check level-based achievements if level increased
        if (newLevel >= 5) {
            updateAchievementProgressInternal(db, "reach_level_5", newLevel, transaction.createdAt, transaction.localDate)
        }
        if (newLevel >= 10) {
            updateAchievementProgressInternal(db, "reach_level_10", newLevel, transaction.createdAt, transaction.localDate)
        }
        return true
    }

    @Synchronized
    fun hasXpTransaction(eventId: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_XP_TRANSACTIONS WHERE $COL_XP_EVENT_ID = ? LIMIT 1",
            arrayOf(eventId)
        )
        var exists = false
        cursor.use {
            exists = it.moveToFirst()
        }
        return exists
    }

    @Synchronized
    fun getXpTransactions(limit: Int = 50): List<XpTransaction> {
        val db = readableDatabase
        val list = mutableListOf<XpTransaction>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_XP_ID, $COL_XP_EVENT_ID, $COL_XP_EVENT_TYPE, $COL_XP_AMOUNT,
                   $COL_XP_DESC, $COL_XP_CREATED_AT, $COL_XP_LOCAL_DATE
            FROM $TABLE_XP_TRANSACTIONS
            ORDER BY $COL_XP_CREATED_AT DESC, $COL_XP_ID DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    XpTransaction(
                        id = it.getLong(0),
                        eventId = it.getString(1),
                        eventType = XpEventType.fromName(it.getString(2)),
                        xpAmount = it.getInt(3),
                        description = it.getString(4),
                        createdAt = it.getLong(5),
                        localDate = it.getString(6)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getUserProgress(): UserProgress {
        return getUserProgressInternal(readableDatabase)
    }

    private fun getUserProgressInternal(db: SQLiteDatabase): UserProgress {
        val cursor = db.rawQuery(
            """
            SELECT $COL_UP_TOTAL_XP, $COL_UP_LEVEL, $COL_UP_CURR_STREAK,
                   $COL_UP_LONG_STREAK, $COL_UP_LAST_QUAL_DATE, $COL_UP_UPDATED_AT
            FROM $TABLE_USER_PROGRESS
            WHERE $COL_UP_ID = 1
            """.trimIndent(),
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val totalXp = it.getInt(0)
                return UserProgress(
                    totalXp = totalXp,
                    currentLevel = LevelCalculator.levelForXp(totalXp),
                    currentStreak = it.getInt(2),
                    longestStreak = it.getInt(3),
                    lastQualifiedDate = if (it.isNull(4)) null else it.getString(4),
                    updatedAt = it.getLong(5)
                )
            }
        }
        return UserProgress()
    }

    @Synchronized
    fun getAllAchievements(): List<Achievement> {
        val db = readableDatabase
        val list = mutableListOf<Achievement>()
        val cursor = db.rawQuery(
            """
            SELECT $COL_ACH_ID, $COL_ACH_CATEGORY, $COL_ACH_TITLE, $COL_ACH_DESC,
                   $COL_ACH_TARGET, $COL_ACH_CURRENT, $COL_ACH_UNLOCKED,
                   $COL_ACH_UNLOCKED_AT, $COL_ACH_XP_REWARD
            FROM $TABLE_ACHIEVEMENTS
            ORDER BY $COL_ACH_CATEGORY ASC, $COL_ACH_TARGET ASC
            """.trimIndent(),
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Achievement(
                        achievementId = it.getString(0),
                        category = AchievementCategory.fromName(it.getString(1)),
                        title = it.getString(2),
                        description = it.getString(3),
                        targetProgress = it.getInt(4),
                        currentProgress = it.getInt(5),
                        isUnlocked = it.getInt(6) == 1,
                        unlockedAt = if (it.isNull(7)) null else it.getLong(7),
                        xpReward = it.getInt(8)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun updateAchievementProgress(
        achievementId: String,
        newProgress: Int,
        timestamp: Long = System.currentTimeMillis(),
        localDate: String = DateTimeUtils.formatDate(timestamp)
    ): Boolean {
        val db = writableDatabase
        var newlyUnlocked = false
        db.beginTransaction()
        try {
            newlyUnlocked = updateAchievementProgressInternal(db, achievementId, newProgress, timestamp, localDate)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return newlyUnlocked
    }

    private fun updateAchievementProgressInternal(
        db: SQLiteDatabase,
        achievementId: String,
        newProgress: Int,
        timestamp: Long,
        localDate: String
    ): Boolean {
        val cursor = db.rawQuery(
            "SELECT $COL_ACH_TARGET, $COL_ACH_CURRENT, $COL_ACH_UNLOCKED, $COL_ACH_XP_REWARD, $COL_ACH_TITLE FROM $TABLE_ACHIEVEMENTS WHERE $COL_ACH_ID = ?",
            arrayOf(achievementId)
        )
        var target = 0
        var current = 0
        var alreadyUnlocked = false
        var xpReward = 0
        var title = ""
        var found = false

        cursor.use {
            if (it.moveToFirst()) {
                found = true
                target = it.getInt(0)
                current = it.getInt(1)
                alreadyUnlocked = it.getInt(2) == 1
                xpReward = it.getInt(3)
                title = it.getString(4)
            }
        }
        if (!found) return false

        val clampedProgress = maxOf(current, newProgress).coerceAtMost(target)
        val shouldUnlock = !alreadyUnlocked && newProgress >= target

        val cv = ContentValues().apply {
            put(COL_ACH_CURRENT, clampedProgress)
            if (shouldUnlock) {
                put(COL_ACH_UNLOCKED, 1)
                put(COL_ACH_UNLOCKED_AT, timestamp)
            }
        }
        db.update(TABLE_ACHIEVEMENTS, cv, "$COL_ACH_ID = ?", arrayOf(achievementId))

        if (shouldUnlock) {
            awardXpTransactionInternal(
                db = db,
                transaction = XpTransaction(
                    eventId = XpTransaction.achievementKey(achievementId),
                    eventType = XpEventType.ACHIEVEMENT_UNLOCKED,
                    xpAmount = xpReward,
                    description = "Achievement Unlocked: $title",
                    createdAt = timestamp,
                    localDate = localDate
                )
            )
            return true
        }
        return false
    }

    /**
     * Awards Focus Session XP (+20 XP) if the session is COMPLETED and actualDurationMs >= 80% of target.
     * Independent of streak qualification.
     */
    @Synchronized
    fun evaluateAndAwardFocusSessionXp(
        session: FocusSession,
        timestamp: Long = session.endTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
    ): Boolean {
        if (!ProgressionDecisionEngine.isFocusSessionEligibleForXp(session)) {
            return false
        }
        if (session.id <= 0L) return false

        val awarded = awardXpTransaction(
            XpTransaction(
                eventId = XpTransaction.focusSessionKey(session.id),
                eventType = XpEventType.FOCUS_SESSION_COMPLETED,
                xpAmount = XpEventType.FOCUS_SESSION_COMPLETED.defaultXp,
                description = "Completed ${session.sessionType.displayName} session",
                createdAt = timestamp,
                localDate = session.dateString
            )
        )

        if (awarded) {
            refreshFocusAchievements(timestamp, session.dateString)
        }
        return awarded
    }

    private fun refreshFocusAchievements(timestamp: Long, localDate: String) {
        val db = writableDatabase
        val allFocus = getRecentFocusSessions(limit = 500)
        val eligibleCompleted = allFocus.filter { ProgressionDecisionEngine.isFocusSessionEligibleForXp(it) }
        val completedCount = eligibleCompleted.size
        val totalFocusMinutes = (eligibleCompleted.sumOf { it.actualDurationMs } / 60_000L).toInt()

        updateAchievementProgressInternal(db, "first_focus", completedCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "focus_5_sessions", completedCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "focus_20_sessions", completedCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "focus_60_minutes", totalFocusMinutes, timestamp, localDate)
    }

    @Synchronized
    fun getStreakRecordForDate(dateKey: String): StreakRecord? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SR_DATE, $COL_SR_QUALIFIED, $COL_SR_REASON, $COL_SR_CREATED_AT FROM $TABLE_STREAK_RECORDS WHERE $COL_SR_DATE = ?",
            arrayOf(dateKey)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return StreakRecord(
                    localDate = it.getString(0),
                    isQualified = it.getInt(1) == 1,
                    qualifyingReason = it.getString(2),
                    createdAt = it.getLong(3)
                )
            }
        }
        return null
    }

    @Synchronized
    fun getAllStreakRecords(): List<StreakRecord> {
        return getAllStreakRecordsInternal(readableDatabase)
    }

    private fun getAllStreakRecordsInternal(db: SQLiteDatabase): List<StreakRecord> {
        val list = mutableListOf<StreakRecord>()
        val cursor = db.rawQuery(
            "SELECT $COL_SR_DATE, $COL_SR_QUALIFIED, $COL_SR_REASON, $COL_SR_CREATED_AT FROM $TABLE_STREAK_RECORDS ORDER BY $COL_SR_DATE ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    StreakRecord(
                        localDate = it.getString(0),
                        isQualified = it.getInt(1) == 1,
                        qualifyingReason = it.getString(2),
                        createdAt = it.getLong(3)
                    )
                )
            }
        }
        return list
    }

    /**
     * Evaluates a completed past calendar day (dateKey < todayDateKey), persists streak_records,
     * awards eligible daily-goal and mindful-day XP idempotently, and updates streaks/milestones.
     */
    @Synchronized
    fun evaluateAndFinalizePastDay(
        dateKey: String,
        todayDateKey: String = DateTimeUtils.getTodayDateString(),
        dailyResetCount: Int = 0,
        evaluationTimestamp: Long = System.currentTimeMillis()
    ): DailyProgressionEvaluation {
        val coverage = recalculateAndSaveDailyCoverage(dateKey, evaluationTimestamp)
        val snapshots = getGoalSnapshotsForDate(dateKey, rebuildFromHistory = true)
        val usage = getDailyUsageMetrics(dateKey)
        val focusSessions = getFocusSessionsForDate(dateKey)

        val evaluation = ProgressionDecisionEngine.evaluateDay(
            dateKey = dateKey,
            todayDateKey = todayDateKey,
            coverage = coverage,
            dailyResetCount = dailyResetCount,
            goalSnapshots = snapshots,
            usage = usage,
            focusSessions = focusSessions
        )

        // Never persist or award daily finalization for the current in-progress day
        if (evaluation.streakStatus == StreakEvaluationStatus.PENDING) {
            return evaluation
        }

        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. Persist streak_records entry
            val isQualified = evaluation.streakStatus == StreakEvaluationStatus.QUALIFIED
            val srCv = ContentValues().apply {
                put(COL_SR_DATE, dateKey)
                put(COL_SR_QUALIFIED, if (isQualified) 1 else 0)
                put(COL_SR_REASON, evaluation.streakReason)
                put(COL_SR_CREATED_AT, evaluationTimestamp)
            }
            db.insertWithOnConflict(TABLE_STREAK_RECORDS, null, srCv, SQLiteDatabase.CONFLICT_REPLACE)

            // 2. Award Daily Goal XP (once per goal type, never per scope)
            if (evaluation.awardCountGoalXp) {
                awardXpTransactionInternal(
                    db = db,
                    transaction = XpTransaction(
                        eventId = XpTransaction.dailyCountGoalKey(dateKey),
                        eventType = XpEventType.DAILY_GOAL_COUNT_MET,
                        xpAmount = XpEventType.DAILY_GOAL_COUNT_MET.defaultXp,
                        description = "Met daily Reel count goal ($dateKey)",
                        createdAt = evaluationTimestamp,
                        localDate = dateKey
                    )
                )
            }

            if (evaluation.awardTimeGoalXp) {
                awardXpTransactionInternal(
                    db = db,
                    transaction = XpTransaction(
                        eventId = XpTransaction.dailyTimeGoalKey(dateKey),
                        eventType = XpEventType.DAILY_GOAL_TIME_MET,
                        xpAmount = XpEventType.DAILY_GOAL_TIME_MET.defaultXp,
                        description = "Met daily viewing-time goal ($dateKey)",
                        createdAt = evaluationTimestamp,
                        localDate = dateKey
                    )
                )
            }

            if (evaluation.awardBothGoalsBonusXp) {
                awardXpTransactionInternal(
                    db = db,
                    transaction = XpTransaction(
                        eventId = XpTransaction.bothDailyGoalsKey(dateKey),
                        eventType = XpEventType.BOTH_DAILY_GOALS_BONUS,
                        xpAmount = XpEventType.BOTH_DAILY_GOALS_BONUS.defaultXp,
                        description = "Met both daily goals bonus ($dateKey)",
                        createdAt = evaluationTimestamp,
                        localDate = dateKey
                    )
                )
            }

            if (evaluation.awardMindfulDayXp) {
                awardXpTransactionInternal(
                    db = db,
                    transaction = XpTransaction(
                        eventId = XpTransaction.mindfulDayKey(dateKey),
                        eventType = XpEventType.MINDFUL_DAY_QUALIFIED,
                        xpAmount = XpEventType.MINDFUL_DAY_QUALIFIED.defaultXp,
                        description = "Qualified for a Mindful Day ($dateKey)",
                        createdAt = evaluationTimestamp,
                        localDate = dateKey
                    )
                )
            }

            // 3. Recompute contiguous streaks across streak_records and check 3/7/30 milestones
            recomputeStreaksAndMilestonesInternal(db, evaluationTimestamp, dateKey)

            // 4. Refresh habit achievements
            refreshHabitAndStreakAchievementsInternal(db, evaluationTimestamp, dateKey)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return evaluation
    }

    private fun recomputeStreaksAndMilestonesInternal(
        db: SQLiteDatabase,
        timestamp: Long,
        latestEvaluatedDate: String
    ) {
        val records = getAllStreakRecordsInternal(db)
        var runningStreak = 0
        var maxStreak = 0
        var prevDateKey: String? = null
        var lastQualDate: String? = null

        for (rec in records) {
            if (rec.isQualified) {
                if (prevDateKey != null && isNextCalendarDay(prevDateKey, rec.localDate)) {
                    runningStreak++
                } else {
                    runningStreak = 1
                }
                if (runningStreak > maxStreak) {
                    maxStreak = runningStreak
                }
                lastQualDate = rec.localDate
                prevDateKey = rec.localDate

                // Award milestone XP ONLY when the streak reaches exactly 3, 7, or 30 days
                val milestoneXp = ProgressionDecisionEngine.STREAK_MILESTONES[runningStreak]
                if (milestoneXp != null) {
                    awardXpTransactionInternal(
                        db = db,
                        transaction = XpTransaction(
                            eventId = XpTransaction.streakMilestoneKey(runningStreak),
                            eventType = XpEventType.STREAK_MILESTONE,
                            xpAmount = milestoneXp,
                            description = "$runningStreak-day mindful streak milestone!",
                            createdAt = timestamp,
                            localDate = rec.localDate
                        )
                    )
                }
            } else {
                runningStreak = 0
                prevDateKey = rec.localDate
            }
        }

        val currentProgress = getUserProgressInternal(db)
        val finalLongest = maxOf(currentProgress.longestStreak, maxStreak)
        val upCv = ContentValues().apply {
            put(COL_UP_ID, 1)
            put(COL_UP_TOTAL_XP, currentProgress.totalXp)
            put(COL_UP_LEVEL, LevelCalculator.levelForXp(currentProgress.totalXp))
            put(COL_UP_CURR_STREAK, runningStreak)
            put(COL_UP_LONG_STREAK, finalLongest)
            if (lastQualDate != null) {
                put(COL_UP_LAST_QUAL_DATE, lastQualDate)
            } else {
                putNull(COL_UP_LAST_QUAL_DATE)
            }
            put(COL_UP_UPDATED_AT, timestamp)
        }
        db.insertWithOnConflict(TABLE_USER_PROGRESS, null, upCv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun refreshHabitAndStreakAchievementsInternal(
        db: SQLiteDatabase,
        timestamp: Long,
        localDate: String
    ) {
        val progress = getUserProgressInternal(db)
        updateAchievementProgressInternal(db, "streak_3_days", progress.longestStreak, timestamp, localDate)
        updateAchievementProgressInternal(db, "streak_7_days", progress.longestStreak, timestamp, localDate)

        // Count distinct goal days, both-goals days, and mindful days from xp_transactions
        val goalDayCursor = db.rawQuery(
            "SELECT COUNT(DISTINCT $COL_XP_LOCAL_DATE) FROM $TABLE_XP_TRANSACTIONS WHERE $COL_XP_EVENT_TYPE IN ('${XpEventType.DAILY_GOAL_COUNT_MET.name}', '${XpEventType.DAILY_GOAL_TIME_MET.name}')",
            null
        )
        var goalDaysCount = 0
        goalDayCursor.use {
            if (it.moveToFirst()) goalDaysCount = it.getInt(0)
        }

        val bothDayCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_XP_TRANSACTIONS WHERE $COL_XP_EVENT_TYPE = '${XpEventType.BOTH_DAILY_GOALS_BONUS.name}'",
            null
        )
        var bothDaysCount = 0
        bothDayCursor.use {
            if (it.moveToFirst()) bothDaysCount = it.getInt(0)
        }

        val mindfulCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_XP_TRANSACTIONS WHERE $COL_XP_EVENT_TYPE = '${XpEventType.MINDFUL_DAY_QUALIFIED.name}'",
            null
        )
        var mindfulDaysCount = 0
        mindfulCursor.use {
            if (it.moveToFirst()) mindfulDaysCount = it.getInt(0)
        }

        updateAchievementProgressInternal(db, "first_goal_day", goalDaysCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "goal_days_10", goalDaysCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "both_goals_day", bothDaysCount, timestamp, localDate)
        updateAchievementProgressInternal(db, "mindful_days_5", mindfulDaysCount, timestamp, localDate)
    }

    private fun isNextCalendarDay(prevDateStr: String, nextDateStr: String): Boolean {
        val prevDate = DateTimeUtils.parseDate(prevDateStr) ?: return false
        val cal = Calendar.getInstance().apply {
            time = prevDate
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return DateTimeUtils.getTodayDateString(cal.time) == nextDateStr
    }
}
