package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.util.buildInternalCoverRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The word that names a kind of media inside a cover file name.
 *
 * It used to be a literal in six places — three for importing a cover, three
 * for migrating an old one — and a typo in any of them would have produced a
 * file nothing ever finds again.
 */
class ReadingMediaSupportTest {

    /**
     * These three words are on disk in every user's cover folder. Nothing
     * parses a ref back into a kind and an id, so changing one would not break
     * the app — it would just leave old covers under names the new code never
     * writes. Pinned so that a change has to be a decision.
     */
    @Test
    fun theWordForEachKindIsTheOneAlreadyOnDisk() {
        assertEquals("book", coverMediaKind(ReadingMediaType.BOOKS))
        assertEquals("movie", coverMediaKind(ReadingMediaType.MOVIES))
        assertEquals("series", coverMediaKind(ReadingMediaType.SERIES))
    }

    @Test
    fun noTwoKindsShareAWord() {
        val words = ReadingMediaType.entries.map { coverMediaKind(it) }

        assertEquals(words.size, words.distinct().size)
    }

    @Test
    fun theWordIsWhatTheCoverRefIsNamedAfter() {
        val ref = buildInternalCoverRef(
            mediaKind = coverMediaKind(ReadingMediaType.SERIES),
            itemId = 17L,
            token = "9f3a1c02",
        )

        assertEquals("internal://media_covers/series_17_9f3a1c02.jpg", ref)
    }

    @Test
    fun everyKindProducesARefOfItsOwn() {
        val refs = ReadingMediaType.entries.map {
            buildInternalCoverRef(mediaKind = coverMediaKind(it), itemId = 1L, token = "aaaa")
        }

        assertTrue("two kinds of media must not name the same file", refs.distinct().size == refs.size)
    }
}
