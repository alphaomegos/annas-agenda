@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.appStateDataStore by preferencesDataStore(name = "app_state_store")

class AppStateStore(private val context: Context) {

    private val key = stringPreferencesKey("app_state_json")

    fun encodeToJson(state: AppState): String =
        appStateStoreJson.encodeToString(state.toDto())

    fun decodeFromJson(raw: String): AppState? =
        runCatching {
            val migrated = migrateAppStateRawJson(raw)
            val dto = appStateStoreJson.decodeFromJsonElement(AppStateDto.serializer(), migrated)
            dto.toDomain()
        }.getOrNull()


    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        val raw = context.appStateDataStore.data.first()[key] ?: return@withContext AppState()
        decodeFromJson(raw) ?: AppState()
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val raw = encodeToJson(state)
        context.appStateDataStore.edit { prefs ->
            prefs[key] = raw
        }
    }
}
