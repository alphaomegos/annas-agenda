package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.alphaomegos.annasagenda.app.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** This phone's oursidecar sign-in against a server that answers as the real one does (16.10). */
class SidecarConnectionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val token = "c0ffee00deadbeef1234567890abcdef"
    private val password = "correct horse battery"

    private class Sent(val method: String, val url: String, val cookie: String?, val json: String?)

    /** Seals by turning the token round; a test cannot reach a keystore. */
    private class FakeSealer(private val works: Boolean = true) : SidecarSealer {
        var forgotten = false
        override fun seal(plain: String): String? = if (works) "sealed:" + plain.reversed() else null
        override fun open(sealed: String): String? = sealed.removePrefix("sealed:").reversed().takeIf { sealed.startsWith("sealed:") }
        override fun forget() {
            forgotten = true
        }
    }

    private val sent = mutableListOf<Sent>()
    private var answer: (Sent) -> SidecarHttpReply = { SidecarHttpReply(404, "{}") }
    private val http = SidecarHttp { method, url, cookie, json ->
        val request = Sent(method, url, cookie, json)
        sent += request
        answer(request)
    }

    private val accountJson = """
        {"user":{"id":"u1","email":"eduard@example.org"},
         "sidecars":[{"id":"s1","name":"Our place","creator_user_id":"u1","created_at":1},{"id":"s2","name":"Other","creator_user_id":"u9","created_at":2}],
         "invitations":[],"canCreateSidecar":false}
    """.trimIndent()

    private fun signedInReply() = SidecarHttpReply(
        200,
        accountJson,
        listOf("oursidecar_session=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000; Secure"),
    )

    private fun file() = File(folder.root, "sidecar/session.json")

    private fun connection(sealer: SidecarSealer = FakeSealer(), at: Long = now) =
        SidecarConnection(file(), sealer, http, clock = { at }, io = Dispatchers.Unconfined)

    private fun signedIn(sealer: SidecarSealer = FakeSealer()): SidecarConnection {
        val c = connection(sealer)
        answer = { signedInReply() }
        runBlocking { c.signIn("oursidecar.com", " eduard@example.org ", password) }
        sent.clear()
        return c
    }

    @Test
    fun signingInKeepsTheAccountItsSidecarsAndTheSessionsEnd() {
        val c = connection()
        answer = { signedInReply() }

        val result = runBlocking { c.signIn("oursidecar.com", " eduard@example.org ", password) }

        assertEquals(SidecarResult.Ok(Unit), result)
        val request = sent.first()
        assertEquals("POST", request.method)
        assertEquals("https://oursidecar.com/api/auth/login", request.url)
        assertNull(request.cookie)
        assertTrue(request.json!!.contains("\"email\":\"eduard@example.org\""))
        val s = c.status.value
        assertEquals(SidecarAccount("u1", "eduard@example.org"), s.account)
        assertEquals(listOf("s1", "s2"), s.spaces.map { it.id })
        assertEquals("s1", s.space?.id)
        assertEquals(now + 2_592_000_000L, s.sessionEndsAtMillis)
        assertEquals(SidecarSignInState.SIGNED_IN, s.signIn(now))
    }

    @Test
    fun theFileKeepsTheTokenSealedAndThePasswordNotAtAll() {
        signedIn()

        val text = file().readText()

        assertFalse(text.contains(token))
        assertFalse(text.contains(password))
        assertTrue(text.contains("eduard@example.org"))
    }

    @Test
    fun theNextStartKnowsTheSignInAndStreamsWithTheToken() {
        signedIn()

        val again = connection()

        assertEquals("u1", again.status.value.account?.userId)
        assertEquals(SidecarSignInState.SIGNED_IN, again.status.value.signIn(now))
        assertEquals("oursidecar_session=$token", runBlocking { again.cookieHeader() })
        assertEquals("https://oursidecar.com/api/tracks/t%201/audio", again.audioUrl("t 1"))
    }

    @Test
    fun aWrongPasswordOrTooManyTriesOrNoNetworkSignNobodyIn() {
        val c = connection()

        answer = { SidecarHttpReply(401, """{"error":"Incorrect email or password."}""") }
        assertEquals(SidecarResult.Failed(SidecarFailure.WRONG_PASSWORD), runBlocking { c.signIn("oursidecar.com", "a@b.c", "x") })

        answer = { SidecarHttpReply(429, "{}", retryAfter = "125") }
        assertEquals(SidecarResult.Failed(SidecarFailure.TOO_MANY_ATTEMPTS, 3), runBlocking { c.signIn("oursidecar.com", "a@b.c", "x") })

        answer = { throw IOException("offline") }
        assertEquals(SidecarResult.Failed(SidecarFailure.NO_NETWORK), runBlocking { c.signIn("oursidecar.com", "a@b.c", "x") })

        assertEquals(SidecarSignInState.SIGNED_OUT, c.status.value.signIn(now))
        assertFalse(file().exists())
    }

    @Test
    fun anAnswerWithoutTheCookieOrOverPlainHttpIsNotASignIn() {
        val c = connection()
        answer = { SidecarHttpReply(200, accountJson) }

        assertEquals(SidecarResult.Failed(SidecarFailure.UNEXPECTED), runBlocking { c.signIn("oursidecar.com", "a@b.c", "x") })
        assertEquals(SidecarResult.Failed(SidecarFailure.UNEXPECTED), runBlocking { c.signIn("http://oursidecar.com", "a@b.c", "x") })
        assertEquals(1, sent.size)
        assertNull(c.status.value.account)
    }

    @Test
    fun theTracksComeWithTheSessionAndAreReadAsTheServerWritesThem() {
        val c = signedIn()
        answer = {
            SidecarHttpReply(
                200,
                """{"tracks":[{"id":"t1","sidecar_id":"s1","created_by_user_id":"u2","created_by_email":"anna@example.org",
                  "title":"Lift Me Up","artist":"Moby","audio_key":"k","audio_filename":"lift.mp3","audio_content_type":"audio/mpeg",
                  "audio_size":7340032,"audio_etag":"abc","approved_at":null,"approved_by_user_id":null,"approved_by_email":null,
                  "created_at":1760000000,"updated_at":1760000000,"hasAudio":true,"pendingApproval":true,"approvalVotes":1,
                  "myApprovalVote":false,"proposedTrackEdit":null,"someFieldAddedLater":[1,2]}]}""",
            )
        }

        val result = runBlocking { c.tracks("s1") }

        val request = sent.single()
        assertEquals("https://oursidecar.com/api/sidecars/s1/tracks", request.url)
        assertEquals("oursidecar_session=$token", request.cookie)
        val track = (result as SidecarResult.Ok).value.single()
        assertEquals(
            SidecarTrack(
                id = "t1",
                title = "Lift Me Up",
                artist = "Moby",
                createdByUserId = "u2",
                createdAt = 1_760_000_000L,
                approvedAt = null,
                hasAudio = true,
                audioSize = 7_340_032L,
                audioEtag = "abc",
                audioFilename = "lift.mp3",
                audioContentType = "audio/mpeg",
                pendingApproval = true,
            ),
            track,
        )
    }

    @Test
    fun thePeopleComeInTheOrderTheyJoined() {
        val c = signedIn()
        answer = {
            SidecarHttpReply(
                200,
                """{"sidecar":{"id":"s1"},"members":[
                  {"id":"u2","email":"anna@example.org","nickname":null,"avatar_id":"avatar-3","joined_at":20},
                  {"id":"u1","email":"eduard@example.org","nickname":"Эдик","avatar_id":null,"joined_at":10}],
                  "isCreator":true,"pendingInvitation":null}""",
            )
        }

        val members = (runBlocking { c.members("s1") } as SidecarResult.Ok).value

        assertEquals(listOf("u1", "u2"), members.map { it.id })
        assertEquals("Эдик", members[0].nickname)
        assertEquals("avatar-3", members[1].avatarId)
    }

    @Test
    fun aRefusedSessionAsksForThePasswordAgainAndIsRememberedSo() {
        val c = signedIn()
        answer = { SidecarHttpReply(401, """{"error":"Not authenticated."}""") }

        assertEquals(SidecarResult.Failed(SidecarFailure.SIGNED_OUT), runBlocking { c.tracks("s1") })

        assertEquals(SidecarSignInState.ATTENTION, c.status.value.signIn(now))
        assertEquals(SidecarSignInState.ATTENTION, connection().status.value.signIn(now))
        assertEquals("eduard@example.org", connection().status.value.account?.email)
    }

    @Test
    fun signingInAgainClearsTheRefusalAndKeepsTheChosenSidecar() {
        val c = signedIn()
        runBlocking { c.chooseSpace("s2") }
        answer = { SidecarHttpReply(401, "{}") }
        runBlocking { c.tracks("s2") }

        answer = { signedInReply() }
        runBlocking { c.signIn("oursidecar.com", "eduard@example.org", password) }

        assertEquals(SidecarSignInState.SIGNED_IN, c.status.value.signIn(now))
        assertEquals("s2", c.status.value.space?.id)
    }

    @Test
    fun aSessionRunOutAsksNothingOfTheServer() {
        signedIn()
        val later = connection(at = now + 2_592_000_000L)

        assertEquals(SidecarResult.Failed(SidecarFailure.SIGNED_OUT), runBlocking { later.tracks("s1") })

        assertTrue(sent.isEmpty())
        assertEquals(SidecarSignInState.ATTENTION, later.status.value.signIn(now + 2_592_000_000L))
    }

    @Test
    fun signingOutTellsTheServerAndForgetsEverythingButTheAddress() {
        val sealer = FakeSealer()
        val c = signedIn(sealer)
        answer = { SidecarHttpReply(200, """{"ok":true}""", listOf("oursidecar_session=; Path=/; Max-Age=0")) }

        runBlocking { c.signOut() }

        val request = sent.single()
        assertEquals("https://oursidecar.com/api/auth/logout", request.url)
        assertEquals("oursidecar_session=$token", request.cookie)
        assertTrue(sealer.forgotten)
        assertEquals(SidecarStatus(server = "https://oursidecar.com"), c.status.value)
        assertEquals(SidecarSignInState.SIGNED_OUT, connection().status.value.signIn(now))
        assertNull(runBlocking { connection().cookieHeader() })
    }

    @Test
    fun signingOutOfflineStillSignsOutHere() {
        val c = signedIn()
        answer = { throw IOException("offline") }

        runBlocking { c.signOut() }

        assertNull(c.status.value.account)
    }

    @Test
    fun aPhoneThatCannotSealPlaysThisTimeAndAsksNextTime() {
        val c = signedIn(FakeSealer(works = false))

        assertEquals("oursidecar_session=$token", runBlocking { c.cookieHeader() })
        assertFalse(file().readText().contains(token))

        val next = connection(FakeSealer(works = false))
        assertEquals(SidecarSignInState.ATTENTION, next.status.value.signIn(now))
    }

    @Test
    fun aJoinedSidecarAppearsAndAGoneOneIsNoLongerChosen() {
        val c = signedIn()
        runBlocking { c.chooseSpace("s2") }
        sent.clear()
        answer = { SidecarHttpReply(200, """{"user":{"id":"u1","email":"eduard@example.org"},"sidecars":[{"id":"s1","name":"Our place"},{"id":"s3","name":"New"}]}""") }

        assertEquals(SidecarResult.Ok(Unit), runBlocking { c.refreshAccount() })

        assertEquals("https://oursidecar.com/api/me", sent.first().url)
        assertEquals(listOf("s1", "s3"), c.status.value.spaces.map { it.id })
        assertEquals("s1", c.status.value.space?.id)
    }

    private val membersOfOurs = """{"members":[{"id":"u1","email":"eduard@example.org","avatar_id":"avatar-7","joined_at":1},
        {"id":"u2","email":"anna@example.org","avatar_id":"avatar-3","joined_at":2}]}"""

    @Test
    fun signingInAsksForThePersonsOwnPictureInTheChosenSidecar() {
        val c = connection()
        answer = { r -> if (r.url.endsWith("/api/sidecars/s1")) SidecarHttpReply(200, membersOfOurs) else signedInReply() }

        runBlocking { c.signIn("oursidecar.com", "eduard@example.org", password) }

        assertEquals("https://oursidecar.com/api/sidecars/s1", sent.last().url)
        assertEquals("oursidecar_session=$token", sent.last().cookie)
        assertEquals("avatar-7", c.status.value.avatarId)
        assertEquals("avatar-7", connection().status.value.avatarId)
    }

    @Test
    fun aPictureNotToBeHadKeepsTheOneKnownAndAnotherSidecarHasItsOwn() {
        val c = connection()
        answer = { r -> if (r.url.endsWith("/api/sidecars/s1")) SidecarHttpReply(200, membersOfOurs) else signedInReply() }
        runBlocking { c.signIn("oursidecar.com", "eduard@example.org", password) }

        answer = { throw IOException("offline") }
        runBlocking { c.refreshAccount() }
        assertEquals("avatar-7", c.status.value.avatarId)

        answer = { r ->
            if (r.url.endsWith("/api/sidecars/s2")) {
                SidecarHttpReply(200, """{"members":[{"id":"u1","email":"e","avatar_id":"custom:avatars/s2/u1/a.webp"}]}""")
            } else {
                SidecarHttpReply(404, "{}")
            }
        }
        runBlocking { c.chooseSpace("s2") }
        assertEquals("custom:avatars/s2/u1/a.webp", c.status.value.avatarId)
    }

    @Test
    fun signingOutForgetsThePicture() {
        val c = connection()
        answer = { r -> if (r.url.endsWith("/api/sidecars/s1")) SidecarHttpReply(200, membersOfOurs) else signedInReply() }
        runBlocking { c.signIn("oursidecar.com", "eduard@example.org", password) }

        answer = { SidecarHttpReply(200, "{}") }
        runBlocking { c.signOut() }

        assertNull(c.status.value.avatarId)
        assertNull(connection().status.value.avatarId)
    }

    @Test
    fun aBrokenFileIsNobodySignedIn() {
        file().parentFile!!.mkdirs()
        file().writeText("{not json")

        assertEquals(SidecarSignInState.SIGNED_OUT, connection().status.value.signIn(now))
    }
}
