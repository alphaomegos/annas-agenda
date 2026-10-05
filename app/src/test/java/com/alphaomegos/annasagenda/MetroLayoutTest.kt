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

    /* ---------------- grid and transfers kept together (06.10) ---------------- */

    private fun close(a: MetroPoint, b: MetroPoint) =
        assertTrue("$a is not $b", hypot(a.x - b.x, a.y - b.y) < 1e-3f)

    @Test
    fun aPointSnapsToTheNearestNodeOfTheGrid() {
        assertEquals(MetroPoint(0.5f, 0f), metroSnapped(MetroPoint(0.26f, -0.24f)))
        assertEquals(MetroPoint(1.5f, -1.5f), metroSnapped(MetroPoint(1.74f, -1.26f)))
        assertEquals(MetroPoint(2f, 3f), metroSnapped(MetroPoint(2.4f, 2.6f), step = 1f))
    }

    @Test
    fun transferGroups_joinAThreeLineInterchangeIntoOne() {
        val three = scheme.copy(transfers = scheme.transfers + MetroTransfer(3, 12, 22))
        assertEquals(listOf(listOf(3L, 12L, 22L), listOf(14L, 21L)), metroTransferGroups(three))
        assertEquals(setOf(3L, 12L, 22L), metroStationsMovingWith(three.copy(transfersTogether = true), 12))
        assertEquals(setOf(12L), metroStationsMovingWith(three, 12))
        assertEquals(setOf(1L), metroStationsMovingWith(three.copy(transfersTogether = true), 1))
    }

    @Test
    fun together_aTransfersStationsStandOneNodeApart_andNothingElseCrowds() {
        val tog = scheme.copy(transfersTogether = true)
        val l = metroLayout(tog)
        assertEquals(l, metroLayout(tog))
        assertEquals(METRO_TOGETHER_GAP, d(l, 3, 12), 1e-3f)
        assertEquals(METRO_TOGETHER_GAP, d(l, 14, 21), 1e-3f)
        listOf(1L to 2L, 11L to 12L, 21L to 22L).forEach { (a, b) ->
            assertTrue("$a–$b is ${d(l, a, b)}", d(l, a, b) in 0.5f..1.7f)
        }
        val ids = l.keys.toList()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            assertTrue("${ids[i]} and ${ids[j]} overlap", d(l, ids[i], ids[j]) > 0.15f)
        }
    }

    @Test
    fun together_draggingOneStation_bringsItsTransferAlong_andNothingElse() {
        val tog = scheme.copy(transfersTogether = true)
        val before = metroLayout(tog)
        val now = metroLayout(metroStationPlaced(tog, 3, 9f, 9f))
        val b3 = before.getValue(3); val b12 = before.getValue(12)
        assertEquals(MetroPoint(9f, 9f), now.getValue(3))
        close(MetroPoint(b12.x + (9f - b3.x), b12.y + (9f - b3.y)), now.getValue(12))
        listOf(1L, 2L, 11L, 14L, 21L).forEach { assertEquals(before.getValue(it), now.getValue(it)) }
        // Without the tick, only the one dragged moves, as before.
        assertEquals(metroLayout(scheme).getValue(12), metroLayout(metroStationPlaced(scheme, 3, 9f, 9f)).getValue(12))
    }

    @Test
    fun turningTogetherOn_forAPictureByHand_bringsEachTransferBesideItsFirstStation() {
        val placed = metroSchemeWithLayoutFrozen(scheme)
        val on = metroTransfersTogether(placed, true)
        val at = metroLayout(on)
        val was = placed.stations.associateBy { it.id }
        assertTrue(on.transfersTogether)
        assertEquals(MetroPoint(was.getValue(3).mapX!!, was.getValue(3).mapY!!), at.getValue(3))
        close(MetroPoint(at.getValue(3).x + METRO_TOGETHER_GAP, at.getValue(3).y), at.getValue(12))
        close(MetroPoint(at.getValue(14).x + METRO_TOGETHER_GAP, at.getValue(14).y), at.getValue(21))
        assertEquals(MetroPoint(was.getValue(1).mapX!!, was.getValue(1).mapY!!), at.getValue(1))
        // Letting go leaves every station where it stands.
        val off = metroTransfersTogether(on, false)
        assertEquals(false, off.transfersTogether)
        assertEquals(on.stations, off.stations)
        assertSame(on, metroTransfersTogether(on, true))
        // Nothing placed by hand: only the tick changes, the layout does the rest.
        assertEquals(scheme.stations, metroTransfersTogether(scheme, true).stations)
    }

    @Test
    fun together_aStationJoinedToAPlacedTransfer_standsBesideTheOnePlaced() {
        val tog = scheme.copy(
            transfersTogether = true,
            stations = scheme.stations.map { if (it.id == 3L) it.copy(mapX = 5f, mapY = 5f) else it },
        )
        val l = metroLayout(tog)
        assertEquals(MetroPoint(5f, 5f), l.getValue(3))
        assertEquals(MetroPoint(5f + METRO_TOGETHER_GAP, 5f), l.getValue(12))
    }
}
