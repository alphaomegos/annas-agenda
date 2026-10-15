package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The editor's grid step (15.10): a whole step, a half — as before — or a quarter. */
class MetroGridStepTest {

    @Test
    fun theStepIsTheChosenPartOfAStep() {
        val scheme = MetroScheme(id = 1, city = "Тест")

        assertEquals(METRO_GRID_STEP, metroGridStep(scheme), 0f)
        assertEquals(1f, metroGridStep(scheme.copy(gridDivisions = 1)), 0f)
        assertEquals(0.25f, metroGridStep(scheme.copy(gridDivisions = 4)), 0f)
    }

    /** A number of nodes the editor does not offer is read as before: two to a step. */
    @Test
    fun anOddChoiceIsReadAsTwoToAStep() {
        assertEquals(0.5f, metroGridStep(MetroScheme(id = 1, city = "Тест", gridDivisions = 3)), 0f)
        assertEquals(0.5f, metroGridStep(MetroScheme(id = 1, city = "Тест", gridDivisions = 0)), 0f)
    }
}
