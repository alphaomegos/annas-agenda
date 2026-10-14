package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * The editor's layers (14.10, Eduard: "show only line 1, place its
 * stations, then show line 2 and hook it on"): each line can be hidden from
 * the editor's picture, or locked — shown, but nothing of it moves. Hidden
 * lines can be shown faintly instead of not at all, for their whereabouts.
 *
 * The editor's view only: the metro screen draws every line. Settings of
 * the scheme, so they are there next time, and not steps of undo — undo
 * takes the picture back, not what is looked at.
 */

/** [lineId] shown if hidden, hidden if shown. */
fun metroLayerShownToggled(scheme: MetroScheme, lineId: Long): MetroScheme =
    scheme.copy(hiddenLines = if (lineId in scheme.hiddenLines) scheme.hiddenLines - lineId else scheme.hiddenLines + lineId)

/**
 * Only [lineId] shown, every other line hidden; asked again with it the
 * only one already, every line shown.
 */
fun metroLayerOnly(scheme: MetroScheme, lineId: Long): MetroScheme {
    val others = scheme.lines.map { it.id }.filter { it != lineId }
    val alreadyOnly = lineId !in scheme.hiddenLines && others.all { it in scheme.hiddenLines }
    return scheme.copy(hiddenLines = if (alreadyOnly) emptyList() else others)
}

/** Every line shown. */
fun metroLayersAllShown(scheme: MetroScheme): MetroScheme = scheme.copy(hiddenLines = emptyList())

/** [lineId] locked if free, free if locked. */
fun metroLayerLockToggled(scheme: MetroScheme, lineId: Long): MetroScheme =
    scheme.copy(lockedLines = if (lineId in scheme.lockedLines) scheme.lockedLines - lineId else scheme.lockedLines + lineId)

/** The stations of hidden lines: not drawn (or drawn faintly), not touched. */
fun metroHiddenStations(scheme: MetroScheme): Set<Long> {
    val hidden = scheme.hiddenLines.toSet()
    return scheme.stations.filter { it.lineId in hidden }.map { it.id }.toSet()
}

/** The stations nothing may move or change: those of hidden and of locked lines. */
fun metroFixedStations(scheme: MetroScheme): Set<Long> {
    val fixed = scheme.hiddenLines.toSet() + scheme.lockedLines
    return scheme.stations.filter { it.lineId in fixed }.map { it.id }.toSet()
}

/**
 * The part of the scheme a finger may change: without the hidden and the
 * locked lines, and without the bends of their track — what bending looks
 * for track and points in.
 */
fun metroEditablePart(scheme: MetroScheme): MetroScheme {
    val fixedLines = scheme.hiddenLines.toSet() + scheme.lockedLines
    if (fixedLines.isEmpty()) return scheme
    val fixed = metroFixedStations(scheme)
    return scheme.copy(
        lines = scheme.lines.filter { it.id !in fixedLines },
        bends = scheme.bends.filter { it.aStationId !in fixed && it.bStationId !in fixed },
    )
}

/** [shown] — a step of undo or redo — with the layers as they are now in [current]. */
fun metroLayersKept(shown: MetroScheme, current: MetroScheme): MetroScheme =
    shown.copy(hiddenLines = current.hiddenLines, lockedLines = current.lockedLines, ghostHidden = current.ghostHidden)
