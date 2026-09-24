package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The calorie arithmetic the calorimeter and the anthropometry screen both
 * show. They used to compute it separately, with different defaults, which
 * meant the same 30-day figure could be two different numbers depending on
 * which screen the user was looking at.
 */
class CalorimeterSupportTest {

    private val day = LocalDate.of(2026, 3, 10)

    @Test
    fun goal_fallsBackToTheDefaultBeforeAnyGoalWasSet() {
        assertEquals(DEFAULT_DAILY_GOAL_KCAL, calorieGoalOn(day, emptyList()))

        assertEquals(
            "a goal set later says nothing about an earlier day",
            DEFAULT_DAILY_GOAL_KCAL,
            calorieGoalOn(day, listOf(CalorieGoalChange(day.plusDays(1), 1800))),
        )
    }

    @Test
    fun goal_isTheLastOneSetOnOrBeforeThatDay() {
        val changes = listOf(
            CalorieGoalChange(day.minusDays(30), 2200),
            CalorieGoalChange(day.minusDays(5), 1800),
            CalorieGoalChange(day.plusDays(5), 1600),
        )

        assertEquals(1800, calorieGoalOn(day, changes))
        assertEquals(2200, calorieGoalOn(day.minusDays(6), changes))
        assertEquals(1600, calorieGoalOn(day.plusDays(10), changes))
    }

    @Test
    fun goal_takesEffectOnTheDayItWasSet() {
        val changes = listOf(CalorieGoalChange(day, 1500))

        assertEquals(1500, calorieGoalOn(day, changes))
        assertEquals(DEFAULT_DAILY_GOAL_KCAL, calorieGoalOn(day.minusDays(1), changes))
    }

    @Test
    fun deficit_isGoalMinusEatenAcrossAnInclusiveRange() {
        val changes = listOf(CalorieGoalChange(day.minusDays(10), 2000))
        val food = listOf(
            food(day, 1500),
            food(day.plusDays(1), 2500),
            food(day.plusDays(1), 100),
        )

        // Day one: 2000 - 1500 = 500. Day two: 2000 - 2600 = -600.
        assertEquals(-100, calorieDeficitInRange(changes, food, day, day.plusDays(1)))
    }

    @Test
    fun deficit_countsADayWithNothingLoggedAsAWholeDayUnder() {
        // The app cannot tell "ate nothing" from "wrote nothing down", and it
        // has always read an empty day the first way.
        assertEquals(
            DEFAULT_DAILY_GOAL_KCAL * 3,
            calorieDeficitInRange(emptyList(), emptyList(), day, day.plusDays(2)),
        )
    }

    @Test
    fun deficit_followsTheGoalThatWasInForceOnEachDay() {
        val changes = listOf(
            CalorieGoalChange(day, 2000),
            CalorieGoalChange(day.plusDays(1), 1000),
        )

        // 2000 on the first day, 1000 on the second, nothing eaten.
        assertEquals(3000, calorieDeficitInRange(changes, emptyList(), day, day.plusDays(1)))
    }

    @Test
    fun deficit_ignoresFoodOutsideTheRange() {
        val changes = listOf(CalorieGoalChange(day, 2000))
        val food = listOf(
            food(day.minusDays(1), 9000),
            food(day.plusDays(1), 9000),
            food(day, 500),
        )

        assertEquals(1500, calorieDeficitInRange(changes, food, day, day))
    }

    @Test
    fun deficit_isZeroForARangeThatEndsBeforeItStarts() {
        assertEquals(0, calorieDeficitInRange(emptyList(), emptyList(), day, day.minusDays(1)))
    }

    private fun food(date: LocalDate, kcal: Int) =
        FoodEntry(id = date.toEpochDay() * 100 + kcal, date = date, title = "meal", kcal = kcal)
}
