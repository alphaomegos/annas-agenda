package com.alphaomegos.annasagenda

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Kilocalories a kilogram of body spends being carried one kilometre at a run.
 *
 * The figure everybody uses, and it is a rough one: the real cost depends on
 * pace, gradient, wind and the runner. It is roughly flat across paces for
 * running specifically, which is why distance alone is enough here and why a
 * run logged without a time still gets a number.
 *
 * Calling it an estimate is not a disclaimer, it is the reason the result is
 * only ever added to an estimate — the thirty-day deficit, which is already a
 * goal minus what somebody remembered to write down.
 */
const val KCAL_PER_KG_PER_KM = 1.036

/**
 * The body doing the running, as far as the measurements know.
 *
 * The most recent weight written down on or before that day, because that is
 * the last thing known to be true at the time. Failing that, the earliest
 * weight ever written down: somebody who started weighing themselves in March
 * still ran in February, and their February body was much nearer their March
 * weight than nothing at all.
 *
 * Null only when no weight has ever been recorded. There is no default body,
 * and inventing one would put a number on the screen that is about nobody.
 */
fun bodyWeightOn(entries: List<AnthropometryEntry>, date: LocalDate): Double? {
    val weighed = entries.filter { it.weightKg != null }
    if (weighed.isEmpty()) return null

    return weighed
        .filter { !it.date.isAfter(date) }
        .maxByOrNull { it.date }
        ?.weightKg
        ?: weighed.minByOrNull { it.date }?.weightKg
}

/** What one run cost, or null when there is no distance or no body to weigh. */
fun caloriesBurnedBy(workout: RunningWorkout, weightKg: Double?): Int? {
    if (workout.distanceKm <= 0.0) return null
    if (weightKg == null || weightKg <= 0.0) return null

    return (KCAL_PER_KG_PER_KM * weightKg * workout.distanceKm).roundToInt()
}

/**
 * What the running between two days cost, both ends included.
 *
 * Each run is priced against the body that ran it rather than against today's
 * weight — over a month that is a small difference, and over a year of
 * training it is not.
 *
 * Runs with no distance contribute nothing rather than being guessed at from
 * their duration. A time with no distance could be an hour of intervals or an
 * hour of walking the dog, and the difference is the whole number.
 */
fun caloriesBurnedRunning(
    workouts: List<RunningWorkout>,
    anthropometry: List<AnthropometryEntry>,
    from: LocalDate,
    to: LocalDate,
): Int =
    workouts
        .filter { it.date >= from && it.date <= to }
        .sumOf { caloriesBurnedBy(it, bodyWeightOn(anthropometry, it.date)) ?: 0 }
