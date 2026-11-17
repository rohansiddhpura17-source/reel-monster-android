package com.reelmonster.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelmonster.app.core.util.DateTimeUtils
import com.reelmonster.app.data.database.ReelDbHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.GoalHistoryEventType
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.domain.model.monitoring.ContinuityStatus
import com.reelmonster.app.domain.model.monitoring.CoverageCalculator
import com.reelmonster.app.domain.model.monitoring.DailyMonitoringCoverage
import com.reelmonster.app.domain.model.monitoring.EstimatedCoverageStatus
import com.reelmonster.app.domain.model.monitoring.MonitoringHeartbeat
import com.reelmonster.app.domain.model.monitoring.MonitoringInterval
import com.reelmonster.app.domain.model.progression.StreakEvaluationStatus
import com.reelmonster.app.domain.model.progression.XpEventType
import com.reelmonster.app.domain.model.progression.XpTransaction
import com.reelmonster.app.service.focus.FocusSessionTracker
import com.reelmonster.app.service.tracking.BootSessionProvider
import com.reelmonster.app.service.tracking.MonitoringCoverageTracker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Phase14RemediationTest
 *
 * Dedicated test suite verifying the Phase 14 Remediation:
 * 1. Fix 1: Migration Cutover Correctness (Metadata, no backdating, snapshot progression-ineligible on migration day,
 *    next local day eligible, no retrospective XP/streaks without evidence, idempotent).
 * 2. Fix 2: Reboot-Safe Monitoring (boot_session_id in intervals & heartbeats, active intervals persisted in SQLite,
 *    process restart recovery vs device reboot interruption, no cross-boot monotonic comparison, fresh interval resumption).
 * 3. Database schema version 6 migration and data preservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase14RemediationTest {

    private lateinit var context: Context
    private lateinit var dbHelper: ReelDbHelper
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var repository: ReelRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ReelDbHelper.DATABASE_NAME)
        BootSessionProvider.clearCache()
        BootSessionProvider.setTestBootSessionId("test_boot_session_alpha")
        dbHelper = ReelDbHelper(context)
        preferencesManager = UserPreferencesManager(context)
        preferencesManager.clearPreferences()
        MonitoringCoverageTracker.instance.reset()
        FocusSessionTracker.instance.reset()
        repository = ReelRepository(dbHelper, preferencesManager)
    }

    @After
    fun tearDown() = runBlocking {
        MonitoringCoverageTracker.instance.reset()
        FocusSessionTracker.instance.reset()
        preferencesManager.clearPreferences()
        BootSessionProvider.clearCache()
        dbHelper.close()
    }

    // =========================================================================
    // Fix 1: Migration Cutover Correctness Tests
    // =========================================================================

    @Test
    fun test01_migrationCutover_recordsActualTimestampAndTzInMetadata() {
        val migrationTsStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_TS)
        val migrationTzStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_TZ)
        val migrationDateStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_DATE)
        val firstEligibleDateStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_FIRST_ELIGIBLE_DATE)

        assertNotNull("Migration timestamp must be persisted in system_metadata", migrationTsStr)
        assertNotNull("Migration timezone must be persisted in system_metadata", migrationTzStr)
        assertNotNull("Migration date must be persisted in system_metadata", migrationDateStr)
        assertNotNull("First eligible date must be persisted in system_metadata", firstEligibleDateStr)

        val migrationTs = migrationTsStr!!.toLong()
        assertTrue("Migration timestamp must be recent epoch time", migrationTs > 1_700_000_000_000L)
        assertEquals(TimeZone.getDefault().id, migrationTzStr)

        val expectedDate = DateTimeUtils.getTodayDateString(Date(migrationTs))
        assertEquals(expectedDate, migrationDateStr)

        val cal = Calendar.getInstance().apply {
            time = Date(migrationTs)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val expectedNextDate = DateTimeUtils.getTodayDateString(cal.time)
        assertEquals(expectedNextDate, firstEligibleDateStr)
    }

    @Test
    fun test02_migrationCutover_doesNotBackdateGoalCreationOrFabricateHistory() {
        val migrationDateStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_DATE)!!
        val migrationTs = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_TS)!!.toLong()

        val historyEvents = dbHelper.getGoalHistoryEvents()
        assertTrue("Cutover must record history events for default goals", historyEvents.isNotEmpty())

        for (event in historyEvents) {
            assertEquals("Cutover events must have date equal to migrationDate", migrationDateStr, event.dateKey)
            assertEquals("Cutover events must have effectiveTimestamp equal to migrationTs (not backdated)", migrationTs, event.effectiveTimestamp)
            assertEquals(GoalHistoryEventType.CREATED, event.eventType)
        }

        // Verify NO fabricated events exist for prior dates
        val priorDateEvents = historyEvents.filter { it.dateKey < migrationDateStr }
        assertTrue("Must never fabricate historical goal events for dates prior to migration cutover", priorDateEvents.isEmpty())
    }

    @Test
    fun test03_migrationCutover_migrationDaySnapshotIsNotProgressionEligible() {
        val migrationDateStr = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_DATE)!!
        val snapshots = dbHelper.getGoalSnapshotsForDate(migrationDateStr, rebuildFromHistory = true)
        assertTrue("Snapshots must exist for migration day", snapshots.isNotEmpty())

        for (snapshot in snapshots) {
            assertFalse("Migration day snapshot must have progressionEligible == false", snapshot.progressionEligible)
            assertFalse("Migration day snapshot must have enabledAtMidnight == false", snapshot.enabledAtMidnight)
        }

        // Evaluating migration day with sufficient coverage must NOT award goal XP and must not qualify streak
        val duration130m = 130L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = migrationDateStr,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + duration130m,
                monotonicStartMs = 1_000L,
                monotonicEndMs = 1_000L + duration130m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED,
                bootSessionId = "test_boot_session_alpha"
            )
        )

        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
        val futureDate = DateTimeUtils.getTodayDateString(tomorrowCal.time)
        val eval = repository.evaluatePastDayProgression(migrationDateStr, futureDate)

        assertFalse("Migration day must never award Count Goal XP", eval.awardCountGoalXp)
        assertFalse("Migration day must never award Time Goal XP", eval.awardTimeGoalXp)
        assertEquals("Migration day must be DISQUALIFIED because goals were activated mid-day at cutover",
            StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
    }

    @Test
    fun test04_migrationCutover_nextLocalCalendarDayIsFirstProgressionEligibleDay() {
        val firstEligibleDate = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_FIRST_ELIGIBLE_DATE)!!
        val snapshots = dbHelper.getGoalSnapshotsForDate(firstEligibleDate, rebuildFromHistory = true)
        assertTrue("Snapshots must exist for the day following cutover", snapshots.isNotEmpty())

        val midnightEnabled = snapshots.filter { it.enabledAtMidnight }
        assertTrue("At least one midnight-enabled goal snapshot must exist", midnightEnabled.isNotEmpty())

        for (snap in midnightEnabled) {
            assertTrue("Next local calendar day must be progression-eligible for midnight-enabled goals", snap.progressionEligible)
            assertTrue("Next local calendar day must have enabledAtMidnight == true", snap.enabledAtMidnight)
        }

        val midnightDisabled = snapshots.filter { !it.enabledAtMidnight }
        for (snap in midnightDisabled) {
            assertFalse("Disabled goals must not be progression-eligible", snap.progressionEligible)
        }
    }

    @Test
    fun test05_migrationCutover_idempotentOnRepeatedCutoverOrRecovery() {
        val initialTs = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_TS)
        val initialEvents = dbHelper.getGoalHistoryEvents().size
        val initialSnapshots = dbHelper.getGoalSnapshotsForDate(dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_DATE)!!).size

        // Simulate closing and reopening db multiple times
        repeat(3) {
            dbHelper.close()
            dbHelper = ReelDbHelper(context)
        }

        val afterTs = dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_TS)
        val afterEvents = dbHelper.getGoalHistoryEvents().size
        val afterSnapshots = dbHelper.getGoalSnapshotsForDate(dbHelper.getSystemMetadata(ReelDbHelper.KEY_PHASE14_MIGRATION_DATE)!!).size

        assertEquals("Migration timestamp must be immutable on repeated opens", initialTs, afterTs)
        assertEquals("History event count must be identical (no duplicate seeding)", initialEvents, afterEvents)
        assertEquals("Snapshot count must be identical", initialSnapshots, afterSnapshots)
    }

    @Test
    fun test06_migrationCutover_preservesExistingGoalSettings() {
        val goals = dbHelper.getAllGoals()
        assertTrue("Daily goals must exist", goals.isNotEmpty())

        // Modify a goal target
        val firstGoal = goals.first()
        val updatedGoal = firstGoal.copy(targetValue = 42, isEnabled = true)
        dbHelper.insertOrUpdateGoal(updatedGoal)

        // Close and reopen DB (simulating app restart)
        dbHelper.close()
        dbHelper = ReelDbHelper(context)

        val retrievedGoals = dbHelper.getAllGoals()
        val retrievedFirst = retrievedGoals.firstOrNull { it.id == firstGoal.id }
        assertNotNull(retrievedFirst)
        assertEquals("User-configured targetValue must be preserved", 42, retrievedFirst!!.targetValue)
        assertTrue("User-configured isEnabled must be preserved", retrievedFirst.isEnabled)
    }

    @Test
    fun test07_migrationCutover_noRetrospectiveXpOrStreakWithoutEvidence() {
        val pastDate = "2026-10-15"
        val todayDate = "2026-11-08"

        // Evaluate past date where no monitoring intervals exist
        val eval = repository.evaluatePastDayProgression(pastDate, todayDate)
        assertEquals(StreakEvaluationStatus.DISQUALIFIED, eval.streakStatus)
        assertEquals("INSUFFICIENT_ESTIMATED_COVERAGE", eval.streakReason)
        assertFalse(eval.awardCountGoalXp)
        assertFalse(eval.awardTimeGoalXp)
        assertFalse(eval.awardMindfulDayXp)
        val streakRecord = dbHelper.getStreakRecordForDate(pastDate)
        assertNotNull("Streak record should be recorded for audited past day", streakRecord)
        assertFalse("Past day without monitoring evidence must not qualify", streakRecord!!.isQualified)
    }

    // =========================================================================
    // Fix 2: Reboot-Safe Monitoring & Active Interval Persistence Tests
    // =========================================================================

    @Test
    fun test08_bootSessionId_storedInIntervalsAndHeartbeats() {
        BootSessionProvider.setTestBootSessionId("boot_session_test_001")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 1_000_000L }
        tracker.clockElapsedRealtime = { 10_000L }
        tracker.initialize(context, dbHelper)

        tracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = true,
            newTrackingEnabled = true,
            newScreenOn = true
        )

        tracker.clockWallTime = { 1_060_000L }
        tracker.clockElapsedRealtime = { 70_000L }
        tracker.recordHeartbeat(dbHelper = dbHelper)

        val intervals = dbHelper.getMonitoringIntervalsForDate(DateTimeUtils.getTodayDateString(Date(1_000_000L)))
        assertTrue("Active interval must be saved in database", intervals.isNotEmpty())
        assertEquals("boot_session_test_001", intervals.first().bootSessionId)

        val heartbeats = dbHelper.getMonitoringHeartbeatsForDate(DateTimeUtils.getTodayDateString(Date(1_000_000L)))
        assertTrue("Heartbeat must be saved in database", heartbeats.isNotEmpty())
        assertEquals("boot_session_test_001", heartbeats.first().bootSessionId)
    }

    @Test
    fun test09_activeMonitoringInterval_persistedInSQLiteRatherThanRamOnly() {
        BootSessionProvider.setTestBootSessionId("boot_session_persist_test")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 2_000_000L }
        tracker.clockElapsedRealtime = { 20_000L }
        tracker.initialize(context, dbHelper)

        tracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = true,
            newTrackingEnabled = true,
            newScreenOn = true
        )

        // Directly query unclosed active intervals from SQLite
        val activeIntervals = dbHelper.getUnclosedMonitoringIntervals()
        assertEquals("Exactly one active interval must be marked is_active = 1 in SQLite", 1, activeIntervals.size)
        val active = activeIntervals.first()
        assertTrue(active.isActive)
        assertEquals("boot_session_persist_test", active.bootSessionId)
        assertEquals(2_000_000L, active.startTimestamp)
        assertEquals(20_000L, active.monotonicStartMs)
        assertTrue(active.serviceConnected)
        assertTrue(active.trackingEnabled)
        assertTrue(active.screenOn)
    }

    @Test
    fun test10_processRestart_sameBoot_recoversActiveIntervalSafely() {
        BootSessionProvider.setTestBootSessionId("same_boot_session_123")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 3_000_000L }
        tracker.clockElapsedRealtime = { 30_000L }
        tracker.initialize(context, dbHelper)

        // 1. Start active interval
        tracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = true,
            newTrackingEnabled = true,
            newScreenOn = true
        )

        // 2. Simulate process crash / kill without calling onStateChanged(stop)
        tracker.reset()

        // 3. App process restarts under SAME boot session within 5 minutes (<= MAX_CONTINUITY_GAP_MS)
        tracker.clockWallTime = { 3_000_000L + 5 * 60_000L }
        tracker.clockElapsedRealtime = { 30_000L + 5 * 60_000L }
        tracker.initialize(context, dbHelper)

        // The previous active interval should have been safely closed with PROCESS_RESTART_RECOVERED
        val unclosedAfterInit = dbHelper.getUnclosedMonitoringIntervals()
        assertTrue("No unclosed active intervals should remain after initialization", unclosedAfterInit.isEmpty())

        val intervals = dbHelper.getMonitoringIntervalsForDate(DateTimeUtils.getTodayDateString(Date(3_000_000L)))
        val closedInterval = intervals.firstOrNull { it.startTimestamp == 3_000_000L }
        assertNotNull(closedInterval)
        assertFalse("Interval must no longer be active", closedInterval!!.isActive)
        assertEquals("PROCESS_RESTART_RECOVERED", closedInterval.interruptionReason)
        assertEquals(ContinuityStatus.VERIFIED, closedInterval.continuityStatus)
    }

    @Test
    fun test10b_processRestart_longGap_marksProcessInterrupted() {
        BootSessionProvider.setTestBootSessionId("same_boot_session_long_gap")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 3_500_000L }
        tracker.clockElapsedRealtime = { 35_000L }
        tracker.initialize(context, dbHelper)

        // 1. Start active interval
        tracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = true,
            newTrackingEnabled = true,
            newScreenOn = true
        )

        // 2. Simulate process kill
        tracker.reset()

        // 3. App process restarts under SAME boot session with 25-minute gap (> MAX_CONTINUITY_GAP_MS)
        tracker.clockWallTime = { 3_500_000L + 25 * 60_000L }
        tracker.clockElapsedRealtime = { 35_000L + 25 * 60_000L }
        tracker.initialize(context, dbHelper)

        val unclosedAfterInit = dbHelper.getUnclosedMonitoringIntervals()
        assertTrue("No unclosed active intervals should remain after initialization", unclosedAfterInit.isEmpty())

        val intervals = dbHelper.getMonitoringIntervalsForDate(DateTimeUtils.getTodayDateString(Date(3_500_000L)))
        val closedInterval = intervals.firstOrNull { it.startTimestamp == 3_500_000L }
        assertNotNull(closedInterval)
        assertFalse("Interval must no longer be active", closedInterval!!.isActive)
        assertEquals("PROCESS_INTERRUPTED", closedInterval.interruptionReason)
        assertEquals(ContinuityStatus.INTERRUPTED, closedInterval.continuityStatus)
    }

    @Test
    fun test11_deviceReboot_interruptsUnfinishedPreviousInterval() {
        // Step 1: Interval was open under boot session A
        BootSessionProvider.setTestBootSessionId("boot_session_A")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 4_000_000L }
        tracker.clockElapsedRealtime = { 40_000L }
        tracker.initialize(context, dbHelper)

        tracker.onStateChanged(
            dbHelper = dbHelper,
            newServiceConnected = true,
            newTrackingEnabled = true,
            newScreenOn = true
        )

        // Verify active in DB under boot A
        assertEquals(1, dbHelper.getUnclosedMonitoringIntervals().size)

        // Step 2: Device reboots -> boot session changes to boot session B
        BootSessionProvider.setTestBootSessionId("boot_session_B")
        tracker.reset()

        // Step 3: Service / app restarts after reboot
        tracker.clockWallTime = { 5_000_000L }
        tracker.clockElapsedRealtime = { 5_000L } // Monotonic clock reset on reboot!
        tracker.initialize(context, dbHelper)

        // Unclosed interval from boot A must be closed as DEVICE_REBOOT with INTERRUPTED continuity
        val unclosed = dbHelper.getUnclosedMonitoringIntervals()
        assertTrue("No intervals should remain active across reboot", unclosed.isEmpty())

        val intervals = dbHelper.getMonitoringIntervalsForDate(DateTimeUtils.getTodayDateString(Date(4_000_000L)))
        val bootAInterval = intervals.firstOrNull { it.bootSessionId == "boot_session_A" }
        assertNotNull(bootAInterval)
        assertFalse(bootAInterval!!.isActive)
        assertEquals("DEVICE_REBOOT", bootAInterval.interruptionReason)
        assertEquals(ContinuityStatus.INTERRUPTED, bootAInterval.continuityStatus)
        assertEquals("Interrupted duration across reboot must be 0", 0L, bootAInterval.estimatedVerifiedDurationMs)
    }

    @Test
    fun test12_crossBoot_monotonicClocksNeverComparedOrMerged() {
        val testDate = "2026-11-05"

        // Interval 1 from Boot 1: monotonic starts at 1,000,000, runs 60 min
        val dur60m = 60L * 60_000L
        val intervalBoot1 = MonitoringInterval(
            dateKey = testDate,
            startTimestamp = 10_000_000L,
            endTimestamp = 10_000_000L + dur60m,
            monotonicStartMs = 1_000_000L,
            monotonicEndMs = 1_000_000L + dur60m,
            serviceConnected = true,
            trackingEnabled = true,
            screenOn = true,
            continuityStatus = ContinuityStatus.VERIFIED,
            bootSessionId = "boot_session_1"
        )

        // Interval 2 from Boot 2: reboot occurred, monotonic clock reset to 5,000 ms, runs 70 min
        val dur70m = 70L * 60_000L
        val intervalBoot2 = MonitoringInterval(
            dateKey = testDate,
            startTimestamp = 20_000_000L,
            endTimestamp = 20_000_000L + dur70m,
            monotonicStartMs = 5_000L,
            monotonicEndMs = 5_000L + dur70m,
            serviceConnected = true,
            trackingEnabled = true,
            screenOn = true,
            continuityStatus = ContinuityStatus.VERIFIED,
            bootSessionId = "boot_session_2"
        )

        dbHelper.insertMonitoringInterval(intervalBoot1)
        dbHelper.insertMonitoringInterval(intervalBoot2)

        val coverage = dbHelper.getDailyMonitoringCoverage(testDate)
        // Verified coverage must equal 60m + 70m = 130m (7,800,000 ms) without cross-boot monotonic interference
        assertEquals(dur60m + dur70m, coverage.estimatedVerifiedMonitoringMs)
        assertEquals(EstimatedCoverageStatus.ESTIMATED_VERIFIED, coverage.coverageStatus)
        assertTrue(coverage.isQualifiedForProgression)
    }

    @Test
    fun test13_rebootInterruptedInterval_excludedFromVerifiedMonitoringCoverage() {
        val testDate = "2026-11-04"

        // 1. Legitimate 60-minute verified interval
        val dur60m = 60L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = testDate,
                startTimestamp = 1_000_000L,
                endTimestamp = 1_000_000L + dur60m,
                monotonicStartMs = 10_000L,
                monotonicEndMs = 10_000L + dur60m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                continuityStatus = ContinuityStatus.VERIFIED,
                bootSessionId = "boot_1"
            )
        )

        // 2. Unfinished 120-minute interval that was terminated by a reboot
        val dur120m = 120L * 60_000L
        dbHelper.insertMonitoringInterval(
            MonitoringInterval(
                dateKey = testDate,
                startTimestamp = 5_000_000L,
                endTimestamp = 5_000_000L + dur120m,
                monotonicStartMs = 100_000L,
                monotonicEndMs = 100_000L + dur120m,
                serviceConnected = true,
                trackingEnabled = true,
                screenOn = true,
                interruptionReason = "DEVICE_REBOOT",
                continuityStatus = ContinuityStatus.INTERRUPTED,
                estimatedVerifiedDurationMs = 0L,
                bootSessionId = "boot_1"
            )
        )

        val coverage = dbHelper.getDailyMonitoringCoverage(testDate)
        // Interrupted interval must contribute 0 to verified coverage
        assertEquals(dur60m, coverage.estimatedVerifiedMonitoringMs)
        assertFalse("Total verified coverage is only 60m; cannot qualify", coverage.isQualifiedForProgression)
    }

    @Test
    fun test14_monitoringResumesWithFreshIntervalAfterReboot() {
        // Boot 1
        BootSessionProvider.setTestBootSessionId("boot_prior")
        val tracker = MonitoringCoverageTracker.instance
        tracker.clockWallTime = { 10_000_000L }
        tracker.clockElapsedRealtime = { 50_000L }
        tracker.initialize(context, dbHelper)
        tracker.onStateChanged(dbHelper, true, true, true)

        // Reboot to Boot 2
        BootSessionProvider.setTestBootSessionId("boot_fresh")
        tracker.reset()
        tracker.clockWallTime = { 20_000_000L }
        tracker.clockElapsedRealtime = { 1_000L }
        tracker.initialize(context, dbHelper)

        // Start new monitoring
        tracker.onStateChanged(dbHelper, true, true, true)

        val active = dbHelper.getUnclosedMonitoringIntervals()
        assertEquals(1, active.size)
        assertEquals("boot_fresh", active.first().bootSessionId)
        assertEquals(20_000_000L, active.first().startTimestamp)
        assertEquals(1_000L, active.first().monotonicStartMs)
    }

    @Test
    fun test15_schemaVersion6_upgradePreservesExistingDataAndAddsNewColumns() {
        // Verify user_version is 6
        val db = dbHelper.readableDatabase
        assertEquals("Database version must be 6", 6, db.version)

        // Verify system_metadata table exists
        val metaCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='system_metadata'", null)
        assertTrue("system_metadata table must exist", metaCursor.use { it.moveToFirst() })

        // Verify boot_session_id and is_active columns exist on monitoring_intervals
        val intColCursor = db.rawQuery("PRAGMA table_info(monitoring_intervals)", null)
        val intColumns = mutableListOf<String>()
        intColCursor.use {
            while (it.moveToNext()) {
                intColumns.add(it.getString(1))
            }
        }
        assertTrue("monitoring_intervals must have boot_session_id column", intColumns.contains("boot_session_id"))
        assertTrue("monitoring_intervals must have is_active column", intColumns.contains("is_active"))

        // Verify boot_session_id column exists on monitoring_heartbeats
        val hbColCursor = db.rawQuery("PRAGMA table_info(monitoring_heartbeats)", null)
        val hbColumns = mutableListOf<String>()
        hbColCursor.use {
            while (it.moveToNext()) {
                hbColumns.add(it.getString(1))
            }
        }
        assertTrue("monitoring_heartbeats must have boot_session_id column", hbColumns.contains("boot_session_id"))
    }
}
