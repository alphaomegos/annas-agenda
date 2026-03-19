package com.alphaomegos.annasagenda.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val BACKUP_APP_STATE_ENTRY_NAME = "app_state.json"
const val BACKUP_META_ENTRY_NAME = "backup_meta.json"
private const val BACKUP_FORMAT_VERSION = 1

suspend fun writeBackupToDocuments(
    context: Context,
    json: String,
    coverFiles: List<StoredCoverFile> = emptyList(),
    fileName: String = "annas_agenda_backup.zip",
) = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext

    val resolver = context.contentResolver
    val relativePath = "Documents/AnnasAgenda/"
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val existingUri = resolver.query(
        collection,
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
        }
        resolver.insert(collection, values) ?: return@withContext
    }

    resolver.openOutputStream(uri, "w")?.use { rawOut ->
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
                    val entryName = zipEntryNameForCoverRef(stored.ref) ?: return@forEach
                    val bytes = runCatching { stored.file.readBytes() }.getOrNull() ?: return@forEach
                    writeZipBytesEntry(
                        zip = zip,
                        entryName = entryName,
                        bytes = bytes
                    )
                }
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