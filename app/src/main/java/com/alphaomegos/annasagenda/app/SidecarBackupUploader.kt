package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.data.SidecarBackupPendingDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.encryptBackup
import com.alphaomegos.annasagenda.support.sidecarBackupAppVersion
import com.alphaomegos.annasagenda.support.sidecarBackupNote
import com.alphaomegos.annasagenda.support.sidecarBackupPartRange
import com.alphaomegos.annasagenda.support.sidecarBackupPartsMissing
import com.alphaomegos.annasagenda.support.sidecarHex
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a backup going up is: which place, its note, and the backup by hand it is to replace. */
internal data class SidecarBackupRequest(
    val slot: SidecarBackupSlot,
    val note: String? = null,
    val replace: String? = null,
)

/**
 * A backup taken to oursidecar (16.10): the archive written, encrypted when
 * the person chose so, its sha256 counted, begun on the server, sent in
 * parts and finished. What is under way is written down in [dir], one for
 * each kind of place, so a run stopped half-way goes on, in the next, from
 * the parts the server has; a run that cannot go on (the server dropped an
 * upload left for a day) starts again from nothing.
 *
 * Nothing Android: the worker (SidecarBackupWorker) gives it the archive
 * through [writeArchive], and tells the progress on.
 */
internal class SidecarBackupUploader(
    private val dir: File,
    private val account: SidecarBackupAccount,
    private val client: SidecarBackupsClient,
    private val appVersion: String,
    private val random: SecureRandom = SecureRandom(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun placeOf(slot: SidecarBackupSlot) = File(dir, slot.wire)

    private fun pendingFile(slot: SidecarBackupSlot) = File(placeOf(slot), "pending.json")

    private fun readPending(slot: SidecarBackupSlot): SidecarBackupPendingDto? = runCatching {
        val f = pendingFile(slot)
        if (f.isFile) sidecarJson.decodeFromString(SidecarBackupPendingDto.serializer(), f.readText()) else null
    }.getOrNull()

    private fun writePending(slot: SidecarBackupSlot, pending: SidecarBackupPendingDto) {
        val place = placeOf(slot).apply { mkdirs() }
        val next = File(place, "pending.json.next")
        next.writeText(sidecarJson.encodeToString(SidecarBackupPendingDto.serializer(), pending))
        if (!next.renameTo(pendingFile(slot))) {
            pendingFile(slot).delete()
            next.renameTo(pendingFile(slot))
        }
    }

    /** Everything of [slot]'s under way forgotten: the files and the note of them. */
    fun clear(slot: SidecarBackupSlot) {
        placeOf(slot).deleteRecursively()
    }

    /** The backup of [slot] under way given up: on the server too, when it can be told. */
    suspend fun discard(slot: SidecarBackupSlot) = withContext(io) {
        val pending = readPending(slot)
        val c = account.credentials()
        if (pending != null && c != null) client.delete(c.first, c.second, pending.backupId)
        clear(slot)
    }

    /** Whether a backup of [slot] is under way, to be gone on with. */
    fun pending(slot: SidecarBackupSlot): Boolean = readPending(slot) != null

    /**
     * The backup [request] asks for, made and sent; or the one under way of
     * its kind gone on with. [onProgress] is told the parts sent of all.
     */
    suspend fun run(
        request: SidecarBackupRequest,
        writeArchive: suspend (File) -> Boolean,
        onProgress: suspend (sent: Int, of: Int) -> Unit = { _, _ -> },
    ): SidecarBackupResult<SidecarBackup> = withContext(io) {
        val (server, token) = account.credentials() ?: return@withContext SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT)
        val result = try {
            goOn(server, token, request.slot, onProgress) ?: fresh(server, token, request, writeArchive, onProgress)
        } catch (e: IOException) {
            // The phone's own disk: full, or gone. What is under way stays, to be gone on with.
            SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        }
        if (result is SidecarBackupResult.Failed) account.note(result.failure)
        result
    }

    /** The upload under way gone on with; null when there is none to go on with. */
    private suspend fun goOn(server: String, token: String, slot: SidecarBackupSlot, onProgress: suspend (Int, Int) -> Unit): SidecarBackupResult<SidecarBackup>? {
        val pending = readPending(slot) ?: return null
        val file = File(placeOf(slot), pending.fileName)
        if (!file.isFile || file.length() != pending.size) {
            clear(slot)
            return null
        }
        return when (val r = client.upload(server, token, pending.backupId)) {
            is SidecarBackupResult.Ok -> if (r.value.backup.ready) {
                clear(slot)
                SidecarBackupResult.Ok(r.value.backup)
            } else {
                sendAndFinish(server, token, slot, pending, file, r.value.received, onProgress)
            }
            is SidecarBackupResult.Failed -> if (r.failure == SidecarBackupFailure.GONE) {
                clear(slot)
                null
            } else {
                r
            }
        }
    }

    private suspend fun fresh(
        server: String,
        token: String,
        request: SidecarBackupRequest,
        writeArchive: suspend (File) -> Boolean,
        onProgress: suspend (Int, Int) -> Unit,
    ): SidecarBackupResult<SidecarBackup> {
        val slot = request.slot
        val encrypt = account.status.value.encrypt ?: return SidecarBackupResult.Failed(SidecarBackupFailure.NOT_CHOSEN)
        val key = if (encrypt) account.encryptionKey() ?: return SidecarBackupResult.Failed(SidecarBackupFailure.NO_KEY) else null
        clear(slot)
        val place = placeOf(slot).apply { mkdirs() }
        val archive = File(place, "backup.zip")
        if (!writeArchive(archive) || !archive.isFile) {
            clear(slot)
            return SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        }
        val file = if (key == null) {
            archive
        } else {
            val sealed = File(place, "backup.aabk")
            BufferedInputStream(FileInputStream(archive)).use { input ->
                BufferedOutputStream(FileOutputStream(sealed)).use { output -> encryptBackup(input, output, key, random) }
            }
            archive.delete()
            sealed
        }
        val size = file.length()
        val sha = sidecarFileSha256(file)
        val started = client.start(
            server,
            token,
            SidecarBackupStart(
                slot = slot,
                size = size,
                sha256 = sha,
                encrypted = key != null,
                appVersion = sidecarBackupAppVersion(appVersion),
                note = sidecarBackupNote(request.note),
                replace = request.replace,
            ),
        )
        val upload = when (started) {
            is SidecarBackupResult.Ok -> started.value
            is SidecarBackupResult.Failed -> {
                clear(slot)
                return started
            }
        }
        if (upload.partSize <= 0 || upload.partCount <= 0) {
            client.delete(server, token, upload.backup.id)
            clear(slot)
            return SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        }
        val pending = SidecarBackupPendingDto(
            backupId = upload.backup.id,
            slot = slot.wire,
            fileName = file.name,
            size = size,
            sha256 = sha,
            encrypted = key != null,
            partSize = upload.partSize,
            partCount = upload.partCount,
        )
        writePending(slot, pending)
        return sendAndFinish(server, token, slot, pending, file, upload.received, onProgress)
    }

    /** The parts the server has not got sent, then the backup finished; once more for any it says are missing. */
    private suspend fun sendAndFinish(
        server: String,
        token: String,
        slot: SidecarBackupSlot,
        pending: SidecarBackupPendingDto,
        file: File,
        received: List<Int>,
        onProgress: suspend (Int, Int) -> Unit,
    ): SidecarBackupResult<SidecarBackup> {
        var missing = sidecarBackupPartsMissing(pending.partCount, received)
        repeat(2) {
            send(server, token, pending, file, missing, onProgress)?.let { return it }
            when (val r = client.finish(server, token, pending.backupId)) {
                is SidecarBackupResult.Ok -> {
                    clear(slot)
                    return r
                }
                is SidecarBackupResult.Failed -> {
                    if (r.failure != SidecarBackupFailure.PARTS_MISSING) return r
                    missing = r.missing.filter { it in 1..pending.partCount }.ifEmpty { return r }
                }
            }
        }
        return SidecarBackupResult.Failed(SidecarBackupFailure.PARTS_MISSING)
    }

    /** [parts] of [file] sent; null when all went, the failure otherwise. */
    private suspend fun send(
        server: String,
        token: String,
        pending: SidecarBackupPendingDto,
        file: File,
        parts: List<Int>,
        onProgress: suspend (Int, Int) -> Unit,
    ): SidecarBackupResult.Failed? {
        val buffer = ByteArray(pending.partSize.toInt())
        var sent = pending.partCount - parts.size
        onProgress(sent, pending.partCount)
        RandomAccessFile(file, "r").use { raf ->
            for (part in parts) {
                val range = sidecarBackupPartRange(pending.size, pending.partSize, part)
                val length = (range.last - range.first + 1).toInt()
                raf.seek(range.first)
                raf.readFully(buffer, 0, length)
                val r = client.putPart(server, token, pending.backupId, part, buffer, 0, length)
                if (r is SidecarBackupResult.Failed) return r
                sent++
                onProgress(sent, pending.partCount)
            }
        }
        return null
    }
}

/** The sha256 of [file]'s bytes, in the hex the server keeps it in. */
internal fun sidecarFileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(FileInputStream(file)).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return sidecarHex(digest.digest())
}
