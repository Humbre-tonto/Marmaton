package com.marmaton.agent.chat

import android.content.Context
import com.marmaton.agent.llm.BackendFactory
import com.marmaton.agent.llm.BackendStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the interactive chat conversation. Kept as a singleton (like
 * [com.marmaton.agent.AgentForegroundService]'s companion state) so the transcript survives
 * bottom-navigation tab switches and configuration changes without a database.
 *
 * Chat talks to whatever backend the user has selected in Backends (on-device `.task`/`.gguf`,
 * Ollama, or cloud) via [BackendFactory], so it automatically benefits from any newly added
 * model — including coder models — with no extra wiring.
 */
object ChatSession {

    enum class Role { USER, ASSISTANT }

    data class ChatMessage(val role: Role, val text: String)

    private const val SYSTEM_PROMPT =
        "You are Marmaton, a helpful, concise assistant running entirely on the user's phone. " +
            "Answer directly. When asked for code, return a short, correct snippet in a fenced code " +
            "block and briefly explain how to use it. Keep responses focused and mobile-friendly."

    /** Only the most recent turns are sent to keep small on-device models within their context. */
    private const val MAX_HISTORY_MESSAGES = 10

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var generateJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** Append the user's message and stream a reply from the active backend. */
    fun send(context: Context, userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        val appContext = context.applicationContext
        _messages.update { it + ChatMessage(Role.USER, trimmed) }
        _isGenerating.value = true

        generateJob = scope.launch {
            val reply = try {
                val backend = BackendFactory.createActiveBackend(appContext)
                when (val status = backend.status()) {
                    is BackendStatus.Ready -> {
                        val prompt = buildPrompt(_messages.value)
                        backend.generate(prompt).trim()
                            .ifBlank { "(the model returned an empty response)" }
                    }
                    is BackendStatus.NotReady ->
                        "⚠️ The selected model isn't ready: ${status.reason}\n\nPick or download a model under Backends."
                    is BackendStatus.Unavailable ->
                        "⚠️ The selected backend is unavailable: ${status.reason}\n\nCheck your model or connection under Backends."
                }
            } catch (e: Throwable) {
                "⚠️ Error talking to the model: ${e.message ?: e.toString()}"
            }
            _messages.update { it + ChatMessage(Role.ASSISTANT, reply) }
            _isGenerating.value = false
        }
    }

    /** Cancel any in-flight generation and wipe the transcript. */
    fun clear() {
        generateJob?.cancel()
        _isGenerating.value = false
        _messages.value = emptyList()
    }

    private fun buildPrompt(history: List<ChatMessage>): String {
        val recent = history.takeLast(MAX_HISTORY_MESSAGES)
        val sb = StringBuilder()
        sb.append(SYSTEM_PROMPT).append("\n\n")
        for (message in recent) {
            when (message.role) {
                Role.USER -> sb.append("User: ").append(message.text).append("\n")
                Role.ASSISTANT -> sb.append("Assistant: ").append(message.text).append("\n")
            }
        }
        sb.append("Assistant:")
        return sb.toString()
    }
}
