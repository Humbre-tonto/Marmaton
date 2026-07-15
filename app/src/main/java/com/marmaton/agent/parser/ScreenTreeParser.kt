package com.marmaton.agent.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object ScreenTreeParser {

    /**
     * Traverses the AccessibilityNodeInfo tree and serializes it to a compact, minimal JSON array string.
     * It aggressively filters out invisible nodes, scrollbars, and decorative layouts with no content/interactions.
     */
    fun serializeTree(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[]"
        val nodes = mutableListOf<ScreenNode>()
        traverseAndCollect(root, nodes)
        return "[${nodes.joinToString(",") { it.toJsonString() }}]"
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
                        id = shortId,
                        txt = text,
                        desc = desc,
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
