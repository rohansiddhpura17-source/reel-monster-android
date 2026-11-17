package com.reelmonster.app

import com.reelmonster.app.service.tracking.DetectorLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Phase17SecurityAuditTest
 *
 * Phase 17 security hardening tests for diagnostic logging (SEC-01).
 * DetectorLogger writes detailed viewing activity to logcat; it must be
 * possible to silence it completely, and release builds must not log.
 * The default is BuildConfig.DEBUG (true for every debug build ROHAN runs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase17SecurityAuditTest {

    private var originalEnabled = true

    @Before
    fun setUp() {
        originalEnabled = DetectorLogger.isEnabled
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        DetectorLogger.isEnabled = originalEnabled
        ShadowLog.clear()
    }

    @Test
    fun test01_disabledLoggerWritesNothingToLogcat() {
        DetectorLogger.isEnabled = false
        DetectorLogger.logEventReceived("com.instagram.android", "TYPE_VIEW_SCROLLED")
        DetectorLogger.logEventReceived("com.google.android.youtube", "TYPE_VIEW_SCROLLED")
        assertEquals(0, ShadowLog.getLogs().size)
    }

    @Test
    fun test02_enabledLoggerWritesDiagnosticTag() {
        DetectorLogger.isEnabled = true
        DetectorLogger.logEventReceived("com.instagram.android", "TYPE_VIEW_SCROLLED")
        val logs = ShadowLog.getLogs()
        assertEquals(1, logs.size)
        assertEquals("ReelMonsterDetector", logs[0].tag)
    }

    @Test
    fun test03_debugBuildKeepsDiagnosticsEnabledByDefault() {
        // Unit tests run against the debug build variant, so the default
        // must be enabled here. Release builds get BuildConfig.DEBUG = false
        // and stay silent (SEC-01).
        assertEquals(true, originalEnabled)
    }
}
