package com.marmaton.agent.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A lightweight, minimized representation of an on-screen UI node.
 * Uses minimized field names to reduce the context window consumption of local LLM models.
 */
@Serializable
data class ScreenNode(
    @SerialName("id") val id: String? = null,
    @SerialName("txt") val txt: String? = null,
    @SerialName("desc") val desc: String? = null,
    @SerialName("clk") val clk: Boolean = false,
    @SerialName("scrl") val scrl: Boolean = false,
    @SerialName("bnd") val bnd: List<Int> // [left, top, right, bottom]
)
