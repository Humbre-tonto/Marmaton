package com.marmaton.agent.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ScreenTreeParser {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    // On-device models have small context windows (e.g. Gemma 3 1B ekv1280). If the serialized
    // screen fills the window, the model's answer gets truncated mid-JSON. Keep the prompt bounded
    // and prioritize interactive elements so the actionable controls always survive the trim.
    private const val MAX_NODES = 30
    private const val MAX_CHARS = 1000

    /**
     * Traverses the AccessibilityNodeInfo tree and serializes it to a compact, minimal JSON array string.
     * It aggressively filters out invisible nodes, scrollbars, and decorative layouts with no content/interactions.
     */
    fun serializeTree(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[]"
        val nodes = mutableListOf<ScreenNode>()
        traverseAndCollect(root, nodes)

        // Interactive (clickable/scrollable) nodes first, so they survive if we trim.
        val ordered = nodes.sortedByDescending { it.clk || it.scrl }.take(MAX_NODES)
        return try {
            var out = json.encodeToString(ordered)
            var count = ordered.size
            while (count > 1 && out.length > MAX_CHARS) {
                count = (count * 3) / 4
                out = json.encodeToString(ordered.take(count))
            }
            out
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun traverseAndCollect(node: AccessibilityNodeInfo, list: MutableList<ScreenNode>) {
        if (!node.isVisibleToUser) {
            return
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val id = node.viewIdResourceName?.toString()?.trim()
        val isClickable = node.isClickable
        val isScrollable = node.isScrollable

        // Check if this node has any semantic or action value
        val hasContent = !text.isNullOrEmpty() || !desc.isNullOrEmpty()
        val hasInteractions = isClickable || isScrollable

        if (hasContent || hasInteractions || !id.isNullOrEmpty()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Only add nodes that are actually on screen (bounds not empty/collapsed)
            if (bounds.width() > 0 && bounds.height() > 0) {
                // Keep the package/view ID short
                val shortId = id?.substringAfter("/") ?: id
                list.add(
                    ScreenNode(
                        id = if (shortId.isNullOrEmpty()) null else shortId,
                        txt = if (text.isNullOrEmpty()) null else text,
                        desc = if (desc.isNullOrEmpty()) null else desc,
                        clk = isClickable,
                        scrl = isScrollable,
                        bnd = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    )
                )
            }
        }

        // Recursively traverse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseAndCollect(child, list)
                child.recycle()
            }
        }
    }
}
