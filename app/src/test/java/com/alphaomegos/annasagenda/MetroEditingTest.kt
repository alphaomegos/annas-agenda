package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/**
 * Editing a scheme (04.10): every edit is a rule, and what hangs off the
 * shape of a line — hints by the side the train comes from, ride times
 * between neighbours, transfers and exits of a station — follows the edit.
 *
 *     line 1:  A(1) B(2) C(3)
 *     line 2:  K(11) L(12)
 *     B ↔ L (transfer 20);  C has exit 30 "к дому".
 */
class MetroEditingTest {

    private val base = MetroScheme(
        id = 7,
        city = "Тест",
        lines = listOf(
            MetroLine(id = 100, label = "1", name = "Красная", color = 0xFFE42313, trunk = listOf(1, 2, 3)),
            MetroLine(id = 200, label = "2", name = "Зелёная", color = 0xFF4FB04F, trunk = listOf(11, 12)),
        ),
        stations = listOf(
            MetroStation(1, 100, "A"), MetroStation(2, 100, "B"), MetroStation(3, 100, "C"),
            MetroStation(11, 200, "K"), MetroStation(12, 200, "L"),
        ),
        transfers = listOf(MetroTransfer(20, 2, 12)),
        exits = listOf(MetroExit(30, 3, "к дому")),
        hints = listOf(
            // At C coming from B: exit to home.
            MetroHint(40, stationId = 3, fromStationId = 2, car = 1, door = 1, target = MetroHintTarget.Exit(30)),
            // At B coming from A: transfer to L.
            MetroHint(41, stationId = 2, fromStationId = 1, car = 3, door = 2, target = MetroHintTarget.Transfer(12)),
            // At L coming from K: transfer to B.
            MetroHint(42, stationId = 12, fromStationId = 11, car = 8, door = 4, target = MetroHintTarget.Transfer(2)),
        ),
        segmentTimes = listOf(MetroSegmentTime(2, 3, 5)),
    )

    private fun MetroScheme.line(id: Long) = lines.first { it.id == id }
    private fun MetroScheme.hint(id: Long) = hints.firstOrNull { it.id == id }
    private fun MetroScheme.stationNamed(name: String) = stations.first { it.name == name }

    @Test
    fun nextId_isPastEveryIdInTheScheme() {
        assertEquals(201L, metroNextId(base))
    }

    @Test
    fun aStationAtTheEnd_andAtTheStart() {
        val end = metroStationAdded(base, 100, null, 99, "  D  ")
        assertEquals(listOf(1L, 2L, 3L, 201L), end.line(100).trunk)
        assertEquals("D", end.stationNamed("D").name)
        val start = metroStationAdded(base, 100, null, 0, "Z")
        assertEquals(listOf(201L, 1L, 2L, 3L), start.line(100).trunk)
    }

    @Test
    fun insertingBetween_movesTheHintsOfThatSideOntoTheNewStation_andForgetsTheRideTime() {
        val after = metroStationAdded(base, 100, null, 2, "X") // between B and C
        assertEquals(listOf(1L, 2L, 201L, 3L), after.line(100).trunk)
        assertEquals(201L, after.hint(40)!!.fromStationId)
        assertEquals(1L, after.hint(41)!!.fromStationId) // the other side of B is untouched
        assertTrue(after.segmentTimes.isEmpty())
    }

    @Test
    fun removingAStation_joinsItsNeighbours_andTakesWhatHungOffIt() {
        val after = metroStationRemoved(base, 2) // B
        assertEquals(listOf(1L, 3L), after.line(100).trunk)
        assertEquals(1L, after.hint(40)!!.fromStationId) // at C, now coming from A
        assertNull(after.hint(41)) // B's own hint
        assertNull(after.hint(42)) // a hint for the transfer to B
        assertTrue(after.transfers.isEmpty())
        assertTrue(after.segmentTimes.isEmpty())
    }

    @Test
    fun aStationThatBecomesATerminal_losesTheHintsFromTheGoneSide() {
        val after = metroStationRemoved(base, 1) // A, so B is now the first station
        assertNull(after.hint(41))
        assertEquals(2L, after.hint(40)!!.fromStationId)
    }

    @Test
    fun movingAStation_keepsItsHintsOnTheSameSide() {
        val after = metroStationMoved(base, 2, +1) // A C B
        assertEquals(listOf(1L, 3L, 2L), after.line(100).trunk)
        // B's hint was "from the start side": that side is C now.
        assertEquals(3L, after.hint(41)!!.fromStationId)
        // C's hint was "from the start side" too: that is A now.
        assertEquals(1L, after.hint(40)!!.fromStationId)
        assertSame(base, metroStationMoved(base, 3, +1))
    }

    @Test
    fun aJunctionRemoved_itsBranchesLeaveFromTheStationBefore() {
        val forked = metroBranchAdded(base, 100, 3, "F")
        val branch = forked.line(100).branches.single()
        assertEquals(3L, branch.fromStationId)
        val after = metroStationRemoved(forked, 3)
        assertEquals(2L, after.line(100).branches.single().fromStationId)
    }

    @Test
    fun aJunctionWithNowhereElseToLeaveFrom_isNotRemoved() {
        val lone = base.copy(lines = base.lines.map { if (it.id == 100L) it.copy(trunk = listOf(1)) else it })
        val forked = metroBranchAdded(lone, 100, 1, "F")
        assertSame(forked, metroStationRemoved(forked, 1))
    }

    @Test
    fun removingABranch_takesItsStationsAndTheBranchesOffIt() {
        val one = metroBranchAdded(base, 100, 3, "F")
        val fId = one.stationNamed("F").id
        val two = metroBranchAdded(one, 100, fId, "G")
        val after = metroBranchRemoved(two, 100, one.line(100).branches.single().id)
        assertTrue(after.line(100).branches.isEmpty())
        assertEquals(setOf(1L, 2L, 3L, 11L, 12L), after.stations.map { it.id }.toSet())
    }

    @Test
    fun aBranchFromAStationOfAnotherLine_isRefused() {
        assertSame(base, metroBranchAdded(base, 100, 11, "F"))
    }

    @Test
    fun transfers_betweenLinesOnly_andSettingAgainChangesTheTime() {
        assertSame(base, metroTransferSet(base, 1, 2, null)) // same line
        assertSame(base, metroTransferSet(base, 1, 11, -1))
        val timed = metroTransferSet(base, 12, 2, 4) // the existing one, written the other way round
        assertEquals(listOf(MetroTransfer(20, 2, 12, 4)), timed.transfers)
        val added = metroTransferSet(base, 3, 11, null)
        assertEquals(2, added.transfers.size)
    }

    @Test
    fun removingATransfer_takesItsHints() {
        val after = metroTransferRemoved(base, 20)
        assertNull(after.hint(41))
        assertNull(after.hint(42))
        assertEquals(40L, after.hints.single().id)
    }

    @Test
    fun rideTimes_onlyBetweenNeighbours_andNullGoesBackToTheDefault() {
        assertSame(base, metroSegmentTimeSet(base, 1, 3, 4))
        assertSame(base, metroSegmentTimeSet(base, 1, 2, 0))
        assertEquals(2, metroSegmentTimeSet(base, 2, 1, 4).segmentTimes.size)
        assertTrue(metroSegmentTimeSet(base, 3, 2, null).segmentTimes.isEmpty())
    }

    @Test
    fun aHint_mustMakeSense() {
        val ok = { from: Long, car: Int, door: Int, target: MetroHintTarget ->
            metroHintSet(base, null, 3, from, car, door, target)
        }
        assertSame(base, ok(1, 1, 1, MetroHintTarget.Exit(30))) // A is not next to C
        assertSame(base, ok(2, 9, 1, MetroHintTarget.Exit(30))) // there is no ninth car
        assertSame(base, ok(2, 1, 5, MetroHintTarget.Exit(30))) // nor a fifth door
        assertSame(base, ok(2, 1, 1, MetroHintTarget.Transfer(12))) // C has no transfer to L
        val added = ok(2, 8, 4, MetroHintTarget.Exit(30))
        assertEquals(4, added.hints.size)
        assertEquals(201L, added.hints.last().id)
    }

    @Test
    fun aHintRewritten_keepsItsId() {
        val after = metroHintSet(base, 40, 3, 2, 2, 3, MetroHintTarget.Exit(30))
        assertEquals(MetroHint(40, 3, 2, 2, 3, MetroHintTarget.Exit(30)), after.hint(40))
        assertSame(after, metroHintSet(after, 40, 3, 2, 2, 3, MetroHintTarget.Exit(30)))
    }

    @Test
    fun removingAnExit_takesItsHints() {
        val after = metroExitRemoved(base, 30)
        assertTrue(after.exits.isEmpty())
        assertNull(after.hint(40))
    }

    @Test
    fun aRingNoMore_losesTheHintsAcrossTheJoin() {
        val ring = metroLineEdited(base, 100, "1", "Красная", 0xFFE42313, ring = true, carCount = 8, doorsPerCar = 4)
        // On the ring A is next to C: a hint at C coming from A is now possible.
        val acrossJoin = metroHintSet(ring, null, 3, 1, 1, 1, MetroHintTarget.Exit(30))
        assertEquals(4, acrossJoin.hints.size)
        val straight = metroLineEdited(acrossJoin, 100, "1", "Красная", 0xFFE42313, ring = false, carCount = 8, doorsPerCar = 4)
        assertEquals(3, straight.hints.size)
    }

    @Test
    fun aLineRemoved_takesItsStationsTransfersAndHints() {
        val after = metroLineRemoved(base, 200)
        assertEquals(listOf(100L), after.lines.map { it.id })
        assertTrue(after.transfers.isEmpty())
        assertEquals(listOf(40L), after.hints.map { it.id })
    }

    @Test
    fun linesMoveInTheList_andTheEndsStay() {
        assertEquals(listOf(200L, 100L), metroLineMoved(base, 200, -1).lines.map { it.id })
        assertSame(base, metroLineMoved(base, 100, -1))
    }

    @Test
    fun blankNames_andNoChange_giveTheSameScheme() {
        assertSame(base, metroStationRenamed(base, 1, "   "))
        assertSame(base, metroStationRenamed(base, 1, "A"))
        assertSame(base, metroSchemeRenamed(base, ""))
        assertSame(base, metroLineAdded(base, " ", "x", 0))
        assertSame(base, metroExitAdded(base, 3, ""))
        assertSame(base, metroSchemeWithDefaults(base, 0, 3))
        assertSame(base, metroLineEdited(base, 100, "1", "Красная", 0xFFE42313, false, 8, 4))
        assertNotSame(base, metroStationRenamed(base, 1, "А  новая"))
        assertEquals("А новая", metroStationRenamed(base, 1, "А  новая").stations.first().name)
    }

    @Test
    fun aLibraryCopy_keepsEveryIdAndRemembersItsSource() {
        val copy = metroSchemeCopied(base, newId = 99, source = "moscow")
        assertEquals(99L, copy.id)
        assertEquals("moscow", copy.librarySource)
        assertEquals(base.stations, copy.stations)
        assertEquals(base.hints, copy.hints)
    }

    @Test
    fun aRouteRunsThroughAStationInsertedLater() {
        val after = metroStationAdded(base, 100, null, 2, "X")
        val r = metroRoute(after, setOf(1), setOf(3))!!
        assertEquals(listOf(1L, 2L, 201L, 3L), (r.steps.single() as MetroRouteStep.Ride).stationIds)
        assertEquals(listOf(40L), (r.steps.single() as MetroRouteStep.Ride).hints.map { it.id })
    }
}
