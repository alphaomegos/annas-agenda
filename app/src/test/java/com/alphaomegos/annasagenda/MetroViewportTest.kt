package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Where the picture is looked at (14.10). */
class MetroViewportTest {

    @Test
    fun fitted_showsEveryPointWithAStepAround() {
        val v = metroFittedViewport(listOf(MetroPoint(0f, 0f), MetroPoint(4f, 2f)), 600f, 400f)
        // 6 × 4 units with the margin into 600 × 400: 100 px a unit, centred on (2, 1).
        assertEquals(100f, v.k, 1e-4f)
        assertEquals(2f, v.cx, 1e-4f); assertEquals(1f, v.cy, 1e-4f)
        assertEquals(100f, v.toScreenX(0f, 600f), 1e-3f)
    }

    @Test
    fun zooming_keepsThePointUnderTheFingers() {
        val v = MetroViewport(100f, 2f, 1f)
        val z = v.zoomed(2f, 450f, 100f, 600f, 400f, 10f, 1000f)
        assertEquals(200f, z.k, 1e-4f)
        assertEquals(v.toMapX(450f, 600f), z.toMapX(450f, 600f), 1e-4f)
        assertEquals(v.toMapY(100f, 400f), z.toMapY(100f, 400f), 1e-4f)
        // Within its bounds.
        assertEquals(1000f, v.zoomed(50f, 0f, 0f, 600f, 400f, 10f, 1000f).k, 1e-4f)
    }

    @Test
    fun panning_movesThePictureWithTheFinger() {
        val v = MetroViewport(100f, 2f, 1f)
        val p = v.panned(50f, -100f)
        assertEquals(v.toScreenX(3f, 600f) + 50f, p.toScreenX(3f, 600f), 1e-3f)
        assertEquals(v.toScreenY(3f, 400f) - 100f, p.toScreenY(3f, 400f), 1e-3f)
    }

    @Test
    fun aPicturePlacedByHand_isReadStraightOff() {
        val s = MetroScheme(
            id = 1, city = "Тест",
            lines = listOf(MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2))),
            stations = listOf(MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 3f, 1f)),
            transfersTogether = true,
        )
        assertTrue(metroIsPlaced(s))
        assertEquals(mapOf(1L to MetroPoint(0f, 0f), 2L to MetroPoint(3f, 1f)), metroLayout(s))
        assertFalse(metroIsPlaced(s.copy(stations = s.stations + MetroStation(3, 1, "C"))))
    }
}
