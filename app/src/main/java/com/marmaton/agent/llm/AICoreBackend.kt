package com.marmaton.agent.llm

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart

class AICoreBackend : LlmBackend {
    override val displayName: String = "AICore (Gemini Nano)"

    private var generativeModel: GenerativeModel? = null

    init {
        try {
            generativeModel = Generation.getClient()
        } catch (e: Exception) {
            Log.e("AICoreBackend", "Error initializing Generation client", e)
        }
    }

    override suspend fun status(): BackendStatus {
        val model = generativeModel ?: return BackendStatus.Unavailable("AICore not supported or initialized")
        return try {
            val status = model.checkStatus()
            when (status) {
                FeatureStatus.UNAVAILABLE -> BackendStatus.Unavailable("Gemma 4 is unavailable on this device configuration.")
                FeatureStatus.DOWNLOADABLE -> BackendStatus.NotReady("Gemma 4 is ready to be downloaded.")
                FeatureStatus.DOWNLOADING -> BackendStatus.NotReady("Gemma 4 is downloading...")
                FeatureStatus.AVAILABLE -> BackendStatus.Ready
                else -> BackendStatus.Unavailable("Unknown feature status: $status")
            }
        } catch (e: Exception) {
            BackendStatus.Unavailable("Status check error: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String): String {
        val model = generativeModel ?: throw IllegalStateException("AICore client not initialized")
        val rawRequest = GenerateContentRequest.Builder(TextPart(prompt)).build()
        val rawResponse = model.generateContent(rawRequest)
        return rawResponse.candidates.firstOrNull()?.text ?: ""
    }
}
