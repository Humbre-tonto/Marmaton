package com.marmaton.agent.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class LocalFileBackend(
    private val context: Context,
    private val modelPath: String?
) : LlmBackend {
    override val displayName: String = "Local Model File"

    private var llmInference: LlmInference? = null
    private var lastLoadedPath: String? = null

    @Synchronized
    private fun initEngine(): LlmInference {
        if (modelPath.isNullOrBlank()) {
            throw IllegalStateException("No model file has been imported yet.")
        }
        val file = File(modelPath)
        if (!file.exists()) {
            throw IllegalStateException("Imported model file does not exist at $modelPath")
        }

        if (llmInference != null && lastLoadedPath == modelPath) {
            return llmInference!!
        }

        // Close old instance if any
        close()

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            lastLoadedPath = modelPath
            return llmInference!!
        } catch (e: Exception) {
            Log.e("LocalFileBackend", "Failed to initialize MediaPipe LlmInference", e)
            throw e
        } catch (e: UnsatisfiedLinkError) {
            Log.e("LocalFileBackend", "UnsatisfiedLinkError (likely JVM test environment)", e)
            throw IllegalStateException("Native MediaPipe library not available", e)
        }
    }

    override suspend fun status(): BackendStatus {
        if (modelPath.isNullOrBlank()) {
            return BackendStatus.NotReady("No model file selected or imported. Please select one in Settings.")
        }
        val file = File(modelPath)
        if (!file.exists()) {
            return BackendStatus.NotReady("Selected model file does not exist. Please re-import.")
        }
        return try {
            initEngine()
            BackendStatus.Ready
        } catch (e: Exception) {
            BackendStatus.Unavailable("Failed to load model: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String): String {
        val engine = initEngine()
        return try {
            engine.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e("LocalFileBackend", "Error during generation", e)
            throw e
        }
    }

    override fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e("LocalFileBackend", "Error closing inference", e)
        } finally {
            llmInference = null
            lastLoadedPath = null
        }
    }
}
