package com.marmaton.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentAction(
    val actionType: String,
    val targetId: String? = null,
    val bounds: List<Int>? = null,
    val textToType: String? = null,
    val reasoning: String
)
