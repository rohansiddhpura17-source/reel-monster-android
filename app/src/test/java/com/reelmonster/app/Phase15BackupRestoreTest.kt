package com.reelmonster.app

import com.reelmonster.app.data.backup.BackupExporter
import com.reelmonster.app.data.backup.BackupImporter
import com.reelmonster.app.data.backup.BackupTableStore
import com.reelmonster.app.data.backup.ImportResult
import com.reelmonster.app.domain.model.backup.BackupChecksum
import com.reelmonster.app.domain.model.backup.BackupFormat
import com.reelmonster.app.domain.model.backup.BackupJson
import com.reelmonster.app.domain.model.backup.BackupSchema
import com.reelmonster.app.domain.model.backup.BackupTable
import com.reelmonster.app.domain.model.backup.BackupValidation
import com.reelmonster.app.domain.model.backup.BackupValidator
import com.reelmonster.app.domain.model.backup.ConflictPolicy
import com.reelmonster.app.domain.model.backup.JsonInt
import com.reelmonster.app.domain.model.backup.JsonNull
import com.reelmonster.app.domain.model.backup.JsonObj
import com.reelmonster.app.domain.model.backup.JsonStr
import com.reelmonster.app.domain.model.backup.JsonValue
import com.reelmonster.app.domain.model.backup.RestoreJournal
import com.reelmonster.app.domain.model.backup.RestoreJournalState
import com.reelmonster.app.domain.model.backup.RestoreMode
import com.reelmonster.app.domain.model.backup.RestorePlanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 — Backup/restore tests (proposal §13, criteria 29–39).
 *
 * Pure JUnit: the exporter/importer run against an in-memory
 * [MapBackupTableStore], so no Robolectric or device is needed.
 * The thin SQLite store is covered by the Mac Gradle build and by
 * device validation.
 */
class Phase15BackupRestoreTest {

    // ---- In-memory store double ----

    class MapBackupTableStore : BackupTableStore {
        val data = linkedMapOf<String, LinkedHashMap<String, Map<String, JsonValue>>>()
        var integrityOk = true
        var failOnInsertTable: String? = null

        init {
            for (table in BackupSchema.TABLES) data[table.name] = LinkedHashMap()
        }

        override fun readTable(table: BackupTable): List<Map<String, JsonValue>> =
            data[table.name]!!.values.toList()

        override fun existingRows(table: BackupTable): Map<String, String> =
            data[table.name]!!.entries.associate { (pk, row) ->
                pk to BackupJson.stringify(JsonObj(row))
            }

        override fun insertRow(table: BackupTable, row: Map<String, JsonValue>) {
            if (failOnInsertTable == table.name) throw IllegalStateException("Injected failure on ${table.name}")
            val pk = RestorePlanner.pkString(table, row) ?: throw IllegalArgumentException("No PK")
            check(!data[table.name]!!.containsKey(pk)) { "Duplicate PK $pk in ${table.name}" }
            data[table.name]!![pk] = row
        }

        override fun updateRow(table: BackupTable, row: Map<String, JsonValue>) {
            val pk = RestorePlanner.pkString(table, row) ?: throw IllegalArgumentException("No PK")
            check(data[table.name]!!.containsKey(pk)) { "Missing PK $pk in ${table.name}" }
            data[table.name]!![pk] = row
        }

        override fun deleteAllRows(table: BackupTable) {
            data[table.name]!!.clear()
        }

        override fun rowCount(table: BackupTable): Int = data[table.name]!!.size

        override fun <T> transaction(block: () -> T): T {
            val snapshot = data.mapValues { (_, rows) -> LinkedHashMap(rows) }
            try {
                return block()
            } catch (e: Exception) {
                data.clear()
                for ((name, rows) in snapshot) data[name] = rows
                throw e
            }
        }

        override fun integrityCheck(): Boolean = integrityOk
    }

    // ---- Sample data ----

    private fun row(vararg pairs: Pair<String, JsonValue>): Map<String, JsonValue> = mapOf(*pairs)

    private fun sampleTables(): Map<String, List<Map<String, JsonValue>>> {
        val tables = linkedMapOf<String, List<Map<String, JsonValue>>>()
        for (table in BackupSchema.TABLES) tables[table.name] = emptyList()
        tables["daily_goals"] = listOf(
            row(
                "id" to JsonInt(1), "goal_type" to JsonStr("REEL_COUNT"),
                "platform_scope" to JsonStr("COMBINED"), "target_value" to JsonInt(30),
                "is_enabled" to JsonInt(1), "created_at" to JsonInt(1000),
                "updated_at" to JsonInt(1000)
            )
        )
        tables["daily_aggregates"] = listOf(
            row(
                "date_key" to JsonStr("2026-11-01"), "total_reels" to JsonInt(10),
                "ig_count" to JsonInt(6), "yt_count" to JsonInt(4)
            )
        )
        tables["reel_events"] = listOf(
            row(
                "id" to JsonInt(1), "timestamp" to JsonInt(1_700_000_000_000),
                "target_app" to JsonStr("INSTAGRAM"),
                "tracking_mode" to JsonStr("LEVEL_1_MANUAL"),
                "session_date" to JsonStr("2026-11-01")
            ),
            row(
                "id" to JsonInt(2), "timestamp" to JsonInt(1_700_000_060_000),
                "target_app" to JsonStr("YOUTUBE"),
                "tracking_mode" to JsonStr("LEVEL_2_AUTOMATIC"),
                "session_date" to JsonStr("2026-11-01")
            )
        )
        tables["viewing_sessions"] = listOf(
            row(
                "id" to JsonInt(1), "target_app" to JsonStr("INSTAGRAM"),
                "fingerprint" to JsonNull, "start_timestamp" to JsonInt(1000),
                "end_timestamp" to JsonInt(61_000), "duration_ms" to JsonInt(60_000),
                "completion_status" to JsonStr("COMPLETED"),
                "session_date" to JsonStr("2026-11-01")
            )
        )
        tables["daily_goal_history"] = listOf(
            row(
                "id" to JsonInt(1), "event_id" to JsonStr("evt-1"),
                "goal_id" to JsonInt(1), "goal_type" to JsonStr("REEL_COUNT"),
                "platform_scope" to JsonStr("COMBINED"), "event_type" to JsonStr("CREATED"),
                "previous_value" to JsonNull, "new_value" to JsonStr("30"),
                "effective_timestamp" to JsonInt(1000), "date_key" to JsonStr("2026-11-01"),
                "timezone_id" to JsonStr("Asia/Kolkata"), "created_at" to JsonInt(1000)
            )
        )
        tables["user_progress"] = listOf(
            row(
                "id" to JsonInt(1), "total_xp" to JsonInt(250),
                "current_level" to JsonInt(3), "current_streak" to JsonInt(2),
                "longest_streak" to JsonInt(5),
                "last_qualified_date" to JsonStr("2026-11-01"),
                "updated_at" to JsonInt(1000)
            )
        )
        tables["system_metadata"] = listOf(
            row(
                "meta_key" to JsonStr("test_key"), "meta_value" to JsonStr("v"),
                "updated_at" to JsonInt(1000)
            )
        )
        return tables
    }

    /** Builds a complete backup JSON string with a correct checksum. */
    private fun buildBackupJson(
        tables: Map<String, List<Map<String, JsonValue>>> = sampleTables(),
        schemaVersion: Long = 6,
        countsOverride: Map<String, Int>? = null
    ): String {
        val counts = countsOverride ?: tables.mapValues { (_, rows) -> rows.size }
        val manifest = JsonObj(
            mapOf(
                "schema_version" to JsonInt(schemaVersion),
                "exported_at_ms" to JsonInt(1_700_000_000_000),
                "exported_timezone" to JsonStr("Asia/Kolkata"),
                "record_counts" to JsonObj(
                    counts.entries.associate { (k, v) -> k to JsonInt(v.toLong()) }
                ),
                "checksum_sha256" to JsonStr(BackupChecksum.checksumOfTables(tables))
            )
        )
        val root = JsonObj(
            mapOf(
                "format_id" to JsonStr(BackupFormat.FORMAT_ID),
                "format_version" to JsonInt(1),
                "manifest" to manifest,
                "tables" to BackupChecksum.tablesToJson(tables)
            )
        )
        return BackupJson.stringify(root)
    }

    private fun validPayload() =
        (BackupValidator.validate(buildBackupJson()) as BackupValidation.Valid).payload

    private fun seed(store: MapBackupTableStore) {
        for ((name, rows) in sampleTables()) {
            val table = BackupSchema.tableByName(name)!!
            for (row in rows) store.insertRow(table, row)
        }
    }

    private fun errorsOf(json: String): List<String> {
        val result = BackupValidator.validate(json)
        assertTrue("Expected Invalid, got $result", result is BackupValidation.Invalid)
        return (result as BackupValidation.Invalid).errors
    }

    // ---- JSON ----

    @Test
    fun testJsonCanonicalOrdering() {
        val value = JsonObj(
            mapOf("b" to JsonInt(2), "a" to JsonObj(mapOf("z" to JsonInt(1), "y" to JsonInt(2))))
        )
        assertEquals("""{"a":{"y":2,"z":1},"b":2}""", BackupJson.stringify(value))
    }

    @Test
    fun testJsonParseRoundTrip() {
        val original = """{"a":[1,"x",true,null,1.5],"b":{"c":"d"}}"""
        val parsed = BackupJson.parse(original)
        assertEquals(original, parsed?.let { BackupJson.stringify(it) })
    }

    @Test
    fun testJsonRejectsDuplicateKeys() {
        assertNull(BackupJson.parse("""{"a":1,"a":2}"""))
    }

    @Test
    fun testJsonRejectsTrailingData() {
        assertNull(BackupJson.parse("""{"a":1} garbage"""))
    }

    @Test
    fun testChecksumDeterministic() {
        val tables = sampleTables()
        assertEquals(
            BackupChecksum.checksumOfTables(tables),
            BackupChecksum.checksumOfTables(tables)
        )
        val changed = tables.toMutableMap()
        changed["reel_events"] = tables["reel_events"]!!.map { row ->
            row + ("timestamp" to JsonInt(1_700_000_000_001))
        }
        assertTrue(BackupChecksum.checksumOfTables(changed) != BackupChecksum.checksumOfTables(tables))
    }

    // ---- Export / validate ----

    @Test
    fun testExporterProducesValidBackup() {
        val store = MapBackupTableStore()
        seed(store)
        val json = BackupExporter(store).export(
            exportedAtMs = 1_700_000_000_000, timezoneId = "Asia/Kolkata"
        )
        val result = BackupValidator.validate(json)
        assertTrue("Expected Valid, got $result", result is BackupValidation.Valid)
        val payload = (result as BackupValidation.Valid).payload
        assertEquals(6, payload.manifest.schemaVersion)
        assertEquals(2, payload.manifest.recordCounts["reel_events"])
        assertEquals(1, payload.manifest.recordCounts["daily_goals"])
    }

    @Test
    fun testValidatorRejectsTamperedChecksum() {
        val tables = sampleTables()
        val tampered = tables.toMutableMap()
        tampered["reel_events"] = listOf(
            tables["reel_events"]!![0] + ("timestamp" to JsonInt(1_700_000_000_001)),
            tables["reel_events"]!![1]
        )
        // Rebuild with the ORIGINAL checksum (simulating file tampering).
        val originalChecksum = BackupChecksum.checksumOfTables(tables)
        val manifest = JsonObj(
            mapOf(
                "schema_version" to JsonInt(6),
                "exported_at_ms" to JsonInt(1_700_000_000_000),
                "exported_timezone" to JsonStr("Asia/Kolkata"),
                "record_counts" to JsonObj(
                    tampered.mapValues { (_, rows) -> JsonInt(rows.size.toLong()) }
                ),
                "checksum_sha256" to JsonStr(originalChecksum)
            )
        )
        val root = JsonObj(
            mapOf(
                "format_id" to JsonStr(BackupFormat.FORMAT_ID),
                "format_version" to JsonInt(1),
                "manifest" to manifest,
                "tables" to BackupChecksum.tablesToJson(tampered)
            )
        )
        val errors = errorsOf(BackupJson.stringify(root))
        assertTrue(errors.any { it.contains("Checksum mismatch") })
    }

    @Test
    fun testValidatorRejectsWrongFormat() {
        val json = buildBackupJson().replace(
            "\"reel-monster-backup\"", "\"some-other-backup\""
        )
        val errors = errorsOf(json)
        assertTrue(errors.any { it.contains("Unknown backup format") })
    }

    @Test
    fun testValidatorRejectsNewerSchema() {
        val errors = errorsOf(buildBackupJson(schemaVersion = 7))
        assertTrue(errors.any { it.contains("UPGRADE_REQUIRED") })
    }

    @Test
    fun testValidatorRejectsOlderSchema() {
        val errors = errorsOf(buildBackupJson(schemaVersion = 5))
        assertTrue(errors.any { it.contains("older than this app's schema") })
    }

    @Test
    fun testValidatorRejectsCountMismatch() {
        val errors = errorsOf(
            buildBackupJson(countsOverride = sampleTables().mapValues { (_, rows) -> rows.size }
                .toMutableMap().also { it["reel_events"] = 99 })
        )
        assertTrue(errors.any { it.contains("Record count mismatch") })
    }

    @Test
    fun testValidatorRejectsDuplicatePrimaryKey() {
        val tables = sampleTables().toMutableMap()
        tables["reel_events"] = tables["reel_events"]!! + tables["reel_events"]!![0]
        val errors = errorsOf(buildBackupJson(tables))
        assertTrue(errors.any { it.contains("duplicates primary key") })
    }

    @Test
    fun testValidatorRejectsBadEnum() {
        val tables = sampleTables().toMutableMap()
        tables["reel_events"] = listOf(
            tables["reel_events"]!![0] + ("target_app" to JsonStr("TIKTOK")),
            tables["reel_events"]!![1]
        )
        val errors = errorsOf(buildBackupJson(tables))
        assertTrue(errors.any { it.contains("unknown value 'TIKTOK'") })
    }

    @Test
    fun testValidatorRejectsForeignKeyViolation() {
        val tables = sampleTables().toMutableMap()
        tables["daily_goal_history"] = listOf(
            tables["daily_goal_history"]!![0] + ("goal_id" to JsonInt(99))
        )
        val errors = errorsOf(buildBackupJson(tables))
        assertTrue(errors.any { it.contains("references missing goal_id 99") })
    }

    @Test
    fun testValidatorRejectsInvalidDateKey() {
        val tables = sampleTables().toMutableMap()
        tables["daily_aggregates"] = listOf(
            tables["daily_aggregates"]!![0] + ("date_key" to JsonStr("2026-13-40"))
        )
        val errors = errorsOf(buildBackupJson(tables))
        assertTrue(errors.any { it.contains("not a valid yyyy-MM-dd date") })
    }

    // ---- Planner ----

    @Test
    fun testPlannerMergeNewIdenticalAndConflict() {
        val payload = validPayload()
        val existing = mapOf(
            "reel_events" to mapOf(
                "1" to BackupJson.stringify(JsonObj(sampleTables()["reel_events"]!![0])),
                "2" to BackupJson.stringify(
                    JsonObj(sampleTables()["reel_events"]!![1] + ("timestamp" to JsonInt(1)))
                )
            )
        )
        val plan = RestorePlanner.plan(
            payload, existing, RestoreMode.MERGE, ConflictPolicy.KEEP_EXISTING
        )
        val eventsPlan = plan.tables.first { it.table == "reel_events" }
        assertEquals(0, eventsPlan.toInsert.size)
        assertEquals(1, eventsPlan.alreadyPresent)
        assertEquals(1, eventsPlan.conflicts.size)
        assertEquals(0, eventsPlan.toUpdate.size)
        assertEquals(1, plan.totalConflicts)
        assertFalse(plan.cancelledByConflict)
    }

    @Test
    fun testPlannerUseBackupUpdatesConflicts() {
        val payload = validPayload()
        val existing = mapOf(
            "reel_events" to mapOf(
                "1" to BackupJson.stringify(
                    JsonObj(sampleTables()["reel_events"]!![0] + ("timestamp" to JsonInt(1)))
                )
            )
        )
        val plan = RestorePlanner.plan(
            payload, existing, RestoreMode.MERGE, ConflictPolicy.USE_BACKUP
        )
        val eventsPlan = plan.tables.first { it.table == "reel_events" }
        assertEquals(1, eventsPlan.toUpdate.size)
        assertEquals(1, eventsPlan.toInsert.size) // id 2 is new
    }

    @Test
    fun testPlannerCancelAbortsOnConflict() {
        val payload = validPayload()
        val existing = mapOf(
            "reel_events" to mapOf(
                "1" to BackupJson.stringify(
                    JsonObj(sampleTables()["reel_events"]!![0] + ("timestamp" to JsonInt(1)))
                )
            )
        )
        val plan = RestorePlanner.plan(
            payload, existing, RestoreMode.MERGE, ConflictPolicy.CANCEL
        )
        assertTrue(plan.cancelledByConflict)
    }

    // ---- Importer ----

    @Test
    fun testMergeRoundTripAndIdempotency() {
        val source = MapBackupTableStore()
        seed(source)
        val json = BackupExporter(source).export(
            exportedAtMs = 1_700_000_000_000, timezoneId = "Asia/Kolkata"
        )
        val target = MapBackupTableStore()
        val journalFile = File.createTempFile("journal", ".json").also { it.delete() }
        val importer = BackupImporter(target, RestoreJournal(journalFile))
        val (validation, plan) = importer.validateAndPlan(json, RestoreMode.MERGE, ConflictPolicy.KEEP_EXISTING)
        assertTrue(validation is BackupValidation.Valid)
        val result = importer.importMerge((validation as BackupValidation.Valid).payload, plan!!)
        assertTrue(result is ImportResult.Success)
        // Sample data has 8 rows across all tables.
        assertEquals(8, (result as ImportResult.Success).inserted)
        // Re-importing the same backup: everything already present.
        val (validation2, plan2) = importer.validateAndPlan(json, RestoreMode.MERGE, ConflictPolicy.KEEP_EXISTING)
        val result2 = importer.importMerge((validation2 as BackupValidation.Valid).payload, plan2!!)
        assertTrue(result2 is ImportResult.Success)
        assertEquals(0, (result2 as ImportResult.Success).inserted)
        assertEquals(8, result2.alreadyPresent)
    }

    @Test
    fun testFullRestoreReplacesAndRequiresPreRestoreBackup() {
        val source = MapBackupTableStore()
        seed(source)
        val json = BackupExporter(source).export(
            exportedAtMs = 1_700_000_000_000, timezoneId = "Asia/Kolkata"
        )
        val target = MapBackupTableStore()
        // Target has different data: one reel event with different content.
        target.insertRow(
            BackupSchema.tableByName("reel_events")!!,
            sampleTables()["reel_events"]!![0] + ("timestamp" to JsonInt(5))
        )
        val journalFile = File.createTempFile("journal", ".json").also { it.delete() }
        val importer = BackupImporter(target, RestoreJournal(journalFile))

        // Refused without a valid pre-restore backup.
        val (validation, plan) = importer.validateAndPlan(json, RestoreMode.FULL, ConflictPolicy.KEEP_EXISTING)
        val refused = importer.importFull(
            (validation as BackupValidation.Valid).payload, plan!!, "not json"
        )
        assertTrue(refused is ImportResult.Failure)
        assertEquals(1, target.rowCount(BackupSchema.tableByName("reel_events")!!))

        // With a valid pre-restore backup, the old row is replaced.
        val preBackup = BackupExporter(target).export(
            exportedAtMs = 1_700_000_000_000, timezoneId = "Asia/Kolkata"
        )
        val result = importer.importFull(
            (validation as BackupValidation.Valid).payload, plan, preBackup
        )
        assertTrue(result is ImportResult.Success)
        assertEquals(1, (result as ImportResult.Success).deleted)
        assertEquals(2, target.rowCount(BackupSchema.tableByName("reel_events")!!))
    }

    @Test
    fun testImporterRollsBackOnFailure() {
        val source = MapBackupTableStore()
        seed(source)
        val json = BackupExporter(source).export(
            exportedAtMs = 1_700_000_000_000, timezoneId = "Asia/Kolkata"
        )
        val target = MapBackupTableStore()
        target.failOnInsertTable = "viewing_sessions"
        val journalFile = File.createTempFile("journal", ".json").also { it.delete() }
        val importer = BackupImporter(target, RestoreJournal(journalFile))
        val (validation, plan) = importer.validateAndPlan(json, RestoreMode.MERGE, ConflictPolicy.KEEP_EXISTING)
        val result = importer.importMerge((validation as BackupValidation.Valid).payload, plan!!)
        assertTrue(result is ImportResult.Failure)
        // Nothing was partially applied: the transaction rolled back.
        assertEquals(0, target.rowCount(BackupSchema.tableByName("reel_events")!!))
        assertEquals(0, target.rowCount(BackupSchema.tableByName("daily_goals")!!))
        val journal = RestoreJournal(journalFile).readCurrent()
        assertEquals(RestoreJournalState.ROLLED_BACK, journal!!.state)
    }

    // ---- Journal ----

    @Test
    fun testJournalInterruptedRecovery() {
        val file = File.createTempFile("journal", ".json").also { it.delete() }
        val journal = RestoreJournal(file)
        val entry = journal.begin("restore-1", RestoreMode.FULL)
        assertEquals(RestoreJournalState.STARTED, entry.state)
        val recovered = journal.recoverOnStartup()
        assertEquals(RestoreJournalState.INTERRUPTED, recovered!!.state)
        assertTrue(recovered.error!!.contains("interrupted"))
        // Second recovery finds nothing new.
        assertNull(journal.recoverOnStartup())
    }

    @Test
    fun testJournalCommittedIsNotRecovered() {
        val file = File.createTempFile("journal", ".json").also { it.delete() }
        val journal = RestoreJournal(file)
        val entry = journal.begin("restore-1", RestoreMode.MERGE)
        journal.markCommitted(entry)
        assertNull(journal.recoverOnStartup())
    }
}
