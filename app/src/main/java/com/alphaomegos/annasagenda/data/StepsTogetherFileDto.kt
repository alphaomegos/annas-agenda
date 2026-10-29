package com.alphaomegos.annasagenda.data

import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.support.StepFriendPref
import com.alphaomegos.annasagenda.support.StepsTogetherState
import kotlinx.serialization.Serializable

/** Steps together on the phone (25.10): its own file, outside the app's state and its backups. */
@Serializable
internal data class StepsTogetherFileDto(
    val sharing: StepSharingDto? = null,
    val friends: List<StepFriendDto> = emptyList(),
    val invitations: StepInvitationsDto = StepInvitationsDto(),
    val prefs: List<StepFriendPrefDto> = emptyList(),
    val selfShown: Boolean = true,
    val notified: List<String> = emptyList(),
    val syncedAt: String? = null,
)

@Serializable
internal data class StepFriendPrefDto(val id: String, val color: Int = 0, val shown: Boolean = true, val alias: String? = null)

internal fun StepsTogetherFileDto.toState() = StepsTogetherState(
    sharing = sharing?.toDomain(),
    friends = friends.map { it.toDomain() },
    invitations = invitations.toDomain(),
    prefs = prefs.associate { it.id to StepFriendPref(it.color.coerceIn(0, 3), it.shown, it.alias) },
    selfShown = selfShown,
    notified = notified.toSet(),
    syncedAt = syncedAt,
)

internal fun StepsTogetherState.toFileDto() = StepsTogetherFileDto(
    sharing = sharing?.let { StepSharingDto(it.enabled, it.displayName, it.accountName, it.customName) },
    friends = friends.map { it.toDto() },
    invitations = StepInvitationsDto(
        incoming = invitations.filter { it.incoming }.map { StepIncomingDto(it.id, it.name, it.createdAt) },
        outgoing = invitations.filter { !it.incoming }.map { StepOutgoingDto(it.id, it.name, it.createdAt) },
    ),
    prefs = prefs.map { (id, p) -> StepFriendPrefDto(id, p.color, p.shown, p.alias) },
    selfShown = selfShown,
    notified = notified.sorted(),
    syncedAt = syncedAt,
)

internal fun StepFriend.toDto() = StepFriendDto(
    id = id,
    name = name,
    sharing = sharing,
    days = days.entries.sortedBy { it.key }.map { StepShareDayDto(it.key.toString(), it.value) },
)
