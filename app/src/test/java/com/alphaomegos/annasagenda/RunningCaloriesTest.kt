package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * What the running cost, in the only currency the calorimeter speaks.
 *
 * The number is an estimate resting on another estimate, and the tests are
 * mostly about the edges where an estimate turns into a fabrication: a run
 * with no distance, a runner with no recorded weight, a run from before
 * anybody started weighing themselves.
 */
class RunningCaloriesTest {

    private val march = LocalDate.of(2026, 3, 1)

    private fun weighed(date: LocalDate, kg: Double?) =
        AnthropometryEntry(date = date, weightKg = kg)

    private fun run(date: LocalDate = march, km: Double = 10.0, minutes: Int = 55) =
        RunningWorkout(id = 1L, date = date, distanceKm = km, durationMinutes = minutes)

    /* ---------------- the body that ran ---------------- */

    @Test
    fun theWeightIsTheLastOneWrittenDownBeforeThatDay() {
        val log = listOf(
            weighed(LocalDate.of(2026, 1, 1), 75.0),
            weighed(LocalDate.of(2026, 2, 1), 73.0),
            weighed(LocalDate.of(2026, 4, 1), 71.0),
        )

        assertEquals(73.0, bodyWeightOn(log, march)!!, 0.0001)
    }

    @Test
    fun aWeightWrittenDownOnTheDayItselfCounts() {
        val log = listOf(weighed(march, 72.0))

        assertEquals(72.0, bodyWeightOn(log, march)!!, 0.0001)
    }

    /**
     * Somebody who started weighing themselves in March still ran in
     * February, and their February body was much nearer their March weight
     * than nothing at all.
     */
    @Test
    fun aRunFromBeforeTheFirstWeighingUsesTheFirstWeighing() {
        val log = listOf(weighed(LocalDate.of(2026, 3, 15), 72.0))

        assertEquals(72.0, bodyWeightOn(log, LocalDate.of(2026, 2, 1))!!, 0.0001)
    }

    @Test
    fun entriesWithNoWeightAreIgnored() {
        val log = listOf(
            weighed(LocalDate.of(2026, 2, 1), 73.0),
            AnthropometryEntry(date = LocalDate.of(2026, 2, 20), waistCm = 80.0),
        )

        assertEquals(73.0, bodyWeightOn(log, march)!!, 0.0001)
    }

    /**
     * There is no default body. Inventing one puts a number on the screen
     * that is about nobody.
     */
    @Test
    fun withNoWeightEverRecordedThereIsNoBody() {
        assertNull(bodyWeightOn(emptyList(), march))
        assertNull(bodyWeightOn(listOf(AnthropometryEntry(date = march, waistCm = 80.0)), march))
    }

    @Test
    fun theOrderTheEntriesAreStoredInDoesNotMatter() {
        val log = listOf(
            weighed(LocalDate.of(2026, 4, 1), 71.0),
            weighed(LocalDate.of(2026, 1, 1), 75.0),
            weighed(LocalDate.of(2026, 2, 1), 73.0),
        )

        assertEquals(73.0, bodyWeightOn(log, march)!!, 0.0001)
    }

    /* ---------------- one run ---------------- */

    @Test
    fun aRunCostsItsDistanceTimesTheBodyCarryingIt() {
        // 1.036 * 72 * 10 = 745.9
        assertEquals(746, caloriesBurnedBy(run(km = 10.0), 72.0))
    }

    @Test
    fun halfTheDistanceIsHalfTheCost() {
        assertEquals(373, caloriesBurnedBy(run(km = 5.0), 72.0))
    }

    /**
     * A time with no distance could be an hour of intervals or an hour of
     * walking the dog, and the difference is the whole number.
     */
    @Test
    fun aRunWithNoDistanceCostsNothingKnowable() {
        assertNull(caloriesBurnedBy(run(km = 0.0, minutes = 60), 72.0))
    }

    @Test
    fun aRunByNobodyCostsNothingKnowable() {
        assertNull(caloriesBurnedBy(run(), null))
        assertNull(caloriesBurnedBy(run(), 0.0))
        assertNull(caloriesBurnedBy(run(), -70.0))
    }

    /* ---------------- a stretch of them ---------------- */

    private val log = listOf(weighed(LocalDate.of(2026, 1, 1), 70.0))

    private val month = listOf(
        RunningWorkout(id = 1, date = LocalDate.of(2026, 3, 1), distanceKm = 10.0, durationMinutes = 55),
        RunningWorkout(id = 2, date = LocalDate.of(2026, 3, 10), distanceKm = 5.0, durationMinutes = 26),
        RunningWorkout(id = 3, date = LocalDate.of(2026, 3, 20), distanceKm = 0.0, durationMinutes = 40),
    )

    @Test
    fun theRunsInRangeAreAddedUp() {
        val burned = caloriesBurnedRunning(
            month,
            log,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31),
        )

        // 1.036 * 70 * 10 = 725.2 -> 725, and 1.036 * 70 * 5 = 362.6 -> 363.
        assertEquals(725 + 363, burned)
    }

    @Test
    fun bothEndsOfTheRangeCount() {
        assertEquals(
            725,
            caloriesBurnedRunning(month, log, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)),
        )
    }

    @Test
    fun runsOutsideTheRangeAreLeftOut() {
        assertEquals(
            0,
            caloriesBurnedRunning(month, log, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
        )
    }

    /**
     * Each run is priced against the body that ran it. Over a month that is a
     * small difference; over a year of training it is not.
     */
    @Test
    fun eachRunIsPricedAgainstTheBodyThatRanIt() {
        val losingWeight = listOf(
            weighed(LocalDate.of(2026, 1, 1), 90.0),
            weighed(LocalDate.of(2026, 3, 15), 70.0),
        )

        val runs = listOf(
            RunningWorkout(id = 1, date = LocalDate.of(2026, 1, 5), distanceKm = 10.0, durationMinutes = 60),
            RunningWorkout(id = 2, date = LocalDate.of(2026, 3, 20), distanceKm = 10.0, durationMinutes = 55),
        )

        val burned = caloriesBurnedRunning(
            runs,
            losingWeight,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
        )

        // 1.036 * 90 * 10 = 932.4 -> 932, and 1.036 * 70 * 10 = 725.2 -> 725.
        assertEquals(932 + 725, burned)
    }

    @Test
    fun withNoWeightEverRecordedTheRunningCostsNothing() {
        assertEquals(
            0,
            caloriesBurnedRunning(month, emptyList(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
        )
    }

    @Test
    fun nothingLoggedCostsNothing() {
        assertEquals(
            0,
            caloriesBurnedRunning(emptyList(), log, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
        )
    }
}
