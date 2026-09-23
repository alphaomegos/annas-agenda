package com.alphaomegos.annasagenda

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphaomegos.annasagenda.util.AUTO_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.BACKUP_APP_STATE_ENTRY_NAME
import com.alphaomegos.annasagenda.util.BACKUP_META_ENTRY_NAME
import com.alphaomegos.annasagenda.util.MANUAL_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.StoredCoverFile
import com.alphaomegos.annasagenda.util.buildInternalCoverRef
import com.alphaomegos.annasagenda.util.readZipBackupPackage
import com.alphaomegos.annasagenda.util.writeBackupToDocuments
import com.alphaomegos.annasagenda.util.zipEntryNameForCoverRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class BackupExportInstrumentedTest {

    private val createdFileNames = mutableListOf<String>()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        createdFileNames.forEach { fileName ->
            findBackupUri(fileName)?.let { uri ->
                context.contentResolver.delete(uri, null, null)
            }
        }
        createdFileNames.clear()
    }

    @Test
    fun writeBackupToDocuments_writesZipWithAppStateMetaAndCovers() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)

        val fileName = uniqueBackupFileName("export_full")
        val json = AppStateStore(context).encodeToJson(
            AppState(
                runningPlanApproved = true,
                mainMenuOrder = listOf("calendar", "running"),
            )
        )

        val coverRefA = buildInternalCoverRef(mediaKind = "series", itemId = 202)
        val coverRefB = buildInternalCoverRef(mediaKind = "book", itemId = 101)

        val coverBytesA = "series-cover-bytes".toByteArray(StandardCharsets.UTF_8)
        val coverBytesB = "book-cover-bytes".toByteArray(StandardCharsets.UTF_8)

        val coverFiles = listOf(
            storedCoverFile(ref = coverRefA, bytes = coverBytesA),
            storedCoverFile(ref = coverRefB, bytes = coverBytesB),
        )

        writeBackupToDocuments(
            context = context,
            json = json,
            coverFiles = coverFiles,
            fileName = fileName,
        )

        val uri = requireBackupUri(fileName)
        val zipEntries = readZipEntries(uri)

        assertEquals(
            listOf(
                BACKUP_APP_STATE_ENTRY_NAME,
                BACKUP_META_ENTRY_NAME,
                zipEntryNameForCoverRef(coverRefB),
                zipEntryNameForCoverRef(coverRefA),
            ),
            zipEntries.map { it.first },
        )

        val appStateEntryText = zipEntries
            .first { it.first == BACKUP_APP_STATE_ENTRY_NAME }
            .second
            .toString(StandardCharsets.UTF_8)

        val metaEntryText = zipEntries
            .first { it.first == BACKUP_META_ENTRY_NAME }
            .second
            .toString(StandardCharsets.UTF_8)

        assertEquals(json, appStateEntryText)
        assertTrue(metaEntryText.contains("annas_agenda_backup"))
        assertTrue(metaEntryText.contains(""""appStateEntry": "app_state.json""""))
        assertTrue(metaEntryText.contains(""""coverCount": 2"""))

        val imported = readZipBackupPackage(context, uri)
        assertNotNull(imported)
        assertEquals(json, imported!!.appStateJson)
        assertEquals(setOf(coverRefA, coverRefB), imported.coverEntries.keys)

        assertArrayEquals(coverBytesA, imported.coverEntries.getValue(coverRefA))
        assertArrayEquals(coverBytesB, imported.coverEntries.getValue(coverRefB))
    }

    @Test
    fun writeBackupToDocuments_overwritesExistingBackupWithSameFileName() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)

        val fileName = uniqueBackupFileName("export_overwrite")

        val firstJson = AppStateStore(context).encodeToJson(
            AppState(
                runningPlanApproved = false,
                mainMenuOrder = listOf("calendar"),
            )
        )

        writeBackupToDocuments(
            context = context,
            json = firstJson,
            coverFiles = emptyList(),
            fileName = fileName,
        )

        val firstUri = requireBackupUri(fileName)

        val coverRef = buildInternalCoverRef(mediaKind = "movie", itemId = 303)
        val coverBytes = "movie-cover".toByteArray(StandardCharsets.UTF_8)

        val secondJson = AppStateStore(context).encodeToJson(
            AppState(
                runningPlanApproved = true,
                mainMenuOrder = listOf("calendar", "reading", "running"),
            )
        )

        writeBackupToDocuments(
            context = context,
            json = secondJson,
            coverFiles = listOf(storedCoverFile(ref = coverRef, bytes = coverBytes)),
            fileName = fileName,
        )

        val secondUri = requireBackupUri(fileName)
        assertEquals(firstUri, secondUri)
        assertEquals(1, countBackupsWithFileName(fileName))

        val imported = readZipBackupPackage(context, secondUri)
        assertNotNull(imported)
        assertEquals(secondJson, imported!!.appStateJson)
        assertEquals(setOf(coverRef), imported.coverEntries.keys)
        assertArrayEquals(coverBytes, imported.coverEntries.getValue(coverRef))
    }

    /**
     * The direction the old overwrite test never took.
     *
     * It wrote a small archive first and a larger one second, so the second
     * write covered the first completely and nothing stale was left behind.
     * Production does the opposite: a manual export with megabytes of covers,
     * then an automatic state-only backup a fraction of the size. With
     * MediaStore's non-truncating "w" the tail of the big archive survived, a
     * reader found the stale central directory at the end of the file, and the
     * backup would not open at all.
     */
    @Test
    fun writeBackupToDocuments_overwritingALargerBackupWithASmallerOneStaysReadable() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)

        val fileName = uniqueBackupFileName("export_shrink")

        val bigJson = AppStateStore(context).encodeToJson(
            AppState(mainMenuOrder = List(200) { "calendar_$it" })
        )
        val covers = (1..12).map { index ->
            val ref = buildInternalCoverRef(mediaKind = "book", itemId = 900L + index)
            storedCoverFile(ref = ref, bytes = ByteArray(40_000) { (index + it).toByte() })
        }

        writeBackupToDocuments(
            context = context,
            json = bigJson,
            coverFiles = covers,
            fileName = fileName,
        )

        val bigSize = backupSize(requireBackupUri(fileName))

        // Now the small, cover-less write — the automatic backup's shape.
        val smallJson = AppStateStore(context).encodeToJson(
            AppState(mainMenuOrder = listOf("calendar"))
        )

        writeBackupToDocuments(
            context = context,
            json = smallJson,
            coverFiles = emptyList(),
            fileName = fileName,
        )

        val uri = requireBackupUri(fileName)
        val smallSize = backupSize(uri)

        assertTrue(
            "the file must actually shrink, otherwise the old bytes are still there " +
                "(was $bigSize, now $smallSize)",
            smallSize < bigSize
        )

        val imported = readZipBackupPackage(context, uri)
        assertNotNull("the overwritten archive must still be readable", imported)
        assertEquals(smallJson, imported!!.appStateJson)
        assertTrue(
            "no covers should survive from the previous, larger archive",
            imported.coverEntries.isEmpty()
        )
    }

    @Test
    fun theAutomaticBackupDoesNotShareItsNameWithTheManualExport() {
        assertTrue(AUTO_BACKUP_FILE_NAME != MANUAL_BACKUP_FILE_NAME)
    }

    private fun backupSize(uri: Uri): Long =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().size.toLong() }
            ?: error("Cannot open $uri")

    private fun uniqueBackupFileName(prefix: String): String {
        val fileName = "annas_agenda_${prefix}_${UUID.randomUUID()}.zip"
        createdFileNames += fileName
        return fileName
    }

    private fun storedCoverFile(
        ref: String,
        bytes: ByteArray,
    ): StoredCoverFile {
        val file = File(context.cacheDir, ref.substringAfterLast('/'))
        file.writeBytes(bytes)
        return StoredCoverFile(ref = ref, file = file)
    }

    private fun requireBackupUri(fileName: String): Uri =
        findBackupUri(fileName) ?: error("Backup URI not found for $fileName")

    private fun findBackupUri(fileName: String): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "Documents/AnnasAgenda/"

        return context.contentResolver.query(
            collection,
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

    private fun countBackupsWithFileName(fileName: String): Int {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "Documents/AnnasAgenda/"

        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(fileName, relativePath),
            null,
        )?.use { cursor ->
            cursor.count
        } ?: 0
    }

    private fun readZipEntries(uri: Uri): List<Pair<String, ByteArray>> {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                val result = mutableListOf<Pair<String, ByteArray>>()

                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        result += entry.name to zip.readBytes()
                    }
                    zip.closeEntry()
                }

                return result
            }
        }

        error("Cannot open ZIP input stream for $uri")
    }
}