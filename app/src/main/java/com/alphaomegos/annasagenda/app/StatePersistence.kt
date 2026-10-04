package com.alphaomegos.annasagenda.app

import android.content.Context
import androidx.core.net.toUri
import com.alphaomegos.annasagenda.util.AUTO_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.appBackgroundScope
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isExternalCoverRef
import com.alphaomegos.annasagenda.util.resolveStoredCoverFiles
import com.alphaomegos.annasagenda.util.writeBackupToDocuments
import com.alphaomegos.annasagenda.util.writeInternalCoverBytes
import kotlinx.coroutines.launch
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

/**
 * Everything the app keeps outside memory, in one place (04.10, step 22 of
 * the old refactor plan).
 *
 * The view model decides **when** — it owns the state, the id counter, the
 * "loaded" and "failed" flags, and the order of things (an import is written
 * before it is shown). This class only knows **how**: the state file, the
 * new-task draft, the backup archives in Documents, and the cover files that
 * travel with them. It holds no state of its own, so there is nothing here
 * that could disagree with the view model.
 *
 * Every write of the state goes through [save] with the id counter's position,
 * for the reason given at nextIdFor: the mark cannot live in the state alone,
 * because callers snapshot the state, take ids, and write the snapshot back.
 */
internal class StatePersistence(private val context: Context) {

    private val store = AppStateStore(context)
    private val draftStore = NewTaskDraftStore(context)

    /* ---------------- the state file ---------------- */

    /** Answers rather than throws; see AppStateLoadResult for the outcomes. */
    suspend fun load(): AppStateLoadResult = store.load()

    suspend fun save(state: AppState, nextId: Long) {
        store.save(stateWithIdHighWaterAtLeast(state, nextId))
    }

    /* ---------------- backups ---------------- */

    /** The archive's JSON, carrying the id counter's position too. */
    fun encodeBackup(state: AppState, nextId: Long): String =
        store.encodeToJson(stateWithIdHighWaterAtLeast(state, nextId))

    /** Null when the payload is unreadable or from a newer build. */
    fun decodeBackup(raw: String): AppState? = store.decodeFromJson(raw)

    /** The manual export: the state and every cover it points at. */
    suspend fun exportToDocuments(state: AppState, nextId: Long) {
        val current = stateWithIdHighWaterAtLeast(state, nextId)
        writeBackupToDocuments(
            context = context,
            json = store.encodeToJson(current),
            coverFiles = resolveStoredCoverFiles(context, current),
        )
    }

    /**
     * The automatic snapshot, on a scope not tied to the activity: onStop is
     * routinely followed by destroy, and the backup used to be cancelled with
     * it. [json] is made by the caller, on its thread, so the archive holds
     * what was on screen when the user left. Failures are swallowed: this runs
     * unattended, where an uncaught exception takes the process down.
     */
    fun writeAutoBackupInBackground(json: String) {
        appBackgroundScope.launch {
            runCatching {
                writeBackupToDocuments(
                    context = context,
                    json = json,
                    fileName = AUTO_BACKUP_FILE_NAME,
                )
            }
        }
    }

    /**
     * Writes the archive's covers that [decoded] actually refers to, and says
     * how many would not write.
     *
     * Covers the archive lacks are **not** deleted: a state-only archive (the
     * automatic one) carries none, and deleting what it does not mention wiped
     * the images of media the restored state still points at. Orphans go by
     * the sweep after adoption, which is the right place.
     */
    suspend fun restoreCovers(decoded: AppState, coverEntries: Map<String, ByteArray>): Int {
        val expectedRefs = collectInternalCoverRefs(decoded)
        var notWritten = 0
        coverEntries.forEach { (ref, bytes) ->
            if (ref in expectedRefs) {
                val written = writeInternalCoverBytes(context = context, coverRef = ref, bytes = bytes)
                if (!written) notWritten++
            }
        }
        return notWritten
    }

    /** Old states pointed covers at content URIs; copies them in, item by item. */
    suspend fun migrateLegacyMediaCovers(state: AppState): AppState =
        stateWithCoverRefsMigrated(state) { coverRef, type, itemId ->
            val source = coverRef?.takeIf(::isExternalCoverRef)
            if (source == null) {
                coverRef
            } else {
                importCoverIntoInternalStorage(
                    context = context,
                    sourceUri = source.toUri(),
                    mediaKind = coverMediaKind(type),
                    itemId = itemId,
                ) ?: coverRef
            }
        }

    /* ---------------- the new-task draft ---------------- */

    suspend fun loadDraft(): NewTaskDraft? = draftStore.load()

    suspend fun saveDraft(draft: NewTaskDraft) = draftStore.save(draft)

    suspend fun clearDraft() = draftStore.clear()
}
