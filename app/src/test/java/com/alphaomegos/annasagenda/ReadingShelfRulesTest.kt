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

    // -- the single year field on a details screen ---------------------------

    @Test
    fun theYearFieldShowsTheYearTheItemHas() {
        assertEquals(
            "2019",
            shelfYearText(ReadingShelf.DONE, ShelfYears(finished = 2019, abandoned = null), thisYear),
        )

        assertEquals(
            "2018",
            shelfYearText(
                ReadingShelf.ABANDONED,
                ShelfYears(finished = null, abandoned = 2018),
                thisYear,
            ),
        )
    }

    @Test
    fun theYearFieldOffersThisYearWhenTheItemHasNone() {
        assertEquals("2026", shelfYearText(ReadingShelf.DONE, NoShelfYears, thisYear))
        assertEquals("2026", shelfYearText(ReadingShelf.ABANDONED, NoShelfYears, thisYear))
    }

    @Test
    fun theYearFieldIsEmptyOnAShelfWithNoYear() {
        assertEquals("", shelfYearText(ReadingShelf.PLANS, ShelfYears(2019, 2018), thisYear))
        assertEquals("", shelfYearText(ReadingShelf.NOW, ShelfYears(2019, 2018), thisYear))
    }

    @Test
    fun whatIsTypedInTheYearFieldBelongsToWhicheverShelfIsChosen() {
        assertEquals(
            ShelfYears(finished = 1999, abandoned = null),
            shelfYearsFromText(ReadingShelf.DONE, "1999", thisYear),
        )

        assertEquals(
            ShelfYears(finished = null, abandoned = 1999),
            shelfYearsFromText(ReadingShelf.ABANDONED, "1999", thisYear),
        )

        assertEquals(NoShelfYears, shelfYearsFromText(ReadingShelf.PLANS, "1999", thisYear))
    }

    @Test
    fun anUnreadableYearSavesAsThisYearRatherThanRefusing() {
        // The field only appears on a shelf that needs a year, and the user has
        // already said the thing is finished — refusing the whole save over the
        // year would be the wrong trade.
        assertEquals(
            ShelfYears(finished = thisYear, abandoned = null),
            shelfYearsFromText(ReadingShelf.DONE, "", thisYear),
        )

        assertEquals(
            ShelfYears(finished = thisYear, abandoned = null),
            shelfYearsFromText(ReadingShelf.DONE, "nineteen", thisYear),
        )
    }

    @Test
    fun theTwoDirectionsAgreeWithEachOther() {
        ReadingShelf.entries.forEach { shelf ->
            val years = ShelfYears(finished = 2019, abandoned = 2018)
            val text = shelfYearText(shelf, years, thisYear)
            val backAgain = shelfYearsFromText(shelf, text, thisYear)

            assertEquals(
                "$shelf does not read back what it showed",
                resolvedShelfYears(shelf, years, years, thisYear),
                backAgain,
            )
        }
    }
}
