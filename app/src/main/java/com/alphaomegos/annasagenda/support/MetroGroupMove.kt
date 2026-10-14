package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroBend
import com.alphaomegos.annasagenda.model.MetroMapPoint
import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * Many stations moved at once (08.10): the editor's selection — stations
 * tapped one by one, or whole lines — dragged by any of them. Track bent
 * between two stations both moving moves with them; track bent towards a
 * station left behind keeps its points.
 */

/** How far an arrow moves the selection without the grid (08.10): a tenth of a step. */
const val METRO_NUDGE_STEP = 0.1f

/** [ids] with [stationId] added, or taken away if it was there. */
fun metroSelectionToggled(ids: Set<Long>, stationId: Long): Set<Long> =
    if (stationId in ids) ids - stationId else ids + stationId

/** The stations of [layout] inside the box between two corners, either way round (14.10): what a box drawn round them takes. */
fun metroStationsInside(layout: Map<Long, MetroPoint>, a: MetroPoint, b: MetroPoint): Set<Long> {
    val l = minOf(a.x, b.x); val r = maxOf(a.x, b.x)
    val t = minOf(a.y, b.y); val d = maxOf(a.y, b.y)
    return layout.filterValues { it.x in l..r && it.y in t..d }.keys
}

/** [ids] grown to every station of each line one of them is on. */
fun metroSelectionWholeLines(scheme: MetroScheme, ids: Set<Long>): Set<Long> {
    val lines = scheme.stations.filter { it.id in ids }.map { it.lineId }.toSet()
    return ids + scheme.stations.filter { it.lineId in lines }.map { it.id }
}

/** The stations of [ids] moved by ([dx], [dy]) with the track bent between them; the picture frozen first. */
fun metroStationsShifted(scheme: MetroScheme, ids: Set<Long>, dx: Float, dy: Float): MetroScheme {
    if (ids.isEmpty() || !dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return scheme
    val frozen = metroSchemeWithLayoutFrozen(scheme)
    return frozen.copy(
        stations = frozen.stations.map {
            if (it.id in ids) it.copy(mapX = (it.mapX ?: 0f) + dx, mapY = (it.mapY ?: 0f) + dy) else it
        },
        bends = frozen.bends.map { b ->
            if (b.aStationId in ids && b.bStationId in ids) {
                MetroBend(b.aStationId, b.bStationId, b.points.map { MetroMapPoint(it.x + dx, it.y + dy) })
            } else b
        },
    )
}

/** The selection dragged by [stationId] to ([x], [y]): all of it moved as far. */
fun metroSelectionPlaced(scheme: MetroScheme, ids: Set<Long>, stationId: Long, x: Float, y: Float): MetroScheme {
    val from = metroLayout(scheme)[stationId] ?: return scheme
    return metroStationsShifted(scheme, ids + stationId, x - from.x, y - from.y)
}

/* ---------------- lining up (08.10) ---------------- */

/** The stations of [ids] as placed now, the picture frozen; fewer than two placed, none. */
private fun placedOf(scheme: MetroScheme, ids: Set<Long>): Pair<MetroScheme, Map<Long, MetroPoint>> {
    val frozen = metroSchemeWithLayoutFrozen(scheme)
    val at = frozen.stations.filter { it.id in ids && it.mapX != null && it.mapY != null }
        .associate { it.id to MetroPoint(it.mapX!!, it.mapY!!) }
    return frozen to at
}

private fun withPlaces(scheme: MetroScheme, places: Map<Long, MetroPoint>): MetroScheme =
    scheme.copy(stations = scheme.stations.map { st -> places[st.id]?.let { st.copy(mapX = it.x, mapY = it.y) } ?: st })

/**
 * The selection put in a row ([across] true: one y for all) or a column
 * (one x): the middle of where they stand, on the grid if [grid] is given.
 */
fun metroSelectionLinedUp(scheme: MetroScheme, ids: Set<Long>, across: Boolean, grid: Float? = null): MetroScheme {
    val (frozen, at) = placedOf(scheme, ids)
    if (at.size < 2) return scheme
    val mid = at.values.map { if (across) it.y else it.x }.average().toFloat()
    val v = grid?.takeIf { it > 0f }?.let { kotlin.math.round(mid / it) * it } ?: mid
    return withPlaces(frozen, at.mapValues { (_, p) -> if (across) MetroPoint(p.x, v) else MetroPoint(v, p.y) })
}

/**
 * The selection spaced evenly (08.10): ordered along the way they spread
 * most, the first and the last stay, the rest go at equal steps on the
 * straight line between them.
 */
fun metroSelectionSpaced(scheme: MetroScheme, ids: Set<Long>): MetroScheme {
    val (frozen, at) = placedOf(scheme, ids)
    if (at.size < 3) return scheme
    val xs = at.values.map { it.x }; val ys = at.values.map { it.y }
    val alongX = (xs.max() - xs.min()) >= (ys.max() - ys.min())
    val order = at.entries.sortedWith(compareBy({ if (alongX) it.value.x else it.value.y }, { it.key }))
    val first = order.first().value; val last = order.last().value
    val n = order.size - 1
    val places = order.mapIndexed { i, e ->
        e.key to MetroPoint(first.x + (last.x - first.x) * i / n, first.y + (last.y - first.y) * i / n)
    }.toMap()
    return withPlaces(frozen, places)
}

/* ---------------- turning and mirroring (08.10) ---------------- */

/** How the selection is turned over: a quarter of 45°-steps clockwise, or mirrored. */
enum class MetroTurn { CLOCKWISE_45, MIRROR_ACROSS, MIRROR_UPDOWN }

/**
 * The selection turned by [turn] about its middle (08.10), with the track
 * bent between its stations. Clockwise is on the screen, where y goes down.
 */
fun metroSelectionTurned(scheme: MetroScheme, ids: Set<Long>, turn: MetroTurn): MetroScheme {
    val (frozen, at) = placedOf(scheme, ids)
    if (at.size < 2) return scheme
    val cx = at.values.map { it.x }.average().toFloat()
    val cy = at.values.map { it.y }.average().toFloat()
    val c = (kotlin.math.sqrt(2.0) / 2).toFloat()
    fun move(x: Float, y: Float): MetroPoint {
        val dx = x - cx; val dy = y - cy
        return when (turn) {
            MetroTurn.CLOCKWISE_45 -> MetroPoint(cx + c * (dx - dy), cy + c * (dx + dy))
            MetroTurn.MIRROR_ACROSS -> MetroPoint(cx - dx, y)
            MetroTurn.MIRROR_UPDOWN -> MetroPoint(x, cy - dy)
        }
    }
    val moved = withPlaces(frozen, at.mapValues { (_, p) -> move(p.x, p.y) })
    return moved.copy(bends = moved.bends.map { b ->
        if (b.aStationId in at && b.bStationId in at) {
            MetroBend(b.aStationId, b.bStationId, b.points.map { val q = move(it.x, it.y); MetroMapPoint(q.x, q.y) })
        } else b
    })
}
