package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroHint
import com.alphaomegos.annasagenda.model.MetroHintTarget
import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import java.util.PriorityQueue

/**
 * How to get from one station to another on a scheme the user wrote down.
 *
 * The cheapest route in minutes wins (decided 04.10): a ride between two
 * neighbouring stations costs what the user set for it or the scheme's
 * default, three minutes; a transfer the same. With nothing set anywhere this
 * is "stations, plus one station's worth for every change". Between routes
 * that take equally long, the one with fewer changes wins.
 *
 * Changing trains at a fork — from one branch to another, or from a branch on
 * into the rest of the trunk — is a change like any other, at the scheme's
 * default transfer time: the passenger gets off and waits for another train.
 */
data class MetroRoute(
    val steps: List<MetroRouteStep>,
    val minutes: Int,
    val transfers: Int,
)

sealed interface MetroRouteStep {
    /**
     * One train, from [stationIds]' first to its last.
     *
     * [towardsStationIds] are the terminals a train going this way may be
     * signed for: one on a plain line, several when the passenger gets off
     * before a fork and any train will do, none on a ring — there the sign
     * says the next station, which is [stationIds]' second.
     *
     * [hints] are where to sit so that getting off is quick: for the transfer
     * that follows, or, on the last ride, for each of the station's exits
     * that is open.
     *
     * [passedClosedStationIds] are the closed stations the train runs
     * through without stopping, in the order it meets them.
     */
    data class Ride(
        val lineId: Long,
        val stationIds: List<Long>,
        val towardsStationIds: List<Long>,
        val minutes: Int,
        val hints: List<MetroHint>,
        val passedClosedStationIds: List<Long> = emptyList(),
    ) : MetroRouteStep

    /**
     * A walk to another line, or, when [fromStationId] and [toStationId] are
     * the same, a change of trains at a fork.
     */
    data class Change(
        val fromStationId: Long,
        val toStationId: Long,
        val minutes: Int,
    ) : MetroRouteStep
}

/**
 * The best route from any of [from] to any of [to], or null when the scheme
 * has no way between them.
 *
 * Sets, because a name can stand for more than one station: "Каховская" is on
 * two lines, and whichever one gives the better route is the one meant.
 * Starting where one already is gives an empty route.
 *
 * Closed stations (04.10) are neither a start nor an end — the screen warns
 * about them before asking (decided 04.10) — nor a place to change; trains
 * run through them. Closed transfers are not walked.
 */
fun metroRoute(scheme: MetroScheme, from: Set<Long>, to: Set<Long>): MetroRoute? {
    val net = MetroNetwork(scheme)
    val starts = from.filter { it in net.stationIds && !net.isClosed(it) }.toSet()
    val ends = to.filter { it in net.stationIds && !net.isClosed(it) }.toSet()
    if (starts.isEmpty() || ends.isEmpty()) return null
    if (starts.any { it in ends }) return MetroRoute(emptyList(), 0, 0)

    // A state is a station and the station the train came from; null means
    // the passenger is on the platform, free to take a train either way.
    data class State(val station: Long, val prev: Long?)
    data class Entry(val state: State, val minutes: Int, val transfers: Int, val order: Long)

    val best = HashMap<State, Pair<Int, Int>>()
    val parent = HashMap<State, State>()
    val queue = PriorityQueue<Entry>(
        compareBy<Entry> { it.minutes }.thenBy { it.transfers }.thenBy { it.order }
    )
    var order = 0L

    fun offer(state: State, minutes: Int, transfers: Int, via: State?) {
        val known = best[state]
        if (known != null && (known.first < minutes || (known.first == minutes && known.second <= transfers))) return
        best[state] = minutes to transfers
        if (via != null) parent[state] = via else parent.remove(state)
        queue.add(Entry(state, minutes, transfers, order++))
    }

    starts.sorted().forEach { offer(State(it, null), 0, 0, null) }

    var goal: State? = null
    while (queue.isNotEmpty()) {
        val (state, minutes, transfers) = queue.poll()
        val known = best[state]
        if (known != null && (known.first != minutes || known.second != transfers)) continue
        if (state.station in ends) {
            goal = state
            break
        }
        val s = state.station
        val closed = net.isClosed(s)
        for (n in net.neighbours(s)) {
            if (n == state.prev) continue
            var cost = net.segmentMinutes(s, n)
            var changes = 0
            if (state.prev != null && !net.continues(state.prev, s, n)) {
                // Nobody gets off at a closed station, so nobody changes there.
                if (closed) continue
                cost += scheme.defaultTransferMinutes
                changes = 1
            }
            offer(State(n, s), minutes + cost, transfers + changes, state)
        }
        for ((t, walk) in net.transfersFrom(s)) {
            offer(State(t, null), minutes + walk, transfers + 1, state)
        }
    }

    val end = goal ?: return null
    val path = generateSequence(end) { parent[it] }.toList().asReversed()
    val total = best.getValue(end)
    return MetroRoute(stepsOf(path.map { it.station to it.prev }, net, scheme), total.first, total.second)
}

/** The route's states, in order, cut into rides and changes. */
private fun stepsOf(path: List<Pair<Long, Long?>>, net: MetroNetwork, scheme: MetroScheme): List<MetroRouteStep> {
    val steps = mutableListOf<MetroRouteStep>()
    var ride = mutableListOf(path.first().first)

    fun closeRide(next: MetroHintTarget?) {
        if (ride.size >= 2) steps += net.ride(ride, next)
        ride = mutableListOf(ride.last())
    }

    for (i in 1 until path.size) {
        val (station, prev) = path[i]
        val before = path[i - 1].first
        if (prev == null) {
            // A walk to another line.
            closeRide(MetroHintTarget.Transfer(station))
            steps += MetroRouteStep.Change(before, station, net.transferMinutes(before, station))
            ride = mutableListOf(station)
        } else {
            val cameFrom = path[i - 1].second
            if (cameFrom != null && ride.size >= 2 && !net.continues(cameFrom, before, station)) {
                // A change of trains at a fork.
                closeRide(null)
                steps += MetroRouteStep.Change(before, before, scheme.defaultTransferMinutes)
            }
            ride += station
        }
    }
    if (ride.size >= 2) steps += net.ride(ride, null, last = true)
    return steps
}

/**
 * The scheme turned into what the search needs: who is next to whom, what a
 * ride or a walk costs, and which ways through a station one train takes.
 */
internal class MetroNetwork(private val scheme: MetroScheme) {

    val stationIds: Set<Long> = scheme.stations.mapTo(HashSet()) { it.id }

    private val closedStations: Set<Long> = scheme.stations.filter { it.closure != null }.mapTo(HashSet()) { it.id }

    fun isClosed(station: Long): Boolean = station in closedStations

    private val lineOf: Map<Long, Long> = scheme.stations.associate { it.id to it.lineId }
    private val lines: Map<Long, MetroLine> = scheme.lines.associateBy { it.id }
    private val topologies = HashMap<Long, MetroLineTopology>()

    private fun topology(lineId: Long): MetroLineTopology? =
        lines[lineId]?.let { line -> topologies.getOrPut(lineId) { MetroLineTopology(line, stationIds) } }

    private val segmentTimes: Map<Pair<Long, Long>, Int> = buildMap {
        scheme.segmentTimes.forEach {
            put(it.aStationId to it.bStationId, it.minutes)
            put(it.bStationId to it.aStationId, it.minutes)
        }
    }

    private val walks: Map<Long, List<Pair<Long, Int>>> = buildMap<Long, MutableList<Pair<Long, Int>>> {
        scheme.transfers.forEach { t ->
            if (t.aStationId == t.bStationId || t.aStationId !in stationIds || t.bStationId !in stationIds) return@forEach
            if (t.closure != null || t.aStationId in closedStations || t.bStationId in closedStations) return@forEach
            val minutes = (t.minutes ?: scheme.defaultTransferMinutes).coerceAtLeast(0)
            getOrPut(t.aStationId) { mutableListOf() } += t.bStationId to minutes
            getOrPut(t.bStationId) { mutableListOf() } += t.aStationId to minutes
        }
    }

    fun neighbours(station: Long): List<Long> =
        lineOf[station]?.let { topology(it)?.neighbours(station) }.orEmpty()

    fun continues(prev: Long, station: Long, next: Long): Boolean =
        lineOf[station]?.let { topology(it)?.continues(prev, station, next) } ?: false

    fun segmentMinutes(a: Long, b: Long): Int =
        (segmentTimes[a to b] ?: scheme.defaultSegmentMinutes).coerceAtLeast(0)

    fun transfersFrom(station: Long): List<Pair<Long, Int>> = walks[station].orEmpty()

    fun transferMinutes(a: Long, b: Long): Int =
        walks[a]?.filter { it.first == b }?.minOfOrNull { it.second } ?: scheme.defaultTransferMinutes

    fun ride(stations: List<Long>, next: MetroHintTarget?, last: Boolean = false): MetroRouteStep.Ride {
        val lineId = lineOf.getValue(stations.first())
        val alight = stations.last()
        val cameFrom = stations[stations.size - 2]
        val closedExits = scheme.exits.filter { it.closure != null }.mapTo(HashSet()) { it.id }
        val hints = scheme.hints.filter { h ->
            h.stationId == alight && h.fromStationId == cameFrom && when {
                last -> h.target is MetroHintTarget.Exit && h.target.exitId !in closedExits
                next != null -> h.target == next
                else -> false
            }
        }.sortedWith(compareBy({ it.car }, { it.door }, { it.id }))
        return MetroRouteStep.Ride(
            lineId = lineId,
            stationIds = stations.toList(),
            towardsStationIds = topology(lineId)?.terminalsAhead(cameFrom, alight).orEmpty(),
            minutes = stations.zipWithNext { a, b -> segmentMinutes(a, b) }.sum(),
            hints = hints,
            passedClosedStationIds = stations.drop(1).dropLast(1).filter { it in closedStations },
        )
    }
}

/**
 * One line's shape: which stations are next to which, and which ways through
 * a station a single train takes.
 *
 * Every station on the line has a way "back" and a way "forward" along its
 * own run — the trunk, or the branch it is on, whose first station's way back
 * is the station the branch leaves from. A station where branches leave has a
 * way into each of them too. One train goes:
 *
 * - back to forward, as on any line;
 * - from a branch to the side of the line its trains run to: back on a
 *   branch, back on the trunk — or forward when the branch leaves from the
 *   trunk's first station — and either way on a ring.
 *
 * Anything else — branch to branch, branch to the far side — is a change.
 * Stations a broken scheme names but does not have are left out.
 */
internal class MetroLineTopology(line: MetroLine, known: Set<Long>) {

    private val back = HashMap<Long, Long>()
    private val forward = HashMap<Long, Long>()
    private val branchStarts = HashMap<Long, MutableList<Long>>()
    private val towardStart = HashMap<Long, List<Long>>()
    private val adjacent = LinkedHashMap<Long, LinkedHashSet<Long>>()

    init {
        val trunk = line.trunk.filter { it in known }.distinct()
        val ring = line.ring && trunk.size >= 3
        trunk.forEachIndexed { i, s ->
            adjacent.getOrPut(s) { LinkedHashSet() }
            if (i > 0) link(trunk[i - 1], s)
        }
        if (ring) link(trunk.last(), trunk.first())

        val onLine = trunk.toMutableSet()
        // A branch may leave from another branch: take them in an order where
        // the station each leaves from is already placed.
        var pending = line.branches
        while (pending.isNotEmpty()) {
            val (ready, waiting) = pending.partition { it.fromStationId in onLine }
            if (ready.isEmpty()) break
            ready.forEach { b ->
                val stations = b.stationIds.filter { it in known && it !in onLine }.distinct()
                if (stations.isEmpty()) return@forEach
                branchStarts.getOrPut(b.fromStationId) { mutableListOf() } += stations.first()
                link(b.fromStationId, stations.first())
                stations.zipWithNext { a, c -> link(a, c) }
                onLine += stations
            }
            pending = waiting
        }

        // Where a branch's trains go on to, from the station it leaves from.
        branchStarts.keys.forEach { j ->
            val ways = listOfNotNull(back[j], forward[j])
            towardStart[j] = when {
                ring && j in trunk -> ways
                j == trunk.firstOrNull() && j !in back -> ways
                else -> listOfNotNull(back[j])
            }
        }
    }

    private fun link(a: Long, b: Long) {
        if (a == b) return
        forward.putIfAbsent(a, b)
        back.putIfAbsent(b, a)
        adjacent.getOrPut(a) { LinkedHashSet() } += b
        adjacent.getOrPut(b) { LinkedHashSet() } += a
    }

    fun neighbours(station: Long): List<Long> = adjacent[station]?.toList().orEmpty()

    fun continues(prev: Long, station: Long, next: Long): Boolean {
        if (prev == next) return false
        val starts = branchStarts[station].orEmpty()
        val prevIsBranch = prev in starts
        val nextIsBranch = next in starts
        return when {
            prevIsBranch && nextIsBranch -> false
            prevIsBranch -> next in towardStart[station].orEmpty()
            nextIsBranch -> prev in towardStart[station].orEmpty()
            else -> true
        }
    }

    /**
     * The terminals a train going from [prev] through [station] may be bound
     * for; empty when it can go round for ever, on a ring.
     */
    fun terminalsAhead(prev: Long, station: Long): List<Long> {
        val ends = LinkedHashSet<Long>()
        val seen = HashSet<Pair<Long, Long>>()
        // Breadth first, so terminals come nearest first.
        val queue = ArrayDeque<Pair<Long, Long>>()
        queue.addLast(prev to station)
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            if (!seen.add(step)) continue
            val (p, s) = step
            val onward = neighbours(s).filter { it != p && continues(p, s, it) }
            if (onward.isEmpty()) ends += s else onward.forEach { queue.addLast(s to it) }
        }
        return ends.toList()
    }
}

/**
 * The stations a typed name stands for: every station so called, on any line,
 * matched without regard to case or the spaces around it.
 */
fun metroStationsNamed(scheme: MetroScheme, name: String): Set<Long> {
    val wanted = name.trim()
    if (wanted.isEmpty()) return emptySet()
    return scheme.stations.filter { it.name.trim().equals(wanted, ignoreCase = true) }.mapTo(LinkedHashSet()) { it.id }
}
