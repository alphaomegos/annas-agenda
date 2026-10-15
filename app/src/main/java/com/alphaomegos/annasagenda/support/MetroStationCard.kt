package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroLine
import com.alphaomegos.annasagenda.model.MetroScheme
import com.alphaomegos.annasagenda.model.MetroStation

/**
 * What a station's card on the map says (15.10): the station, its line, and
 * the stations a walk away from it, each with its line.
 */
data class MetroStationFacts(
    val station: MetroStation,
    val line: MetroLine?,
    val walks: List<MetroWalk>,
)

/** A station a walk away, and whether that walk is closed now. */
data class MetroWalk(val station: MetroStation, val line: MetroLine?, val closed: Boolean)

/**
 * The card of [stationId] on [scheme], or null when there is no such station.
 *
 * The walks are the transfers from the station itself, each other end once,
 * in the order the scheme lists the lines — the order the legend shows them.
 */
fun metroStationFacts(scheme: MetroScheme, stationId: Long): MetroStationFacts? {
    val station = scheme.stations.firstOrNull { it.id == stationId } ?: return null
    val lines = scheme.lines.associateBy { it.id }
    val stations = scheme.stations.associateBy { it.id }
    val order = scheme.lines.withIndex().associate { (i, l) -> l.id to i }
    val walks = scheme.transfers
        .mapNotNull { t ->
            val other = when (stationId) {
                t.aStationId -> t.bStationId
                t.bStationId -> t.aStationId
                else -> return@mapNotNull null
            }
            if (other == stationId) return@mapNotNull null
            val there = stations[other] ?: return@mapNotNull null
            MetroWalk(there, lines[there.lineId], closed = t.closure != null)
        }
        .distinctBy { it.station.id }
        .sortedBy { order[it.station.lineId] ?: Int.MAX_VALUE }
    return MetroStationFacts(station, lines[station.lineId], walks)
}
