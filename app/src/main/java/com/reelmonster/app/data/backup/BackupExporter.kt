package com.reelmonster.app.data.backup

import com.reelmonster.app.domain.model.backup.BackupChecksum
import com.reelmonster.app.domain.model.backup.BackupFormat
import com.reelmonster.app.domain.model.backup.BackupJson
import com.reelmonster.app.domain.model.backup.BackupSchema
import com.reelmonster.app.domain.model.backup.JsonArr
import com.reelmonster.app.domain.model.backup.JsonInt
import com.reelmonster.app.domain.model.backup.JsonObj
import com.reelmonster.app.domain.model.backup.JsonStr
import com.reelmonster.app.domain.model.backup.JsonValue
import java.time.ZoneId

/**
 * Phase 15 — Offline backup exporter (proposal §8).
 *
 * Exports every user-data table in schema order, rows ordered by
 * primary key, into one canonical JSON document with a manifest
 * (schema version, export timestamp/timezone, per-table record
 * counts, SHA-256 over the canonical tables JSON). No network, no
 * account, no cloud: the caller hands the returned string to the
 * Android Storage Access Framework document the user chose.
 */
class BackupExporter(private val store: BackupTableStore) {

    /** Returns the complete backup file contents as canonical JSON. */
    fun export(
        exportedAtMs: Long = System.currentTimeMillis(),
        timezoneId: String = ZoneId.systemDefault().id
    ): String {
        val tables = linkedMapOf<String, List<Map<String, JsonValue>>>()
        val counts = linkedMapOf<String, Int>()
        for (table in BackupSchema.TABLES) {
            val rows = store.readTable(table)
            tables[table.name] = rows
            counts[table.name] = rows.size
        }
        val checksum = BackupChecksum.checksumOfTables(tables)
        val manifest = JsonObj(
            mapOf(
                "schema_version" to JsonInt(BackupSchema.CURRENT_SCHEMA_VERSION.toLong()),
                "exported_at_ms" to JsonInt(exportedAtMs),
                "exported_timezone" to JsonStr(timezoneId),
                "record_counts" to JsonObj(
                    counts.entries.associate { (k, v) -> k to JsonInt(v.toLong()) }
                ),
                "checksum_sha256" to JsonStr(checksum)
            )
        )
        val root = JsonObj(
            mapOf(
                "format_id" to JsonStr(BackupFormat.FORMAT_ID),
                "format_version" to JsonInt(BackupFormat.FORMAT_VERSION.toLong()),
                "manifest" to manifest,
                "tables" to BackupChecksum.tablesToJson(tables)
            )
        )
        return BackupJson.stringify(root)
    }
}
