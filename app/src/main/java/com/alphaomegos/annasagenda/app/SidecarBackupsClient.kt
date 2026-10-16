package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.SidecarBackupErrorDto
import com.alphaomegos.annasagenda.data.SidecarBackupListReplyDto
import com.alphaomegos.annasagenda.data.SidecarBackupSignInDto
import com.alphaomegos.annasagenda.data.SidecarBackupSignInReplyDto
import com.alphaomegos.annasagenda.data.SidecarBackupStartDto
import com.alphaomegos.annasagenda.data.SidecarBackupUploadReplyDto
import com.alphaomegos.annasagenda.data.SidecarBackupWhenFullDto
import com.alphaomegos.annasagenda.data.sidecarBackupWhenFullOf
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupListing
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.model.SidecarBackupWhenFull
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.sidecarBackupDeviceSignInPath
import com.alphaomegos.annasagenda.support.sidecarBackupDevicePath
import com.alphaomegos.annasagenda.support.sidecarBackupDownloadPath
import com.alphaomegos.annasagenda.support.sidecarBackupFailureOf
import com.alphaomegos.annasagenda.support.sidecarBackupFinishPath
import com.alphaomegos.annasagenda.support.sidecarBackupPartPath
import com.alphaomegos.annasagenda.support.sidecarBackupPath
import com.alphaomegos.annasagenda.support.sidecarBackupSettingsPath
import com.alphaomegos.annasagenda.support.sidecarBackupShaValid
import com.alphaomegos.annasagenda.support.sidecarBackupTokenUsable
import com.alphaomegos.annasagenda.support.sidecarBackupsPath
import com.alphaomegos.annasagenda.support.sidecarRetryMinutes
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/** A request about backups: what it brought, or what went wrong — with what the server sent beside it. */
internal sealed interface SidecarBackupResult<out T> {
    data class Ok<T>(val value: T) : SidecarBackupResult<T>

    data class Failed(
        val failure: SidecarBackupFailure,
        val retryMinutes: Long? = null,
        // With FULL: the backups by hand, one of which a new one would replace.
        val backups: List<SidecarBackup> = emptyList(),
        // With PARTS_MISSING: the parts the server has not got.
        val missing: List<Int> = emptyList(),
    ) : SidecarBackupResult<Nothing>
}

/** A phone signed in for backups: its token, and its id and name on the server. */
internal data class SidecarBackupDevice(val token: String, val id: String, val name: String)

/** What a backup about to go up is. */
internal data class SidecarBackupStart(
    val slot: SidecarBackupSlot,
    val size: Long,
    val sha256: String,
    val encrypted: Boolean,
    val appVersion: String? = null,
    val note: String? = null,
    val replace: String? = null,
)

/** A backup's upload as the server keeps it: the backup, the size of its parts, how many, and those it has. */
internal data class SidecarBackupUpload(
    val backup: SidecarBackup,
    val partSize: Long,
    val partCount: Int,
    val received: List<Int> = emptyList(),
    // The backup a new one by hand takes the place of, once finished.
    val replaces: String? = null,
)

/**
 * oursidecar's backups asked of [server]s through [http] (16.10). Holds
 * nothing itself: the token goes with each call (SidecarBackupAccount keeps
 * it). Every call off the main thread, on [io].
 */
internal class SidecarBackupsClient(
    private val http: SidecarBackupHttp,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private fun <T> parse(serializer: KSerializer<T>, body: String): T? =
        runCatching { sidecarJson.decodeFromString(serializer, body) }.getOrNull()

    private fun failed(reply: SidecarBackupReply, signingIn: Boolean = false): SidecarBackupResult.Failed? {
        val error = parse(SidecarBackupErrorDto.serializer(), reply.body)
        val failure = sidecarBackupFailureOf(reply.status, error?.code, signingIn) ?: return null
        return SidecarBackupResult.Failed(
            failure = failure,
            retryMinutes = sidecarRetryMinutes(reply.retryAfter),
            backups = error?.backups.orEmpty().mapNotNull { it.toDomain() },
            missing = error?.missing.orEmpty(),
        )
    }

    private suspend fun <T> call(block: () -> SidecarBackupResult<T>): SidecarBackupResult<T> = withContext(io) {
        try {
            block()
        } catch (e: IOException) {
            SidecarBackupResult.Failed(SidecarBackupFailure.NO_NETWORK)
        }
    }

    /** Signs this phone in for backups as [email]; the password goes to the server and nowhere else. */
    suspend fun signIn(server: String, email: String, password: String, deviceName: String): SidecarBackupResult<SidecarBackupDevice> = call {
        val body = sidecarJson.encodeToString(SidecarBackupSignInDto.serializer(), SidecarBackupSignInDto(email.trim(), password, deviceName))
        val reply = http.json("POST", server + sidecarBackupDeviceSignInPath(), null, body)
        failed(reply, signingIn = true) ?: run {
            val answer = parse(SidecarBackupSignInReplyDto.serializer(), reply.body)
            val token = answer?.token
            val device = answer?.device
            if (token == null || !sidecarBackupTokenUsable(token) || device == null) {
                SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
            } else {
                SidecarBackupResult.Ok(SidecarBackupDevice(token, device.id, device.name.ifBlank { deviceName }))
            }
        }
    }

    /** The phone signed out: its token revoked on the server. The backups stay. */
    suspend fun signOut(server: String, token: String, deviceId: String): SidecarBackupResult<Unit> = call {
        val reply = http.json("DELETE", server + sidecarBackupDevicePath(deviceId), token, null)
        failed(reply) ?: SidecarBackupResult.Ok(Unit)
    }

    /** The account's backups, and its rules. */
    suspend fun list(server: String, token: String): SidecarBackupResult<SidecarBackupListing> = call {
        val reply = http.json("GET", server + sidecarBackupsPath(), token, null)
        failed(reply) ?: parse(SidecarBackupListReplyDto.serializer(), reply.body)
            ?.let { SidecarBackupResult.Ok(it.toDomain()) }
            ?: SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
    }

    /** What the server does with a fourth backup by hand. */
    suspend fun setWhenFull(server: String, token: String, whenFull: SidecarBackupWhenFull): SidecarBackupResult<SidecarBackupWhenFull> = call {
        val body = sidecarJson.encodeToString(SidecarBackupWhenFullDto.serializer(), SidecarBackupWhenFullDto(whenFull.wire))
        val reply = http.json("PUT", server + sidecarBackupSettingsPath(), token, body)
        failed(reply) ?: SidecarBackupResult.Ok(
            parse(SidecarBackupWhenFullDto.serializer(), reply.body)?.let { sidecarBackupWhenFullOf(it.whenFull) } ?: whenFull,
        )
    }

    /** A backup begun: the server says how to send it. FULL brings the backups to choose from. */
    suspend fun start(server: String, token: String, start: SidecarBackupStart): SidecarBackupResult<SidecarBackupUpload> = call {
        if (!sidecarBackupShaValid(start.sha256) || start.size <= 0) {
            SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        } else {
            val body = sidecarJson.encodeToString(
                SidecarBackupStartDto.serializer(),
                SidecarBackupStartDto(
                    slot = start.slot.wire,
                    size = start.size,
                    sha256 = start.sha256.lowercase(),
                    encrypted = start.encrypted,
                    appVersion = start.appVersion,
                    note = start.note,
                    replace = start.replace,
                ),
            )
            val reply = http.json("POST", server + sidecarBackupsPath(), token, body)
            failed(reply) ?: uploadOf(reply.body)
        }
    }

    /** A backup's upload as the server has it now: the parts it has got. GONE after a day unfinished. */
    suspend fun upload(server: String, token: String, backupId: String): SidecarBackupResult<SidecarBackupUpload> = call {
        val reply = http.json("GET", server + sidecarBackupPath(backupId), token, null)
        failed(reply) ?: uploadOf(reply.body)
    }

    private fun uploadOf(body: String): SidecarBackupResult<SidecarBackupUpload> {
        val answer = parse(SidecarBackupUploadReplyDto.serializer(), body)
        val backup = answer?.backup?.toDomain() ?: return SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        val upload = answer.upload
        return SidecarBackupResult.Ok(
            SidecarBackupUpload(
                backup = backup,
                partSize = upload?.partSize ?: 0L,
                partCount = upload?.partCount ?: 0,
                received = upload?.received.orEmpty(),
                replaces = answer.replaces,
            ),
        )
    }

    /** Part [part] (from 1): [length] bytes of [bytes] from [offset]. Sending one again replaces it. */
    suspend fun putPart(server: String, token: String, backupId: String, part: Int, bytes: ByteArray, offset: Int, length: Int): SidecarBackupResult<Unit> = call {
        val reply = http.putBytes(server + sidecarBackupPartPath(backupId, part), token, bytes, offset, length)
        failed(reply) ?: SidecarBackupResult.Ok(Unit)
    }

    /** The backup finished: whole on the server, and the one it replaces gone. PARTS_MISSING names what is not there. */
    suspend fun finish(server: String, token: String, backupId: String): SidecarBackupResult<SidecarBackup> = call {
        val reply = http.json("POST", server + sidecarBackupFinishPath(backupId), token, null)
        failed(reply) ?: parse(SidecarBackupUploadReplyDto.serializer(), reply.body)?.backup?.toDomain()
            ?.let { SidecarBackupResult.Ok(it) }
            ?: SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
    }

    /** A backup deleted, or an upload given up. */
    suspend fun delete(server: String, token: String, backupId: String): SidecarBackupResult<Unit> = call {
        val reply = http.json("DELETE", server + sidecarBackupPath(backupId), token, null)
        failed(reply) ?: SidecarBackupResult.Ok(Unit)
    }

    /**
     * A backup brought down into [out] from byte [from] on. The answer is
     * whether it came as the rest (true) or whole from the start (false),
     * and the sha256 the server keeps.
     */
    suspend fun download(
        server: String,
        token: String,
        backupId: String,
        from: Long,
        out: OutputStream,
        onBytes: (Long) -> Unit = {},
    ): SidecarBackupResult<Pair<Boolean, String?>> = call {
        val reply = http.download(server + sidecarBackupDownloadPath(backupId), token, from, out, onBytes)
        failed(SidecarBackupReply(reply.status, reply.body))
            ?: SidecarBackupResult.Ok((reply.status == 206) to reply.sha256?.trim()?.lowercase())
    }
}
