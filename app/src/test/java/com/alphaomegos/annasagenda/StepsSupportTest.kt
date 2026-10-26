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

    @Test
    fun theTabShowsEveryDayOfItsWindow_newestFirst_neverTomorrow() {
        val week = stepsWindowFor(AnthropometryRange.WEEK, day, null, emptyList())
        assertEquals(7, stepsListDays(week, day).size)
        assertEquals(day, stepsListDays(week, day).first())
        assertEquals(listOf(day, day.minusDays(1)), stepsListDays(DateWindow(day.minusDays(1), day.plusDays(3)), day))
        assertEquals(emptyList<LocalDate>(), stepsListDays(DateWindow(day.plusDays(1), day.plusDays(2)), day))
    }

    @Test
    fun allRunsFromTheFirstDayWithSteps() {
        assertEquals(DateWindow(day, day), stepsWindowFor(AnthropometryRange.ALL, day, null, emptyList()))
        val days = listOf(read(day.minusDays(40), 10), read(day.minusDays(2), 20))
        assertEquals(DateWindow(day.minusDays(40), day), stepsWindowFor(AnthropometryRange.ALL, day, null, days))
    }

    @Test
    fun aReadSaysWhatItFoundAndFromWhom() {
        val at = day.atTime(13, 40)
        val report = stepsReadReport(at, mapOf(day to 0, day.minusDays(1) to 5_000, day.minusDays(2) to 0), setOf("com.sec.android.app.shealth", "android"))
        assertEquals(3, report.daysRead)
        assertEquals(1, report.daysWithSteps)
        assertEquals(listOf("android", "com.sec.android.app.shealth"), report.sources)
        assertNull(report.error)
    }

    @Test
    fun theSourcesAreTheAppsTheUserKnows() {
        assertEquals(StepsSourceKind.SAMSUNG_HEALTH, stepsSourceKind("com.sec.android.app.shealth"))
        assertEquals(StepsSourceKind.THIS_PHONE, stepsSourceKind("android"))
        // Health Connect's June 2026 attribution for the phone's own steps.
        assertEquals(StepsSourceKind.THIS_PHONE, stepsSourceKind("com.android.healthconnect.phone.jd5bdd37e1a8d3667a05d0abebfc4a89e"))
        assertEquals(StepsSourceKind.FITBIT, stepsSourceKind("com.fitbit.FitbitMobile"))
        assertEquals(StepsSourceKind.OTHER, stepsSourceKind("com.example.pedometer"))
    }

    @Test
    fun theMonthsShortFormIsThousandsToOneDecimal() {
        assertEquals(12.3, stepsThousands(12_345), 0.0)
        assertEquals(10.0, stepsThousands(9_960), 0.0)
        assertEquals(0.4, stepsThousands(420), 0.0)
    }

    @Test
    fun pagesAreTheCalendarsOwnPeriods() {
        // 19 October 2026 is a Monday.
        assertEquals(DateWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), stepsPage(AnthropometryRange.MONTH, day, null, emptyList(), 0))
        assertEquals(DateWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), stepsPage(AnthropometryRange.MONTH, day, null, emptyList(), -1))
        assertEquals(DateWindow(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)), stepsPage(AnthropometryRange.WEEK, day, null, emptyList(), -1))
        assertEquals(DateWindow(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)), stepsPage(AnthropometryRange.QUARTER, day, null, emptyList(), -1))
        assertEquals(DateWindow(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), stepsPage(AnthropometryRange.YEAR, day, null, emptyList(), -1))
        val own = DateWindow(day.minusDays(9), day)
        assertEquals(DateWindow(day.minusDays(19), day.minusDays(10)), stepsPage(AnthropometryRange.CUSTOM, day, own, emptyList(), -1))
        assertEquals(3, stepsQuarterOf(DateWindow(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))))
        assertEquals(31, stepsChartDays(stepsPage(AnthropometryRange.MONTH, day, null, emptyList(), 0)).size)
    }

    @Test
    fun pagingStopsWhereTheStepsDoAndAtToday() {
        val month = stepsPage(AnthropometryRange.MONTH, day, null, emptyList(), 0)
        assertEquals(false, canPageStepsBack(AnthropometryRange.MONTH, month, listOf(read(day, 5))))
        assertEquals(true, canPageStepsBack(AnthropometryRange.MONTH, month, listOf(read(day.minusDays(40), 5))))
        assertEquals(false, canPageStepsForward(AnthropometryRange.MONTH, 0))
        assertEquals(true, canPageStepsForward(AnthropometryRange.MONTH, -1))
        assertEquals(false, canPageStepsBack(AnthropometryRange.ALL, month, listOf(read(day.minusDays(40), 5))))
    }

    @Test
    fun theScaleTopsAtARoundNumber() {
        assertEquals(20_000, niceStepsCeiling(19_002))
        assertEquals(10_000, niceStepsCeiling(8_304))
        assertEquals(1_500, niceStepsCeiling(1_492))
        // Not 50 000 for a day of 21 000 (25.10).
        assertEquals(25_000, niceStepsCeiling(21_000))
        assertEquals(20_000, niceStepsCeiling(18_000))
        assertEquals(10_000, niceStepsCeiling(10_000))
        assertEquals(0, niceStepsCeiling(0))
    }

    @Test
    fun theWidgetsWeekAndGoal() {
        val days = (1L..7L).map { read(day.minusDays(it), 7_000) } + read(day, 50_000)
        // The seven whole days before today; today is not over.
        assertEquals(7_000, stepsWeekAverage(days, day))
        assertEquals(4_000, stepsWeekAverage(days.drop(3), day))
        assertEquals(0.5f, stepsGoalProgress(5_000, 10_000), 0f)
        assertEquals(1f, stepsGoalProgress(25_000, 10_000), 0f)
        assertEquals(0f, stepsGoalProgress(5_000, 0), 0f)
    }

    @Test
    fun theChartsLabelsUnderneath() {
        val ru = java.util.Locale.forLanguageTag("ru")
        val week = stepsChartDays(stepsPage(AnthropometryRange.WEEK, day, null, emptyList(), 0))
        assertEquals("Пн", stepsAxisLabels(AnthropometryRange.WEEK, week, ru).first().second)
        assertEquals(7, stepsAxisLabels(AnthropometryRange.WEEK, week, ru).size)
        val month = stepsChartDays(stepsPage(AnthropometryRange.MONTH, day, null, emptyList(), 0))
        assertEquals(listOf("1", "10", "20", "30"), stepsAxisLabels(AnthropometryRange.MONTH, month, ru).map { it.second })
        assertEquals(listOf(0, 9, 19, 29), stepsAxisLabels(AnthropometryRange.MONTH, month, ru).map { it.first })
        val year = stepsChartDays(stepsPage(AnthropometryRange.YEAR, day, null, emptyList(), 0))
        assertEquals(12, stepsAxisLabels(AnthropometryRange.YEAR, year, ru).size)
    }
}
