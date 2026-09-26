package com.alphaomegos.annasagenda

import kotlin.math.sqrt

/** A point on the chart, in whatever units the caller is drawing in. */
data class CurvePoint(val x: Float, val y: Float)

/**
 * One cubic Bézier hop, starting wherever the previous one ended.
 *
 * Bézier rather than a list of sampled points because that is what a canvas
 * draws: `cubicTo` takes exactly these three, and sampling would mean choosing
 * a resolution and being wrong about it at both ends.
 */
data class CurveSegment(
    val control1: CurvePoint,
    val control2: CurvePoint,
    val end: CurvePoint,
)

/**
 * Turns a run of measurements into a curve that is smooth and **honest**.
 *
 * The obvious way to smooth a chart is a Catmull-Rom spline, and it is wrong
 * here. It overshoots: three weights of 72, 70, 72 come out as a curve that
 * dips below 70 on its way through, and the chart then shows a weight the
 * person never was. On a screen whose whole purpose is to watch a number move
 * by half a kilo, inventing half a kilo is not a cosmetic problem.
 *
 * So this is monotone cubic interpolation — Fritsch-Carlson. Its one promise
 * is the one that matters: **between two measurements the curve stays between
 * their values**. Where the data goes up, the curve goes up; where the data
 * turns, the curve is flat at the turn rather than sailing past it.
 *
 * Two points come out as exactly a straight line, which is why there is no
 * "only smooth when there are enough points" rule anywhere: with this curve
 * the sparse case takes care of itself. Three points bend gently, thirty bend
 * into the shape the numbers actually have.
 *
 * Returns the hops after the first point. Fewer than two points draw nothing,
 * because a line needs somewhere to go.
 */
fun smoothCurveSegments(points: List<CurvePoint>): List<CurveSegment> {
    if (points.size < 2) return emptyList()

    val n = points.size
    val slopes = FloatArray(n - 1)
    val widths = FloatArray(n - 1)

    for (k in 0 until n - 1) {
        val h = points[k + 1].x - points[k].x
        widths[k] = h
        // Two measurements landing on the same pixel column: no run, so no
        // slope. Left at zero, which makes the pair a flat hop rather than an
        // infinity that poisons both its neighbours' tangents.
        slopes[k] = if (h == 0f) 0f else (points[k + 1].y - points[k].y) / h
    }

    val tangents = FloatArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[n - 2]

    for (k in 1 until n - 1) {
        val before = slopes[k - 1]
        val after = slopes[k]

        // A turning point. The tangent is flat, which is what stops the curve
        // sailing past the value and inventing a lower low.
        tangents[k] = if (before * after <= 0f) 0f else (before + after) / 2f
    }

    // Fritsch-Carlson: pull any tangent back inside the circle of radius 3
    // around its segment's own slope. This is the step that makes the promise
    // above true rather than usually true.
    for (k in 0 until n - 1) {
        val slope = slopes[k]

        if (slope == 0f) {
            tangents[k] = 0f
            tangents[k + 1] = 0f
            continue
        }

        val alpha = tangents[k] / slope
        val beta = tangents[k + 1] / slope
        val radius = alpha * alpha + beta * beta

        if (radius > 9f) {
            val scale = 3f / sqrt(radius)
            tangents[k] = scale * alpha * slope
            tangents[k + 1] = scale * beta * slope
        }
    }

    return (0 until n - 1).map { k ->
        val from = points[k]
        val to = points[k + 1]
        val third = widths[k] / 3f

        CurveSegment(
            control1 = CurvePoint(from.x + third, from.y + tangents[k] * third),
            control2 = CurvePoint(to.x - third, to.y - tangents[k + 1] * third),
            end = to,
        )
    }
}
