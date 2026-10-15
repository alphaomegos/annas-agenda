package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/**
 * What the picture needs to know about each station, worked out in one pass
 * (14.10): the ways its own track leaves it (towards the first bend, or the
 * neighbour) and the ways its links leave it, as angles on the screen.
 *
 * Asked station by station, every station went through every track and
 * every bend of the scheme again — a few hundred times over on each drop in
 * a Moscow-sized scheme, on the main thread.
 */
class MetroPictureFacts internal constructor(
    val trackDirections: Map<Long, List<Double>>,
    val linkDirections: Map<Long, List<Double>>,
) {
    /** Every way something leaves [stationId]. */
    fun directions(stationId: Long): List<Double> =
        trackDirections[stationId].orEmpty() + linkDirections[stationId].orEmpty()
}

fun metroPictureFacts(scheme: MetroScheme, layout: Map<Long, MetroPoint>): MetroPictureFacts {
    // Each bend by its track, its points from the track's a to its b.
    val bends = HashMap<MetroTrack, List<MetroPoint>>()
    scheme.bends.forEach { b ->
        val pts = b.points.map { MetroPoint(it.x, it.y) }
        if (b.aStationId <= b.bStationId) bends[MetroTrack(b.aStationId, b.bStationId)] = pts
        else bends[MetroTrack(b.bStationId, b.aStationId)] = pts.reversed()
    }
    val tracks = HashMap<Long, MutableList<Double>>()
    val links = HashMap<Long, MutableList<Double>>()
    fun add(into: HashMap<Long, MutableList<Double>>, id: Long, from: MetroPoint, to: MetroPoint) {
        if (from.x == to.x && from.y == to.y) return
        into.getOrPut(id) { ArrayList(2) } += atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    }
    metroTracks(scheme).forEach { t ->
        val a = layout[t.a] ?: return@forEach
        val b = layout[t.b] ?: return@forEach
        val pts = bends[t].orEmpty()
        add(tracks, t.a, a, pts.firstOrNull() ?: b)
        add(tracks, t.b, b, pts.lastOrNull() ?: a)
    }
    scheme.transfers.forEach { tr ->
        val a = layout[tr.aStationId] ?: return@forEach
        val b = layout[tr.bStationId] ?: return@forEach
        add(links, tr.aStationId, a, b)
        add(links, tr.bStationId, b, a)
    }
    return MetroPictureFacts(tracks, links)
}

/** The side every station's name goes on (14.10): set by hand, or by itself as [metroAutoLabelSide] says; hidden ones too. */
fun metroLabelSides(scheme: MetroScheme, layout: Map<Long, MetroPoint>, facts: MetroPictureFacts = metroPictureFacts(scheme, layout)): Map<Long, Int> {
    // Neighbours looked up in squares as wide as the reach, not among every station.
    val reach = METRO_LABEL_CROWD_REACH
    val cells = HashMap<Long, MutableList<Pair<Long, MetroPoint>>>()
    fun cell(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    layout.forEach { (id, p) -> cells.getOrPut(cell(floor(p.x / reach).toInt(), floor(p.y / reach).toInt())) { ArrayList() } += id to p }
    return scheme.stations.associate { st ->
        val byHand = st.labelSide?.takeIf { it in 0 until METRO_LABEL_SIDES }
        st.id to (byHand ?: if (!scheme.labelsAuto) 0 else run {
            val p = layout[st.id]
            metroAutoSideOf(facts.directions(st.id), scheme.labelsAvoidCrowd) {
                if (p == null) emptyList()
                else {
                    val cx = floor(p.x / reach).toInt(); val cy = floor(p.y / reach).toInt()
                    (cx - 1..cx + 1).flatMap { x -> (cy - 1..cy + 1).flatMap { y -> cells[cell(x, y)].orEmpty() } }
                        .filter { (id, q) -> id != st.id && metroNear(p, q, reach) }
                        .map { (_, q) -> atan2((q.y - p.y).toDouble(), (q.x - p.x).toDouble()) }
                }
            }
        })
    }
}

/** Which way every tick points (14.10), as [metroTickAngle] says, for every station on a track. */
fun metroTickAngles(scheme: MetroScheme, sides: Map<Long, Int>, facts: MetroPictureFacts): Map<Long, Double> =
    scheme.stations.mapNotNull { st ->
        metroTickAngleOf(facts.trackDirections[st.id].orEmpty(), sides[st.id] ?: 0)?.let { st.id to it }
    }.toMap()

/** The way out past the end at every terminus (14.10), as [metroTerminusOutward] says. */
fun metroTerminusOutwards(facts: MetroPictureFacts): Map<Long, Double> =
    facts.trackDirections.filterValues { it.size == 1 }.mapValues { it.value[0] + PI }

internal fun metroNear(p: MetroPoint, q: MetroPoint, reach: Float): Boolean {
    val dx = q.x - p.x; val dy = q.y - p.y
    return (dx != 0f || dy != 0f) && dx * dx + dy * dy <= reach * reach
}

internal fun metroAngleGap(a: Double, b: Double): Double {
    val d = abs(a - b) % (2 * PI)
    return minOf(d, 2 * PI - d)
}
