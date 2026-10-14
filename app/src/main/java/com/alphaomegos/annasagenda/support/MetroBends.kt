package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroBend
import com.alphaomegos.annasagenda.model.MetroMapPoint
import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Bent track and glued interchanges (08.10, Eduard): the picture's editor
 * lets a track between two neighbours run through points of its own — an
 * elbow at a right angle with a tap, any shape by dragging — and lets the
 * rings of one interchange sit edge to edge, at 8 or 12 points round the
 * circle, to build figures from them.
 *
 * Points are kept in the picture's units, like a placed station, so the
 * first bend freezes the picture as shown (metroSchemeWithLayoutFrozen):
 * a track bent round stations that then moved by themselves would be
 * nonsense.
 */

/** A track between two neighbouring stations, the smaller id first. */
data class MetroTrack(val a: Long, val b: Long)

private fun track(a: Long, b: Long) = if (a <= b) MetroTrack(a, b) else MetroTrack(b, a)

/** Every track of the scheme: neighbours on a line, each pair once. */
fun metroTracks(scheme: MetroScheme): List<MetroTrack> =
    scheme.lines.flatMap { line ->
        metroSidesOf(line).flatMap { (s, sides) -> sides.values.filter { s < it }.map { MetroTrack(s, it) } }
    }.distinct()

/** The points [track] is bent through, from a to b; none for a straight one. */
fun metroBendPoints(scheme: MetroScheme, track: MetroTrack): List<MetroPoint> {
    val bend = scheme.bends.firstOrNull { track(it.aStationId, it.bStationId) == track } ?: return emptyList()
    val pts = bend.points.map { MetroPoint(it.x, it.y) }
    return if (bend.aStationId == track.a) pts else pts.reversed()
}

/** [track] drawn through [points] (from a to b), the picture frozen first; no points makes it straight. */
fun metroSchemeWithBend(scheme: MetroScheme, track: MetroTrack, points: List<MetroPoint>): MetroScheme {
    val frozen = if (points.isEmpty()) scheme else metroSchemeWithLayoutFrozen(scheme)
    val rest = frozen.bends.filter { track(it.aStationId, it.bStationId) != track }
    val bends = if (points.isEmpty()) rest
    else rest + MetroBend(track.a, track.b, points.map { MetroMapPoint(it.x, it.y) })
    return if (bends == frozen.bends) frozen else frozen.copy(bends = bends)
}

/** The whole line drawn for [track]: its two stations and the bend between, from a to b. */
fun metroTrackPolyline(scheme: MetroScheme, layout: Map<Long, MetroPoint>, track: MetroTrack): List<MetroPoint>? {
    val a = layout[track.a] ?: return null
    val b = layout[track.b] ?: return null
    return listOf(a) + metroBendPoints(scheme, track) + b
}

/**
 * A tap on a track (08.10): straight → an elbow along x first → an elbow
 * along y first → straight. A track bent some other way, by hand, goes
 * straight.
 */
fun metroElbowCycled(scheme: MetroScheme, layout: Map<Long, MetroPoint>, track: MetroTrack): MetroScheme {
    val a = layout[track.a] ?: return scheme
    val b = layout[track.b] ?: return scheme
    val xFirst = listOf(MetroPoint(b.x, a.y))
    val yFirst = listOf(MetroPoint(a.x, b.y))
    val next = when (metroBendPoints(scheme, track)) {
        emptyList<MetroPoint>() -> xFirst
        xFirst -> yFirst
        else -> emptyList()
    }
    return metroSchemeWithBend(scheme, track, next)
}

/** A point of a bend: the track and its index along it. */
data class MetroBendHit(val track: MetroTrack, val index: Int)

/** The bend point nearest ([x], [y]) within [radius]. */
fun metroBendPointAt(scheme: MetroScheme, x: Float, y: Float, radius: Float): MetroBendHit? =
    scheme.bends.flatMap { bend ->
        val t = track(bend.aStationId, bend.bStationId)
        metroBendPoints(scheme, t).mapIndexed { i, p -> Triple(MetroBendHit(t, i), p, dist2(p.x - x, p.y - y)) }
    }.filter { it.third <= radius * radius }.minByOrNull { it.third }?.first

/**
 * The track whose drawn line passes nearest ([x], [y]) within [radius], and
 * where along it: the index a point put there would take.
 */
fun metroTrackAt(scheme: MetroScheme, layout: Map<Long, MetroPoint>, x: Float, y: Float, radius: Float): MetroBendHit? {
    var best: MetroBendHit? = null
    var bestD = radius * radius
    metroTracks(scheme).forEach { t ->
        val line = metroTrackPolyline(scheme, layout, t) ?: return@forEach
        for (i in 0 until line.size - 1) {
            val d = segmentDist2(x, y, line[i], line[i + 1])
            if (d <= bestD) { bestD = d; best = MetroBendHit(t, i) }
        }
    }
    return best
}

/** A point put into [hit]'s track at its index, at ([x], [y]). */
fun metroBendPointInserted(scheme: MetroScheme, hit: MetroBendHit, x: Float, y: Float): MetroScheme {
    val pts = metroBendPoints(scheme, hit.track).toMutableList()
    pts.add(hit.index.coerceIn(0, pts.size), MetroPoint(x, y))
    return metroSchemeWithBend(scheme, hit.track, pts)
}

/** A bend point moved to ([x], [y]). */
fun metroBendPointMoved(scheme: MetroScheme, hit: MetroBendHit, x: Float, y: Float): MetroScheme {
    val pts = metroBendPoints(scheme, hit.track).toMutableList()
    if (hit.index !in pts.indices) return scheme
    pts[hit.index] = MetroPoint(x, y)
    return metroSchemeWithBend(scheme, hit.track, pts)
}

/** A bend point taken away; the last one gone, the track is straight again. */
fun metroBendPointRemoved(scheme: MetroScheme, hit: MetroBendHit): MetroScheme {
    val pts = metroBendPoints(scheme, hit.track).toMutableList()
    if (hit.index !in pts.indices) return scheme
    pts.removeAt(hit.index)
    return metroSchemeWithBend(scheme, hit.track, pts)
}

/* ---------------- round corners ---------------- */

/** How far back from a corner the rounding starts, in the picture's units (08.10). */
const val METRO_CORNER_RADIUS = 0.35f

/** A corner rounded: the line runs straight to [from], curves round [corner] to [to]. */
data class MetroCorner(val from: MetroPoint, val corner: MetroPoint, val to: MetroPoint)

/**
 * The corners of a drawn track [points] rounded by [radius] (08.10): each
 * inner point cut back along both its sides, never past half of either, so
 * two corners close together still meet smoothly. A corner where the track
 * goes straight on, or doubles back on itself, is left out.
 */
fun metroRoundedCorners(points: List<MetroPoint>, radius: Float): List<MetroCorner> {
    if (points.size < 3 || radius <= 0f) return emptyList()
    val out = ArrayList<MetroCorner>()
    for (i in 1 until points.size - 1) {
        val p = points[i - 1]; val c = points[i]; val n = points[i + 1]
        val inX = c.x - p.x; val inY = c.y - p.y
        val outX = n.x - c.x; val outY = n.y - c.y
        val inLen = kotlin.math.sqrt(inX * inX + inY * inY)
        val outLen = kotlin.math.sqrt(outX * outX + outY * outY)
        if (inLen == 0f || outLen == 0f) continue
        val cross = inX * outY - inY * outX
        if (kotlin.math.abs(cross) < 1e-6f * inLen * outLen) continue
        val r = minOf(radius, inLen / 2, outLen / 2)
        out += MetroCorner(
            MetroPoint(c.x - inX / inLen * r, c.y - inY / inLen * r),
            c,
            MetroPoint(c.x + outX / outLen * r, c.y + outY / outLen * r),
        )
    }
    return out
}

/* ---------------- glue ---------------- */

/**
 * How far apart the centres of two glued rings are (08.10): a ring's
 * radius in the picture's units is [METRO_STATION_RADIUS], its stroke
 * reaches 0.225 of that further, so two rings touch at this distance.
 */
const val METRO_STATION_RADIUS = 0.13f
const val METRO_GLUE_GAP = 2 * METRO_STATION_RADIUS * 1.225f

/** The gap interchange stations stand apart by when kept together: touching when glued. */
fun metroTogetherGap(scheme: MetroScheme): Float = if (scheme.glue > 0) metroGlueGap(scheme) else METRO_TOGETHER_GAP

/** The glue gap at the scheme's ring size (08.10): bigger rings stand further apart to touch. */
fun metroGlueGap(scheme: MetroScheme): Float = METRO_GLUE_GAP * scheme.stationScale

/** How wide a line's edging is on each side, as a part of the line's width (08.10). */
const val METRO_CASING = 0.35f

/** How much larger or smaller names, track and rings can be made (08.10). */
const val METRO_SCALE_MIN = 0.5f
const val METRO_SCALE_MAX = 2.5f

/**
 * Where a ring dropped at [target] sits glued to the ring at [anchor]:
 * touching it, at the nearest of [points] places round it (8 or 12).
 */
fun metroGluedPoint(target: MetroPoint, anchor: MetroPoint, points: Int, gap: Float = METRO_GLUE_GAP): MetroPoint {
    val step = 2 * PI / points
    val angle = atan2((target.y - anchor.y).toDouble(), (target.x - anchor.x).toDouble())
    val snapped = round(angle / step) * step
    return MetroPoint(
        anchor.x + (gap * cos(snapped)).toFloat() + 0f,
        anchor.y + (gap * sin(snapped)).toFloat() + 0f,
    )
}

/**
 * Where a station dropped at [p] alone lands (08.10): with glue on, near
 * another station of its own interchange (within three gaps) it sits glued
 * to the nearest; else where it was dropped. [positions] is the picture.
 */
fun metroAloneDropPoint(scheme: MetroScheme, positions: Map<Long, MetroPoint>, stationId: Long, p: MetroPoint): MetroPoint {
    if (scheme.glue <= 0) return p
    val group = metroTransferGroups(scheme).firstOrNull { stationId in it }.orEmpty() - stationId
    val anchor = group.mapNotNull { positions[it] }.minByOrNull { dist2(it.x - p.x, it.y - p.y) } ?: return p
    val gap = metroGlueGap(scheme)
    val reach = 3 * gap
    return if (dist2(anchor.x - p.x, anchor.y - p.y) <= reach * reach) metroGluedPoint(p, anchor, scheme.glue, gap) else p
}

/**
 * One station moved alone (08.10), glued as [metroAloneDropPoint] says; the
 * rest of the picture frozen where it was.
 */
fun metroStationPlacedAlone(scheme: MetroScheme, stationId: Long, x: Float, y: Float, unseen: Set<Long> = emptySet()): MetroScheme {
    if (scheme.stations.none { it.id == stationId } || !x.isFinite() || !y.isFinite()) return scheme
    val frozen = metroSchemeWithLayoutFrozen(scheme)
    // Glued only to a station in sight (14.10): not to one of a hidden line.
    val positions = frozen.stations.filter { it.mapX != null && it.mapY != null && it.id !in unseen }.associate { it.id to MetroPoint(it.mapX!!, it.mapY!!) }
    val at = metroAloneDropPoint(frozen, positions, stationId, MetroPoint(x, y))
    return frozen.copy(stations = frozen.stations.map { if (it.id == stationId) it.copy(mapX = at.x, mapY = at.y) else it })
}

/* ---------------- one name per interchange ---------------- */

/**
 * The stations whose names the picture writes (08.10): every one, or with
 * [MetroScheme.labelsOncePerInterchange] one for each name within an
 * interchange — the three Kuntsevskayas once; two Taganskayas and
 * Marksistskaya as "Taganskaya" and "Marksistskaya". The one written is the
 * rightmost of its name, the name going off to its right.
 */
fun metroLabelledStations(scheme: MetroScheme, layout: Map<Long, MetroPoint>): Set<Long> {
    val all = scheme.stations.map { it.id }.toSet()
    if (!scheme.labelsOncePerInterchange) return all
    val skip = HashSet<Long>()
    metroTransferGroups(scheme).forEach { group ->
        scheme.stations.filter { it.id in group }.groupBy { metroNameKey(it.name) }.values.forEach { same ->
            val keep = same.maxByOrNull { layout[it.id]?.x ?: Float.NEGATIVE_INFINITY } ?: return@forEach
            same.filter { it.id != keep.id }.forEach { skip += it.id }
        }
    }
    return all - skip
}

private fun dist2(dx: Float, dy: Float) = dx * dx + dy * dy

private fun segmentDist2(x: Float, y: Float, a: MetroPoint, b: MetroPoint): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
    val px = a.x + t * dx; val py = a.y + t * dy
    return dist2(x - px, y - py)
}
