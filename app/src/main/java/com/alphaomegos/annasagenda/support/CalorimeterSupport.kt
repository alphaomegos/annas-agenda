package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * What the daily goal is before the user has ever set one.
 *
 * It was written twice: as this named constant in CalorimeterScreen and as a
 * bare `2000` in AnthropometryScreen. The two screens show the same 30-day
 * deficit, so changing the default in one place would have made them disagree
 * about a number the user reads as a single fact about themselves.
 */
const val DEFAULT_DAILY_GOAL_KCAL = 2000

/**
 * Kilocalories in a kilogram of body fat, for the "this is worth about N kg"
 * line. Written as a named constant in one screen and as a bare 7800.0 in the
 * other, showing the same estimate.
 */
const val KCAL_PER_KG_FAT = 7800.0

/**
 * The goal in force on [date]: the most recent change made on or before it.
 *
 * Goal changes are a history rather than a setting, so a day is always counted
 * against the goal that was in force that day, not against today's.
 */
fun calorieGoalOn(date: LocalDate, changes: List<CalorieGoalChange>): Int =
    changes
        .filter { !it.date.isAfter(date) }
        .maxByOrNull { it.date }
        ?.kcal
        ?: DEFAULT_DAILY_GOAL_KCAL

/**
 * Goal minus what was eaten, summed over [start]..[end] inclusive.
 *
 * Positive is a deficit and negative is a surplus. A day with nothing logged
 * counts as a full day's deficit — the app cannot tell "ate nothing" from
 * "wrote nothing down", and the screens have always treated it this way.
 */
fun calorieDeficitInRange(
    changes: List<CalorieGoalChange>,
    foodLog: List<FoodEntry>,
    start: LocalDate,
    end: LocalDate,
): Int {
    if (end.isBefore(start)) return 0

    // One pass over the log instead of one per day: the log only ever grows,
    // and this runs on every recomposition of two screens.
    val eatenByDate = foodLog
        .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        .groupBy { it.date }
        .mapValues { (_, entries) -> entries.sumOf { it.kcal } }

    var day = start
    var total = 0

    while (!day.isAfter(end)) {
        total += calorieGoalOn(day, changes) - (eatenByDate[day] ?: 0)
        day = day.plusDays(1)
    }

    return total
}
