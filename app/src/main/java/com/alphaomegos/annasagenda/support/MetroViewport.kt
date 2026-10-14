package com.alphaomegos.annasagenda.support

/**
 * Where the picture is looked at (14.10): [k] pixels to a unit of the
 * picture, and the picture's point ([cx], [cy]) at the middle of a view
 * [w] × [h] pixels.
 *
 * Kept in the picture's own units, not as a zoom of "the whole picture
 * fitted": that one moved and rescaled everything the moment a station was
 * dropped past the edge of the rest, since "the whole picture" had grown.
 */
data class MetroViewport(val k: Float, val cx: Float, val cy: Float) {

    fun toScreenX(x: Float, w: Float) = (x - cx) * k + w / 2
    fun toScreenY(y: Float, h: Float) = (y - cy) * k + h / 2
    fun toMapX(sx: Float, w: Float) = (sx - w / 2) / k + cx
    fun toMapY(sy: Float, h: Float) = (sy - h / 2) / k + cy

    /** Moved by a finger by ([dx], [dy]) pixels: the picture follows the finger. */
    fun panned(dx: Float, dy: Float) = copy(cx = cx - dx / k, cy = cy - dy / k)

    /**
     * Zoomed by [factor] about the screen point ([sx], [sy]), which stays over
     * the same point of the picture; the scale kept within [minK]..[maxK].
     */
    fun zoomed(factor: Float, sx: Float, sy: Float, w: Float, h: Float, minK: Float, maxK: Float): MetroViewport {
        val nk = (k * factor).coerceIn(minK, maxOf(minK, maxK))
        val mx = toMapX(sx, w)
        val my = toMapY(sy, h)
        return MetroViewport(nk, mx - (sx - w / 2) / nk, my - (sy - h / 2) / nk)
    }
}

/**
 * The view that shows all of [points] in a [w] × [h] view, with a step's
 * margin round them (14.10) — as the picture opens, and after "Fit to the
 * screen".
 */
fun metroFittedViewport(points: Collection<MetroPoint>, w: Float, h: Float): MetroViewport {
    val b = metroLayoutBounds(points)
    val bw = (b[2] - b[0]) + 2 * METRO_LAYOUT_STEP
    val bh = (b[3] - b[1]) + 2 * METRO_LAYOUT_STEP
    val k = if (w > 0f && h > 0f) minOf(w / bw, h / bh) else 1f
    return MetroViewport(k, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
}
