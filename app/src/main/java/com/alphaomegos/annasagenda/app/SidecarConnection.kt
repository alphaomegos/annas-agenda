package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.SidecarAccountReplyDto
import com.alphaomegos.annasagenda.data.SidecarDeviceDto
import com.alphaomegos.annasagenda.data.SidecarSpaceReplyDto
import com.alphaomegos.annasagenda.data.SidecarTracksReplyDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toDomain
import com.alphaomegos.annasagenda.data.toDto
import com.alphaomegos.annasagenda.model.SidecarAccount
import com.alphaomegos.annasagenda.model.SidecarMember
import com.alphaomegos.annasagenda.model.SidecarSpace
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.SIDECAR_DEFAULT_SERVER
import com.alphaomegos.annasagenda.support.SidecarFailure
import com.alphaomegos.annasagenda.support.SidecarSignInState
import com.alphaomegos.annasagenda.support.sidecarAudioPath
import com.alphaomegos.annasagenda.support.sidecarCookieHeader
import com.alphaomegos.annasagenda.support.sidecarFailureOf
import com.alphaomegos.annasagenda.support.sidecarRetryMinutes
import com.alphaomegos.annasagenda.support.sidecarServerUrl
import com.alphaomegos.annasagenda.support.sidecarSessionFromCookies
import com.alphaomegos.annasagenda.support.sidecarSignInState
import com.alphaomegos.annasagenda.support.sidecarSpacePath
import com.alphaomegos.annasagenda.support.sidecarTracksPath
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the screens know of the oursidecar sign-in (16.10) — never the token. */
internal data class SidecarStatus(
    val server: String = SIDECAR_DEFAULT_SERVER,
    val account: SidecarAccount? = null,
    val sessionEndsAtMillis: Long? = null,
    val rejected: Boolean = false,
    val spaces: List<SidecarSpace> = emptyList(),
    val spaceId: String? = null,
    // The person's own picture in the chosen sidecar, as the server names it (sidecarAvatarPath).
    val avatarId: String? = null,
) {
    /** How the icon looks at [nowMillis]. */
    fun signIn(nowMillis: Long): SidecarSignInState = sidecarSignInState(account != null, sessionEndsAtMillis, rejected, nowMillis)

    /** The sidecar whose music plays: the one chosen, or the first. */
    val space: SidecarSpace? get() = spaces.firstOrNull { it.id == spaceId } ?: spaces.firstOrNull()
}

/** A request's outcome: what it brought, or what went wrong — with the minutes to wait, when the server said. */
internal sealed interface SidecarResult<out T> {
    data class Ok<T>(val value: T) : SidecarResult<T>

    data class Failed(val failure: SidecarFailure, val retryMinutes: Long? = null) : SidecarResult<Nothing>
}

/** Seals the token with a key only this phone holds (16.10); null when it cannot. */
internal interface SidecarSealer {
    fun seal(plain: String): String?

    fun open(sealed: String): String?

    /** The key itself thrown away, on signing out. */
    fun forget()
}

/**
 * This phone's oursidecar sign-in (16.10): signs in and out, asks the
 * server for the account, a sidecar's people and its tracks, and keeps
 * what it learns in [file] — the token sealed by [sealer], the password
 * never. One for the process (SidecarConnections), shared by the screens
 * and the player.
 *
 * A refused session is remembered as such: the icon then asks for the
 * password again, and the account — who it was — is kept for it, until
 * the user signs out.
 */
internal class SidecarConnection(
    private val file: File,
    private val sealer: SidecarSealer,
    private val http: SidecarHttp,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var sealedToken: String? = null
    private var token: String? = null
    private var tokenOpened = false

    private val _status = MutableStateFlow(SidecarStatus())
    val status: StateFlow<SidecarStatus> = _status.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val dto = runCatching {
            if (file.isFile) sidecarJson.decodeFromString(SidecarDeviceDto.serializer(), file.readText()) else null
        }.getOrNull() ?: return
        sealedToken = dto.sealedToken
        val userId = dto.userId
        val email = dto.email
        _status.value = SidecarStatus(
            server = dto.server?.let(::sidecarServerUrl) ?: SIDECAR_DEFAULT_SERVER,
            account = if (userId != null && email != null) SidecarAccount(userId, email) else null,
            sessionEndsAtMillis = dto.expiresAtMillis.takeIf { dto.sealedToken != null && it > 0L },
            rejected = dto.rejected,
            spaces = dto.spaces.map { it.toDomain() },
            spaceId = dto.spaceId,
            avatarId = dto.avatarId,
        )
    }

    /** Written whole beside itself and moved over, so a phone switched off mid-write keeps the old one. */
    private fun save() {
        val s = _status.value
        val dto = SidecarDeviceDto(
            server = s.server,
            userId = s.account?.userId,
            email = s.account?.email,
            sealedToken = sealedToken,
            expiresAtMillis = s.sessionEndsAtMillis ?: 0L,
            rejected = s.rejected,
            spaces = s.spaces.map { it.toDto() },
            spaceId = s.spaceId,
            avatarId = s.avatarId,
        )
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            next.writeText(sidecarJson.encodeToString(SidecarDeviceDto.serializer(), dto))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    /** The token, opened from its seal the first time it is wanted. Call under [mutex]. */
    private fun currentToken(): String? {
        if (!tokenOpened) {
            token = sealedToken?.let { sealer.open(it) }
            tokenOpened = true
        }
        return token
    }

    private fun <T> parse(serializer: KSerializer<T>, body: String): T? =
        runCatching { sidecarJson.decodeFromString(serializer, body) }.getOrNull()

    /** The sidecar to keep chosen of [spaces]: the one that was, while it is there; else the first. */
    private fun keptSpace(spaces: List<SidecarSpace>, was: String?): String? =
        was?.takeIf { id -> spaces.any { it.id == id } } ?: spaces.firstOrNull()?.id

    /** Signs in to [server] as [email] (16.10). The password goes to the server and nowhere else. */
    suspend fun signIn(server: String, email: String, password: String): SidecarResult<Unit> = withContext(io) {
        signInNow(server, email, password)
    }

    private suspend fun signInNow(server: String, email: String, password: String): SidecarResult<Unit> {
        val base = sidecarServerUrl(server) ?: return SidecarResult.Failed(SidecarFailure.UNEXPECTED)
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val reply = try {
            http.send("POST", "$base/api/auth/login", null, body)
        } catch (e: IOException) {
            return SidecarResult.Failed(SidecarFailure.NO_NETWORK)
        }
        sidecarFailureOf(reply.status, signingIn = true)?.let {
            return SidecarResult.Failed(it, sidecarRetryMinutes(reply.retryAfter))
        }
        val session = sidecarSessionFromCookies(reply.setCookies, clock())
            ?: return SidecarResult.Failed(SidecarFailure.UNEXPECTED)
        val answer = parse(SidecarAccountReplyDto.serializer(), reply.body)
        val user = answer?.user ?: return SidecarResult.Failed(SidecarFailure.UNEXPECTED)
        mutex.withLock {
            val spaces = answer.sidecars.map { it.toDomain() }
            token = session.token
            tokenOpened = true
            sealedToken = sealer.seal(session.token)
            val was = _status.value
            _status.value = SidecarStatus(
                server = base,
                account = user.toDomain(),
                sessionEndsAtMillis = session.expiresAtMillis,
                rejected = false,
                spaces = spaces,
                spaceId = keptSpace(spaces, was.spaceId),
                avatarId = was.avatarId.takeIf { was.account?.userId == user.id },
            )
            save()
        }
        refreshAvatarNow()
        return SidecarResult.Ok(Unit)
    }

    /**
     * The person's own picture in the chosen sidecar, asked again (16.10):
     * one changed on the web since appears. When the asking fails, the one
     * known stays.
     */
    private suspend fun refreshAvatarNow() {
        val s = _status.value
        val me = s.account?.userId ?: return
        val space = s.space?.id ?: return
        val r = authorizedGet(sidecarSpacePath(space)) as? SidecarResult.Ok ?: return
        val members = parse(SidecarSpaceReplyDto.serializer(), r.value) ?: return
        val avatarId = members.members.firstOrNull { it.id == me }?.avatarId
        mutex.withLock {
            val now = _status.value
            if (now.account?.userId == me && now.space?.id == space && now.avatarId != avatarId) {
                _status.value = now.copy(avatarId = avatarId)
                save()
            }
        }
    }

    /**
     * Signs out (16.10): the server is told, if it can be reached, and this
     * phone forgets the account, the token and the key that sealed it. The
     * server's address stays as it was typed.
     */
    suspend fun signOut() = withContext(io) {
        val (t, base) = mutex.withLock { currentToken() to _status.value.server }
        if (t != null) {
            try {
                http.send("POST", "$base/api/auth/logout", sidecarCookieHeader(t), null)
            } catch (e: IOException) {
                // Offline: the server lets the session lapse in its thirty days.
            }
        }
        mutex.withLock {
            token = null
            tokenOpened = true
            sealedToken = null
            sealer.forget()
            _status.value = SidecarStatus(server = base)
            save()
        }
    }

    /** The server's address for the next sign-in; null when it is not one ([sidecarServerUrl]). */
    suspend fun setServer(server: String): String? = withContext(io) {
        val base = sidecarServerUrl(server) ?: return@withContext null
        mutex.withLock {
            if (_status.value.server != base) {
                _status.value = _status.value.copy(server = base)
                save()
            }
        }
        base
    }

    /** The sidecar whose music plays, of those the account has. */
    suspend fun chooseSpace(spaceId: String) = withContext(io) {
        mutex.withLock {
            val s = _status.value
            if (s.spaceId != spaceId && s.spaces.any { it.id == spaceId }) {
                _status.value = s.copy(spaceId = spaceId, avatarId = null)
                save()
            }
        }
        // A picture is the person's in one sidecar: another may have another.
        refreshAvatarNow()
    }

    /**
     * A GET of [path] with the session (16.10). No request at all with no
     * session or one that has run out; a refusal marks it ended.
     */
    private suspend fun authorizedGet(path: String): SidecarResult<String> {
        val (t, s) = mutex.withLock { currentToken() to _status.value }
        val ends = s.sessionEndsAtMillis
        if (s.account == null) return SidecarResult.Failed(SidecarFailure.SIGNED_OUT)
        if (t == null || ends == null || clock() >= ends) {
            markRejected()
            return SidecarResult.Failed(SidecarFailure.SIGNED_OUT)
        }
        val reply = try {
            http.send("GET", s.server + path, sidecarCookieHeader(t), null)
        } catch (e: IOException) {
            return SidecarResult.Failed(SidecarFailure.NO_NETWORK)
        }
        val failure = sidecarFailureOf(reply.status) ?: return SidecarResult.Ok(reply.body)
        if (failure == SidecarFailure.SIGNED_OUT) markRejected()
        return SidecarResult.Failed(failure, sidecarRetryMinutes(reply.retryAfter))
    }

    private suspend fun markRejected() = mutex.withLock {
        val s = _status.value
        if (s.account != null && !s.rejected) {
            _status.value = s.copy(rejected = true)
            save()
        }
    }

    /** The account and its sidecars, asked again (a sidecar joined on the web since appears). */
    suspend fun refreshAccount(): SidecarResult<Unit> = withContext(io) {
        when (val r = authorizedGet("/api/me")) {
            is SidecarResult.Failed -> r
            is SidecarResult.Ok -> {
                val answer = parse(SidecarAccountReplyDto.serializer(), r.value)
                if (answer?.user == null) {
                    SidecarResult.Failed(SidecarFailure.UNEXPECTED)
                } else {
                    mutex.withLock {
                        val spaces = answer.sidecars.map { it.toDomain() }
                        val s = _status.value
                        _status.value = s.copy(spaces = spaces, spaceId = keptSpace(spaces, s.spaceId))
                        save()
                    }
                    refreshAvatarNow()
                    SidecarResult.Ok(Unit)
                }
            }
        }
    }

    /** The people of sidecar [spaceId], in the order they joined it. */
    suspend fun members(spaceId: String): SidecarResult<List<SidecarMember>> = withContext(io) {
        when (val r = authorizedGet(sidecarSpacePath(spaceId))) {
            is SidecarResult.Failed -> r
            is SidecarResult.Ok -> parse(SidecarSpaceReplyDto.serializer(), r.value)
                ?.let { answer -> SidecarResult.Ok(answer.members.map { it.toDomain() }.sortedBy { it.joinedAt }) }
                ?: SidecarResult.Failed(SidecarFailure.UNEXPECTED)
        }
    }

    /** The tracks of sidecar [spaceId], as the server lists them: the newest first. */
    suspend fun tracks(spaceId: String): SidecarResult<List<SidecarTrack>> = withContext(io) {
        when (val r = authorizedGet(sidecarTracksPath(spaceId))) {
            is SidecarResult.Failed -> r
            is SidecarResult.Ok -> parse(SidecarTracksReplyDto.serializer(), r.value)
                ?.let { answer -> SidecarResult.Ok(answer.tracks.map { it.toDomain() }) }
                ?: SidecarResult.Failed(SidecarFailure.UNEXPECTED)
        }
    }

    /** The Cookie header the player streams a track with; null with no session. */
    suspend fun cookieHeader(): String? = withContext(io) {
        mutex.withLock { currentToken()?.let { sidecarCookieHeader(it) } }
    }

    /** Where track [trackId]'s file is streamed from. */
    fun audioUrl(trackId: String): String = _status.value.server + sidecarAudioPath(trackId)
}
