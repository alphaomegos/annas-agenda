package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.MusicHeardClock
import com.alphaomegos.annasagenda.support.MusicHeardPosition
import com.alphaomegos.annasagenda.support.MusicSampleRing
import com.alphaomegos.annasagenda.support.MusicStreamBase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sound kept for the visualizer, and which of it is heard (0363). */
class MusicTapTest {

    private val rate = 44_100

    @Test
    fun theRingGivesBackTheLatestFrames_acrossItsEnd() {
        val ring = MusicSampleRing(4096 * 4)
        repeat(20_000) { ring.put(it.toFloat(), -it.toFloat()) }
        ring.publish()
        val l = FloatArray(4); val r = FloatArray(4)
        assertTrue(ring.read(20_000, 4, l, r))
        assertArrayEquals(floatArrayOf(19_996f, 19_997f, 19_998f, 19_999f), l, 0f)
        assertArrayEquals(floatArrayOf(-19_996f, -19_997f, -19_998f, -19_999f), r, 0f)
    }

    @Test
    fun theRingRefusesWhatIsNotThere() {
        val ring = MusicSampleRing(4096 * 4)
        repeat(20_000) { ring.put(1f, 1f) }
        val l = FloatArray(2048); val r = FloatArray(2048)
        assertFalse("put but not published", ring.read(20_000, 2048, l, r))
        ring.publish()
        assertFalse("not yet written", ring.read(20_001, 2048, l, r))
        assertFalse("already written over", ring.read(5_000, 2048, l, r))
        assertFalse("before the first", ring.read(100, 2048, l, r))
        ring.skip(1000)
        ring.publish()
        assertEquals(21_000L, ring.written)
    }

    /** Frames only counted while nobody watched hold older sound: not read (0365). */
    @Test
    fun whatWasOnlyCountedIsNotRead() {
        val ring = MusicSampleRing(4096 * 4)
        repeat(6_000) { ring.put(9f, 9f) }      // an earlier listening
        ring.skip(6_000)                         // nobody watching: counted only
        ring.publish()
        repeat(1_000) { ring.put(1f, 1f) }       // watching again
        ring.publish()
        val l = FloatArray(2048); val r = FloatArray(2048)
        assertFalse("reaches back into the counted frames", ring.read(13_000, 2048, l, r))
        repeat(2_000) { ring.put(1f, 1f) }
        ring.publish()
        assertTrue(ring.read(15_000, 2048, l, r))
        assertTrue(l.all { it == 1f })
    }

    @Test
    fun theHeardFrameIsAtThePlayersPosition() {
        val clock = MusicHeardClock()
        // The stream was flushed at 5 s with frame 1 000 next; 1 s has been written since.
        clock.base = MusicStreamBase(frame = 1_000, positionUs = 5_000_000, sampleRate = rate)
        val written = 1_000L + rate
        clock.heard = MusicHeardPosition(positionUs = 5_500_000, atNanos = 0, playing = true)
        assertEquals(1_000L + rate / 2, clock.heardFrame(0, written))
        // A tenth of a second later the player is a tenth further on.
        assertEquals(1_000L + rate * 6 / 10, clock.heardFrame(100_000_000, written))
        // Twice as fast, twice as far.
        clock.heard = MusicHeardPosition(5_500_000, 0, playing = true, speed = 2f)
        assertEquals(1_000L + rate * 7 / 10, clock.heardFrame(100_000_000, written))
        // Paused, it stays.
        clock.heard = MusicHeardPosition(5_500_000, 0, playing = false)
        assertEquals(1_000L + rate / 2, clock.heardFrame(900_000_000, written))
    }

    @Test
    fun afterOneTrackRanIntoTheNext_theLastDelayStandsIn() {
        val clock = MusicHeardClock()
        clock.base = MusicStreamBase(frame = 0, positionUs = 0, sampleRate = rate)
        clock.heard = MusicHeardPosition(positionUs = 2_000_000, atNanos = 0, playing = true)
        val written = rate * 23L / 10  // 0.3 s ahead of the 2 s heard
        repeat(50) { clock.heardFrame(0, written) }
        assertEquals(rate * 0.3, clock.delayFrames, 1.0)
        // The next track: its position starts again from nought, the frames go on.
        clock.heard = MusicHeardPosition(positionUs = 100_000, atNanos = 0, playing = true)
        val later = written + rate * 5L
        assertEquals(later - Math.round(rate * 0.3), clock.heardFrame(0, later))
    }

    @Test
    fun withNothingKnown_aQuarterOfASecondBehind() {
        val clock = MusicHeardClock()
        assertEquals(100_000L - rate / 4, clock.heardFrame(0, 100_000))
        clock.base = MusicStreamBase(0, 0, 48_000)
        assertEquals(100_000L - 12_000, clock.heardFrame(0, 100_000))
    }

    @Test
    fun aPositionJustAheadOfTheWritten_readsTheNewest() {
        val clock = MusicHeardClock()
        clock.base = MusicStreamBase(0, 0, rate)
        clock.heard = MusicHeardPosition(1_020_000, 0, playing = true)
        assertEquals(rate.toLong(), clock.heardFrame(0, rate.toLong()))
    }
}
