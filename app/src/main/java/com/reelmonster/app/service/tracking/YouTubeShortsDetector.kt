package com.reelmonster.app.service.tracking

import com.reelmonster.app.domain.model.DetectionCandidate
import com.reelmonster.app.domain.model.TargetApp
import java.security.MessageDigest

/**
 * YouTubeShortsDetector
 *
 * Multi-signal semantic and structural layout inspector for YouTube Shorts (com.google.android.youtube).
 * Supports deep hierarchies (depth 18–36 on Android 14–17 / One UI), resource-ID signals
 * (reel_watch_fragment_root, reel_recycler, reel_player_footer_container), and semantic descriptors
 * ("Go to channel @...", "Subscribe to @...", title nodes, and engagement panel suppression).
 */
class YouTubeShortsDetector {

    companion object {
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        // View IDs specific to the YouTube Shorts player hierarchy
        private val SHORTS_VIEWER_VIEW_IDS = setOf(
            "reel_watch_fragment_root",
            "reel_recycler",
            "reel_player_page_container",
            "reel_player_overlay_root",
            "reel_player_overlay_container",
            "reel_video_interactions",
            "reel_player_footer_container"
        )

        // View IDs indicating an open child engagement panel (comments / description)
        private val CHILD_OVERLAY_VIEW_IDS = setOf(
            "app_engagement_panel_wrapper",
            "app_engagement_panel",
            "engagement_panel"
        )

        // Keywords unique to YouTube Shorts interaction rail
        private val SHORTS_ACTION_KEYWORDS = listOf(
            "dislike", "remix", "shorts", "sound", "subscribe",
            "like this video", "thumbs up", "share this video",
            "go to channel", "see more videos using this sound"
        )

        // Markers indicating standard 16:9 video player or browse feed
        private val STANDARD_PLAYER_MARKERS = listOf(
            "play in full screen", "enter full screen", "up next",
            "autonav", "chapters", "save to playlist"
        )

        // Static UI chrome labels that must never be mistaken for a video title or channel
        private val UI_CHROME_EXACT = setOf(
            "home", "shorts", "create", "subscriptions", "you", "library",
            "search", "more actions", "save", "share", "dislike", "like",
            "subscribe", "subscribed", "remix", "comments", "close", "drag handle"
        )

        private val GO_TO_CHANNEL_REGEX = Regex("(?i)^Go to channel\\s+(.+)$")
        private val SUBSCRIBE_TO_REGEX = Regex("(?i)^Subscribe to\\s+(.+?)\\.?$")
        private val VIEW_COMMENTS_REGEX = Regex("(?i)^View\\s+[0-9.,\\sA-Za-z]+\\s+comments?$")
        private val NUMERIC_COUNT_REGEX = Regex("^[0-9.,\\s\\u00a0]+(k|m|b|lakh|crore)?$", RegexOption.IGNORE_CASE)
    }

    /**
     * Checks if the package belongs to YouTube.
     */
    fun matchesPackage(packageName: CharSequence?): Boolean {
        return packageName?.toString() == PACKAGE_YOUTUBE
    }

    /**
     * Checks if a child overlay (such as comments drawer) is open.
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
                lower.startsWith("comments.") ||
                lower == "sort comments" ||
                lower.contains("top comments")
        }
    }

    /**
     * Evaluates if the current hierarchy represents the active YouTube Shorts viewer.
     */
    fun isViewerActive(root: NodeSource?): Boolean {
        if (root == null || !matchesPackage(root.packageName)) return false
        return isViewerActive(root.captureSnapshot())
    }

    fun isViewerActive(snapshot: HierarchySnapshot): Boolean {
        if (!matchesPackage(snapshot.packageName)) return false

        val lowerTexts = snapshot.allLowerTexts

        // Reject standard 16:9 horizontal video player
        val standardMatches = STANDARD_PLAYER_MARKERS.count { marker ->
            lowerTexts.any { it.contains(marker) }
        }
        if (standardMatches > 0) return false

        // 1. Direct structural check via Shorts View IDs
        val hasShortsContainer = SHORTS_VIEWER_VIEW_IDS.any { snapshot.hasViewId(it) }
        if (hasShortsContainer) {
            return true
        }

        // 2. Semantic keyword check for obfuscated or mock hierarchies
        val shortsMatches = SHORTS_ACTION_KEYWORDS.count { keyword ->
            lowerTexts.any { it.contains(keyword) }
        }

        return shortsMatches >= 2
    }

    /**
     * Extracts a DetectionCandidate from the Shorts viewer hierarchy.
     */
    fun extractCandidate(root: NodeSource?): DetectionCandidate? {
        if (root == null) return null
        return extractCandidate(root.captureSnapshot())
    }

    fun extractCandidate(snapshot: HierarchySnapshot): DetectionCandidate? {
        if (!isViewerActive(snapshot) || isChildOverlayActive(snapshot)) {
            return null
        }

        val allTexts = snapshot.scopedTexts

        // 1. Extract channel name using prioritized multi-signal cascade
        var channel: String? = null

        // Priority 1a: "Go to channel @ChannelName"
        for (text in allTexts) {
            val match = GO_TO_CHANNEL_REGEX.find(text)
            if (match != null) {
                channel = match.groupValues[1].trim()
                break
            }
        }

        // Priority 1b: "Subscribe to @ChannelName."
        if (channel.isNullOrBlank()) {
            for (text in allTexts) {
                val match = SUBSCRIBE_TO_REGEX.find(text)
                if (match != null) {
                    channel = match.groupValues[1].trim()
                    break
                }
            }
        }

        // Priority 1c: Explicit @handle
        if (channel.isNullOrBlank()) {
            channel = allTexts.firstOrNull { it.startsWith("@") && it.length > 1 }
        }

        // Priority 1d: Node immediately preceding "Subscribe"
        if (channel.isNullOrBlank()) {
            val subscribeIndex = allTexts.indexOfFirst { it.equals("Subscribe", ignoreCase = true) }
            if (subscribeIndex > 0) {
                val candidateChannel = allTexts[subscribeIndex - 1].trim()
                if (candidateChannel.lowercase() !in UI_CHROME_EXACT) {
                    channel = candidateChannel
                }
            }
        }

        // 2. Extract video title (excluding UI chrome, progress seekbar, and action rail counts)
        val title = allTexts.firstOrNull { text ->
            val lower = text.lowercase().trim()
            text != channel &&
                !text.equals(channel?.removePrefix("@"), ignoreCase = true) &&
                text.length > 3 &&
                lower !in UI_CHROME_EXACT &&
                !lower.startsWith("go to channel") &&
                !lower.startsWith("subscribe to") &&
                !lower.startsWith("like this video") &&
                !lower.startsWith("dislike this video") &&
                !lower.startsWith("share this video") &&
                !lower.startsWith("remix this short") &&
                !lower.startsWith("see more videos using") &&
                !lower.startsWith("search what you see") &&
                !lower.contains("seconds of") &&
                !lower.contains("minutes") &&
                !lower.contains("views") &&
                !VIEW_COMMENTS_REGEX.matches(text) &&
                !NUMERIC_COUNT_REGEX.matches(text) &&
                !SHORTS_ACTION_KEYWORDS.any { lower == it }
        }

        // Secondary fallback when Litho renders channel/title as a canvas without text nodes
        val fallbackDescriptor = if (channel.isNullOrBlank() && title.isNullOrBlank()) {
            allTexts.firstOrNull { it.startsWith("See more videos using", ignoreCase = true) }
                ?: allTexts.firstOrNull { VIEW_COMMENTS_REGEX.matches(it) }
                ?: allTexts.firstOrNull { it.startsWith("Like this video along with", ignoreCase = true) }
        } else {
            null
        }

        // Wait for metadata binding if neither channel, title, nor fallback descriptor is present yet
        if (channel.isNullOrBlank() && title.isNullOrBlank() && fallbackDescriptor.isNullOrBlank()) {
            return null
        }

        val normalizedChannel = channel?.removePrefix("@")?.lowercase()?.trim() ?: "unknown_channel"
        val normalizedTitle = (title ?: fallbackDescriptor)?.take(36)?.lowercase()?.trim() ?: ""

        // Secondary fallback tag for title-less Shorts from the same channel
        val fallbackMediaTag = if (normalizedTitle.isEmpty()) {
            val commentsDesc = allTexts.firstOrNull { VIEW_COMMENTS_REGEX.matches(it) }?.lowercase() ?: ""
            val remixDesc = allTexts.firstOrNull { it.startsWith("Remix this Short", ignoreCase = true) }?.lowercase() ?: ""
            "$commentsDesc|$remixDesc"
        } else {
            ""
        }

        val rawSeed = "${TargetApp.YOUTUBE.name}:$normalizedChannel:$normalizedTitle:$fallbackMediaTag"
        val fingerprint = computeHash(rawSeed)

        return DetectionCandidate(
            targetApp = TargetApp.YOUTUBE,
            fingerprint = fingerprint,
            authorOrChannel = channel,
            titleOrCaption = title
        )
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
