package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Where a station's name is written (08.10). Eight sides, counted clockwise
 * on the screen (y goes down) from the right: 0 right, 1 down-right, 2 down,
 * 3 down-left, 4 left, 5 up-left, 6 up, 7 up-right; [METRO_LABEL_HIDDEN] —
 * not written at all. A station that says nothing gets its side by itself,
 * away from the track and links that leave it ([metroAutoLabelSide]).
 */
const val METRO_LABEL_SIDES = 8
const val METRO_LABEL_HIDDEN = 8

/** A tap with the names tool: by itself → right → … → up-right → hidden → by itself. */
fun metroLabelSideCycled(current: Int?): Int? = when {
    current == null -> 0
    current in 0 until METRO_LABEL_SIDES - 1 -> current + 1
    current == METRO_LABEL_SIDES - 1 -> METRO_LABEL_HIDDEN
    else -> null
}

/** [stationId]'s name moved to the next side, as [metroLabelSideCycled] says. */
fun metroStationLabelCycled(scheme: MetroScheme, stationId: Long): MetroScheme =
    scheme.copy(stations = scheme.stations.map {
        if (it.id == stationId) it.copy(labelSide = metroLabelSideCycled(it.labelSide)) else it
    })

/** The directions, in radians on the screen, of what leaves [stationId]: its track (to the first bend) and links. */
fun metroStationDirections(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): List<Double> {
    val p = layout[stationId] ?: return emptyList()
    val towards = ArrayList<MetroPoint>()
    metroTracks(scheme).forEach { t ->
        if (t.a != stationId && t.b != stationId) return@forEach
        val line = metroTrackPolyline(scheme, layout, t) ?: return@forEach
        towards += if (t.a == stationId) line[1] else line[line.size - 2]
    }
    scheme.transfers.forEach { tr ->
        val other = when (stationId) { tr.aStationId -> tr.bStationId; tr.bStationId -> tr.aStationId; else -> null }
        other?.let { layout[it] }?.let { towards += it }
    }
    return towards.filter { it.x != p.x || it.y != p.y }.map { atan2((it.y - p.y).toDouble(), (it.x - p.x).toDouble()) }
}

/** The angle of a side, in radians on the screen. */
fun metroLabelAngle(side: Int): Double = side * PI / 4

// Right first, as before; then left; then above and below; the corners last.
private val PREFERENCE = listOf(0, 4, 6, 2, 1, 7, 5, 3)

/**
 * The side a name goes by itself (08.10): the one furthest from every
 * direction something leaves the station, right when it is free — so a
 * name never lies along its own line.
 */
fun metroAutoLabelSide(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): Int {
    val dirs = metroStationDirections(scheme, layout, stationId)
    if (dirs.isEmpty()) return 0
    fun clearance(side: Int): Double = dirs.minOf { d ->
        val diff = abs(metroLabelAngle(side) - d) % (2 * PI)
        minOf(diff, 2 * PI - diff)
    }
    val best = PREFERENCE.maxOf { clearance(it) }
    return PREFERENCE.first { clearance(it) >= best - 1e-6 }
}

/** The side [stationId]'s name is written on, or [METRO_LABEL_HIDDEN]. */
fun metroLabelSide(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): Int =
    scheme.stations.firstOrNull { it.id == stationId }?.labelSide?.takeIf { it in 0..METRO_LABEL_HIDDEN }
        ?: metroAutoLabelSide(scheme, layout, stationId)

/**
 * The top-left corner of a [width] × [height] name written on [side] of a
 * ring at ([x], [y]), [gap] from its centre: beside it on the right and left,
 * centred over or under it, by its corner on a diagonal.
 */
fun metroLabelTopLeft(x: Float, y: Float, gap: Float, side: Int, width: Float, height: Float): MetroPoint {
    val a = metroLabelAngle(side)
    val ux = kotlin.math.cos(a).toFloat().let { if (abs(it) < 1e-3f) 0f else it }
    val uy = kotlin.math.sin(a).toFloat().let { if (abs(it) < 1e-3f) 0f else it }
    val ax = x + ux * gap
    val ay = y + uy * gap
    val left = when { ux > 0f -> ax; ux < 0f -> ax - width; else -> ax - width / 2 }
    val top = when { uy > 0f -> ay; uy < 0f -> ay - height; else -> ay - height / 2 }
    return MetroPoint(left, top)
}
