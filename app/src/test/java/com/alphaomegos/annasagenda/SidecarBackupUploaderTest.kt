package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.SidecarBackupAccount
import com.alphaomegos.annasagenda.app.SidecarBackupDownloadReply
import com.alphaomegos.annasagenda.app.SidecarBackupHttp
import com.alphaomegos.annasagenda.app.SidecarBackupReply
import com.alphaomegos.annasagenda.app.SidecarBackupRequest
import com.alphaomegos.annasagenda.app.SidecarBackupResult
import com.alphaomegos.annasagenda.app.SidecarBackupUploader
import com.alphaomegos.annasagenda.app.SidecarBackupsClient
import com.alphaomegos.annasagenda.app.SidecarSealer
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.support.BackupDecryptOutcome
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.decryptBackup
import com.alphaomegos.annasagenda.support.deriveBackupKey
import com.alphaomegos.annasagenda.support.sidecarHex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backup taken to oursidecar in parts, and gone on with after a stop (16.10). */
class SidecarBackupUploaderTest {

    private class FakeSealer : SidecarSealer {
        override fun seal(plain: String): String? = "sealed:$plain"
        override fun open(sealed: String): String? = sealed.removePrefix("sealed:")
        override fun forget() {}
    }

    /** oursidecar as far as backups go, in memory, with parts of ten bytes. */
    private inner class FakeServer : SidecarBackupHttp {
        val partSize = 10
        var size = 0L
        var sha = ""
        var encrypted = false
        var startBody = ""
        val parts = sortedMapOf<Int, ByteArray>()
        var ready = false
        var gone = false
        var finishes = 0
        var failPart: Int? = null
        var refuseStart: SidecarBackupReply? = null
        val putCounts = mutableMapOf<Int, Int>()
        private val count get() = ((size + partSize - 1) / partSize).toInt()

        private fun backup(state: String) =
            """{"id":"n1","slot":"manual","state":"$state","size":$size,"sha256":"$sha","encrypted":$encrypted,"createdAt":5}"""

        override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply = when {
            url.endsWith("/api/backup-devices/sign-in") -> SidecarBackupReply(201, """{"token":"osb_t","device":{"id":"d1","name":"Pixel"}}""")
            method == "POST" && url.endsWith("/api/backups") -> refuseStart ?: run {
                startBody = json!!
                size = Regex(""""size":(\d+)""").find(json)!!.groupValues[1].toLong()
                sha = Regex(""""sha256":"([0-9a-f]+)"""").find(json)!!.groupValues[1]
                encrypted = json.contains(""""encrypted":true""")
                parts.clear()
                ready = false
                gone = false
                SidecarBackupReply(201, """{"backup":${backup("uploading")},"upload":{"partSize":$partSize,"partCount":$count}}""")
            }
            method == "GET" && url.endsWith("/api/backups/n1") ->
                if (gone) SidecarBackupReply(404, """{"error":"Not found"}""")
                else SidecarBackupReply(200, """{"backup":${backup(if (ready) "ready" else "uploading")},"upload":{"partSize":$partSize,"partCount":$count,"received":${parts.keys.toList()}}}""")
            method == "POST" && url.endsWith("/finish") -> {
                finishes++
                val missing = (1..count).filter { it !in parts }
                if (missing.isNotEmpty()) {
                    SidecarBackupReply(409, """{"error":"Missing","code":"parts_missing","missing":$missing}""")
                } else {
                    ready = true
                    SidecarBackupReply(200, """{"backup":${backup("ready")}}""")
                }
            }
            method == "DELETE" -> SidecarBackupReply(204, "")
            else -> SidecarBackupReply(404, "{}")
        }

        override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int): SidecarBackupReply {
            val n = url.substringAfterLast('/').toInt()
            putCounts[n] = (putCounts[n] ?: 0) + 1
            if (failPart == n) {
                failPart = null
                throw IOException("the network went")
            }
            val expected = if (n < count) partSize.toLong() else size - (count - 1).toLong() * partSize
            if (length.toLong() != expected) return SidecarBackupReply(400, """{"error":"Wrong length"}""")
            parts[n] = bytes.copyOfRange(offset, offset + length)
            return SidecarBackupReply(200, "{}")
        }

        override fun download(url: String, bearer: String, from: Long, open: (Boolean) -> OutputStream, onBytes: (Long) -> Unit) = SidecarBackupDownloadReply(500)

        fun whole(): ByteArray = parts.values.fold(ByteArray(0)) { a, b -> a + b }
    }

    private val dir = Files.createTempDirectory("upload").toFile()
    private val server = FakeServer()
    private val client = SidecarBackupsClient(server, Dispatchers.Unconfined)
    private val account = SidecarBackupAccount(
        file = File(dir, "backups.json"),
        sealer = FakeSealer(),
        client = client,
        deviceName = "Pixel",
        deriveKey = { deriveBackupKey(it, ByteArray(16) { 3 }, 10) },
        io = Dispatchers.Unconfined,
    )
    private val uploader = SidecarBackupUploader(File(dir, "upload"), account, client, "22.0", io = Dispatchers.Unconfined)
    private val archive = ByteArray(47) { (it * 3).toByte() }
    private var archivesWritten = 0
    private val writeArchive: suspend (File) -> Boolean = { file ->
        archivesWritten++
        file.writeBytes(archive)
        true
    }

    private fun sha(bytes: ByteArray) = sidecarHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun signedIn(encrypt: Boolean) = runBlocking {
        account.signedIn("https://oursidecar.com", "anna@example.org", "pw")
        account.chooseEncryption(encrypt)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun anUnencryptedBackupGoesUpInWholePartsAndTheServerHasItAsItIs() = runBlocking {
        signedIn(encrypt = false)
        val progress = mutableListOf<Pair<Int, Int>>()

        val r = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL, note = " before\nthe trip "), writeArchive) { a, b -> progress += a to b }

        assertTrue(r is SidecarBackupResult.Ok)
        assertArrayEquals(archive, server.whole())
        assertEquals(sha(archive), server.sha)
        assertEquals(listOf(0 to 5, 1 to 5, 2 to 5, 3 to 5, 4 to 5, 5 to 5), progress)
        assertTrue(server.startBody.contains(""""note":"before the trip""""))
        assertTrue(server.startBody.contains(""""appVersion":"22.0""""))
        assertFalse(server.encrypted)
        assertFalse(uploader.pending(SidecarBackupSlot.MANUAL))
        assertFalse(File(dir, "upload/manual").exists())
    }

    @Test
    fun anEncryptedBackupIsTheArchiveSealedWithTheAccountsKeyAndItsShaIsOfWhatWentUp() = runBlocking {
        signedIn(encrypt = true)

        uploader.run(SidecarBackupRequest(SidecarBackupSlot.AUTO), writeArchive)

        val up = server.whole()
        assertTrue(server.encrypted)
        assertEquals(sha(up), server.sha)
        val out = ByteArrayOutputStream()
        val outcome = decryptBackup(ByteArrayInputStream(up), out) { deriveBackupKey("pw", ByteArray(16) { 3 }, 10).bytes }
        assertEquals(BackupDecryptOutcome.OPENED, outcome)
        assertArrayEquals(archive, out.toByteArray())
    }

    @Test
    fun aRunStoppedHalfWayGoesOnFromThePartsTheServerHasWithTheSameFile() = runBlocking {
        signedIn(encrypt = false)
        server.failPart = 3

        val stopped = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive)

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.NO_NETWORK), stopped)
        assertTrue(uploader.pending(SidecarBackupSlot.MANUAL))

        val done = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive)

        assertTrue(done is SidecarBackupResult.Ok)
        assertEquals(1, archivesWritten)
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 1, 5 to 1), server.putCounts.toMap())
        assertArrayEquals(archive, server.whole())
    }

    @Test
    fun anUploadTheServerDroppedStartsAgainFromNothing() = runBlocking {
        signedIn(encrypt = false)
        server.failPart = 2
        uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive)
        server.gone = true

        val done = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive)

        assertTrue(done is SidecarBackupResult.Ok)
        assertEquals(2, archivesWritten)
        assertArrayEquals(archive, server.whole())
    }

    @Test
    fun withThreeByHandTheRefusalBringsTheBackupsToChooseFromAndNothingIsLeftBehind() = runBlocking {
        signedIn(encrypt = false)
        server.refuseStart = SidecarBackupReply(
            409,
            """{"error":"Full","code":"backups_full","backups":[{"id":"b1","slot":"manual","state":"ready","size":1,"sha256":"${"a".repeat(64)}","encrypted":false,"createdAt":1}]}""",
        )

        val r = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive) as SidecarBackupResult.Failed

        assertEquals(SidecarBackupFailure.FULL, r.failure)
        assertEquals(listOf("b1"), r.backups.map { it.id })
        assertFalse(uploader.pending(SidecarBackupSlot.MANUAL))
        assertFalse(File(dir, "upload/manual").exists())
    }

    @Test
    fun nothingGoesUpUntilEncryptionIsChosen() = runBlocking {
        account.signedIn("https://oursidecar.com", "anna@example.org", "pw")

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.NOT_CHOSEN), uploader.run(SidecarBackupRequest(SidecarBackupSlot.AUTO), writeArchive))
        assertEquals(0, archivesWritten)
        assertEquals("", server.startBody)
    }

    @Test
    fun signedOutNothingIsAsked() = runBlocking {
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT), uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), writeArchive))
        assertEquals(0, archivesWritten)
    }

    @Test
    fun anArchiveThatWouldNotWriteGoesNowhere() = runBlocking {
        signedIn(encrypt = false)

        val r = uploader.run(SidecarBackupRequest(SidecarBackupSlot.MANUAL), { false })

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED), r)
        assertEquals("", server.startBody)
    }
}
