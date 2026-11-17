package com.reelmonster.app.service.tracking

import com.reelmonster.app.domain.model.DetectionCandidate
import java.util.Collections
import java.util.LinkedHashMap

/**
 * DeduplicationEngine
 *
 * Multi-stage event deduplication, progressive-metadata merger, and anti-fling filter
 * for Level 2 Automatic Tracking.
 *
 * Guarantees:
 * 1. One genuine new Reel/Short = +1.
 * 2. Repeated playback/progress accessibility events for the same Reel = +0.
 * 3. Progressive metadata binding (e.g. author loads first, caption loads 120ms later)
 *    is merged into the active candidate without resetting dwell or double-counting.
 * 4. If a previous candidate satisfied [minDwellMs] right as the next swipe candidate arrives,
 *    it is auto-confirmed via [consumeAutoConfirmedPrevious] instead of being lost.
 * 5. Returning to a recently watched Reel within [bounceBackWindowMs] is suppressed.
 */
class DeduplicationEngine(
    val minDwellMs: Long = DEFAULT_MIN_DWELL_MS,
    val bounceBackWindowMs: Long = DEFAULT_BOUNCE_BACK_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val DEFAULT_MIN_DWELL_MS = 450L
        const val DEFAULT_BOUNCE_BACK_WINDOW_MS = 10_000L
        const val MAX_CACHE_SIZE = 20
        private const val ENRICHMENT_WINDOW_MS = 1_500L
    }

    enum class Decision {
        /** Content confirmed as a newly watched video (+1 increment) */
        CONFIRMED,
        /** Content is pending the minimum dwell time */
        PENDING_DWELL,
        /** Previous content was skipped too quickly (< minDwellMs), new candidate is now pending dwell */
        IGNORED_FLING,
        /** Content is identical to the current active video (e.g. playback update) */
        IGNORED_SAME_CONTENT,
        /** Content was already counted within the bounce-back window (10s) */
        IGNORED_RECENT_BOUNCE_BACK,
        /** Engine is idle or input candidate is invalid */
        IGNORED_INVALID
    }

    private var currentCandidate: DetectionCandidate? = null
    private var candidateStartTime: Long = 0L
    private var isCurrentCandidateConfirmed: Boolean = false
    private var autoConfirmedPrevious: DetectionCandidate? = null

    // LRU cache mapping fingerprint -> confirmedTimestamp
    private val recentFingerprints: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    val activeCandidate: DetectionCandidate? get() = currentCandidate
    val isConfirmed: Boolean get() = isCurrentCandidateConfirmed

    @Synchronized
    fun currentDwellElapsedMs(timestamp: Long = clock()): Long {
        if ( currentCandidate == null || candidateStartTime == 0L ) return 0L
        return (timestamp - candidateStartTime).coerceAtLeast(0L)
    }

    /**
     * Retrieves and clears any previous candidate that satisfied [minDwellMs] at the exact
     * moment a transition to a new candidate occurred before the background timer fired.
     */
    @Synchronized
    fun consumeAutoConfirmedPrevious(): DetectionCandidate? {
        val prev = autoConfirmedPrevious
        autoConfirmedPrevious = null
        return prev
    }

    /**
     * Refreshes the bounce-back timestamp of the currently active candidate (e.g. while
     * the user is reading the Comments sheet on the current Reel/Short).
     */
    @Synchronized
    fun touchActiveTimestamp(timestamp: Long = clock()) {
        val active = currentCandidate ?: return
        if (isCurrentCandidateConfirmed) {
            recentFingerprints[active.fingerprint] = timestamp
        }
    }

    /**
     * Ingests a detection candidate from an accessibility event pass.
     *
     * @param candidate The candidate detected from the UI hierarchy.
     * @param timestamp Optional explicit timestamp (for deterministic unit tests).
     * @return The deduplication decision.
     */
    @Synchronized
    fun processCandidate(
        candidate: DetectionCandidate,
        timestamp: Long = clock()
    ): Decision {
        val active = currentCandidate

        // Case 1: Exact same candidate fingerprint OR progressive metadata enrichment for the same creator
        if (active != null && active.targetApp == candidate.targetApp &&
            (active.fingerprint == candidate.fingerprint || isProgressiveEnrichment(active, candidate, timestamp))
        ) {
            if (active.fingerprint != candidate.fingerprint) {
                // Keep richer metadata candidate while preserving start time and confirmation status
                val richer = if (!candidate.titleOrCaption.isNullOrBlank()) candidate else active
                currentCandidate = richer
                if (isCurrentCandidateConfirmed) {
                    recentFingerprints[active.fingerprint] = timestamp
                    recentFingerprints[richer.fingerprint] = timestamp
                }
            }

            return if (!isCurrentCandidateConfirmed) {
                if ((timestamp - candidateStartTime) >= minDwellMs) {
                    confirmActive(timestamp)
                    Decision.CONFIRMED
                } else {
                    Decision.PENDING_DWELL
                }
            } else {
                recentFingerprints[currentCandidate!!.fingerprint] = timestamp
                Decision.IGNORED_SAME_CONTENT
            }
        }

        // Case 2: New distinct candidate arrived (Transition)
        // First check if the outgoing unconfirmed candidate had already satisfied minDwellMs!
        val wasPreviousFling = if (active != null && !isCurrentCandidateConfirmed) {
            val prevElapsed = timestamp - candidateStartTime
            if (prevElapsed >= minDwellMs) {
                // Previous candidate met full dwell time before user swiped away -> auto-confirm it!
                recentFingerprints[active.fingerprint] = timestamp
                autoConfirmedPrevious = active
                false
            } else {
                true
            }
        } else {
            false
        }

        // Check if the incoming candidate was recently counted within the bounce-back window
        val lastSeen = recentFingerprints[candidate.fingerprint]
        if (lastSeen != null && (timestamp - lastSeen) < bounceBackWindowMs) {
            currentCandidate = candidate
            candidateStartTime = timestamp
            isCurrentCandidateConfirmed = true
            recentFingerprints[candidate.fingerprint] = timestamp
            return Decision.IGNORED_RECENT_BOUNCE_BACK
        }

        // Set up the new candidate as active and pending dwell
        currentCandidate = candidate
        candidateStartTime = timestamp
        isCurrentCandidateConfirmed = false

        return if (wasPreviousFling) {
            Decision.IGNORED_FLING
        } else {
            Decision.PENDING_DWELL
        }
    }

    /**
     * Checks if two candidates represent the same video where the caption/title bound
     * shortly after the creator/channel handle (or vice versa).
     */
    private fun isProgressiveEnrichment(
        active: DetectionCandidate,
        incoming: DetectionCandidate,
        timestamp: Long
    ): Boolean {
        val elapsed = timestamp - candidateStartTime
        val activeAuthor = active.authorOrChannel?.removePrefix("@")?.trim()?.lowercase()
        val incomingAuthor = incoming.authorOrChannel?.removePrefix("@")?.trim()?.lowercase()

        // Case A: Initial frame had only fallback/caption (<none> author), and author bound within 1.5s
        if (elapsed <= ENRICHMENT_WINDOW_MS && activeAuthor.isNullOrBlank() && !incomingAuthor.isNullOrBlank()) {
            return true
        }

        if (activeAuthor.isNullOrBlank() || incomingAuthor.isNullOrBlank()) return false
        if (activeAuthor != incomingAuthor) return false

        // Case B: Same creator where one frame lacked caption/title and the other has it,
        // or secondary ticker/overlay text updated on the same creator's video within 2.5s
        val activeHasTitle = !active.titleOrCaption.isNullOrBlank()
        val incomingHasTitle = !incoming.titleOrCaption.isNullOrBlank()
        return (activeHasTitle != incomingHasTitle && elapsed <= ENRICHMENT_WINDOW_MS) || elapsed <= 2_500L
    }

    /**
     * Checks if the currently active candidate has now satisfied the dwell threshold.
     * Call this periodically or upon idle time checks.
     */
    @Synchronized
    fun checkDwell(timestamp: Long = clock()): Decision {
        val active = currentCandidate ?: return Decision.IGNORED_INVALID
        if (isCurrentCandidateConfirmed) return Decision.IGNORED_SAME_CONTENT

        return if ((timestamp - candidateStartTime) >= minDwellMs) {
            confirmActive(timestamp)
            Decision.CONFIRMED
        } else {
            Decision.PENDING_DWELL
        }
    }

    /**
     * Marks the active candidate as confirmed and records it in the bounce-back cache.
     */
    @Synchronized
    private fun confirmActive(timestamp: Long) {
        val active = currentCandidate ?: return
        isCurrentCandidateConfirmed = true
        recentFingerprints[active.fingerprint] = timestamp
    }

    /**
     * Resets active state (e.g. when exiting viewer or switching apps).
     */
    @Synchronized
    fun reset(clearRecentHistory: Boolean = false) {
        currentCandidate = null
        candidateStartTime = 0L
        isCurrentCandidateConfirmed = false
        autoConfirmedPrevious = null
        if (clearRecentHistory) {
            recentFingerprints.clear()
        }
    }
}
