package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.Fft
import com.alphaomegos.annasagenda.support.MusicAnalyzer
import com.alphaomegos.annasagenda.support.smoothingShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The visualizer's analysis (0362) on made-up sound, played as the screen would: at each
 * frame, the last 2048 samples up to that moment.
 */
class MusicSpectrumTest {

    private val rate = 44_100

    /** Plays [seconds] of [left] (and [right]) through a fresh analyzer at [fps]; [each] sees every frame. */
    private fun play(
        seconds: Float,
        fps: Int = 60,
        analyzer: MusicAnalyzer = MusicAnalyzer(rate),
        right: ((Int) -> Float)? = null,
        each: (Float, MusicAnalyzer) -> Unit = { _, _ -> },
        left: (Int) -> Float,
    ): MusicAnalyzer {
        val n = analyzer.tuning.fftSize
        val l = FloatArray(n)
        val r = right?.let { FloatArray(n) }
        val frames = (seconds * fps).toInt()
        for (f in 1..frames) {
            val end = (f.toLong() * rate / fps).toInt()
            for (i in 0 until n) {
                val s = end - n + i
                l[i] = if (s < 0) 0f else left(s)
                if (r != null && right != null) r[i] = if (s < 0) 0f else right(s)
            }
            analyzer.update(l, r, 1f / fps)
            each(f.toFloat() / fps, analyzer)
        }
        return analyzer
    }

    private fun tone(hz: Double, db: Double = -12.0): (Int) -> Float {
        val a = 10.0.pow(db / 20)
        return { s -> (a * sin(2 * PI * hz * s / rate)).toFloat() }
    }

    private fun MusicAnalyzer.loudestBand(): Int = bands.indices.maxByOrNull { bands[it] }!!
    private fun MusicAnalyzer.bandOf(hz: Float): Int = bandCenterHz.indices.minByOrNull { abs(bandCenterHz[it] - hz) }!!

    @Test
    fun theFftIsTheDiscreteFourierTransform() {
        val n = 64
        val random = Random(7)
        val xr = FloatArray(n) { random.nextFloat() - 0.5f }
        val xi = FloatArray(n) { random.nextFloat() - 0.5f }
        val re = xr.copyOf(); val im = xi.copyOf()
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            var sr = 0.0; var si = 0.0
            for (j in 0 until n) {
                val a = -2 * PI * j * k / n
                sr += xr[j] * cos(a) - xi[j] * sin(a)
                si += xr[j] * sin(a) + xi[j] * cos(a)
            }
            assertEquals(sr, re[k].toDouble(), 1e-3)
            assertEquals(si, im[k].toDouble(), 1e-3)
        }
    }

    @Test
    fun theBandsRunLowToHighInEvenSteps() {
        val a = MusicAnalyzer(rate)
        assertEquals(48, a.bands.size)
        assertEquals(32f, a.bandLowHz.first(), 0.01f)
        assertEquals(16_000f, a.bandHighHz.last(), 1f)
        val ratios = a.bandCenterHz.toList().zipWithNext { x, y -> y / x }
        assertTrue(ratios.all { abs(it - ratios.first()) < 1e-3f })
    }

    @Test
    fun aSteadyToneStandsStillInItsOwnBand() {
        val seen = mutableListOf<Float>()
        val a = play(1.5f, each = { t, x -> if (t > 0.5f) seen += x.bands[x.bandOf(1000f)] }, left = tone(1000.0))
        assertTrue(abs(a.loudestBand() - a.bandOf(1000f)) <= 1)
        assertTrue("the tone's band is high: ${seen.last()}", seen.last() > 0.6f)
        assertTrue("it wobbles: ${seen.minOrNull()}..${seen.maxOrNull()}", seen.maxOrNull()!! - seen.minOrNull()!! < 0.05f)
        // Far from the tone, the bands are low.
        assertTrue(a.bands[a.bandOf(60f)] < 0.3f && a.bands[a.bandOf(12_000f)] < 0.3f)
    }

    @Test
    fun aSweepMovesUpTheBands() {
        val seconds = 4.0
        var phase = 0.0
        var last = -1
        val peaks = mutableListOf<Int>()
        val sweep: (Int) -> Float = { s ->
            val t = s.toDouble() / rate
            val hz = 100.0 * (8000.0 / 100.0).pow(t / seconds)
            (0.25 * sin(2 * PI * 100.0 * seconds / Math.log(80.0) * ((80.0).pow(t / seconds) - 1))).toFloat()
        }
        play(seconds.toFloat(), each = { t, x -> if (t > 0.3f && x.audible > 0.5f) peaks += x.loudestBand() }, left = sweep)
        // Non-decreasing, give or take a band of jitter, and it covers most of the scale.
        assertTrue(peaks.zipWithNext().all { (p, q) -> q >= p - 1 })
        assertTrue("from ${peaks.first()} to ${peaks.last()}", peaks.last() - peaks.first() > 25)
    }

    @Test
    fun oppositeSidesDoNotCancel() {
        val same = play(1f, right = tone(440.0), left = tone(440.0))
        val opposite = play(1f, right = { s -> -tone(440.0)(s) }, left = tone(440.0))
        val b = same.bandOf(440f)
        assertEquals(same.bands[b], opposite.bands[b], 0.01f)
        assertTrue(opposite.bands[b] > 0.5f)
    }

    @Test
    fun silenceIsStill_andNearSilenceIsNotBlownUp() {
        val silent = play(1f) { 0f }
        assertTrue(silent.bands.all { it < 0.01f } && silent.energy < 0.01f && silent.onsets == 0L)
        val random = Random(3)
        val hiss = play(2f) { (random.nextFloat() - 0.5f) * 2f * 10f.pow(-78f / 20f) }
        assertTrue("hiss shows: ${hiss.bands.maxOrNull()}", hiss.bands.all { it < 0.05f } && hiss.energy < 0.05f)
    }

    @Test
    fun aQuietPassageStaysQuieterThanALoudOne() {
        val random = Random(5)
        val loud = play(3f) { (random.nextFloat() - 0.5f) * 2f * 10f.pow(-12f / 20f) }
        val quiet = play(3f) { (random.nextFloat() - 0.5f) * 2f * 10f.pow(-42f / 20f) }
        assertTrue("loud ${loud.bands.average()} quiet ${quiet.bands.average()}", quiet.bands.average() < loud.bands.average() - 0.25)
        assertTrue(quiet.energy < loud.energy - 0.25f)
    }

    @Test
    fun drumsGiveOnsets_aHeldToneDoesNot() {
        val random = Random(11)
        // A short burst every half second over a quiet bed: eight beats in four seconds.
        val drums = play(4f) { s ->
            val inBeat = s % (rate / 2)
            val hit = if (inBeat < rate / 50) (random.nextFloat() - 0.5f) * 1.6f * (1f - inBeat / (rate / 50f)) else 0f
            hit + 0.01f * sin(2 * PI * 220.0 * s / rate).toFloat()
        }
        assertTrue("onsets: ${drums.onsets}", drums.onsets in 7L..9L)
        val held = play(4f, left = tone(330.0))
        assertTrue("onsets in a held tone: ${held.onsets}", held.onsets <= 1L)
    }

    @Test
    fun bassMiddleAndTrebleGoWhereTheSoundIs() {
        val low = play(1f, left = tone(70.0))
        assertTrue(low.bass > low.middle && low.bass > low.treble)
        val high = play(1f, left = tone(9000.0))
        assertTrue(high.treble > high.bass && high.treble > high.middle)
    }

    @Test
    fun thirtyOrSixtyFramesASecondCometoTheSame() {
        val sixty = play(1.2f, fps = 60, left = tone(500.0))
        val thirty = play(1.2f, fps = 30, left = tone(500.0))
        val b = sixty.bandOf(500f)
        assertEquals(sixty.bands[b], thirty.bands[b], 0.03f)
        assertEquals(sixty.energy, thirty.energy, 0.03f)
        assertEquals(1f - kotlin.math.exp(-1f), smoothingShare(0.2f, 0.2f), 1e-6f)
    }

    @Test
    fun afterTheMusicStopsEverythingSettles() {
        val a = play(1f, left = tone(800.0))
        assertTrue(a.bands.maxOrNull()!! > 0.5f)
        repeat(90) { a.quiet(1f / 60f) }
        assertTrue(a.bands.all { it < 0.01f } && a.energy < 0.01f && a.waveform.all { it == 0f })
    }

    @Test
    fun theWaveformIsTheLatestMoment() {
        val a = play(0.5f, left = tone(100.0, db = 0.0))
        assertEquals(128, a.waveform.size)
        assertTrue(a.waveform.maxOrNull()!! > 0.9f && a.waveform.minOrNull()!! < -0.9f)
    }
}
