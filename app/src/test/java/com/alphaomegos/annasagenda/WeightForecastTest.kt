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
    fun eachDaysBalance() {
        val f = forecast()!!
        // Day 1: rest 600+1050−180−161 = 1309 × 1.2 = 1570.8, walking 0.5·60·6.9552 = 208.656, eaten 1500.
        // Day 2: weighed 59.5 → rest 1304 × 1.2 = 1564.8; nothing eaten written → at the norm; running 1.036·59.5·5 = 308.21.
        val walk = 0.5 * 60.0 * (10_000 * 168 * 0.414 / 100.0 / 1000.0)
        val run = 1.036 * 59.5 * 5.0
        val balance = (1570.8 + walk - 1500) + run
        assertEquals(balance, f.balanceKcal, 1e-6)
        assertEquals(-balance / 7700.0, f.changeKg, 1e-9)
        assertEquals(2, f.days)
        assertEquals(1, f.normDays)
        assertEquals(1500L, f.eatenKcal)
        assertEquals(-0.5, f.measuredKg!!, 1e-9)
        assertEquals(-0.5 - f.changeKg, f.discrepancyKg!!, 1e-9)
    }

    @Test
    fun withoutTheProfileThereIsNoForecast() {
        assertNull(forecast(sex = null))
    }
}
