package com.reelmonster.app.domain.model.backup

/**
 * Phase 15 — Backup schema (proposal §8).
 *
 * The backup covers every user-data table in the schema-v6 database.
 * Each table declares its exact column set (taken from the
 * `CREATE TABLE` statements in `ReelDbHelper`), its primary key, and
 * its column types. The validator requires an exact column match:
 * unknown or missing columns are rejected, so a backup from a
 * different schema can never be silently half-restored.
 *
 * Insert order is parent-first (goals before goal history/snapshots).
 * Delete order for a full restore is the reverse.
 */
enum class BackupColType {
    INT, TEXT, INT_NULLABLE, TEXT_NULLABLE
}

data class BackupColumn(
    val name: String,
    val type: BackupColType
)

data class BackupTable(
    val name: String,
    /** Primary-key column (identity for merge conflict detection). */
    val pkColumn: String,
    val columns: List<BackupColumn>
) {
    val columnNames: List<String> get() = columns.map { it.name }
}

object BackupSchema {
    const val CURRENT_SCHEMA_VERSION = 6

    val TABLES: List<BackupTable> = listOf(
        BackupTable(
            name = "daily_goals",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("goal_type", BackupColType.TEXT),
                BackupColumn("platform_scope", BackupColType.TEXT),
                BackupColumn("target_value", BackupColType.INT),
                BackupColumn("is_enabled", BackupColType.INT),
                BackupColumn("created_at", BackupColType.INT),
                BackupColumn("updated_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "daily_aggregates",
            pkColumn = "date_key",
            columns = listOf(
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("total_reels", BackupColType.INT),
                BackupColumn("ig_count", BackupColType.INT),
                BackupColumn("yt_count", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "reel_events",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("timestamp", BackupColType.INT),
                BackupColumn("target_app", BackupColType.TEXT),
                BackupColumn("tracking_mode", BackupColType.TEXT),
                BackupColumn("session_date", BackupColType.TEXT)
            )
        ),
        BackupTable(
            name = "viewing_sessions",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("target_app", BackupColType.TEXT),
                BackupColumn("fingerprint", BackupColType.TEXT_NULLABLE),
                BackupColumn("start_timestamp", BackupColType.INT),
                BackupColumn("end_timestamp", BackupColType.INT),
                BackupColumn("duration_ms", BackupColType.INT),
                BackupColumn("completion_status", BackupColType.TEXT),
                BackupColumn("session_date", BackupColType.TEXT)
            )
        ),
        BackupTable(
            name = "focus_sessions",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("session_type", BackupColType.TEXT),
                BackupColumn("target_duration_ms", BackupColType.INT),
                BackupColumn("actual_duration_ms", BackupColType.INT),
                BackupColumn("start_timestamp", BackupColType.INT),
                BackupColumn("end_timestamp", BackupColType.INT),
                BackupColumn("completion_status", BackupColType.TEXT),
                BackupColumn("date_string", BackupColType.TEXT)
            )
        ),
        BackupTable(
            name = "monitoring_intervals",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("start_timestamp", BackupColType.INT),
                BackupColumn("end_timestamp", BackupColType.INT),
                BackupColumn("monotonic_start_ms", BackupColType.INT),
                BackupColumn("monotonic_end_ms", BackupColType.INT),
                BackupColumn("service_connected", BackupColType.INT),
                BackupColumn("tracking_enabled", BackupColType.INT),
                BackupColumn("screen_on", BackupColType.INT),
                BackupColumn("interruption_reason", BackupColType.TEXT_NULLABLE),
                BackupColumn("estimated_verified_duration_ms", BackupColType.INT),
                BackupColumn("continuity_status", BackupColType.TEXT),
                BackupColumn("boot_session_id", BackupColType.TEXT),
                BackupColumn("is_active", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "monitoring_heartbeats",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("timestamp", BackupColType.INT),
                BackupColumn("monotonic_ms", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("service_connected", BackupColType.INT),
                BackupColumn("tracking_enabled", BackupColType.INT),
                BackupColumn("screen_on", BackupColType.INT),
                BackupColumn("continuity_status", BackupColType.TEXT),
                BackupColumn("boot_session_id", BackupColType.TEXT)
            )
        ),
        BackupTable(
            name = "supported_app_detection_events",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("timestamp", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("platform", BackupColType.TEXT),
                BackupColumn("event_type", BackupColType.TEXT),
                BackupColumn("detection_status", BackupColType.TEXT),
                BackupColumn("content_fingerprint", BackupColType.TEXT_NULLABLE)
            )
        ),
        BackupTable(
            name = "daily_monitoring_coverage",
            pkColumn = "date_key",
            columns = listOf(
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("service_connected_ms", BackupColType.INT),
                BackupColumn("tracking_enabled_ms", BackupColType.INT),
                BackupColumn("screen_on_ms", BackupColType.INT),
                BackupColumn("estimated_verified_monitoring_ms", BackupColType.INT),
                BackupColumn("heartbeat_count", BackupColType.INT),
                BackupColumn("detection_event_count", BackupColType.INT),
                BackupColumn("interruption_count", BackupColType.INT),
                BackupColumn("recovery_count", BackupColType.INT),
                BackupColumn("coverage_status", BackupColType.TEXT),
                BackupColumn("calculated_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "daily_goal_records",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("goal_type", BackupColType.TEXT),
                BackupColumn("platform_scope", BackupColType.TEXT),
                BackupColumn("target_value", BackupColType.INT),
                BackupColumn("actual_value", BackupColType.INT),
                BackupColumn("was_successful", BackupColType.INT),
                BackupColumn("recorded_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "daily_goal_history",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("event_id", BackupColType.TEXT),
                BackupColumn("goal_id", BackupColType.INT),
                BackupColumn("goal_type", BackupColType.TEXT),
                BackupColumn("platform_scope", BackupColType.TEXT),
                BackupColumn("event_type", BackupColType.TEXT),
                BackupColumn("previous_value", BackupColType.TEXT_NULLABLE),
                BackupColumn("new_value", BackupColType.TEXT),
                BackupColumn("effective_timestamp", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("timezone_id", BackupColType.TEXT),
                BackupColumn("created_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "daily_goal_snapshots",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("date_key", BackupColType.TEXT),
                BackupColumn("goal_id", BackupColType.INT),
                BackupColumn("goal_type", BackupColType.TEXT),
                BackupColumn("platform_scope", BackupColType.TEXT),
                BackupColumn("enabled_at_midnight", BackupColType.INT),
                BackupColumn("target_at_midnight", BackupColType.INT_NULLABLE),
                BackupColumn("strictest_target", BackupColType.INT),
                BackupColumn("first_activation_timestamp", BackupColType.INT_NULLABLE),
                BackupColumn("was_disabled_during_day", BackupColType.INT),
                BackupColumn("was_reenabled_midday", BackupColType.INT),
                BackupColumn("was_violated", BackupColType.INT),
                BackupColumn("progression_eligible", BackupColType.INT),
                BackupColumn("usage_since_midnight", BackupColType.INT),
                BackupColumn("usage_since_activation", BackupColType.INT),
                BackupColumn("last_history_event_id", BackupColType.INT_NULLABLE),
                BackupColumn("updated_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "xp_transactions",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("event_id", BackupColType.TEXT),
                BackupColumn("event_type", BackupColType.TEXT),
                BackupColumn("xp_amount", BackupColType.INT),
                BackupColumn("description", BackupColType.TEXT),
                BackupColumn("created_at", BackupColType.INT),
                BackupColumn("local_date", BackupColType.TEXT)
            )
        ),
        BackupTable(
            name = "user_progress",
            pkColumn = "id",
            columns = listOf(
                BackupColumn("id", BackupColType.INT),
                BackupColumn("total_xp", BackupColType.INT),
                BackupColumn("current_level", BackupColType.INT),
                BackupColumn("current_streak", BackupColType.INT),
                BackupColumn("longest_streak", BackupColType.INT),
                BackupColumn("last_qualified_date", BackupColType.TEXT_NULLABLE),
                BackupColumn("updated_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "achievements",
            pkColumn = "achievement_id",
            columns = listOf(
                BackupColumn("achievement_id", BackupColType.TEXT),
                BackupColumn("category", BackupColType.TEXT),
                BackupColumn("title", BackupColType.TEXT),
                BackupColumn("description", BackupColType.TEXT),
                BackupColumn("target_progress", BackupColType.INT),
                BackupColumn("current_progress", BackupColType.INT),
                BackupColumn("is_unlocked", BackupColType.INT),
                BackupColumn("unlocked_at", BackupColType.INT_NULLABLE),
                BackupColumn("xp_reward", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "streak_records",
            pkColumn = "local_date",
            columns = listOf(
                BackupColumn("local_date", BackupColType.TEXT),
                BackupColumn("is_qualified", BackupColType.INT),
                BackupColumn("qualifying_reason", BackupColType.TEXT),
                BackupColumn("created_at", BackupColType.INT)
            )
        ),
        BackupTable(
            name = "system_metadata",
            pkColumn = "meta_key",
            columns = listOf(
                BackupColumn("meta_key", BackupColType.TEXT),
                BackupColumn("meta_value", BackupColType.TEXT),
                BackupColumn("updated_at", BackupColType.INT)
            )
        )
    )

    fun tableByName(name: String): BackupTable? = TABLES.firstOrNull { it.name == name }

    /** Known enum value sets, used by the validator. */
    object Enums {
        val TARGET_APPS = setOf("INSTAGRAM", "YOUTUBE")
        val TRACKING_MODES = setOf("LEVEL_1_MANUAL", "LEVEL_2_AUTOMATIC")
        val CONTINUITY_STATUSES = setOf("VERIFIED", "UNCERTAIN", "INTERRUPTED")
        val COVERAGE_STATUSES = setOf("ESTIMATED_VERIFIED", "ESTIMATED_INCOMPLETE", "UNVERIFIED")
        val SESSION_COMPLETION_STATUSES = setOf(
            "COMPLETED", "INTERRUPTED_APP_SWITCH", "INTERRUPTED_SCREEN_OFF",
            "INTERRUPTED_PAUSED", "INTERRUPTED_GLOBAL_OFF", "INTERRUPTED_RESET"
        )
        val FOCUS_COMPLETION_STATUSES = setOf("COMPLETED", "CANCELLED", "ABANDONED")
        val GOAL_TYPES = setOf("REEL_COUNT", "VIEWING_TIME_MINUTES")
        val GOAL_SCOPES = setOf("COMBINED", "INSTAGRAM", "YOUTUBE")
    }
}
