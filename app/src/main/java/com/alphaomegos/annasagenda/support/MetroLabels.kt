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
fun metroStationDirections(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): List<Double> =
    metroPictureFacts(scheme, layout).directions(stationId)

/** From how many letters a name is broken in two (15.10), when the scheme says so. */
const val METRO_LABEL_WRAP_FROM = 14

/**
 * [name] as written with long names in two lines (15.10): broken at the space
 * nearest its middle, neither half shorter than three letters; a short name,
 * or one with nowhere to break, as it is. A hyphenated name may break after
 * its hyphen.
 */
fun metroLabelWrapped(name: String, from: Int = METRO_LABEL_WRAP_FROM): String {
    val text = name.trim()
    if (text.length < from) return text
    val middle = text.length / 2.0
    val breaks = text.indices.filter { i -> text[i] == ' ' || text[i] == '-' }
        .filter { i -> i >= 3 && text.length - i > 3 }
    val at = breaks.minByOrNull { abs(it + 0.5 - middle) } ?: return text
    return if (text[at] == ' ') text.substring(0, at) + "\n" + text.substring(at + 1)
    else text.substring(0, at + 1) + "\n" + text.substring(at + 1)
}

/** The angle of a side, in radians on the screen. */
fun metroLabelAngle(side: Int): Double = side * PI / 4

// Right first, as before; then left; then above and below; the corners last.
private val PREFERENCE = listOf(0, 4, 6, 2, 1, 7, 5, 3)

/** How near another station has to be, in steps, to crowd a name's side (08.10). */
const val METRO_LABEL_CROWD_REACH = 2f

/**
 * The side a name goes by itself (08.10): the one furthest from every
 * direction something leaves the station, right when it is free — so a
 * name never lies along its own line. Between sides equally clear of it,
 * the one with fewer other stations close by that way (within 30°, as near
 * as [METRO_LABEL_CROWD_REACH]) wins: a name keeps off a line running
 * alongside.
 */
fun metroAutoLabelSide(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): Int {
    // Each rule the user's to turn off (15.10): names on the right, as before, or
    // no keeping off lines alongside.
    if (!scheme.labelsAuto) return 0
    val p = layout[stationId]
    return metroAutoSideOf(metroStationDirections(scheme, layout, stationId), scheme.labelsAvoidCrowd) {
        if (p == null) emptyList()
        else layout.filter { (id, q) -> id != stationId && metroNear(p, q, METRO_LABEL_CROWD_REACH) }
            .values.map { atan2((it.y - p.y).toDouble(), (it.x - p.x).toDouble()) }
    }
}

/**
 * The rule itself (14.10), for a station whose track and links leave it
 * along [dirs]; [near] gives the ways to the stations close by, asked only
 * when two sides are equally clear.
 */
internal fun metroAutoSideOf(dirs: List<Double>, avoidCrowd: Boolean = true, near: () -> List<Double>): Int {
    if (dirs.isEmpty()) return 0
    fun clearance(side: Int): Double = dirs.minOf { d -> metroAngleGap(metroLabelAngle(side), d) }
    val best = PREFERENCE.maxOf { clearance(it) }
    val clear = PREFERENCE.filter { clearance(it) >= best - 1e-6 }
    if (clear.size == 1 || !avoidCrowd) return clear[0]
    val others = near()
    fun crowd(side: Int): Int = others.count { metroAngleGap(metroLabelAngle(side), it) <= PI / 6 + 1e-6 }
    // Stable: fewest crowding first, then the order of preference.
    return clear.minByOrNull { crowd(it) } ?: clear[0]
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
