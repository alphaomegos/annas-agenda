package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Track run together (15.10). */
class MetroBundlesTest {

    // Eduard's case: D4 Kurskaya (1) – Three Stations (2), the ring's Kurskaya (11) – Komsomolskaya (12);
    // transfers Kurskaya–Kurskaya and Three Stations–Komsomolskaya. And a third line's track
    // (21–22) from Kurskaya elsewhere, which runs with nothing.
    private val scheme = MetroScheme(
        id = 1, city = "Москва",
        lines = listOf(
            MetroLine(1, "D4", "D4", 0, trunk = listOf(1, 2)),
            MetroLine(5, "5", "Кольцевая", 0, trunk = listOf(11, 12)),
            MetroLine(3, "3", "Арбатско-Покровская", 0, trunk = listOf(21, 22)),
        ),
        stations = listOf(
            MetroStation(1, 1, "Курская", 0f, 0f), MetroStation(2, 1, "Площадь трёх вокзалов", 4f, 0f),
            MetroStation(11, 5, "Курская", 0f, 0.4f), MetroStation(12, 5, "Комсомольская", 4f, 0.4f),
            MetroStation(21, 3, "Курская", -0.4f, 0f), MetroStation(22, 3, "Бауманская", -0.4f, 3f),
        ),
        transfers = listOf(MetroTransfer(1, 1, 11), MetroTransfer(2, 2, 12), MetroTransfer(3, 1, 21)),
    )

    @Test
    fun twoLinesBetweenTheSameInterchanges_runTogether_theFirstLeading() {
        val bundles = metroTrackBundles(scheme)
        assertEquals(mapOf(MetroTrack(11, 12) to MetroBundled(MetroTrack(1, 2), sameWay = true)), bundles)
    }

    @Test
    fun aFollower_theOtherWayRound_isSaidSo() {
        // The ring drawn the other way: Komsomolskaya first.
        val s = scheme.copy(
            stations = scheme.stations.map { if (it.id == 11L) it.copy(id = 13) else it },
            lines = scheme.lines.map { if (it.id == 5L) it.copy(trunk = listOf(12, 13)) else it },
            transfers = listOf(MetroTransfer(1, 1, 13), MetroTransfer(2, 2, 12)),
        )
        assertEquals(MetroBundled(MetroTrack(1, 2), sameWay = false), metroTrackBundles(s)[MetroTrack(12, 13)])
    }

    @Test
    fun theSideOffset_isLeftOfTheWay_onTheScreen() {
        // Going right along y = 0 with y down, left is up (negative y).
        assertEquals(0.4f, metroSideOffset(MetroPoint(0f, 0f), MetroPoint(4f, 0f), MetroPoint(2f, -0.4f)), 1e-5f)
        assertEquals(-0.4f, metroSideOffset(MetroPoint(0f, 0f), MetroPoint(4f, 0f), MetroPoint(2f, 0.4f)), 1e-5f)
    }

    @Test
    fun anOffsetPolyline_keepsItsSidesApart_roundACorner() {
        val bent = listOf(MetroPoint(0f, 0f), MetroPoint(2f, 0f), MetroPoint(2f, 2f))
        val out = metroOffsetPolyline(bent, -0.5f)
        // Half a step to the right of "right, then down": below the first leg, left of the second.
        assertEquals(MetroPoint(0f, 0.5f), out[0])
        assertEquals(1.5f, out[1].x, 1e-4f); assertEquals(0.5f, out[1].y, 1e-4f)
        assertEquals(MetroPoint(1.5f, 2f), out[2])
        assertTrue(metroOffsetPolyline(bent, 0f) === bent)
    }
}
