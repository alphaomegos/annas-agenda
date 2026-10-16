package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.alphaomegos.annasagenda.app.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.model.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** A sidecar's music asked of a server that answers as the real one does — through SidecarConnection (16.10). */
class MusicLibraryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private var answer: (String) -> SidecarHttpReply = { SidecarHttpReply(404, "{}") }

    private val http = SidecarHttp { _, url, _, _ ->
        if (url.endsWith("/api/auth/login")) {
            SidecarHttpReply(
                200,
                """{"user":{"id":"u1","email":"e@example.org"},"sidecars":[{"id":"s1","name":"Ours"}]}""",
                listOf("oursidecar_session=abc123; Max-Age=600"),
            )
        } else {
            answer(url)
        }
    }

    private val sealer = object : SidecarSealer {
        override fun seal(plain: String): String? = "x$plain"
        override fun open(sealed: String): String? = sealed.removePrefix("x")
        override fun forget() {}
    }

    private fun library(): MusicLibrary {
        val connection = SidecarConnection(File(folder.root, "s.json"), sealer, http, clock = { now }, io = Dispatchers.Unconfined)
        runBlocking { connection.signIn("oursidecar.com", "e@example.org", "pw") }
        return MusicLibrary(connection)
    }

    private val members = """{"members":[{"id":"u1","email":"e@example.org","joined_at":1},{"id":"u2","email":"a@example.org","joined_at":2}]}"""
    private val tracks = """{"tracks":[{"id":"t1","title":"One","created_by_user_id":"u1","hasAudio":true,"audio_etag":"e1","approved_at":5}]}"""

    private fun server(url: String): SidecarHttpReply = when {
        url.endsWith("/api/sidecars/s1") -> SidecarHttpReply(200, members)
        url.endsWith("/api/sidecars/s1/tracks") -> SidecarHttpReply(200, tracks)
        else -> SidecarHttpReply(404, "{}")
    }

    @Test
    fun theSidecarsPeopleAndTracksArrive() {
        val library = library()
        answer = ::server

        runBlocking { library.refresh("s1") }

        val s = library.state.value
        assertTrue(s.loaded)
        assertFalse(s.loading)
        assertEquals(null, s.failure)
        assertEquals(listOf("u1", "u2"), s.members.map { it.id })
        assertEquals(listOf("t1"), s.tracks.map { it.id })
    }

    @Test
    fun aFailedAskingKeepsTheListThatWasAndSaysWhy() {
        val library = library()
        answer = ::server
        runBlocking { library.refresh("s1") }

        answer = { throw IOException("offline") }
        runBlocking { library.refresh("s1") }

        val s = library.state.value
        assertEquals(SidecarFailure.NO_NETWORK, s.failure)
        assertEquals(listOf("t1"), s.tracks.map { it.id })
        assertTrue(s.loaded)
        assertFalse(s.loading)
    }

    @Test
    fun anotherSidecarStartsFromNothing() {
        val library = library()
        answer = ::server
        runBlocking { library.refresh("s1") }

        runBlocking { library.refresh("s2") }

        val s = library.state.value
        assertEquals("s2", s.spaceId)
        assertTrue(s.tracks.isEmpty())
        assertFalse(s.loaded)
        assertEquals(SidecarFailure.NOT_FOUND, s.failure)
    }

    @Test
    fun whereThePlayerWasIsKeptAndReadBack() {
        val memoryFile = MusicPlayerMemoryFile(File(folder.root, "music/player.json"))
        val memory = MusicPlayerMemory("s1", listOf("t2", "t1"), "t1", 95_000L, shuffle = true, repeat = MusicRepeat.ONE)

        assertEquals(null, memoryFile.read())
        memoryFile.write(memory)
        assertEquals(memory, memoryFile.read())

        File(folder.root, "music/player.json").writeText("{broken")
        assertEquals(null, memoryFile.read())

        memoryFile.write(memory)
        memoryFile.forget()
        assertEquals(null, memoryFile.read())
    }

    @Test
    fun whatSaveAllIsToFetchIsWrittenDownWhole() {
        val plan = MusicSavePlanFile(File(folder.root, "music/save-all.json"))
        val items = listOf(MusicSaveItem("t1", "track:t1:e", 7L, "https://oursidecar.com/api/tracks/t1/audio"))

        assertEquals(null, plan.read())
        plan.write(items)
        assertEquals(items, plan.read())
        plan.forget()
        assertEquals(null, plan.read())
    }

    @Test
    fun anEndedSessionIsSaidSoAndForgettingEmptiesIt() {
        val library = library()
        answer = ::server
        runBlocking { library.refresh("s1") }

        answer = { SidecarHttpReply(401, "{}") }
        runBlocking { library.refresh("s1") }
        assertEquals(SidecarFailure.SIGNED_OUT, library.state.value.failure)

        library.forget()
        assertEquals(MusicLibraryState(), library.state.value)
    }
}
