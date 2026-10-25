package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.StepDay
import java.time.LocalDate

/*
 * Steps together (25.10): the paths of oursidecar's steps API (its patch 0059,
 * «Шаги вместе: API для Anna's Agenda»), what its answers mean, and which days
 * go up. The phone signs in as for backups and sends the same bearer token.
 */

/** Friends besides oneself: «me and four friends» (Eduard, 25.10); the server holds to it too. */
const val MAX_STEP_FRIENDS = 4

/** The longest name friends see, as the server takes it. */
const val STEPS_SHARE_NAME_MAX = 40

/** The days the server keeps and shows: today and the thirty before it. */
const val STEPS_SHARE_DAYS = 30

/** The most days one upload may carry. */
const val STEPS_SHARE_UPLOAD_MAX = 100

fun stepsSharingPath(): String = "/api/steps/sharing"
fun stepsDaysPath(): String = "/api/steps/days"
fun stepsInvitationsPath(): String = "/api/steps/invitations"
fun stepsInvitationPath(id: String): String = "/api/steps/invitations/${sidecarPathSegment(id)}"
fun stepsInvitationAcceptPath(id: String): String = stepsInvitationPath(id) + "/accept"
fun stepsInvitationDeclinePath(id: String): String = stepsInvitationPath(id) + "/decline"
fun stepsFriendsPath(): String = "/api/steps/friends"
fun stepsFriendPath(id: String): String = "/api/steps/friends/${sidecarPathSegment(id)}"

/** What can go wrong talking to the server about steps. */
enum class StepsSharingFailure {
    NO_NETWORK,

    // The phone's token is gone or revoked: sign in to oursidecar again.
    SIGNED_OUT,

    // This account does not share (409 sharing_off): turn sharing on first.
    SHARING_OFF,

    // Four friends already, here or on the other side (409 too_many_friends).
    TOO_MANY_FRIENDS,

    // Ten invitations a day (429).
    TOO_MANY_INVITATIONS,

    // The invitation is not there for this account any more (404).
    GONE,

    // The server would not take what was sent (400): a bad address, a bad name.
    REJECTED,
    SERVER,
    UNEXPECTED,
}

/** What an answer of [status] with the error [code] means; null for a success. */
fun stepsSharingFailureOf(status: Int, code: String?): StepsSharingFailure? = when {
    status in 200..299 -> null
    status == 401 -> StepsSharingFailure.SIGNED_OUT
    status == 409 && code == "sharing_off" -> StepsSharingFailure.SHARING_OFF
    status == 409 && code == "too_many_friends" -> StepsSharingFailure.TOO_MANY_FRIENDS
    status == 429 -> StepsSharingFailure.TOO_MANY_INVITATIONS
    status == 404 -> StepsSharingFailure.GONE
    status == 400 -> StepsSharingFailure.REJECTED
    status >= 500 -> StepsSharingFailure.SERVER
    else -> StepsSharingFailure.UNEXPECTED
}

/**
 * The days to send up: today and the thirty before it, oldest first, the
 * typed-in and imported ones too — what the user sees as their own is what
 * friends see. Days of nothing go up as well, so a day once sent and then
 * emptied is emptied there too.
 */
fun stepsToShare(days: List<StepDay>, today: LocalDate): List<StepDay> {
    val from = today.minusDays(STEPS_SHARE_DAYS.toLong())
    return normalizedStepDays(days)
        .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        .takeLast(STEPS_SHARE_UPLOAD_MAX)
}

/** A name for friends the server will take: something left after trimming, and not too long. */
fun stepsShareNameValid(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= STEPS_SHARE_NAME_MAX }
