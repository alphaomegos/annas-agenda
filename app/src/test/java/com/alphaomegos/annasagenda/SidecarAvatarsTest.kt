package com.alphaomegos.annasagenda

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.alphaomegos.annasagenda.app.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** The person's own picture from oursidecar, fetched once and kept — through SidecarConnection (16.10). */
class SidecarAvatarsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_700_000_000_000L

    private val sealer = object : SidecarSealer {
        override fun seal(plain: String): String? = "x$plain"
        override fun open(sealed: String): String? = sealed.removePrefix("x")
        override fun forget() {}
    }

    private val http = SidecarHttp { _, url, _, _ ->
        if (url.endsWith("/api/auth/login")) {
            SidecarHttpReply(200, """{"user":{"id":"u1","email":"e@example.org"},"sidecars":[{"id":"s1","name":"Ours"}]}""", listOf("oursidecar_session=abc123; Max-Age=600"))
        } else {
            SidecarHttpReply(404, "{}")
        }
    }

    private class Asked(val url: String, val cookie: String?)

    private val asked = mutableListOf<Asked>()
    private var picture: ByteArray? = byteArrayOf(1, 2, 3)
    private val fetch = SidecarFetch { url, cookie, _ ->
        asked += Asked(url, cookie)
        picture
    }

    private val dir get() = File(folder.root, "avatars")

    private fun avatars(signIn: Boolean = true): SidecarAvatars {
        val connection = SidecarConnection(File(folder.root, "s.json"), sealer, http, clock = { now }, io = Dispatchers.Unconfined)
        if (signIn) runBlocking { connection.signIn("oursidecar.com", "e@example.org", "pw") }
        return SidecarAvatars(dir, connection, fetch, io = Dispatchers.Unconfined)
    }

    @Test
    fun aDrawnPictureIsFetchedOpenlyOnceAndThenKept() {
        val avatars = avatars()

        assertArrayEquals(byteArrayOf(1, 2, 3), runBlocking { avatars.bytes("avatar-7") })
        assertArrayEquals(byteArrayOf(1, 2, 3), runBlocking { avatars.bytes("avatar-7") })

        assertEquals(1, asked.size)
        assertEquals("https://oursidecar.com/avatars/avatar-7.png", asked.single().url)
        assertNull(asked.single().cookie)
    }

    @Test
    fun anUploadedPictureIsAskedForWithTheSession() {
        val avatars = avatars()

        runBlocking { avatars.bytes("custom:avatars/s1/u1/a.webp") }

        assertTrue(asked.single().url.startsWith("https://oursidecar.com/api/sidecars/s1/members/u1/avatar/image?v="))
        assertEquals("oursidecar_session=abc123", asked.single().cookie)
    }

    @Test
    fun anUploadedOneWithoutASessionIsNotAskedFor() {
        val avatars = avatars(signIn = false)

        assertNull(runBlocking { avatars.bytes("custom:avatars/s1/u1/a.webp") })
        assertTrue(asked.isEmpty())
    }

    @Test
    fun onlyTheLastPictureIsKeptAndNoneAfterSigningOut() {
        val avatars = avatars()
        runBlocking { avatars.bytes("avatar-7") }
        picture = byteArrayOf(9)
        runBlocking { avatars.bytes("avatar-3") }

        assertEquals(1, dir.listFiles()!!.size)

        runBlocking { avatars.forget() }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun aPictureNotToBeHadIsNoneAndNothingIsKept() {
        val avatars = avatars()
        picture = null

        assertNull(runBlocking { avatars.bytes("avatar-7") })
        assertNull(runBlocking { avatars.bytes("someone-else's") })
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
