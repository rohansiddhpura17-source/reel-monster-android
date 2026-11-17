package com.reelmonster.app.domain.model.backup

import java.security.MessageDigest

/**
 * Phase 15 — Backup manifest, payload, and checksum (proposal §8).
 *
 * File layout (canonical JSON, keys sorted):
 * ```
 * {
 *   "format_id": "reel-monster-backup",
 *   "format_version": 1,
 *   "manifest": {
 *     "schema_version": 6,
 *     "exported_at_ms": 1792000000000,
 *     "exported_timezone": "Asia/Kolkata",
 *     "record_counts": { "reel_events": 123, ... },
 *     "checksum_sha256": "<hex of the canonical tables JSON>"
 *   },
 *   "tables": { "<table>": [ { "<column>": value, ... }, ... ], ... }
 * }
 * ```
 * The checksum covers only the canonical `tables` value, so the
 * manifest can be read and verified without circularity. Row order
 * inside a table is part of the checksum: the exporter writes rows
 * ordered by primary key, and the validator requires the checksum to
 * match the file exactly as read.
 */
object BackupFormat {
    const val FORMAT_ID = "reel-monster-backup"
    const val FORMAT_VERSION = 1
    const val MAX_BACKUP_BYTES = 50 * 1024 * 1024L
    const val MAX_ROWS_PER_TABLE = 200_000
}

data class BackupManifest(
    val schemaVersion: Int,
    val exportedAtMs: Long,
    val exportedTimezone: String,
    val recordCounts: Map<String, Int>,
    val checksumSha256: String
)

data class BackupPayload(
    val manifest: BackupManifest,
    /** Table name -> rows, each row a column-name -> JSON value map. */
    val tables: Map<String, List<Map<String, JsonValue>>>
)

object BackupChecksum {
    /** SHA-256 hex of the canonical JSON of the tables value. */
    fun sha256Hex(canonicalTablesJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonicalTablesJson.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun checksumOfTables(tables: Map<String, List<Map<String, JsonValue>>>): String {
        return sha256Hex(BackupJson.stringify(tablesToJson(tables)))
    }

    fun tablesToJson(
        tables: Map<String, List<Map<String, JsonValue>>>
    ): JsonObj {
        val entries = tables.entries.associate { (table, rows) ->
            table to JsonArr(rows.map { row -> JsonObj(row) })
        }
        return JsonObj(entries)
    }

    fun tablesFromJson(value: JsonValue): Map<String, List<Map<String, JsonValue>>>? {
        val obj = value.asObj() ?: return null
        val result = linkedMapOf<String, List<Map<String, JsonValue>>>()
        for ((table, rowsValue) in obj.entries) {
            val rowsArr = rowsValue.asArr() ?: return null
            val rows = mutableListOf<Map<String, JsonValue>>()
            for (rowValue in rowsArr.items) {
                val rowObj = rowValue.asObj() ?: return null
                rows.add(rowObj.entries)
            }
            result[table] = rows
        }
        return result
    }
}
