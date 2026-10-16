package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.model.SidecarMember
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.SidecarFailure
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
)

/**
 * The music of the chosen sidecar, asked of the server through [connection]
 * (16.10). One for the process (MusicLibraries), so the list the screen shows
 * is the one the player plays from. A failed asking keeps the list that was
 * there and says why; another sidecar starts from nothing.
 */
internal class MusicLibrary(private val connection: SidecarConnection) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(MusicLibraryState())
    val state: StateFlow<MusicLibraryState> = _state.asStateFlow()

    /** The people and tracks of [spaceId], asked again. */
    suspend fun refresh(spaceId: String) = mutex.withLock {
        _state.update {
            if (it.spaceId != spaceId) MusicLibraryState(spaceId = spaceId, loading = true) else it.copy(loading = true, failure = null)
        }
        val members = connection.members(spaceId)
        val tracks = if (members is SidecarResult.Ok) connection.tracks(spaceId) else null
        _state.update { s ->
            when {
                s.spaceId != spaceId -> s
                members is SidecarResult.Failed -> s.copy(loading = false, failure = members.failure)
                tracks is SidecarResult.Failed -> s.copy(loading = false, failure = tracks.failure)
                members is SidecarResult.Ok && tracks is SidecarResult.Ok ->
                    s.copy(members = members.value, tracks = tracks.value, loaded = true, loading = false, failure = null)
                else -> s.copy(loading = false, failure = SidecarFailure.UNEXPECTED)
            }
        }
    }

    /** Nothing kept: the account signed out, or another came in. */
    fun forget() {
        _state.value = MusicLibraryState()
    }
}
