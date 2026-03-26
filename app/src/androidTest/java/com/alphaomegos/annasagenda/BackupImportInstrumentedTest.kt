package com.alphaomegos.annasagenda

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphaomegos.annasagenda.util.BACKUP_APP_STATE_ENTRY_NAME
import com.alphaomegos.annasagenda.util.BACKUP_META_ENTRY_NAME
import com.alphaomegos.annasagenda.util.BackupImportPayload
import com.alphaomegos.annasagenda.util.readBackupImportPayload
import com.alphaomegos.annasagenda.util.readLegacyJsonBackup
import com.alphaomegos.annasagenda.util.readZipBackupPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupImportInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readLegacyJsonBackup_returnsTrimmedJsonPayload() {
        val file = writeTextFile(
            name = "legacy_backup.json",
            text = """
                
                  { "v": 3, "runningPlanApproved": true }  
                
            """.trimIndent()
        )

        val payload = runBlockingIo {
            readLegacyJsonBackup(context, Uri.fromFile(file))
        }

        assertNotNull(payload)
        assertEquals(
            BackupImportPayload.LegacyJson("""{ "v": 3, "runningPlanApproved": true }"""),
            payload,
        )
    }

    @Test
    fun readZipBackupPackage_readsAppStateJsonAndIgnoresMeta() {
        val zipFile = writeZipFile(
            name = "backup_package.zip",
            entries = listOf(
                BACKUP_APP_STATE_ENTRY_NAME to """{ "v": 3, "mainMenuOrder": ["calendar"] }""".toByteArray(
                    StandardCharsets.UTF_8
                ),
                BACKUP_META_ENTRY_NAME to """
                    {
                      "format": "annas_agenda_backup",
                      "version": 1,
                      "appStateEntry": "app_state.json",
                      "coverCount": 0
                    }
                """.trimIndent().toByteArray(StandardCharsets.UTF_8),
                "ignored_extra.txt" to "hello".toByteArray(StandardCharsets.UTF_8),
            ),
        )

        val payload = runBlockingIo {
            readZipBackupPackage(context, Uri.fromFile(zipFile))
        }

        assertNotNull(payload)
        assertEquals(
            """{ "v": 3, "mainMenuOrder": ["calendar"] }""",
            payload!!.appStateJson,
        )
        assertTrue(payload.coverEntries.isEmpty())
    }

    @Test
    fun readBackupImportPayload_prefersZipPackageWhenUriPointsToZip() {
        val zipFile = writeZipFile(
            name = "prefer_zip.zip",
            entries = listOf(
                BACKUP_APP_STATE_ENTRY_NAME to """{ "v": 3, "runningPlanApproved": true }""".toByteArray(
                    StandardCharsets.UTF_8
                ),
            ),
        )

        val payload = runBlockingIo {
            readBackupImportPayload(context, Uri.fromFile(zipFile))
        }

        assertTrue(payload is BackupImportPayload.ZipPackage)
        assertEquals(
            """{ "v": 3, "runningPlanApproved": true }""",
            (payload as BackupImportPayload.ZipPackage).appStateJson,
        )
    }

    @Test
    fun readLegacyJsonBackup_returnsNullForBlankFile() {
        val file = writeTextFile(
            name = "blank_backup.json",
            text = "   \n\t   ",
        )

        val payload = runBlockingIo {
            readLegacyJsonBackup(context, Uri.fromFile(file))
        }

        assertEquals(null, payload)
    }

    private fun writeTextFile(
        name: String,
        text: String,
    ): File {
        val file = File(context.cacheDir, name)
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    private fun writeZipFile(
        name: String,
        entries: List<Pair<String, ByteArray>>,
    ): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}