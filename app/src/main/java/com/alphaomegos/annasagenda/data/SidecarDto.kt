package com.alphaomegos.annasagenda.data

import com.alphaomegos.annasagenda.model.MusicPlayerMemory
import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.model.SidecarAccount
import com.alphaomegos.annasagenda.model.SidecarMember
import com.alphaomegos.annasagenda.model.SidecarSpace
import com.alphaomegos.annasagenda.model.SidecarTrack
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What oursidecar's server answers (16.10), as its own handlers write it:
 * worker/core/auth.ts (accountPayload), worker/modules/sidecars.ts
 * (getSidecar) and worker/modules/music.ts (getTracks). Only the fields
 * this app uses are named; the rest are let past.
 */

/** Read leniently: a field the server adds later is no reason to stop playing. */
@OptIn(ExperimentalSerializationApi::class)
internal val sidecarJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** The answer to signing in and to /api/me. */
@Serializable
internal data class SidecarAccountReplyDto(
    val user: SidecarUserDto? = null,
    val sidecars: List<SidecarSpaceDto> = emptyList(),
)

@Serializable
internal data class SidecarUserDto(
    val id: String,
    val email: String = "",
)

@Serializable
internal data class SidecarSpaceDto(
    val id: String,
    val name: String = "",
)

/** The answer to /api/sidecars/{id}: who is in it. */
@Serializable
internal data class SidecarSpaceReplyDto(
    val members: List<SidecarMemberDto> = emptyList(),
)

@Serializable
internal data class SidecarMemberDto(
    val id: String,
    val email: String = "",
    val nickname: String? = null,
    @SerialName("avatar_id")
    val avatarId: String? = null,
    @SerialName("joined_at")
    val joinedAt: Long = 0L,
)

/** The answer to /api/sidecars/{id}/tracks. */
@Serializable
internal data class SidecarTracksReplyDto(
    val tracks: List<SidecarTrackDto> = emptyList(),
)

@Serializable
internal data class SidecarTrackDto(
    val id: String,
    val title: String = "",
    val artist: String? = null,
    @SerialName("created_by_user_id")
    val createdByUserId: String = "",
    @SerialName("created_at")
    val createdAt: Long = 0L,
    @SerialName("approved_at")
    val approvedAt: Long? = null,
    val hasAudio: Boolean = false,
    @SerialName("audio_size")
    val audioSize: Long? = null,
    @SerialName("audio_etag")
    val audioEtag: String? = null,
    @SerialName("audio_filename")
    val audioFilename: String? = null,
    @SerialName("audio_content_type")
    val audioContentType: String? = null,
    val pendingApproval: Boolean = false,
)

internal fun SidecarUserDto.toDomain() = SidecarAccount(userId = id, email = email)

internal fun SidecarSpaceDto.toDomain() = SidecarSpace(id = id, name = name)

internal fun SidecarSpace.toDto() = SidecarSpaceDto(id = id, name = name)

internal fun SidecarMemberDto.toDomain() = SidecarMember(
    id = id,
    email = email,
    nickname = nickname,
    avatarId = avatarId,
    joinedAt = joinedAt,
)

internal fun SidecarTrackDto.toDomain() = SidecarTrack(
    id = id,
    title = title,
    artist = artist,
    createdByUserId = createdByUserId,
    createdAt = createdAt,
    approvedAt = approvedAt,
    hasAudio = hasAudio,
    audioSize = audioSize,
    audioEtag = audioEtag,
    audioFilename = audioFilename,
    audioContentType = audioContentType,
    pendingApproval = pendingApproval,
)

/**
 * What this phone keeps of its oursidecar sign-in (16.10), in the app's
 * no-backup folder: neither the app's export nor Android's backup takes it.
 * The token is sealed with a key of the phone's keystore; the password is
 * never kept at all.
 */
@Serializable
internal data class SidecarDeviceDto(
    val server: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val sealedToken: String? = null,
    val expiresAtMillis: Long = 0L,
    val rejected: Boolean = false,
    val spaces: List<SidecarSpaceDto> = emptyList(),
    val spaceId: String? = null,
)

/** Where the player was (16.10), in the no-backup folder: MusicPlayerMemory. */
@Serializable
internal data class MusicPlayerMemoryDto(
    val spaceId: String,
    val queueIds: List<String> = emptyList(),
    val currentId: String? = null,
    val positionMillis: Long = 0L,
    val shuffle: Boolean = false,
    // MusicRepeat by name.
    val repeat: String = "ALL",
)

internal fun MusicPlayerMemory.toDto() = MusicPlayerMemoryDto(spaceId, queueIds, currentId, positionMillis, shuffle, repeat.name)

internal fun MusicPlayerMemoryDto.toDomain() = MusicPlayerMemory(
    spaceId = spaceId,
    queueIds = queueIds.filter { it.isNotBlank() },
    currentId = currentId?.takeIf { it.isNotBlank() },
    positionMillis = positionMillis.coerceAtLeast(0L),
    shuffle = shuffle,
    repeat = MusicRepeat.entries.firstOrNull { it.name == repeat } ?: MusicRepeat.ALL,
)
