package com.alphaomegos.annasagenda.data

import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.model.StepSharing
import kotlinx.serialization.Serializable
import java.time.LocalDate

/* oursidecar's steps API (25.10, its patch 0059), as it answers. */

@Serializable
internal data class StepSharingDto(
    val enabled: Boolean = false,
    val displayName: String = "",
    val accountName: String = "",
    val customName: Boolean = false,
)

@Serializable
internal data class StepShareDayDto(val day: String, val steps: Int)

@Serializable
internal data class StepShareDaysDto(val days: List<StepShareDayDto>)

@Serializable
internal data class StepStoredDto(val stored: Int = 0)

@Serializable
internal data class StepInviteDto(val email: String)

@Serializable
internal data class StepIncomingDto(val id: String, val fromName: String = "", val createdAt: String = "")

@Serializable
internal data class StepOutgoingDto(val id: String, val toName: String = "", val createdAt: String = "")

@Serializable
internal data class StepInvitationsDto(
    val incoming: List<StepIncomingDto> = emptyList(),
    val outgoing: List<StepOutgoingDto> = emptyList(),
)

@Serializable
internal data class StepFriendDto(
    val id: String,
    val name: String = "",
    val sharing: Boolean = false,
    val days: List<StepShareDayDto> = emptyList(),
)

@Serializable
internal data class StepFriendsDto(val friends: List<StepFriendDto> = emptyList())

@Serializable
internal data class StepAcceptedDto(val friend: StepFriendDto)

@Serializable
internal data class StepErrorDto(val error: String? = null, val code: String? = null)

internal fun StepSharingDto.toDomain() = StepSharing(enabled, displayName, accountName, customName)

/** A friend as the server describes them; a day that is not a real date, or negative, is left out. */
internal fun StepFriendDto.toDomain() = StepFriend(
    id = id,
    name = name,
    sharing = sharing,
    days = days.mapNotNull { d ->
        val date = runCatching { LocalDate.parse(d.day) }.getOrNull()
        if (date == null || d.steps < 0) null else date to d.steps
    }.toMap(),
)

internal fun StepInvitationsDto.toDomain(): List<StepInvitation> =
    incoming.map { StepInvitation(it.id, it.fromName, incoming = true, createdAt = it.createdAt) } +
        outgoing.map { StepInvitation(it.id, it.toName, incoming = false, createdAt = it.createdAt) }
