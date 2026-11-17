package com.reelmonster.app.service.tracking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * NodeSource
 *
 * Unified abstraction over accessibility node hierarchies.
 * Allows production code to inspect real Android [AccessibilityNodeInfo]
 * and test harnesses to supply lightweight [MockNodeSource] fixtures
 * without needing mock frameworks.
 */
interface NodeSource {
    val packageName: CharSequence?
    val className: CharSequence?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val viewIdResourceName: String?
    val isScrollable: Boolean
    val isSelected: Boolean get() = false
    val isVisibleToUser: Boolean get() = true
    val childCount: Int
    fun getChild(index: Int): NodeSource?
}

/**
 * Production implementation wrapping Android [AccessibilityNodeInfo].
 */
class AccessibilityNodeSource(private val node: AccessibilityNodeInfo) : NodeSource {
    override val packageName: CharSequence? get() = node.packageName
    override val className: CharSequence? get() = node.className
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val isScrollable: Boolean get() = node.isScrollable
    override val isSelected: Boolean get() = node.isSelected
    override val isVisibleToUser: Boolean
        get() = try {
            node.isVisibleToUser
        } catch (_: Exception) {
            true
        }
    override val childCount: Int get() = node.childCount

    override fun getChild(index: Int): NodeSource? {
        val child = try {
            node.getChild(index)
        } catch (_: Exception) {
            null
        } ?: return null
        return AccessibilityNodeSource(child)
    }
}

/**
 * Lightweight mock implementation for automated unit tests.
 */
class MockNodeSource(
    override val packageName: CharSequence? = null,
    override val className: CharSequence? = null,
    override val text: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val viewIdResourceName: String? = null,
    override val isScrollable: Boolean = false,
    override val isSelected: Boolean = false,
    val children: List<MockNodeSource> = emptyList()
) : NodeSource {
    override val childCount: Int get() = children.size

    override fun getChild(index: Int): NodeSource? {
        return if (index in children.indices) children[index] else null
    }
}

/**
 * Single node entry captured during a single-pass tree traversal.
 */
data class NodeEntry(
    val depth: Int,
    val className: String,
    val viewId: String,
    val shortViewId: String,
    val text: String,
    val contentDesc: String,
    val isScrollable: Boolean,
    val isSelected: Boolean,
    val inClipsOrShortsSubtree: Boolean = false
)

private val TARGET_SUBTREE_ROOT_IDS = setOf(
    "clips_viewer_view_pager",
    "clips_viewer_container",
    "root_clips_layout",
    "clips_media_component",
    "reel_watch_fragment_root",
    "reel_recycler",
    "reel_player_page_container"
)

/**
 * Pre-computed snapshot of an AccessibilityNodeInfo hierarchy from a single tree pass.
 * Eliminates repeated IPC traversals across deep (30+ level) Instagram and YouTube view trees.
 */
class HierarchySnapshot(
    val packageName: String,
    val entries: List<NodeEntry>
) {
    /**
     * Entries scoped strictly to the Clips / Shorts viewer subtree when such a container
     * view ID is present in the hierarchy; falls back to [entries] for mock/obfuscated trees.
     */
    val scopedEntries: List<NodeEntry> by lazy {
        val subtree = entries.filter { it.inClipsOrShortsSubtree }
        if (subtree.isNotEmpty()) subtree else entries
    }

    val scopedTexts: List<String> by lazy {
        val list = ArrayList<String>(scopedEntries.size * 2)
        for (entry in scopedEntries) {
            if (entry.text.isNotEmpty()) list.add(entry.text)
            if (entry.contentDesc.isNotEmpty() && entry.contentDesc != entry.text) {
                list.add(entry.contentDesc)
            }
        }
        list
    }

    val allTexts: List<String> by lazy {
        val list = ArrayList<String>(entries.size * 2)
        for (entry in entries) {
            if (entry.text.isNotEmpty()) list.add(entry.text)
            if (entry.contentDesc.isNotEmpty() && entry.contentDesc != entry.text) {
                list.add(entry.contentDesc)
            }
        }
        list
    }

    val allLowerTexts: List<String> by lazy {
        allTexts.map { it.lowercase() }
    }

    val allShortViewIds: Set<String> by lazy {
        entries.mapNotNull { entry ->
            entry.shortViewId.takeIf { it.isNotEmpty() }?.lowercase()
        }.toSet()
    }

    fun hasViewId(shortId: String): Boolean {
        val target = shortId.lowercase()
        return allShortViewIds.any { it == target || it.contains(target) }
    }
}

/**
 * Captures a single-pass [HierarchySnapshot] up to [maxDepth] (default 45).
 * Real Instagram Reels and YouTube Shorts hierarchies on Android 14–17 reach depths 18–35.
 * Only records nodes where [NodeSource.isVisibleToUser] is true so off-screen ViewPager/RecyclerView
 * pages and backgrounded fragments never pollute content extraction.
 */
fun NodeSource.captureSnapshot(maxDepth: Int = 45): HierarchySnapshot {
    val pkg = packageName?.toString() ?: ""
    val entries = ArrayList<NodeEntry>(64)

    fun walk(node: NodeSource, depth: Int, inSubtree: Boolean) {
        if (depth > maxDepth) return
        val rawViewId = node.viewIdResourceName?.trim() ?: ""
        val shortId = rawViewId.substringAfterLast('/', rawViewId)
        val lowerId = shortId.lowercase()
        val nowInSubtree = inSubtree ||
            (lowerId.isNotEmpty() && (
                lowerId in TARGET_SUBTREE_ROOT_IDS ||
                    lowerId.startsWith("clips_") ||
                    lowerId.startsWith("reel_player") ||
                    lowerId.startsWith("reel_watch")
                ))

        if (node.isVisibleToUser) {
            val rawText = node.text?.toString()?.replace('\u00a0', ' ')?.trim() ?: ""
            val rawDesc = node.contentDescription?.toString()?.replace('\u00a0', ' ')?.trim() ?: ""
            val cls = node.className?.toString() ?: ""

            if (rawText.isNotEmpty() || rawDesc.isNotEmpty() || shortId.isNotEmpty() || node.isScrollable || node.isSelected) {
                entries.add(
                    NodeEntry(
                        depth = depth,
                        className = cls,
                        viewId = rawViewId,
                        shortViewId = shortId,
                        text = rawText,
                        contentDesc = rawDesc,
                        isScrollable = node.isScrollable,
                        isSelected = node.isSelected,
                        inClipsOrShortsSubtree = nowInSubtree
                    )
                )
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            node.getChild(i)?.let { walk(it, depth + 1, nowInSubtree) }
        }
    }

    walk(this, 0, false)
    return HierarchySnapshot(packageName = pkg, entries = entries)
}

/**
 * Traverses a node tree and collects all non-blank text and content descriptions.
 * Default maxDepth is 45 to reach deep ViewPager2/RecyclerView children in Instagram and YouTube.
 */
fun NodeSource.collectAllText(maxDepth: Int = 45, currentDepth: Int = 0): List<String> {
    if (currentDepth > maxDepth) return emptyList()
    val results = mutableListOf<String>()

    text?.toString()?.replace('\u00a0', ' ')?.trim()?.takeIf { it.isNotEmpty() }?.let { results.add(it) }
    contentDescription?.toString()?.replace('\u00a0', ' ')?.trim()?.takeIf { it.isNotEmpty() }?.let {
        if (results.lastOrNull() != it) {
            results.add(it)
        }
    }

    for (i in 0 until childCount) {
        getChild(i)?.collectAllText(maxDepth, currentDepth + 1)?.let { results.addAll(it) }
    }
    return results
}

/**
 * Searches for any node satisfying the given predicate.
 */
fun NodeSource.findNode(predicate: (NodeSource) -> Boolean): NodeSource? {
    if (predicate(this)) return this
    for (i in 0 until childCount) {
        val found = getChild(i)?.findNode(predicate)
        if (found != null) return found
    }
    return null
}
