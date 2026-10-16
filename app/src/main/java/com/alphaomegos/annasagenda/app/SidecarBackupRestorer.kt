package com.alphaomegos.annasagenda.app

import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.support.BackupDecryptOutcome
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.decryptBackup
import com.alphaomegos.annasagenda.support.deriveBackupKey
import com.alphaomegos.annasagenda.support.readBackupCipherHeader
import com.alphaomegos.annasagenda.support.sidecarHex
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A backup brought back from oursidecar (16.10), up to the archive the
 * import takes: downloaded into [dir] — on from where it stopped, when it
 * did — its sha256 checked against the server's before anything is
 * opened, and, encrypted, opened with the key on the phone or the
 * password the person gives: the account's password of when it was made.
 *
 * What came down stays until [clear], so another password is tried
 * without fetching it again.
 */
internal class SidecarBackupRestorer(
    private val dir: File,
    private val account: SidecarBackupAccount,
    private val client: SidecarBackupsClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun downloaded(backup: SidecarBackup) = File(dir, "${safeName(backup.id)}.down")

    private fun archive(backup: SidecarBackup) = File(dir, "${safeName(backup.id)}.zip")

    /** An id as a file's name, whatever letters it has. */
    private fun safeName(id: String): String = sidecarHex(id.toByteArray(Charsets.UTF_8)).take(64)

    /** Everything brought down forgotten. */
    fun clear() {
        dir.deleteRecursively()
    }

    /**
     * The archive of [backup], ready for the import; [password] for one
     * encrypted with a key that is not on the phone (WRONG_BACKUP_PASSWORD
     * asks for it, or says the one given is not it). [onBytes] is told what
     * has come down of all.
     */
    suspend fun fetch(
        backup: SidecarBackup,
        password: String? = null,
        onBytes: (got: Long, of: Long) -> Unit = { _, _ -> },
    ): SidecarBackupResult<File> = withContext(io) {
        try {
            val (server, token) = account.credentials() ?: return@withContext SidecarBackupResult.Failed(SidecarBackupFailure.SIGNED_OUT)
            dir.mkdirs()
            val file = downloaded(backup)
            val zip = archive(backup)
            if (zip.isFile && !backup.encrypted) return@withContext SidecarBackupResult.Ok(zip)
            if (!file.isFile || file.length() != backup.size) {
                download(server, token, backup, file, onBytes)?.let { return@withContext it }
            }
            if (file.length() != backup.size || sidecarFileSha256(file) != backup.sha256.lowercase()) {
                file.delete()
                return@withContext SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED)
            }
            if (!backup.encrypted) {
                zip.delete()
                if (!file.renameTo(zip)) return@withContext SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
                return@withContext SidecarBackupResult.Ok(zip)
            }
            open(file, zip, password)
        } catch (e: IOException) {
            SidecarBackupResult.Failed(SidecarBackupFailure.UNEXPECTED)
        }
    }

    /** Brought down, on from what is there; null when it came whole, the failure otherwise. */
    private suspend fun download(
        server: String,
        token: String,
        backup: SidecarBackup,
        file: File,
        onBytes: (Long, Long) -> Unit,
    ): SidecarBackupResult.Failed? {
        if (file.length() > backup.size) file.delete()
        val from = if (file.isFile) file.length() else 0L
        val r = client.download(server, token, backup.id, from, { partial ->
            // A 200 to a Range is the whole again: what was there goes.
            BufferedOutputStream(FileOutputStream(file, partial && from > 0))
        }) { got -> onBytes((if (from > 0) from else 0L) + got, backup.size) }
        return when (r) {
            is SidecarBackupResult.Ok -> {
                val sha = r.value.second
                if (sha != null && sha != backup.sha256.lowercase()) {
                    // The server's file is not the one listed: replaced since, or damaged.
                    file.delete()
                    SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED)
                } else {
                    null
                }
            }
            is SidecarBackupResult.Failed -> {
                account.note(r.failure)
                r
            }
        }
    }

    /** The encrypted [file] opened into [zip]. */
    private suspend fun open(file: File, zip: File, password: String?): SidecarBackupResult<File> {
        val header = BufferedInputStream(FileInputStream(file)).use { readBackupCipherHeader(it) }
            ?: return SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED)
        val key = account.keyFor(header)?.bytes
            ?: password?.takeIf { it.isNotEmpty() }?.let { deriveBackupKey(it, header.salt, header.iterations).bytes }
            ?: return SidecarBackupResult.Failed(SidecarBackupFailure.WRONG_BACKUP_PASSWORD)
        val outcome = BufferedInputStream(FileInputStream(file)).use { input ->
            BufferedOutputStream(FileOutputStream(zip)).use { output -> decryptBackup(input, output) { key } }
        }
        return when (outcome) {
            BackupDecryptOutcome.OPENED -> SidecarBackupResult.Ok(zip)
            BackupDecryptOutcome.WRONG_KEY -> {
                zip.delete()
                SidecarBackupResult.Failed(SidecarBackupFailure.WRONG_BACKUP_PASSWORD)
            }
            BackupDecryptOutcome.DAMAGED, BackupDecryptOutcome.NOT_ENCRYPTED -> {
                zip.delete()
                file.delete()
                SidecarBackupResult.Failed(SidecarBackupFailure.DAMAGED)
            }
        }
    }
}
