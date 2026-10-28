package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
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
}
