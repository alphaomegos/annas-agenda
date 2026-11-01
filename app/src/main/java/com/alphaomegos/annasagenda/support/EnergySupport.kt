package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.model.RunningWorkout
import com.alphaomegos.annasagenda.model.StepDay
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * The body's own energy use (28.10), for the new weight forecast: the
 * Mifflin–St Jeor equation, the one dietitians reach for first.
 */

/** Years of birth the profile takes. */
val PROFILE_BIRTH_YEARS = 1900..2100

/** Age in [year] for someone born in [birthYear] (the year's birthday reached or not: near enough here). */
fun ageIn(year: Int, birthYear: Int): Int = (year - birthYear).coerceAtLeast(0)

/**
 * Resting energy in kcal a day (Mifflin–St Jeor): 10 × kg + 6.25 × cm − 5 ×
 * years, plus 5 for men, minus 161 for women.
 */
fun restingKcal(sex: ProfileSex, weightKg: Double, heightCm: Int, ageYears: Int): Double =
    10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + if (sex == ProfileSex.MALE) 5.0 else -161.0

/**
 * The day's energy before walking and running are counted: rest times 1.2,
 * «sedentary». Low on purpose — the forecast adds steps and runs itself, and a
 * higher factor would count them twice.
 */
const val SEDENTARY_FACTOR = 1.2

fun baselineKcal(sex: ProfileSex, weightKg: Double, heightCm: Int, ageYears: Int): Double =
    restingKcal(sex, weightKg, heightCm, ageYears) * SEDENTARY_FACTOR

/** A daily norm the app will take as the user's own, in kcal. */
val DAILY_NORM_RANGE = 800..6000

/**
 * A day's energy as the weight forecast counts it (01.11, for the calorimeter): the
 * norm — the user's own, or rest × 1.2 from the profile and the last weight — and the
 * day's walking and running on top. Null when there is no own norm and the profile or
 * a weight is missing; steps and runs count only where height and weight are known.
 */
data class DayEnergy(val normKcal: Int, val ownNorm: Boolean, val stepsKcal: Int, val runKcal: Int) {
    val totalKcal: Int get() = normKcal + stepsKcal + runKcal
}

fun dayEnergy(
    date: LocalDate,
    sex: ProfileSex?,
    heightCm: Int?,
    birthYear: Int?,
    anthropometry: List<AnthropometryEntry>,
    stepDays: List<StepDay>,
    workouts: List<RunningWorkout>,
    planRuns: List<Pair<LocalDate, Double>> = emptyList(),
    ownNormKcal: Int? = null,
): DayEnergy? {
    val weight = weightOn(date, anthropometry)
    val norm = ownNormKcal?.toDouble()
        ?: if (sex != null && heightCm != null && birthYear != null && weight != null) {
            baselineKcal(sex, weight, heightCm, ageIn(date.year, birthYear))
        } else {
            return null
        }
    val steps = stepDays.firstOrNull { it.date == date }?.steps ?: 0
    val walk = if (heightCm != null && weight != null) stepsKcal(steps, heightCm, weight) else 0.0
    val km = workouts.filter { it.date == date }.sumOf { it.distanceKm } + planRuns.filter { it.first == date }.sumOf { it.second }
    val run = if (weight != null) KCAL_PER_KG_PER_KM * weight * km else 0.0
    return DayEnergy(norm.roundToInt(), ownNormKcal != null, walk.roundToInt(), run.roundToInt())
}
