package com.reelmonster.app.data.backup

import com.reelmonster.app.domain.model.backup.BackupPayload
import com.reelmonster.app.domain.model.backup.BackupSchema
import com.reelmonster.app.domain.model.backup.BackupValidation
import com.reelmonster.app.domain.model.backup.BackupValidator
import com.reelmonster.app.domain.model.backup.ConflictPolicy
import com.reelmonster.app.domain.model.backup.RestoreJournal
import com.reelmonster.app.domain.model.backup.RestoreMode
import com.reelmonster.app.domain.model.backup.RestorePlan
import com.reelmonster.app.domain.model.backup.RestorePlanner
import java.util.UUID

/**
 * Phase 15 — Offline backup importer (proposal §8).
 *
 * Rules enforced here, in order:
 * 1. The backup is validated FULLY before any database mutation.
 *    An invalid file changes nothing.
 * 2. Merge (default): inserts new rows, updates only conflicts the
 *    user explicitly resolved with USE_BACKUP, never silently
 *    overwrites. A plan cancelled by conflict policy changes nothing.
 * 3. Full restore: requires a verified pre-restore backup of the
 *    current database first (validated with the same validator).
 *    The replacement runs in ONE transaction: delete all, insert
 *    all, verify integrity and counts. Any failure rolls the whole
 *    transaction back and the pre-restore backup remains available.
 * 4. Every attempt is journaled (see [RestoreJournal]) so an
 *    interruption is detectable and reportable at startup.
 *
 * The importer never deletes historical data except as part of a
 * user-confirmed full restore that already has a verified
 * pre-restore backup.
 */
sealed interface ImportResult {
    data class Success(
        val mode: RestoreMode,
        val inserted: Int,
        val updated: Int,
        val alreadyPresent: Int,
        val deleted: Int
    ) : ImportResult

    data class Failure(
        val mode: RestoreMode,
        val error: String,
        /** True when a verified pre-restore backup exists for recovery. */
        val preRestoreBackupAvailable: Boolean
    ) : ImportResult
}

class BackupImporter(
    private val store: BackupTableStore,
    private val journal: RestoreJournal
) {

    /** Validates [backupJson] and builds a plan against current data. */
    fun validateAndPlan(
        backupJson: String,
        mode: RestoreMode,
        conflictPolicy: ConflictPolicy
    ): Pair<BackupValidation, RestorePlan?> {
        val validation = BackupValidator.validate(backupJson)
        if (validation !is BackupValidation.Valid) return validation to null
        val existing = BackupSchema.TABLES.associate { table ->
            table.name to store.existingRows(table)
        }
        val plan = RestorePlanner.plan(
            payload = validation.payload,
            existingRows = existing,
            mode = mode,
            conflictPolicy = conflictPolicy
        )
        return validation to plan
    }

    /** Applies a merge plan. The payload must already be validated. */
    fun importMerge(payload: BackupPayload, plan: RestorePlan): ImportResult {
        require(plan.mode == RestoreMode.MERGE) { "importMerge requires a MERGE plan." }
        if (plan.cancelledByConflict) {
            return ImportResult.Failure(
                mode = RestoreMode.MERGE,
                error = "Restore cancelled: ${plan.totalConflicts} conflicting record(s) " +
                    "and the conflict policy is CANCEL. Nothing was changed.",
                preRestoreBackupAvailable = false
            )
        }
        val entry = journal.begin(UUID.randomUUID().toString(), RestoreMode.MERGE)
        return try {
            val counts = store.transaction {
                var inserted = 0
                var updated = 0
                for (tablePlan in plan.tables) {
                    val table = BackupSchema.tableByName(tablePlan.table) ?: continue
                    for (row in tablePlan.toInsert) {
                        store.insertRow(table, row)
                        inserted++
                    }
                    for (row in tablePlan.toUpdate) {
                        store.updateRow(table, row)
                        updated++
                    }
                }
                inserted to updated
            }
            if (!store.integrityCheck()) {
                throw IllegalStateException("Integrity check failed after merge restore.")
            }
            journal.markCommitted(entry)
            ImportResult.Success(
                mode = RestoreMode.MERGE,
                inserted = counts.first,
                updated = counts.second,
                alreadyPresent = plan.totalAlreadyPresent,
                deleted = 0
            )
        } catch (e: Exception) {
            journal.markRolledBack(entry, e.message ?: "Unknown error during merge restore.")
            ImportResult.Failure(
                mode = RestoreMode.MERGE,
                error = "Merge restore failed and was rolled back: ${e.message}",
                preRestoreBackupAvailable = false
            )
        }
    }

    /**
     * Applies a full restore. [preRestoreBackupJson] must be a valid
     * backup of the CURRENT database, created and verified before this
     * call. Without it, the restore is refused outright.
     */
    fun importFull(
        payload: BackupPayload,
        plan: RestorePlan,
        preRestoreBackupJson: String
    ): ImportResult {
        require(plan.mode == RestoreMode.FULL) { "importFull requires a FULL plan." }
        val preValidation = BackupValidator.validate(preRestoreBackupJson)
        if (preValidation !is BackupValidation.Valid) {
            return ImportResult.Failure(
                mode = RestoreMode.FULL,
                error = "Full restore refused: the pre-restore backup of the current " +
                    "database did not validate. Nothing was changed.",
                preRestoreBackupAvailable = false
            )
        }
        val preChecksum = preValidation.payload.manifest.checksumSha256
        val entry = journal.begin(UUID.randomUUID().toString(), RestoreMode.FULL)
        journal.markPreBackupDone(entry, preChecksum)
        return try {
            val deleted = store.transaction {
                var deletedCount = 0
                for (table in BackupSchema.TABLES.reversed()) {
                    deletedCount += store.rowCount(table)
                    store.deleteAllRows(table)
                }
                for (tablePlan in plan.tables) {
                    val table = BackupSchema.tableByName(tablePlan.table) ?: continue
                    for (row in tablePlan.toInsert) {
                        store.insertRow(table, row)
                    }
                }
                // Verify inside the transaction: counts + integrity.
                for (table in BackupSchema.TABLES) {
                    val expected = payload.tables[table.name].orEmpty().size
                    val actual = store.rowCount(table)
                    check(actual == expected) {
                        "Row count mismatch for '${table.name}' after restore: " +
                            "expected $expected, found $actual."
                    }
                }
                check(store.integrityCheck()) {
                    "Integrity check failed after full restore."
                }
                deletedCount
            }
            journal.markCommitted(entry)
            ImportResult.Success(
                mode = RestoreMode.FULL,
                inserted = plan.totalToInsert,
                updated = 0,
                alreadyPresent = 0,
                deleted = deleted
            )
        } catch (e: Exception) {
            journal.markRolledBack(entry, e.message ?: "Unknown error during full restore.")
            ImportResult.Failure(
                mode = RestoreMode.FULL,
                error = "Full restore failed and was rolled back: ${e.message}. " +
                    "The pre-restore backup is still available for recovery.",
                preRestoreBackupAvailable = true
            )
        }
    }
}
