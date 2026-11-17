package com.reelmonster.app.service.tracking

import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp
import java.security.MessageDigest

/**
 * InstagramReelDetector
 *
 * Multi-signal semantic and structural layout inspector for Instagram Reels (com.instagram.android).
 * Supports deep hierarchies (depth 12–40 on Android 14–17 / One UI), resource-ID signals when
 * available, and obfuscation-resilient contentDescription/text signals ("Reel by <creator>",
 * "Profile picture of <creator>", author username buttons, and caption components).
 */
class InstagramReelDetector {

    companion object {
        const val PACKAGE_INSTAGRAM = "com.instagram.android"

        // View IDs specific to the Instagram Reels (Clips) viewer
        private val CLIPS_VIEWER_VIEW_IDS = setOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "root_clips_layout",
            "clips_media_component",
            "clips_video_container",
            "clips_item_overlay_component",
            "clips_author_username",
            "clips_ufi_component"
        )

        // View IDs indicating an open child bottom sheet (comments / share)
        private val CHILD_OVERLAY_VIEW_IDS = setOf(
            "layout_comment_thread_edittext",
            "comment_composer_options_container",
            "bottom_sheet_container_view",
            "direct_private_share_container_view"
        )

        // Common keywords in Reels action rail
        private val REEL_ACTION_KEYWORDS = listOf(
            "like", "comment", "share", "remix", "audio", "reels",
            "repost", "send", "bookmark", "save"
        )

        // Home feed markers to distinguish from Reels viewer when no clips container is present
        private val HOME_FEED_MARKERS = listOf(
            "direct messages", "search and explore", "camera", "your story", "stories tray"
        )

        // Non-Reels bottom navigation tab IDs
        private val NON_REEL_TAB_IDS = setOf(
            "feed_tab", "direct_tab", "search_tab", "profile_tab"
        )

        // Static UI labels that must never be mistaken for a creator handle or caption
        private val UI_STOPWORDS = setOf(
            "reels", "friends", "for you", "following", "follow", "like", "likes",
            "comment", "comments", "share", "repost", "save", "more", "audio",
            "original", "home", "message", "messages", "profile", "search",
            "camera", "direct", "sponsored", "ad", "see more", "… more",
            "create a reel", "search and explore", "notifications"
        )

        private val REEL_BY_REGEX = Regex("(?i)^Reel by\\s+([^.]+?)\\.(?:\\s|$)")
        private val PROFILE_PIC_REGEX = Regex("(?i)^Profile picture of\\s+(.+)$")
    }

    /**
     * Checks if the package belongs to Instagram.
     */
    fun matchesPackage(packageName: CharSequence?): Boolean {
        return packageName?.toString() == PACKAGE_INSTAGRAM
    }

    /**
     * Checks if a child overlay (such as comments sheet or share dialog) is currently presented.
     */
    fun isChildOverlayActive(root: NodeSource?): Boolean {
        if (root == null) return false
        return isChildOverlayActive(root.captureSnapshot())
    }

    fun isChildOverlayActive(snapshot: HierarchySnapshot): Boolean {
        if (CHILD_OVERLAY_VIEW_IDS.any { snapshot.hasViewId(it) }) {
            return true
        }
        return snapshot.allLowerTexts.any { lower ->
            lower.contains("add a comment") ||
                lower == "comments" ||
                lower.contains("share with friends") ||
                lower.contains("no comments yet") ||
                lower.contains("reactions and comments")
        }
    }

    /**
     * Evaluates if the current hierarchy represents the active Reels viewer.
     */
    fun isViewerActive(root: NodeSource?): Boolean {
        if (root == null || !matchesPackage(root.packageName)) return false
        return isViewerActive(root.captureSnapshot())
    }

    fun isViewerActive(snapshot: HierarchySnapshot): Boolean {
        if (!matchesPackage(snapshot.packageName)) return false

        // 1. Check if a non-Reel bottom tab (Home, Messages, Search, Profile) is explicitly selected
        // while the Reels tab (clips_tab) is not selected. Instagram's swipeable_tab_view_pager
        // keeps adjacent tab fragments in memory, so this check must run first.
        val nonReelTabSelected = snapshot.entries.any {
            it.isSelected && it.shortViewId.lowercase() in NON_REEL_TAB_IDS
        }
        val clipsTabSelected = snapshot.entries.any {
            it.isSelected && it.shortViewId.equals("clips_tab", ignoreCase = true)
        }
        if (nonReelTabSelected && !clipsTabSelected) {
            return false
        }

        // 2. Direct structural check via Clips viewer View IDs
        val hasClipsContainer = CLIPS_VIEWER_VIEW_IDS.any { snapshot.hasViewId(it) }
        if (hasClipsContainer) {
            return true
        }

        // 3. Direct semantic check via "Reel by <creator>" video container description
        val hasReelByDescriptor = snapshot.allTexts.any { REEL_BY_REGEX.containsMatchIn(it) }
        if (hasReelByDescriptor) {
            return true
        }

        // 4. Semantic keyword fallback for obfuscated or mock hierarchies
        val lowerTexts = snapshot.allLowerTexts
        val actionMatches = REEL_ACTION_KEYWORDS.count { keyword ->
            lowerTexts.any { it.contains(keyword) }
        }
        val homeMatches = HOME_FEED_MARKERS.count { marker ->
            lowerTexts.any { it.contains(marker) }
        }

        return actionMatches >= 2 && homeMatches < 2
    }

    /**
     * Extracts a DetectionCandidate from the Reels viewer hierarchy.
     */
    fun extractCandidate(root: NodeSource?): DetectionCandidate? {
        if (root == null) return null
        return extractCandidate(root.captureSnapshot())
    }

    fun extractCandidate(snapshot: HierarchySnapshot): DetectionCandidate? {
        if (!isViewerActive(snapshot) || isChildOverlayActive(snapshot)) {
            return null
        }

        val entries = snapshot.scopedEntries
        val allTexts = snapshot.scopedTexts

        // 1. Extract creator handle using prioritized multi-signal cascade
        var creator: String? = null

        // Priority 1a: Explicit clips_author_username node
        val authorNode = entries.firstOrNull {
            it.shortViewId.equals("clips_author_username", ignoreCase = true) &&
                (it.text.isNotBlank() || it.contentDesc.isNotBlank())
        }
        if (authorNode != null) {
            creator = (authorNode.text.ifBlank { authorNode.contentDesc }).trim()
        }

        // Priority 1b: "Reel by <creator>. Double tap to play or pause."
        if (creator.isNullOrBlank()) {
            for (text in allTexts) {
                val match = REEL_BY_REGEX.find(text)
                if (match != null) {
                    creator = match.groupValues[1].trim()
                    break
                }
            }
        }

        // Priority 1c: "Profile picture of <creator>"
        if (creator.isNullOrBlank()) {
            for (text in allTexts) {
                val match = PROFILE_PIC_REGEX.find(text)
                if (match != null) {
                    creator = match.groupValues[1].trim()
                    break
                }
            }
        }

        // Priority 1d: Explicit @handle text
        if (creator.isNullOrBlank()) {
            creator = allTexts.firstOrNull { it.startsWith("@") && it.length > 1 }
        }

        // Priority 1e: "Follow <Creator Name>" on inline_follow_button
        if (creator.isNullOrBlank()) {
            val followNode = entries.firstOrNull {
                it.shortViewId.equals("inline_follow_button", ignoreCase = true) &&
                    it.contentDesc.startsWith("Follow ", ignoreCase = true)
            }
            if (followNode != null) {
                creator = followNode.contentDesc.substringAfter("Follow ").trim()
            }
        }

        // Priority 1f: Fallback single-token handle excluding UI stopwords
        if (creator.isNullOrBlank()) {
            creator = allTexts.firstOrNull { text ->
                val lower = text.lowercase()
                text.length in 3..30 &&
                    !text.contains(" ") &&
                    lower !in UI_STOPWORDS &&
                    !REEL_ACTION_KEYWORDS.any { lower.contains(it) } &&
                    !lower.contains("original")
            }
        }

        // 2. Extract audio or tagged pill
        val audio = allTexts.firstOrNull { text ->
            val lower = text.lowercase()
            lower.contains("original audio") ||
                lower.contains("audio by") ||
                lower.contains("sound") ||
                lower.contains("posts tagged")
        }

        // 3. Extract caption text (excluding UI chrome and dynamic like counts)
        val caption = allTexts.firstOrNull { text ->
            val lower = text.lowercase()
            text != creator &&
                text != audio &&
                text.length > 5 &&
                lower !in UI_STOPWORDS &&
                !lower.startsWith("reel by ") &&
                !lower.startsWith("photo by ") &&
                !lower.startsWith("profile picture of ") &&
                !lower.startsWith("follow ") &&
                !lower.startsWith("like number is") &&
                !lower.startsWith("comment number is") &&
                !lower.startsWith("reshare number is") &&
                !lower.startsWith("reposted ") &&
                !lower.contains("double tap to play")
        }

        // If no content identity at all has bound yet (mid-scroll skeleton frame), wait for next event
        if (creator.isNullOrBlank() && caption.isNullOrBlank() && audio.isNullOrBlank()) {
            return null
        }

        val normalizedCreator = creator?.lowercase()?.trim() ?: "unknown_creator"
        val normalizedCaption = caption?.take(32)?.lowercase()?.trim() ?: ""
        val normalizedAudio = audio?.take(32)?.lowercase()?.trim() ?: ""

        // If both caption and audio are absent, use comment-count descriptor (which does not change on Like)
        // to distinguish consecutive captionless Reels from the same creator.
        val fallbackMediaTag = if (normalizedCaption.isEmpty() && normalizedAudio.isEmpty()) {
            allTexts.firstOrNull { it.startsWith("Comment number is", ignoreCase = true) }?.lowercase() ?: ""
        } else {
            ""
        }

        val rawSeed = "${TargetApp.INSTAGRAM.name}:$normalizedCreator:$normalizedCaption:$normalizedAudio:$fallbackMediaTag"
        val fingerprint = computeHash(rawSeed)

        return DetectionCandidate(
            targetApp = TargetApp.INSTAGRAM,
            fingerprint = fingerprint,
            authorOrChannel = creator,
            titleOrCaption = caption ?: audio
        )
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
