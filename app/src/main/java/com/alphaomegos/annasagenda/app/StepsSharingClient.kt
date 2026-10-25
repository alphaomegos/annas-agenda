package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.StepAcceptedDto
import com.alphaomegos.annasagenda.data.StepErrorDto
import com.alphaomegos.annasagenda.data.StepFriendsDto
import com.alphaomegos.annasagenda.data.StepInvitationsDto
import com.alphaomegos.annasagenda.data.StepInviteDto
import com.alphaomegos.annasagenda.data.StepShareDayDto
import com.alphaomegos.annasagenda.data.StepShareDaysDto
import com.alphaomegos.annasagenda.data.StepSharingDto
import com.alphaomegos.annasagenda.data.StepStoredDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepFriend
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.model.StepSharing
import com.alphaomegos.annasagenda.support.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException

/** How a steps request ended. */
internal sealed interface StepsSharingResult<out T> {
    data class Ok<T>(val value: T) : StepsSharingResult<T>
    data class Failed(val failure: StepsSharingFailure, val retryMinutes: Long? = null) : StepsSharingResult<Nothing>
}

/** What to do with the name friends see, when sharing is set. */
internal sealed interface StepShareName {
    /** Leave it as it is (the field is not sent). */
    data object Keep : StepShareName

    /** Back to the account's own name (sent as null). */
    data object FromAccount : StepShareName

    data class Own(val name: String) : StepShareName
}

/**
 * oursidecar's steps API (25.10, its patch 0059) for this phone: the server
 * and the bearer token are the ones the phone signed in with for backups.
 * Each call answers or fails; none throws. Blocking work runs on [io].
 */
internal class StepsSharingClient(
    private val http: SidecarBackupHttp,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun <T> parse(serializer: KSerializer<T>, body: String): T? =
        runCatching { sidecarJson.decodeFromString(serializer, body) }.getOrNull()

    private fun failed(reply: SidecarBackupReply): StepsSharingResult.Failed? {
        val error = parse(StepErrorDto.serializer(), reply.body)
        val failure = stepsSharingFailureOf(reply.status, error?.code) ?: return null
        return StepsSharingResult.Failed(failure, sidecarRetryMinutes(reply.retryAfter))
    }

    private fun <T> answer(reply: SidecarBackupReply, serializer: KSerializer<T>): StepsSharingResult<T> =
        failed(reply) ?: parse(serializer, reply.body)?.let { StepsSharingResult.Ok(it) }
            ?: StepsSharingResult.Failed(StepsSharingFailure.UNEXPECTED)

    private fun done(reply: SidecarBackupReply): StepsSharingResult<Unit> = failed(reply) ?: StepsSharingResult.Ok(Unit)

    private suspend fun <T> call(block: () -> StepsSharingResult<T>): StepsSharingResult<T> = withContext(io) {
        try {
            block()
        } catch (e: IOException) {
            StepsSharingResult.Failed(StepsSharingFailure.NO_NETWORK)
        }
    }

    suspend fun sharing(server: String, token: String): StepsSharingResult<StepSharing> = call {
        when (val r = answer(http.json("GET", server + stepsSharingPath(), token, null), StepSharingDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.toDomain())
            is StepsSharingResult.Failed -> r
        }
    }

    /** Sharing on or off; off deletes this account's days on the server (friends stay friends). */
    suspend fun setSharing(server: String, token: String, enabled: Boolean, name: StepShareName = StepShareName.Keep): StepsSharingResult<StepSharing> = call {
        val body = buildJsonObject {
            put("enabled", JsonPrimitive(enabled))
            when (name) {
                StepShareName.Keep -> Unit
                StepShareName.FromAccount -> put("displayName", JsonNull)
                is StepShareName.Own -> put("displayName", JsonPrimitive(name.name.trim()))
            }
        }.toString()
        when (val r = answer(http.json("PUT", server + stepsSharingPath(), token, body), StepSharingDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.toDomain())
            is StepsSharingResult.Failed -> r
        }
    }

    /** This account's days, as stepsToShare picks them; the number the server kept. */
    suspend fun uploadDays(server: String, token: String, days: List<StepDay>): StepsSharingResult<Int> = call {
        val body = sidecarJson.encodeToString(
            StepShareDaysDto.serializer(),
            StepShareDaysDto(days.take(STEPS_SHARE_UPLOAD_MAX).map { StepShareDayDto(it.date.toString(), it.steps) }),
        )
        when (val r = answer(http.json("PUT", server + stepsDaysPath(), token, body), StepStoredDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.stored)
            is StepsSharingResult.Failed -> r
        }
    }

    /**
     * An invitation to [email]. The server answers the same whether anyone has
     * that address or not, so success says only that it was sent on its way.
     */
    suspend fun invite(server: String, token: String, email: String): StepsSharingResult<Unit> = call {
        val body = sidecarJson.encodeToString(StepInviteDto.serializer(), StepInviteDto(email.trim()))
        done(http.json("POST", server + stepsInvitationsPath(), token, body))
    }

    suspend fun invitations(server: String, token: String): StepsSharingResult<List<StepInvitation>> = call {
        when (val r = answer(http.json("GET", server + stepsInvitationsPath(), token, null), StepInvitationsDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.toDomain())
            is StepsSharingResult.Failed -> r
        }
    }

    suspend fun accept(server: String, token: String, id: String): StepsSharingResult<StepFriend> = call {
        when (val r = answer(http.json("POST", server + stepsInvitationAcceptPath(id), token, null), StepAcceptedDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.friend.toDomain())
            is StepsSharingResult.Failed -> r
        }
    }

    suspend fun decline(server: String, token: String, id: String): StepsSharingResult<Unit> = call {
        done(http.json("POST", server + stepsInvitationDeclinePath(id), token, null))
    }

    /** Takes back an invitation this account sent. */
    suspend fun cancel(server: String, token: String, id: String): StepsSharingResult<Unit> = call {
        done(http.json("DELETE", server + stepsInvitationPath(id), token, null))
    }

    suspend fun friends(server: String, token: String): StepsSharingResult<List<StepFriend>> = call {
        when (val r = answer(http.json("GET", server + stepsFriendsPath(), token, null), StepFriendsDto.serializer())) {
            is StepsSharingResult.Ok -> StepsSharingResult.Ok(r.value.friends.map { it.toDomain() })
            is StepsSharingResult.Failed -> r
        }
    }

    /** Ends sharing with a friend, for both: neither sees the other's steps any more. */
    suspend fun unfriend(server: String, token: String, id: String): StepsSharingResult<Unit> = call {
        done(http.json("DELETE", server + stepsFriendPath(id), token, null))
    }
}
