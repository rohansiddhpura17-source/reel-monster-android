package com.reelmonster.app.domain.model.backup

import java.time.LocalDate

/**
 * Phase 15 — Backup validation (proposal §8).
 *
 * Validates a backup file FULLY, before any database mutation:
 * 1. Size limit.
 * 2. Strict JSON parse.
 * 3. Format id and version.
 * 4. Schema compatibility: v6 supported; newer rejected with
 *    UPGRADE_REQUIRED; older rejected (no tested migration exists for
 *    older schemas — never downgrade, never guess).
 * 5. Manifest fields and record counts.
 * 6. SHA-256 checksum over the canonical tables JSON.
 * 7. Per-table exact column sets, value types, ranges, and enums.
 * 8. Duplicate primary keys / duplicate unique identifiers.
 * 9. Foreign keys: `daily_goal_history.goal_id` and
 *    `daily_goal_snapshots.goal_id` must exist in `daily_goals`.
 *
 * Nothing here touches the database. A file that fails any check is
 * rejected with every error found, so the user gets one complete
 * report instead of a new error per attempt.
 */
sealed interface BackupValidation {
    data class Valid(val payload: BackupPayload) : BackupValidation
    data class Invalid(val errors: List<String>) : BackupValidation
}

object BackupValidator {

    fun validate(json: String): BackupValidation {
        val errors = mutableListOf<String>()

        if (json.toByteArray(Charsets.UTF_8).size > BackupFormat.MAX_BACKUP_BYTES) {
            return BackupValidation.Invalid(
                listOf("Backup file exceeds the ${BackupFormat.MAX_BACKUP_BYTES} byte limit.")
            )
        }

        val root = BackupJson.parse(json)
            ?: return BackupValidation.Invalid(listOf("Backup file is not valid JSON."))
        val rootObj = root.asObj()
            ?: return BackupValidation.Invalid(listOf("Backup root must be a JSON object."))

        val formatId = rootObj.entries["format_id"]?.asStr()
        if (formatId != BackupFormat.FORMAT_ID) {
            errors.add("Unknown backup format: '${formatId ?: "missing"}' (expected '${BackupFormat.FORMAT_ID}').")
        }
        val formatVersion = rootObj.entries["format_version"]?.asLong()
        if (formatVersion != BackupFormat.FORMAT_VERSION.toLong()) {
            errors.add(
                "Unsupported backup format version: '${formatVersion?.toString() ?: "missing"}' " +
                    "(expected ${BackupFormat.FORMAT_VERSION})."
            )
        }

        val manifestObj = rootObj.entries["manifest"]?.asObj()
        if (manifestObj == null) {
            errors.add("Backup manifest is missing or not an object.")
            return BackupValidation.Invalid(errors)
        }
        val schemaVersion = manifestObj.entries["schema_version"]?.asLong()
        when {
            schemaVersion == null -> errors.add("Manifest schema_version is missing.")
            schemaVersion > BackupSchema.CURRENT_SCHEMA_VERSION -> errors.add(
                "Backup was created by a newer app version (schema $schemaVersion, " +
                    "this app supports ${BackupSchema.CURRENT_SCHEMA_VERSION}). " +
                    "UPGRADE_REQUIRED: update the app before restoring this backup."
            )
            schemaVersion < BackupSchema.CURRENT_SCHEMA_VERSION -> errors.add(
                "Backup schema $schemaVersion is older than this app's schema " +
                    "${BackupSchema.CURRENT_SCHEMA_VERSION} and no tested migration exists " +
                    "for it. Restore is refused rather than guessing a migration."
            )
        }

        val exportedAtMs = manifestObj.entries["exported_at_ms"]?.asLong()
        if (exportedAtMs == null || exportedAtMs <= 0L || exportedAtMs > MAX_SANE_TIMESTAMP_MS) {
            errors.add("Manifest exported_at_ms is missing or out of range.")
        }
        val exportedTimezone = manifestObj.entries["exported_timezone"]?.asStr()
        if (exportedTimezone.isNullOrBlank()) {
            errors.add("Manifest exported_timezone is missing.")
        }
        val countsObj = manifestObj.entries["record_counts"]?.asObj()
        if (countsObj == null) {
            errors.add("Manifest record_counts is missing or not an object.")
        }
        val expectedChecksum = manifestObj.entries["checksum_sha256"]?.asStr()
        if (expectedChecksum.isNullOrBlank()) {
            errors.add("Manifest checksum_sha256 is missing.")
        }

        val tablesValue = rootObj.entries["tables"]
        if (tablesValue == null) {
            errors.add("Backup tables section is missing.")
            return BackupValidation.Invalid(errors)
        }
        val tables = BackupChecksum.tablesFromJson(tablesValue)
        if (tables == null) {
            errors.add("Backup tables section is malformed (expected table -> row list).")
            return BackupValidation.Invalid(errors)
        }

        // Counts must match the manifest exactly.
        if (countsObj != null) {
            for ((table, countValue) in countsObj.entries) {
                val expected = countValue.asLong()
                val actual = tables[table]?.size
                if (expected == null) {
                    errors.add("Manifest record count for '$table' is not a number.")
                } else if (actual == null) {
                    errors.add("Manifest lists table '$table' but it is absent from the backup.")
                } else if (actual.toLong() != expected) {
                    errors.add(
                        "Record count mismatch for '$table': manifest says $expected, " +
                            "backup contains $actual."
                    )
                }
            }
            for (table in tables.keys) {
                if (table !in countsObj.entries) {
                    errors.add("Table '$table' is present in the backup but missing from the manifest counts.")
                }
            }
        }

        // Checksum must match the tables exactly as read.
        if (!expectedChecksum.isNullOrBlank()) {
            val actualChecksum = BackupChecksum.checksumOfTables(tables)
            if (actualChecksum != expectedChecksum) {
                errors.add(
                    "Checksum mismatch: the backup file was modified or corrupted " +
                        "after export (expected $expectedChecksum, computed $actualChecksum)."
                )
            }
        }

        // Per-table validation.
        for ((tableName, rows) in tables) {
            val table = BackupSchema.tableByName(tableName)
            if (table == null) {
                errors.add("Unknown table in backup: '$tableName'.")
                continue
            }
            if (rows.size > BackupFormat.MAX_ROWS_PER_TABLE) {
                errors.add("Table '$tableName' has ${rows.size} rows, over the ${BackupFormat.MAX_ROWS_PER_TABLE} limit.")
            }
            val seenPks = mutableSetOf<String>()
            for ((index, row) in rows.withIndex()) {
                validateRow(table, row, index, errors, seenPks)
            }
        }

        // Foreign keys + unique identifiers (only meaningful if rows parsed).
        if (errors.isEmpty()) {
            validateCrossTable(tables, errors)
        }

        if (errors.isNotEmpty()) return BackupValidation.Invalid(errors)

        val counts = countsObj!!.entries.mapValues { (_, v) -> v.asLong()!!.toInt() }
        val manifest = BackupManifest(
            schemaVersion = schemaVersion!!.toInt(),
            exportedAtMs = exportedAtMs!!,
            exportedTimezone = exportedTimezone!!,
            recordCounts = counts,
            checksumSha256 = expectedChecksum!!
        )
        return BackupValidation.Valid(BackupPayload(manifest, tables))
    }

    private fun validateRow(
        table: BackupTable,
        row: Map<String, JsonValue>,
        index: Int,
        errors: MutableList<String>,
        seenPks: MutableSet<String>
    ) {
        val where = "Table '${table.name}' row ${index + 1}"
        val expectedCols = table.columnNames.toSet()
        val actualCols = row.keys
        val missing = expectedCols - actualCols
        val unknown = actualCols - expectedCols
        if (missing.isNotEmpty()) {
            errors.add("$where is missing columns: ${missing.sorted().joinToString(", ")}.")
        }
        if (unknown.isNotEmpty()) {
            errors.add("$where has unknown columns: ${unknown.sorted().joinToString(", ")}.")
        }
        if (missing.isNotEmpty() || unknown.isNotEmpty()) return

        for (column in table.columns) {
            val value = row[column.name]!!
            when (column.type) {
                BackupColType.INT -> {
                    val v = value.asLong()
                    if (v == null) {
                        errors.add("$where column '${column.name}' must be an integer.")
                    } else {
                        validateIntRange(table.name, column.name, v, where, errors)
                    }
                }
                BackupColType.INT_NULLABLE -> {
                    if (value !is JsonNull) {
                        val v = value.asLong()
                        if (v == null) {
                            errors.add("$where column '${column.name}' must be an integer or null.")
                        } else {
                            validateIntRange(table.name, column.name, v, where, errors)
                        }
                    }
                }
                BackupColType.TEXT -> {
                    val v = value.asStr()
                    if (v == null) {
                        errors.add("$where column '${column.name}' must be a string.")
                    } else {
                        validateText(table.name, column.name, v, where, errors)
                    }
                }
                BackupColType.TEXT_NULLABLE -> {
                    if (value !is JsonNull) {
                        val v = value.asStr()
                        if (v == null) {
                            errors.add("$where column '${column.name}' must be a string or null.")
                        } else {
                            validateText(table.name, column.name, v, where, errors)
                        }
                    }
                }
            }
        }

        val pkValue = row[table.pkColumn]
        val pkText = when (pkValue) {
            is JsonInt -> pkValue.value.toString()
            is JsonStr -> pkValue.value
            else -> null
        }
        if (pkText == null) {
            errors.add("$where has an invalid primary key '${table.pkColumn}'.")
        } else if (!seenPks.add(pkText)) {
            errors.add("$where duplicates primary key '${table.pkColumn}' = '$pkText'.")
        }
    }

    private fun validateIntRange(
        table: String,
        column: String,
        value: Long,
        where: String,
        errors: MutableList<String>
    ) {
        fun flag(message: String) = errors.add("$where column '$column' $message.")
        when (column) {
            "id", "goal_id" -> if (value < 1) flag("must be >= 1 (got $value)")
            "timestamp", "start_timestamp", "end_timestamp", "monotonic_start_ms",
            "monotonic_end_ms", "monotonic_ms", "created_at", "updated_at",
            "effective_timestamp", "recorded_at", "calculated_at" -> {
                if (value < 0L || value > MAX_SANE_TIMESTAMP_MS) flag("is out of range ($value)")
            }
            "duration_ms", "target_duration_ms", "actual_duration_ms",
            "estimated_verified_duration_ms", "service_connected_ms",
            "tracking_enabled_ms", "screen_on_ms",
            "estimated_verified_monitoring_ms" -> if (value < 0L) flag("must be >= 0 (got $value)")
            "total_reels", "ig_count", "yt_count", "target_value", "actual_value",
            "target_progress", "current_progress", "xp_reward", "xp_amount",
            "total_xp", "current_streak", "longest_streak", "heartbeat_count",
            "detection_event_count", "interruption_count", "recovery_count",
            "usage_since_midnight", "usage_since_activation",
            "strictest_target" -> if (value < 0L) flag("must be >= 0 (got $value)")
            "current_level" -> if (value !in 1..10) flag("must be 1..10 (got $value)")
            "is_enabled", "is_unlocked", "is_active", "was_successful",
            "is_qualified", "was_disabled_during_day", "was_reenabled_midday",
            "was_violated", "progression_eligible", "enabled_at_midnight",
            "service_connected", "tracking_enabled", "screen_on" -> {
                if (value !in 0L..1L) flag("must be 0 or 1 (got $value)")
            }
        }
        if (table == "user_progress" && column == "id" && value != 1L) {
            flag("must be 1 for the single user_progress row (got $value)")
        }
        if (column == "end_timestamp" || column == "monotonic_end_ms") {
            // Cross-field checks need the whole row; handled in validateCrossTable.
        }
    }

    private fun validateText(
        table: String,
        column: String,
        value: String,
        where: String,
        errors: MutableList<String>
    ) {
        fun flag(message: String) = errors.add("$where column '$column' $message.")
        when (column) {
            "target_app" -> if (value !in BackupSchema.Enums.TARGET_APPS) {
                flag("has unknown value '$value'")
            }
            "tracking_mode" -> if (value !in BackupSchema.Enums.TRACKING_MODES) {
                flag("has unknown value '$value'")
            }
            "continuity_status" -> if (value !in BackupSchema.Enums.CONTINUITY_STATUSES) {
                flag("has unknown value '$value'")
            }
            "coverage_status" -> if (value !in BackupSchema.Enums.COVERAGE_STATUSES) {
                flag("has unknown value '$value'")
            }
            "completion_status" -> {
                val allowed = if (table == "focus_sessions") {
                    BackupSchema.Enums.FOCUS_COMPLETION_STATUSES
                } else {
                    BackupSchema.Enums.SESSION_COMPLETION_STATUSES
                }
                if (value !in allowed) flag("has unknown value '$value'")
            }
            "goal_type" -> if (value !in BackupSchema.Enums.GOAL_TYPES) {
                flag("has unknown value '$value'")
            }
            "platform_scope" -> if (value !in BackupSchema.Enums.GOAL_SCOPES) {
                flag("has unknown value '$value'")
            }
            "date_key", "session_date", "local_date", "date_string" -> {
                if (!isValidDateKey(value)) flag("is not a valid yyyy-MM-dd date ('$value')")
            }
        }
        if (column == "event_id" && value.isBlank()) flag("must not be blank")
        if (column == "achievement_id" && value.isBlank()) flag("must not be blank")
        if (column == "meta_key" && value.isBlank()) flag("must not be blank")
    }

    private fun isValidDateKey(value: String): Boolean {
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) return false
        return try {
            LocalDate.parse(value)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun validateCrossTable(
        tables: Map<String, List<Map<String, JsonValue>>>,
        errors: MutableList<String>
    ) {
        // Goal foreign keys: history + snapshots must reference existing goals.
        val goalIds = tables["daily_goals"].orEmpty().mapNotNull { it["id"]?.asLong() }.toSet()
        for ((tableName, idColumn) in listOf(
            "daily_goal_history" to "goal_id",
            "daily_goal_snapshots" to "goal_id"
        )) {
            for ((index, row) in tables[tableName].orEmpty().withIndex()) {
                val goalId = row[idColumn]?.asLong()
                if (goalId != null && goalId !in goalIds) {
                    errors.add(
                        "Table '$tableName' row ${index + 1} references missing goal_id $goalId."
                    )
                }
            }
        }
        // Unique business identifiers must not repeat.
        for ((tableName, idColumn) in listOf(
            "daily_goal_history" to "event_id",
            "xp_transactions" to "event_id"
        )) {
            val seen = mutableSetOf<String>()
            for ((index, row) in tables[tableName].orEmpty().withIndex()) {
                val id = row[idColumn]?.asStr() ?: continue
                if (!seen.add(id)) {
                    errors.add("Table '$tableName' row ${index + 1} duplicates '$idColumn' = '$id'.")
                }
            }
        }
        // user_progress must have at most one row.
        val progressRows = tables["user_progress"].orEmpty().size
        if (progressRows > 1) {
            errors.add("Table 'user_progress' must have at most 1 row, found $progressRows.")
        }
        // Sessions must not end before they start (wall clock or monotonic).
        for ((index, row) in tables["viewing_sessions"].orEmpty().withIndex()) {
            val start = row["start_timestamp"]?.asLong()
            val end = row["end_timestamp"]?.asLong()
            if (start != null && end != null && end < start) {
                errors.add("Table 'viewing_sessions' row ${index + 1} ends before it starts.")
            }
        }
        for ((index, row) in tables["monitoring_intervals"].orEmpty().withIndex()) {
            val start = row["monotonic_start_ms"]?.asLong()
            val end = row["monotonic_end_ms"]?.asLong()
            if (start != null && end != null && end < start) {
                errors.add("Table 'monitoring_intervals' row ${index + 1} ends before it starts.")
            }
        }
    }

    /** 2100-01-01 in epoch ms; anything later is treated as corrupt data. */
    const val MAX_SANE_TIMESTAMP_MS = 4_102_444_800_000L
}
