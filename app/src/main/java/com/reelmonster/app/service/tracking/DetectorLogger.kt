package com.reelmonster.app.service.tracking

import android.util.Log
import com.reelmonster.app.BuildConfig
import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp

/**
 * DetectorLogger
 *
 * Structured diagnostic logger for Level 2 Automatic Reel & Short detection.
 * Uses consistent logcat tag [TAG] ("ReelMonsterDetector") while sanitizing
 * text fields so no private messages or sensitive user data are ever logged.
 */
object DetectorLogger {
    const val TAG = "ReelMonsterDetector"

    /**
     * Phase 17 (SEC-01): diagnostic logging is on only in debug builds.
     * Release builds stay silent so detailed viewing activity (apps, creators,
     * titles, counts) is never written to logcat. Debug builds — including
     * every build ROHAN runs — keep full diagnostics. Toggle at runtime
     * only for a live debugging session.
     */
    var isEnabled: Boolean = BuildConfig.DEBUG

    fun logEventReceived(packageName: String, eventTypeName: String) {
        log("EVENT_RECEIVED | pkg=$packageName | type=$eventTypeName")
    }

    fun logTargetAppDetected(app: TargetApp) {
        log("TARGET_APP_DETECTED | app=${app.name} (${app.packageName})")
    }

    fun logReelViewerDetected(app: TargetApp, active: Boolean, reason: String = "") {
        val suffix = if (reason.isNotEmpty()) " | detail=$reason" else ""
        log("VIEWER_STATE | app=${app.name} | active=$active$suffix")
    }

    fun logContentIdentityExtracted(candidate: DetectionCandidate) {
        val safeAuthor = sanitize(candidate.authorOrChannel, maxLen = 24)
        val safeTitle = sanitize(candidate.titleOrCaption, maxLen = 28)
        log(
            "IDENTITY_EXTRACTED | app=${candidate.targetApp.name} | " +
                "fp=${candidate.fingerprint} | author=$safeAuthor | title=$safeTitle"
        )
    }

    fun logContentTransitionDetected(
        app: TargetApp,
        previousFp: String?,
        newCandidate: DetectionCandidate
    ) {
        val safeAuthor = sanitize(newCandidate.authorOrChannel, maxLen = 24)
        log(
            "TRANSITION_DETECTED | app=${app.name} | " +
                "from=${previousFp ?: "NONE"} -> to=${newCandidate.fingerprint} ($safeAuthor)"
        )
    }

    fun logTransitionRejected(app: TargetApp, reason: String, fingerprint: String? = null) {
        val fpPart = if (fingerprint != null) " | fp=$fingerprint" else ""
        log("TRANSITION_REJECTED | app=${app.name}$fpPart | reason=$reason")
    }

    fun logDeduplicationResult(
        candidate: DetectionCandidate,
        decision: DeduplicationEngine.Decision,
        dwellMs: Long = 0L
    ) {
        log(
            "DEDUP_RESULT | app=${candidate.targetApp.name} | " +
                "fp=${candidate.fingerprint} | decision=${decision.name} | dwellMs=$dwellMs"
        )
    }

    fun logRepositoryIncrement(
        app: TargetApp,
        fingerprint: String,
        success: Boolean,
        newTotalToday: Int
    ) {
        val status = if (success) "SUCCESS (+1)" else "FAILED/DEBOUNCED (+0)"
        log(
            "REPO_INCREMENT | app=${app.name} | fp=$fingerprint | " +
                "status=$status | todayCount=$newTotalToday"
        )
    }

    private fun sanitize(input: String?, maxLen: Int): String {
        if (input.isNullOrBlank()) return "<none>"
        val clean = input.replace("\n", " ").replace("\r", " ").trim()
        return if (clean.length <= maxLen) clean else clean.take(maxLen) + "..."
    }

    private fun log(message: String) {
        if (!isEnabled) return
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // Safe fallback when running in local JVM unit tests without android.util.Log
        }
    }
}
