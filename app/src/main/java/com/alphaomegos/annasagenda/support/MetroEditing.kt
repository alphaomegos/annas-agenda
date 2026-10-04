package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroBranch
import com.alphaomegos.annasagenda.model.MetroExit
import com.alphaomegos.annasagenda.model.MetroHint
import com.alphaomegos.annasagenda.model.MetroHintTarget
import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.model.MetroSegmentTime
import com.alphaomegos.annasagenda.model.MetroStation
import com.alphaomegos.annasagenda.model.MetroTransfer

/**
 * Editing a metro scheme, as rules (04.10).
 *
 * Every function returns the scheme as it is after the edit, and the **same
 * instance** when the edit changes nothing — a blank name, a station that is
 * not there — so the caller can tell, and nothing is written for nothing.
 *
 * The hard part is what hangs off the line's shape. A hint names the
 * direction by the station the train comes from; a ride time names two
 * neighbours. Inserting, removing or moving a station changes who is next
 * to whom, so after every change of shape [reconciled] puts things right:
 *
 * - a hint keeps its **side**: "coming from Чистые пруды" on Лубянка means
 *   "from the side of the line where Чистые пруды is"; insert a station on
 *   that side and the hint now comes from the new station; remove that
 *   neighbour and it comes from the one beyond. When no station is left on
 *   that side — the station became a terminal — the hint goes;
 * - a ride time between two stations no longer next to each other goes;
 * - transfers, exits and hints of a station that is gone go with it, and so
 *   do hints for a transfer or exit that is gone.
 */

/* ---------------- ids and names ---------------- */

/** The next free id inside [scheme]: ids are the scheme's own (see MetroScheme). */
fun metroNextId(scheme: MetroScheme): Long = maxOf(
    scheme.lines.maxOfOrNull { it.id } ?: 0L,
    scheme.lines.flatMap { it.branches }.maxOfOrNull { it.id } ?: 0L,
    scheme.stations.maxOfOrNull { it.id } ?: 0L,
    scheme.transfers.maxOfOrNull { it.id } ?: 0L,
    scheme.exits.maxOfOrNull { it.id } ?: 0L,
    scheme.hints.maxOfOrNull { it.id } ?: 0L,
) + 1

private fun cleanName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

fun metroSchemeRenamed(scheme: MetroScheme, city: String): MetroScheme {
    val clean = cleanName(city) ?: return scheme
    return if (clean == scheme.city) scheme else scheme.copy(city = clean)
}

/** The defaults for rides and transfers nobody timed; below one minute is refused. */
fun metroSchemeWithDefaults(scheme: MetroScheme, segmentMinutes: Int, transferMinutes: Int): MetroScheme {
    if (segmentMinutes < 1 || transferMinutes < 1) return scheme
    if (segmentMinutes == scheme.defaultSegmentMinutes && transferMinutes == scheme.defaultTransferMinutes) return scheme
    return scheme.copy(defaultSegmentMinutes = segmentMinutes, defaultTransferMinutes = transferMinutes)
}

/**
 * The user's own copy of a library scheme (decided 04.10: a library scheme
 * is not edited in place). Everything inside keeps its id — they are the
 * scheme's own — and the copy remembers where it came from.
 */
fun metroSchemeCopied(library: MetroScheme, newId: Long, source: String): MetroScheme =
    library.copy(id = newId, librarySource = source)

/* ---------------- lines ---------------- */

fun metroLineAdded(scheme: MetroScheme, label: String, name: String, color: Long, ring: Boolean = false): MetroScheme {
    val cleanLabel = cleanName(label) ?: return scheme
    val line = MetroLine(
        id = metroNextId(scheme),
        label = cleanLabel,
        name = cleanName(name).orEmpty(),
        color = color,
        ring = ring,
    )
    return scheme.copy(lines = scheme.lines + line)
}

/**
 * A line's number, name, colour, ring and train. A train of fewer than one
 * car or door is refused. Making a line a ring or not changes its shape, so
 * hints are reconciled.
 */
fun metroLineEdited(
    scheme: MetroScheme,
    lineId: Long,
    label: String,
    name: String,
    color: Long,
    ring: Boolean,
    carCount: Int,
    doorsPerCar: Int,
): MetroScheme {
    val line = scheme.lines.firstOrNull { it.id == lineId } ?: return scheme
    val cleanLabel = cleanName(label) ?: return scheme
    if (carCount < 1 || doorsPerCar < 1) return scheme
    val edited = line.copy(
        label = cleanLabel,
        name = cleanName(name).orEmpty(),
        color = color,
        ring = ring,
        carCount = carCount,
        doorsPerCar = doorsPerCar,
    )
    if (edited == line) return scheme
    return withLine(scheme, edited)
}

/** A line, its stations and everything that hangs off them. */
fun metroLineRemoved(scheme: MetroScheme, lineId: Long): MetroScheme {
    if (scheme.lines.none { it.id == lineId }) return scheme
    return reconciled(
        scheme,
        scheme.copy(
            lines = scheme.lines.filterNot { it.id == lineId },
            stations = scheme.stations.filterNot { it.lineId == lineId },
        ),
    )
}

/** One place up (-1) or down (+1) in the list of lines; the ends do not move. */
fun metroLineMoved(scheme: MetroScheme, lineId: Long, step: Int): MetroScheme {
    val from = scheme.lines.indexOfFirst { it.id == lineId }
    val to = from + step
    if (from < 0 || to !in scheme.lines.indices || to == from) return scheme
    return scheme.copy(lines = scheme.lines.toMutableList().apply { add(to, removeAt(from)) })
}

/* ---------------- stations ---------------- */

/**
 * Where a station sits: on the trunk ([branchId] null) or on a branch, and
 * at which place.
 */
data class MetroTrackPlace(val branchId: Long?, val index: Int)

fun metroTrackPlace(line: MetroLine, stationId: Long): MetroTrackPlace? {
    val i = line.trunk.indexOf(stationId)
    if (i >= 0) return MetroTrackPlace(null, i)
    line.branches.forEach { b ->
        val j = b.stationIds.indexOf(stationId)
        if (j >= 0) return MetroTrackPlace(b.id, j)
    }
    return null
}

private fun MetroLine.track(branchId: Long?): List<Long>? =
    if (branchId == null) trunk else branches.firstOrNull { it.id == branchId }?.stationIds

private fun MetroLine.withTrack(branchId: Long?, stations: List<Long>): MetroLine =
    if (branchId == null) copy(trunk = stations)
    else copy(branches = branches.map { if (it.id == branchId) it.copy(stationIds = stations) else it })

/**
 * A new station on [lineId]'s trunk, or on its branch [branchId], at [index]
 * — 0 is the start, the track's length is the end, and anything beyond is
 * the end too. Inserting between two stations moves their hints for that
 * side onto the new one, and forgets the ride time between them.
 */
fun metroStationAdded(
    scheme: MetroScheme,
    lineId: Long,
    branchId: Long?,
    index: Int,
    name: String,
): MetroScheme {
    val clean = cleanName(name) ?: return scheme
    val line = scheme.lines.firstOrNull { it.id == lineId } ?: return scheme
    val track = line.track(branchId) ?: return scheme
    val station = MetroStation(id = metroNextId(scheme), lineId = lineId, name = clean)
    val at = index.coerceIn(0, track.size)
    val edited = line.withTrack(branchId, track.toMutableList().apply { add(at, station.id) })
    return reconciled(
        scheme,
        scheme.copy(
            lines = scheme.lines.map { if (it.id == lineId) edited else it },
            stations = scheme.stations + station,
        ),
    )
}

fun metroStationRenamed(scheme: MetroScheme, stationId: Long, name: String): MetroScheme {
    val clean = cleanName(name) ?: return scheme
    val station = scheme.stations.firstOrNull { it.id == stationId } ?: return scheme
    if (station.name == clean) return scheme
    return scheme.copy(stations = scheme.stations.map { if (it.id == stationId) it.copy(name = clean) else it })
}

/**
 * A station out of its line. Its neighbours become neighbours. Branches that
 * left from it now leave from the station before it — towards the start of
 * its track, or the next one when it was the first. A branch left with no
 * stations goes. A station with branches and no neighbour on its own track
 * is refused: its branches would have nowhere to leave from.
 */
fun metroStationRemoved(scheme: MetroScheme, stationId: Long): MetroScheme {
    val station = scheme.stations.firstOrNull { it.id == stationId } ?: return scheme
    val line = scheme.lines.firstOrNull { it.id == station.lineId }
    if (line == null) {
        return reconciled(scheme, scheme.copy(stations = scheme.stations.filterNot { it.id == stationId }))
    }
    val place = metroTrackPlace(line, stationId)
    val track = place?.let { line.track(it.branchId) }.orEmpty()

    var edited = line
    if (place != null) {
        val leaving = line.branches.filter { it.fromStationId == stationId }
        if (leaving.isNotEmpty()) {
            val before = when {
                place.index > 0 -> track[place.index - 1]
                place.branchId != null -> line.branches.first { it.id == place.branchId }.fromStationId
                else -> track.getOrNull(1)
            } ?: return scheme
            edited = edited.copy(
                branches = edited.branches.map { if (it.fromStationId == stationId) it.copy(fromStationId = before) else it },
            )
        }
        edited = edited.withTrack(place.branchId, track.filterNot { it == stationId })
        edited = edited.copy(branches = edited.branches.filter { it.stationIds.isNotEmpty() })
    }
    return reconciled(
        scheme,
        scheme.copy(
            lines = scheme.lines.map { if (it.id == line.id) edited else it },
            stations = scheme.stations.filterNot { it.id == stationId },
        ),
    )
}

/**
 * One place along its track (-1 towards the start, +1 towards the end); the
 * ends do not move. Branches leaving from it go with it.
 */
fun metroStationMoved(scheme: MetroScheme, stationId: Long, step: Int): MetroScheme {
    val station = scheme.stations.firstOrNull { it.id == stationId } ?: return scheme
    val line = scheme.lines.firstOrNull { it.id == station.lineId } ?: return scheme
    val place = metroTrackPlace(line, stationId) ?: return scheme
    val track = line.track(place.branchId) ?: return scheme
    val to = place.index + step
    if (to !in track.indices || to == place.index) return scheme
    val moved = track.toMutableList().apply { add(to, removeAt(place.index)) }
    return withLine(scheme, line.withTrack(place.branchId, moved))
}

/* ---------------- branches ---------------- */

/** A new fork from [fromStationId], with its first station [firstStation]. */
fun metroBranchAdded(scheme: MetroScheme, lineId: Long, fromStationId: Long, firstStation: String): MetroScheme {
    val clean = cleanName(firstStation) ?: return scheme
    val line = scheme.lines.firstOrNull { it.id == lineId } ?: return scheme
    if (metroTrackPlace(line, fromStationId) == null) return scheme
    val branchId = metroNextId(scheme)
    val station = MetroStation(id = branchId + 1, lineId = lineId, name = clean)
    val edited = line.copy(branches = line.branches + MetroBranch(branchId, fromStationId, listOf(station.id)))
    return reconciled(
        scheme,
        scheme.copy(
            lines = scheme.lines.map { if (it.id == lineId) edited else it },
            stations = scheme.stations + station,
        ),
    )
}

/** A fork with its stations, and the forks that leave from them. */
fun metroBranchRemoved(scheme: MetroScheme, lineId: Long, branchId: Long): MetroScheme {
    val line = scheme.lines.firstOrNull { it.id == lineId } ?: return scheme
    if (line.branches.none { it.id == branchId }) return scheme
    val goneBranches = mutableSetOf(branchId)
    val goneStations = mutableSetOf<Long>()
    var grew = true
    while (grew) {
        grew = false
        line.branches.forEach { b ->
            if (b.id in goneBranches && goneStations.addAll(b.stationIds)) grew = true
            if (b.id !in goneBranches && b.fromStationId in goneStations) {
                goneBranches += b.id
                grew = true
            }
        }
    }
    val edited = line.copy(branches = line.branches.filterNot { it.id in goneBranches })
    return reconciled(
        scheme,
        scheme.copy(
            lines = scheme.lines.map { if (it.id == lineId) edited else it },
            stations = scheme.stations.filterNot { it.id in goneStations },
        ),
    )
}

/* ---------------- transfers and ride times ---------------- */

/**
 * A walk between two stations of different lines, or its time changed when
 * there already is one; [minutes] null means the scheme's default, and below
 * zero is refused.
 */
fun metroTransferSet(scheme: MetroScheme, a: Long, b: Long, minutes: Int?): MetroScheme {
    if (a == b || (minutes != null && minutes < 0)) return scheme
    val sa = scheme.stations.firstOrNull { it.id == a } ?: return scheme
    val sb = scheme.stations.firstOrNull { it.id == b } ?: return scheme
    if (sa.lineId == sb.lineId) return scheme
    val existing = scheme.transfers.firstOrNull { pairOf(it.aStationId, it.bStationId) == pairOf(a, b) }
    if (existing != null) {
        if (existing.minutes == minutes) return scheme
        return scheme.copy(transfers = scheme.transfers.map { if (it.id == existing.id) it.copy(minutes = minutes) else it })
    }
    return scheme.copy(transfers = scheme.transfers + MetroTransfer(metroNextId(scheme), a, b, minutes))
}

fun metroTransferRemoved(scheme: MetroScheme, transferId: Long): MetroScheme {
    if (scheme.transfers.none { it.id == transferId }) return scheme
    return reconciled(scheme, scheme.copy(transfers = scheme.transfers.filterNot { it.id == transferId }))
}

/**
 * How long the train takes between two neighbouring stations; null goes back
 * to the scheme's default. Stations that are not next to each other, and
 * times below one minute, are refused.
 */
fun metroSegmentTimeSet(scheme: MetroScheme, a: Long, b: Long, minutes: Int?): MetroScheme {
    if (minutes != null && minutes < 1) return scheme
    if (pairOf(a, b) !in adjacentPairs(scheme)) return scheme
    val others = scheme.segmentTimes.filterNot { pairOf(it.aStationId, it.bStationId) == pairOf(a, b) }
    val next = if (minutes == null) others else others + MetroSegmentTime(a, b, minutes)
    return if (next == scheme.segmentTimes) scheme else scheme.copy(segmentTimes = next)
}

/* ---------------- exits ---------------- */

fun metroExitAdded(scheme: MetroScheme, stationId: Long, name: String): MetroScheme {
    val clean = cleanName(name) ?: return scheme
    if (scheme.stations.none { it.id == stationId }) return scheme
    return scheme.copy(exits = scheme.exits + MetroExit(metroNextId(scheme), stationId, clean))
}

fun metroExitRenamed(scheme: MetroScheme, exitId: Long, name: String): MetroScheme {
    val clean = cleanName(name) ?: return scheme
    val exit = scheme.exits.firstOrNull { it.id == exitId } ?: return scheme
    if (exit.name == clean) return scheme
    return scheme.copy(exits = scheme.exits.map { if (it.id == exitId) it.copy(name = clean) else it })
}

/** An exit, and the hints that pointed at it. */
fun metroExitRemoved(scheme: MetroScheme, exitId: Long): MetroScheme {
    if (scheme.exits.none { it.id == exitId }) return scheme
    return reconciled(scheme, scheme.copy(exits = scheme.exits.filterNot { it.id == exitId }))
}

/* ---------------- hints ---------------- */

/**
 * Where to sit, written or rewritten. [hintId] null adds a new one.
 *
 * Refused when it could not be right: [fromStationId] not next to
 * [stationId] on its line, a car or door outside the line's train, a
 * transfer that does not exist from this station, an exit of another one.
 */
fun metroHintSet(
    scheme: MetroScheme,
    hintId: Long?,
    stationId: Long,
    fromStationId: Long,
    car: Int,
    door: Int,
    target: MetroHintTarget,
): MetroScheme {
    val station = scheme.stations.firstOrNull { it.id == stationId } ?: return scheme
    val line = scheme.lines.firstOrNull { it.id == station.lineId } ?: return scheme
    if (car !in 1..line.carCount || door !in 1..line.doorsPerCar) return scheme
    if (fromStationId !in metroSidesOf(line)[stationId]?.values.orEmpty()) return scheme
    val targetOk = when (target) {
        is MetroHintTarget.Transfer -> scheme.transfers.any {
            pairOf(it.aStationId, it.bStationId) == pairOf(stationId, target.toStationId)
        }
        is MetroHintTarget.Exit -> scheme.exits.any { it.id == target.exitId && it.stationId == stationId }
    }
    if (!targetOk) return scheme

    if (hintId == null) {
        val hint = MetroHint(metroNextId(scheme), stationId, fromStationId, car, door, target)
        return scheme.copy(hints = scheme.hints + hint)
    }
    val old = scheme.hints.firstOrNull { it.id == hintId } ?: return scheme
    val new = MetroHint(hintId, stationId, fromStationId, car, door, target)
    if (old == new) return scheme
    return scheme.copy(hints = scheme.hints.map { if (it.id == hintId) new else it })
}

fun metroHintRemoved(scheme: MetroScheme, hintId: Long): MetroScheme {
    if (scheme.hints.none { it.id == hintId }) return scheme
    return scheme.copy(hints = scheme.hints.filterNot { it.id == hintId })
}

/* ---------------- shape ---------------- */

/** A side of a station along its line: back, forward, or into one of the branches leaving from it. */
sealed interface MetroSide {
    data object Back : MetroSide
    data object Forward : MetroSide
    data class IntoBranch(val branchId: Long) : MetroSide
}

/**
 * Every station's neighbours on [line], by side. A branch's first station
 * has the station the branch leaves from as its back; the station a branch
 * leaves from has the branch's first station as a side of its own. A ring
 * of three or more closes its trunk.
 */
fun metroSidesOf(line: MetroLine): Map<Long, Map<MetroSide, Long>> {
    val sides = HashMap<Long, MutableMap<MetroSide, Long>>()
    fun put(station: Long, side: MetroSide, neighbour: Long) {
        if (station != neighbour) sides.getOrPut(station) { LinkedHashMap() }[side] = neighbour
    }

    val trunk = line.trunk
    val ring = line.ring && trunk.size >= 3
    trunk.forEachIndexed { i, s ->
        sides.getOrPut(s) { LinkedHashMap() }
        (trunk.getOrNull(i - 1) ?: if (ring) trunk.last() else null)?.let { put(s, MetroSide.Back, it) }
        (trunk.getOrNull(i + 1) ?: if (ring) trunk.first() else null)?.let { put(s, MetroSide.Forward, it) }
    }
    line.branches.forEach { b ->
        val stations = b.stationIds
        if (stations.isEmpty()) return@forEach
        put(b.fromStationId, MetroSide.IntoBranch(b.id), stations.first())
        stations.forEachIndexed { i, s ->
            sides.getOrPut(s) { LinkedHashMap() }
            put(s, MetroSide.Back, stations.getOrNull(i - 1) ?: b.fromStationId)
            stations.getOrNull(i + 1)?.let { put(s, MetroSide.Forward, it) }
        }
    }
    return sides
}

private fun pairOf(a: Long, b: Long): Pair<Long, Long> = if (a <= b) a to b else b to a

private fun adjacentPairs(scheme: MetroScheme): Set<Pair<Long, Long>> =
    scheme.lines.flatMapTo(HashSet()) { line ->
        metroSidesOf(line).flatMap { (s, bySide) -> bySide.values.map { pairOf(s, it) } }
    }

private fun withLine(scheme: MetroScheme, line: MetroLine): MetroScheme =
    reconciled(scheme, scheme.copy(lines = scheme.lines.map { if (it.id == line.id) line else it }))

/**
 * [after] with what hangs off the shape put right against [before]; see the
 * top of the file.
 */
internal fun reconciled(before: MetroScheme, after: MetroScheme): MetroScheme {
    val lineIds = after.lines.mapTo(HashSet()) { it.id }
    val stations = after.stations.filter { it.lineId in lineIds }.distinctBy { it.id }
    val stationIds = stations.mapTo(HashSet()) { it.id }
    val lines = after.lines.map { tidiedTracks(it, stations) }
    val oldSides = before.lines.fold(HashMap<Long, Map<MetroSide, Long>>()) { acc, l -> acc.apply { putAll(metroSidesOf(l)) } }
    val newSides = lines.fold(HashMap<Long, Map<MetroSide, Long>>()) { acc, l -> acc.apply { putAll(metroSidesOf(l)) } }

    val transfers = after.transfers.filter { it.aStationId in stationIds && it.bStationId in stationIds }
    val exits = after.exits.filter { it.stationId in stationIds }
    val exitIds = exits.mapTo(HashSet()) { it.id }

    val hints = after.hints.mapNotNull { h ->
        if (h.stationId !in stationIds) return@mapNotNull null
        val targetOk = when (val t = h.target) {
            is MetroHintTarget.Transfer -> transfers.any {
                pairOf(it.aStationId, it.bStationId) == pairOf(h.stationId, t.toStationId)
            }
            is MetroHintTarget.Exit -> t.exitId in exitIds
        }
        if (!targetOk) return@mapNotNull null
        val side = oldSides[h.stationId]?.entries?.firstOrNull { it.value == h.fromStationId }?.key
        val from = if (side != null) newSides[h.stationId]?.get(side) else h.fromStationId
        if (from == null || from !in newSides[h.stationId]?.values.orEmpty()) null
        else if (from == h.fromStationId) h else h.copy(fromStationId = from)
    }

    val adjacent = adjacentPairs(after.copy(lines = lines))
    val segmentTimes = after.segmentTimes.filter { pairOf(it.aStationId, it.bStationId) in adjacent }

    return after.copy(
        lines = lines,
        stations = stations,
        transfers = transfers,
        exits = exits,
        hints = hints,
        segmentTimes = segmentTimes,
    )
}

/**
 * A line's tracks holding only its own stations, each once: a station
 * missing from the scheme, or of another line, is left out; a branch left
 * empty, or leaving from a station no longer on the line, goes. Edits never
 * produce such a line; a payload read from disk may.
 */
private fun tidiedTracks(line: MetroLine, stations: List<MetroStation>): MetroLine {
    val own = stations.filter { it.lineId == line.id }.mapTo(HashSet()) { it.id }
    val placed = HashSet<Long>()
    val trunk = line.trunk.filter { it in own && placed.add(it) }
    var branches = line.branches.map { b -> b.copy(stationIds = b.stationIds.filter { it in own && placed.add(it) }) }
        .filter { it.stationIds.isNotEmpty() }
    // A branch may leave from another branch's station: drop, until nothing
    // more goes, the branches that leave from nowhere.
    while (true) {
        val onLine = trunk.toHashSet().apply { branches.forEach { addAll(it.stationIds) } }
        val kept = branches.filter { it.fromStationId in onLine && it.fromStationId !in it.stationIds }
        if (kept.size == branches.size) break
        branches = kept
    }
    if (trunk == line.trunk && branches == line.branches) return line
    return line.copy(trunk = trunk, branches = branches)
}
