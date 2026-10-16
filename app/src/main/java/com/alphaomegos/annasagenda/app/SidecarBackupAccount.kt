package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.SidecarBackupAccountDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.model.SidecarBackupListing
import com.alphaomegos.annasagenda.model.SidecarBackupWhenFull
import com.alphaomegos.annasagenda.support.BackupCipherHeader
import com.alphaomegos.annasagenda.support.BackupKey
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.backupKeyOf
import com.alphaomegos.annasagenda.support.backupKeyText
import com.alphaomegos.annasagenda.support.newBackupKey
import com.alphaomegos.annasagenda.support.sidecarBackupTokenUsable
import com.alphaomegos.annasagenda.support.sidecarServerUrl
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the screens know of this phone's backups in oursidecar (16.10) — never the token, never the key. */
internal data class SidecarBackupStatus(
    val server: String? = null,
    val email: String? = null,
    val deviceName: String? = null,
    // The phone has a token for backups.
    val signedIn: Boolean = false,
    // The token was revoked (on the web), or signing in for backups failed: the password is asked again.
    val needsSignIn: Boolean = false,
    // The account is not one the server keeps backups for.
    val notEnabled: Boolean = false,
    // Whether backups are encrypted: null until the person has chosen.
    val encrypt: Boolean? = null,
    // The key is on the phone, so an encrypted backup can be made with no one to type the password.
    val hasKey: Boolean = false,
    // The weekly backup, on Wi-Fi and charging.
    val auto: Boolean = true,
    val listing: SidecarBackupListing? = null,
    val loading: Boolean = false,
    val failure: SidecarBackupFailure? = null,
)

/**
 * This phone's backups in the oursidecar account (16.10, Eduard): signs the
 * phone in for them alongside the music's sign-in, with the same password,
 * and keeps in [file] what it learns — its token and, when the backups are
 * encrypted, the key drawn from that password, both sealed by [sealer]; the
 * password never. One for the process (SidecarBackupAccounts).
 *
 * The key is drawn again at every sign-in (the music's comes once a month),
 * so after the account's password changes, new backups follow the new one.
 */
internal class SidecarBackupAccount(
    private val file: File,
    private val sealer: SidecarSealer,
    private val client: SidecarBackupsClient,
    private val deviceName: String,
    private val deriveKey: (String) -> BackupKey = { newBackupKey(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var dto = SidecarBackupAccountDto()
    private var token: String? = null
    private var key: BackupKey? = null
    private var opened = false

    private val _status = MutableStateFlow(SidecarBackupStatus())
    val status: StateFlow<SidecarBackupStatus> = _status.asStateFlow()

    init {
        dto = runCatching {
            if (file.isFile) sidecarJson.decodeFromString(SidecarBackupAccountDto.serializer(), file.readText()) else null
        }.getOrNull() ?: SidecarBackupAccountDto()
        publish()
    }

    /** The token and the key, opened from their seals the first time they are wanted. Call under [mutex]. */
    private fun open() {
        if (opened) return
        token = dto.sealedToken?.let { sealer.open(it) }?.takeIf { sidecarBackupTokenUsable(it) }
        key = dto.sealedKey?.let { sealer.open(it) }?.let { backupKeyOf(it) }
        opened = true
    }

    private fun publish(listing: SidecarBackupListing? = _status.value.listing, loading: Boolean = false, failure: SidecarBackupFailure? = null) {
        _status.value = SidecarBackupStatus(
            server = dto.server,
            email = dto.email,
            deviceName = dto.deviceName,
            signedIn = dto.sealedToken != null && !dto.needsSignIn,
            needsSignIn = dto.needsSignIn,
            notEnabled = dto.notEnabled,
            encrypt = dto.encrypt,
            hasKey = dto.sealedKey != null,
            auto = dto.auto,
            listing = listing,
            loading = loading,
            failure = failure,
        )
    }

    /** Written whole beside itself and moved over. */
    private fun save() {
        runCatching {
            val dir = file.absoluteFile.parentFile ?: return
            dir.mkdirs()
            val next = File(dir, file.name + ".next")
            next.writeText(sidecarJson.encodeToString(SidecarBackupAccountDto.serializer(), dto))
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    /** The key sealed and kept, or none. Call under [mutex]. */
    private fun keep(newKey: BackupKey?) {
        key = newKey
        dto = dto.copy(sealedKey = newKey?.let { sealer.seal(backupKeyText(it)) })
    }

    /**
     * The music signed in to [server] as [email] with [password] (16.10):
     * the phone signs in for backups too, unless it already is, and the key
     * is drawn from the password unless the backups are not encrypted.
     */
    suspend fun signedIn(server: String, email: String, password: String) = withContext(io) {
        val base = sidecarServerUrl(server) ?: return@withContext
        val account = email.trim()
        val (stale, needed) = mutex.withLock {
            open()
            val other = dto.server != base || !dto.email.equals(account, ignoreCase = true)
            val stale = if (other && token != null) Triple(dto.server, token, dto.deviceId) else null
            stale to (other || token == null || dto.needsSignIn)
        }
        // Another account (or server) came in: the old phone token is given up first.
        if (stale != null) {
            val (oldServer, oldToken, oldDevice) = stale
            if (oldServer != null && oldToken != null && oldDevice != null) client.signOut(oldServer, oldToken, oldDevice)
        }
        var failure: SidecarBackupFailure? = null
        if (needed) {
            when (val r = client.signIn(base, account, password, deviceName)) {
                is SidecarBackupResult.Ok -> mutex.withLock {
                    token = r.value.token
                    dto = dto.copy(
                        server = base,
                        email = account,
                        deviceId = r.value.id,
                        deviceName = r.value.name,
                        sealedToken = sealer.seal(r.value.token),
                        needsSignIn = false,
                        notEnabled = false,
                    )
                }
                is SidecarBackupResult.Failed -> mutex.withLock {
                    failure = r.failure
                    token = null
                    if (stale != null) keep(null)
                    dto = dto.copy(
                        server = base,
                        email = account,
                        deviceId = null,
                        sealedToken = null,
                        notEnabled = r.failure == SidecarBackupFailure.NOT_ENABLED,
                        needsSignIn = r.failure != SidecarBackupFailure.NOT_ENABLED,
                    )
                }
            }
        }
        val encrypt = mutex.withLock { dto.encrypt }
        val signedIn = mutex.withLock { token != null }
        // The key, drawn from the password just typed — slow on purpose, so only when it may be wanted.
        val drawn = if (signedIn && encrypt != false) deriveKey(password) else null
        mutex.withLock {
            if (drawn != null) keep(drawn)
            save()
            publish(listing = if (stale != null) null else _status.value.listing, failure = failure)
        }
    }

    /** Signs the phone out of backups (16.10): its token revoked when the server can be reached, and all forgotten. */
    suspend fun signOut() = withContext(io) {
        val (server, t, device) = mutex.withLock {
            open()
            Triple(dto.server, token, dto.deviceId)
        }
        if (server != null && t != null && device != null) client.signOut(server, t, device)
        mutex.withLock {
            token = null
            key = null
            sealer.forget()
            dto = SidecarBackupAccountDto(server = dto.server)
            save()
            publish(listing = null)
        }
    }

    /** The server and the token to send with, or null when the phone is not signed in for backups. */
    suspend fun credentials(): Pair<String, String>? = mutex.withLock {
        open()
        val server = dto.server
        val t = token
        if (server == null || t == null || dto.needsSignIn) null else server to t
    }

    /** The key backups are encrypted with now, when they are; null otherwise. */
    suspend fun encryptionKey(): BackupKey? = mutex.withLock {
        open()
        if (dto.encrypt == true) key else null
    }

    /** The key on the phone, when it is the one a file with [header] was encrypted with. */
    suspend fun keyFor(header: BackupCipherHeader): BackupKey? = mutex.withLock {
        open()
        key?.takeIf { it.matches(header) }
    }

    /** What a request's [failure] means for the phone: a revoked token asks for the password again. */
    private suspend fun noted(failure: SidecarBackupFailure) {
        if (failure != SidecarBackupFailure.SIGNED_OUT) return
        mutex.withLock {
            token = null
            dto = dto.copy(sealedToken = null, deviceId = null, needsSignIn = true)
            save()
        }
    }

    /** Hands a request's failure on, with what it means for the phone noted. */
    suspend fun note(failure: SidecarBackupFailure) {
        noted(failure)
        mutex.withLock { publish(failure = failure) }
    }

    /** The account's backups asked again. */
    suspend fun refresh(): SidecarBackupResult<SidecarBackupListing> {
        val c = credentials() ?: return SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT)
        mutex.withLock { publish(loading = true) }
        val r = client.list(c.first, c.second)
        when (r) {
            is SidecarBackupResult.Ok -> mutex.withLock { publish(listing = r.value) }
            is SidecarBackupResult.Failed -> note(r.failure)
        }
        return r
    }

    /**
     * Whether backups are encrypted (16.10) — the person's choice, made once
     * and changed when they like. Turning it on with no key on the phone
     * takes the account's [password] (checked by the caller against the
     * server); turning it off forgets the key.
     */
    suspend fun chooseEncryption(encrypt: Boolean, password: String? = null): Boolean = withContext(io) {
        val hasKey = mutex.withLock { open(); key != null }
        val drawn = if (encrypt && !hasKey) {
            if (password.isNullOrEmpty()) return@withContext false
            deriveKey(password)
        } else {
            null
        }
        mutex.withLock {
            if (drawn != null) keep(drawn)
            if (!encrypt) keep(null)
            dto = dto.copy(encrypt = encrypt)
            save()
            publish()
        }
        true
    }

    /** The weekly backup, on or off. */
    suspend fun setAuto(on: Boolean) = withContext(io) {
        mutex.withLock {
            if (dto.auto != on) {
                dto = dto.copy(auto = on)
                save()
                publish()
            }
        }
    }

    /** What the server does with a fourth backup by hand. */
    suspend fun setWhenFull(whenFull: SidecarBackupWhenFull): SidecarBackupResult<SidecarBackupWhenFull> {
        val c = credentials() ?: return SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT)
        val r = client.setWhenFull(c.first, c.second, whenFull)
        when (r) {
            is SidecarBackupResult.Ok -> mutex.withLock {
                publish(listing = _status.value.listing?.copy(whenFull = r.value))
            }
            is SidecarBackupResult.Failed -> note(r.failure)
        }
        return r
    }

    /** A backup deleted on the server, and the list asked again. */
    suspend fun delete(backupId: String): SidecarBackupResult<Unit> {
        val c = credentials() ?: return SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT)
        val r = client.delete(c.first, c.second, backupId)
        when (r) {
            is SidecarBackupResult.Ok -> refresh()
            is SidecarBackupResult.Failed -> note(r.failure)
        }
        return r
    }
}
