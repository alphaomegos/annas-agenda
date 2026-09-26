@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal const val APP_STATE_STORE_NAME = "app_state_store"

/**
 * A DataStore file that could not be parsed at all, along with the copy taken
 * of it before DataStore replaced it.
 */
internal data class AppStateFileCorruption(
    val cause: Throwable,
    val quarantineFile: File?,
)

/**
 * Carries the fact that DataStore hit an unparseable file from the corruption
 * handler — which runs deep inside DataStore and can return nothing but
 * replacement data — out to [AppStateStore.load].
 *
 * Without it a corrupt file would look exactly like an empty one: the same
 * confusion that used to lose the user's data one level up, at the JSON layer.
 */
internal object AppStateStoreCorruption {

    @Volatile
    private var pending: AppStateFileCorruption? = null

    fun record(context: Context, file: File, cause: Throwable) {
        pending = AppStateFileCorruption(
            cause = cause,
            quarantineFile = quarantineCorruptFile(context, file),
        )
    }

    /** Returns the pending record, if any, and clears it. */
    fun consume(): AppStateFileCorruption? {
        val current = pending
        pending = null
        return current
    }

    fun clear() {
        pending = null
    }
}

/**
 * Builds a Preferences DataStore over [file] that survives an unparseable file
 * instead of throwing on every read for the rest of the process lifetime.
 *
 * The handler copies the bad file aside *before* returning replacement data,
 * because DataStore overwrites the original as soon as this returns.
 */
internal fun buildAppStateDataStore(context: Context, file: File): DataStore<Preferences> {
    val app = context.applicationContext

    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { cause ->
            AppStateStoreCorruption.record(app, file, cause)
            emptyPreferences()
        },
        produceFile = { file },
    )
}

private val appStateDataStoreLock = Any()

@Volatile
private var appStateDataStoreInstance: DataStore<Preferences>? = null

/**
 * The process-wide DataStore for app state. A single instance is mandatory:
 * DataStore throws if two are created over the same file.
 */
internal fun appStateDataStore(context: Context): DataStore<Preferences> {
    val app = context.applicationContext

    return synchronized(appStateDataStoreLock) {
        appStateDataStoreInstance ?: buildAppStateDataStore(
            context = app,
            file = app.preferencesDataStoreFile(APP_STATE_STORE_NAME),
        ).also { appStateDataStoreInstance = it }
    }
}

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
     * There is data on disk and this build must not write over it.
     *
     * The two reasons differ in what the user should do about it, which is why
     * they are told apart, but they are handled identically everywhere else:
     * autosave never starts, so whatever is on disk stays exactly as it is.
     */
    sealed interface Failed : AppStateLoadResult

    /**
     * Saved data exists but could not be read — either the DataStore file
     * itself is unparseable, or the JSON inside it is.
     *
     * [quarantineFile] is a copy of whatever could be salvaged, so the data can
     * still be recovered by hand. It is null only if even that copy failed.
     */
    data class Corrupted(
        val cause: Throwable,
        val quarantineFile: File?,
    ) : Failed

    /**
     * The data was written by a newer version of the app.
     *
     * Nothing is wrong with it — this build simply cannot read it without
     * throwing away the parts it does not know. There is nothing to salvage
     * and nothing to quarantine: the payload is intact and the newer version
     * will read it as it always did.
     */
    data class TooNew(
        val payloadVersion: Int,
        val supportedVersion: Int,
    ) : Failed
}

class AppStateStore internal constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context, appStateDataStore(context))

    private val key = stringPreferencesKey(APP_STATE_KEY_NAME)

    fun encodeToJson(state: AppState): String =
        appStateStoreJson.encodeToString(state.toDto())

    /**
     * Reads a payload that came from outside this device — an imported archive.
     *
     * No week start is passed on purpose: the rules inside were written under
     * somebody else's language, and this device's is not evidence of what that
     * was. See migrateAppState3To4.
     */
    fun decodeFromJson(raw: String): AppState? =
        when (val result = decodeAppStateJsonOrFailure(raw)) {
            is AppStateDecodeResult.Success -> result.state
            is AppStateDecodeResult.Failure -> null
        }

    suspend fun load(): AppStateLoadResult = withContext(Dispatchers.IO) {
        val prefs = try {
            dataStore.data.first()
        } catch (e: CancellationException) {
            // Being cancelled is not a failure to read; it must not be
            // reported as one, and the rest of this must not run on a dead
            // coroutine.
            throw e
        } catch (e: Throwable) {
            // Everything, not only IOException.
            //
            // The corruption handler covers an unparseable file, and an
            // IOException covers permissions and a truncated read. What used
            // to be left out was everything else — and "everything else" is
            // real: DataStore throws IllegalStateException when two instances
            // are opened over one file, which is a mistake in our code rather
            // than in the user's data, and exactly the sort of mistake that
            // must not vanish.
            //
            // It did vanish. The throw left load(), left the launch in the
            // view model's init, and killed that coroutine with nobody
            // listening: isLoaded stayed false for ever and storageFailure
            // stayed null. On a phone that is a screen that never opens and
            // says nothing. Reported as unreadable, it is the storage-failure
            // screen, autosave stays off, and the payload is left untouched —
            // which is the right answer whatever the cause turns out to be.
            return@withContext AppStateLoadResult.Corrupted(
                cause = e,
                quarantineFile = AppStateStoreCorruption.consume()?.quarantineFile,
            )
        }

        val fileCorruption = AppStateStoreCorruption.consume()
        if (fileCorruption != null) {
            return@withContext AppStateLoadResult.Corrupted(
                cause = fileCorruption.cause,
                quarantineFile = fileCorruption.quarantineFile,
            )
        }

        val raw = prefs[key] ?: return@withContext AppStateLoadResult.Empty

        // Upgrading our own payload in place: the app's current language is
        // the one its schedules were built under, so it is the right answer for
        // rules that predate the week start being recorded.
        val decoded = decodeAppStateJsonOrFailure(
            raw = raw,
            weekStartForLegacyRules = currentLocaleWeekStart(),
        )

        when (decoded) {
            is AppStateDecodeResult.Success -> AppStateLoadResult.Loaded(decoded.state)

            is AppStateDecodeResult.Failure -> when (val cause = decoded.cause) {
                is AppStateTooNewException -> AppStateLoadResult.TooNew(
                    payloadVersion = cause.payloadVersion,
                    supportedVersion = cause.supportedVersion,
                )

                else -> AppStateLoadResult.Corrupted(
                    cause = cause,
                    quarantineFile = quarantineCorruptText(context, raw),
                )
            }
        }
    }

    /**
     * Writes the state, minus the future occurrences that loading it back and
     * drawing the day would produce again.
     *
     * Deliberately here and not in [encodeToJson]: an export is read by a
     * device whose language may put the week boundary somewhere else, and a
     * rule saved before the week start was recorded would then regenerate onto
     * different days. An archive keeps everything.
     *
     * The state held in memory is untouched, so nothing on screen moves and
     * ids stay put for as long as the app is running.
     */
    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val raw = encodeToJson(stateWithDerivableOccurrencesDropped(state))
        dataStore.edit { prefs ->
            prefs[key] = raw
        }
    }

    companion object {
        internal const val APP_STATE_KEY_NAME = "app_state_json"
        internal const val QUARANTINE_DIR_NAME = "corrupt_state"
        internal const val MAX_QUARANTINE_FILES = 5
    }
}

/**
 * Copies an unreadable JSON payload aside, verbatim, so that a parsing bug can
 * never destroy the user's data. Best-effort: a failure here must not stop the
 * app from starting.
 */
internal fun quarantineCorruptText(context: Context, raw: String): File? = runCatching {
    val dir = quarantineDir(context).apply { mkdirs() }
    val file = File(dir, "app_state_${System.currentTimeMillis()}.json")
    file.writeText(raw)
    trimQuarantine(dir)
    file
}.getOrNull()

/** Same, for a DataStore file that could not be parsed as Preferences at all. */
internal fun quarantineCorruptFile(context: Context, source: File): File? = runCatching {
    val dir = quarantineDir(context).apply { mkdirs() }
    val file = File(dir, "app_state_${System.currentTimeMillis()}.preferences_pb")
    source.copyTo(file, overwrite = true)
    trimQuarantine(dir)
    file
}.getOrNull()

private fun quarantineDir(context: Context): File =
    File(context.filesDir, AppStateStore.QUARANTINE_DIR_NAME)

/** Keeps only the newest [AppStateStore.MAX_QUARANTINE_FILES] copies. */
private fun trimQuarantine(dir: File) {
    val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
    files.drop(AppStateStore.MAX_QUARANTINE_FILES).forEach { runCatching { it.delete() } }
}
