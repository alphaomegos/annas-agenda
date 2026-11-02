package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.MusicProposalDto
import com.alphaomegos.annasagenda.data.MusicProposalsDto
import com.alphaomegos.annasagenda.data.sidecarJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Where a proposed track is: waiting to go, in the sidecar, or not taken (and why). */
internal enum class MusicProposalState { WAITING, SENT, REFUSED }

/** A track of the phone's proposed to a sidecar (02.11), until and after it gets there. */
internal data class MusicProposal(
    val key: String,
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val title: String,
    val artist: String?,
    val spaceId: String,
    val trackId: String? = null,
    val state: MusicProposalState = MusicProposalState.WAITING,
    val error: String? = null,
)

/**
 * The phone's proposals to the sidecar (02.11), in noBackup/music/proposals.json: the
 * wish to send is kept until it is done — over Wi-Fi only, it waits for Wi-Fi, and the
 * phone may be switched off meanwhile. A sent one stays, for the «in OURPLACE» mark
 * on the phone's list; a refused one says why and may be proposed again.
 */
internal class MusicProposals(private val file: File) {
    private val _all = MutableStateFlow(read())
    val all: StateFlow<List<MusicProposal>> = _all.asStateFlow()

    fun waiting(): List<MusicProposal> = _all.value.filter { it.state == MusicProposalState.WAITING }

    /** Proposes [p] (again, if it was refused); one already waiting or sent stays as it is. */
    @Synchronized
    fun add(p: MusicProposal) {
        val old = _all.value.firstOrNull { it.key == p.key }
        if (old != null && old.state != MusicProposalState.REFUSED) return
        _all.value = _all.value.filter { it.key != p.key } + p.copy(state = MusicProposalState.WAITING, error = null)
        save()
    }

    @Synchronized
    fun update(key: String, change: (MusicProposal) -> MusicProposal) {
        if (_all.value.none { it.key == key }) return
        _all.value = _all.value.map { if (it.key == key) change(it) else it }
        save()
    }

    private fun read(): List<MusicProposal> = runCatching {
        if (!file.isFile) return@runCatching emptyList()
        sidecarJson.decodeFromString(MusicProposalsDto.serializer(), file.readText()).proposals.map { d ->
            MusicProposal(
                key = d.key, uri = d.uri, fileName = d.fileName, mimeType = d.mimeType, sizeBytes = d.sizeBytes,
                title = d.title, artist = d.artist, spaceId = d.spaceId, trackId = d.trackId,
                state = MusicProposalState.entries.firstOrNull { it.name.equals(d.state, ignoreCase = true) } ?: MusicProposalState.WAITING,
                error = d.error,
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            val dto = MusicProposalsDto(_all.value.map { p ->
                MusicProposalDto(p.key, p.uri, p.fileName, p.mimeType, p.sizeBytes, p.title, p.artist, p.spaceId, p.trackId, p.state.name.lowercase(), p.error)
            })
            next.writeText(sidecarJson.encodeToString(MusicProposalsDto.serializer(), dto))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }
}

/** The site takes a track's file up to this size (music.audio.ts, MAX_TRACK_AUDIO_BYTES). */
internal const val MUSIC_PROPOSAL_MAX_BYTES = 50L * 1024 * 1024

/** What sending one proposal came to: in the sidecar, not taken (why), or to try again later. */
internal sealed interface MusicSendOutcome {
    data class Sent(val trackId: String) : MusicSendOutcome
    data class Refused(val why: String) : MusicSendOutcome
    data object Later : MusicSendOutcome
}

/**
 * Sends one proposal (02.11): the track made on the server if it is not yet (its id told
 * to [onTrack], so a retry only sends the file), then its file through [put]. No network,
 * an ended session, a busy or broken server: later. A file too big, gone from the phone,
 * or refused by the site: not taken, with why.
 */
internal suspend fun sendMusicProposal(
    p: MusicProposal,
    create: suspend (spaceId: String, title: String, artist: String?) -> SidecarResult<com.alphaomegos.annasagenda.model.SidecarTrack>,
    audioUrl: (trackId: String) -> String,
    cookie: String,
    put: SidecarAudioPut,
    open: () -> java.io.InputStream?,
    onTrack: (String) -> Unit,
    onSent: (Long) -> Unit,
): MusicSendOutcome {
    if (p.sizeBytes <= 0L || p.sizeBytes > MUSIC_PROPOSAL_MAX_BYTES) return MusicSendOutcome.Refused("size")
    val trackId = p.trackId ?: when (val r = create(p.spaceId, p.title, p.artist)) {
        is SidecarResult.Ok -> r.value.id.also(onTrack)
        is SidecarResult.Failed -> return when (r.failure) {
            com.alphaomegos.annasagenda.support.SidecarFailure.NO_NETWORK,
            com.alphaomegos.annasagenda.support.SidecarFailure.SIGNED_OUT,
            com.alphaomegos.annasagenda.support.SidecarFailure.SERVER,
            com.alphaomegos.annasagenda.support.SidecarFailure.TOO_MANY_ATTEMPTS -> MusicSendOutcome.Later
            else -> MusicSendOutcome.Refused(r.failure.name.lowercase())
        }
    }
    val body = runCatching { open() }.getOrNull() ?: return MusicSendOutcome.Refused("file")
    val type = p.mimeType?.takeIf { it.startsWith("audio/") } ?: "application/octet-stream"
    val url = audioUrl(trackId) + "?filename=" + java.net.URLEncoder.encode(p.fileName, "UTF-8").replace("+", "%20")
    val status = try {
        body.use { put.put(url, cookie, type, p.sizeBytes, it, onSent) }
    } catch (e: java.io.IOException) {
        return MusicSendOutcome.Later
    }
    return when {
        status in 200..299 -> MusicSendOutcome.Sent(trackId)
        status == 401 || status == 429 || status >= 500 -> MusicSendOutcome.Later
        status == 413 -> MusicSendOutcome.Refused("size")
        status == 409 -> MusicSendOutcome.Refused("space")
        else -> MusicSendOutcome.Refused("http $status")
    }
}
