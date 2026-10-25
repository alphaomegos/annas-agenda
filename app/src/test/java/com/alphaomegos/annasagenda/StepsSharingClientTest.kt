package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.*
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import com.alphaomegos.annasagenda.support.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate

/**
 * The steps API as oursidecar's patch 0059 describes it (25.10): what the
 * phone sends, and what it makes of the answers.
 */
class StepsSharingClientTest {

    private class Fake(val reply: (String, String, String?) -> SidecarBackupReply) : SidecarBackupHttp {
        val sent = mutableListOf<Triple<String, String, String?>>()
        val bearers = mutableListOf<String?>()
        override fun json(method: String, url: String, bearer: String?, json: String?): SidecarBackupReply {
            sent += Triple(method, url, json); bearers += bearer
            return reply(method, url, json)
        }
        override fun putBytes(url: String, bearer: String, bytes: ByteArray, offset: Int, length: Int) = error("not used")
        override fun download(url: String, bearer: String, from: Long, open: (Boolean) -> OutputStream, onBytes: (Long) -> Unit) = error("not used")
    }

    private val server = "https://oursidecar.com"

    @Test
    fun theNameIsKeptResetOrSet_asThreeDifferentBodies() = runBlocking {
        val http = Fake { _, _, _ -> SidecarBackupReply(200, """{"enabled":true,"displayName":"Эдик","accountName":"Эдуард","customName":true}""") }
        val client = StepsSharingClient(http, Dispatchers.Unconfined)
        val r = client.setSharing(server, "osb_t", true) as StepsSharingResult.Ok
        assertEquals("Эдик", r.value.displayName)
        client.setSharing(server, "osb_t", true, StepShareName.FromAccount)
        client.setSharing(server, "osb_t", true, StepShareName.Own("  Аня "))
        assertEquals("""{"enabled":true}""", http.sent[0].third)
        assertEquals("""{"enabled":true,"displayName":null}""", http.sent[1].third)
        assertEquals("""{"enabled":true,"displayName":"Аня"}""", http.sent[2].third)
        assertEquals("PUT" to "$server/api/steps/sharing", http.sent[0].first to http.sent[0].second)
        assertTrue(http.bearers.all { it == "osb_t" })
    }

    @Test
    fun daysGoUpAsDatesAndCounts() = runBlocking {
        val http = Fake { _, _, _ -> SidecarBackupReply(200, """{"stored":2}""") }
        val d = LocalDate.of(2026, 10, 25)
        val r = StepsSharingClient(http, Dispatchers.Unconfined)
            .uploadDays(server, "osb_t", listOf(StepDay(d.minusDays(1), 8012, StepSource.HEALTH_CONNECT), StepDay(d, 0, StepSource.MANUAL)))
        assertEquals(StepsSharingResult.Ok(2), r)
        assertEquals("""{"days":[{"day":"2026-10-24","steps":8012},{"day":"2026-10-25","steps":0}]}""", http.sent.single().third)
    }

    @Test
    fun friendsComeAsNamesAndDays_aBadDayLeftOut() = runBlocking {
        val body = """{"friends":[{"id":"u1","name":"Аня","sharing":true,"days":[{"day":"2026-10-24","steps":8012},{"day":"2026-13-01","steps":5},{"day":"2026-10-23","steps":-1}]},{"id":"u2","name":"Ира","sharing":false,"days":[]}]}"""
        val friends = (StepsSharingClient(Fake { _, _, _ -> SidecarBackupReply(200, body) }, Dispatchers.Unconfined).friends(server, "t") as StepsSharingResult.Ok).value
        assertEquals(listOf("Аня", "Ира"), friends.map { it.name })
        assertEquals(mapOf(LocalDate.of(2026, 10, 24) to 8012), friends[0].days)
        assertFalse(friends[1].sharing)
    }

    @Test
    fun anInvitationIsSentOnItsWay_whateverTheAddress() = runBlocking {
        val http = Fake { _, _, _ -> SidecarBackupReply(202, """{"ok":true}""") }
        assertEquals(StepsSharingResult.Ok(Unit), StepsSharingClient(http, Dispatchers.Unconfined).invite(server, "t", " anna@example.com "))
        assertEquals("""{"email":"anna@example.com"}""", http.sent.single().third)
    }

    @Test
    fun invitationsBothWays() = runBlocking {
        val body = """{"incoming":[{"id":"i1","fromName":"Эдуард","createdAt":"2026-10-25T09:14:03.000Z"}],"outgoing":[{"id":"o1","toName":"Аня","createdAt":"x"}]}"""
        val list = (StepsSharingClient(Fake { _, _, _ -> SidecarBackupReply(200, body) }, Dispatchers.Unconfined).invitations(server, "t") as StepsSharingResult.Ok).value
        assertEquals(listOf(true, false), list.map { it.incoming })
        assertEquals(listOf("Эдуард", "Аня"), list.map { it.name })
    }

    @Test
    fun theServersRefusalsAreTold() = runBlocking {
        fun client(status: Int, body: String, retry: String? = null) =
            StepsSharingClient(Fake { _, _, _ -> SidecarBackupReply(status, body, retry) }, Dispatchers.Unconfined)
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.SHARING_OFF), client(409, """{"error":"x","code":"sharing_off"}""").uploadDays(server, "t", emptyList()))
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.TOO_MANY_FRIENDS), client(409, """{"code":"too_many_friends"}""").accept(server, "t", "i1"))
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.TOO_MANY_INVITATIONS, 2L), client(429, "{}", "120").invite(server, "t", "a@b.c"))
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.GONE), client(404, "{}").decline(server, "t", "i1"))
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.SIGNED_OUT), client(401, "{}").friends(server, "t"))
        val offline = StepsSharingClient(Fake { _, _, _ -> throw IOException("no route") }, Dispatchers.Unconfined)
        assertEquals(StepsSharingResult.Failed(StepsSharingFailure.NO_NETWORK), offline.friends(server, "t"))
    }

    @Test
    fun pathsAndTheDaysThatGoUp() {
        assertEquals("/api/steps/invitations/a%20b/accept", stepsInvitationAcceptPath("a b"))
        assertEquals("/api/steps/friends/u1", stepsFriendPath("u1"))
        val today = LocalDate.of(2026, 10, 25)
        val days = (0L..40L).map { StepDay(today.minusDays(it), 100, StepSource.HEALTH_CONNECT) } + StepDay(today.plusDays(1), 5, StepSource.MANUAL)
        val up = stepsToShare(days, today)
        assertEquals(31, up.size)
        assertEquals(today.minusDays(30), up.first().date)
        assertEquals(today, up.last().date)
        assertTrue(stepsShareNameValid(" Аня "))
        assertFalse(stepsShareNameValid("   "))
        assertFalse(stepsShareNameValid("x".repeat(41)))
    }
}
