package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Stations drawn as ticks (08.10). */
class MetroStationMarksTest {

    // A line along x (1–2–3) and one down from 2's partner 11.
    private val scheme = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12, 13)),
        ),
        stations = listOf(
            MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f), MetroStation(3, 1, "C", 2f, 0f),
            MetroStation(11, 2, "D", 1f, 0.5f), MetroStation(12, 2, "E", 1f, 2f), MetroStation(13, 2, "F", 2f, 3f),
        ),
        transfers = listOf(MetroTransfer(1, 2, 11)),
    )
    private val layout get() = metroLayout(scheme)

    private fun same(e: Double, a: Double?) {
        assertNotNull(a)
        val d = abs(e - a!!) % (2 * PI)
        assertEquals(0.0, minOf(d, 2 * PI - d), 1e-6)
    }

    @Test
    fun interchangesAndTermini() {
        assertEquals(setOf(2L, 11L), metroInterchangeStations(scheme))
        assertEquals(setOf(1L, 3L, 11L, 13L), metroTermini(scheme))
    }

    @Test
    fun aTickStandsSquareToItsLine_onItsNamesSide() {
        // A horizontal line, the name above: the tick points up.
        same(-PI / 2, metroTickAngle(scheme, layout, 3, labelSide = 6))
        // The name below: down.
        same(PI / 2, metroTickAngle(scheme, layout, 3, labelSide = 2))
    }

    @Test
    fun atABend_theTickBisectsIt() {
        // E: track up to D (−90°) and down-right to F (45°): square to the line through it is
        // −22.5° or 157.5°; the name on the right takes −22.5°.
        same(-PI / 8, metroTickAngle(scheme, layout, 12, labelSide = 0))
    }
}
