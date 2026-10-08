package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Bent track, glued interchanges, one name per interchange (08.10). */
class MetroBendsTest {

    private val placed = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12)),
            MetroLine(3, "3", "Blue", 0, trunk = listOf(21, 22)),
        ),
        stations = listOf(
            MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "Таганская", 2f, 1f), MetroStation(3, 1, "C", 4f, 1f),
            MetroStation(11, 2, "Таганская", 2.4f, 1f), MetroStation(12, 2, "E", 2.4f, 3f),
            MetroStation(21, 3, "Марксистская", 2.2f, 1.4f), MetroStation(22, 3, "F", 0f, 3f),
        ),
        transfers = listOf(MetroTransfer(1, 2, 11), MetroTransfer(2, 11, 21)),
    )
    private val layout get() = metroLayout(placed)
    private val t12 = MetroTrack(1, 2)

    @Test
    fun aTapOnATrack_cyclesStraight_elbowAlongX_elbowAlongY_straight() {
        var s = metroElbowCycled(placed, layout, t12)
        assertEquals(listOf(MetroPoint(2f, 0f)), metroBendPoints(s, t12))
        s = metroElbowCycled(s, metroLayout(s), t12)
        assertEquals(listOf(MetroPoint(0f, 1f)), metroBendPoints(s, t12))
        s = metroElbowCycled(s, metroLayout(s), t12)
        assertTrue(metroBendPoints(s, t12).isEmpty())
        assertTrue(s.bends.isEmpty())
    }

    @Test
    fun bendPoints_areInsertedMovedAndRemoved_andReadEitherWayRound() {
        val hit = metroTrackAt(placed, layout, 1f, 0.5f, 0.3f)!!
        assertEquals(MetroBendHit(t12, 0), hit)
        var s = metroBendPointInserted(placed, hit, 1f, 0f)
        s = metroBendPointInserted(s, MetroBendHit(t12, 1), 2f, 0f)
        assertEquals(listOf(MetroPoint(1f, 0f), MetroPoint(2f, 0f)), metroBendPoints(s, t12))
        assertEquals(MetroBendHit(t12, 1), metroBendPointAt(s, 2.05f, 0.05f, 0.2f))
        s = metroBendPointMoved(s, MetroBendHit(t12, 1), 2f, 0.5f)
        assertEquals(MetroPoint(2f, 0.5f), metroBendPoints(s, t12)[1])
        s = metroBendPointRemoved(s, MetroBendHit(t12, 0))
        assertEquals(listOf(MetroPoint(2f, 0.5f)), metroBendPoints(s, t12))
        assertNull(metroBendPointAt(s, 3f, 3f, 0.2f))
        // Kept from b to a, read from a to b all the same.
        val reversed = s.copy(bends = listOf(MetroBend(2, 1, listOf(MetroMapPoint(5f, 5f), MetroMapPoint(6f, 6f)))))
        assertEquals(listOf(MetroPoint(6f, 6f), MetroPoint(5f, 5f)), metroBendPoints(reversed, t12))
    }

    @Test
    fun aBendGoes_whenItsStationsStopBeingNeighbours() {
        val bent = metroElbowCycled(placed, layout, t12)
        val removed = metroStationRemoved(bent, 2)
        assertTrue(removed.bends.isEmpty())
    }

    @Test
    fun theFirstBend_freezesThePicture() {
        val loose = placed.copy(stations = placed.stations.map { if (it.id == 3L) it.copy(mapX = null, mapY = null) else it })
        val bent = metroElbowCycled(loose, metroLayout(loose), t12)
        assertTrue(bent.stations.all { it.mapX != null && it.mapY != null })
    }

    @Test
    fun aGluedRing_touchesItsNeighbour_atOneOf8or12Places() {
        val anchor = MetroPoint(0f, 0f)
        val p8 = metroGluedPoint(MetroPoint(1f, 0.3f), anchor, 8)
        assertEquals(METRO_GLUE_GAP, hypot(p8.x, p8.y), 1e-4f)
        assertEquals(0f, p8.y, 1e-4f) // 17° rounds to 0° of the eight
        val p12 = metroGluedPoint(MetroPoint(1f, 0.6f), anchor, 12)
        assertEquals(30.0, Math.toDegrees(kotlin.math.atan2(p12.y, p12.x).toDouble()), 1e-3)
    }

    @Test
    fun aStationMovedAlone_gluesToItsInterchange_whenDroppedNearIt() {
        val glued = placed.copy(glue = 8)
        // Marksistskaya dropped just right of the line-2 Taganskaya: glued to it, touching.
        val s = metroStationPlacedAlone(glued, 21, 2.7f, 1.05f)
        val m = s.stations.first { it.id == 21L }
        assertEquals(2.4f + METRO_GLUE_GAP, m.mapX!!, 1e-4f)
        assertEquals(1f, m.mapY!!, 1e-4f)
        // Far from its interchange it stays where it was dropped.
        val far = metroStationPlacedAlone(glued, 21, 6f, 6f).stations.first { it.id == 21L }
        assertEquals(6f, far.mapX)
        // Glue off: where dropped.
        assertEquals(2.7f, metroStationPlacedAlone(placed, 21, 2.7f, 1.05f).stations.first { it.id == 21L }.mapX)
    }

    @Test
    fun oneNamePerInterchange() {
        assertEquals(placed.stations.map { it.id }.toSet(), metroLabelledStations(placed, layout))
        val once = placed.copy(labelsOncePerInterchange = true)
        // Two Taganskayas: the rightmost (11) keeps the name; Marksistskaya keeps its own.
        assertEquals(setOf(1L, 3L, 11L, 12L, 21L, 22L), metroLabelledStations(once, metroLayout(once)))
    }

    @Test
    fun glueMakesTheTogetherGapTouching() {
        assertEquals(METRO_TOGETHER_GAP, metroTogetherGap(placed))
        assertEquals(METRO_GLUE_GAP, metroTogetherGap(placed.copy(glue = 12)))
    }

    @Test
    fun biggerRings_standFurtherApartToTouch() {
        val big = placed.copy(glue = 8, stationScale = 2f)
        assertEquals(2 * METRO_GLUE_GAP, metroGlueGap(big), 1e-5f)
        val m = metroStationPlacedAlone(big, 21, 2.9f, 1.05f).stations.first { it.id == 21L }
        assertEquals(2.4f + 2 * METRO_GLUE_GAP, m.mapX!!, 1e-4f)
    }
}
