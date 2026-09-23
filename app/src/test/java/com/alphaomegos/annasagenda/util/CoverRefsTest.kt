package com.alphaomegos.annasagenda.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The naming rule everything about covers rests on.
 *
 * The bug worth remembering: a ref was built from the media kind and the item
 * id alone, so replacing a cover produced the very same string. The file on
 * disk was overwritten, the old picture destroyed — and the app state came out
 * equal to what it already was, which a StateFlow does not emit and an image
 * loader keyed on the ref does not reload. The new cover was invisible until
 * the app was restarted.
 */
class CoverRefsTest {

    @Test
    fun twoRefsForTheSameItemAreNeverEqual() {
        val first = buildInternalCoverRef(mediaKind = "book", itemId = 17)
        val second = buildInternalCoverRef(mediaKind = "book", itemId = 17)

        assertNotEquals("replacing a cover must change the ref", first, second)
    }

    @Test
    fun aRefIsOneOfOursAndCarriesNoPath() {
        val ref = buildInternalCoverRef(mediaKind = "book", itemId = 17)

        assertTrue(isInternalCoverRef(ref))
        assertFalse(isExternalCoverRef(ref))

        val fileName = coverFileNameForRef(ref)
        assertEquals("book_17_", fileName?.substringBeforeLast('_')?.plus('_'))
        assertTrue(fileName!!.endsWith(".jpg"))
        assertFalse(fileName.contains('/'))
        assertFalse(fileName.contains('\\'))
    }

    @Test
    fun theMediaKindIsSanitised() {
        // " Bo ok/../x " -> trimmed, lowercased, every other character replaced:
        // b o _ o k _ _ _ _ x
        val ref = buildInternalCoverRef(mediaKind = " Bo ok/../x ", itemId = 1, token = "abc")

        assertEquals("internal://media_covers/bo_ok____x_1_abc.jpg", ref)
    }

    @Test
    fun aBlankMediaKindGetsAName() {
        val ref = buildInternalCoverRef(mediaKind = "  ", itemId = 1, token = "abc")

        assertEquals("internal://media_covers/item_1_abc.jpg", ref)
    }

    @Test
    fun aTokenIsStrippedDownToLettersAndDigits() {
        val ref = buildInternalCoverRef(mediaKind = "book", itemId = 1, token = "A-b/../9")

        assertEquals("internal://media_covers/book_1_ab9.jpg", ref)
    }

    @Test
    fun anEmptyTokenStillProducesAUsableRef() {
        val ref = buildInternalCoverRef(mediaKind = "book", itemId = 1, token = "")

        assertTrue(isInternalCoverRef(ref))
        assertFalse(coverFileNameForRef(ref)!!.contains("__"))
    }

    @Test
    fun aFreshTokenIsNotTheSameTwice() {
        assertNotEquals(newCoverToken(), newCoverToken())
    }

    /* ---------------- what the backup archive sees ---------------- */

    @Test
    fun aRefRoundTripsThroughAZipEntryName() {
        val ref = buildInternalCoverRef(mediaKind = "series", itemId = 202)
        val entry = zipEntryNameForCoverRef(ref)

        assertEquals("media_covers/${coverFileNameForRef(ref)}", entry)
        assertEquals(ref, coverRefFromZipEntryName(entry!!))
    }

    /** Refs written before the token existed are still perfectly good. */
    @Test
    fun aLegacyRefWithoutATokenStillWorks() {
        val legacy = "internal://media_covers/book_5.jpg"

        assertTrue(isInternalCoverRef(legacy))
        assertEquals("book_5.jpg", coverFileNameForRef(legacy))
        assertEquals("media_covers/book_5.jpg", zipEntryNameForCoverRef(legacy))
        assertEquals(legacy, coverRefFromZipEntryName("media_covers/book_5.jpg"))
    }

    @Test
    fun anExternalRefIsNotPackedIntoTheArchive() {
        assertNull(zipEntryNameForCoverRef("content://media/external/images/1"))
        assertTrue(isExternalCoverRef("content://media/external/images/1"))
    }

    @Test
    fun anEntryFromSomewhereElseInTheArchiveIsIgnored() {
        assertNull(coverRefFromZipEntryName("app_state.json"))
        assertNull(coverRefFromZipEntryName("media_covers/"))
    }

    /**
     * An archive is a file someone else wrote, so a ref arriving from one is
     * not to be trusted with a path.
     */
    @Test
    fun anEntryNameThatClimbsOutOfTheFolderIsRejected() {
        assertNull(coverRefFromZipEntryName("media_covers/.."))
        assertNull(coverRefFromZipEntryName("media_covers/../secrets.txt"))
        assertNull(coverRefFromZipEntryName("media_covers/nested/cover.jpg"))
        assertNull(coverFileNameForRef("internal://media_covers/.."))
        assertNull(coverFileNameForRef("internal://media_covers/../secrets.txt"))
    }

    @Test
    fun aBlankOrNullRefIsNobodysCover() {
        assertFalse(isInternalCoverRef(null))
        assertFalse(isInternalCoverRef(""))
        assertFalse(isExternalCoverRef(null))
        assertFalse(isExternalCoverRef("   "))
        assertNull(coverFileNameForRef(null))
        assertNull(coverFileNameForRef("internal://media_covers/"))
    }
}
