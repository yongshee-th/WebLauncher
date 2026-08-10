package com.yongsheeth.weblauncher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "launcher_settings_v3")

enum class SourceType {
    ASSETS, LOCAL, GITHUB
}

@Serializable
data class ProjectItem(
    val name: String,
    val uri: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class SettingsManager(private val context: Context) {

    companion object {
        val SOURCE_TYPE = stringPreferencesKey("source_type")
        val SELECTED_PROJECT_URI = stringPreferencesKey("selected_project_uri")
        val GITHUB_URL = stringPreferencesKey("github_url")
        val RECENT_PROJECTS = stringPreferencesKey("recent_projects")
        val IS_FLOATING_BUTTON_ENABLED = booleanPreferencesKey("is_floating_button_enabled")
        val WEB_PERSISTENT_DATA = stringPreferencesKey("web_persistent_data")
        val IS_REMOTE_DEBUGGING_ENABLED = booleanPreferencesKey("is_remote_debugging_enabled")
    }

    val sourceType: Flow<SourceType> = context.dataStore.data.map { preferences ->
        SourceType.valueOf(preferences[SOURCE_TYPE] ?: SourceType.ASSETS.name)
    }

    val selectedProjectUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_PROJECT_URI]
    }

    val githubUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GITHUB_URL]
    }

    val recentProjects: Flow<List<ProjectItem>> = context.dataStore.data.map { preferences ->
        val json = preferences[RECENT_PROJECTS] ?: "[]"
        try {
            Json.decodeFromString<List<ProjectItem>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val isFloatingButtonEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_FLOATING_BUTTON_ENABLED] ?: false
    }

    val isRemoteDebuggingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_REMOTE_DEBUGGING_ENABLED] ?: false
    }

    suspend fun updateSource(sourceType: SourceType, uri: String? = null, url: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[SOURCE_TYPE] = sourceType.name
            uri?.let { preferences[SELECTED_PROJECT_URI] = it }
            url?.let { preferences[GITHUB_URL] = it }
        }
    }

    suspend fun toggleFloatingButton(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_FLOATING_BUTTON_ENABLED] = enabled
        }
    }

    suspend fun toggleRemoteDebugging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_REMOTE_DEBUGGING_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun setPersistentData(key: String, value: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[WEB_PERSISTENT_DATA] ?: "{}"
            val data = try {
                Json.decodeFromString<MutableMap<String, String>>(currentJson)
            } catch (e: Exception) {
                mutableMapOf()
            }
            data[key] = value
            preferences[WEB_PERSISTENT_DATA] = Json.encodeToString(data)
        }
    }

    val persistentData: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val json = preferences[WEB_PERSISTENT_DATA] ?: "{}"
        try {
            Json.decodeFromString<Map<String, String>>(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun addProjectToHistory(project: ProjectItem) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[RECENT_PROJECTS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<ProjectItem>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // Remove if already exists (to update timestamp/position)
            currentList.removeAll { it.uri == project.uri }
            currentList.add(0, project)
            
            // Limit history size
            val limitedList = currentList.take(10)
            preferences[RECENT_PROJECTS] = Json.encodeToString(limitedList)
        }
    }

    suspend fun removeProjectFromHistory(uri: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[RECENT_PROJECTS] ?: "[]"
            val currentList = try {
                Json.decodeFromString<List<ProjectItem>>(currentJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            currentList.removeAll { it.uri == uri }
            preferences[RECENT_PROJECTS] = Json.encodeToString(currentList)
        }
    }
}
