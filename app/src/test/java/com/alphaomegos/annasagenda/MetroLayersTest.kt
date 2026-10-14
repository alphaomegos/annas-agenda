package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The editor's layers (14.10). */
class MetroLayersTest {

    private val scheme = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2)),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12)),
            MetroLine(3, "3", "Blue", 0, trunk = listOf(21, 22)),
        ),
        stations = listOf(
            MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f),
            MetroStation(11, 2, "C", 1f, 1f), MetroStation(12, 2, "D", 2f, 1f),
            MetroStation(21, 3, "E", 0f, 2f), MetroStation(22, 3, "F", 1f, 2f),
        ),
        transfers = listOf(MetroTransfer(1, 2, 11)),
        bends = listOf(MetroBend(11, 12, listOf(MetroMapPoint(2f, 0.5f)))),
    )

    @Test
    fun onlyOneLine_thenAgain_everyLine() {
        val only = metroLayerOnly(scheme, 1)
        assertEquals(setOf(2L, 3L), only.hiddenLines.toSet())
        assertEquals(emptyList<Long>(), metroLayerOnly(only, 1).hiddenLines)
        // Line 2 shown next to it, to hook it on.
        val two = metroLayerShownToggled(only, 2)
        assertEquals(listOf(3L), two.hiddenLines)
        assertEquals(setOf(21L, 22L), metroHiddenStations(two))
        assertEquals(emptyList<Long>(), metroLayersAllShown(two).hiddenLines)
    }

    @Test
    fun hiddenAndLockedStations_areLeftAlone_andTheirTrackCannotBeBent() {
        val s = metroLayerLockToggled(metroLayerShownToggled(scheme, 3), 2)
        assertEquals(setOf(11L, 12L, 21L, 22L), metroFixedStations(s))
        val editable = metroEditablePart(s)
        assertEquals(listOf(1L), editable.lines.map { it.id })
        assertTrue(editable.bends.isEmpty())
        assertEquals(scheme, metroEditablePart(scheme))
    }

    @Test
    fun undo_takesThePictureBack_notTheLayers() {
        val now = metroLayerOnly(scheme.copy(stations = scheme.stations.map { it.copy(mapX = (it.mapX ?: 0f) + 1f) }), 2)
        val back = metroLayersKept(scheme, now)
        assertEquals(scheme.stations, back.stations)
        assertEquals(now.hiddenLines, back.hiddenLines)
    }

    @Test
    fun aLineGone_leavesTheLayers() {
        val s = metroLayerLockToggled(metroLayerShownToggled(scheme, 3), 3)
        val gone = reconciled(s, s.copy(lines = s.lines.filter { it.id != 3L }))
        assertTrue(gone.hiddenLines.isEmpty())
        assertTrue(gone.lockedLines.isEmpty())
    }

    @Test
    fun aMove_leavesHiddenAndLockedStationsOfItsTransfer_andGluesOnlyInSight() {
        // B (1,0) and C (1,1) are one interchange.
        fun MetroScheme.at(id: Long) = stations.first { it.id == id }.let { MetroPoint(it.mapX!!, it.mapY!!) }
        val together = scheme.copy(transfersTogether = true)
        assertEquals(MetroPoint(5f, 6f), metroStationPlaced(together, 2, 5f, 5f).at(11L))
        assertEquals(MetroPoint(1f, 1f), metroStationPlaced(together, 2, 5f, 5f, keep = setOf(11L)).at(11L))
        // B dropped beside C: glued to it in sight, left where dropped when C is hidden.
        val glued = scheme.copy(glue = 8)
        assertTrue(metroStationPlacedAlone(glued, 2, 1.2f, 1.1f).at(2L) != MetroPoint(1.2f, 1.1f))
        assertEquals(MetroPoint(1.2f, 1.1f), metroStationPlacedAlone(glued, 2, 1.2f, 1.1f, unseen = setOf(11L, 12L)).at(2L))
    }
}
