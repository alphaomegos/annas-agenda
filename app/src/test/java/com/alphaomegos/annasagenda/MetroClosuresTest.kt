package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/**
 * Temporarily closed stations, transfers and exits (04.10).
 *
 * Agreed: trains run through a closed station without stopping; a closed
 * start or end is warned about, not routed; the expected opening day only
 * makes the screen ask, it opens nothing.
 *
 *     line 1:  A(1) B(2) C(3) D(4)       line 2:  K(11) L(12)
 *     C ↔ L (transfer 20);  D has exits 30, 31, with hints from C.
 */
class MetroClosuresTest {

    private val today = LocalDate.of(2026, 10, 20)

    private val base = MetroScheme(
        id = 1,
        city = "Тест",
        lines = listOf(
            MetroLine(id = 100, label = "1", name = "Красная", color = 0, trunk = listOf(1, 2, 3, 4)),
            MetroLine(id = 200, label = "2", name = "Зелёная", color = 0, trunk = listOf(11, 12)),
        ),
        stations = listOf(
            MetroStation(1, 100, "A"), MetroStation(2, 100, "B"), MetroStation(3, 100, "C"), MetroStation(4, 100, "D"),
            MetroStation(11, 200, "K"), MetroStation(12, 200, "L"),
        ),
        transfers = listOf(MetroTransfer(20, 3, 12)),
        exits = listOf(MetroExit(30, 4, "к МЦК"), MetroExit(31, 4, "к дому")),
        hints = listOf(
            MetroHint(40, stationId = 4, fromStationId = 3, car = 1, door = 1, target = MetroHintTarget.Exit(30)),
            MetroHint(41, stationId = 4, fromStationId = 3, car = 8, door = 4, target = MetroHintTarget.Exit(31)),
        ),
    )

    private val shut = MetroClosure(expectedOpening = today)

    private fun closed(vararg targets: MetroClosable) =
        targets.fold(base) { s, t -> metroClosureSet(s, t, shut) }

    @Test
    fun theTrainRunsThroughAClosedStation_andSaysSo() {
        val r = metroRoute(closed(MetroClosable.Station(2)), setOf(1), setOf(4))!!
        val ride = r.steps.single() as MetroRouteStep.Ride
        assertEquals(listOf(1L, 2L, 3L, 4L), ride.stationIds)
        assertEquals(listOf(2L), ride.passedClosedStationIds)
        assertEquals(9, r.minutes)
    }

    @Test
    fun nobodyChangesAtAClosedStation() {
        assertNull(metroRoute(closed(MetroClosable.Station(3)), setOf(1), setOf(11)))
        assertNull(metroRoute(closed(MetroClosable.Station(12)), setOf(1), setOf(11)))
    }

    @Test
    fun aClosedTransfer_isNotWalked() {
        assertNull(metroRoute(closed(MetroClosable.Transfer(20)), setOf(1), setOf(11)))
    }

    @Test
    fun aClosedStart_orEnd_isNoRoute() {
        assertNull(metroRoute(closed(MetroClosable.Station(1)), setOf(1), setOf(4)))
        assertNull(metroRoute(closed(MetroClosable.Station(4)), setOf(1), setOf(4)))
    }

    @Test
    fun aNameWithAnOpenTwin_usesTheOpenOne() {
        val s = closed(MetroClosable.Station(4))
        val r = metroRoute(s, setOf(1), setOf(4, 12))!!
        // The route ends with the walk over to L.
        assertEquals(MetroRouteStep.Change(3, 12, 3), r.steps.last())
        assertEquals(listOf(4L), metroClosedStations(s, setOf(4, 12)))
    }

    @Test
    fun aClosedExit_isNotOffered() {
        val r = metroRoute(closed(MetroClosable.Exit(30)), setOf(1), setOf(4))!!
        assertEquals(listOf(41L), (r.steps.single() as MetroRouteStep.Ride).hints.map { it.id })
    }

    @Test
    fun noChangeAtAClosedFork() {
        val forked = base.copy(
            lines = base.lines.map {
                if (it.id == 100L) it.copy(
                    trunk = listOf(1, 2),
                    branches = listOf(MetroBranch(5, 2, listOf(3)), MetroBranch(6, 2, listOf(4))),
                ) else it
            },
        )
        assertEquals(1, metroRoute(forked, setOf(3), setOf(4))!!.transfers)
        assertNull(metroRoute(metroClosureSet(forked, MetroClosable.Station(2), shut), setOf(3), setOf(4)))
    }

    @Test
    fun dueClosures_todayAndOverdue_oldestFirst_undatedNever() {
        var s = base
        s = metroClosureSet(s, MetroClosable.Exit(30), MetroClosure(today.minusDays(3)))
        s = metroClosureSet(s, MetroClosable.Station(2), MetroClosure(today))
        s = metroClosureSet(s, MetroClosable.Transfer(20), MetroClosure(today))
        s = metroClosureSet(s, MetroClosable.Station(3), MetroClosure(today.plusDays(1)))
        s = metroClosureSet(s, MetroClosable.Station(4), MetroClosure(null))
        assertEquals(
            listOf(
                MetroClosureDue(MetroClosable.Exit(30), today.minusDays(3)),
                MetroClosureDue(MetroClosable.Station(2), today),
                MetroClosureDue(MetroClosable.Transfer(20), today),
            ),
            metroClosuresDue(s, today),
        )
    }

    @Test
    fun openingAndMovingTheDate_areEditsLikeAnyOther() {
        val s = closed(MetroClosable.Station(2))
        assertEquals(shut, metroClosureOf(s, MetroClosable.Station(2)))
        val moved = metroClosureSet(s, MetroClosable.Station(2), MetroClosure(today.plusDays(7)))
        assertTrue(metroClosuresDue(moved, today).isEmpty())
        val opened = metroClosureSet(moved, MetroClosable.Station(2), null)
        assertNull(metroClosureOf(opened, MetroClosable.Station(2)))
        assertSame(opened, metroClosureSet(opened, MetroClosable.Station(2), null))
        assertSame(base, metroClosureSet(base, MetroClosable.Exit(404), shut))
    }
}
