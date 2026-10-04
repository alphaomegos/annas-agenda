package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/**
 * Routes on a scheme the user wrote down (04.10).
 *
 * The rules being checked, as agreed: the fewest minutes win, three a ride
 * and three a change unless the user set otherwise; equal minutes go to
 * fewer changes; trains run from the trunk into each branch, and branch to
 * branch is a change at the fork; hints come from the station one gets off
 * at, for the side the train comes from.
 *
 * The test map:
 *
 *     line 1:  A(1) B(2) C(3) D(4) E(5) ─┬─ F(6) G(7)
 *                                        └─ H(8) I(9)
 *     line 2:  K(11) L(12) M(13) N(14)
 *     line 3:  a ring R1(21)…R6(26)
 *
 *     C ↔ L, 5 minutes;  D ↔ M, 10 minutes;  N ↔ R1, default.
 */
class MetroRoutingTest {

    private val line1 = MetroLine(
        id = 1, label = "1", name = "Красная", color = 0xFFE42313,
        trunk = listOf(1, 2, 3, 4, 5),
        branches = listOf(
            MetroBranch(id = 100, fromStationId = 5, stationIds = listOf(6, 7)),
            MetroBranch(id = 101, fromStationId = 5, stationIds = listOf(8, 9)),
        ),
    )
    private val line2 = MetroLine(id = 2, label = "2", name = "Зелёная", color = 0xFF4FB04F, trunk = listOf(11, 12, 13, 14))
    private val line3 = MetroLine(
        id = 3, label = "5", name = "Кольцевая", color = 0xFF915133, ring = true,
        trunk = listOf(21, 22, 23, 24, 25, 26),
    )

    private val names = mapOf(
        1L to "A", 2L to "B", 3L to "C", 4L to "D", 5L to "E", 6L to "F", 7L to "G", 8L to "H", 9L to "I",
        11L to "K", 12L to "L", 13L to "M", 14L to "N",
        21L to "R1", 22L to "R2", 23L to "R3", 24L to "R4", 25L to "R5", 26L to "R6",
    )

    private fun lineOf(id: Long) = when {
        id < 10 -> 1L
        id < 20 -> 2L
        else -> 3L
    }

    private val scheme = MetroScheme(
        id = 1,
        city = "Тест",
        lines = listOf(line1, line2, line3),
        stations = names.map { (id, name) -> MetroStation(id = id, lineId = lineOf(id), name = name) },
        transfers = listOf(
            MetroTransfer(id = 1, aStationId = 3, bStationId = 12, minutes = 5),
            MetroTransfer(id = 2, aStationId = 4, bStationId = 13, minutes = 10),
            MetroTransfer(id = 3, aStationId = 14, bStationId = 21),
        ),
        exits = listOf(
            MetroExit(id = 1, stationId = 11, name = "к МЦК"),
            MetroExit(id = 2, stationId = 11, name = "к дому"),
        ),
        hints = listOf(
            // At C, for L: coming from B — and, differently, coming from D.
            MetroHint(id = 1, stationId = 3, fromStationId = 2, car = 3, door = 2, target = MetroHintTarget.Transfer(12)),
            MetroHint(id = 2, stationId = 3, fromStationId = 4, car = 5, door = 3, target = MetroHintTarget.Transfer(12)),
            // At K, coming from L: two exits.
            MetroHint(id = 3, stationId = 11, fromStationId = 12, car = 8, door = 4, target = MetroHintTarget.Exit(2)),
            MetroHint(id = 4, stationId = 11, fromStationId = 12, car = 1, door = 1, target = MetroHintTarget.Exit(1)),
        ),
    )

    private fun route(from: Long, to: Long, on: MetroScheme = scheme) =
        metroRoute(on, setOf(from), setOf(to))

    private fun rides(r: MetroRoute?) = r!!.steps.filterIsInstance<MetroRouteStep.Ride>()

    @Test
    fun aStraightRide_isOneStepAtThreeMinutesAStation() {
        val r = route(1, 4)!!
        assertEquals(9, r.minutes)
        assertEquals(0, r.transfers)
        val ride = rides(r).single()
        assertEquals(listOf(1L, 2L, 3L, 4L), ride.stationIds)
        assertEquals(1L, ride.lineId)
    }

    @Test
    fun gettingOffBeforeTheFork_anyTrainThatWayWillDo() {
        assertEquals(listOf(7L, 9L), rides(route(1, 4)).single().towardsStationIds)
    }

    @Test
    fun goingIntoABranch_needsATrainForThatBranch() {
        val ride = rides(route(1, 7)).single()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), ride.stationIds)
        assertEquals(listOf(7L), ride.towardsStationIds)
    }

    @Test
    fun theOtherWay_theTrainIsForTheTrunksStart() {
        assertEquals(listOf(1L), rides(route(9, 4)).single().towardsStationIds)
    }

    @Test
    fun branchToBranch_isAChangeAtTheFork() {
        val r = route(7, 9)!!
        assertEquals(1, r.transfers)
        assertEquals(15, r.minutes) // two stations, a change, two stations
        assertEquals(3, r.steps.size)
        assertEquals(listOf(7L, 6L, 5L), (r.steps[0] as MetroRouteStep.Ride).stationIds)
        assertEquals(MetroRouteStep.Change(5, 5, 3), r.steps[1])
        assertEquals(listOf(5L, 8L, 9L), (r.steps[2] as MetroRouteStep.Ride).stationIds)
    }

    @Test
    fun aForkInTheMiddleOfTheTrunk_branchTrainsGoTowardsTheStart() {
        val line = MetroLine(
            id = 1, label = "1", name = "x", color = 0,
            trunk = listOf(1, 2, 3, 4),
            branches = listOf(MetroBranch(id = 9, fromStationId = 2, stationIds = listOf(5))),
        )
        val s = MetroScheme(id = 1, city = "x", lines = listOf(line), stations = (1L..5L).map { MetroStation(it, 1, "S$it") })
        assertEquals(0, route(5, 1, s)!!.transfers)
        assertEquals(1, route(5, 3, s)!!.transfers)
        assertEquals(0, route(1, 4, s)!!.transfers)
    }

    @Test
    fun aForkAtTheTrunksFirstStation_branchTrainsGoAlongTheTrunk() {
        val line = MetroLine(
            id = 1, label = "1", name = "x", color = 0,
            trunk = listOf(1, 2, 3),
            branches = listOf(
                MetroBranch(id = 8, fromStationId = 1, stationIds = listOf(4)),
                MetroBranch(id = 9, fromStationId = 1, stationIds = listOf(5)),
            ),
        )
        val s = MetroScheme(id = 1, city = "x", lines = listOf(line), stations = (1L..5L).map { MetroStation(it, 1, "S$it") })
        assertEquals(0, route(4, 3, s)!!.transfers)
        assertEquals(1, route(4, 5, s)!!.transfers)
    }

    @Test
    fun aTransfer_takesTheCheaperWalkAndSaysWhereToSit() {
        val r = route(1, 11)!!
        // A-B-C (6), walk to L (5), L-K (3) beats A…D (9), walk (10), M-L-K (6).
        assertEquals(14, r.minutes)
        assertEquals(1, r.transfers)
        val first = r.steps[0] as MetroRouteStep.Ride
        assertEquals(listOf(1L, 2L, 3L), first.stationIds)
        assertEquals(listOf(1L), first.hints.map { it.id })
        assertEquals(MetroRouteStep.Change(3, 12, 5), r.steps[1])
        assertEquals(listOf(12L, 11L), (r.steps[2] as MetroRouteStep.Ride).stationIds)
    }

    @Test
    fun theHint_dependsOnTheSideTheTrainComesFrom() {
        val r = route(7, 11)!! // G…E, D, C, then L: arriving at C from D
        val first = r.steps[0] as MetroRouteStep.Ride
        assertEquals(3L, first.stationIds.last())
        assertEquals(listOf(2L), first.hints.map { it.id })
    }

    @Test
    fun theLastRide_offersEveryExitHint_frontOfTheTrainFirst() {
        val last = rides(route(1, 11)).last()
        assertEquals(listOf(4L, 3L), last.hints.map { it.id })
    }

    @Test
    fun noHintWritten_meansNoHint() {
        assertTrue(rides(route(1, 4)).single().hints.isEmpty())
    }

    @Test
    fun aRideTimeTheUserSet_isUsed() {
        val slow = scheme.copy(segmentTimes = listOf(MetroSegmentTime(aStationId = 3, bStationId = 2, minutes = 20)))
        assertEquals(23, route(1, 3, slow)!!.minutes) // set as C-B, ridden B-C
    }

    @Test
    fun theSchemesDefaults_areUsedWhereNothingIsSet() {
        val fast = scheme.copy(defaultSegmentMinutes = 2, defaultTransferMinutes = 1)
        // N ↔ R1 has no time of its own: K…N (6), walk (1), R1 (0).
        assertEquals(7, route(11, 21, fast)!!.minutes)
    }

    @Test
    fun onARing_theShortWayRound_withNoTerminal() {
        val ride = rides(route(21, 25)).single()
        assertEquals(listOf(21L, 26L, 25L), ride.stationIds)
        assertTrue(ride.towardsStationIds.isEmpty())
    }

    @Test
    fun equalMinutes_fewerChangesWin() {
        // Line 1: A-B-C, 6 minutes. Line 2: A'-C', 6 minutes, with free walks.
        val s = MetroScheme(
            id = 1, city = "x",
            lines = listOf(
                MetroLine(id = 1, label = "1", name = "x", color = 0, trunk = listOf(1, 2, 3)),
                MetroLine(id = 2, label = "2", name = "y", color = 0, trunk = listOf(4, 5)),
            ),
            stations = listOf(1L, 2L, 3L).map { MetroStation(it, 1, "S$it") } + listOf(4L, 5L).map { MetroStation(it, 2, "S$it") },
            transfers = listOf(MetroTransfer(1, 1, 4, minutes = 0), MetroTransfer(2, 5, 3, minutes = 0)),
            segmentTimes = listOf(MetroSegmentTime(4, 5, 6)),
        )
        val r = route(1, 3, s)!!
        assertEquals(6, r.minutes)
        assertEquals(0, r.transfers)
    }

    @Test
    fun aNameOnTwoLines_eitherStationWillDo() {
        val s = scheme.copy(stations = scheme.stations.map { if (it.id == 13L) it.copy(name = "d ") else it })
        val r = metroRoute(s, setOf(1), metroStationsNamed(s, "D"))!!
        assertEquals(setOf(4L, 13L), metroStationsNamed(s, " d"))
        assertEquals(9, r.minutes) // D on line 1, not M via the walk
    }

    @Test
    fun startingWhereOneIs_isAnEmptyRoute() {
        assertEquals(MetroRoute(emptyList(), 0, 0), route(3, 3))
    }

    @Test
    fun noWayThere_isNull() {
        val cut = scheme.copy(transfers = emptyList())
        assertNull(route(1, 11, cut))
        assertNull(metroRoute(scheme, emptySet(), setOf(1)))
    }

    @Test
    fun aBrokenScheme_doesNotThrow() {
        val broken = scheme.copy(
            lines = scheme.lines + MetroLine(id = 9, label = "9", name = "?", color = 0, trunk = listOf(99, 1)),
            transfers = scheme.transfers + MetroTransfer(9, 3, 404),
        )
        assertEquals(9, route(1, 4, broken)!!.minutes)
    }
}
