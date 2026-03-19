package com.alphaomegos.annasagenda.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

sealed interface BackupImportPayload {
    data class ZipPackage(
        val appStateJson: String,
        val coverEntries: Map<String, ByteArray>,
    ) : BackupImportPayload

    data class LegacyJson(
        val json: String,
    ) : BackupImportPayload
}

suspend fun readBackupImportPayload(
    context: Context,
    uri: Uri,
): BackupImportPayload? = withContext(Dispatchers.IO) {
    readZipBackupPackage(context, uri) ?: readLegacyJsonBackup(context, uri)
}

suspend fun readZipBackupPackage(
    context: Context,
    uri: Uri,
): BackupImportPayload.ZipPackage? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { rawInput ->
            ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                var appStateJson: String? = null
                val coverEntries = linkedMapOf<String, ByteArray>()

                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val entryBytes = zip.readBytes()

                    when (entry.name) {
                        BACKUP_APP_STATE_ENTRY_NAME -> {
                            appStateJson = entryBytes.toString(StandardCharsets.UTF_8)
                        }

                        BACKUP_META_ENTRY_NAME -> {
                            // Metadata is optional for current import logic.
                        }

                        else -> {
                            val coverRef = coverRefFromZipEntryName(entry.name)
                            if (coverRef != null) {
                                coverEntries[coverRef] = entryBytes
                            }
                        }
                    }

                    zip.closeEntry()
                }

                val json = appStateJson?.takeIf { it.isNotBlank() } ?: return@use null

                BackupImportPayload.ZipPackage(
                    appStateJson = json,
                    coverEntries = coverEntries
                )
            }
        }
    }.getOrNull()
}

suspend fun readLegacyJsonBackup(
    context: Context,
    uri: Uri,
): BackupImportPayload.LegacyJson? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val json = input.bufferedReader(StandardCharsets.UTF_8).readText()
            val clean = json.trim()
            if (clean.isBlank()) {
                null
            } else {
                BackupImportPayload.LegacyJson(clean)
            }
        }
    }.getOrNull()
}