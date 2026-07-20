package com.marmaton.agent.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.marmaton.agent.util.FileLogger
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

        FileLogger.log("LocalBackend", "Loading MediaPipe model: $modelPath (${file.length()} bytes)")
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            lastLoadedPath = modelPath
            FileLogger.log("LocalBackend", "MediaPipe model loaded OK")
            return llmInference!!
        } catch (e: Exception) {
            Log.e("LocalFileBackend", "Failed to initialize MediaPipe LlmInference", e)
            FileLogger.log("LocalBackend", "Model load FAILED: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        } catch (e: UnsatisfiedLinkError) {
            Log.e("LocalFileBackend", "UnsatisfiedLinkError (likely JVM test environment)", e)
            FileLogger.log("LocalBackend", "Native MediaPipe library not available: ${e.message}")
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
            val msg = e.message ?: ""
            val friendly = if (msg.contains("zip archive", ignoreCase = true) || msg.contains("104")) {
                "This model file can't be opened by the on-device engine — it's likely a web build or an incompatible/corrupted .task. Delete it and download a different model (Gemma 3 1B works)."
            } else {
                "Failed to load model: $msg"
            }
            BackendStatus.Unavailable(friendly)
        }
    }

    override suspend fun generate(prompt: String): String {
        val engine = initEngine()
        return try {
            val start = System.currentTimeMillis()
            FileLogger.log("LocalBackend", "generate() start, prompt chars=${prompt.length}")
            val response = engine.generateResponse(prompt)
            val ms = System.currentTimeMillis() - start
            FileLogger.log(
                "LocalBackend",
                "generate() done in ${ms}ms, response chars=${response.length}: " +
                    response.take(400).replace("\n", " ")
            )
            response
        } catch (e: Exception) {
            Log.e("LocalFileBackend", "Error during generation", e)
            FileLogger.log("LocalBackend", "generate() FAILED: ${e.javaClass.simpleName}: ${e.message}")
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
