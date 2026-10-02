package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * How much of the history the chart shows.
 *
 * Two things are easy to get wrong here and neither is visible from the
 * screen: an end that is a day short, and a range that quietly leaves out
 * today — which would hide the measurement the user typed in a moment ago and
 * opened the screen to look at.
 */
class AnthropometryRangeTest {

    private val today = LocalDate.of(2026, 9, 26)

    private fun entry(date: LocalDate, kg: Double = 70.0) =
        AnthropometryEntry(date = date, weightKg = kg)

    private fun window(
        range: AnthropometryRange,
        custom: DateWindow? = null,
        entries: List<AnthropometryEntry> = emptyList(),
    ) = anthropometryWindowFor(range, today, custom, entries)

    /* ---------------- the named ranges ---------------- */

    @Test
    fun aWeekIsSevenDaysWithTodayAsTheSeventh() {
        val w = window(AnthropometryRange.WEEK)

        assertEquals(LocalDate.of(2026, 9, 20), w.from)
        assertEquals(today, w.to)
        assertEquals(7, w.from.datesUntil(w.to.plusDays(1)).count())
    }

    @Test
    fun aMonthIsTheSameDayLastMonthPlusOne() {
        val w = window(AnthropometryRange.MONTH)

        assertEquals(LocalDate.of(2026, 8, 27), w.from)
        assertEquals(today, w.to)
    }

    @Test
    fun aQuarterIsThreeMonthsAndAYearIsTwelve() {
        assertEquals(LocalDate.of(2026, 6, 27), window(AnthropometryRange.QUARTER).from)
        assertEquals(LocalDate.of(2025, 9, 27), window(AnthropometryRange.YEAR).from)
    }

    /**
     * Every named range ends today. A chart that stops yesterday is a chart
     * missing the measurement the user came to see.
     */
    @Test
    fun everyNamedRangeEndsToday() {
        listOf(
            AnthropometryRange.WEEK,
            AnthropometryRange.MONTH,
            AnthropometryRange.QUARTER,
            AnthropometryRange.YEAR,
        ).forEach { range ->
            assertEquals(range.name, today, window(range).to)
        }
    }

    /**
     * The 31st is where "a month back" stops being arithmetic. There is no
     * 31st of February, and java.time answers with the last day of the month
     * rather than rolling over into March — which is what we want, and what a
     * hand-rolled minusDays(30) would get wrong by a different amount every
     * month.
     */
    @Test
    fun aMonthBackFromTheThirtyFirstLandsInsideTheShorterMonth() {
        val endOfMarch = LocalDate.of(2026, 3, 31)

        val w = anthropometryWindowFor(AnthropometryRange.MONTH, endOfMarch)

        assertEquals(LocalDate.of(2026, 3, 1), w.from)
        assertEquals(endOfMarch, w.to)
    }

    @Test
    fun aYearBackFromTheTwentyNinthOfFebruaryLandsOnARealDay() {
        val leapDay = LocalDate.of(2028, 2, 29)

        val w = anthropometryWindowFor(AnthropometryRange.YEAR, leapDay)

        assertEquals(LocalDate.of(2027, 3, 1), w.from)
        assertEquals(leapDay, w.to)
    }

    /* ---------------- all of it ---------------- */

    @Test
    fun allRunsFromTheFirstMeasurementToTheLast() {
        val entries = listOf(
            entry(LocalDate.of(2024, 1, 5)),
            entry(LocalDate.of(2025, 6, 6)),
            entry(LocalDate.of(2026, 2, 2)),
        )

        val w = window(AnthropometryRange.ALL, entries = entries)

        assertEquals(LocalDate.of(2024, 1, 5), w.from)
        assertEquals(LocalDate.of(2026, 2, 2), w.to)
    }

    @Test
    fun allFindsTheEndsEvenWhenTheListIsNotInOrder() {
        val entries = listOf(
            entry(LocalDate.of(2026, 2, 2)),
            entry(LocalDate.of(2024, 1, 5)),
            entry(LocalDate.of(2025, 6, 6)),
        )

        val w = window(AnthropometryRange.ALL, entries = entries)

        assertEquals(LocalDate.of(2024, 1, 5), w.from)
        assertEquals(LocalDate.of(2026, 2, 2), w.to)
    }

    /**
     * With nothing measured, "all" collapses onto today. The alternative is a
     * chart of the empty set spread across fifty-six years, starting at the
     * epoch.
     */
    @Test
    fun allOfNothingIsToday() {
        val w = window(AnthropometryRange.ALL)

        assertEquals(today, w.from)
        assertEquals(today, w.to)
    }

    /* ---------------- the user's own dates ---------------- */

    @Test
    fun aCustomRangeIsTheTwoDatesPicked() {
        val from = LocalDate.of(2026, 5, 1)
        val to = LocalDate.of(2026, 6, 1)

        val w = window(AnthropometryRange.CUSTOM, custom = DateWindow(from, to))

        assertEquals(from, w.from)
        assertEquals(to, w.to)
    }

    /**
     * A date picker will happily hand back an end before a start. Drawing
     * nothing at all would be technically right and useless.
     */
    @Test
    fun aCustomRangePickedBackwardsIsPutTheRightWayRound() {
        val w = window(
            AnthropometryRange.CUSTOM,
            custom = DateWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1)),
        )

        assertEquals(LocalDate.of(2026, 5, 1), w.from)
        assertEquals(LocalDate.of(2026, 6, 1), w.to)
    }

    @Test
    fun aCustomRangeOfOneDayIsThatOneDay() {
        val day = LocalDate.of(2026, 5, 1)

        val w = window(AnthropometryRange.CUSTOM, custom = DateWindow(day, day))

        assertEquals(day, w.from)
        assertEquals(day, w.to)
    }

    @Test
    fun aCustomRangeWithNothingPickedYetShowsAMonth() {
        assertEquals(
            window(AnthropometryRange.MONTH),
            window(AnthropometryRange.CUSTOM, custom = null),
        )
    }

    /* ---------------- what falls inside ---------------- */

    private val history = listOf(
        entry(LocalDate.of(2026, 9, 19)),
        entry(LocalDate.of(2026, 9, 20)),
        entry(LocalDate.of(2026, 9, 23)),
        entry(LocalDate.of(2026, 9, 26)),
        entry(LocalDate.of(2026, 9, 27)),
    )

    /**
     * Both ends count. The last measurement in a week's window is today's,
     * and the first is the one from seven days ago — not six.
     */
    @Test
    fun bothEndsOfTheWindowAreIncluded() {
        val kept = anthropometryEntriesIn(history, window(AnthropometryRange.WEEK))

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 26),
            ),
            kept.map { it.date },
        )
    }

    @Test
    fun aMeasurementDatedInTheFutureIsOutsideARangeThatEndsToday() {
        val kept = anthropometryEntriesIn(history, window(AnthropometryRange.WEEK))

        assertTrue(kept.none { it.date == LocalDate.of(2026, 9, 27) })
    }

    @Test
    fun theKeptMeasurementsComeBackInDateOrder() {
        val shuffled = history.reversed()

        val kept = anthropometryEntriesIn(shuffled, window(AnthropometryRange.YEAR))

        assertEquals(kept.sortedBy { it.date }.map { it.date }, kept.map { it.date })
    }

    @Test
    fun aWindowWithNothingInItKeepsNothing() {
        val w = DateWindow(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31))

        assertTrue(anthropometryEntriesIn(history, w).isEmpty())
    }

    @Test
    fun aWindowOfOneDayKeepsThatDaysMeasurement() {
        val w = DateWindow(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 23))

        assertEquals(listOf(LocalDate.of(2026, 9, 23)), anthropometryEntriesIn(history, w).map { it.date })
    }

    /* ---------------- paging back (0136) ---------------- */

    private val pagedRanges = listOf(
        AnthropometryRange.WEEK,
        AnthropometryRange.MONTH,
        AnthropometryRange.QUARTER,
        AnthropometryRange.YEAR,
        AnthropometryRange.CUSTOM,
    )

    private val pickedFortnight = DateWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14))

    private fun paged(range: AnthropometryRange, page: Int, on: LocalDate = today) =
        anthropometryWindowFor(range, on, custom = pickedFortnight, page = page)

    @Test
    fun pageZeroIsTheWindowItAlwaysWas() {
        pagedRanges.forEach { range ->
            assertEquals("$range", anthropometryWindowFor(range, today, custom = pickedFortnight), paged(range, 0))
        }
    }

    /**
     * Pages meet: each ends the day before the next one starts, with nothing
     * missing and nothing twice — checked two years back, from days that end
     * months badly (the 31st, a leap day).
     */
    @Test
    fun pagesMeetWithNoDayMissingAndNoDayTwice() {
        val awkwardDays = listOf(today, LocalDate.of(2026, 3, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2026, 1, 1))

        awkwardDays.forEach { on ->
            pagedRanges.forEach { range ->
                (0 downTo -24).zipWithNext().forEach { (later, earlier) ->
                    val after = paged(range, later, on)
                    val before = paged(range, earlier, on)
                    assertEquals("$range from $on, page $earlier", after.from.minusDays(1), before.to)
                    assertTrue("$range from $on, page $earlier", !before.from.isAfter(before.to))
                }
            }
        }
    }

    /** From 26.09.2026: the current week is 20–26, the month 27.08–26.09. */
    @Test
    fun aPageBackIsAsLongAsTheRangeSays() {
        assertEquals(DateWindow(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 19)), paged(AnthropometryRange.WEEK, -1))
        assertEquals(DateWindow(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 26)), paged(AnthropometryRange.MONTH, -1))
        assertEquals(DateWindow(LocalDate.of(2026, 3, 27), LocalDate.of(2026, 6, 26)), paged(AnthropometryRange.QUARTER, -1))
        assertEquals(DateWindow(LocalDate.of(2024, 9, 27), LocalDate.of(2025, 9, 26)), paged(AnthropometryRange.YEAR, -1))
        // A picked fortnight (1–14 September) pages back by a fortnight.
        assertEquals(DateWindow(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 31)), paged(AnthropometryRange.CUSTOM, -1))
    }

    @Test
    fun allHasNoPages() {
        val entries = listOf(entry(LocalDate.of(2025, 1, 1)), entry(today))
        val all = anthropometryWindowFor(AnthropometryRange.ALL, today, entries = entries)

        assertEquals(all, anthropometryWindowFor(AnthropometryRange.ALL, today, entries = entries, page = -3))
        assertTrue(!canPageAnthropometryBack(AnthropometryRange.ALL, all, entries))
        assertTrue(!canPageAnthropometryForward(AnthropometryRange.ALL, -1))
    }

    /** Back only while there is something earlier to see; forward only up to today. */
    @Test
    fun pagingGoesBackWhileThereIsDataAndForwardUpToToday() {
        val entries = listOf(entry(LocalDate.of(2026, 7, 10)), entry(today))
        val thisMonth = paged(AnthropometryRange.MONTH, 0)
        val lastMonth = paged(AnthropometryRange.MONTH, -1)
        val twoBack = paged(AnthropometryRange.MONTH, -2)
        val threeBack = paged(AnthropometryRange.MONTH, -3)

        // Months back: 27.08–26.09, 27.07–26.08, 27.06–26.07 (holds 10.07).
        assertTrue(canPageAnthropometryBack(AnthropometryRange.MONTH, thisMonth, entries))
        assertTrue(canPageAnthropometryBack(AnthropometryRange.MONTH, lastMonth, entries))
        assertEquals(1, anthropometryEntriesIn(entries, twoBack).size)
        assertTrue(!canPageAnthropometryBack(AnthropometryRange.MONTH, twoBack, entries))
        assertTrue(!canPageAnthropometryBack(AnthropometryRange.MONTH, threeBack, entries))

        assertTrue(!canPageAnthropometryForward(AnthropometryRange.MONTH, 0))
        assertTrue(canPageAnthropometryForward(AnthropometryRange.MONTH, -1))
    }
}
