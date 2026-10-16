package com.alphaomegos.annasagenda

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A backup encrypted with the oursidecar password before it leaves the phone (16.10). */
class BackupCryptoTest {

    /** Few rounds: the tests are about the format, not the cost. */
    private val rounds = 1000

    /** Bytes counting up from [start]: a "random" that gives the same file every time. */
    private class Counting(private var start: Int) : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            for (i in bytes.indices) bytes[i] = (start++).toByte()
        }
    }

    private fun hex(text: String): ByteArray = text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun key(password: String = "correct horse") = deriveBackupKey(password, ByteArray(16) { it.toByte() }, rounds)

    private fun encrypt(plain: ByteArray, key: BackupKey = key(), chunk: Int = 32, random: SecureRandom = Counting(100)): ByteArray =
        ByteArrayOutputStream().also { encryptBackup(ByteArrayInputStream(plain), it, key, random, chunk) }.toByteArray()

    private fun decrypt(sealed: ByteArray, key: ByteArray?): Pair<BackupDecryptOutcome, ByteArray> {
        val out = ByteArrayOutputStream()
        val outcome = decryptBackup(ByteArrayInputStream(sealed), out) { key }
        return outcome to out.toByteArray()
    }

    @Test
    fun pbkdf2IsRfc7914sOwn() {
        assertArrayEquals(
            hex(
                "55 ac 04 6e 56 e3 08 9f ec 16 91 c2 25 44 b6 05 f9 41 85 21 6d de 04 65 e6 8b 9d 57 c2 0d ac bc" +
                    "49 ca 9c cc f1 79 b6 45 99 16 64 b3 9d 77 ef 31 7c 71 b8 45 b1 e3 0b d5 09 11 20 41 d3 a1 97 83",
            ),
            pbkdf2HmacSha256("passwd".toByteArray(), "salt".toByteArray(), 1, 64),
        )
        assertArrayEquals(
            hex(
                "4d dc d8 f6 0b 98 be 21 83 0c ee 5e f2 27 01 f9 64 1a 44 18 d0 4c 04 14 ae ff 08 87 6b 34 ab 56" +
                    "a1 d4 25 a1 22 58 33 54 9a db 84 1b 51 c9 b3 17 6a 27 2b de bb a1 d0 78 47 8f 62 b3 97 f3 3c 8d",
            ),
            pbkdf2HmacSha256("Password".toByteArray(), "NaCl".toByteArray(), 80000, 64),
        )
    }

    @Test
    fun pbkdf2AgreesWithTheJvmsOwnWhereItHasOne() {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        listOf(1 to 20, 2 to 32, 4096 to 32, 3 to 50).forEach { (c, length) ->
            val salt = ByteArray(16) { (it * 7).toByte() }
            val theirs = factory.generateSecret(PBEKeySpec("an oursidecar password".toCharArray(), salt, c, length * 8)).encoded
            assertArrayEquals("c=$c, $length bytes", theirs, pbkdf2HmacSha256("an oursidecar password".toByteArray(), salt, c, length))
        }
    }

    @Test
    fun aPasswordTypedComposedOrNotGivesOneKey() {
        val composed = "café"
        val decomposed = "café"

        assertArrayEquals(key(composed).bytes, key(decomposed).bytes)
        assertFalse(key("cafe").bytes.contentEquals(key(composed).bytes))
    }

    @Test
    fun anyLengthComesBackAsItWent() {
        listOf(0, 1, 31, 32, 33, 64, 95, 96, 200).forEach { size ->
            val plain = ByteArray(size) { (it * 31 + 7).toByte() }
            val sealed = encrypt(plain)
            val (outcome, back) = decrypt(sealed, key().bytes)

            assertEquals("$size bytes", BackupDecryptOutcome.OPENED, outcome)
            assertArrayEquals("$size bytes", plain, back)
            assertEquals("$size bytes", BACKUP_HEADER_BYTES + maxOf(1, (size + 31) / 32) * 16 + size, sealed.size)
        }
    }

    @Test
    fun aBackupOfSeveralMegabytesInChunksOfAMegabyte() {
        val plain = ByteArray(3 * BACKUP_CHUNK_BYTES + 7) { (it xor (it ushr 8)).toByte() }
        val sealed = ByteArrayOutputStream().also { encryptBackup(ByteArrayInputStream(plain), it, key()) }.toByteArray()
        val (outcome, back) = decrypt(sealed, key().bytes)

        assertEquals(BackupDecryptOutcome.OPENED, outcome)
        assertArrayEquals(plain, back)
    }

    @Test
    fun anotherPasswordOrNoKeyDoesNotOpenIt() {
        val sealed = encrypt("tasks, books, runs".toByteArray())

        assertEquals(BackupDecryptOutcome.WRONG_KEY, decrypt(sealed, key("wrong horse").bytes).first)
        assertEquals(BackupDecryptOutcome.WRONG_KEY, decrypt(sealed, null).first)
    }

    @Test
    fun aChangedByteACutAndAnAddedChunkAreAllFound() {
        val plain = ByteArray(100) { it.toByte() }
        val sealed = encrypt(plain)
        val k = key().bytes

        val changed = sealed.copyOf().also { it[BACKUP_HEADER_BYTES + 48 + 5] = (it[BACKUP_HEADER_BYTES + 48 + 5].toInt() xor 1).toByte() }
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(changed, k).first)

        // The last chunk gone, at a chunk's edge.
        val lastChunk = 100 % 32 + 16
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(sealed.copyOf(sealed.size - lastChunk), k).first)
        // Cut in the middle of one.
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(sealed.copyOf(sealed.size - 5), k).first)
        // Cut to its first chunk only.
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(sealed.copyOf(BACKUP_HEADER_BYTES + 48), k).first)
        // A chunk more than there was.
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(sealed + ByteArray(48), k).first)
        // ...also after a backup of one whole chunk. (After a short one, what is added runs into
        // its only chunk, which then reads as another password's: the sha256 the server keeps is
        // what tells a damaged file from a wrong key — it is checked before anything is opened.)
        assertEquals(BackupDecryptOutcome.DAMAGED, decrypt(encrypt(ByteArray(32)) + ByteArray(48), k).first)
    }

    @Test
    fun aZipIsNotAnEncryptedBackupAndItsHeaderSaysHowTheKeyWasDrawn() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(60)

        assertEquals(BackupDecryptOutcome.NOT_ENCRYPTED, decrypt(zip, key().bytes).first)
        assertNull(readBackupCipherHeader(ByteArrayInputStream(zip)))

        val header = readBackupCipherHeader(ByteArrayInputStream(encrypt(ByteArray(5))))
        assertNotNull(header)
        assertEquals(rounds, header!!.iterations)
        assertEquals(32, header.chunkSize)
        assertTrue(key().matches(header))
        assertFalse(deriveBackupKey("correct horse", ByteArray(16) { 9 }, rounds).matches(header))
    }

    @Test
    fun eachFileHasItsOwnIdAndStillOpens() {
        val plain = "the same backup twice".toByteArray()
        val first = encrypt(plain, random = Counting(1))
        val second = encrypt(plain, random = Counting(50))

        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, decrypt(first, key().bytes).second)
        assertArrayEquals(plain, decrypt(second, key().bytes).second)
    }

    @Test
    fun aNewKeyHasASaltOfItsOwn() {
        val a = newBackupKey("correct horse", Counting(0), rounds)
        val b = newBackupKey("correct horse", Counting(16), rounds)

        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.bytes.contentEquals(b.bytes))
        assertArrayEquals(a.bytes, deriveBackupKey("correct horse", a.salt, rounds).bytes)
    }

    /**
     * The format, pinned: these bytes are a backup made by this build, and
     * every later one must open them. A change here is a change to backups
     * already on the server — a new version byte, never an edit.
     */
    @Test
    fun aBackupMadeNowOpensForEver() {
        val pinned = PINNED_FILE
        val (outcome, back) = decrypt(hex(pinned), key().bytes)

        assertEquals(BackupDecryptOutcome.OPENED, outcome)
        assertEquals("Anna's agenda, 16.10.2026: a backup in three chunks, the last short.", String(back, Charsets.UTF_8))
        assertEquals(pinned, sidecarHex(encrypt("Anna's agenda, 16.10.2026: a backup in three chunks, the last short.".toByteArray(), random = Counting(100))))
    }

    private companion object {
        // Made 16.10.2026 by this build, and opened the same day by Python's own
        // AES-GCM and PBKDF2 (the "cryptography" package and hashlib) — not only
        // by the code it tests.
        const val PINNED_FILE =
            "4141424b0101000003e8000102030405060708090a0b0c0d0e0f6465666768696a6b6c6d6e6f707172730000002088e5" +
            "d2d6c1d0a356aeb00b9d28cbc7d28ea8512bc3f09ad340b2b040a5eb70201040b1dbf786414eda14fe889791badf9291" +
            "55843bbadc0b9d4e03d0f74a4ad5e284325b6dabc0a47abce059d074999da6e84b42f342e0f8c7a1a6795f21fba5aa18" +
            "07ea3afa566b2b691c0f0ee9d7affb5da305"
    }
}
