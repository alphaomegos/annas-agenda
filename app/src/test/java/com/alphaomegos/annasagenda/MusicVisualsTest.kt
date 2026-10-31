package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.SPECTRUM_VERTEX_FLOATS
import com.alphaomegos.annasagenda.support.SpectrumPeaks
import com.alphaomegos.annasagenda.support.TWO_PI_F
import com.alphaomegos.annasagenda.support.VortexFilaments
import com.alphaomegos.annasagenda.support.spectrumBars
import com.alphaomegos.annasagenda.support.spectrumFloats
import com.alphaomegos.annasagenda.support.wrapPhase
import com.alphaomegos.annasagenda.support.bandGroups
import com.alphaomegos.annasagenda.support.cycle
import com.alphaomegos.annasagenda.support.ribbonGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The visualizer's pictures as numbers (0364): peaks, bars, filaments, phases. */
class MusicVisualsTest {

    @Test
    fun aPhaseStaysWithinItsPeriod() {
        assertEquals(1f, wrapPhase(TWO_PI_F + 1f), 1e-5f)
        assertEquals(TWO_PI_F - 1f, wrapPhase(-1f), 1e-5f)
        assertEquals(0.5f, wrapPhase(4.5f, period = 2f), 1e-6f)
    }

    @Test
    fun aPeakJumpsUp_holds_thenFallsSlowerThanItsBar() {
        val peaks = SpectrumPeaks(1, hold = 0.2f, fallPerSecond = 0.8f)
        peaks.update(floatArrayOf(0.9f), 1f / 60)
        assertEquals(0.9f, peaks.level[0], 0f)
        repeat(10) { peaks.update(floatArrayOf(0.1f), 1f / 60) }   // 1/6 s: still held
        assertEquals(0.9f, peaks.level[0], 0f)
        repeat(30) { peaks.update(floatArrayOf(0.1f), 1f / 60) }   // then falling, 0.8 a second
        assertTrue(peaks.level[0] < 0.9f && peaks.level[0] > 0.5f)
        repeat(120) { peaks.update(floatArrayOf(0.1f), 1f / 60) }
        assertEquals("never below its bar", 0.1f, peaks.level[0], 1e-6f)
        repeat(240) { peaks.update(floatArrayOf(0f), 1f / 60) }
        assertEquals("silence: down to the floor", 0f, peaks.level[0], 1e-6f)
    }

    @Test
    fun barsStandOnTheFloorInTheirOwnSlots_lowOnTheLeft() {
        val bars = FloatArray(48) { it / 47f }
        val peaks = FloatArray(48) { (it / 47f + 0.05f).coerceAtMost(1f) }
        val out = FloatArray(spectrumFloats(48))
        val vertices = spectrumBars(bars, peaks, 1080, 600, out)
        // The first bar is empty (nothing to draw) but has its peak; every other has both.
        assertEquals((47 * 2 + 1) * 6, vertices)
        var lastRight = -2f
        var v = 0
        var floor = Float.NaN
        while (v < vertices * SPECTRUM_VERTEX_FLOATS) {
            val xs = (0 until 6).map { out[v + it * SPECTRUM_VERTEX_FLOATS] }
            val ys = (0 until 6).map { out[v + it * SPECTRUM_VERTEX_FLOATS + 1] }
            val green = out[v + 3]
            if (green > 0.9f) {
                // A bar: right of the last one, from the same floor.
                assertTrue(xs.minOrNull()!! >= lastRight)
                lastRight = xs.maxOrNull()!!
                if (floor.isNaN()) floor = ys.minOrNull()!! else assertEquals(floor, ys.minOrNull()!!, 1e-6f)
            }
            xs.forEach { assertTrue(it in -1f..1f) }
            ys.forEach { assertTrue(it in -1f..1f) }
            v += 6 * SPECTRUM_VERTEX_FLOATS
        }
        // Whole pixels: every x lands on one.
        for (i in 0 until vertices) {
            val px = (out[i * SPECTRUM_VERTEX_FLOATS] + 1f) / 2f * 1080f
            assertEquals(Math.round(px).toFloat(), px, 1e-2f)
        }
    }

    @Test
    fun silenceDrawsNoBars() {
        val out = FloatArray(spectrumFloats(48))
        assertEquals(0, spectrumBars(FloatArray(48), FloatArray(48), 1080, 600, out))
    }

    @Test
    fun theFilamentsAreClosedLoops_roundOnAnyScreen() {
        listOf(2f, 0.5f, 1f).forEach { aspect ->
            val f = VortexFilaments()
            f.update(1f / 60, 0f, 0f, 0f, 0f, 0f, FloatArray(0), aspect, 0.004f)
            for (k in 0 until f.count) {
                val s = f.first(k) * 3
                val e = (f.first(k) + f.vertexCount - 2) * 3
                assertEquals(f.vertices[s], f.vertices[e], 1e-4f)
                assertEquals(f.vertices[s + 1], f.vertices[e + 1], 1e-4f)
                // Width and height in screen terms alike: x in clip space times the aspect.
                val xs = (0 until f.vertexCount).map { f.vertices[s + it * 3] * aspect }
                val ys = (0 until f.vertexCount).map { f.vertices[s + it * 3 + 1] }
                val w = xs.maxOrNull()!! - xs.minOrNull()!!
                val h = ys.maxOrNull()!! - ys.minOrNull()!!
                assertTrue("aspect $aspect loop $k: $w × $h", abs(w - h) / h < 0.2f)
                (0 until f.vertexCount).forEach { assertTrue(abs(f.vertices[s + it * 3]) <= 1f && abs(f.vertices[s + it * 3 + 1]) <= 1f) }
            }
        }
    }

    @Test
    fun theBassSwellsThem_andTheyNeverLeaveTheScreen() {
        fun extent(bass: Float): Float {
            val f = VortexFilaments()
            f.update(1f / 60, bass, 1f, 1f, 1f, 1f, FloatArray(128) { if (it % 2 == 0) 1f else -1f }, 1f, 0.004f)
            return (0 until f.vertices.size / 3).maxOf { abs(f.vertices[it * 3 + 1]) }
        }
        assertTrue(extent(1f) > extent(0f))
        assertTrue(extent(1f) <= 1f)
    }

    @Test
    fun hoursOfMusicKeepThePhasesSmall() {
        val f = VortexFilaments(points = 16)
        repeat(200_000) { f.update(1f / 60, 0.5f, 0.5f, 0.5f, 0f, 1f, FloatArray(0), 1.5f, 0.004f) }
        assertTrue(f.phases().all { it in 0f..TWO_PI_F })
    }

    /** The rainbow ribbon's grid (0367): every place once, around 0..1, along −1..1 end to end. */
    @Test
    fun theRibbonsGridCoversTheSheet() {
        val g = ribbonGrid(around = 8, along = 5)
        assertEquals(8 * 5 * 2, g.size)
        val places = (0 until 40).map { g[it * 2] to g[it * 2 + 1] }
        assertEquals(40, places.toSet().size)
        assertTrue(places.all { (u, v) -> u in 0f..1f && u < 1f && v in -1f..1f })
        assertEquals(-1f, places.minOf { it.second }, 0f)
        assertEquals(1f, places.maxOf { it.second }, 1e-6f)
    }

    @Test
    fun theBandsGatherInOrder() {
        val bands = FloatArray(48) { it / 47f }
        val groups = FloatArray(16)
        bandGroups(bands, groups)
        assertEquals((0f + 1f + 2f) / 3f / 47f, groups[0], 1e-6f)
        assertEquals((45f + 46f + 47f) / 3f / 47f, groups[15], 1e-6f)
        assertTrue(groups.toList().zipWithNext().all { (a, b) -> b > a })
    }

    /** A swipe goes round the pictures (0367): past the last comes the first. */
    @Test
    fun aSwipeGoesRound() {
        assertEquals(1, cycle(0, 4, 1))
        assertEquals(0, cycle(3, 4, 1))
        assertEquals(3, cycle(0, 4, -1))
    }
}
