package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.model.SidecarMember
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.SidecarFailure
import com.alphaomegos.annasagenda.data.MusicLibrarySnapshotDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.data.toDto
import com.alphaomegos.annasagenda.support.sidecarPathSegment
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A sidecar's music as the server last gave it (16.10): its people and its
 * tracks, whether a list is on its way, and what went wrong the last time.
 * [loaded] says a list has come for [spaceId] at all — an empty sidecar and
 * one not asked yet look different.
 */
internal data class MusicLibraryState(
    val spaceId: String? = null,
    val members: List<SidecarMember> = emptyList(),
    val tracks: List<SidecarTrack> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failure: SidecarFailure? = null,
    // The list is the one kept on the phone, not yet asked again of the server.
    val fromPhone: Boolean = false,
)

/**
 * A sidecar's list kept on the phone (16.10), so the music section opens at
 * once and offline — where the kept tracks play. In the no-backup folder,
 * one file a sidecar; forgotten on signing out.
 */
internal class MusicLibrarySnapshots(private val dir: File) {

    fun read(spaceId: String): Pair<List<SidecarMember>, List<SidecarTrack>>? = runCatching {
        val file = fileOf(spaceId)
        if (!file.isFile) return null
        val dto = sidecarJson.decodeFromString(MusicLibrarySnapshotDto.serializer(), file.readText())
        if (dto.spaceId != spaceId) null else dto.members.map { it.toDomain() } to dto.tracks.map { it.toDomain() }
    }.getOrNull()

    fun write(spaceId: String, members: List<SidecarMember>, tracks: List<SidecarTrack>) {
        runCatching {
            dir.mkdirs()
            val file = fileOf(spaceId)
            val next = File(dir, file.name + ".next")
            next.writeText(
                sidecarJson.encodeToString(
                    MusicLibrarySnapshotDto.serializer(),
                    MusicLibrarySnapshotDto(spaceId, members.map { it.toDto() }, tracks.map { it.toDto() }),
                ),
            )
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    fun forget() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** The sidecar's id as a file's name, whatever letters it has: every one not plain is escaped. */
    private fun fileOf(spaceId: String) = File(dir, "library-" + sidecarPathSegment(spaceId).replace('%', '_') + ".json")
}

/**
 * The music of the chosen sidecar, asked of the server through [connection]
 * (16.10). One for the process (MusicLibraries), so the list the screen shows
 * is the one the player plays from. A failed asking keeps the list that was
 * there and says why; another sidecar starts from nothing.
 */
internal class MusicLibrary(private val connection: SidecarConnection, private val snapshots: MusicLibrarySnapshots? = null) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(MusicLibraryState())
    val state: StateFlow<MusicLibraryState> = _state.asStateFlow()

    /** The people and tracks of [spaceId], asked again. */
    suspend fun refresh(spaceId: String) = mutex.withLock {
        _state.update {
            if (it.spaceId != spaceId) MusicLibraryState(spaceId = spaceId, loading = true) else it.copy(loading = true, failure = null)
        }
        // The list kept on the phone, at once, while the server is asked.
        if (!_state.value.loaded) {
            snapshots?.read(spaceId)?.let { (members, tracks) ->
                _state.update { s -> if (s.spaceId == spaceId && !s.loaded) s.copy(members = members, tracks = tracks, loaded = true, fromPhone = true) else s }
            }
        }
        val members = connection.members(spaceId)
        val tracks = if (members is SidecarResult.Ok) connection.tracks(spaceId) else null
        if (members is SidecarResult.Ok && tracks is SidecarResult.Ok) snapshots?.write(spaceId, members.value, tracks.value)
        _state.update { s ->
            when {
                s.spaceId != spaceId -> s
                members is SidecarResult.Failed -> s.copy(loading = false, failure = members.failure)
                tracks is SidecarResult.Failed -> s.copy(loading = false, failure = tracks.failure)
                members is SidecarResult.Ok && tracks is SidecarResult.Ok ->
                    s.copy(members = members.value, tracks = tracks.value, loaded = true, loading = false, failure = null, fromPhone = false)
                else -> s.copy(loading = false, failure = SidecarFailure.UNEXPECTED)
            }
        }
    }

    /** Nothing kept, here or on the phone: the account signed out, or another came in. */
    fun forget() {
        _state.value = MusicLibraryState()
        snapshots?.forget()
    }
}
