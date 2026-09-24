package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the three kinds of media share.
 *
 * They were written out nine times between them, and the only reason is that
 * a book calls its year "read" while a film calls it "watched". Nine copies of
 * a rule is nine chances to disagree about it.
 */
class ReadingShelfRulesTest {

    private val thisYear = 2026

    @Test
    fun aShelfAllowsAtMostOneOfTheTwoYears() {
        assertEquals(
            ShelfYears(finished = thisYear, abandoned = null),
            yearsForShelf(ReadingShelf.DONE, thisYear),
        )

        assertEquals(
            ShelfYears(finished = null, abandoned = thisYear),
            yearsForShelf(ReadingShelf.ABANDONED, thisYear),
        )

        listOf(ReadingShelf.PLANS, ReadingShelf.NOW).forEach { shelf ->
            assertEquals(
                "$shelf is not a thing that happened, so it carries no year",
                ShelfYears(finished = null, abandoned = null),
                yearsForShelf(shelf, thisYear),
            )
        }
    }

    @Test
    fun everyShelfHasAnAnswer() {
        // A new shelf added to the enum has to be thought about here rather
        // than falling into somebody's else branch.
        ReadingShelf.entries.forEach { shelf ->
            val years = yearsForShelf(shelf, thisYear)

            assertTrue(
                "$shelf sets both years at once",
                years.finished == null || years.abandoned == null,
            )
        }
    }

    @Test
    fun editingDoesNotRestampAYearTheItemAlreadyHas() {
        val years = resolvedShelfYears(
            shelf = ReadingShelf.DONE,
            requested = ShelfYears(finished = null, abandoned = null),
            existing = ShelfYears(finished = 2019, abandoned = null),
            currentYear = thisYear,
        )

        assertEquals(2019, years.finished)
        assertNull(years.abandoned)
    }

    @Test
    fun theYearAnEditGivesWinsOverTheOneTheItemHad() {
        val years = resolvedShelfYears(
            shelf = ReadingShelf.DONE,
            requested = ShelfYears(finished = 2024, abandoned = null),
            existing = ShelfYears(finished = 2019, abandoned = null),
            currentYear = thisYear,
        )

        assertEquals(2024, years.finished)
    }

    @Test
    fun arrivingOnAShelfWithNoYearAtAllTakesThisOne() {
        val years = resolvedShelfYears(
            shelf = ReadingShelf.ABANDONED,
            requested = ShelfYears(finished = null, abandoned = null),
            existing = ShelfYears(finished = 2019, abandoned = null),
            currentYear = thisYear,
        )

        assertEquals(thisYear, years.abandoned)
        assertNull("a book abandoned is not a book read", years.finished)
    }

    @Test
    fun leavingAShelfThatHadAYearDropsIt() {
        listOf(ReadingShelf.PLANS, ReadingShelf.NOW).forEach { shelf ->
            val years = resolvedShelfYears(
                shelf = shelf,
                requested = ShelfYears(finished = 2024, abandoned = 2024),
                existing = ShelfYears(finished = 2019, abandoned = 2018),
                currentYear = thisYear,
            )

            assertEquals(ShelfYears(finished = null, abandoned = null), years)
        }
    }

    @Test
    fun aTitleCannotBeEmptiedByAnEdit() {
        assertEquals("Dune", titleAfterEdit(null, "Dune"))
        assertEquals("Dune", titleAfterEdit("", "Dune"))
        assertEquals("Dune", titleAfterEdit("   ", "Dune"))
        assertEquals("Messiah", titleAfterEdit("  Messiah ", "Dune"))
    }

    @Test
    fun clearingACoverBeatsReplacingIt() {
        assertNull(coverAfterEdit(requested = "new.jpg", existing = "old.jpg", clearCover = true))
        assertEquals("new.jpg", coverAfterEdit("new.jpg", "old.jpg", clearCover = false))
        assertEquals("old.jpg", coverAfterEdit(null, "old.jpg", clearCover = false))
        assertNull(coverAfterEdit(null, null, clearCover = false))
    }

    @Test
    fun aReleaseYearHasToBeAYear() {
        assertTrue(isPossibleReleaseYear(1))
        assertTrue(isPossibleReleaseYear(1977))
        assertTrue(isPossibleReleaseYear(9999))

        assertFalse(isPossibleReleaseYear(0))
        assertFalse(isPossibleReleaseYear(-1))
        assertFalse(isPossibleReleaseYear(10000))
    }
}
