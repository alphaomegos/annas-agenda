package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.app.MUSIC_PROPOSAL_MAX_BYTES
import com.alphaomegos.annasagenda.app.MusicProposal
import com.alphaomegos.annasagenda.app.MusicProposalState
import com.alphaomegos.annasagenda.app.MusicProposals
import com.alphaomegos.annasagenda.app.MusicSendOutcome
import com.alphaomegos.annasagenda.app.SidecarAudioPut
import com.alphaomegos.annasagenda.app.SidecarResult
import com.alphaomegos.annasagenda.app.sendMusicProposal
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.SidecarFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Proposing the phone's tracks to the sidecar (0374): kept until sent; made, then its file sent. */
class MusicProposalsTest {

    private val file = File(Files.createTempDirectory("proposals").toFile(), "music/proposals.json")
    private val p = MusicProposal(
        key = "Music/Кино/Кукушка.mp3", uri = "content://media/external/audio/media/7", fileName = "Кукушка 1990.mp3",
        mimeType = "audio/mpeg", sizeBytes = 5, title = "Кукушка", artist = "Кино", spaceId = "s1",
    )
    private val track = SidecarTrack(id = "t9", title = "Кукушка", artist = "Кино", createdByUserId = "u1", createdAt = 0, approvedAt = null, hasAudio = false)

    @Test
    fun aWishToSendIsKeptUntilItIsDone() {
        val store = MusicProposals(file)
        store.add(p)
        store.add(p.copy(title = "again"))                       // waiting: stays as it was
        store.update(p.key) { it.copy(trackId = "t9") }
        assertEquals(listOf(p.copy(trackId = "t9")), MusicProposals(file).waiting())
        store.update(p.key) { it.copy(state = MusicProposalState.SENT) }
        store.add(p)                                             // sent: not again
        assertEquals(MusicProposalState.SENT, MusicProposals(file).all.value.single().state)
        assertTrue(MusicProposals(file).waiting().isEmpty())
    }

    @Test
    fun aRefusedOneMayBeProposedAgain() {
        val store = MusicProposals(file)
        store.add(p)
        store.update(p.key) { it.copy(state = MusicProposalState.REFUSED, error = "size") }
        store.add(p.copy(title = "Кукушка (live)"))
        val again = MusicProposals(file).all.value.single()
        assertEquals(MusicProposalState.WAITING, again.state)
        assertEquals("Кукушка (live)", again.title)
        assertEquals(null, again.error)
    }

    private class Put(private val status: Int = 201, private val fail: Boolean = false) : SidecarAudioPut {
        var url: String? = null
        var type: String? = null
        var bytes: ByteArray? = null
        override fun put(url: String, cookie: String, contentType: String, length: Long, body: java.io.InputStream, onSent: (Long) -> Unit): Int {
            if (fail) throw IOException("gone")
            this.url = url
            type = contentType
            bytes = body.readBytes()
            onSent(length)
            return status
        }
    }

    private fun send(p: MusicProposal, put: Put, created: SidecarResult<SidecarTrack> = SidecarResult.Ok(track), made: MutableList<String> = mutableListOf(), open: () -> java.io.InputStream? = { ByteArrayInputStream("12345".toByteArray()) }) = runBlocking {
        sendMusicProposal(p, { _, _, _ -> made += "create"; created }, { id -> "https://oursidecar.example/api/tracks/$id/audio" }, "session=x", put, open, { made += "track:$it" }, {})
    }

    @Test
    fun theTrackIsMadeThenItsFileGoes() {
        val put = Put()
        val made = mutableListOf<String>()
        assertEquals(MusicSendOutcome.Sent("t9"), send(p, put, made = made))
        assertEquals(listOf("create", "track:t9"), made)
        assertEquals("https://oursidecar.example/api/tracks/t9/audio?filename=%D0%9A%D1%83%D0%BA%D1%83%D1%88%D0%BA%D0%B0%201990.mp3", put.url)
        assertEquals("audio/mpeg", put.type)
        assertEquals("12345", String(put.bytes!!))
    }

    @Test
    fun aRetryOnlySendsTheFile() {
        val made = mutableListOf<String>()
        assertEquals(MusicSendOutcome.Sent("t1"), send(p.copy(trackId = "t1"), Put(), made = made))
        assertEquals(emptyList<String>(), made)
    }

    @Test
    fun noNetworkOrAnEndedSessionIsLater_theSiteSayingNoIsRefused() {
        assertEquals(MusicSendOutcome.Later, send(p, Put(fail = true)))
        assertEquals(MusicSendOutcome.Later, send(p, Put(), created = SidecarResult.Failed(SidecarFailure.NO_NETWORK)))
        assertEquals(MusicSendOutcome.Later, send(p, Put(), created = SidecarResult.Failed(SidecarFailure.SIGNED_OUT)))
        assertEquals(MusicSendOutcome.Later, send(p, Put(status = 503)))
        assertEquals(MusicSendOutcome.Refused("size"), send(p, Put(status = 413)))
        assertEquals(MusicSendOutcome.Refused("space"), send(p, Put(status = 409)))
        assertEquals(MusicSendOutcome.Refused("not_found"), send(p, Put(), created = SidecarResult.Failed(SidecarFailure.NOT_FOUND)))
    }

    @Test
    fun tooBigOrGoneIsRefusedBeforeAnythingIsSent() {
        val made = mutableListOf<String>()
        assertEquals(MusicSendOutcome.Refused("size"), send(p.copy(sizeBytes = MUSIC_PROPOSAL_MAX_BYTES + 1), Put(), made = made))
        assertEquals(emptyList<String>(), made)
        assertEquals(MusicSendOutcome.Refused("file"), send(p.copy(trackId = "t1"), Put(), open = { null }))
        // A type that is not audio goes as bytes; the site reads the file's name.
        val put = Put()
        send(p.copy(mimeType = "application/ogg"), put)
        assertEquals("application/octet-stream", put.type)
    }
}
