package com.marmaton.agent.llm

import com.marmaton.agent.model.AgentAction

class AgentReasoner(private val backend: LlmBackend) {
    suspend fun reason(userGoal: String, serializedScreen: String): AgentAction? {
        val prompt = GemmaAgentEngine.buildSystemPrompt(userGoal, serializedScreen)
        val raw = backend.generate(prompt)
        return GemmaAgentEngine.parseAction(raw)
    }
}
