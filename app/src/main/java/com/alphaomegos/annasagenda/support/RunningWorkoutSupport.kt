package com.alphaomegos.annasagenda

import java.time.LocalDate
import kotlin.math.roundToInt

/** Minutes and seconds per kilometre, the way runners say a pace. */
data class RunningPace(val minutes: Int, val seconds: Int)

/** What a stretch of the log adds up to. */
data class RunningWorkoutTotals(
    val runs: Int,
    val distanceKm: Double,
    val minutes: Int,
)

/**
 * A run written down after the fact.
 *
 * Null when there is nothing to write down. **One of the two numbers is
 * enough**: a treadmill run is forty minutes and no distance, a route run is
 * eight kilometres and a watch nobody looked at. Demanding both would mean
 * the person with one of them either types a lie or writes nothing, and a
 * missing number is honest where an invented one is not.
 *
 * What the two numbers can do depends on which of them are there, and that is
 * the whole reason to keep the missing one missing rather than zero-by-another-
 * name: a pace needs both, calories need the distance, and the week's hours
 * need the duration.
 */
fun runningWorkoutFromInput(
    id: Long,
    date: LocalDate,
    distanceText: String,
    durationText: String,
    note: String = "",
): RunningWorkout? {
    val km = parseRunningKm(distanceText)?.takeIf { it > 0.0 } ?: 0.0
    val minutes = parseRunningDurationToMinutes(durationText)?.takeIf { it > 0 } ?: 0

    if (km <= 0.0 && minutes <= 0) return null

    return RunningWorkout(
        id = id,
        date = date,
        distanceKm = km,
        durationMinutes = minutes,
        note = note.trim(),
    )
}

/**
 * The log with one more run in it, in date order.
 *
 * Sorted on the way in rather than on the way out, like the measurements are:
 * everything that reads this — the list, the totals, a future chart — reads it
 * as a sequence, and a list that is only sorted at one of those places is a
 * list that disagrees with itself at the others.
 *
 * Ties are broken by id, which only goes up, so two runs on the same day stay
 * in the order they were written down.
 */
fun workoutsAfterAdding(
    workouts: List<RunningWorkout>,
    workout: RunningWorkout,
): List<RunningWorkout> =
    (workouts + workout).sortedWith(compareBy({ it.date }, { it.id }))

/** Null when there was no such run, so the caller can leave the state alone. */
fun workoutsAfterDeleting(workouts: List<RunningWorkout>, id: Long): List<RunningWorkout>? {
    if (workouts.none { it.id == id }) return null
    return workouts.filterNot { it.id == id }
}

/**
 * Minutes and seconds per kilometre.
 *
 * Null unless both numbers are there — a pace made from one of them would be
 * a number with no meaning, and this is a screen people read numbers off.
 *
 * Rounded to the nearest second rather than truncated, because a pace is read
 * against another pace and a consistent half-second downwards would make every
 * run look very slightly better than the last one measured differently.
 */
fun runningWorkoutPace(workout: RunningWorkout): RunningPace? {
    if (workout.distanceKm <= 0.0 || workout.durationMinutes <= 0) return null

    val secondsPerKm = (workout.durationMinutes * 60.0 / workout.distanceKm).roundToInt()

    return RunningPace(minutes = secondsPerKm / 60, seconds = secondsPerKm % 60)
}

/** A pace as runners write it: 5'42". */
fun formatRunningPace(pace: RunningPace): String =
    "${pace.minutes}'${pace.seconds.toString().padStart(2, '0')}\""

/**
 * What the runs between two days add up to, both ends included.
 *
 * A run with no distance still counts as a run and still adds its minutes;
 * only the kilometres it does not have are missing. Dropping it from the
 * count entirely would mean the week said "3 runs" to somebody who went out
 * four times.
 */
fun runningWorkoutTotals(
    workouts: List<RunningWorkout>,
    from: LocalDate,
    to: LocalDate,
): RunningWorkoutTotals {
    val inRange = workouts.filter { it.date >= from && it.date <= to }

    return RunningWorkoutTotals(
        runs = inRange.size,
        distanceKm = inRange.sumOf { it.distanceKm },
        minutes = inRange.sumOf { it.durationMinutes },
    )
}
