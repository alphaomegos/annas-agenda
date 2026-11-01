package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.model.FoodEntry
import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.model.RunningWorkout
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import java.time.LocalDate
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body's own energy use (0327): Mifflin–St Jeor, then «sedentary» × 1.2. */
class EnergyTest {
    @Test
    fun restAndBaseline() {
        // 60 kg, 168 cm, 36 years: 600 + 1050 − 180 − 161 = 1309 kcal at rest.
        assertEquals(1309.0, restingKcal(ProfileSex.FEMALE, 60.0, 168, 36), 1e-9)
        assertEquals(1309.0 + 166.0, restingKcal(ProfileSex.MALE, 60.0, 168, 36), 1e-9)
        assertEquals(1309.0 * 1.2, baselineKcal(ProfileSex.FEMALE, 60.0, 168, 36), 1e-9)
    }

    @Test
    fun age() {
        assertEquals(36, ageIn(2026, 1990))
        assertEquals(0, ageIn(2026, 2030))
    }

    private val day = LocalDate.of(2026, 10, 30)
    private val weights = listOf(AnthropometryEntry(date = day.minusDays(3), weightKg = 60.0))
    private val steps = listOf(StepDay(day, 10_000, StepSource.HEALTH_CONNECT))
    private val runs = listOf(RunningWorkout(id = 1, date = day, distanceKm = 5.0, durationMinutes = 30))

    /** The calorimeter's day spending (0369): nothing to say without the profile or an own norm. */
    @Test
    fun noSpendingWithoutTheProfile() {
        assertNull(dayEnergy(day, null, 168, 1990, weights, steps, runs))
        assertNull(dayEnergy(day, ProfileSex.FEMALE, 168, 1990, emptyList(), steps, runs))
    }

    @Test
    fun theNormThenStepsAndRuns() {
        val e = dayEnergy(day, ProfileSex.FEMALE, 168, 1990, weights, steps, runs)!!
        assertEquals(Math.round(baselineKcal(ProfileSex.FEMALE, 60.0, 168, 36)).toInt(), e.normKcal)
        assertEquals(Math.round(stepsKcal(10_000, 168, 60.0)).toInt(), e.stepsKcal)
        assertEquals(Math.round(KCAL_PER_KG_PER_KM * 60.0 * 5.0).toInt(), e.runKcal)
        assertEquals(e.normKcal + e.stepsKcal + e.runKcal, e.totalKcal)
        assertTrue(!e.ownNorm)
        // A plan's run done that day counts like a workout.
        val withPlan = dayEnergy(day, ProfileSex.FEMALE, 168, 1990, weights, steps, emptyList(), planRuns = listOf(day to 5.0))!!
        assertEquals(e.runKcal, withPlan.runKcal)
    }

    @Test
    fun anOwnNormStandsInForTheFormula() {
        val own = dayEnergy(day, null, null, null, emptyList(), steps, runs, ownNormKcal = 1900)!!
        assertEquals(1900, own.normKcal)
        assertTrue(own.ownNorm)
        // Without height and weight the walking and running cannot be counted.
        assertEquals(0, own.stepsKcal)
        assertEquals(0, own.runKcal)
    }

    /** What the calorimeter shows is what the weight forecast counts for the same day. */
    @Test
    fun theSameSpendingAsTheForecast() {
        val eaten = 1500
        val forecast = newWeightForecast(
            from = day, to = day, today = day,
            sex = ProfileSex.FEMALE, heightCm = 168, birthYear = 1990,
            anthropometry = weights, foodLog = listOf(FoodEntry(id = 1, date = day, title = "Обед", kcal = eaten)),
            stepDays = steps, workouts = runs,
        )!!
        val e = dayEnergy(day, ProfileSex.FEMALE, 168, 1990, weights, steps, runs)!!
        assertEquals(forecast.balanceKcal + eaten, e.totalKcal.toDouble(), 1.5)
    }
}
