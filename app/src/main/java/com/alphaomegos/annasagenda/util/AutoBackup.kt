package com.alphaomegos.annasagenda.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

suspend fun writeBackupToDocuments(
    context: Context,
    json: String,
    fileName: String = "annas_agenda_backup.json",
) = withContext(Dispatchers.IO) {

    // Works best on Android 10+ (API 29+).
    if (Build.VERSION.SDK_INT < 29) return@withContext

    val resolver = context.contentResolver
    val relativePath = "Documents/AnnasAgenda/"

    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // Try to find existing file and overwrite it
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
        } else null
    }

    val uri = existingUri ?: run {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        resolver.insert(collection, values) ?: return@withContext
    }

    resolver.openOutputStream(uri, "wt")?.use { os ->
        OutputStreamWriter(os).use { it.write(json) }
    }
}
