package com.marmaton.agent.parser

import android.graphics.Rect

/**
 * A lightweight, minimized representation of an on-screen UI node.
 * Uses minimized field names to reduce the context window consumption of local LLM models.
 */
data class ScreenNode(
    val id: String?,
    val txt: String?,
    val desc: String?,
    val clk: Boolean,
    val scrl: Boolean,
    val bnd: List<Int> // [left, top, right, bottom]
) {
    fun toJsonString(): String {
        val parts = mutableListOf<String>()
        id?.let { parts.add("\"id\":\"$it\"") }
        txt?.let { parts.add("\"txt\":\"${escapeJson(it)}\"") }
        desc?.let { parts.add("\"desc\":\"${escapeJson(it)}\"") }
        if (clk) parts.add("\"clk\":true")
        if (scrl) parts.add("\"scrl\":true")
        parts.add("\"bnd\":[${bnd.joinToString(",")}]")
        return "{${parts.joinToString(",")}}"
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
