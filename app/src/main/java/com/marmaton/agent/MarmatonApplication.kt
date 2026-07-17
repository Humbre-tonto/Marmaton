package com.marmaton.agent

import android.app.Application
import com.marmaton.agent.analytics.Analytics
import com.marmaton.agent.llm.BackendConfig
import com.marmaton.agent.llm.BackendType
import com.marmaton.agent.llm.SettingsPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MarmatonApplication : Application() {
    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Analytics.init(this, false)

        // Run background configuration collection to sync consent, track first-run and track changes
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val persistence = SettingsPersistence(this@MarmatonApplication)
            var lastConsent: Boolean? = null

            persistence.configFlow.collect { config ->
                val currentConsent = config.analyticsConsent
                if (currentConsent != lastConsent) {
                    lastConsent = currentConsent
                    Analytics.get().setEnabled(currentConsent)
                }

                if (currentConsent && !config.firstRunTracked) {
                    Analytics.get().trackFirstRun()
                    persistence.updateFirstRunTracked(true)
                }
            }
        }

        scope.launch {
            val persistence = SettingsPersistence(this@MarmatonApplication)
            var lastType: String? = null
            var lastModel: String? = null
            var lastHost: String? = null

            persistence.configFlow
                .debounce(1500)
                .collect { config ->
                    val (type, modelName, providerHost) = getBackendSelectedDetails(config)
                    if (type != lastType || modelName != lastModel || providerHost != lastHost) {
                        Analytics.get().trackBackendSelected(type, modelName, providerHost)
                        lastType = type
                        lastModel = modelName
                        lastHost = providerHost
                    }
                }
        }
    }
}

fun getBackendSelectedDetails(config: BackendConfig): Triple<String, String, String?> {
    val type = when (config.selectedType) {
        BackendType.LOCAL_FILE, BackendType.AICORE -> "on_device"
        BackendType.OLLAMA -> "ollama"
        BackendType.CLOUD -> "cloud"
    }
    val modelName = when (config.selectedType) {
        BackendType.LOCAL_FILE -> {
            if (config.localModelFileName.isNotBlank()) {
                config.localModelFileName
            } else {
                val path = config.localModelFilePath
                if (path.isBlank()) {
                    "unknown"
                } else {
                    val file = java.io.File(path)
                    file.name.ifBlank { "unknown" }
                }
            }
        }
        BackendType.AICORE -> "gemini-nano"
        BackendType.OLLAMA -> config.ollamaModel.ifBlank { "unknown" }
        BackendType.CLOUD -> config.cloudModel.ifBlank { "unknown" }
    }
    val providerHost = if (config.selectedType == BackendType.CLOUD) {
        extractHost(config.cloudBaseUrl)
    } else {
        null
    }
    return Triple(type, modelName, providerHost)
}

fun extractHost(urlStr: String): String? {
    if (urlStr.isBlank()) return null
    return try {
        val uri = java.net.URI(urlStr)
        val host = uri.host
        if (host != null) {
            host
        } else {
            // fallback: string parsing
            val withoutScheme = if (urlStr.contains("://")) urlStr.substringAfter("://") else urlStr
            val pathOrPort = withoutScheme.substringBefore("/")
            if (pathOrPort.contains(":")) pathOrPort.substringBefore(":") else pathOrPort
        }
    } catch (e: Exception) {
        null
    }
}
