package com.alphaomegos.annasagenda.support

/*
 * The music's pulse for the visualizer (29.10, Eduard: «pictures that move
 * with the music, made on the fly, not a heavier APK»): from the loudness of
 * each slice of sound the player plays, a smoothed level and the beats —
 * a level well above the recent average, no closer than a quick tempo allows.
 */

/** Loudness of a slice to a level 0..1 (music's RMS sits low: ×3 makes room). */
fun pulseLevel(rms: Float): Float = (rms * 3f).coerceIn(0f, 1f)

/**
 * Beats from levels: one when the level rises 40 % above its running average
 * (and above a whisper), at most one per [minGapNanos] — 280 ms, 214 bpm.
 */
class PulseBeats(private val minGapNanos: Long = 280_000_000L) {
    private var average = 0f
    private var lastBeat = Long.MIN_VALUE / 2
    private var warm = false

    /** Feeds a level heard at [nowNanos]; true when it is a beat. */
    fun feed(level: Float, nowNanos: Long): Boolean {
        // The first level sets the average: from silence, the first sound is not a beat.
        if (!warm) {
            warm = true
            average = level
            return false
        }
        val beat = level > average * 1.4f && level > 0.08f && nowNanos - lastBeat >= minGapNanos
        average = average * 0.95f + level * 0.05f
        if (beat) lastBeat = nowNanos
        return beat
    }
}
