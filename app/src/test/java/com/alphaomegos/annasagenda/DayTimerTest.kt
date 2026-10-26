package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.DayTimerCounter
import com.alphaomegos.annasagenda.model.ManualCounter
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The day counter (0319, 26.10): 1 on its first day, one more each midnight, plus the hand correction. */
class DayTimerTest {

    private val start = LocalDate.of(2026, 8, 17)
    private fun timer(correction: Int = 0) = DayTimerCounter(1, "Ногти", start, correction)

    @Test
    fun theNumberIsTheDaysSinceTheStartPlusTheCorrection() {
        assertEquals(1, dayTimerValue(timer(), start))
        assertEquals(2, dayTimerValue(timer(), start.plusDays(1)))
        // Two months, a week and two days on it is day 71; corrected by +2, it shows 73.
        assertEquals(71 + 2, dayTimerValue(timer(2), LocalDate.of(2026, 10, 26)))
        // Started tomorrow: nothing yet but the correction.
        assertEquals(-1, dayTimerValue(timer(-1), start.minusDays(1)))
    }

    @Test
    fun howLongSinceTheStart_asTheCalendarHasIt() {
        val parts = dayTimerElapsedParts(start, LocalDate.of(2026, 10, 26))
        assertEquals(
            listOf(RemainingPart(RemainingUnit.MONTHS, 2), RemainingPart(RemainingUnit.WEEKS, 1), RemainingPart(RemainingUnit.DAYS, 2)),
            parts,
        )
        assertEquals(emptyList<RemainingPart>(), dayTimerElapsedParts(start, start))
        assertEquals(listOf(RemainingPart(RemainingUnit.YEARS, 1)), dayTimerElapsedParts(start, start.plusYears(1)))
        assertEquals(listOf(RemainingPart(RemainingUnit.WEEKS, 2)), dayTimerElapsedParts(start, start.plusDays(14)))
    }

    @Test
    fun correctingAndStartingOver() {
        val counters = listOf(timer(), ManualCounter(2, "Другое", 5))
        val corrected = countersWithDayTimerCorrection(countersWithDayTimerCorrection(counters, 1, 1), 1, 1)
        assertEquals(2, (corrected[0] as DayTimerCounter).correction)
        assertEquals(counters[1], corrected[1])
        val today = LocalDate.of(2026, 10, 26)
        val again = countersWithDayTimerRestarted(corrected, 1, today)[0] as DayTimerCounter
        assertEquals(today, again.startDate)
        assertEquals(0, again.correction)
        assertEquals(1, dayTimerValue(again, today))
    }
}
