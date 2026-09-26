package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's curve, and the one promise it has to keep.
 *
 * A smooth line through measurements is a drawing decision everywhere except
 * here: this chart exists to watch a weight move by half a kilo, so a curve
 * that dips half a kilo below the lowest measurement is showing a weight the
 * person never was. The usual spline does exactly that.
 *
 * So most of what is asked below is one question in several shapes: does the
 * curve ever leave the range of the numbers it connects? It is asked by
 * walking the Bézier rather than by trusting the formula, because the formula
 * is the thing under test.
 */
class CurveSmoothingTest {

    private fun p(x: Float, y: Float) = CurvePoint(x, y)

    /** Walks the whole curve, start point included, at a fine step. */
    private fun walk(points: List<CurvePoint>, steps: Int = 40): List<CurvePoint> {
        val segments = smoothCurveSegments(points)
        if (segments.isEmpty()) return points.take(1)

        val out = mutableListOf(points.first())
        var from = points.first()

        segments.forEach { seg ->
            (1..steps).forEach { i ->
                val t = i.toFloat() / steps
                out += bezierAt(from, seg, t)
            }
            from = seg.end
        }

        return out
    }

    private fun bezierAt(from: CurvePoint, seg: CurveSegment, t: Float): CurvePoint {
        val u = 1 - t
        fun cubic(a: Float, b: Float, c: Float, d: Float) =
            u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d

        return CurvePoint(
            x = cubic(from.x, seg.control1.x, seg.control2.x, seg.end.x),
            y = cubic(from.y, seg.control1.y, seg.control2.y, seg.end.y),
        )
    }

    /* ---------------- nothing to draw ---------------- */

    @Test
    fun nothingIsDrawnThroughFewerThanTwoPoints() {
        assertTrue(smoothCurveSegments(emptyList()).isEmpty())
        assertTrue(smoothCurveSegments(listOf(p(0f, 0f))).isEmpty())
    }

    @Test
    fun everyPointAfterTheFirstIsTheEndOfAHop() {
        val points = listOf(p(0f, 10f), p(1f, 20f), p(2f, 15f), p(3f, 15f))

        assertEquals(
            points.drop(1),
            smoothCurveSegments(points).map { it.end },
        )
    }

    /* ---------------- the sparse case looks after itself ---------------- */

    /**
     * Two measurements come out as exactly a straight line, which is why
     * there is no "only smooth once there are enough points" rule anywhere.
     */
    @Test
    fun twoPointsAreAStraightLine() {
        val points = listOf(p(0f, 0f), p(30f, 60f))

        walk(points).forEach { point ->
            assertEquals("y should be 2x at x=${point.x}", 2f * point.x, point.y, 0.01f)
        }
    }

    @Test
    fun pointsOnAStraightLineStayOnIt() {
        val points = (0..5).map { p(it * 10f, 100f - it * 7f) }

        walk(points).forEach { point ->
            assertEquals(100f - 0.7f * point.x, point.y, 0.02f)
        }
    }

    /* ---------------- the promise ---------------- */

    /**
     * The one that rules out a Catmull-Rom spline: down and back up again.
     * A spline through these three dips below 70 in the middle.
     */
    @Test
    fun aDipNeverGoesLowerThanTheLowestMeasurement() {
        val points = listOf(p(0f, 72f), p(10f, 70f), p(20f, 72f))

        val lowest = walk(points).minOf { it.y }

        assertTrue("curve reached $lowest, below the lowest measurement 70", lowest >= 70f - 0.001f)
    }

    @Test
    fun aPeakNeverGoesHigherThanTheHighestMeasurement() {
        val points = listOf(p(0f, 70f), p(10f, 72f), p(20f, 70f))

        val highest = walk(points).maxOf { it.y }

        assertTrue("curve reached $highest, above the highest measurement 72", highest <= 72f + 0.001f)
    }

    /**
     * The general form, on data with several turns in it: nowhere on the
     * curve may there be a value that is not between two measurements.
     */
    @Test
    fun theCurveNeverLeavesTheRangeOfTheMeasurements() {
        val ys = listOf(72.4f, 71.8f, 71.9f, 70.2f, 70.1f, 71.6f, 69.4f, 69.5f, 69.0f)
        val points = ys.mapIndexed { i, y -> p(i * 12f, y) }

        val walked = walk(points)

        assertTrue("min ${walked.minOf { it.y }} below ${ys.min()}", walked.minOf { it.y } >= ys.min() - 0.001f)
        assertTrue("max ${walked.maxOf { it.y }} above ${ys.max()}", walked.maxOf { it.y } <= ys.max() + 0.001f)
    }

    /**
     * Between any two neighbouring measurements specifically — not just
     * inside the overall range. A curve can respect the global bounds and
     * still bulge past a local one.
     */
    @Test
    fun eachHopStaysBetweenItsOwnTwoMeasurements() {
        val ys = listOf(10f, 30f, 12f, 40f, 39f, 5f)
        val points = ys.mapIndexed { i, y -> p(i * 10f, y) }
        val segments = smoothCurveSegments(points)

        segments.forEachIndexed { k, seg ->
            val lo = minOf(points[k].y, points[k + 1].y)
            val hi = maxOf(points[k].y, points[k + 1].y)

            (0..40).forEach { i ->
                val y = bezierAt(points[k], seg, i / 40f).y
                assertTrue("hop $k reached $y, outside $lo..$hi", y in (lo - 0.001f)..(hi + 0.001f))
            }
        }
    }

    /**
     * Where the numbers only go one way, the curve only goes one way. A
     * wobble here would read as a week of gaining weight that never happened.
     */
    @Test
    fun aSteadyDescentNeverTurnsBackUp() {
        val points = listOf(72f, 71.9f, 71.1f, 71.0f, 68.0f, 67.9f)
            .mapIndexed { i, y -> p(i * 10f, y) }

        walk(points).zipWithNext().forEach { (a, b) ->
            assertTrue("went back up at x=${b.x}: ${a.y} -> ${b.y}", b.y <= a.y + 0.001f)
        }
    }

    /**
     * A flat stretch is flat. Two equal measurements with a rise after them
     * must not be joined by a line that sags first — that is the shape that
     * would suggest a loss the scale never showed.
     */
    @Test
    fun aFlatStretchStaysFlat() {
        val points = listOf(p(0f, 70f), p(10f, 70f), p(20f, 74f))
        val segments = smoothCurveSegments(points)

        (0..40).forEach { i ->
            val y = bezierAt(points[0], segments[0], i / 40f).y
            assertEquals("the flat hop sagged at t=${i / 40f}", 70f, y, 0.001f)
        }
    }

    /* ---------------- the awkward inputs ---------------- */

    /**
     * A long range squashes several dates onto one pixel column. That used to
     * be a division by zero, and one infinite slope poisons both of its
     * neighbours' tangents — so the damage would have shown up as a wild
     * curve somewhere else entirely.
     */
    @Test
    fun twoMeasurementsOnTheSameColumnProduceNoNonsense() {
        val points = listOf(p(0f, 70f), p(10f, 71f), p(10f, 73f), p(20f, 72f))

        walk(points).forEach { point ->
            assertTrue("x is not finite: ${point.x}", point.x.isFinite())
            assertTrue("y is not finite: ${point.y}", point.y.isFinite())
        }
    }

    @Test
    fun aCompletelyFlatRunIsACompletelyFlatLine() {
        val points = (0..5).map { p(it * 10f, 68.5f) }

        walk(points).forEach { point ->
            assertEquals(68.5f, point.y, 0.001f)
        }
    }

    /**
     * One measurement a hundred times the size of its neighbours — a typo, or
     * a height typed into the weight field. The curve must still be finite and
     * still confined; the wrong number is the user's problem, a chart that
     * cannot draw it is ours.
     */
    @Test
    fun oneAbsurdValueDoesNotBreakTheRest() {
        val ys = listOf(70f, 71f, 7000f, 71f, 70f)
        val points = ys.mapIndexed { i, y -> p(i * 10f, y) }

        val walked = walk(points)

        assertTrue(walked.all { it.y.isFinite() })
        assertTrue(walked.minOf { it.y } >= 70f - 0.001f)
        assertTrue(walked.maxOf { it.y } <= 7000f + 0.001f)
    }
}
