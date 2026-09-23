package com.alphaomegos.annasagenda.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val BACKUP_APP_STATE_ENTRY_NAME = "app_state.json"
const val BACKUP_META_ENTRY_NAME = "backup_meta.json"
private const val BACKUP_FORMAT_VERSION = 1

/**
 * The full export the user asks for by hand: app state plus every cover image.
 */
const val MANUAL_BACKUP_FILE_NAME = "annas_agenda_backup.zip"

/**
 * The automatic snapshot written whenever the app goes to the background. It
 * carries app state only — writing several megabytes of covers every time the
 * user leaves the app is not something to do behind their back.
 *
 * It must NOT share a name with the manual export. It used to, and since it is
 * far smaller it overwrote the full backup with a cover-less one, so the covers
 * silently disappeared from the only archive that had them.
 */
const val AUTO_BACKUP_FILE_NAME = "annas_agenda_autobackup.zip"

/**
 * One writer at a time.
 *
 * A manual export and an automatic snapshot can overlap — the user taps Export
 * and immediately leaves the app. Opening the same MediaStore entry twice with
 * "wt" means two truncating streams over one file, and what lands there is
 * neither archive. They write different files today, which is what makes that
 * survivable rather than routine; serialising them removes the question.
 */
private val backupWriteMutex = Mutex()

/**
 * Writes a backup archive into Documents/AnnasAgenda/.
 *
 * Uncancellable once it starts: "wt" truncates the file the moment the stream
 * opens, so a cancellation between that and the last byte leaves a file that is
 * neither the old backup nor the new one. Waiting for the lock is still
 * cancellable — it is only the write itself that must run to the end.
 */
suspend fun writeBackupToDocuments(
    context: Context,
    json: String,
    coverFiles: List<StoredCoverFile> = emptyList(),
    fileName: String = MANUAL_BACKUP_FILE_NAME,
) {
    if (Build.VERSION.SDK_INT < 29) return

    backupWriteMutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            writeBackupArchive(
                context = context,
                json = json,
                coverFiles = coverFiles,
                fileName = fileName,
            )
        }
    }
}

@Suppress("DEPRECATION") // MediaStore.setIncludePending, needed on API 29 only
private fun writeBackupArchive(
    context: Context,
    json: String,
    coverFiles: List<StoredCoverFile>,
    fileName: String,
) {
    val resolver = context.contentResolver
    val relativePath = "Documents/AnnasAgenda/"
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // A backup left pending by a killed process is invisible to everyone else,
    // but it is still ours, and it is the entry we want to reuse rather than
    // creating a second file beside it. On API 29 the owner has to ask for
    // pending rows explicitly; from 30 on they are always included.
    val queryCollection =
        if (Build.VERSION.SDK_INT == 29) {
            MediaStore.setIncludePending(collection)
        } else {
            collection
        }

    val existingUri = resolver.query(
        queryCollection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(fileName, relativePath),
        null
    )?.use { c ->
        if (c.moveToFirst()) {
            val id = c.getLong(0)
            ContentUris.withAppendedId(collection, id)
        } else {
            null
        }
    }

    val uri = existingUri ?: run {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            // Created hidden, so a brand new backup is never briefly visible as
            // an empty file.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        resolver.insert(collection, values) ?: return
    }

    // The window this closes: the process is killed after the stream truncated
    // the file and before the archive is complete. Nothing of ours runs then —
    // no finally, no handler — so the only way to mark the file unusable is to
    // mark it before the write and unmark it after. A file still flagged
    // pending is hidden from other apps, and the next backup overwrites it.
    setPending(resolver, uri, pending = true)

    try {
        // "wt" and not "w": MediaStore's plain "w" does not truncate. Writing a
        // shorter archive over a longer one left the old bytes past the new end,
        // and a reader looking for the central directory from the end of the file
        // found the stale one, whose offsets pointed into the new data. The result
        // was an archive that failed to open at all.
        resolver.openOutputStream(uri, "wt")?.use { rawOut ->
            ZipOutputStream(rawOut).use { zip ->
                writeZipStringEntry(
                    zip = zip,
                    entryName = BACKUP_APP_STATE_ENTRY_NAME,
                    text = json
                )

                writeZipStringEntry(
                    zip = zip,
                    entryName = BACKUP_META_ENTRY_NAME,
                    text = buildBackupMetaJson(
                        coverCount = coverFiles.size
                    )
                )

                coverFiles
                    .sortedBy { it.ref }
                    .forEach { stored ->
                        val entryName = zipEntryNameForCoverRef(stored.ref)
                            ?: return@forEach
                        val bytes = runCatching { stored.file.readBytes() }.getOrNull()
                            ?: return@forEach
                        writeZipBytesEntry(
                            zip = zip,
                            entryName = entryName,
                            bytes = bytes
                        )
                    }
            }
        }
    } finally {
        // An exception here is a failure inside a living process: the next
        // backup will overwrite this file anyway, and leaving it hidden would
        // look to the user like the backup vanished. Only a killed process,
        // which never reaches this line, leaves the file flagged.
        setPending(resolver, uri, pending = false)
    }
}

private fun setPending(
    resolver: ContentResolver,
    uri: Uri,
    pending: Boolean,
) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)
    }
    runCatching { resolver.update(uri, values, null, null) }
}

private fun buildBackupMetaJson(
    coverCount: Int,
): String {
    return """
        {
          "format": "annas_agenda_backup",
          "version": $BACKUP_FORMAT_VERSION,
          "appStateEntry": "$BACKUP_APP_STATE_ENTRY_NAME",
          "coverCount": $coverCount
        }
    """.trimIndent()
}

private fun writeZipStringEntry(
    zip: ZipOutputStream,
    entryName: String,
    text: String,
) {
    writeZipBytesEntry(
        zip = zip,
        entryName = entryName,
        bytes = text.toByteArray(StandardCharsets.UTF_8)
    )
}

private fun writeZipBytesEntry(
    zip: ZipOutputStream,
    entryName: String,
    bytes: ByteArray,
) {
    zip.putNextEntry(ZipEntry(entryName))
    zip.write(bytes)
    zip.closeEntry()
}