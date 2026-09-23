@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

internal val Context.appStateDataStore by preferencesDataStore(name = "app_state_store")

/**
 * Outcome of reading persisted state at startup.
 *
 * [Empty] and [Corrupted] must never be treated the same way: the first means
 * "nothing was ever saved", the second means "the user's data is on disk but we
 * could not parse it". Writing over the second one loses everything.
 */
sealed interface AppStateLoadResult {

    /** Nothing has ever been persisted — a genuine first launch. */
    data object Empty : AppStateLoadResult

    data class Loaded(val state: AppState) : AppStateLoadResult

    /**
     * A payload exists but could not be decoded.
     *
     * [quarantineFile] is a verbatim copy of the unreadable payload, so the
     * data can still be recovered by hand. It is null only if even that copy
     * could not be written.
     */
    data class Corrupted(
        val cause: Throwable,
        val quarantineFile: File?,
    ) : AppStateLoadResult
}

class AppStateStore(private val context: Context) {

    private val key = stringPreferencesKey(APP_STATE_KEY_NAME)

    fun encodeToJson(state: AppState): String =
        appStateStoreJson.encodeToString(state.toDto())

    fun decodeFromJson(raw: String): AppState? =
        when (val result = decodeAppStateJsonOrFailure(raw)) {
            is AppStateDecodeResult.Success -> result.state
            is AppStateDecodeResult.Failure -> null
        }

    suspend fun load(): AppStateLoadResult = withContext(Dispatchers.IO) {
        val raw = context.appStateDataStore.data.first()[key]
            ?: return@withContext AppStateLoadResult.Empty

        when (val result = decodeAppStateJsonOrFailure(raw)) {
            is AppStateDecodeResult.Success -> AppStateLoadResult.Loaded(result.state)

            is AppStateDecodeResult.Failure -> AppStateLoadResult.Corrupted(
                cause = result.cause,
                quarantineFile = quarantineUnreadablePayload(raw),
            )
        }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val raw = encodeToJson(state)
        context.appStateDataStore.edit { prefs ->
            prefs[key] = raw
        }
    }

    /**
     * Copies an unreadable payload aside, verbatim, so that a parsing bug can
     * never destroy the user's data. Best-effort: a failure here must not stop
     * the app from starting.
     */
    private fun quarantineUnreadablePayload(raw: String): File? = runCatching {
        val dir = File(context.filesDir, QUARANTINE_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "app_state_${System.currentTimeMillis()}.json")
        file.writeText(raw)
        trimQuarantine(dir)
        file
    }.getOrNull()

    /** Keeps only the newest [MAX_QUARANTINE_FILES] copies. */
    private fun trimQuarantine(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_QUARANTINE_FILES).forEach { runCatching { it.delete() } }
    }

    companion object {
        internal const val APP_STATE_KEY_NAME = "app_state_json"
        internal const val QUARANTINE_DIR_NAME = "corrupt_state"
        internal const val MAX_QUARANTINE_FILES = 5
    }
}
