package com.marmaton.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.marmaton.agent.model.AgentAction

class MarmatonAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MarmatonAccessibilityService"

        @Volatile
        var instance: MarmatonAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Marmaton Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No action needed on individual events as we pull state on demand
    }

    override fun onInterrupt() {
        Log.d(TAG, "Marmaton Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Executes the parsed Action on the active screen structure.
     */
    fun executeAction(action: AgentAction): Boolean {
        return try {
            when (action.actionType) {
                "CLICK" -> performClickAction(action)
                "SWIPE_UP" -> performSwipe(up = true)
                "SWIPE_DOWN" -> performSwipe(up = false)
                "TYPE_TEXT" -> performTypeAction(action)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${action.actionType}", e)
            false
        }
    }

    private fun performClickAction(action: AgentAction): Boolean {
        val targetId = action.targetId
        val boundsList = action.bounds

        // 1. Try to find the node by viewId/text and click it
        if (!targetId.isNullOrEmpty()) {
            val root = rootInActiveWindow
            if (root != null) {
                // Search by view id resource name first
                var targetNodes = root.findAccessibilityNodeInfosByViewId(targetId)
                if (targetNodes.isEmpty()) {
                    // Fallback to text search
                    targetNodes = root.findAccessibilityNodeInfosByText(targetId)
                }
                for (node in targetNodes) {
                    if (clickNodeOrParent(node)) {
                        root.recycle()
                        return true
                    }
                }
                root.recycle()
            }
        }

        // 2. Fallback: coordinate-based click using dispatchGesture
        if (boundsList != null && boundsList.size == 4) {
            val left = boundsList[0]
            val top = boundsList[1]
            val right = boundsList[2]
            val bottom = boundsList[3]
            val clickX = (left + right) / 2f
            val clickY = (top + bottom) / 2f
            return clickCoordinates(clickX, clickY)
        }

        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    current.recycle()
                    return true
                }
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return false
    }

    private fun clickCoordinates(x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 100)
        gestureBuilder.addStroke(stroke)

        var success = false
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                success = true
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                success = false
            }
        }, null)

        return true // Dispatched gesture successfully
    }

    private fun performSwipe(up: Boolean): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val startX = screenWidth / 2f
        val startY = if (up) screenHeight * 0.8f else screenHeight * 0.2f
        val endY = if (up) screenHeight * 0.2f else screenHeight * 0.8f

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 500)
        gestureBuilder.addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), null, null)
        return true
    }

    private fun performTypeAction(action: AgentAction): Boolean {
        val text = action.textToType ?: return false
        val targetId = action.targetId
        val root = rootInActiveWindow ?: return false

        var targetNode: AccessibilityNodeInfo? = null

        if (!targetId.isNullOrEmpty()) {
            val targetNodes = root.findAccessibilityNodeInfosByViewId(targetId)
            if (targetNodes.isNotEmpty()) {
                targetNode = targetNodes[0]
            } else {
                val textNodes = root.findAccessibilityNodeInfosByText(targetId)
                if (textNodes.isNotEmpty()) {
                    targetNode = textNodes[0]
                }
            }
        }

        if (targetNode == null) {
            // Find currently focused node
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (targetNode != null) {
            // Focus and set text
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            val success = targetNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )
            targetNode.recycle()
            root.recycle()
            return success
        }

        root.recycle()
        return false
    }
}
