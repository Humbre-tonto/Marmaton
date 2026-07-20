package com.marmaton.agent.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "marmaton_settings")

enum class BackendType {
    LOCAL_FILE, OLLAMA, CLOUD, AICORE
}

data class BackendConfig(
    val selectedType: BackendType = BackendType.LOCAL_FILE,
    val localModelFilePath: String = "",
    val localModelUri: String = "",
    val localModelFileName: String = "",
    val ollamaScheme: String = "http",
    val ollamaHost: String = "10.0.2.2",
    val ollamaPort: Int = 11434,
    val ollamaModel: String = "gemma",
    val cloudBaseUrl: String = "https://api.openai.com",
    val cloudModel: String = "gpt-4o-mini",
    val isOnboardingCompleted: Boolean = false,
    val analyticsConsent: Boolean = false,
    val firstRunTracked: Boolean = false,
    val voiceEnabled: Boolean = false
)

class SettingsPersistence(
    private val context: Context,
    private val customDataStore: DataStore<Preferences>? = null
) {
    private val dataStore = customDataStore ?: context.dataStore

    companion object {
        private val KEY_SELECTED_TYPE = stringPreferencesKey("selected_backend")
        private val KEY_LOCAL_MODEL_FILE_PATH = stringPreferencesKey("local_model_file_path")
        private val KEY_LOCAL_MODEL_URI = stringPreferencesKey("local_model_uri")
        private val KEY_LOCAL_MODEL_FILE_NAME = stringPreferencesKey("local_model_file_name")
        private val KEY_OLLAMA_SCHEME = stringPreferencesKey("ollama_scheme")
        private val KEY_OLLAMA_HOST = stringPreferencesKey("ollama_host")
        private val KEY_OLLAMA_PORT = intPreferencesKey("ollama_port")
        private val KEY_OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        private val KEY_CLOUD_BASE_URL = stringPreferencesKey("cloud_base_url")
        private val KEY_CLOUD_MODEL = stringPreferencesKey("cloud_model")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
        private val KEY_FIRST_RUN_TRACKED = booleanPreferencesKey("first_run_tracked")
        private val KEY_VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
    }

    val configFlow: Flow<BackendConfig> = dataStore.data.map { preferences ->
        val typeStr = preferences[KEY_SELECTED_TYPE] ?: BackendType.LOCAL_FILE.name
        val selectedType = try {
            BackendType.valueOf(typeStr)
        } catch (e: Exception) {
            BackendType.LOCAL_FILE
        }

        BackendConfig(
            selectedType = selectedType,
            localModelFilePath = preferences[KEY_LOCAL_MODEL_FILE_PATH] ?: "",
            localModelUri = preferences[KEY_LOCAL_MODEL_URI] ?: "",
            localModelFileName = preferences[KEY_LOCAL_MODEL_FILE_NAME] ?: "",
            ollamaScheme = preferences[KEY_OLLAMA_SCHEME] ?: "http",
            ollamaHost = preferences[KEY_OLLAMA_HOST] ?: "10.0.2.2",
            ollamaPort = preferences[KEY_OLLAMA_PORT] ?: 11434,
            ollamaModel = preferences[KEY_OLLAMA_MODEL] ?: "gemma",
            cloudBaseUrl = preferences[KEY_CLOUD_BASE_URL] ?: "https://api.openai.com",
            cloudModel = preferences[KEY_CLOUD_MODEL] ?: "gpt-4o-mini",
            isOnboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false,
            analyticsConsent = preferences[KEY_ANALYTICS_CONSENT] ?: false,
            firstRunTracked = preferences[KEY_FIRST_RUN_TRACKED] ?: false,
            voiceEnabled = preferences[KEY_VOICE_ENABLED] ?: false
        )
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun updateSelectedType(type: BackendType) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_TYPE] = type.name
        }
    }

    suspend fun updateLocalModel(filePath: String, uri: String, fileName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LOCAL_MODEL_FILE_PATH] = filePath
            preferences[KEY_LOCAL_MODEL_URI] = uri
            preferences[KEY_LOCAL_MODEL_FILE_NAME] = fileName
        }
    }

    suspend fun updateOllamaConfig(scheme: String, host: String, port: Int, model: String) {
        dataStore.edit { preferences ->
            preferences[KEY_OLLAMA_SCHEME] = scheme
            preferences[KEY_OLLAMA_HOST] = host
            preferences[KEY_OLLAMA_PORT] = port
            preferences[KEY_OLLAMA_MODEL] = model
        }
    }

    suspend fun updateCloudConfig(baseUrl: String, model: String) {
        dataStore.edit { preferences ->
            preferences[KEY_CLOUD_BASE_URL] = baseUrl
            preferences[KEY_CLOUD_MODEL] = model
        }
    }

    suspend fun updateAnalyticsConsent(consent: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ANALYTICS_CONSENT] = consent
        }
    }

    suspend fun updateVoiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_VOICE_ENABLED] = enabled
        }
    }

    suspend fun updateFirstRunTracked(tracked: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_FIRST_RUN_TRACKED] = tracked
        }
    }
}
