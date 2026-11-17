package com.reelmonster.app.domain.model

/**
 * DetectionCandidate
 *
 * Represents an extracted content candidate candidate from an accessibility inspection pass.
 * Holds structural content descriptors used for deduplication.
 *
 * @property targetApp Instagram or YouTube
 * @property fingerprint Unique cryptographic or structural hash of the content
 * @property authorOrChannel Creator username or channel name if detectable
 * @property titleOrCaption Caption prefix or audio title if detectable
 * @property timestamp Extraction timestamp in milliseconds
 */
data class DetectionCandidate(
    val targetApp: TargetApp,
    val fingerprint: String,
    val authorOrChannel: String? = null,
    val titleOrCaption: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
