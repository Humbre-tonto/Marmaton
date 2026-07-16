package com.marmaton.agent.llm

interface LlmBackend {
    val displayName: String
    suspend fun status(): BackendStatus
    suspend fun generate(prompt: String): String
}

sealed interface BackendStatus {
    data object Ready : BackendStatus
    data class NotReady(val reason: String) : BackendStatus
    data class Unavailable(val reason: String) : BackendStatus
}
