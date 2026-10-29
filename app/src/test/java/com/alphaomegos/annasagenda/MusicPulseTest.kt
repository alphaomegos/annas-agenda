package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.PulseBeats
import com.alphaomegos.annasagenda.support.pulseLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The music's pulse (0349): a level from loudness, beats from levels well above their average. */
class MusicPulseTest {
    @Test
    fun theLevel() {
        assertEquals(0.3f, pulseLevel(0.1f), 1e-6f)
        assertEquals(1f, pulseLevel(0.9f), 0f)
    }

    @Test
    fun aBeatIsALevelWellAboveTheAverage_notTooOften() {
        val beats = PulseBeats()
        var t = 0L
        repeat(50) { assertFalse(beats.feed(0.1f, t)); t += 20_000_000L }
        assertTrue(beats.feed(0.6f, t))
        // Too soon after the last: not another beat.
        assertFalse(beats.feed(0.7f, t + 100_000_000L))
        // A whisper is never a beat.
        val quiet = PulseBeats()
        assertFalse(quiet.feed(0.05f, 0L))
    }
}
