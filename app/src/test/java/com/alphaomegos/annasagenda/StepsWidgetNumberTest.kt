package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import com.alphaomegos.annasagenda.support.stepRecordsIn
import com.alphaomegos.annasagenda.support.stepsNumberStart
import com.alphaomegos.annasagenda.support.stepsNumberWidthDp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The steps widget's number along with the bar, and the record of the period shown (0382). */
class StepsWidgetNumberTest {

    @Test
    fun theNumberRidesTheBarsTipUntilTheRightEdge() {
        val room = 200f
        val width = 60f
        assertEquals(0f, stepsNumberStart(room, width, 0f), 0f)
        assertEquals(0f, stepsNumberStart(room, width, 0.2f), 0f)     // the bar still shorter than the number
        assertEquals(40f, stepsNumberStart(room, width, 0.5f), 1e-4f)  // its right edge at the tip, 100
        assertEquals(140f, stepsNumberStart(room, width, 1f), 1e-4f)   // at the right edge
        assertEquals(140f, stepsNumberStart(room, width, 1.4f), 1e-4f) // past the goal: still there
        assertEquals(0f, stepsNumberStart(40f, width, 0.9f), 0f)       // no room at all
    }

    @Test
    fun aNumbersWidthIsAboutItsDigits() {
        assertEquals(6 * 0.6f * 30f, stepsNumberWidthDp("123456", 30f), 1e-3f)
        assertEquals((5 * 0.6f + 0.3f) * 30f, stepsNumberWidthDp("12 345", 30f), 1e-3f)
    }

    @Test
    fun theRecordIsThePeriodsOwn() {
        fun d(y: Int, m: Int, day: Int, steps: Int) = StepDay(LocalDate.of(y, m, day), steps, StepSource.HEALTH_CONNECT)
        val days = listOf(d(2024, 10, 5, 31_000), d(2026, 10, 28, 12_000), d(2026, 11, 2, 15_500), d(2026, 11, 3, 9_000))
        val week = stepRecordsIn(days, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))
        assertEquals(listOf(15_500, 9_000), week.map { it.steps })
        val month = stepRecordsIn(days, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(listOf(12_000), month.map { it.steps })
        val all = stepRecordsIn(days, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 11, 3))
        assertEquals(31_000, all.first().steps)
    }
}
