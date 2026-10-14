package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stations drawn as ticks (08.10), as printed maps do: a station that is no
 * interchange is a short bar off its line, on its name's side; a terminus
 * is a bar across the line's end. Interchanges stay rings.
 */

/** The stations that are interchanges: joined to another by a transfer. */
fun metroInterchangeStations(scheme: MetroScheme): Set<Long> =
    scheme.transfers.flatMap { listOf(it.aStationId, it.bStationId) }.toSet()

/** The stations at the end of a line or a branch: one neighbour along it. */
fun metroTermini(scheme: MetroScheme): Set<Long> {
    val count = HashMap<Long, Int>()
    metroTracks(scheme).forEach { t ->
        count[t.a] = (count[t.a] ?: 0) + 1
        count[t.b] = (count[t.b] ?: 0) + 1
    }
    return count.filterValues { it == 1 }.keys
}

/**
 * Which way [stationId]'s tick points (08.10): square to its line — to the
 * line through it, bisecting a bend at the station — on the side nearer its
 * name ([labelSide], as in MetroLabels.kt). Null for a station off any track.
 */
fun metroTickAngle(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long, labelSide: Int): Double? =
    metroTickAngleOf(metroPictureFacts(scheme, layout).trackDirections[stationId].orEmpty(), labelSide)

/** The rule itself (14.10), for a station whose own track leaves it along [dirs]. */
internal fun metroTickAngleOf(dirs: List<Double>, labelSide: Int): Double? {
    if (dirs.isEmpty()) return null
    // The line's axis through the station: from the second track's way out to the first's.
    val axis = if (dirs.size >= 2) {
        atan2(sin(dirs[0]) - sin(dirs[1]), cos(dirs[0]) - cos(dirs[1]))
    } else dirs[0]
    val label = metroLabelAngle(labelSide.coerceIn(0, METRO_LABEL_SIDES - 1))
    val left = axis - PI / 2
    val right = axis + PI / 2
    return if (metroAngleGap(right, label) < metroAngleGap(left, label) - 1e-9) right else left
}

/**
 * The way out past the end of a line at a terminus (08.10), where its
 * number is put: straight on from the track that comes in. Null for a
 * station that is no terminus.
 */
fun metroTerminusOutward(scheme: MetroScheme, layout: Map<Long, MetroPoint>, stationId: Long): Double? {
    val dirs = metroPictureFacts(scheme, layout).trackDirections[stationId].orEmpty()
    if (dirs.size != 1) return null
    return dirs[0] + PI
}

/** Whether a name on [labelSide] goes the way [outward] points, give or take 45°: then it starts past the badge. */
fun metroLabelFacesOutward(labelSide: Int, outward: Double): Boolean {
    val d = abs(metroLabelAngle(labelSide) - outward) % (2 * PI)
    return minOf(d, 2 * PI - d) <= PI / 4 + 1e-6
}
