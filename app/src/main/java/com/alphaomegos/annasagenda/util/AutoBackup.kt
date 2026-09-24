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
import java.io.IOException
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
 * Writes a backup archive into Documents/AnnasAgenda/, or throws.
 *
 * Either the archive is complete and in place, or the one that was already
 * there is untouched. There is no third outcome, and in particular no outcome
 * where the call returns quietly having written nothing.
 *
 * Uncancellable once it starts: the last two steps replace the old archive with
 * the new one, and being interrupted between them would leave neither. Waiting
 * for the lock is still cancellable.
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
    val stagingName = stagingFileNameFor(fileName)

    // The archive is built beside the real one and only takes its place once it
    // is complete. Writing straight into the real one truncates it the instant
    // the stream opens, so anything going wrong after that — a full disk is the
    // obvious one — left the user with a stub where their only backup had been,
    // and an error message they could do nothing about.
    val staleStaging = findBackupUri(resolver, collection, stagingName, relativePath)
    if (staleStaging != null) {
        runCatching { resolver.delete(staleStaging, null, null) }
    }

    val stagingUri = resolver.insert(
        collection,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stagingName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            // Hidden while it is being written, so a half-finished archive is
            // never offered to anyone — including a process that gets killed
            // mid-write, where no code of ours runs to clean up.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        },
    ) ?: throw IOException("MediaStore refused to create $stagingName")

    try {
        val out = resolver.openOutputStream(stagingUri, "wt")
            ?: throw IOException("MediaStore refused to open $stagingName for writing")

        out.use { rawOut ->
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
    } catch (e: Throwable) {
        // Leave nothing behind that could be mistaken for a backup. The one
        // that was already there has not been touched.
        runCatching { resolver.delete(stagingUri, null, null) }
        throw e
    }

    // From here the new archive is complete on disk. The previous one is
    // removed and the new one takes its name.
    val previous = findBackupUri(resolver, collection, fileName, relativePath)
    if (previous != null && resolver.delete(previous, null, null) <= 0) {
        // Renaming onto a name that is still taken gets "(1)" appended, and a
        // backup under a name nobody looks at is worse than an error. The old
        // archive is still whole; the staging row is cleaned up by the next run.
        throw IOException("Could not replace the previous $fileName")
    }

    resolver.update(
        stagingUri,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        },
        null,
        null,
    )
}

/** Named so it keeps the .zip extension MediaStore checks against the type. */
internal fun stagingFileNameFor(fileName: String): String =
    fileName.removeSuffix(".zip") + ".part.zip"

@Suppress("DEPRECATION") // MediaStore.setIncludePending, needed on API 29 only
private fun findBackupUri(
    resolver: ContentResolver,
    collection: Uri,
    fileName: String,
    relativePath: String,
): Uri? {
    // A row left pending by a killed process is invisible to everyone else but
    // is still ours, and it is exactly the leftover we want to find. On API 29
    // the owner has to ask for pending rows; from 30 on they are always there.
    val queryCollection =
        if (Build.VERSION.SDK_INT == 29) {
            MediaStore.setIncludePending(collection)
        } else {
            collection
        }

    return resolver.query(
        queryCollection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(fileName, relativePath),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            ContentUris.withAppendedId(collection, cursor.getLong(0))
        } else {
            null
        }
    }
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