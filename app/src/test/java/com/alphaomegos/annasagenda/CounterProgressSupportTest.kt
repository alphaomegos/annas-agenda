package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The countdown on a date counter, and the bar under it.
 *
 * Both used to be worked out inside the card that drew them, which is how the
 * countdown came to be written in English on every card in every language.
 */
class CounterProgressSupportTest {

    private val today = LocalDate.of(2026, 3, 10)

    private fun parts(end: LocalDate) = remainingUntil(today, end)

    private fun days(n: Int) = listOf(RemainingPart(RemainingUnit.DAYS, n))

    @Test
    fun aCountdownThatHasRunOutStillSaysZeroDays() {
        assertEquals(days(0), parts(today))
        assertEquals(days(0), parts(today.minusDays(1)))
        assertEquals(days(0), parts(today.minusYears(3)))
    }

    @Test
    fun tomorrowIsOneDay() {
        assertEquals(days(1), parts(today.plusDays(1)))
    }

    @Test
    fun aWholeMonthIsAMonthAndNoDays() {
        assertEquals(
            listOf(RemainingPart(RemainingUnit.MONTHS, 1)),
            parts(today.plusMonths(1)),
        )
    }

    @Test
    fun theTermsComeOutBiggestFirst() {
        val end = today.plusYears(1).plusMonths(2).plusDays(3)

        assertEquals(
            listOf(
                RemainingPart(RemainingUnit.YEARS, 1),
                RemainingPart(RemainingUnit.MONTHS, 2),
                RemainingPart(RemainingUnit.DAYS, 3),
            ),
            parts(end),
        )
    }

    @Test
    fun aTermThatIsZeroIsLeftOut() {
        val end = today.plusYears(2).plusDays(5)

        assertEquals(
            listOf(
                RemainingPart(RemainingUnit.YEARS, 2),
                RemainingPart(RemainingUnit.DAYS, 5),
            ),
            parts(end),
        )
    }

    /**
     * Months are calendar months. From the 31st, "one month" lands on the last
     * day of the shorter month and the leftover days are counted from there —
     * which is what a person reading a date counter means by a month.
     */
    @Test
    fun monthsAreCalendarMonthsNotThirtyDayBlocks() {
        val jan31 = LocalDate.of(2026, 1, 31)

        assertEquals(
            listOf(
                RemainingPart(RemainingUnit.MONTHS, 1),
                RemainingPart(RemainingUnit.DAYS, 1),
            ),
            remainingUntil(jan31, LocalDate.of(2026, 3, 1)),
        )
    }

    @Test
    fun aLeapDayIsCountedLikeAnyOther() {
        val feb28 = LocalDate.of(2028, 2, 28)

        assertEquals(days(1), remainingUntil(feb28, LocalDate.of(2028, 2, 29)))
        assertEquals(days(2), remainingUntil(feb28, LocalDate.of(2028, 3, 1)))
    }

    /* ---------- the bar ---------- */

    private val start = LocalDate.of(2026, 1, 1)
    private val end = LocalDate.of(2026, 1, 11)

    @Test
    fun halfWayThroughIsHalfABar() {
        assertEquals(0.5, remainingFractionOf(start, end, LocalDate.of(2026, 1, 6)), 1e-9)
    }

    @Test
    fun aBarIsFullBeforeItStartsAndEmptyAfterItEnds() {
        assertEquals(1.0, remainingFractionOf(start, end, start), 1e-9)
        assertEquals(1.0, remainingFractionOf(start, end, start.minusYears(1)), 1e-9)
        assertEquals(0.0, remainingFractionOf(start, end, end), 1e-9)
        assertEquals(0.0, remainingFractionOf(start, end, end.plusYears(1)), 1e-9)
    }

    /**
     * The editor asks for two dates and the user can get them the wrong way
     * round. Whatever the card then shows, it must not be a crash.
     */
    @Test
    fun aRangeThatIsEmptyOrBackwardsStillGivesANumber() {
        val f1 = remainingFractionOf(start, start, start)
        val f2 = remainingFractionOf(end, start, start)
        val f3 = remainingFractionOf(end, start, end)

        listOf(f1, f2, f3).forEach { f ->
            assertTrue("$f is not a fraction", f in 0.0..1.0)
            assertTrue("$f is not a number", !f.isNaN())
        }
    }

    @Test
    fun theBarNeverLeavesItsRange() {
        var day = start.minusDays(5)

        while (day.isBefore(end.plusDays(5))) {
            val f = remainingFractionOf(start, end, day)
            assertTrue("$day gave $f", f in 0.0..1.0)
            day = day.plusDays(1)
        }
    }
}
