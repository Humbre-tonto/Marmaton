package com.marmaton.agent.llm

interface LlmBackend : java.lang.AutoCloseable {
    val displayName: String
    suspend fun status(): BackendStatus
    suspend fun generate(prompt: String): String
    override fun close() {}
}

sealed interface BackendStatus {
    data object Ready : BackendStatus
    data class NotReady(val reason: String) : BackendStatus
    data class Unavailable(val reason: String) : BackendStatus
}
