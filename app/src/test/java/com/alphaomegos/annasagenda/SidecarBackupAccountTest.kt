package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.SidecarBackupAccount
import com.alphaomegos.annasagenda.app.SidecarBackupDownloadReply
import com.alphaomegos.annasagenda.app.SidecarBackupHttp
import com.alphaomegos.annasagenda.app.SidecarBackupReply
import com.alphaomegos.annasagenda.app.SidecarBackupsClient
import com.alphaomegos.annasagenda.app.SidecarSealer
import com.alphaomegos.annasagenda.support.BackupCipherHeader
import com.alphaomegos.annasagenda.support.BackupKey
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.deriveBackupKey
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone signed in for backups beside the music, and the key kept for them (16.10). */
class SidecarBackupAccountTest {

    private class FakeSealer : SidecarSealer {
        var forgotten = 0
        override fun seal(plain: String): String? = "sealed:" + plain.reversed()
        override fun open(sealed: String): String? = sealed.removePrefix("sealed:").reversed().takeIf { sealed.startsWith("sealed:") }
        override fun forget() {
            forgotten++
        }
    }

    private val dir = Files.createTempDirectory("backups").toFile()
    private val file = File(dir, "backups.json")
    private val sealer = FakeSealer()
    private val sent = mutableListOf<Triple<String, String, String?>>()
    private var answer: (String, String) -> SidecarBackupReply = { _, _ -> SidecarBackupReply(404, "{}") }
    private val drawn = mutableListOf<String>()

    private val http = object : SidecarBackupHttp {
        override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply {
            sent += Triple(method, url, bearer)
            return answer(method, url)
        }

        override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int) = SidecarBackupReply(500, "")

        override fun download(url: String, bearer: String, from: Long, open: (Boolean) -> OutputStream, onBytes: (Long) -> Unit) = SidecarBackupDownloadReply(500)
    }

    private fun salt(password: String) = ByteArray(16) { (password.length + it).toByte() }

    private fun account() = SidecarBackupAccount(
        file = file,
        sealer = sealer,
        client = SidecarBackupsClient(http, Dispatchers.Unconfined),
        deviceName = "Samsung SM-F926B",
        deriveKey = { password -> drawn += password; deriveBackupKey(password, salt(password), 10) },
        io = Dispatchers.Unconfined,
    )

    private val server = "https://oursidecar.com"

    private fun signsIn(token: String = "osb_t1", device: String = "d1") {
        answer = { method, url ->
            when {
                method == "POST" && url.endsWith("/api/backup-devices/sign-in") ->
                    SidecarBackupReply(201, """{"token":"$token","device":{"id":"$device","name":"Samsung SM-F926B"}}""")
                method == "DELETE" -> SidecarBackupReply(204, "")
                method == "GET" && url.endsWith("/api/backups") -> SidecarBackupReply(200, """{"backups":[],"settings":{"whenFull":"ask"}}""")
                else -> SidecarBackupReply(404, "{}")
            }
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun signingInToTheMusicSignsThePhoneInForBackupsAndDrawsTheKey() = runBlocking {
        signsIn()
        val account = account()

        account.signedIn(server, "anna@example.org", "pw-of-anna")

        val s = account.status.value
        assertTrue(s.signedIn)
        assertTrue(s.hasKey)
        assertNull(s.encrypt)
        assertEquals("Samsung SM-F926B", s.deviceName)
        assertEquals(server to "osb_t1", account.credentials())
        assertEquals(listOf("pw-of-anna"), drawn)
        // Kept sealed: neither the token nor the password nor the key is in the file as it is.
        val text = file.readText()
        assertFalse(text.contains("osb_t1"))
        assertFalse(text.contains("pw-of-anna"))
        assertTrue(text.contains("sealed:"))
    }

    @Test
    fun aSecondSignInDoesNotSignThePhoneInAgainButDrawsTheKeyAfresh() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "old")
        account.signedIn(server, "Anna@Example.org", "new")

        assertEquals(1, sent.count { it.second.endsWith("/sign-in") })
        assertEquals(listOf("old", "new"), drawn)
        account.chooseEncryption(true)
        assertEquals(deriveBackupKey("new", salt("new"), 10).bytes.toList(), account.encryptionKey()!!.bytes.toList())
    }

    @Test
    fun withEncryptionTurnedOffNoKeyIsDrawnAndTurningItOnTakesThePassword() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "pw")
        account.chooseEncryption(false)
        assertFalse(account.status.value.hasKey)
        assertNull(account.encryptionKey())

        account.signedIn(server, "anna@example.org", "pw")
        assertEquals(listOf("pw"), drawn)

        assertFalse(account.chooseEncryption(true))
        assertTrue(account.chooseEncryption(true, "pw"))
        assertTrue(account.status.value.encrypt == true && account.status.value.hasKey)
        assertNotNull(account.encryptionKey())
    }

    @Test
    fun anAccountWithoutBackupsIsToldApartAndNoKeyIsDrawn() = runBlocking {
        answer = { _, _ -> SidecarBackupReply(403, """{"error":"No","code":"backups_not_enabled"}""") }
        val account = account()

        account.signedIn(server, "eduard@example.org", "pw")

        val s = account.status.value
        assertTrue(s.notEnabled)
        assertFalse(s.signedIn)
        assertFalse(s.needsSignIn)
        assertTrue(drawn.isEmpty())
        assertNull(account.credentials())
    }

    @Test
    fun anotherAccountSignsTheOldPhoneTokenOutFirst() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "pw")
        signsIn(token = "osb_t2", device = "d2")

        account.signedIn(server, "eduard@example.org", "pw2")

        assertEquals(Triple("DELETE", "$server/api/backup-devices/d1", "osb_t1"), sent.first { it.first == "DELETE" })
        assertEquals(server to "osb_t2", account.credentials())
        assertEquals("eduard@example.org", account.status.value.email)
    }

    @Test
    fun aRevokedTokenAsksForThePasswordAgain() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "pw")
        answer = { _, _ -> SidecarBackupReply(401, """{"error":"Unauthorized"}""") }

        account.refresh()

        assertTrue(account.status.value.needsSignIn)
        assertFalse(account.status.value.signedIn)
        assertEquals(SidecarBackupFailure.SIGNED_OUT, account.status.value.failure)
        assertNull(account.credentials())

        signsIn(token = "osb_t3")
        account.signedIn(server, "anna@example.org", "pw")
        assertEquals(server to "osb_t3", account.credentials())
    }

    @Test
    fun signingOutRevokesThePhoneAndForgetsEverything() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "pw")

        account.signOut()

        assertEquals(Triple("DELETE", "$server/api/backup-devices/d1", "osb_t1"), sent.last())
        assertEquals(1, sealer.forgotten)
        assertFalse(account.status.value.signedIn)
        assertFalse(account.status.value.hasKey)
        assertNull(account.credentials())
        assertFalse(file.readText().contains("sealed:"))
    }

    @Test
    fun whatWasKeptIsThereForTheNextProcess() = runBlocking {
        signsIn()
        account().run {
            signedIn(server, "anna@example.org", "pw")
            chooseEncryption(true)
            setAuto(false)
        }

        val again = account()

        assertTrue(again.status.value.signedIn)
        assertEquals(true, again.status.value.encrypt)
        assertFalse(again.status.value.auto)
        assertEquals(server to "osb_t1", again.credentials())
        assertNotNull(again.encryptionKey())
    }

    @Test
    fun theKeyOnThePhoneOpensOnlyTheFilesItEncrypted() = runBlocking {
        signsIn()
        val account = account()
        account.signedIn(server, "anna@example.org", "pw")
        val key: BackupKey = deriveBackupKey("pw", salt("pw"), 10)

        assertNotNull(account.keyFor(BackupCipherHeader(10, key.salt, ByteArray(16), 32)))
        assertNull(account.keyFor(BackupCipherHeader(10, ByteArray(16), ByteArray(16), 32)))
        assertNull(account.keyFor(BackupCipherHeader(11, key.salt, ByteArray(16), 32)))
    }
}
