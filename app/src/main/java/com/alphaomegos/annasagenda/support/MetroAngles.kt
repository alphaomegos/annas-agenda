package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Angles of 45° (08.10): the rule of every schematic metro map since Harry
 * Beck — track runs only straight across, straight up or at 45°. With the
 * scheme's tick on, a dragged station is pulled onto those directions from
 * its neighbours: onto a point square with both of them where there is one
 * close by, else onto the nearest direction from one of them.
 */

private val DIRS = run {
    val d = (sqrt(2.0) / 2).toFloat()
    listOf(1f to 0f, 0f to 1f, d to d, d to -d)
}

/**
 * Where a station dropped at [p] goes with angles of 45°: the point within
 * [tolerance] of [p] that lies on an octilinear line through two of
 * [anchors] if there is one, else on one through the nearest of them,
 * else [p] itself.
 */
fun metroOctilinearSnap(p: MetroPoint, anchors: List<MetroPoint>, tolerance: Float): MetroPoint {
    if (anchors.isEmpty()) return p
    val tol2 = tolerance * tolerance
    fun d2(q: MetroPoint) = (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y)

    // Square with two neighbours at once: where their lines cross.
    var best: MetroPoint? = null
    var bestD = tol2
    for (i in anchors.indices) for (j in i + 1 until anchors.size) {
        val a = anchors[i]; val b = anchors[j]
        for ((ax, ay) in DIRS) for ((bx, by) in DIRS) {
            val det = ax * by - ay * bx
            if (abs(det) < 1e-6f) continue
            val t = ((b.x - a.x) * by - (b.y - a.y) * bx) / det
            val q = MetroPoint(a.x + t * ax, a.y + t * ay)
            val d = d2(q)
            if (d <= bestD) { bestD = d; best = q }
        }
    }
    best?.let { return it }

    // Square with one: the nearest line through a neighbour.
    bestD = tol2
    for (a in anchors) for ((dx, dy) in DIRS) {
        val t = (p.x - a.x) * dx + (p.y - a.y) * dy
        val q = MetroPoint(a.x + t * dx, a.y + t * dy)
        val d = d2(q)
        if (d <= bestD) { bestD = d; best = q }
    }
    return best ?: p
}

/**
 * What a station's track is squared against: each neighbour on its line —
 * or, when the track to it is bent, the bend point next to the station —
 * leaving out the stations in [moving], which move with it.
 */
fun metroAngleAnchors(
    scheme: MetroScheme,
    layout: Map<Long, MetroPoint>,
    stationId: Long,
    moving: Set<Long> = setOf(stationId),
): List<MetroPoint> {
    val lineId = scheme.stations.firstOrNull { it.id == stationId }?.lineId ?: return emptyList()
    val line = scheme.lines.firstOrNull { it.id == lineId } ?: return emptyList()
    val neighbours = metroSidesOf(line)[stationId]?.values.orEmpty()
    return neighbours.mapNotNull { n ->
        val bends = metroBendPoints(scheme, if (stationId < n) MetroTrack(stationId, n) else MetroTrack(n, stationId))
        when {
            bends.isNotEmpty() -> if (stationId < n) bends.first() else bends.last()
            n in moving -> null
            else -> layout[n]
        }
    }
}
