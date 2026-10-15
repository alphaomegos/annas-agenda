package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Many stations moved at once (08.10). */
class MetroGroupMoveTest {

    private val scheme = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12)),
        ),
        stations = listOf(
            MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f), MetroStation(3, 1, "C", 2f, 1f),
            MetroStation(11, 2, "D", 1f, 1f), MetroStation(12, 2, "E", 1f, 2f),
        ),
        bends = listOf(
            MetroBend(2, 3, listOf(MetroMapPoint(2f, 0f))),
            MetroBend(11, 12, listOf(MetroMapPoint(0.5f, 1.5f))),
        ),
    )

    @Test
    fun aTapPutsInOrTakesOut() {
        assertEquals(setOf(1L, 2L), metroSelectionToggled(setOf(1L), 2L))
        assertEquals(setOf(1L), metroSelectionToggled(setOf(1L, 2L), 2L))
    }

    @Test
    fun wholeLines_growToEveryStationOfThem() {
        assertEquals(setOf(1L, 2L, 3L), metroSelectionWholeLines(scheme, setOf(2L)))
        assertEquals(setOf(1L, 2L, 3L, 11L, 12L), metroSelectionWholeLines(scheme, setOf(3L, 12L)))
    }

    @Test
    fun theSelectionMovesWithTrackBentBetweenIt_notTowardsTheRest() {
        val s = metroSelectionPlaced(scheme, setOf(2L, 3L), 2L, 1f, 3f)
        val at = s.stations.associate { it.id to MetroPoint(it.mapX!!, it.mapY!!) }
        assertEquals(MetroPoint(1f, 3f), at[2L])
        assertEquals(MetroPoint(2f, 4f), at[3L])
        assertEquals(MetroPoint(0f, 0f), at[1L])
        assertEquals(listOf(MetroMapPoint(2f, 3f)), s.bends.first { it.aStationId == 2L }.points)
        assertEquals(listOf(MetroMapPoint(0.5f, 1.5f)), s.bends.first { it.aStationId == 11L }.points)
    }

    private fun MetroScheme.at(id: Long) = stations.first { it.id == id }.let { MetroPoint(it.mapX!!, it.mapY!!) }

    @Test
    fun aRow_takesTheMiddleY_onTheGridIfAsked() {
        // A (0,0), B (1,0), C (2,1): the middle y is 1/3.
        val row = metroSelectionLinedUp(scheme, setOf(1L, 2L, 3L), across = true)
        listOf(1L, 2L, 3L).forEach { assertEquals(1f / 3, row.at(it).y, 1e-5f) }
        assertEquals(2f, row.at(3L).x, 0f)
        val onGrid = metroSelectionLinedUp(scheme, setOf(1L, 2L, 3L), across = true, grid = 0.5f)
        assertEquals(0.5f, onGrid.at(1L).y, 0f)
        val column = metroSelectionLinedUp(scheme, setOf(11L, 12L, 3L), across = false)
        assertEquals(4f / 3, column.at(3L).x, 1e-5f)
        // One station is no row.
        assertEquals(scheme, metroSelectionLinedUp(scheme, setOf(1L), across = true))
    }

    @Test
    fun evenSpacing_keepsTheEnds_andPutsTheRestBetween() {
        val s = scheme.copy(stations = scheme.stations.map { if (it.id == 2L) it.copy(mapX = 0.3f, mapY = 0.4f) else it })
        val spaced = metroSelectionSpaced(s, setOf(1L, 2L, 3L))
        assertEquals(MetroPoint(0f, 0f), spaced.at(1L))
        assertEquals(MetroPoint(2f, 1f), spaced.at(3L))
        assertEquals(1f, spaced.at(2L).x, 1e-5f); assertEquals(0.5f, spaced.at(2L).y, 1e-5f)
    }

    @Test
    fun turningAndMirroring_aboutTheMiddle_withTheBendBetween() {
        // B (1,0) and C (2,1), the bend between at (2,0); the middle is (1.5, 0.5).
        val mirrored = metroSelectionTurned(scheme, setOf(2L, 3L), MetroTurn.MIRROR_ACROSS)
        assertEquals(MetroPoint(2f, 0f), mirrored.at(2L))
        assertEquals(MetroPoint(1f, 1f), mirrored.at(3L))
        assertEquals(listOf(MetroMapPoint(1f, 0f)), mirrored.bends.first { it.aStationId == 2L }.points)
        val flipped = metroSelectionTurned(scheme, setOf(2L, 3L), MetroTurn.MIRROR_UPDOWN)
        assertEquals(MetroPoint(1f, 1f), flipped.at(2L))
        // Eight turns of 45° bring everything back.
        var t = scheme
        repeat(8) { t = metroSelectionTurned(t, setOf(2L, 3L), MetroTurn.CLOCKWISE_45) }
        assertEquals(1f, t.at(2L).x, 1e-4f); assertEquals(0f, t.at(2L).y, 1e-4f)
        // Two turns: a quarter clockwise on the screen — B, up-left of the middle, goes up-right of it.
        var q = scheme
        repeat(2) { q = metroSelectionTurned(q, setOf(2L, 3L), MetroTurn.CLOCKWISE_45) }
        assertEquals(2f, q.at(2L).x, 1e-4f); assertEquals(0f, q.at(2L).y, 1e-4f)
    }

    @Test
    fun aBoxDrawnRoundStations_takesThoseInside_drawnEitherWay() {
        val layout = metroLayout(scheme)
        assertEquals(setOf(2L, 11L), metroStationsInside(layout, MetroPoint(0.5f, -0.5f), MetroPoint(1.5f, 1.2f)))
        assertEquals(setOf(2L, 11L), metroStationsInside(layout, MetroPoint(1.5f, 1.2f), MetroPoint(0.5f, -0.5f)))
        assertEquals(emptySet<Long>(), metroStationsInside(layout, MetroPoint(5f, 5f), MetroPoint(6f, 6f)))
    }

    private fun hexScheme() = MetroScheme(
        id = 9, city = "Тест",
        lines = (1L..6L).map { MetroLine(it, "$it", "L$it", 0, trunk = listOf(it * 10)) },
        stations = (1L..6L).map { MetroStation(it * 10, it, "Узел", it * 0.1f, (it % 3) * 0.1f) },
    )

    private fun dist(a: MetroPoint, b: MetroPoint) = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    @Test
    fun sixRings_makeARegularHexagon_neighboursTouching() {
        val s = metroSelectionFigured(hexScheme(), (1L..6L).map { it * 10 }.toSet(), MetroFigure.POLYGON, gap = 0.3f)
        val pts = (1L..6L).map { s.at(it * 10) }
        val cx = pts.map { it.x }.average().toFloat(); val cy = pts.map { it.y }.average().toFloat()
        val middle = MetroPoint(cx, cy)
        // Every ring as far from the middle — for a hexagon, the side itself.
        pts.forEach { assertEquals(0.3f, dist(it, middle), 1e-4f) }
        // Round the middle, each touches the next.
        val round = pts.sortedBy { kotlin.math.atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        round.indices.forEach { assertEquals(0.3f, dist(round[it], round[(it + 1) % 6]), 1e-4f) }
        // A flat side on top: the two highest at one height.
        val top = pts.sortedBy { it.y }.take(2)
        assertEquals(top[0].y, top[1].y, 1e-4f)
    }

    @Test
    fun fiveRings_makeARegularPentagon_andFiveRoundOne_aCrossOfFour() {
        val ids = (1L..5L).map { it * 10 }.toSet()
        val pent = metroSelectionFigured(hexScheme(), ids, MetroFigure.POLYGON, gap = 0.3f)
        val pts = ids.map { pent.at(it) }
        val sides = pts.flatMap { a -> pts.map { b -> dist(a, b) } }.filter { it > 1e-3f }
        // A regular pentagon has two lengths only: its side and its diagonal.
        assertEquals(0.3f, sides.minOrNull()!!, 1e-4f)
        assertTrue(sides.all { kotlin.math.abs(it - 0.3f) < 1e-3f || kotlin.math.abs(it - 0.3f * 1.618034f) < 1e-3f })
        val around = metroSelectionFigured(hexScheme(), ids, MetroFigure.AROUND_ONE, gap = 0.3f)
        val all = ids.map { around.at(it) }
        val mid = MetroPoint(all.map { it.x }.average().toFloat(), all.map { it.y }.average().toFloat())
        // One in the middle, four touching it.
        assertEquals(1, all.count { dist(it, mid) < 1e-4f })
        assertEquals(4, all.count { kotlin.math.abs(dist(it, mid) - 0.3f) < 1e-4f })
    }

    @Test
    fun aRowOfThree_keepsItsOrder_touching() {
        val ids = setOf(10L, 20L, 30L)
        val s = metroSelectionFigured(hexScheme(), ids, MetroFigure.ROW, gap = 0.3f)
        assertEquals(s.at(10L).y, s.at(30L).y, 1e-5f)
        assertTrue(s.at(10L).x < s.at(20L).x && s.at(20L).x < s.at(30L).x)
        assertEquals(0.3f, s.at(20L).x - s.at(10L).x, 1e-4f)
    }
}
