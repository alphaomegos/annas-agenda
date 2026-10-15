package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where each station goes on the picture of the map (05.10: "automatically
 * first, then put right by hand").
 *
 * A station the user placed keeps its place ([MetroStation.mapX], mapY);
 * every other one is laid out around them: lines start as spokes from the
 * middle — a ring as a circle, a branch turning off its fork — and then a
 * few hundred rounds of springs pull neighbours on a line to one step apart
 * and the two ends of a transfer close together, while every station pushes
 * away the ones that crowd it. The same scheme always gives the same
 * picture: nothing here is random.
 *
 * With [MetroScheme.transfersTogether] (06.10) the stations of one transfer
 * are held side by side, [METRO_TOGETHER_GAP] apart, and move as one body:
 * a transfer with a station placed by hand stays where that one is, the
 * others of it beside it.
 *
 * Units: one step between neighbouring stations is [METRO_LAYOUT_STEP].
 */
const val METRO_LAYOUT_STEP = 1f

/** The grid of the picture's editor (06.10): two nodes to a step. */
const val METRO_GRID_STEP = 0.5f

/** How many grid nodes to a step the editor offers (15.10): one, two — as it was — or four. */
val METRO_GRID_DIVISIONS = listOf(1, 2, 4)

/** The grid step of [scheme] (15.10), in the picture's units; one it does not offer is read as two to a step. */
fun metroGridStep(scheme: MetroScheme): Float =
    METRO_LAYOUT_STEP / (scheme.gridDivisions.takeIf { it in METRO_GRID_DIVISIONS } ?: 2)

/** How far apart the stations of a transfer kept together stand: one grid node, so they all land on nodes. */
const val METRO_TOGETHER_GAP = METRO_GRID_STEP

data class MetroPoint(val x: Float, val y: Float)

fun metroLayout(scheme: MetroScheme, rounds: Int = 300): Map<Long, MetroPoint> {
    val ids = scheme.stations.map { it.id }
    if (ids.isEmpty()) return emptyMap()
    val index = ids.withIndex().associate { it.value to it.index }
    val n = ids.size
    val x = FloatArray(n)
    val y = FloatArray(n)
    val fixed = BooleanArray(n)
    val placed = BooleanArray(n)

    scheme.stations.forEachIndexed { i, s ->
        if (s.mapX != null && s.mapY != null) {
            x[i] = s.mapX; y[i] = s.mapY; fixed[i] = true; placed[i] = true
        }
    }

    // Edges: neighbours on a line (one step), and transfers (close).
    val lineEdges = mutableListOf<Pair<Int, Int>>()
    scheme.lines.forEach { line ->
        metroSidesOf(line).forEach { (s, sides) ->
            val a = index[s] ?: return@forEach
            sides.values.forEach { t -> val b = index[t] ?: return@forEach; if (a < b) lineEdges += a to b }
        }
    }
    val transferEdges = scheme.transfers.mapNotNull { t ->
        val a = index[t.aStationId]; val b = index[t.bStationId]
        if (a == null || b == null || a == b) null else a to b
    }

    // A first guess: every line a spoke (or a circle), branches turning off.
    val lineCount = max(1, scheme.lines.size)
    scheme.lines.forEachIndexed { li, line ->
        val angle = 2 * PI * li / lineCount
        val dx = cos(angle).toFloat(); val dy = sin(angle).toFloat()
        val trunk = line.trunk.mapNotNull { index[it] }
        if (line.ring && trunk.size >= 3) {
            val r = trunk.size * METRO_LAYOUT_STEP / (2 * PI).toFloat()
            trunk.forEachIndexed { k, i ->
                if (placed[i]) return@forEachIndexed
                val a = 2 * PI * k / trunk.size
                x[i] = (r * cos(a)).toFloat(); y[i] = (r * sin(a)).toFloat(); placed[i] = true
            }
        } else {
            // Shifted sideways a little per line, so two spokes never start on one point.
            val side = 0.15f * li
            trunk.forEachIndexed { k, i ->
                if (placed[i]) return@forEachIndexed
                val t = (k - (trunk.size - 1) / 2f) * METRO_LAYOUT_STEP
                x[i] = dx * t - dy * side; y[i] = dy * t + dx * side; placed[i] = true
            }
        }
        line.branches.forEachIndexed { bi, branch ->
            val from = index[branch.fromStationId] ?: return@forEachIndexed
            val turn = angle + (if (bi % 2 == 0) 0.7 else -0.7) * (1 + bi / 2)
            val bx = cos(turn).toFloat(); val by = sin(turn).toFloat()
            branch.stationIds.mapNotNull { index[it] }.forEachIndexed { k, i ->
                if (placed[i]) return@forEachIndexed
                x[i] = x[from] + bx * (k + 1) * METRO_LAYOUT_STEP
                y[i] = y[from] + by * (k + 1) * METRO_LAYOUT_STEP
                placed[i] = true
            }
        }
    }
    // A station on no track: beside the first placed one, stepped so none share a point.
    var spare = 0
    for (i in 0 until n) if (!placed[i]) {
        spare++
        x[i] = 0.3f * spare; y[i] = -0.3f * spare; placed[i] = true
    }

    // Transfers kept together: each one a body. One with a station placed by
    // hand is anchored there, the rest of it on the free places beside it;
    // one with none is set round where its stations were guessed to be.
    val together = scheme.transfersTogether
    val gap = metroTogetherGap(scheme)
    val groupOf = IntArray(n) { -1 }
    val bodies = mutableListOf<IntArray>()
    if (together) {
        metroTransferGroups(scheme).forEach { group ->
            val members = group.mapNotNull { index[it] }
            if (members.size < 2) return@forEach
            val anchor = members.firstOrNull { fixed[it] }
            if (anchor != null) {
                var slot = 0
                members.filter { !fixed[it] }.forEach { i ->
                    while (true) {
                        val (sx, sy) = metroTogetherSlot(slot++, gap)
                        val px = x[anchor] + sx; val py = y[anchor] + sy
                        if (members.none { fixed[it] && hypot2(x[it] - px, y[it] - py) < 0.01f }) {
                            x[i] = px; y[i] = py; break
                        }
                    }
                }
                members.forEach { fixed[it] = true }
            } else {
                val slots = members.indices.map { metroTogetherSlot(it, gap) }
                val ox = members.map { x[it] }.average().toFloat() - slots.map { it.first }.average().toFloat()
                val oy = members.map { y[it] }.average().toFloat() - slots.map { it.second }.average().toFloat()
                members.forEachIndexed { k, i -> x[i] = ox + slots[k].first; y[i] = oy + slots[k].second; groupOf[i] = bodies.size }
                bodies += members.toIntArray()
            }
        }
    }

    if (fixed.all { it }) return ids.indices.associate { ids[it] to MetroPoint(x[it], y[it]) }

    val fx = FloatArray(n)
    val fy = FloatArray(n)
    val transferLength = 0.35f * METRO_LAYOUT_STEP
    val crowd = 2.5f * METRO_LAYOUT_STEP
    for (round in 0 until rounds) {
        fx.fill(0f); fy.fill(0f)
        fun spring(a: Int, b: Int, length: Float, strength: Float) {
            val dx = x[b] - x[a]; val dy = y[b] - y[a]
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val f = strength * (d - length) / d
            fx[a] += f * dx; fy[a] += f * dy
            fx[b] -= f * dx; fy[b] -= f * dy
        }
        lineEdges.forEach { (a, b) -> spring(a, b, METRO_LAYOUT_STEP, 0.5f) }
        if (!together) transferEdges.forEach { (a, b) -> spring(a, b, transferLength, 0.8f) }
        for (a in 0 until n) for (b in a + 1 until n) {
            val dx = x[b] - x[a]; val dy = y[b] - y[a]
            val d2 = dx * dx + dy * dy
            if (d2 > crowd * crowd) continue
            val d = sqrt(d2).coerceAtLeast(0.05f)
            val f = 0.08f / (d * d)
            fx[a] -= f * dx / d; fy[a] -= f * dy / d
            fx[b] += f * dx / d; fy[b] += f * dy / d
        }
        // Cooling: big moves first, then settling.
        val limit = 0.5f * (1f - round.toFloat() / rounds) + 0.01f
        for (i in 0 until n) {
            if (fixed[i] || groupOf[i] >= 0) continue
            val len = sqrt(fx[i] * fx[i] + fy[i] * fy[i])
            val k = if (len > limit) limit / len else 1f
            x[i] += fx[i] * k; y[i] += fy[i] * k
        }
        // A body moves by what pulls on all of it, every station alike.
        bodies.forEach { body ->
            var bx = 0f; var by = 0f
            body.forEach { bx += fx[it]; by += fy[it] }
            bx /= body.size; by /= body.size
            val len = sqrt(bx * bx + by * by)
            val k = if (len > limit) limit / len else 1f
            body.forEach { x[it] += bx * k; y[it] += by * k }
        }
    }
    return ids.indices.associate { ids[it] to MetroPoint(x[it], y[it]) }
}

/** Whether every station has its place by hand (14.10): then the layout is read off, with nothing to work out. */
fun metroIsPlaced(scheme: MetroScheme): Boolean = scheme.stations.all { it.mapX != null && it.mapY != null }

/** The smallest box around [points], as left, top, right, bottom; a unit box around nothing. */
fun metroLayoutBounds(points: Collection<MetroPoint>): FloatArray {
    if (points.isEmpty()) return floatArrayOf(-1f, -1f, 1f, 1f)
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) }
    return floatArrayOf(l, t, r, b)
}

/**
 * Every station given the place the picture shows it in now, so that moving
 * one by hand moves only that one: without this, the others — laid out
 * around whatever is placed — would all shift the moment one became placed.
 */
fun metroSchemeWithLayoutFrozen(scheme: MetroScheme): MetroScheme {
    if (scheme.stations.all { it.mapX != null && it.mapY != null }) return scheme
    val layout = metroLayout(scheme)
    return scheme.copy(stations = scheme.stations.map { s ->
        if (s.mapX != null && s.mapY != null) s
        else layout[s.id]?.let { s.copy(mapX = it.x, mapY = it.y) } ?: s
    })
}

/**
 * One station put where the user dragged it; the rest frozen where they were.
 * With transfers kept together, the other stations of its transfer come
 * along, moved just as far.
 */
fun metroStationPlaced(scheme: MetroScheme, stationId: Long, x: Float, y: Float, keep: Set<Long> = emptySet()): MetroScheme {
    if (scheme.stations.none { it.id == stationId } || !x.isFinite() || !y.isFinite()) return scheme
    val frozen = metroSchemeWithLayoutFrozen(scheme)
    val from = frozen.stations.first { it.id == stationId }
    val dx = x - (from.mapX ?: x)
    val dy = y - (from.mapY ?: y)
    // Stations of hidden or locked lines (14.10) stay, even of its own transfer.
    val moving = metroStationsMovingWith(frozen, stationId) - keep
    return frozen.copy(stations = frozen.stations.map {
        when {
            it.id == stationId -> it.copy(mapX = x, mapY = y)
            it.id in moving -> it.copy(mapX = (it.mapX ?: 0f) + dx, mapY = (it.mapY ?: 0f) + dy)
            else -> it
        }
    })
}

/** The stations a drag of [stationId] moves: its whole transfer when they are kept together, else itself. */
fun metroStationsMovingWith(scheme: MetroScheme, stationId: Long): Set<Long> =
    if (!scheme.transfersTogether) setOf(stationId)
    else metroTransferGroups(scheme).firstOrNull { stationId in it }?.toSet() ?: setOf(stationId)

/**
 * The stations joined by transfers, each set of two or more as one list
 * (sorted by id, the lists by their first): a transfer's stations, and
 * further ones joined to those, as at a three-line interchange.
 */
fun metroTransferGroups(scheme: MetroScheme): List<List<Long>> {
    val ids = scheme.stations.mapTo(HashSet()) { it.id }
    val parent = HashMap<Long, Long>()
    fun root(a: Long): Long {
        var r = a
        while (parent[r] != null && parent[r] != r) r = parent.getValue(r)
        return r
    }
    scheme.transfers.forEach { t ->
        if (t.aStationId !in ids || t.bStationId !in ids || t.aStationId == t.bStationId) return@forEach
        parent.putIfAbsent(t.aStationId, t.aStationId)
        parent.putIfAbsent(t.bStationId, t.bStationId)
        val ra = root(t.aStationId); val rb = root(t.bStationId)
        if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
    }
    return parent.keys.groupBy { root(it) }.values
        .map { it.sorted() }
        .filter { it.size >= 2 }
        .sortedBy { it.first() }
}

/**
 * Transfers kept together, or let go (06.10). Turning it on for a picture
 * placed by hand freezes it as shown, then brings each transfer's stations
 * beside its first one; letting go leaves every station where it stands.
 */
fun metroTransfersTogether(scheme: MetroScheme, on: Boolean): MetroScheme {
    if (scheme.transfersTogether == on) return scheme
    if (!on || scheme.stations.none { it.mapX != null && it.mapY != null }) return scheme.copy(transfersTogether = on)
    val frozen = metroSchemeWithLayoutFrozen(scheme)
    val at = HashMap<Long, Pair<Float, Float>>()
    val byId = frozen.stations.associateBy { it.id }
    metroTransferGroups(frozen).forEach { group ->
        val anchor = byId.getValue(group.first())
        val ax = anchor.mapX ?: 0f; val ay = anchor.mapY ?: 0f
        group.forEachIndexed { k, id -> val (sx, sy) = metroTogetherSlot(k, metroTogetherGap(scheme)); at[id] = ax + sx to ay + sy }
    }
    return frozen.copy(
        transfersTogether = true,
        stations = frozen.stations.map { s -> at[s.id]?.let { s.copy(mapX = it.first, mapY = it.second) } ?: s },
    )
}

/** The grid node nearest [p]. */
fun metroSnapped(p: MetroPoint, step: Float = METRO_GRID_STEP): MetroPoint =
    // "+ 0f" turns -0.0 into 0.0, so a point snapped to the axis equals the axis.
    MetroPoint(round(p.x / step) * step + 0f, round(p.y / step) * step + 0f)

/** Where the [k]-th station of a transfer kept together stands from the first: two abreast, then the next row. */
internal fun metroTogetherSlot(k: Int, gap: Float = METRO_TOGETHER_GAP): Pair<Float, Float> =
    (k % 2) * gap to (k / 2) * gap

private fun hypot2(dx: Float, dy: Float) = dx * dx + dy * dy

/** Every place forgotten: the picture is laid out afresh. */
fun metroLayoutCleared(scheme: MetroScheme): MetroScheme {
    if (scheme.stations.all { it.mapX == null && it.mapY == null }) return scheme
    return scheme.copy(stations = scheme.stations.map { it.copy(mapX = null, mapY = null) })
}

/**
 * The station nearest ([x], [y]) within [radius], for a tap on the picture;
 * null when there is none that close.
 */
fun metroStationAt(layout: Map<Long, MetroPoint>, x: Float, y: Float, radius: Float): Long? =
    layout.entries
        .minByOrNull { (_, p) -> (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) }
        ?.takeIf { (_, p) -> (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) <= radius * radius }
        ?.key
