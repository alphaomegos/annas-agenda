package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.monthCellHeightPx
import com.alphaomegos.annasagenda.support.monthGridDates
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The month's rows fit the screen to the pixel (0311: the folded Fold 3 lost the last week's lower edge). */
class MonthGridTest {

    /** The month's weeks whole (0366): the days of the months around fill the first and the last week. */
    @Test
    fun theWeeksAreWhole_withTheDaysAround() {
        // October 2026 starts on a Thursday: three of September's days ahead of it on a Monday-first week.
        val october = monthGridDates(YearMonth.of(2026, 10), leading = 3)
        assertEquals(LocalDate.of(2026, 9, 28), october.first())
        assertEquals(LocalDate.of(2026, 10, 1), october[3])
        assertEquals(LocalDate.of(2026, 11, 1), october.last())
        assertEquals(35, october.size)
        assertTrue(october.zipWithNext().all { (a, b) -> b == a.plusDays(1) })
        // A month that fills its weeks exactly gets nothing around it.
        val february = monthGridDates(YearMonth.of(2027, 2), leading = 0)
        assertEquals(28, february.size)
        assertTrue(february.all { YearMonth.from(it) == YearMonth.of(2027, 2) })
        // Six weeks when it needs them: August 2027 starts on a Sunday.
        val august = monthGridDates(YearMonth.of(2027, 8), leading = 6)
        assertEquals(42, august.size)
        assertEquals(LocalDate.of(2027, 7, 26), august.first())
        assertEquals(LocalDate.of(2027, 9, 5), august.last())
        // Across the year's end: December 2026 starts on a Tuesday.
        val december = monthGridDates(YearMonth.of(2026, 12), leading = 1)
        assertEquals(LocalDate.of(2026, 11, 30), december.first())
        assertEquals(LocalDate.of(2027, 1, 3), december.last())
    }
    @Test
    fun rowsAndGapsNeverComeToMoreThanTheScreen() {
        for (available in 300..1400 step 7) for (rows in 4..6) for (gap in listOf(15, 16, 18)) {
            val cell = monthCellHeightPx(available, gap, rows)
            assertTrue("$available/$rows/$gap", cell * rows + gap * (rows - 1) <= available)
            assertTrue("no more than a pixel a row lost", available - (cell * rows + gap * (rows - 1)) < rows)
        }
        assertEquals(0, monthCellHeightPx(10, 16, 6))
    }

    /** The figure stays; the number goes beside it if it can, under it if not (0313). */
    @Test
    fun stepsSitBesideTheFigureOrUnderIt() {
        val texts = listOf("12 345", "12,3k", "12k")
        assertEquals(true to "12,3k", com.alphaomegos.annasagenda.support.stepsCellChoice(texts, { it.length <= 5 }, { true }))
        assertEquals(false to "12 345", com.alphaomegos.annasagenda.support.stepsCellChoice(texts, { false }, { true }))
        assertEquals(false to "12k", com.alphaomegos.annasagenda.support.stepsCellChoice(texts, { false }, { it.length <= 3 }))
        assertEquals(false to "12k", com.alphaomegos.annasagenda.support.stepsCellChoice(texts, { false }, { false }))
    }
}
