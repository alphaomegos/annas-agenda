package com.alphaomegos.annasagenda.support

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The visualizer's pictures as numbers (30.10): what is worked out on the CPU each
 * frame and handed to the GPU — the classic spectrum's bars and peak markers, the
 * vortex's filaments — and the phases the shaders move by. Pure, so it is tested.
 */

const val TWO_PI_F = 6.2831855f

/** A phase kept within one [period]: hours of music never cost a shader its precision. */
fun wrapPhase(phase: Float, period: Float = TWO_PI_F): Float {
    val p = phase % period
    return if (p < 0f) p + period else p
}

/**
 * The classic spectrum's peak markers: up at once with their bar, held [hold] seconds,
 * then falling [fallPerSecond] of the height a second — slower than the bars.
 */
class SpectrumPeaks(count: Int, private val hold: Float = 0.2f, private val fallPerSecond: Float = 0.8f) {
    val level = FloatArray(count)
    private val held = FloatArray(count)

    fun update(bars: FloatArray, dt: Float) {
        for (i in level.indices) {
            val b = bars[i]
            if (b >= level[i]) {
                level[i] = b
                held[i] = 0f
            } else {
                held[i] += dt
                if (held[i] > hold) level[i] = max(b, level[i] - fallPerSecond * dt)
            }
        }
    }
}

/** Floats per vertex of [spectrumBars]: x, y in clip space, then r, g, b, a. */
const val SPECTRUM_VERTEX_FLOATS = 6

/** Floats [spectrumBars] needs for [bars] bars. */
fun spectrumFloats(bars: Int): Int = bars * 12 * SPECTRUM_VERTEX_FLOATS

/**
 * The classic spectrum (Windows Media Player's «bars and waves»): low on the left, a
 * lime bar a band from the floor and a thin blue peak marker above it, small even gaps,
 * the edges on whole pixels so they stay sharp. Two triangles a rectangle, written into
 * [out]; returns how many vertices.
 */
fun spectrumBars(bars: FloatArray, peaks: FloatArray, widthPx: Int, heightPx: Int, out: FloatArray): Int {
    val n = bars.size
    if (n == 0 || widthPx <= 0 || heightPx <= 0) return 0
    val slot = widthPx.toFloat() / n
    val gap = max(1f, slot * 0.22f)
    val floorPx = heightPx * 0.03f
    val tallest = heightPx * 0.93f
    val marker = max(2f, heightPx * 0.008f).roundToInt().toFloat()
    var v = 0
    fun x(px: Float) = px / widthPx * 2f - 1f
    fun y(px: Float) = px / heightPx * 2f - 1f
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float) {
        val ax = x(x0); val bx = x(x1); val ay = y(y0); val by = y(y1)
        for ((px, py) in arrayOf(ax to ay, bx to ay, bx to by, ax to ay, bx to by, ax to by)) {
            out[v++] = px; out[v++] = py; out[v++] = r; out[v++] = g; out[v++] = b; out[v++] = 1f
        }
    }
    for (i in 0 until n) {
        val left = (i * slot + gap / 2f).roundToInt().toFloat()
        val right = max(left + 1f, ((i + 1) * slot - gap / 2f).roundToInt().toFloat())
        val top = (floorPx + tallest * bars[i].coerceIn(0f, 1f)).roundToInt().toFloat()
        if (top > floorPx) rect(left, floorPx, right, top, 0.55f, 0.96f, 0.12f)
        if (peaks[i] > 0.004f) {
            val p = (floorPx + tallest * peaks[i].coerceIn(0f, 1f)).roundToInt().toFloat() + 2f
            rect(left, p, right, p + marker, 0.22f, 0.62f, 1f)
        }
    }
    return v / SPECTRUM_VERTEX_FLOATS
}

/**
 * The crimson vortex's filaments (the first reference's pale threads): [count] closed
 * loops of [points] points round the centre, each a rosette whose lobes the middle of the
 * sound deepens and the real waveform ripples; the bass and the onsets swell them, the
 * treble frays them a little. Each loop is a triangle strip, x, y in clip space and the
 * side (−1, +1) for a soft edge. Shapes are worked out where the screen is round — a
 * circle stays a circle however wide or narrow the view.
 */
class VortexFilaments(val count: Int = 3, val points: Int = 192) {
    /** Vertices a loop: its points and the first again, two sides each. */
    val vertexCount = (points + 1) * 2
    val vertices = FloatArray(count * vertexCount * 3)
    private val turn = FloatArray(count) { it * 2.1f }
    private val sway = FloatArray(count) { it * 1.3f }
    private var drift = 0f
    private val xs = FloatArray(points + 1)
    private val ys = FloatArray(points + 1)

    /** The phases, for the tests: they stay within a turn. */
    fun phases(): List<Float> = (turn + sway).toList() + drift

    /** Where loop [k]'s vertices start in [vertices], in vertices. */
    fun first(k: Int): Int = k * vertexCount

    fun update(
        dt: Float, bass: Float, middle: Float, treble: Float, surge: Float, energy: Float,
        waveform: FloatArray, aspect: Float, halfWidth: Float,
    ) {
        drift = wrapPhase(drift + dt * 0.23f)
        val cx = 0.025f * sin(drift)
        val cy = 0.025f * cos(drift * 2f)
        val fit = min(1f, aspect)
        for (k in 0 until count) {
            turn[k] = wrapPhase(turn[k] + dt * (0.12f + 0.07f * k) * (1f + 0.4f * energy))
            sway[k] = wrapPhase(sway[k] + dt * (0.35f + 0.18f * k))
            val lobes = LOBES[k % LOBES.size]
            val radius = (0.24f + 0.08f * k) * fit * (1f + 0.28f * bass + 0.22f * surge)
            val depth = 0.12f + 0.3f * middle
            for (i in 0..points) {
                val t = TWO_PI_F * (i % points) / points
                val w = if (waveform.isEmpty()) 0f else waveform[(i % points) * waveform.size / points]
                val r = radius * (1f + depth * sin(lobes * t + sway[k]) + 0.07f * sin((lobes + 2) * t - 1.7f * sway[k])) +
                    0.05f * fit * middle * w + 0.012f * fit * treble * sin(37f * t + 3f * sway[k])
                xs[i] = cx + r * cos(t + turn[k])
                ys[i] = cy + r * sin(t + turn[k])
            }
            var v = first(k) * 3
            for (i in 0..points) {
                val a = if (i == 0) points - 1 else i - 1
                val b = if (i == points) 1 else i + 1
                var nx = -(ys[b] - ys[a])
                var ny = xs[b] - xs[a]
                val len = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-6f)
                nx = nx / len * halfWidth
                ny = ny / len * halfWidth
                for (side in SIDES) {
                    vertices[v++] = (xs[i] + side * nx) / aspect
                    vertices[v++] = ys[i] + side * ny
                    vertices[v++] = side
                }
            }
        }
    }

    private companion object {
        val LOBES = intArrayOf(3, 5, 4)
        val SIDES = floatArrayOf(-1f, 1f)
    }
}

/**
 * The rainbow ribbon's dots (31.10): a grid of [around] × [along] places on the sheet,
 * each as (around 0..1, along −1..1) — the shader makes the shape of it. Made once.
 */
fun ribbonGrid(around: Int = 160, along: Int = 72): FloatArray {
    val out = FloatArray(around * along * 2)
    var k = 0
    for (j in 0 until along) for (i in 0 until around) {
        out[k++] = i.toFloat() / around
        out[k++] = -1f + 2f * j / (along - 1)
    }
    return out
}

/** The bands gathered into [out].size groups (each the mean of its share), low to high. */
fun bandGroups(bands: FloatArray, out: FloatArray) {
    val n = bands.size
    for (g in out.indices) {
        val from = g * n / out.size
        val to = maxOf(from + 1, (g + 1) * n / out.size)
        var s = 0f
        for (b in from until to) s += bands[b]
        out[g] = s / (to - from)
    }
}

/** One step [step] round [size] things from [index]: past the last is the first again. */
fun cycle(index: Int, size: Int, step: Int): Int = Math.floorMod(index + step, size)
