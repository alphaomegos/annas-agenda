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

/**
 * The view [t] of the way from [a] to [b] (15.10), for a zoom that glides
 * rather than jumps.
 *
 * The scale goes by ratio, so every frame zooms by the same factor; the
 * middle follows so that the one point of the picture both views put on the
 * same place of the screen stays there all the way — a zoom about the
 * fingers looks like one, and "fit" swings about its own still point. With
 * one scale, a plain pan, the middle simply slides.
 */
fun metroViewportBetween(a: MetroViewport, b: MetroViewport, t: Float): MetroViewport {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val k = a.k * Math.pow((b.k / a.k).toDouble(), t.toDouble()).toFloat()
    if (kotlin.math.abs(b.k - a.k) < 1e-6f * a.k) {
        return MetroViewport(k, a.cx + (b.cx - a.cx) * t, a.cy + (b.cy - a.cy) * t)
    }
    // The still point: (m - a.c) * a.k == (m - b.c) * b.k.
    val mx = (b.cx * b.k - a.cx * a.k) / (b.k - a.k)
    val my = (b.cy * b.k - a.cy * a.k) / (b.k - a.k)
    return MetroViewport(k, mx - (mx - a.cx) * a.k / k, my - (my - a.cy) * a.k / k)
}

/** How far a button or a double tap zooms at once (15.10): twice, or half. */
const val METRO_ZOOM_STEP = 2f

/** The scales a view may take, as multiples of the fitted one: from half of it to twelve times. */
const val METRO_MIN_ZOOM = 0.5f
const val METRO_MAX_ZOOM = 12f
