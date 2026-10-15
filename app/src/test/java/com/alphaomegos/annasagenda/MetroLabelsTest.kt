package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Where station names are written (08.10). */
class MetroLabelsTest {

    // A line along x (1–2–3), one going down from 2's neighbour 11, linked to 2.
    private val scheme = MetroScheme(
        id = 1, city = "Тест",
        lines = listOf(
            MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
            MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12)),
        ),
        stations = listOf(
            MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f), MetroStation(3, 1, "C", 2f, 0f),
            MetroStation(11, 2, "D", 1f, 0.5f), MetroStation(12, 2, "E", 1f, 2f),
        ),
        transfers = listOf(MetroTransfer(1, 2, 11)),
    )
    private val layout get() = metroLayout(scheme)

    @Test
    fun aTapGoesRoundTheEightSides_thenHidden_thenByItself() {
        var side: Int? = null
        val seen = ArrayList<Int?>()
        repeat(10) { side = metroLabelSideCycled(side); seen += side }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, METRO_LABEL_HIDDEN, null), seen)
    }

    @Test
    fun byItself_aNameKeepsOffItsOwnLine() {
        // The end of a horizontal line: its track goes left, so the name goes right.
        assertEquals(0, metroAutoLabelSide(scheme, layout, 3))
        // In the middle of it, track both ways and a link down: above.
        assertEquals(6, metroAutoLabelSide(scheme, layout, 2))
        // The other end of it goes on, out past the end: left.
        assertEquals(4, metroAutoLabelSide(scheme, layout, 1))
        // In the middle of a vertical line: right, as before.
        assertEquals(0, metroAutoLabelSide(scheme, layout, 11))
        // At its end: under it, out past the end.
        assertEquals(2, metroAutoLabelSide(scheme, layout, 12))
    }

    @Test
    fun aSideChosenByHandWins_andATapStoresIt() {
        val s = metroStationLabelCycled(metroStationLabelCycled(scheme, 3), 3)
        assertEquals(1, s.stations.first { it.id == 3L }.labelSide)
        assertEquals(1, metroLabelSide(s, layout, 3))
        assertNull(s.stations.first { it.id == 1L }.labelSide)
    }

    @Test
    fun theNameSitsBesideOverOrUnderTheRing() {
        fun close(e: MetroPoint, a: MetroPoint) { assertEquals(e.x, a.x, 1e-3f); assertEquals(e.y, a.y, 1e-3f) }
        close(MetroPoint(15f, 5f), metroLabelTopLeft(10f, 10f, 5f, 0, 20f, 10f))   // right, centred
        close(MetroPoint(-15f, 5f), metroLabelTopLeft(10f, 10f, 5f, 4, 20f, 10f))  // left, ends at the ring
        close(MetroPoint(0f, -5f), metroLabelTopLeft(10f, 10f, 5f, 6, 20f, 10f))   // above, centred
        close(MetroPoint(0f, 15f), metroLabelTopLeft(10f, 10f, 5f, 2, 20f, 10f))   // below
    }

    @Test
    fun byItself_aNameKeepsOffALineRunningAlongside() {
        // Two lines along x, the second 0.6 above the first: names on the first go under it.
        val two = MetroScheme(
            id = 2, city = "Тест",
            lines = listOf(
                MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
                MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12, 13)),
            ),
            stations = listOf(
                MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f), MetroStation(3, 1, "C", 2f, 0f),
                MetroStation(11, 2, "D", 0f, -0.6f), MetroStation(12, 2, "E", 1f, -0.6f), MetroStation(13, 2, "F", 2f, -0.6f),
            ),
        )
        val l = metroLayout(two)
        assertEquals(2, metroAutoLabelSide(two, l, 2))
        // And those on the second, over it.
        assertEquals(6, metroAutoLabelSide(two, l, 12))
    }

    /** In one pass (14.10), as station by station: the same sides, the same ticks, the same ends. */
    @Test
    fun allAtOnce_sameAsOneByOne() {
        val withBend = scheme.copy(bends = listOf(MetroBend(12, 11, listOf(MetroMapPoint(2f, 1f)))))
        listOf(scheme, withBend).forEach { s ->
            val l = metroLayout(s)
            val facts = metroPictureFacts(s, l)
            val sides = metroLabelSides(s, l, facts)
            s.stations.forEach { st ->
                assertEquals(metroLabelSide(s, l, st.id), sides[st.id])
                assertEquals(metroTickAngle(s, l, st.id, sides.getValue(st.id)), metroTickAngles(s, sides, facts)[st.id])
                assertEquals(metroTerminusOutward(s, l, st.id), metroTerminusOutwards(facts)[st.id])
            }
        }
    }

    /** Each rule the user's to turn off (15.10). */
    @Test
    fun namesOnTheRight_orNotKeepingOffLinesAlongside_whenSoChosen() {
        val right = scheme.copy(labelsAuto = false)
        right.stations.forEach { assertEquals(0, metroLabelSide(right, metroLayout(right), it.id)) }
        assertEquals(right.stations.associate { it.id to 0 }, metroLabelSides(right, metroLayout(right)))
        // Two lines 0.6 apart: without keeping off, the first clear side in order — above.
        val two = MetroScheme(
            id = 2, city = "Тест",
            lines = listOf(
                MetroLine(1, "1", "Red", 0, trunk = listOf(1, 2, 3)),
                MetroLine(2, "2", "Green", 0, trunk = listOf(11, 12, 13)),
            ),
            stations = listOf(
                MetroStation(1, 1, "A", 0f, 0f), MetroStation(2, 1, "B", 1f, 0f), MetroStation(3, 1, "C", 2f, 0f),
                MetroStation(11, 2, "D", 0f, -0.6f), MetroStation(12, 2, "E", 1f, -0.6f), MetroStation(13, 2, "F", 2f, -0.6f),
            ),
            labelsAvoidCrowd = false,
        )
        assertEquals(6, metroAutoLabelSide(two, metroLayout(two), 2))
        assertEquals(6, metroLabelSides(two, metroLayout(two))[2L])
    }
}
