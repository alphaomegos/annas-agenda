package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Angles of 45° while dragging a station (08.10). */
class MetroAnglesTest {

    private fun close(e: MetroPoint, a: MetroPoint) {
        assertEquals(e.x, a.x, 1e-4f); assertEquals(e.y, a.y, 1e-4f)
    }

    @Test
    fun ontoTheNearestDirectionFromOneNeighbour() {
        close(MetroPoint(3f, 0f), metroOctilinearSnap(MetroPoint(3f, 0.2f), listOf(MetroPoint(0f, 0f)), 0.5f))
        close(MetroPoint(2.1f, 2.1f), metroOctilinearSnap(MetroPoint(2f, 2.2f), listOf(MetroPoint(0f, 0f)), 0.5f))
    }

    @Test
    fun squareWithBothNeighbours_whenThereIsSuchAPointClose() {
        // Between (0,0) and (4,2): across from the first, at 45° from the second → (2, 0).
        close(MetroPoint(2f, 0f), metroOctilinearSnap(MetroPoint(2.1f, 0.2f), listOf(MetroPoint(0f, 0f), MetroPoint(4f, 2f)), 0.5f))
    }

    @Test
    fun farFromEveryDirection_itStaysWhereDropped() {
        val p = MetroPoint(3f, 1.3f)
        assertSame(p, metroOctilinearSnap(p, listOf(MetroPoint(0f, 0f)), 0.2f))
        assertSame(p, metroOctilinearSnap(p, emptyList(), 1f))
    }

    @Test
    fun anchors_areTheNeighbours_orTheBendNextToTheStation() {
        val s = MetroScheme(
            id = 1, city = "T",
            lines = listOf(MetroLine(1, "1", "", 0, trunk = listOf(1, 2, 3))),
            stations = listOf(MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 2f, 0f), MetroStation(3, 1, "C", 4f, 2f)),
            bends = listOf(MetroBend(2, 3, listOf(MetroMapPoint(3f, 0f), MetroMapPoint(3.5f, 1f)))),
        )
        val layout = metroLayout(s)
        assertEquals(listOf(MetroPoint(0f, 0f), MetroPoint(3f, 0f)), metroAngleAnchors(s, layout, 2))
        assertEquals(listOf(MetroPoint(3.5f, 1f)), metroAngleAnchors(s, layout, 3))
        // A neighbour moving along is no anchor.
        assertEquals(listOf(MetroPoint(3f, 0f)), metroAngleAnchors(s, layout, 2, moving = setOf(1L, 2L)))
    }
}
