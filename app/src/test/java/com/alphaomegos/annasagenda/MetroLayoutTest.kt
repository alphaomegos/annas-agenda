package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The picture of the map laid out by itself, and then put right by hand (05.10). */
class MetroLayoutTest {

    private val scheme = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3, 4, 5), branches = listOf(MetroBranch(9, 5, listOf(6, 7)))),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12, 13, 14)),
            MetroLine(3, "5", "Ring", 0, ring = true, trunk = (21L..28L).toList()),
        ),
        stations = (1L..7L).map { MetroStation(it, 1, "S$it") } + (11L..14L).map { MetroStation(it, 2, "S$it") } +
            (21L..28L).map { MetroStation(it, 3, "S$it") },
        transfers = listOf(MetroTransfer(1, 3, 12), MetroTransfer(2, 14, 21)),
    )

    private fun d(l: Map<Long, MetroPoint>, a: Long, b: Long) = hypot(l.getValue(a).x - l.getValue(b).x, l.getValue(a).y - l.getValue(b).y)

    @Test
    fun everyStationGetsAPlace_andTheSameSchemeTheSamePicture() {
        val l = metroLayout(scheme)
        assertEquals(scheme.stations.map { it.id }.toSet(), l.keys)
        assertTrue(l.values.all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(l, metroLayout(scheme))
    }

    @Test
    fun neighboursOnALineAreAboutOneStepApart_transfersClose_noneOnTopOfAnother() {
        val l = metroLayout(scheme)
        listOf(1L to 2L, 4L to 5L, 5L to 6L, 11L to 12L, 21L to 22L, 28L to 21L).forEach { (a, b) ->
            assertTrue("$a–$b is ${d(l, a, b)}", d(l, a, b) in 0.5f..1.7f)
        }
        assertTrue("transfer 3–12 is ${d(l, 3, 12)}", d(l, 3, 12) < 0.9f)
        assertTrue("transfer 14–21 is ${d(l, 14, 21)}", d(l, 14, 21) < 0.9f)
        val ids = l.keys.toList()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            assertTrue("${ids[i]} and ${ids[j]} overlap", d(l, ids[i], ids[j]) > 0.15f)
        }
    }

    @Test
    fun aStationPlacedByHandKeepsItsPlace() {
        val placed = scheme.copy(stations = scheme.stations.map { if (it.id == 1L) it.copy(mapX = 40f, mapY = -7f) else it })
        assertEquals(MetroPoint(40f, -7f), metroLayout(placed).getValue(1))
    }

    @Test
    fun movingOneStation_movesOnlyThatOne() {
        val before = metroLayout(scheme)
        val after = metroStationPlaced(scheme, 4, 9f, 9f)
        assertTrue(after.stations.all { it.mapX != null && it.mapY != null })
        val now = metroLayout(after)
        assertEquals(MetroPoint(9f, 9f), now.getValue(4))
        scheme.stations.filter { it.id != 4L }.forEach { assertEquals(before.getValue(it.id), now.getValue(it.id)) }
        assertSame(scheme, metroStationPlaced(scheme, 404, 1f, 1f))
    }

    @Test
    fun aStationAddedLater_isLaidOutBesideItsNeighbours_theRestStayPut() {
        val frozen = metroSchemeWithLayoutFrozen(scheme)
        val grown = metroStationAdded(frozen, 2, null, Int.MAX_VALUE, "New")
        val newId = grown.stations.last().id
        val l = metroLayout(grown)
        frozen.stations.forEach { assertEquals(MetroPoint(it.mapX!!, it.mapY!!), l.getValue(it.id)) }
        assertTrue("new station is ${d(l, newId, 14)} from its neighbour", d(l, newId, 14) < 2f)
    }

    @Test
    fun clearing_forgetsEveryPlace() {
        val cleared = metroLayoutCleared(metroSchemeWithLayoutFrozen(scheme))
        assertTrue(cleared.stations.all { it.mapX == null && it.mapY == null })
        assertSame(scheme, metroLayoutCleared(scheme))
    }

    @Test
    fun aTapFindsTheNearestStationWithinReach() {
        val l = mapOf(1L to MetroPoint(0f, 0f), 2L to MetroPoint(1f, 0f))
        assertEquals(2L, metroStationAt(l, 0.8f, 0.1f, 0.5f))
        assertNull(metroStationAt(l, 0.5f, 3f, 0.5f))
        assertEquals(floatArrayOf(0f, 0f, 1f, 0f).toList(), metroLayoutBounds(l.values).toList())
    }
}
