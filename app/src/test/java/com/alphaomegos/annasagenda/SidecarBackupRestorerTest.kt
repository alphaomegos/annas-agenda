package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.SidecarBackupAccount
import com.alphaomegos.annasagenda.app.SidecarBackupDownloadReply
import com.alphaomegos.annasagenda.app.SidecarBackupHttp
import com.alphaomegos.annasagenda.app.SidecarBackupReply
import com.alphaomegos.annasagenda.app.SidecarBackupRestorer
import com.alphaomegos.annasagenda.app.SidecarBackupResult
import com.alphaomegos.annasagenda.app.SidecarBackupsClient
import com.alphaomegos.annasagenda.app.SidecarSealer
import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.deriveBackupKey
import com.alphaomegos.annasagenda.support.encryptBackup
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

/** A backup brought back from oursidecar, checked and opened (16.10). */
class SidecarBackupRestorerTest {

    private class FakeSealer : SidecarSealer {
        override fun seal(plain: String): String? = "sealed:$plain"
        override fun open(sealed: String): String? = sealed.removePrefix("sealed:")
        override fun forget() {}
    }

    /** oursidecar's download, with Range, that can be cut after [cutAfter] bytes. */
    private inner class FakeServer : SidecarBackupHttp {
        var blob = ByteArray(0)
        var sha: String? = null
        var cutAfter: Int? = null
        var ignoreRange = false
        val froms = mutableListOf<Long>()

        override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply =
            if (url.endsWith("/sign-in")) SidecarBackupReply(201, """{"token":"osb_t","device":{"id":"d1"}}""") else SidecarBackupReply(404, "{}")

        override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int) = SidecarBackupReply(500, "")

        override fun download(url: String, bearer: String, from: Long, open: (Boolean) -> OutputStream, onBytes: (Long) -> Unit): SidecarBackupDownloadReply {
            froms += from
            val partial = from > 0 && !ignoreRange
            val rest = if (partial) blob.copyOfRange(from.toInt(), blob.size) else blob
            open(partial).use { out ->
                val cut = cutAfter
                if (cut != null && cut < rest.size) {
                    cutAfter = null
                    out.write(rest, 0, cut)
                    throw IOException("the network went")
                }
                out.write(rest)
            }
            return SidecarBackupDownloadReply(if (partial) 206 else 200, sha)
        }
    }

    private val dir = Files.createTempDirectory("restore").toFile()
    private val server = FakeServer()
    private val client = SidecarBackupsClient(server, Dispatchers.Unconfined)
    private val salt = ByteArray(16) { 7 }
    private val account = SidecarBackupAccount(
        file = File(dir, "backups.json"),
        sealer = FakeSealer(),
        client = client,
        deviceName = "Pixel",
        deriveKey = { deriveBackupKey(it, salt, 10) },
        io = Dispatchers.Unconfined,
    )
    private val restorer = SidecarBackupRestorer(File(dir, "restore"), account, client, Dispatchers.Unconfined)
    private val archive = ByteArray(5000) { (it * 7 + 1).toByte() }

    private fun sha(bytes: ByteArray) = sidecarHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun listed(blob: ByteArray, encrypted: Boolean) =
        SidecarBackup(id = "b/1", slot = SidecarBackupSlot.MANUAL, ready = true, size = blob.size.toLong(), sha256 = sha(blob), encrypted = encrypted)

    private fun sealedWith(password: String, keySalt: ByteArray = salt): ByteArray =
        ByteArrayOutputStream().also { encryptBackup(ByteArrayInputStream(archive), it, deriveBackupKey(password, keySalt, 10)) }.toByteArray()

    private fun signedIn(password: String = "pw") = runBlocking {
        account.signedIn("https://oursidecar.com", "anna@example.org", password)
        account.chooseEncryption(true)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun anUnencryptedBackupComesBackAsItWentAndItsShaIsChecked() = runBlocking {
        signedIn()
        server.blob = archive
        server.sha = sha(archive).uppercase()

        val zip = (restorer.fetch(listed(archive, encrypted = false)) as SidecarBackupResult.Ok).value

        assertArrayEquals(archive, zip.readBytes())
        assertEquals(listOf(0L), server.froms)
    }

    @Test
    fun aDownloadCutHalfWayGoesOnFromWhereItStopped() = runBlocking {
        signedIn()
        server.blob = archive
        server.cutAfter = 1234

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.NO_NETWORK), restorer.fetch(listed(archive, false)))
        val zip = (restorer.fetch(listed(archive, false)) as SidecarBackupResult.Ok).value

        assertEquals(listOf(0L, 1234L), server.froms)
        assertArrayEquals(archive, zip.readBytes())
    }

    @Test
    fun aServerThatSendsTheWholeToARangeIsNotAppendedTo() = runBlocking {
        signedIn()
        server.blob = archive
        server.cutAfter = 100
        restorer.fetch(listed(archive, false))
        server.ignoreRange = true

        val zip = (restorer.fetch(listed(archive, false)) as SidecarBackupResult.Ok).value

        assertArrayEquals(archive, zip.readBytes())
    }

    @Test
    fun whatIsNotWhatWasListedIsNotOpened() = runBlocking {
        signedIn()
        server.blob = archive.copyOf().also { it[10] = 0 }

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED), restorer.fetch(listed(archive, false)))

        server.blob = archive
        server.sha = "f".repeat(64)
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED), restorer.fetch(listed(archive, false)))
    }

    @Test
    fun anEncryptedBackupOpensWithTheKeyOnThePhone() = runBlocking {
        signedIn("pw")
        val sealed = sealedWith("pw")
        server.blob = sealed

        val zip = (restorer.fetch(listed(sealed, encrypted = true)) as SidecarBackupResult.Ok).value

        assertArrayEquals(archive, zip.readBytes())
    }

    @Test
    fun anotherPhonesBackupAsksForThePasswordOfWhenItWasMadeAndIsFetchedOnce() = runBlocking {
        signedIn("new password")
        val sealed = sealedWith("old password", keySalt = ByteArray(16) { 1 })
        server.blob = sealed
        val backup = listed(sealed, encrypted = true)

        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.WRONG_BACKUP_PASSWORD), restorer.fetch(backup))
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.WRONG_BACKUP_PASSWORD), restorer.fetch(backup, "new password"))
        val zip = (restorer.fetch(backup, "old password") as SidecarBackupResult.Ok).value

        assertArrayEquals(archive, zip.readBytes())
        assertEquals(listOf(0L), server.froms)
    }

    @Test
    fun clearingForgetsWhatCameDown() = runBlocking {
        signedIn()
        server.blob = archive
        restorer.fetch(listed(archive, false))

        restorer.clear()

        assertFalse(File(dir, "restore").exists())
        assertTrue(restorer.fetch(listed(archive, false)) is SidecarBackupResult.Ok)
        assertEquals(2, server.froms.size)
    }

    @Test
    fun signedOutNothingIsFetched() = runBlocking {
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT), restorer.fetch(listed(archive, false)))
        assertTrue(server.froms.isEmpty())
    }
}
