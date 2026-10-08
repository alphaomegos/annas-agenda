package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
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
}
