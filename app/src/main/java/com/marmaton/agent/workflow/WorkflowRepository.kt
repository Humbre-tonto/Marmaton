package com.marmaton.agent.workflow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.workflowDataStore: DataStore<Preferences> by preferencesDataStore(name = "marmaton_workflows")

/**
 * Persists user [Workflow]s as a single JSON blob in a dedicated DataStore. Kept separate from
 * [com.marmaton.agent.llm.SettingsPersistence] so workflow edits never race backend settings.
 */
class WorkflowRepository(
    context: Context,
    customDataStore: DataStore<Preferences>? = null
) {
    private val dataStore = customDataStore ?: context.workflowDataStore
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_WORKFLOWS_JSON = stringPreferencesKey("workflows_json")
    }

    val workflowsFlow: Flow<List<Workflow>> = dataStore.data.map { preferences ->
        decode(preferences[KEY_WORKFLOWS_JSON])
    }

    /** Insert a new workflow or replace the existing one with the same id. */
    suspend fun upsert(workflow: Workflow) {
        dataStore.edit { preferences ->
            val current = decode(preferences[KEY_WORKFLOWS_JSON])
            val next = current.filterNot { it.id == workflow.id } + workflow
            preferences[KEY_WORKFLOWS_JSON] = json.encodeToString(next)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[KEY_WORKFLOWS_JSON])
            preferences[KEY_WORKFLOWS_JSON] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    private fun decode(raw: String?): List<Workflow> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Workflow>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
