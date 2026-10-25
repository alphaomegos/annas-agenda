package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.model.StepSharing

/*
 * Steps together (25.10): what the phone knows of it between syncs. Kept in
 * a file of its own beside the app's state, never in it — the friends' days
 * are theirs, and the app's backups carry only what is the user's own.
 */

/** How a friend is drawn here: their colour (0–3, of four) and whether the chart shows them. */
data class StepFriendPref(val color: Int, val shown: Boolean = true)

data class StepsTogetherState(
    val sharing: StepSharing? = null,
    val friends: List<StepFriend> = emptyList(),
    val invitations: List<StepInvitation> = emptyList(),
    val prefs: Map<String, StepFriendPref> = emptyMap(),
    // The user's own steps on the chart beside the friends'.
    val selfShown: Boolean = true,
    // The incoming invitations already told of, so each is told once.
    val notified: Set<String> = emptySet(),
    val syncedAt: String? = null,
    // What stopped the last sync; not kept.
    val failure: StepsSharingFailure? = null,
    // Signed in to oursidecar for backups, as of the last sync; not kept.
    val signedIn: Boolean = false,
)

/**
 * Each friend's colour and visibility: the ones known keep theirs, a new one
 * takes the first colour no one has, and a friend gone takes theirs away.
 */
fun stepFriendPrefsFor(friends: List<StepFriend>, prefs: Map<String, StepFriendPref>): Map<String, StepFriendPref> {
    val kept = prefs.filterKeys { id -> friends.any { it.id == id } }.toMutableMap()
    friends.filter { it.id !in kept }.forEach { friend ->
        val taken = kept.values.map { it.color }.toSet()
        val free = (0 until MAX_STEP_FRIENDS).firstOrNull { it !in taken } ?: 0
        kept[friend.id] = StepFriendPref(color = free)
    }
    return kept
}

/** The incoming invitations not told of yet. */
fun stepInvitationsToNotify(invitations: List<StepInvitation>, notified: Set<String>): List<StepInvitation> =
    invitations.filter { it.incoming && it.id !in notified }

/** The ids to remember as told of: the ones still waiting, so the set does not grow forever. */
fun stepInvitationsNotifiedAfter(invitations: List<StepInvitation>, notified: Set<String>): Set<String> =
    invitations.filter { it.incoming }.map { it.id }.toSet()
        .let { waiting -> (notified + waiting).filter { it in waiting }.toSet() }
