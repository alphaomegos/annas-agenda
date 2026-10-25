package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Steps (0294, 19.10): what a read changes, what a hand changes, and what is worked out. */
class StepsSupportTest {

    private val day = LocalDate.of(2026, 10, 19)
    private fun read(d: LocalDate, n: Int) = StepDay(d, n, StepSource.HEALTH_CONNECT)
    private fun typed(d: LocalDate, n: Int) = StepDay(d, n, StepSource.MANUAL)

    @Test
    fun aReadFillsNewDaysAndGrowsOldOnes() {
        val before = listOf(read(day.minusDays(1), 4_000))
        val after = stepDaysAfterRead(before, mapOf(day.minusDays(1) to 9_500, day to 1_200))
        // Yesterday grew: the watch sent the evening's steps late.
        assertEquals(listOf(read(day.minusDays(1), 9_500), read(day, 1_200)), after)
    }

    @Test
    fun aTypedDayIsNeverWrittenOverByARead() {
        val before = listOf(typed(day, 12_000))
        assertEquals(before, stepDaysAfterRead(before, mapOf(day to 3_000)))
        assertEquals(before, stepDaysAfterRead(before, mapOf(day to 0)))
    }

    @Test
    fun aDayReadAsNothingIsNotKept_unlessHealthConnectLostIt() {
        assertEquals(emptyList<StepDay>(), stepDaysAfterRead(emptyList(), mapOf(day to 0)))
        assertEquals(emptyList<StepDay>(), stepDaysAfterRead(listOf(read(day, 500)), mapOf(day to 0)))
        // An imported day is not Health Connect's to take away.
        val imported = StepDay(day, 700, StepSource.SAMSUNG_IMPORT)
        assertEquals(listOf(imported), stepDaysAfterRead(listOf(imported), mapOf(day to 0)))
    }

    @Test
    fun typingADayAndTakingItBack() {
        val typedIn = stepDaysWithTyped(listOf(read(day, 5_000)), day, 15_000)
        assertEquals(listOf(typed(day, 15_000)), typedIn)
        // Taken back, the day is empty until the next read refills it.
        assertEquals(emptyList<StepDay>(), stepDaysWithoutTyped(typedIn, day))
        assertEquals(listOf(read(day, 6_000)), stepDaysAfterRead(stepDaysWithoutTyped(typedIn, day), mapOf(day to 6_000)))
        assertEquals(listOf(typed(day, 0)), stepDaysWithTyped(emptyList(), day, -5))
    }

    @Test
    fun oneDayPerDate_theTypedOneWinning() {
        val days = listOf(read(day, 1), typed(day, 2), read(day.minusDays(3), 3), StepDay(day.minusDays(1), -4, StepSource.HEALTH_CONNECT))
        assertEquals(listOf(read(day.minusDays(3), 3), typed(day, 2)), normalizedStepDays(days))
    }

    @Test
    fun theRecordsAreTheThreeBestDays_theEarlierFirstOnATie() {
        val days = listOf(
            read(day.minusDays(5), 8_000), read(day.minusDays(4), 21_000), read(day.minusDays(3), 15_000),
            read(day.minusDays(2), 21_000), read(day.minusDays(1), 2_000), read(day, 0),
        )
        val records = stepRecords(days)
        assertEquals(listOf(day.minusDays(4), day.minusDays(2), day.minusDays(3)), records.map { it.date })
        assertEquals(1, stepMedal(days[1], records))
        assertEquals(3, stepMedal(days[2], records))
        assertNull(stepMedal(days[0], records))
        assertEquals(emptyList<StepDay>(), stepRecords(listOf(read(day, 0))))
    }

    @Test
    fun theListIsTheWindowNewestFirst() {
        val days = (0L..9L).map { read(day.minusDays(it), 100 + it.toInt()) }
        val shown = stepDaysIn(days, DateWindow(day.minusDays(2), day))
        assertEquals(listOf(day, day.minusDays(1), day.minusDays(2)), shown.map { it.date })
        assertEquals(10, stepDaysIn(days, null).size)
    }

    @Test
    fun distanceAndEnergy() {
        // 170 cm: a step of 70.38 cm; 10 000 steps are 7.038 km.
        assertEquals(7.038, stepsDistanceKm(10_000, 170), 1e-9)
        // At 60 kg: half a kilocalorie a kilogram a kilometre — 211 kcal.
        assertEquals(211.14, stepsKcal(10_000, 170, 60.0), 1e-9)
    }

    @Test
    fun aDayIsWeighedWithTheLastWeightKnownByThen() {
        val log = listOf(
            AnthropometryEntry(date = day.minusDays(10), weightKg = 64.0),
            AnthropometryEntry(date = day.minusDays(5), waistCm = 70.0),
            AnthropometryEntry(date = day.minusDays(3), weightKg = 63.0),
        )
        assertEquals(63.0, weightOn(day, log)!!, 0.0)
        assertEquals(64.0, weightOn(day.minusDays(4), log)!!, 0.0)
        // Before the first weighing: the first weight, the nearest thing known.
        assertEquals(64.0, weightOn(day.minusDays(30), log)!!, 0.0)
        assertNull(weightOn(day, listOf(AnthropometryEntry(date = day, waistCm = 70.0))))
    }
}
