package com.reelmonster.app.domain.model.backup

/**
 * Phase 15 — Restore planning (proposal §8).
 *
 * The planner is pure: it takes a validated payload and the current
 * database state (primary keys + canonical row strings) and produces
 * a plan. Nothing is mutated here.
 *
 * Merge (default):
 * - A backup row whose primary key does not exist yet -> insert.
 * - A backup row identical to the existing row (same key, same
 *   canonical content) -> already present, skip.
 * - A backup row with the same key but different content -> conflict.
 *   Conflicts are NEVER silently overwritten or discarded: the plan
 *   reports them and the caller's chosen [ConflictPolicy] decides:
 *   KEEP_EXISTING (skip), USE_BACKUP (update), or CANCEL (abort the
 *   whole restore before any mutation).
 *
 * Full restore:
 * - Replaces the entire database. The planner reports what will be
 *   deleted and inserted; the importer requires a verified
 *   pre-restore backup of the current database before it deletes
 *   anything, and runs the replacement in one transaction.
 */
enum class RestoreMode { MERGE, FULL }

enum class ConflictPolicy { KEEP_EXISTING, USE_BACKUP, CANCEL }

data class RestoreConflict(
    val table: String,
    val pkValue: String,
    val existingRowJson: String,
    val backupRowJson: String
)

data class TableRestorePlan(
    val table: String,
    val toInsert: List<Map<String, JsonValue>>,
    val toUpdate: List<Map<String, JsonValue>>,
    val alreadyPresent: Int,
    val conflicts: List<RestoreConflict>
)

data class RestorePlan(
    val mode: RestoreMode,
    val conflictPolicy: ConflictPolicy,
    val tables: List<TableRestorePlan>,
    /** True when the policy is CANCEL and at least one conflict exists. */
    val cancelledByConflict: Boolean,
    val totalToInsert: Int,
    val totalToUpdate: Int,
    val totalAlreadyPresent: Int,
    val totalConflicts: Int,
    /** For FULL mode: rows that will be deleted from each table. */
    val rowsToDelete: Map<String, Int>
)

object RestorePlanner {

    /**
     * @param existingRows table -> (primary-key string -> canonical row JSON).
     *   Canonical row JSON is [BackupJson.stringify] of the row object.
     */
    fun plan(
        payload: BackupPayload,
        existingRows: Map<String, Map<String, String>>,
        mode: RestoreMode,
        conflictPolicy: ConflictPolicy
    ): RestorePlan {
        val tablePlans = mutableListOf<TableRestorePlan>()
        val rowsToDelete = mutableMapOf<String, Int>()
        var totalInsert = 0
        var totalUpdate = 0
        var totalPresent = 0
        var totalConflicts = 0

        for (table in BackupSchema.TABLES) {
            val backupRows = payload.tables[table.name].orEmpty()
            val existing = existingRows[table.name].orEmpty()
            rowsToDelete[table.name] = existing.size

            if (mode == RestoreMode.FULL) {
                tablePlans.add(
                    TableRestorePlan(
                        table = table.name,
                        toInsert = backupRows,
                        toUpdate = emptyList(),
                        alreadyPresent = 0,
                        conflicts = emptyList()
                    )
                )
                totalInsert += backupRows.size
                continue
            }

            val toInsert = mutableListOf<Map<String, JsonValue>>()
            val toUpdate = mutableListOf<Map<String, JsonValue>>()
            val conflicts = mutableListOf<RestoreConflict>()
            var present = 0
            for (row in backupRows) {
                val pk = pkString(table, row) ?: continue
                val existingJson = existing[pk]
                val backupJson = BackupJson.stringify(JsonObj(row))
                when {
                    existingJson == null -> toInsert.add(row)
                    existingJson == backupJson -> present++
                    else -> {
                        conflicts.add(
                            RestoreConflict(
                                table = table.name,
                                pkValue = pk,
                                existingRowJson = existingJson,
                                backupRowJson = backupJson
                            )
                        )
                        if (conflictPolicy == ConflictPolicy.USE_BACKUP) {
                            toUpdate.add(row)
                        }
                    }
                }
            }
            tablePlans.add(
                TableRestorePlan(
                    table = table.name,
                    toInsert = toInsert,
                    toUpdate = toUpdate,
                    alreadyPresent = present,
                    conflicts = conflicts
                )
            )
            totalInsert += toInsert.size
            totalUpdate += toUpdate.size
            totalPresent += present
            totalConflicts += conflicts.size
        }

        val cancelled = mode == RestoreMode.MERGE &&
            conflictPolicy == ConflictPolicy.CANCEL &&
            totalConflicts > 0
        return RestorePlan(
            mode = mode,
            conflictPolicy = conflictPolicy,
            tables = tablePlans,
            cancelledByConflict = cancelled,
            totalToInsert = totalInsert,
            totalToUpdate = totalUpdate,
            totalAlreadyPresent = totalPresent,
            totalConflicts = totalConflicts,
            rowsToDelete = if (mode == RestoreMode.FULL) rowsToDelete else emptyMap()
        )
    }

    /** Primary-key value as a string, for identity comparison. */
    fun pkString(table: BackupTable, row: Map<String, JsonValue>): String? {
        return when (val pk = row[table.pkColumn]) {
            is JsonInt -> pk.value.toString()
            is JsonStr -> pk.value
            else -> null
        }
    }

    /** Canonical JSON of one row, for identical-row detection. */
    fun rowJson(row: Map<String, JsonValue>): String = BackupJson.stringify(JsonObj(row))
}
