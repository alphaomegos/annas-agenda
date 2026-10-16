package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.SidecarBackupDevice
import com.alphaomegos.annasagenda.app.SidecarBackupDownloadReply
import com.alphaomegos.annasagenda.app.SidecarBackupHttp
import com.alphaomegos.annasagenda.app.SidecarBackupReply
import com.alphaomegos.annasagenda.app.SidecarBackupResult
import com.alphaomegos.annasagenda.app.SidecarBackupStart
import com.alphaomegos.annasagenda.app.SidecarBackupsClient
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.model.SidecarBackupWhenFull
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What this app sends oursidecar about backups, and what it makes of the answers (16.10). */
class SidecarBackupsClientTest {

    private data class Sent(val method: String, val url: String, val bearer: String?, val body: String?, val bytes: Int = -1, val from: Long = -1)

    private val server = "https://oursidecar.com"
    private val sha = "0123456789abcdef".repeat(4)
    private val sent = mutableListOf<Sent>()
    private var answer: (Sent) -> SidecarBackupReply = { SidecarBackupReply(404, "{}") }
    private var download: (Sent, OutputStream) -> SidecarBackupDownloadReply = { _, _ -> SidecarBackupDownloadReply(404) }

    private val http = object : SidecarBackupHttp {
        override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply =
            Sent(method, url, bearer, json).also { sent += it }.let(answer)

        override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int): SidecarBackupReply =
            Sent("PUT", url, bearer, String(bytes, offset, length), bytes = length).also { sent += it }.let(answer)

        override fun download(url: String, bearer: String, from: Long, out: OutputStream, onBytes: (Long) -> Unit): SidecarBackupDownloadReply =
            Sent("GET", url, bearer, null, from = from).also { sent += it }.let { download(it, out) }
    }

    private val client = SidecarBackupsClient(http, Dispatchers.Unconfined)

    private fun backupJson(id: String, slot: String = "manual", state: String = "ready", createdAt: Long = 100) =
        """{"id":"$id","slot":"$slot","state":"$state","size":70000000,"sha256":"${sha.uppercase()}","encrypted":true,
            "deviceName":"Samsung SM-F926B","appVersion":"22.0","note":"before the trip","createdAt":$createdAt,"completedAt":${createdAt + 60},"newField":1}"""

    @Test
    fun aPhoneSignsInWithTheAccountsPasswordAndGetsATokenOfItsOwn() = runBlocking {
        answer = { SidecarBackupReply(201, """{"token":"osb_t0k3n","device":{"id":"d1","name":"Samsung SM-F926B"}}""") }

        val result = client.signIn(server, " anna@example.org ", "pw", "Samsung SM-F926B")

        assertEquals(SidecarBackupResult.Ok(SidecarBackupDevice("osb_t0k3n", "d1", "Samsung SM-F926B")), result)
        val request = sent.single()
        assertEquals("POST", request.method)
        assertEquals("$server/api/backup-devices/sign-in", request.url)
        assertNull(request.bearer)
        assertEquals("""{"email":"anna@example.org","password":"pw","deviceName":"Samsung SM-F926B"}""", request.body)
    }

    @Test
    fun signingInSaysAWrongPasswordAnAccountWithoutBackupsAndHowLongToWait() = runBlocking {
        answer = { SidecarBackupReply(401, """{"error":"Wrong email or password"}""") }
        assertEquals(SidecarBackupFailure.WRONG_PASSWORD, (client.signIn(server, "a", "b", "c") as SidecarBackupResult.Failed).failure)

        answer = { SidecarBackupReply(403, """{"error":"Backups are not enabled","code":"backups_not_enabled"}""") }
        assertEquals(SidecarBackupFailure.NOT_ENABLED, (client.signIn(server, "a", "b", "c") as SidecarBackupResult.Failed).failure)

        answer = { SidecarBackupReply(429, """{"error":"Too many"}""", retryAfter = "90") }
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.TOO_MANY_ATTEMPTS, retryMinutes = 2), client.signIn(server, "a", "b", "c"))

        // A token that is not one for backups is not taken.
        answer = { SidecarBackupReply(201, """{"token":"session-cookie","device":{"id":"d1"}}""") }
        assertEquals(SidecarBackupFailure.UNEXPECTED, (client.signIn(server, "a", "b", "c") as SidecarBackupResult.Failed).failure)
    }

    @Test
    fun theListIsReadWithTheTokenAndItsUnknownKindsLeftOut() = runBlocking {
        answer = {
            SidecarBackupReply(
                200,
                """{"backups":[${backupJson("b1")},${backupJson("a1", slot = "auto")},${backupJson("x", slot = "weekly")},${backupJson("u", state = "uploading")}],
                   "settings":{"whenFull":"replace-oldest"},"limits":{"manual":3,"auto":1,"maxSize":209715200,"partSize":8388608}}""",
            )
        }

        val listing = (client.list(server, "osb_t") as SidecarBackupResult.Ok).value

        assertEquals(listOf("b1", "a1", "u"), listing.backups.map { it.id })
        assertEquals(SidecarBackupWhenFull.REPLACE_OLDEST, listing.whenFull)
        assertEquals(8_388_608L, listing.limits.partSize)
        val b1 = listing.backups.first()
        assertTrue(b1.ready && b1.encrypted)
        assertEquals(sha, b1.sha256)
        assertEquals("before the trip", b1.note)
        assertEquals(160L, b1.completedAtSeconds)
        assertTrue(!listing.backups.last().ready)
        assertEquals(Sent("GET", "$server/api/backups", "osb_t", null), sent.single())
    }

    @Test
    fun aRevokedTokenIsSaidAsSignedOutAndNoNetworkAsSuch() = runBlocking {
        answer = { SidecarBackupReply(401, """{"error":"Unauthorized"}""") }
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT), client.list(server, "osb_t"))

        answer = { throw IOException("offline") }
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.NO_NETWORK), client.list(server, "osb_t"))
    }

    @Test
    fun startingSaysWhatGoesUpAndTheServerSaysHowToSendIt() = runBlocking {
        answer = {
            SidecarBackupReply(
                201,
                """{"backup":${backupJson("n1", state = "uploading")},"replaces":"b0","upload":{"partSize":8388608,"partCount":9}}""",
            )
        }

        val upload = (
            client.start(
                server,
                "osb_t",
                SidecarBackupStart(SidecarBackupSlot.MANUAL, size = 70_000_000, sha256 = sha.uppercase(), encrypted = true, appVersion = "22.0"),
            ) as SidecarBackupResult.Ok
            ).value

        assertEquals("n1", upload.backup.id)
        assertEquals(9, upload.partCount)
        assertEquals("b0", upload.replaces)
        assertEquals(
            """{"slot":"manual","size":70000000,"sha256":"$sha","encrypted":true,"appVersion":"22.0"}""",
            sent.single().body,
        )
    }

    @Test
    fun withThreeByHandAndTheServerAskingTheBackupsToChooseFromComeWithTheRefusal() = runBlocking {
        answer = {
            SidecarBackupReply(409, """{"error":"Full","code":"backups_full","backups":[${backupJson("b1", createdAt = 1)},${backupJson("b2", createdAt = 2)}]}""")
        }

        val refused = client.start(server, "osb_t", SidecarBackupStart(SidecarBackupSlot.MANUAL, 10, sha, false)) as SidecarBackupResult.Failed

        assertEquals(SidecarBackupFailure.FULL, refused.failure)
        assertEquals(listOf("b1", "b2"), refused.backups.map { it.id })

        answer = { SidecarBackupReply(201, """{"backup":${backupJson("n2", state = "uploading")},"replaces":"b1","upload":{"partSize":8,"partCount":2}}""") }
        client.start(server, "osb_t", SidecarBackupStart(SidecarBackupSlot.MANUAL, 10, sha, false, replace = "b1"))
        assertTrue(sent.last().body!!.endsWith(""""replace":"b1"}"""))
    }

    @Test
    fun aBackupWithoutAWholeShaOrBytesIsNotStarted() = runBlocking {
        assertEquals(SidecarBackupFailure.UNEXPECTED, (client.start(server, "t", SidecarBackupStart(SidecarBackupSlot.AUTO, 10, "abc", false)) as SidecarBackupResult.Failed).failure)
        assertEquals(SidecarBackupFailure.UNEXPECTED, (client.start(server, "t", SidecarBackupStart(SidecarBackupSlot.AUTO, 0, sha, false)) as SidecarBackupResult.Failed).failure)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aPartGoesAsItsBytesAndFinishingNamesThePartsMissing() = runBlocking {
        answer = { SidecarBackupReply(200, "{}") }
        val bytes = "0123456789".toByteArray()

        assertEquals(SidecarBackupResult.Ok(Unit), client.putPart(server, "osb_t", "n1", 2, bytes, 3, 4))
        assertEquals(Sent("PUT", "$server/api/backups/n1/parts/2", "osb_t", "3456", bytes = 4), sent.single())

        answer = { SidecarBackupReply(409, """{"error":"Missing","code":"parts_missing","missing":[3,5]}""") }
        val missing = client.finish(server, "osb_t", "n1") as SidecarBackupResult.Failed
        assertEquals(SidecarBackupFailure.PARTS_MISSING, missing.failure)
        assertEquals(listOf(3, 5), missing.missing)

        answer = { SidecarBackupReply(200, """{"backup":${backupJson("n1")},"removed":["b0"]}""") }
        val done = (client.finish(server, "osb_t", "n1") as SidecarBackupResult.Ok).value
        assertTrue(done.ready)
        assertEquals("$server/api/backups/n1/finish", sent.last().url)
    }

    @Test
    fun anUploadLeftForADayIsGone() = runBlocking {
        answer = { SidecarBackupReply(200, """{"backup":${backupJson("n1", state = "uploading")},"upload":{"partSize":8,"partCount":3,"received":[1,3]}}""") }
        assertEquals(listOf(1, 3), (client.upload(server, "osb_t", "n1") as SidecarBackupResult.Ok).value.received)

        answer = { SidecarBackupReply(404, """{"error":"Not found"}""") }
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.GONE), client.upload(server, "osb_t", "n1"))
    }

    @Test
    fun aDownloadGoesOnFromWhereItStoppedAndBringsTheServersSha() = runBlocking {
        download = { request, out ->
            out.write("rest".toByteArray())
            SidecarBackupDownloadReply(if (request.from > 0) 206 else 200, sha256 = " ${sha.uppercase()} ")
        }
        val out = ByteArrayOutputStream()

        val result = (client.download(server, "osb_t", "b1", 100, out) as SidecarBackupResult.Ok).value

        assertEquals(true to sha, result)
        assertEquals(Sent("GET", "$server/api/backups/b1/download", "osb_t", null, from = 100), sent.single())
        assertEquals("rest", out.toString())

        download = { _, _ -> SidecarBackupDownloadReply(404, body = """{"error":"Not found"}""") }
        assertEquals(SidecarBackupResult.Failed(SidecarBackupFailure.GONE), client.download(server, "osb_t", "b1", 0, out))
    }

    @Test
    fun settingsAndSigningOutAndDeletingAreTheirOwnRequests() = runBlocking {
        answer = { SidecarBackupReply(200, """{"whenFull":"replace-oldest"}""") }
        assertEquals(SidecarBackupResult.Ok(SidecarBackupWhenFull.REPLACE_OLDEST), client.setWhenFull(server, "osb_t", SidecarBackupWhenFull.REPLACE_OLDEST))
        assertEquals(Sent("PUT", "$server/api/backups/settings", "osb_t", """{"whenFull":"replace-oldest"}"""), sent.last())

        answer = { SidecarBackupReply(204, "") }
        assertEquals(SidecarBackupResult.Ok(Unit), client.signOut(server, "osb_t", "d1"))
        assertEquals(Sent("DELETE", "$server/api/backup-devices/d1", "osb_t", null), sent.last())
        assertEquals(SidecarBackupResult.Ok(Unit), client.delete(server, "osb_t", "b1"))
        assertEquals(Sent("DELETE", "$server/api/backups/b1", "osb_t", null), sent.last())
    }
}
