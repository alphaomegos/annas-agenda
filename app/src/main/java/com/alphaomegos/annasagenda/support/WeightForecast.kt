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
 * running, less what was eaten. A day with nothing in the calorimeter is a
 * day without deficit (Eduard: «съел ровно норму»): it adds nothing, its
 * walking and running included — eaten to the day's whole spending (28.10;
 * counting its walking had a year of steps make 20 kg the scales never saw).
 * The sum over 7 700 kcal a kilogram.
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
    // Days with food written down: the only ones that count.
    val loggedDays: Int,
    val eatenKcal: Long,
    val normDays: Int,
    // The runs' kilometres on the counted days: logged workouts and plan runs done.
    val runKm: Double,
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
    // Runs of the plan, done (28.10: a year's running came to 1 600 kcal
    // because only «between races» workouts were counted).
    planRuns: List<Pair<LocalDate, Double>> = emptyList(),
    // The user's own daily norm, instead of the formula's, when set.
    normKcal: Int? = null,
): WeightForecast? {
    if (sex == null || heightCm == null || birthYear == null) return null
    if (anthropometry.none { it.weightKg != null }) return null
    val last = if (to.isAfter(today)) today else to
    if (last.isBefore(from)) return null
    val eatenBy = foodLog.filter { !it.date.isBefore(from) && !it.date.isAfter(last) }.groupBy { it.date }.mapValues { (_, e) -> e.sumOf { it.kcal } }
    val stepsBy = stepDays.associate { it.date to it.steps }
    val runKmBy = (workouts.map { it.date to it.distanceKm } + planRuns)
        .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(last) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, kms) -> kms.sum() }
    var days = 0
    var logged = 0
    var runKm = 0.0
    var eaten = 0L
    var normDays = 0
    var steps = 0L
    var stepsKcal = 0.0
    var runKcal = 0.0
    var balance = 0.0
    var d = from
    while (!d.isAfter(last)) {
        days++
        val ate = eatenBy[d]
        if (ate == null) {
            // No deficit that day: eaten to all it spent.
            normDays++
            d = d.plusDays(1)
            continue
        }
        val weight = weightOn(d, anthropometry) ?: return null
        val baseline = normKcal?.toDouble() ?: baselineKcal(sex, weight, heightCm, ageIn(d.year, birthYear))
        val s = stepsBy[d] ?: 0
        val walk = stepsKcal(s, heightCm, weight)
        val km = runKmBy[d] ?: 0.0
        val run = KCAL_PER_KG_PER_KM * weight * km
        logged++
        eaten += ate
        steps += s
        stepsKcal += walk
        runKm += km
        runKcal += run
        balance += baseline + walk + run - ate
        d = d.plusDays(1)
    }
    val weighed = anthropometry.filter { it.weightKg != null && !it.date.isBefore(from) && !it.date.isAfter(last) }.sortedBy { it.date }
    val measured = if (weighed.size >= 2) weighed.last().weightKg!! - weighed.first().weightKg!! else null
    return WeightForecast(days, logged, eaten, normDays, runKm, steps, stepsKcal, runKcal, balance, -balance / KCAL_PER_KG_BODY, measured)
}

/**
 * The plan's runs that were run (28.10): a past day's entry with a distance,
 * its task ticked — or with no task to tick.
 */
fun runningPlanRunsDone(
    entries: List<com.alphaomegos.annasagenda.model.RunningPlanEntry>,
    doneTaskIds: Set<Long>,
    today: LocalDate,
): List<Pair<LocalDate, Double>> = entries.mapNotNull { e ->
    val km = parseRunningKm(e.distanceKmText) ?: return@mapNotNull null
    if (e.date.isAfter(today) || km <= 0.0) return@mapNotNull null
    val done = e.taskId?.let { it in doneTaskIds } ?: !e.date.isAfter(today)
    if (done) e.date to km else null
}
