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

    // Guards modelHandle: status() (polled from the UI) and generate() (from the agent/chat loop)
    // run on the same cached instance from different threads. Without this, two threads can both
    // native-load(), or close() can free() the handle mid-load (use-after-free → native crash).
    private val lock = Any()
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

        // NOT @JvmStatic: the JNI functions in llama-jni.cpp are exported for the companion
        // object (Java_com_marmaton_agent_llm_GgufBackend_00024Companion_load). @JvmStatic would
        // hoist these onto the GgufBackend class, so the runtime would look for
        // Java_com_marmaton_agent_llm_GgufBackend_load — which doesn't exist → UnsatisfiedLinkError
        // ("No implementation found for ... load"). Keeping them as companion methods matches the
        // native symbol names.
        private external fun load(modelPath: String, nCtx: Int, nThreads: Int): Long

        private external fun generate(handle: Long, prompt: String, maxTokens: Int): String

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

        synchronized(lock) {
            if (modelHandle != 0L) {
                return@withContext
            }
            try {
                val handle = load(modelPath, 4096, 4)
                if (handle == 0L) {
                    throw IllegalStateException("Failed to load native GGUF model.")
                }
                modelHandle = handle
            } catch (e: Exception) {
                Log.e("GgufBackend", "Error initialising GGUF native engine", e)
                throw e
            }
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
        val handle = synchronized(lock) { modelHandle }
        if (handle == 0L) {
            throw IllegalStateException("GGUF native model handle is invalid.")
        }
        try {
            generate(handle, prompt, 256)
        } catch (e: Exception) {
            Log.e("GgufBackend", "Error during JNI text generation", e)
            throw e
        }
    }

    override fun close() {
        synchronized(lock) {
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
}
