package com.alphaomegos.annasagenda.support

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * What the music visualizer draws from (30.10): the sound being heard, analysed
 * the moment a frame is drawn — a spectrum in log-spaced bands, a short waveform,
 * the overall energy, bass / middle / treble, and onsets (sudden rises) for the
 * accents. Pure Kotlin, nothing allocated per frame, tested on made-up sound.
 *
 * Stereo is never read as one stream: both channels go through one complex FFT
 * (left as the real part, right as the imaginary) and their powers are added,
 * |L|² + |R|² = (|Z[k]|² + |Z[n−k]|²) / 2, so a sound in opposite phase on the
 * two sides does not cancel out.
 */

/** A radix-2 FFT of [n] complex values in place, n a power of two; its tables are made once. */
class Fft(val n: Int) {
    init {
        require(n >= 2 && (n and (n - 1)) == 0) { "n must be a power of two: $n" }
    }

    private val bits = Integer.numberOfTrailingZeros(n)
    private val reversed = IntArray(n) { Integer.reverse(it) ushr (32 - bits) }
    private val cosine = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sine = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }

    /** X[k] = Σ x[j]·e^(−2πi·jk/n), written over [re] and [im]. */
    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (a in start until start + half) {
                    val b = a + half
                    val c = cosine[k]
                    val s = -sine[k]
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }
}

/** How the analysis responds; the defaults follow the brief and are tuned by eye. */
data class MusicAnalysisTuning(
    val fftSize: Int = 2048,
    val bandCount: Int = 48,
    val lowHz: Float = 32f,
    val highHz: Float = 16_000f,
    /** Bands rise this fast and fall this slowly (seconds, the time constant). */
    val bandAttack: Float = 0.025f,
    val bandRelease: Float = 0.22f,
    /** Bass, middle, treble and the energy. */
    val levelAttack: Float = 0.02f,
    val levelRelease: Float = 0.2f,
    /** The loudness the top of the scale follows: slowly, and within bounds. */
    val referenceRise: Float = 0.3f,
    val referenceFall: Float = 6f,
    val referenceMinDb: Float = -36f,
    val referenceMaxDb: Float = -6f,
    /** A band's scale: this many decibels from nothing to the top. */
    val rangeDb: Float = 48f,
    /** Below the first the sound is silence; above the second, fully shown. */
    val silenceDb: Float = -66f,
    val audibleDb: Float = -48f,
    /** Onsets: the threshold over the recent mean, the gap between two, the fade of one. */
    val onsetSensitivity: Float = 1.5f,
    val onsetMinimumRiseDb: Float = 0.6f,
    val onsetRefractory: Float = 0.12f,
    val onsetFade: Float = 0.15f,
    val onsetMemory: Float = 1f,
    /** Points in the waveform. */
    val wavePoints: Int = 128,
)

/**
 * The analysis, kept between frames. [update] takes the [MusicAnalysisTuning.fftSize]
 * samples that end at the moment being heard (the right channel null for mono) and
 * how long since the last frame; the results are the public arrays and numbers,
 * overwritten in place.
 */
class MusicAnalyzer(val sampleRate: Int, val tuning: MusicAnalysisTuning = MusicAnalysisTuning()) {
    private val n = tuning.fftSize
    private val fft = Fft(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val power = FloatArray(n / 2 + 1)
    private val binHz = sampleRate.toFloat() / n

    /** The bands' edges and centres, low to high, log-spaced. */
    val bandLowHz = FloatArray(tuning.bandCount)
    val bandHighHz = FloatArray(tuning.bandCount)
    val bandCenterHz = FloatArray(tuning.bandCount)

    /** Each band, 0..1, smoothed: what the bars and the flame stand on. */
    val bands = FloatArray(tuning.bandCount)

    /** Each band's level this frame in decibels, before scaling and smoothing (for the onsets). */
    val bandDb = FloatArray(tuning.bandCount)
    private val previousDb = FloatArray(tuning.bandCount)
    private var hasPrevious = false

    /** The last moment's shape, −1..1, mono (both channels' mean), for the filaments. */
    val waveform = FloatArray(tuning.wavePoints)

    var energy = 0f; private set
    var bass = 0f; private set
    var middle = 0f; private set
    var treble = 0f; private set

    /** The latest onset's strength, 0..1, fading; and how many there have been. */
    var onset = 0f; private set
    var onsets = 0L; private set

    /** The decibel level the top of the scale stands at now. */
    var referenceDb = -20f; private set

    /** How audible the sound is, 0 (silence) to 1. */
    var audible = 0f; private set

    private var fluxMean = 0f
    private var fluxDeviation = 0f
    private var sinceOnset = Float.MAX_VALUE

    // The FFT's own gain: a full-scale sine through the Hann window peaks at (n/4)².
    private val fullScale = (n / 4.0).pow(2).toFloat()

    init {
        val ratio = ln(tuning.highHz / tuning.lowHz) / tuning.bandCount
        for (b in 0 until tuning.bandCount) {
            bandLowHz[b] = (tuning.lowHz * exp(ratio * b))
            bandHighHz[b] = (tuning.lowHz * exp(ratio * (b + 1)))
            bandCenterHz[b] = sqrt(bandLowHz[b] * bandHighHz[b])
        }
    }

    fun update(left: FloatArray, right: FloatArray?, dtSeconds: Float) {
        require(left.size >= n && (right == null || right.size >= n))
        val dt = dtSeconds.coerceIn(1e-4f, 0.25f)
        val offset = left.size - n
        var sum = 0.0
        for (i in 0 until n) {
            val l = left[offset + i]
            val r = right?.get(offset + i) ?: 0f
            re[i] = l * window[i]
            im[i] = r * window[i]
            sum += if (right == null) (l * l).toDouble() else ((l * l + r * r) / 2f).toDouble()
        }
        val rmsDb = 10f * log10((sum / n).toFloat() + 1e-12f)
        val stretch = (n - 1).toFloat() / (tuning.wavePoints - 1)
        for (p in 0 until tuning.wavePoints) {
            val i = offset + (p * stretch).toInt()
            waveform[p] = if (right == null) left[i] else (left[i] + right[i]) / 2f
        }
        fft.transform(re, im)
        // |L|² + |R|² from one transform (see the top of the file); a mono signal is all in L.
        for (k in 0..n / 2) {
            val m = (n - k) % n
            val a = re[k] * re[k] + im[k] * im[k]
            val b = re[m] * re[m] + im[m] * im[m]
            power[k] = if (right == null) a else (a + b) / 2f
        }
        audible = smoothstep(tuning.silenceDb, tuning.audibleDb, rmsDb)

        var loudest = -120f
        for (b in 0 until tuning.bandCount) {
            val db = bandPowerDb(b)
            bandDb[b] = db
            if (db > loudest) loudest = db
        }
        // The top of the scale follows the loudest band — slowly, within bounds, and not in silence.
        if (audible > 0.5f) {
            val target = loudest.coerceIn(tuning.referenceMinDb, tuning.referenceMaxDb)
            val tau = if (target > referenceDb) tuning.referenceRise else tuning.referenceFall
            referenceDb += (target - referenceDb) * smoothingShare(dt, tau)
        }
        val floorDb = referenceDb - tuning.rangeDb
        var low = 0f; var lowN = 0; var mid = 0f; var midN = 0; var high = 0f; var highN = 0
        for (b in 0 until tuning.bandCount) {
            val target = ((bandDb[b] - floorDb) / tuning.rangeDb).coerceIn(0f, 1f) * audible
            val tau = if (target > bands[b]) tuning.bandAttack else tuning.bandRelease
            bands[b] += (target - bands[b]) * smoothingShare(dt, tau)
            val c = bandCenterHz[b]
            when {
                c < 250f -> { low += target; lowN++ }
                c < 4000f -> { mid += target; midN++ }
                else -> { high += target; highN++ }
            }
        }
        bass = follow(bass, if (lowN > 0) low / lowN else 0f, dt)
        middle = follow(middle, if (midN > 0) mid / midN else 0f, dt)
        treble = follow(treble, if (highN > 0) high / highN else 0f, dt)
        energy = follow(energy, ((rmsDb - floorDb) / tuning.rangeDb).coerceIn(0f, 1f) * audible, dt)

        // Onsets: the bands' rise since the last frame, per sixtieth of a second, over an
        // adaptive threshold — the recent mean and spread — with a gap after each.
        var flux = 0f
        if (hasPrevious) {
            for (b in 0 until tuning.bandCount) {
                val rise = bandDb[b] - previousDb[b]
                if (rise > 0f) flux += rise
            }
            flux = flux / tuning.bandCount * (1f / 60f) / dt
        }
        System.arraycopy(bandDb, 0, previousDb, 0, tuning.bandCount)
        hasPrevious = true
        val threshold = fluxMean + tuning.onsetSensitivity * fluxDeviation + tuning.onsetMinimumRiseDb
        sinceOnset += dt
        onset *= exp(-dt / tuning.onsetFade)
        if (flux > threshold && sinceOnset >= tuning.onsetRefractory && audible > 0.5f) {
            onset = maxOf(onset, ((flux - threshold) / threshold).coerceIn(0.2f, 1f))
            onsets++
            sinceOnset = 0f
        }
        val memory = smoothingShare(dt, tuning.onsetMemory)
        fluxDeviation += (kotlin.math.abs(flux - fluxMean) - fluxDeviation) * memory
        fluxMean += (flux - fluxMean) * memory
    }

    /** Silence: everything falls as it would, with nothing new coming in. */
    fun quiet(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(1e-4f, 0.25f)
        for (b in bands.indices) bands[b] += (0f - bands[b]) * smoothingShare(dt, tuning.bandRelease)
        bass = follow(bass, 0f, dt); middle = follow(middle, 0f, dt); treble = follow(treble, 0f, dt)
        energy = follow(energy, 0f, dt)
        onset *= exp(-dt / tuning.onsetFade)
        waveform.fill(0f)
        audible = 0f
        hasPrevious = false
    }

    // A band's power in dB below full scale, tilted +3 dB an octave about 1 kHz so that music,
    // which falls about that much, looks level. A band narrower than a bin is not invented: it
    // reads the spectrum between the two bins round its centre.
    private fun bandPowerDb(b: Int): Float {
        val lo = bandLowHz[b] / binHz
        val hi = bandHighHz[b] / binHz
        val p = if (hi - lo < 1f) {
            val x = (bandCenterHz[b] / binHz).coerceIn(0f, (n / 2).toFloat())
            val i = floor(x).toInt().coerceAtMost(n / 2 - 1)
            val f = x - i
            power[i] * (1f - f) + power[i + 1] * f
        } else {
            val from = kotlin.math.ceil(lo).toInt().coerceIn(0, n / 2)
            val to = (kotlin.math.ceil(hi).toInt() - 1).coerceIn(from, n / 2)
            var s = 0f
            for (k in from..to) s += power[k]
            s / (to - from + 1)
        }
        val tilt = 3f * (ln(bandCenterHz[b] / 1000f) / ln(2f))
        return 10f * log10(p / fullScale + 1e-12f) + tilt
    }

    private fun follow(now: Float, target: Float, dt: Float): Float =
        now + (target - now) * smoothingShare(dt, if (target > now) tuning.levelAttack else tuning.levelRelease)
}

/** The share of the way to a target covered in [dt] with time constant [tau]: 1 − e^(−dt/τ), any frame rate. */
fun smoothingShare(dt: Float, tau: Float): Float = if (tau <= 0f) 1f else 1f - exp(-dt / tau)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
