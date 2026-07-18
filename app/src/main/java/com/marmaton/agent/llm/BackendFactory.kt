package com.marmaton.agent.llm

import android.content.Context
import kotlinx.coroutines.flow.first

object BackendFactory {
    private val lock = Any()
    private var cachedBackend: LlmBackend? = null
    private var cachedConfig: BackendConfig? = null

    suspend fun createActiveBackend(context: Context): LlmBackend {
        val persistence = SettingsPersistence(context)
        val config = persistence.configFlow.first()

        synchronized(lock) {
            val currentCached = cachedBackend
            if (currentCached != null && cachedConfig == config) {
                return currentCached
            }

            closeCurrentLocked()

            val newBackend = createBackend(context, config)
            cachedBackend = newBackend
            cachedConfig = config
            return newBackend
        }
    }

    fun closeCurrent() {
        synchronized(lock) {
            closeCurrentLocked()
        }
    }

    private fun closeCurrentLocked() {
        try {
            cachedBackend?.close()
        } catch (e: Exception) {
            // Safe fallback
        }
        cachedBackend = null
        cachedConfig = null
    }

    fun createBackend(context: Context, config: BackendConfig): LlmBackend {
        return when (config.selectedType) {
            BackendType.LOCAL_FILE -> {
                LocalFileBackend(context, config.localModelFilePath)
            }
            BackendType.OLLAMA -> {
                OllamaBackend(
                    scheme = config.ollamaScheme,
                    host = config.ollamaHost,
                    port = config.ollamaPort,
                    modelName = config.ollamaModel
                )
            }
            BackendType.CLOUD -> {
                val apiKey = SecurePreferences.getApiKey(context)
                CloudBackend(
                    baseUrl = config.cloudBaseUrl,
                    apiKey = apiKey,
                    modelName = config.cloudModel
                )
            }
            BackendType.AICORE -> {
                AICoreBackend()
            }
        }
    }
}
