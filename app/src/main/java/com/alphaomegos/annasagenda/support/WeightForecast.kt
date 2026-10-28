package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.FoodEntry
import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.model.RunningWorkout
import com.alphaomegos.annasagenda.model.StepDay
import java.time.LocalDate

/*
 * The new weight forecast (28.10, Eduard): the energy balance of each day of
 * the period on screen. What the body spends — at rest by Mifflin–St Jeor
 * from the profile and the day's weight, times 1.2 — plus walking and
 * running, less what was eaten; a day with nothing in the calorimeter counts
 * as eaten exactly to that norm. The sum over 7 700 kcal a kilogram.
 * «Summer 2026» — the calorimeter's goals less what was eaten, last 30 days,
 * over 7 800 — stays a choice.
 */

/** Kilocalories in a kilogram of body weight for the new forecast — the usual figure. */
const val KCAL_PER_KG_BODY = 7700.0

/** How the forecast is worked out: the new energy balance, or the summer's calorimeter deficit. */
enum class ForecastMethod { NEW, SUMMER_2026 }

fun parseForecastMethod(raw: String): ForecastMethod =
    runCatching { ForecastMethod.valueOf(raw) }.getOrElse { ForecastMethod.NEW }

/** The forecast for a period, and what it is made of. */
data class WeightForecast(
    val days: Int,
    val eatenKcal: Long,
    val normDays: Int,
    val steps: Long,
    val stepsKcal: Double,
    val runKcal: Double,
    val balanceKcal: Double,
    // The change the energy says: negative is a loss.
    val changeKg: Double,
    // The change the scales said over the same period, if weighed twice in it.
    val measuredKg: Double?,
) {
    val stepsKg: Double get() = stepsKcal / KCAL_PER_KG_BODY
    val runKg: Double get() = runKcal / KCAL_PER_KG_BODY
    /** The scales less the energy: how far the forecast was off. */
    val discrepancyKg: Double? get() = measuredKg?.let { it - changeKg }
}

/**
 * The period's forecast, from [from] to [to] but never past [today]; null
 * without the profile's sex, height and year of birth, or without a single
 * weight measured — the body's own use needs them.
 */
fun newWeightForecast(
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    sex: ProfileSex?,
    heightCm: Int?,
    birthYear: Int?,
    anthropometry: List<AnthropometryEntry>,
    foodLog: List<FoodEntry>,
    stepDays: List<StepDay>,
    workouts: List<RunningWorkout>,
): WeightForecast? {
    if (sex == null || heightCm == null || birthYear == null) return null
    if (anthropometry.none { it.weightKg != null }) return null
    val last = if (to.isAfter(today)) today else to
    if (last.isBefore(from)) return null
    val eatenBy = foodLog.filter { !it.date.isBefore(from) && !it.date.isAfter(last) }.groupBy { it.date }.mapValues { (_, e) -> e.sumOf { it.kcal } }
    val stepsBy = stepDays.associate { it.date to it.steps }
    val runBy = workouts.filter { !it.date.isBefore(from) && !it.date.isAfter(last) }.groupBy { it.date }
    var days = 0
    var eaten = 0L
    var normDays = 0
    var steps = 0L
    var stepsKcal = 0.0
    var runKcal = 0.0
    var balance = 0.0
    var d = from
    while (!d.isAfter(last)) {
        val weight = weightOn(d, anthropometry) ?: return null
        val baseline = baselineKcal(sex, weight, heightCm, ageIn(d.year, birthYear))
        val ate = eatenBy[d]
        val intake = ate?.toDouble() ?: baseline
        if (ate == null) normDays++ else eaten += ate
        val s = stepsBy[d] ?: 0
        val walk = stepsKcal(s, heightCm, weight)
        val run = runBy[d].orEmpty().sumOf { KCAL_PER_KG_PER_KM * weight * it.distanceKm }
        steps += s
        stepsKcal += walk
        runKcal += run
        balance += baseline + walk + run - intake
        days++
        d = d.plusDays(1)
    }
    val weighed = anthropometry.filter { it.weightKg != null && !it.date.isBefore(from) && !it.date.isAfter(last) }.sortedBy { it.date }
    val measured = if (weighed.size >= 2) weighed.last().weightKg!! - weighed.first().weightKg!! else null
    return WeightForecast(days, eaten, normDays, steps, stepsKcal, runKcal, balance, -balance / KCAL_PER_KG_BODY, measured)
}
