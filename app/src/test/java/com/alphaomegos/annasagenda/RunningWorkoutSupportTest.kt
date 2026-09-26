package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Runs written down after the fact.
 *
 * The rule most of this is about: **one of the two numbers is enough.** A
 * treadmill run is forty minutes and no distance; a route run is eight
 * kilometres and a watch nobody looked at. Demanding both would mean the
 * person with one of them either types a lie or writes nothing, and a missing
 * number is honest where an invented one is not.
 */
class RunningWorkoutSupportTest {

    private val day = LocalDate.of(2026, 9, 20)

    private fun input(
        distance: String,
        duration: String,
        note: String = "",
        id: Long = 1L,
        date: LocalDate = day,
    ) = runningWorkoutFromInput(id, date, distance, duration, note)

    private fun run(
        id: Long,
        date: LocalDate = day,
        km: Double = 10.0,
        minutes: Int = 55,
    ) = RunningWorkout(id = id, date = date, distanceKm = km, durationMinutes = minutes)

    /* ---------------- writing one down ---------------- */

    @Test
    fun aRunWithBothNumbersIsWrittenDown() {
        val workout = input("10,5", "0058")!!

        assertEquals(10.5, workout.distanceKm, 0.0001)
        assertEquals(58, workout.durationMinutes)
        assertEquals(day, workout.date)
    }

    @Test
    fun aDotAndACommaAreTheSameDecimalPoint() {
        assertEquals(10.5, input("10.5", "0058")!!.distanceKm, 0.0001)
        assertEquals(10.5, input("10,5", "0058")!!.distanceKm, 0.0001)
    }

    @Test
    fun aRunWithOnlyADistanceIsStillARun() {
        val workout = input("8", "")!!

        assertEquals(8.0, workout.distanceKm, 0.0001)
        assertEquals(0, workout.durationMinutes)
    }

    @Test
    fun aRunWithOnlyADurationIsStillARun() {
        val workout = input("", "0040")!!

        assertEquals(0.0, workout.distanceKm, 0.0001)
        assertEquals(40, workout.durationMinutes)
    }

    @Test
    fun aRunWithNeitherNumberIsNotARun() {
        assertNull(input("", ""))
        assertNull(input("   ", "  "))
        assertNull(input("0", "0"))
        assertNull(input("нет", "нет"))
    }

    @Test
    fun aNegativeOrZeroNumberCountsAsNotGiven() {
        assertEquals(0.0, input("-5", "0040")!!.distanceKm, 0.0001)
        assertNull(input("-5", ""))
    }

    @Test
    fun theNoteIsTrimmedAndKept() {
        assertEquals("набережная", input("8", "0040", note = "  набережная  ")!!.note)
        assertEquals("", input("8", "0040")!!.note)
    }

    /* ---------------- the log ---------------- */

    @Test
    fun aNewRunGoesIntoTheLogInDateOrder() {
        val log = listOf(
            run(1, date = LocalDate.of(2026, 9, 1)),
            run(2, date = LocalDate.of(2026, 9, 30)),
        )

        val after = workoutsAfterAdding(log, run(3, date = LocalDate.of(2026, 9, 15)))

        assertEquals(listOf(1L, 3L, 2L), after.map { it.id })
    }

    @Test
    fun anUnsortedLogComesBackSorted() {
        val log = listOf(
            run(2, date = LocalDate.of(2026, 9, 30)),
            run(1, date = LocalDate.of(2026, 9, 1)),
        )

        val after = workoutsAfterAdding(log, run(3, date = LocalDate.of(2026, 9, 15)))

        assertEquals(listOf(1L, 3L, 2L), after.map { it.id })
    }

    /**
     * Two runs on one day keep the order they were written down in, because
     * ids only ever go up.
     */
    @Test
    fun twoRunsOnOneDayStayInTheOrderTheyWereWritten() {
        val morning = run(10)
        val evening = run(11)

        assertEquals(
            listOf(10L, 11L),
            workoutsAfterAdding(listOf(evening), morning).map { it.id },
        )
    }

    @Test
    fun aRunCanBeTakenOutAgain() {
        val log = listOf(run(1), run(2), run(3))

        assertEquals(listOf(1L, 3L), workoutsAfterDeleting(log, 2L)!!.map { it.id })
    }

    /**
     * Null rather than the same list, so the caller can leave the state
     * untouched instead of writing an identical copy of it.
     */
    @Test
    fun deletingSomethingThatIsNotThereAnswersNothing() {
        assertNull(workoutsAfterDeleting(listOf(run(1)), 404L))
        assertNull(workoutsAfterDeleting(emptyList(), 1L))
    }

    /* ---------------- pace ---------------- */

    @Test
    fun aPaceIsMinutesAndSecondsPerKilometre() {
        val pace = runningWorkoutPace(run(1, km = 10.0, minutes = 55))!!

        assertEquals(5, pace.minutes)
        assertEquals(30, pace.seconds)
    }

    @Test
    fun aPaceIsRoundedToTheNearestSecond() {
        // 42 minutes over 7.3 km is 345.2 seconds per kilometre.
        val pace = runningWorkoutPace(run(1, km = 7.3, minutes = 42))!!

        assertEquals(5, pace.minutes)
        assertEquals(45, pace.seconds)
    }

    /**
     * A pace made from one of the two numbers would be a number with no
     * meaning, on a screen people read numbers off.
     */
    @Test
    fun aRunMissingEitherNumberHasNoPace() {
        assertNull(runningWorkoutPace(run(1, km = 0.0, minutes = 40)))
        assertNull(runningWorkoutPace(run(1, km = 8.0, minutes = 0)))
    }

    @Test
    fun aPaceIsWrittenTheWayRunnersWriteIt() {
        assertEquals("5'30\"", formatRunningPace(RunningPace(5, 30)))
        assertEquals("5'05\"", formatRunningPace(RunningPace(5, 5)))
        assertEquals("10'00\"", formatRunningPace(RunningPace(10, 0)))
    }

    /* ---------------- totals ---------------- */

    private val week = listOf(
        run(1, date = LocalDate.of(2026, 9, 20), km = 10.0, minutes = 55),
        run(2, date = LocalDate.of(2026, 9, 23), km = 5.0, minutes = 26),
        run(3, date = LocalDate.of(2026, 9, 26), km = 21.1, minutes = 120),
    )

    @Test
    fun theTotalsAddUpWhatIsInsideTheRange() {
        val totals = runningWorkoutTotals(
            week,
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 23),
        )

        assertEquals(2, totals.runs)
        assertEquals(15.0, totals.distanceKm, 0.0001)
        assertEquals(81, totals.minutes)
    }

    @Test
    fun bothEndsOfTheRangeCount() {
        val totals = runningWorkoutTotals(
            week,
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 26),
        )

        assertEquals(3, totals.runs)
    }

    /**
     * A run with no distance is still a run and still spent its minutes. Not
     * counting it would tell somebody who went out four times that they went
     * out three.
     */
    @Test
    fun aRunWithNoDistanceStillCountsAsARun() {
        val log = listOf(run(1, km = 0.0, minutes = 40))

        val totals = runningWorkoutTotals(log, day, day)

        assertEquals(1, totals.runs)
        assertEquals(0.0, totals.distanceKm, 0.0001)
        assertEquals(40, totals.minutes)
    }

    @Test
    fun aRangeWithNothingInItAddsUpToNothing() {
        val totals = runningWorkoutTotals(
            week,
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 12, 31),
        )

        assertEquals(0, totals.runs)
        assertEquals(0.0, totals.distanceKm, 0.0001)
        assertEquals(0, totals.minutes)
        assertTrue(totals.distanceKm == 0.0)
    }
}
