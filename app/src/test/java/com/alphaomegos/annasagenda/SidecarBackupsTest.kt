package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The rules of oursidecar's backups as this app keeps to them (16.10). */
class SidecarBackupsTest {

    private val mib = 1024L * 1024

    private fun backup(
        id: String,
        slot: SidecarBackupSlot = SidecarBackupSlot.MANUAL,
        createdAt: Long,
        ready: Boolean = true,
    ) = SidecarBackup(id = id, slot = slot, ready = ready, size = 10, sha256 = "a".repeat(64), encrypted = false, createdAtSeconds = createdAt)

    @Test
    fun aBackupGoesInWholePartsAndALastOneWithTheRest() {
        val size = 70 * mib + 5
        val part = 8 * mib

        assertEquals(9, sidecarBackupPartCount(size, part))
        assertEquals(0L..part - 1, sidecarBackupPartRange(size, part, 1))
        assertEquals(8 * part..size - 1, sidecarBackupPartRange(size, part, 9))
        assertEquals(6 * mib + 5, sidecarBackupPartRange(size, part, 9).let { it.last - it.first + 1 })
    }

    @Test
    fun aBackupOfExactlyWholePartsHasNoEmptyLastOneAndOneByteIsOnePart() {
        assertEquals(2, sidecarBackupPartCount(16 * mib, 8 * mib))
        assertEquals(8 * mib..16 * mib - 1, sidecarBackupPartRange(16 * mib, 8 * mib, 2))
        assertEquals(1, sidecarBackupPartCount(1, 8 * mib))
        assertEquals(0L..0L, sidecarBackupPartRange(1, 8 * mib, 1))
    }

    @Test
    fun thePartsStillToSendAreThoseTheServerHasNot() {
        assertEquals(listOf(1, 3, 5), sidecarBackupPartsMissing(5, listOf(4, 2)))
        assertEquals(emptyList<Int>(), sidecarBackupPartsMissing(3, setOf(3, 1, 2)))
        // A number the server names beyond the count changes nothing.
        assertEquals(listOf(1), sidecarBackupPartsMissing(1, listOf(7)))
    }

    @Test
    fun whatTheServerSaysIsToldApartByItsCode() {
        assertNull(sidecarBackupFailureOf(201, null))
        assertEquals(SidecarBackupFailure.WRONG_PASSWORD, sidecarBackupFailureOf(401, null, signingIn = true))
        assertEquals(SidecarBackupFailure.SIGNED_OUT, sidecarBackupFailureOf(401, null))
        assertEquals(SidecarBackupFailure.NOT_ENABLED, sidecarBackupFailureOf(403, "backups_not_enabled"))
        assertEquals(SidecarBackupFailure.UNEXPECTED, sidecarBackupFailureOf(403, null))
        assertEquals(SidecarBackupFailure.FULL, sidecarBackupFailureOf(409, "backups_full"))
        assertEquals(SidecarBackupFailure.PARTS_MISSING, sidecarBackupFailureOf(409, "parts_missing"))
        assertEquals(SidecarBackupFailure.TOO_LARGE, sidecarBackupFailureOf(400, "too_large"))
        assertEquals(SidecarBackupFailure.BAD_REPLACE, sidecarBackupFailureOf(400, "bad_replace"))
        assertEquals(SidecarBackupFailure.GONE, sidecarBackupFailureOf(404, null))
        assertEquals(SidecarBackupFailure.TOO_MANY_ATTEMPTS, sidecarBackupFailureOf(429, null))
        assertEquals(SidecarBackupFailure.SERVER, sidecarBackupFailureOf(503, null))
    }

    @Test
    fun theListShowsTheFinishedOnesByHandFirstAndNewestFirst() {
        val listing = SidecarBackupListing(
            backups = listOf(
                backup("auto", SidecarBackupSlot.AUTO, createdAt = 500),
                backup("old", createdAt = 100),
                backup("going", createdAt = 900, ready = false),
                backup("new", createdAt = 300),
            ),
        )

        assertEquals(listOf("new", "old", "auto"), sidecarBackupsShown(listing).map { it.id })
        assertEquals(2 to 1, sidecarBackupCounts(listing))
    }

    @Test
    fun withThreeByHandTheOldestIsTheFirstToReplaceAndTheOneMadeByItselfIsNotOffered() {
        val listing = SidecarBackupListing(
            backups = listOf(
                backup("b", createdAt = 200),
                backup("auto", SidecarBackupSlot.AUTO, createdAt = 50),
                backup("a", createdAt = 100),
                backup("c", createdAt = 300),
            ),
        )

        assertTrue(sidecarBackupManualFull(listing))
        assertEquals(listOf("a", "b", "c"), sidecarBackupReplaceChoices(listing).map { it.id })
        assertFalse(sidecarBackupManualFull(listing.copy(backups = listing.backups.drop(1))))
    }

    @Test
    fun anUnfinishedUploadDoesNotTakeAPlace() {
        val listing = SidecarBackupListing(
            backups = listOf(backup("a", createdAt = 1), backup("b", createdAt = 2), backup("c", createdAt = 3, ready = false)),
        )

        assertFalse(sidecarBackupManualFull(listing))
    }

    @Test
    fun aNoteIsOneTrimmedLineNoLongerThanTheServerKeeps() {
        assertEquals("before the trip", sidecarBackupNote("  before\n the   trip "))
        assertNull(sidecarBackupNote("   \n "))
        assertNull(sidecarBackupNote(null))
        assertEquals(SIDECAR_BACKUP_NOTE_MAX, sidecarBackupNote("x".repeat(500))!!.length)
        assertEquals("22.0", sidecarBackupAppVersion(" 22.0 "))
    }

    @Test
    fun thePhoneIsNamedWithItsMakerOnce() {
        assertEquals("Samsung SM-F926B", sidecarBackupDeviceName("samsung", "SM-F926B"))
        assertEquals("Google Pixel 8", sidecarBackupDeviceName("Google", "Pixel 8"))
        assertEquals("Google Pixel 8", sidecarBackupDeviceName("Google", "Google Pixel 8"))
        assertEquals("Android", sidecarBackupDeviceName(" ", null))
        assertEquals(SIDECAR_BACKUP_DEVICE_NAME_MAX, sidecarBackupDeviceName("Maker", "M".repeat(200)).length)
    }

    @Test
    fun onlyATokenForBackupsIsTakenAsOne() {
        assertTrue(sidecarBackupTokenUsable("osb_abc123"))
        assertFalse(sidecarBackupTokenUsable("osb_"))
        assertFalse(sidecarBackupTokenUsable("abc123"))
        assertFalse(sidecarBackupTokenUsable("osb_ab c"))
        assertFalse(sidecarBackupTokenUsable(null))
        assertEquals("Bearer osb_x", sidecarBearerHeader("osb_x"))
    }

    @Test
    fun theAddressesAreTheServersWithTheIdsEscaped() {
        assertEquals("/api/backups/b%2F1/parts/3", sidecarBackupPartPath("b/1", 3))
        assertEquals("/api/backups/b1/finish", sidecarBackupFinishPath("b1"))
        assertEquals("/api/backups/b1/download", sidecarBackupDownloadPath("b1"))
        assertEquals("/api/backup-devices/d%201", sidecarBackupDevicePath("d 1"))
    }

    @Test
    fun aShaIsSixtyFourHexDigits() {
        assertTrue(sidecarBackupShaValid("0123456789abcdef".repeat(4)))
        assertFalse(sidecarBackupShaValid("0123456789abcdeg".repeat(4)))
        assertFalse(sidecarBackupShaValid("ab"))
        assertEquals("00ff10", sidecarHex(byteArrayOf(0, -1, 16)))
    }

    @Test
    fun theKeyIsKeptAsTextAndReadBackWhole() {
        val key = deriveBackupKey("pw", ByteArray(16) { it.toByte() }, 5)
        val back = backupKeyOf(backupKeyText(key))

        assertEquals(5, back!!.iterations)
        assertTrue(back.salt.contentEquals(key.salt) && back.bytes.contentEquals(key.bytes))
        assertNull(backupKeyOf("5:00ff"))
        assertNull(backupKeyOf("x:00:00"))
        assertNull(backupKeyOf("5:" + "0".repeat(32) + ":abc"))
        assertNull(sidecarUnhex("0g"))
        assertTrue(sidecarUnhex("00ff10")!!.contentEquals(byteArrayOf(0, -1, 16)))
    }
}
