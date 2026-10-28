package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The new weight forecast (0328): each day's energy balance over the period on screen. */
class WeightForecastTest {
    private val d1 = LocalDate.of(2026, 10, 1)
    private val d2 = d1.plusDays(1)
    private val weights = listOf(AnthropometryEntry(date = d1, weightKg = 60.0), AnthropometryEntry(date = d2, weightKg = 59.5))

    private fun forecast(sex: ProfileSex? = ProfileSex.FEMALE) = newWeightForecast(
        from = d1, to = d2, today = d2.plusDays(10),
        sex = sex, heightCm = 168, birthYear = 1990,
        anthropometry = weights,
        foodLog = listOf(FoodEntry(id = 1, date = d1, title = "обед", kcal = 1500)),
        stepDays = listOf(StepDay(d1, 10_000, StepSource.HEALTH_CONNECT)),
        workouts = listOf(RunningWorkout(id = 1, date = d2, distanceKm = 5.0, durationMinutes = 30)),
    )

    @Test
    fun onlyDaysWithFoodWrittenCount() {
        val f = forecast()!!
        // Day 1: rest 600+1050−180−161 = 1309 × 1.2 = 1570.8, walking 0.5·60·6.9552, eaten 1500.
        // Day 2: nothing eaten written → no deficit, its run included (0328 counted it; 0330 does not).
        val walk = 0.5 * 60.0 * (10_000 * 168 * 0.414 / 100.0 / 1000.0)
        val balance = 1570.8 + walk - 1500
        assertEquals(balance, f.balanceKcal, 1e-6)
        assertEquals(-balance / 7700.0, f.changeKg, 1e-9)
        assertEquals(2, f.days)
        assertEquals(1, f.loggedDays)
        assertEquals(1, f.normDays)
        assertEquals(1500L, f.eatenKcal)
        assertEquals(0.0, f.runKm, 0.0)
        assertEquals(-0.5, f.measuredKg!!, 1e-9)
        assertEquals(-0.5 - f.changeKg, f.discrepancyKg!!, 1e-9)
    }

    @Test
    fun thePlansRunsCountAndTheOwnNormReplacesTheFormula() {
        val f = newWeightForecast(
            from = d1, to = d1, today = d2,
            sex = ProfileSex.FEMALE, heightCm = 168, birthYear = 1990,
            anthropometry = weights,
            foodLog = listOf(FoodEntry(id = 1, date = d1, title = "обед", kcal = 1500)),
            stepDays = emptyList(),
            workouts = emptyList(),
            planRuns = listOf(d1 to 10.0),
            normKcal = 2000,
        )!!
        assertEquals(10.0, f.runKm, 0.0)
        assertEquals(2000 + 1.036 * 60.0 * 10.0 - 1500, f.balanceKcal, 1e-6)
    }

    @Test
    fun aPlanRunIsDoneWhenItsTaskIsTicked() {
        val entries = listOf(
            RunningPlanEntry(date = d1, distanceKmText = "8", taskId = 7),
            RunningPlanEntry(date = d2, distanceKmText = "5", taskId = 8),
            RunningPlanEntry(date = d2.plusDays(5), distanceKmText = "12"),
        )
        assertEquals(listOf(d1 to 8.0), runningPlanRunsDone(entries, setOf(7L), d2))
    }

    @Test
    fun withoutTheProfileThereIsNoForecast() {
        assertNull(forecast(sex = null))
    }
}
