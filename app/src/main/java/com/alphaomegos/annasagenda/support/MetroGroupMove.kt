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

/** [ids] with [stationId] added, or taken away if it was there. */
fun metroSelectionToggled(ids: Set<Long>, stationId: Long): Set<Long> =
    if (stationId in ids) ids - stationId else ids + stationId

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
