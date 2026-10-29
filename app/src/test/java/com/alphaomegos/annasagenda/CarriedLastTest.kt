package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A period with nothing measured shows the last measurement before it, level (0345). */
class CarriedLastTest {
    private val d = LocalDate.of(2026, 10, 12)
    private val all = listOf(AnthropometryEntry(date = d.minusDays(20), weightKg = 77.0), AnthropometryEntry(date = d, weightKg = 76.5, waistCm = 80.0))
    private val week = DateWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))

    @Test
    fun theLastOneBeforeLevelAcrossThePeriod() {
        val c = anthropometryCarriedLast(all, week)!!
        assertEquals(d, c.lastDate)
        assertEquals(listOf(week.from, week.to), c.entries.map { it.date })
        assertEquals(listOf(76.5, 76.5), c.entries.map { it.weightKg })
        assertEquals(80.0, c.entries.first().waistCm!!, 0.0)
    }

    @Test
    fun notWhenThePeriodHasOneOrNothingCameBefore() {
        assertNull(anthropometryCarriedLast(all, DateWindow(d, d.plusDays(6))))
        assertNull(anthropometryCarriedLast(all, DateWindow(d.minusDays(40), d.minusDays(30))))
    }
}
