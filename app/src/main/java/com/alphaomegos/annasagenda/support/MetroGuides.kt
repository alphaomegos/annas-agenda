package com.alphaomegos.annasagenda.support

import kotlin.math.abs

/**
 * Guides while dragging (08.10), as in drawing programs: a station dropped
 * nearly level with another one — or nearly under it — lands exactly level
 * (or under), and the editor draws the line it lines up on.
 */
data class MetroGuided(val point: MetroPoint, val x: Float?, val y: Float?)

/**
 * [p] with its x moved onto the nearest x among [others] within
 * [tolerance], and its y likewise, each on its own; [MetroGuided.x] and
 * [MetroGuided.y] say which guides it took.
 */
fun metroGuided(p: MetroPoint, others: Collection<MetroPoint>, tolerance: Float): MetroGuided {
    var bestX: Float? = null
    var bestY: Float? = null
    var dx = tolerance
    var dy = tolerance
    others.forEach { q ->
        val ax = abs(q.x - p.x)
        if (ax <= dx) { dx = ax; bestX = q.x }
        val ay = abs(q.y - p.y)
        if (ay <= dy) { dy = ay; bestY = q.y }
    }
    return MetroGuided(MetroPoint(bestX ?: p.x, bestY ?: p.y), bestX, bestY)
}
