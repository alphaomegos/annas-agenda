package com.alphaomegos.annasagenda.support

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/*
 * A backup encrypted on the phone before it goes to oursidecar (16.10).
 *
 * Eduard: whether to encrypt is the person's choice, and the password is
 * the one of their oursidecar account. The key is drawn from it with
 * PBKDF2-HMAC-SHA256 — written out here rather than asked of Android,
 * which has it only from API 26 and this app runs from 24 — and kept on
 * the phone, sealed, so that the weekly backup needs no one to type it.
 * Another phone draws the same key from the same password and the salt
 * the file carries.
 *
 * The file: a header of 46 bytes, then the backup in chunks of a MiB, each
 * sealed by AES-256-GCM on its own — a 200 MB backup is never held in
 * memory whole, and a cut or a changed byte anywhere is found. Each file
 * has a key of its own, drawn from the account's and a random id in the
 * header, so a chunk's nonce can simply count. The last chunk is marked in
 * its nonce: a file cut short at a chunk's edge does not open either. The
 * header is the authenticated data of every chunk.
 *
 *   "AABK" | version 1 | kdf 1 (PBKDF2-HMAC-SHA256) | iterations, 4 bytes
 *   | salt, 16 | file id, 16 | chunk size, 4          (big-endian)
 *
 * The format is fixed by BackupCryptoTest's pinned file: a backup made now
 * must open with every later build.
 */

private val BACKUP_MAGIC = byteArrayOf('A'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
private const val BACKUP_FORMAT_VERSION: Byte = 1
private const val BACKUP_KDF_PBKDF2_SHA256: Byte = 1
private const val BACKUP_TAG_BYTES = 16
private const val BACKUP_KEY_BYTES = 32
private const val BACKUP_FILE_ID_BYTES = 16
private val BACKUP_FILE_KEY_LABEL = "annas-agenda backup file key v1".toByteArray(Charsets.US_ASCII)

/** The header's length: the magic, two bytes of version, the iterations, the salt, the file's id, the chunk size. */
const val BACKUP_HEADER_BYTES = 4 + 1 + 1 + 4 + 16 + 16 + 4

/** PBKDF2's rounds: OWASP's 600 000 for HMAC-SHA256 (2023) — a second or two on a phone, once a sign-in. */
const val BACKUP_KDF_ITERATIONS = 600_000

const val BACKUP_SALT_BYTES = 16

/** The plain bytes in a chunk. */
const val BACKUP_CHUNK_BYTES = 1 shl 20

/** More rounds than this in a header is not a backup of ours but an attempt to keep the phone busy. */
private const val BACKUP_KDF_ITERATIONS_MAX = 10_000_000
private const val BACKUP_CHUNK_BYTES_MAX = 16 shl 20

/** The account's key for backups: what it was drawn from (the salt, the rounds), and itself. */
class BackupKey(val salt: ByteArray, val iterations: Int, val bytes: ByteArray) {
    init {
        require(salt.size == BACKUP_SALT_BYTES && bytes.size == BACKUP_KEY_BYTES && iterations >= 1)
    }

    /** Whether this key was drawn the way [header] says its file's was. */
    fun matches(header: BackupCipherHeader): Boolean = header.iterations == iterations && header.salt.contentEquals(salt)
}

/** What an encrypted backup's header says. */
class BackupCipherHeader(
    val iterations: Int,
    val salt: ByteArray,
    val fileId: ByteArray,
    val chunkSize: Int,
) {
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream(BACKUP_HEADER_BYTES)
        out.write(BACKUP_MAGIC)
        out.write(BACKUP_FORMAT_VERSION.toInt())
        out.write(BACKUP_KDF_PBKDF2_SHA256.toInt())
        out.write(intBytes(iterations))
        out.write(salt)
        out.write(fileId)
        out.write(intBytes(chunkSize))
        return out.toByteArray()
    }

    companion object {
        /** The header [bytes] begin with; null when they are not an encrypted backup of this app's. */
        fun parse(bytes: ByteArray): BackupCipherHeader? {
            if (bytes.size < BACKUP_HEADER_BYTES) return null
            if (!bytes.copyOfRange(0, 4).contentEquals(BACKUP_MAGIC)) return null
            if (bytes[4] != BACKUP_FORMAT_VERSION || bytes[5] != BACKUP_KDF_PBKDF2_SHA256) return null
            val iterations = intOf(bytes, 6)
            val salt = bytes.copyOfRange(10, 26)
            val fileId = bytes.copyOfRange(26, 42)
            val chunk = intOf(bytes, 42)
            if (iterations !in 1..BACKUP_KDF_ITERATIONS_MAX || chunk !in 1..BACKUP_CHUNK_BYTES_MAX) return null
            return BackupCipherHeader(iterations, salt, fileId, chunk)
        }
    }
}

/** How opening an encrypted backup went. */
enum class BackupDecryptOutcome { OPENED, WRONG_KEY, DAMAGED, NOT_ENCRYPTED }

/* ---------------- the key ---------------- */

/** A password as the key is drawn from it: composed the one way (NFC), as UTF-8. */
fun backupPasswordBytes(password: String): ByteArray =
    Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

/** PBKDF2 with HMAC-SHA256 (RFC 8018), [length] bytes of it. */
fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    require(password.isNotEmpty() && iterations >= 1 && length >= 1)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(password, "HmacSHA256"))
    val hLen = mac.macLength
    val out = ByteArray(length)
    var block = 1
    var offset = 0
    while (offset < length) {
        mac.update(salt)
        mac.update(intBytes(block))
        val u = mac.doFinal()
        val t = u.copyOf()
        repeat(iterations - 1) {
            mac.update(u)
            mac.doFinal(u, 0)
            for (j in t.indices) t[j] = t[j] xor u[j]
        }
        val n = minOf(hLen, length - offset)
        System.arraycopy(t, 0, out, offset, n)
        offset += n
        block++
    }
    return out
}

/** The key drawn from [password] with [salt] and [iterations]. Slow on purpose: off the main thread. */
fun deriveBackupKey(password: String, salt: ByteArray, iterations: Int = BACKUP_KDF_ITERATIONS): BackupKey =
    BackupKey(salt.copyOf(), iterations, pbkdf2HmacSha256(backupPasswordBytes(password), salt, iterations, BACKUP_KEY_BYTES))

/** A new key from [password], with a salt of its own. */
fun newBackupKey(password: String, random: SecureRandom = SecureRandom(), iterations: Int = BACKUP_KDF_ITERATIONS): BackupKey {
    val salt = ByteArray(BACKUP_SALT_BYTES).also { random.nextBytes(it) }
    return deriveBackupKey(password, salt, iterations)
}

/** The key of one file, drawn from the account's and the file's id. */
private fun fileKey(key: ByteArray, fileId: ByteArray): SecretKeySpec {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    mac.update(BACKUP_FILE_KEY_LABEL)
    mac.update(fileId)
    return SecretKeySpec(mac.doFinal(), "AES")
}

/** Chunk [index]'s nonce: the count, then whether it is the last. */
private fun chunkNonce(index: Long, last: Boolean): ByteArray {
    val nonce = ByteArray(12)
    for (i in 0 until 8) nonce[i] = (index ushr (56 - 8 * i)).toByte()
    nonce[11] = if (last) 1 else 0
    return nonce
}

/* ---------------- encrypting and opening ---------------- */

/**
 * [input] encrypted with [key] into [output] (16.10). Neither is closed.
 * [chunkSize] other than a MiB is for tests.
 */
fun encryptBackup(
    input: InputStream,
    output: OutputStream,
    key: BackupKey,
    random: SecureRandom = SecureRandom(),
    chunkSize: Int = BACKUP_CHUNK_BYTES,
) {
    require(chunkSize in 1..BACKUP_CHUNK_BYTES_MAX)
    val fileId = ByteArray(BACKUP_FILE_ID_BYTES).also { random.nextBytes(it) }
    val header = BackupCipherHeader(key.iterations, key.salt, fileId, chunkSize).toBytes()
    output.write(header)
    val k = fileKey(key.bytes, fileId)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    var current = ByteArray(chunkSize)
    var currentLength = readUpTo(input, current)
    var next = ByteArray(chunkSize)
    var index = 0L
    while (true) {
        // A chunk is the last when nothing follows it; an empty backup is one empty chunk.
        val nextLength = if (currentLength < chunkSize) 0 else readUpTo(input, next)
        val last = nextLength == 0
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(BACKUP_TAG_BYTES * 8, chunkNonce(index, last)))
        cipher.updateAAD(header)
        output.write(cipher.doFinal(current, 0, currentLength))
        if (last) break
        val swap = current
        current = next
        next = swap
        currentLength = nextLength
        index++
    }
}

/**
 * Opens the encrypted backup in [input] into [output] (16.10), with the
 * account key [keyFor] gives for its header — null when there is none to
 * try. On anything but [BackupDecryptOutcome.OPENED], what was written is
 * not the backup and goes: write into a file of your own.
 */
fun decryptBackup(
    input: InputStream,
    output: OutputStream,
    keyFor: (BackupCipherHeader) -> ByteArray?,
): BackupDecryptOutcome {
    val headerBytes = ByteArray(BACKUP_HEADER_BYTES)
    if (readUpTo(input, headerBytes) < BACKUP_HEADER_BYTES) return BackupDecryptOutcome.NOT_ENCRYPTED
    val header = BackupCipherHeader.parse(headerBytes) ?: return BackupDecryptOutcome.NOT_ENCRYPTED
    val key = keyFor(header) ?: return BackupDecryptOutcome.WRONG_KEY
    if (key.size != BACKUP_KEY_BYTES) return BackupDecryptOutcome.WRONG_KEY
    val k = fileKey(key, header.fileId)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val sealed = header.chunkSize + BACKUP_TAG_BYTES
    var current = ByteArray(sealed)
    var currentLength = readUpTo(input, current)
    var next = ByteArray(sealed)
    var index = 0L
    while (true) {
        if (currentLength < BACKUP_TAG_BYTES) return BackupDecryptOutcome.DAMAGED
        val nextLength = if (currentLength < sealed) 0 else readUpTo(input, next)
        val last = nextLength == 0
        val plain = try {
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(BACKUP_TAG_BYTES * 8, chunkNonce(index, last)))
            cipher.updateAAD(headerBytes)
            cipher.doFinal(current, 0, currentLength)
        } catch (e: AEADBadTagException) {
            // The first chunk not opening is a key that is not the file's — unless it opens as
            // the other kind of chunk, first of more or the only one: then the file was cut or
            // lengthened. A later one not opening is a file changed.
            return if (index == 0L && !opensAs(cipher, k, headerBytes, current, currentLength, !last)) {
                BackupDecryptOutcome.WRONG_KEY
            } else {
                BackupDecryptOutcome.DAMAGED
            }
        } catch (e: GeneralSecurityException) {
            return BackupDecryptOutcome.DAMAGED
        }
        output.write(plain)
        if (last) return BackupDecryptOutcome.OPENED
        val swap = current
        current = next
        next = swap
        currentLength = nextLength
        index++
    }
}

/** Whether the first chunk opens with the key when taken as [last] or not. */
private fun opensAs(cipher: Cipher, key: SecretKeySpec, header: ByteArray, chunk: ByteArray, length: Int, last: Boolean): Boolean =
    try {
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BACKUP_TAG_BYTES * 8, chunkNonce(0L, last)))
        cipher.updateAAD(header)
        cipher.doFinal(chunk, 0, length)
        true
    } catch (e: GeneralSecurityException) {
        false
    }

/** The header of the file [input] begins with, or null; reads at most the header's bytes. */
fun readBackupCipherHeader(input: InputStream): BackupCipherHeader? {
    val bytes = ByteArray(BACKUP_HEADER_BYTES)
    return if (readUpTo(input, bytes) < BACKUP_HEADER_BYTES) null else BackupCipherHeader.parse(bytes)
}

/** As many bytes as [buffer] holds, or all that are left; fewer only at the end. */
private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val n = try {
            input.read(buffer, total, buffer.size - total)
        } catch (e: EOFException) {
            -1
        }
        if (n < 0) break
        if (n == 0) throw IOException("The stream gave nothing and did not end.")
        total += n
    }
    return total
}

private fun intBytes(value: Int): ByteArray =
    byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

private fun intOf(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
        ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
