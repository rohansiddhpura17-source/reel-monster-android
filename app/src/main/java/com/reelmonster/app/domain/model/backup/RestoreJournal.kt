package com.reelmonster.app.domain.model.backup

import java.io.File

/**
 * Phase 15 — Durable restore journal (proposal §8).
 *
 * A restore is transactional in the database, so an interrupted
 * restore can never leave a half-written database: the transaction
 * either commits or rolls back. The journal is the audit trail that
 * survives the interruption, so startup can detect and report it.
 *
 * States: STARTED -> PRE_BACKUP_DONE -> COMMITTED | ROLLED_BACK.
 * If the app starts and finds STARTED or PRE_BACKUP_DONE with no
 * terminal state, the restore was interrupted: the journal is marked
 * INTERRUPTED and the interruption is reported. The database itself
 * is consistent either way (old data if the transaction rolled back,
 * new data if it committed before the crash) — the journal never
 * claims otherwise.
 *
 * The journal lives in a plain file in the app's files directory
 * (passed in by the caller), NOT in the database being restored, so
 * a full restore can never delete its own journal.
 */
enum class RestoreJournalState {
    STARTED, PRE_BACKUP_DONE, COMMITTED, ROLLED_BACK, INTERRUPTED
}

data class RestoreJournalEntry(
    val restoreId: String,
    val mode: String,
    val startedAtMs: Long,
    val state: RestoreJournalState,
    val preRestoreBackupChecksum: String?,
    val error: String?
)

class RestoreJournal(private val journalFile: File) {

    fun readCurrent(): RestoreJournalEntry? {
        if (!journalFile.exists()) return null
        val text = try {
            journalFile.readText()
        } catch (_: Exception) {
            return null
        }
        val value = BackupJson.parse(text)?.asObj() ?: return null
        val entries = value.entries
        val restoreId = entries["restore_id"]?.asStr() ?: return null
        val mode = entries["mode"]?.asStr() ?: return null
        val startedAtMs = entries["started_at_ms"]?.asLong() ?: return null
        val stateName = entries["state"]?.asStr() ?: return null
        val state = try {
            RestoreJournalState.valueOf(stateName)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return RestoreJournalEntry(
            restoreId = restoreId,
            mode = mode,
            startedAtMs = startedAtMs,
            state = state,
            preRestoreBackupChecksum = entries["pre_restore_backup_checksum"]?.asStr(),
            error = entries["error"]?.asStr()
        )
    }

    fun write(entry: RestoreJournalEntry) {
        val obj = JsonObj(
            mapOf(
                "restore_id" to JsonStr(entry.restoreId),
                "mode" to JsonStr(entry.mode),
                "started_at_ms" to JsonInt(entry.startedAtMs),
                "state" to JsonStr(entry.state.name),
                "pre_restore_backup_checksum" to
                    (entry.preRestoreBackupChecksum?.let { JsonStr(it) } ?: JsonNull),
                "error" to (entry.error?.let { JsonStr(it) } ?: JsonNull)
            )
        )
        journalFile.parentFile?.mkdirs()
        journalFile.writeText(BackupJson.stringify(obj))
    }

    fun begin(restoreId: String, mode: RestoreMode): RestoreJournalEntry {
        val entry = RestoreJournalEntry(
            restoreId = restoreId,
            mode = mode.name,
            startedAtMs = System.currentTimeMillis(),
            state = RestoreJournalState.STARTED,
            preRestoreBackupChecksum = null,
            error = null
        )
        write(entry)
        return entry
    }

    fun markPreBackupDone(entry: RestoreJournalEntry, preRestoreChecksum: String): RestoreJournalEntry {
        val updated = entry.copy(
            state = RestoreJournalState.PRE_BACKUP_DONE,
            preRestoreBackupChecksum = preRestoreChecksum
        )
        write(updated)
        return updated
    }

    fun markCommitted(entry: RestoreJournalEntry): RestoreJournalEntry {
        val updated = entry.copy(state = RestoreJournalState.COMMITTED)
        write(updated)
        return updated
    }

    fun markRolledBack(entry: RestoreJournalEntry, error: String): RestoreJournalEntry {
        val updated = entry.copy(state = RestoreJournalState.ROLLED_BACK, error = error)
        write(updated)
        return updated
    }

    /**
     * Startup recovery: if a restore was left in a non-terminal state,
     * mark it INTERRUPTED and return it so the UI can report it.
     * Returns null when there is nothing to recover.
     */
    fun recoverOnStartup(): RestoreJournalEntry? {
        val current = readCurrent() ?: return null
        return when (current.state) {
            RestoreJournalState.STARTED, RestoreJournalState.PRE_BACKUP_DONE -> {
                val updated = current.copy(
                    state = RestoreJournalState.INTERRUPTED,
                    error = "Restore was interrupted before it finished. " +
                        "The database was left unchanged by the interrupted restore."
                )
                write(updated)
                updated
            }
            else -> null
        }
    }
}
