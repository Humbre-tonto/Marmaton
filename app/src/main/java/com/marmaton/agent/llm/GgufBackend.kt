package com.marmaton.agent.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GgufBackend(
    private val context: Context,
    private val modelPath: String?
) : LlmBackend {
    override val displayName: String = "Local GGUF Model File"

    private var modelHandle: Long = 0L

    companion object {
        init {
            try {
                System.loadLibrary("marmaton_llm")
            } catch (e: UnsatisfiedLinkError) {
                System.err.println("GgufBackend: Failed to load marmaton_llm native library: ${e.message}")
            } catch (e: Throwable) {
                System.err.println("GgufBackend: Error initializing native library: ${e.message}")
            }
        }

        @JvmStatic
        private external fun load(modelPath: String, nCtx: Int, nThreads: Int): Long

        @JvmStatic
        private external fun generate(handle: Long, prompt: String, maxTokens: Int): String

        @JvmStatic
        private external fun free(handle: Long)
    }

    private suspend fun initEngine() = withContext(Dispatchers.IO) {
        if (modelPath.isNullOrBlank()) {
            throw IllegalStateException("No GGUF model file has been imported yet.")
        }
        val file = File(modelPath)
        if (!file.exists()) {
            throw IllegalStateException("Imported GGUF model file does not exist at $modelPath")
        }

        if (modelHandle != 0L) {
            return@withContext
        }

        try {
            modelHandle = load(modelPath, 4096, 4)
            if (modelHandle == 0L) {
                throw IllegalStateException("Failed to load native GGUF model.")
            }
        } catch (e: Exception) {
            Log.e("GgufBackend", "Error initialising GGUF native engine", e)
            throw e
        }
    }

    override suspend fun status(): BackendStatus = withContext(Dispatchers.IO) {
        if (modelPath.isNullOrBlank()) {
            return@withContext BackendStatus.NotReady("No model file selected or imported. Please select one in Settings.")
        }
        val file = File(modelPath)
        if (!file.exists()) {
            return@withContext BackendStatus.NotReady("Selected model file does not exist. Please re-import.")
        }
        return@withContext try {
            initEngine()
            BackendStatus.Ready
        } catch (e: Exception) {
            BackendStatus.Unavailable("Failed to load GGUF model: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        initEngine()
        if (modelHandle == 0L) {
            throw IllegalStateException("GGUF native model handle is invalid.")
        }
        try {
            generate(modelHandle, prompt, 256)
        } catch (e: Exception) {
            Log.e("GgufBackend", "Error during JNI text generation", e)
            throw e
        }
    }

    override fun close() {
        if (modelHandle != 0L) {
            try {
                free(modelHandle)
            } catch (e: Exception) {
                Log.e("GgufBackend", "Error freeing GGUF native engine handle", e)
            } finally {
                modelHandle = 0L
            }
        }
    }
}
