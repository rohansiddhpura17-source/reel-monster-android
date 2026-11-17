package com.reelmonster.app.data.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.domain.model.backup.BackupColType
import com.reelmonster.app.domain.model.backup.BackupJson
import com.reelmonster.app.domain.model.backup.BackupTable
import com.reelmonster.app.domain.model.backup.JsonArr
import com.reelmonster.app.domain.model.backup.JsonBool
import com.reelmonster.app.domain.model.backup.JsonDouble
import com.reelmonster.app.domain.model.backup.JsonInt
import com.reelmonster.app.domain.model.backup.JsonNull
import com.reelmonster.app.domain.model.backup.JsonObj
import com.reelmonster.app.domain.model.backup.JsonStr
import com.reelmonster.app.domain.model.backup.JsonValue
import com.reelmonster.app.domain.model.backup.RestorePlanner

/**
 * Phase 15 — Backup table store (proposal §8).
 *
 * The exporter and importer work against this narrow interface, not
 * directly against SQLite, so their logic is testable on plain JVM
 * with an in-memory store. [SqliteBackupTableStore] is the thin
 * Android implementation used by the app; tests use
 * [MapBackupTableStore] (in the test sources).
 */
interface BackupTableStore {
    fun readTable(table: BackupTable): List<Map<String, JsonValue>>
    fun existingRows(table: BackupTable): Map<String, String>
    fun insertRow(table: BackupTable, row: Map<String, JsonValue>)
    fun updateRow(table: BackupTable, row: Map<String, JsonValue>)
    fun deleteAllRows(table: BackupTable)
    fun rowCount(table: BackupTable): Int
    fun <T> transaction(block: () -> T): T
    fun integrityCheck(): Boolean
}

class SqliteBackupTableStore(private val dbHelper: ReelDbHelper) : BackupTableStore {

    override fun readTable(table: BackupTable): List<Map<String, JsonValue>> {
        val db = dbHelper.readableDatabase
        val columns = table.columns.joinToString(", ") { it.name }
        val cursor = db.rawQuery(
            "SELECT $columns FROM ${table.name} ORDER BY ${table.pkColumn} ASC",
            null
        )
        val rows = mutableListOf<Map<String, JsonValue>>()
        cursor.use {
            while (it.moveToNext()) {
                val row = linkedMapOf<String, JsonValue>()
                for ((index, column) in table.columns.withIndex()) {
                    if (it.isNull(index)) {
                        row[column.name] = JsonNull
                    } else {
                        when (column.type) {
                            BackupColType.INT, BackupColType.INT_NULLABLE ->
                                row[column.name] = JsonInt(it.getLong(index))
                            BackupColType.TEXT, BackupColType.TEXT_NULLABLE ->
                                row[column.name] = JsonStr(it.getString(index) ?: "")
                        }
                    }
                }
                rows.add(row)
            }
        }
        return rows
    }

    override fun existingRows(table: BackupTable): Map<String, String> {
        return readTable(table).associate { row ->
            val pk = RestorePlanner.pkString(table, row) ?: ""
            pk to BackupJson.stringify(JsonObj(row))
        }
    }

    override fun insertRow(table: BackupTable, row: Map<String, JsonValue>) {
        val db = dbHelper.writableDatabase
        val values = rowToContentValues(table, row)
        val inserted = db.insertOrThrow(table.name, null, values)
        check(inserted != -1L) { "Insert into '${table.name}' failed." }
    }

    override fun updateRow(table: BackupTable, row: Map<String, JsonValue>) {
        val db = dbHelper.writableDatabase
        val values = rowToContentValues(table, row)
        val pk = RestorePlanner.pkString(table, row)
            ?: throw IllegalArgumentException("Row for '${table.name}' has no primary key.")
        val updated = db.update(
            table.name, values, "${table.pkColumn} = ?", arrayOf(pk)
        )
        check(updated == 1) {
            "Update of '${table.name}' pk=$pk affected $updated rows (expected 1)."
        }
    }

    override fun deleteAllRows(table: BackupTable) {
        dbHelper.writableDatabase.delete(table.name, null, null)
    }

    override fun rowCount(table: BackupTable): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${table.name}", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val db: SQLiteDatabase = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun integrityCheck(): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("PRAGMA integrity_check", null)
        cursor.use {
            if (!it.moveToFirst()) return false
            if (it.getString(0) != "ok") return false
        }
        val fkCursor = db.rawQuery("PRAGMA foreign_key_check", null)
        fkCursor.use {
            if (it.count != 0) return false
        }
        return true
    }

    private fun rowToContentValues(
        table: BackupTable,
        row: Map<String, JsonValue>
    ): ContentValues {
        val values = ContentValues()
        for (column in table.columns) {
            val value = row[column.name] ?: continue
            when (value) {
                is JsonInt -> values.put(column.name, value.value)
                is JsonDouble -> values.put(column.name, value.value)
                is JsonStr -> values.put(column.name, value.value)
                is JsonBool -> values.put(column.name, if (value.value) 1 else 0)
                JsonNull -> values.putNull(column.name)
                // Nested values can never appear in a validated backup row;
                // reject them loudly instead of silently dropping data.
                is JsonArr, is JsonObj -> throw IllegalArgumentException(
                    "Column ${column.name} in ${table.name} must be a scalar, not a nested value."
                )
            }
        }
        return values
    }
}
