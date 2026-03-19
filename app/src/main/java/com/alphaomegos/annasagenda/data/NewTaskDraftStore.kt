package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.newTaskDraftDataStore by preferencesDataStore(name = "new_task_draft_store")

@Serializable
data class NewTaskDraft(
    val description: String = "",
    val taskColorArgb: Long? = null,
    val subtasks: List<NewTaskDraftSubtask> = emptyList(),
) {
    fun isEffectivelyEmpty(): Boolean {
        val hasText = description.isNotBlank() || subtasks.any { it.description.isNotBlank() }
        val hasColors =
            taskColorArgb != null || subtasks.any { it.colorArgb != null || it.colorOverridden }
        return !hasText && !hasColors
    }
}

@Serializable
data class NewTaskDraftSubtask(
    val description: String = "",
    val colorArgb: Long? = null,
    val colorOverridden: Boolean = false,
)

class NewTaskDraftStore(private val context: Context) {
    private val key = stringPreferencesKey("new_task_draft_json_v1")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun load(): NewTaskDraft? = withContext(Dispatchers.IO) {
        val raw = context.newTaskDraftDataStore.data.first()[key] ?: return@withContext null
        runCatching { json.decodeFromString<NewTaskDraft>(raw) }.getOrNull()
    }

    suspend fun save(draft: NewTaskDraft) = withContext(Dispatchers.IO) {
        context.newTaskDraftDataStore.edit { prefs ->
            if (draft.isEffectivelyEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(draft)
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        context.newTaskDraftDataStore.edit { it.remove(key) }
    }
}
