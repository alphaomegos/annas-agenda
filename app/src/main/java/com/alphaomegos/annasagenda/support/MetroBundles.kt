package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme
import kotlin.math.sqrt

/**
 * Track run together (15.10, Eduard): two lines between the same two
 * interchanges with no station between — D4 from Kurskaya to Three
 * Stations Square beside the ring from Kurskaya to Komsomolskaya — drawn
 * as one bundle when the scheme says so: the first of them leads, and the
 * others follow its bends side by side, as far from it as their stations
 * stand from its stations. Bending any of them bends the leader, so all
 * of them.
 */

/** A track following [leader]; [sameWay]: its first station is at the leader's first end. */
data class MetroBundled(val leader: MetroTrack, val sameWay: Boolean)

/**
 * The tracks that follow another (15.10): grouped by the two interchanges
 * they join (a station of no interchange is one on its own), two or more
 * to a group; the first of each group, by its stations, leads and is not
 * in the map.
 */
fun metroTrackBundles(scheme: MetroScheme): Map<MetroTrack, MetroBundled> {
    val groupOf = HashMap<Long, Long>()
    metroTransferGroups(scheme).forEach { group -> group.forEach { groupOf[it] = group.first() } }
    fun g(id: Long) = groupOf[id] ?: id
    val byEnds = metroTracks(scheme)
        .filter { g(it.a) != g(it.b) }
        .groupBy { minOf(g(it.a), g(it.b)) to maxOf(g(it.a), g(it.b)) }
    val out = HashMap<MetroTrack, MetroBundled>()
    byEnds.values.filter { it.size >= 2 }.forEach { tracks ->
        val sorted = tracks.sortedWith(compareBy({ it.a }, { it.b }))
        val leader = sorted.first()
        sorted.drop(1).forEach { out[it] = MetroBundled(leader, g(it.a) == g(leader.a)) }
    }
    return out
}

/**
 * How far to the left of the line from [a] to [b] the point [p] lies
 * (15.10) — left as one goes from a to b on the screen, where y goes down;
 * negative to the right.
 */
fun metroSideOffset(a: MetroPoint, b: MetroPoint, p: MetroPoint): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len == 0f) return 0f
    return ((p.x - a.x) * dy - (p.y - a.y) * dx) / len
}

/**
 * [points] moved [d] to the left of their way (15.10), as [metroSideOffset]
 * measures it: each end square off its own segment, each corner along the
 * line halving it, as far out as keeps the sides [d] apart (no further
 * than four times [d] at a hairpin).
 */
fun metroOffsetPolyline(points: List<MetroPoint>, d: Float): List<MetroPoint> {
    if (points.size < 2 || d == 0f) return points
    fun normal(p: MetroPoint, q: MetroPoint): Pair<Float, Float>? {
        val dx = q.x - p.x; val dy = q.y - p.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len == 0f) null else (dy / len) to (-dx / len)
    }
    val normals = (0 until points.size - 1).map { normal(points[it], points[it + 1]) }
    return points.mapIndexed { i, p ->
        val before = if (i > 0) normals[i - 1] else null
        val after = if (i < normals.size) normals[i] else null
        val n = when {
            before == null && after == null -> null
            before == null -> after
            after == null -> before
            else -> {
                val sx = before.first + after.first; val sy = before.second + after.second
                val len = sqrt(sx * sx + sy * sy)
                if (len < 1e-4f) after else {
                    // The miter: the half-angle's secant, at most four times.
                    val mx = sx / len; val my = sy / len
                    val cos = mx * after.first + my * after.second
                    val k = (1f / cos.coerceAtLeast(0.25f))
                    (mx * k) to (my * k)
                }
            }
        }
        if (n == null) p else MetroPoint(p.x + n.first * d, p.y + n.second * d)
    }
}

/*
 * One line of several colours (16.10, Eduard): rather than the tracks of a
 * bundle side by side, one band along the leader's bends — a little wider
 * than a line — split along its length into a stripe of each line's colour,
 * in the order their stations stand across it.
 */

/**
 * Each leader with all its bundle's tracks (16.10): the leader first, then
 * its followers by their stations; each with whether it runs the leader's way.
 */
fun metroBundleGroups(bundles: Map<MetroTrack, MetroBundled>): Map<MetroTrack, List<Pair<MetroTrack, Boolean>>> =
    bundles.entries
        .groupBy { it.value.leader }
        .mapValues { (leader, followers) ->
            listOf(leader to true) + followers
                .sortedWith(compareBy({ it.key.a }, { it.key.b }))
                .map { it.key to it.value.sameWay }
        }

/** How wide the band of [n] lines is (16.10): a line's [lineWidth], and a third of it more for each line past the first. */
fun metroBandWidth(lineWidth: Float, n: Int): Float = lineWidth * (1f + 0.35f * (n - 1).coerceAtLeast(0))

/**
 * How far to the left of the band's middle each of its [n] stripes runs
 * (16.10), for a band [width] across: side by side, touching, the first
 * furthest to the right.
 */
fun metroStripeOffsets(n: Int, width: Float): List<Float> =
    if (n <= 0) emptyList() else (0 until n).map { (it + 0.5f - n / 2f) * width / n }
