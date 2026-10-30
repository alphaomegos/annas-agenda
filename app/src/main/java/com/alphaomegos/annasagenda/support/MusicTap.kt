package com.alphaomegos.annasagenda.support

/*
 * The sound kept for the visualizer (30.10): the player's samples as they pass
 * on their way out, and which of them is being heard now.
 *
 * The audio processor sees the sound before it is played — the sink holds a
 * quarter of a second or more ahead — so drawing what it has just seen draws
 * early. Here every sample has an index; a flush (a seek, a new stream) says
 * which stream position the next sample is; the player says where it is in
 * the stream. The index being heard is the one at the player's position.
 * Where the two do not meet — after one track ran into the next with no flush,
 * the player's position starts again from nought while the samples go on — the
 * delay last measured stands in: the sink's lead does not change with the track.
 */

/**
 * The last [capacity] frames, left and right, written by one thread (the player's)
 * and read by another (the drawing's). A frame's index only grows; [written] is
 * published after each buffer.
 */
class MusicSampleRing(val capacity: Int = 1 shl 17) {
    init {
        require(capacity >= 4096 && (capacity and (capacity - 1)) == 0) { "capacity: a power of two, 4096 or more" }
    }

    private val mask = (capacity - 1).toLong()
    private val left = FloatArray(capacity)
    private val right = FloatArray(capacity)

    /** The next frame's index, on the writing thread. */
    var cursor: Long = 0L
        private set

    /** Frames up to here may be read. */
    @Volatile
    var written: Long = 0L
        private set

    /** Frames from here on were kept: what lies before was only counted, and holds older sound. */
    @Volatile
    var keptFrom: Long = 0L
        private set

    /** Writes one frame (a mono sound twice). */
    fun put(l: Float, r: Float) {
        val i = (cursor and mask).toInt()
        left[i] = l
        right[i] = r
        cursor++
    }

    /** Counts [frames] that passed without being kept (nothing is watching). */
    fun skip(frames: Int) {
        cursor += frames
        keptFrom = cursor
    }

    /** Makes what was put so far readable. */
    fun publish() {
        written = cursor
    }

    /**
     * The [n] frames ending just before [end] into [outLeft] and [outRight]; false when
     * they are not all here — not yet written, or already written over (a margin is
     * kept from the oldest, which the writer may be overwriting).
     */
    fun read(end: Long, n: Int, outLeft: FloatArray, outRight: FloatArray): Boolean {
        val top = written
        if (end > top || end - n < top - capacity + SAFETY || end - n < keptFrom) return false
        for (k in 0 until n) {
            val i = ((end - n + k) and mask).toInt()
            outLeft[k] = left[i]
            outRight[k] = right[i]
        }
        return true
    }

    private companion object {
        const val SAFETY = 8192
    }
}

/** Where a stream starts: frame [frame] is stream position [positionUs], at [sampleRate]. */
data class MusicStreamBase(val frame: Long, val positionUs: Long, val sampleRate: Int)

/** The player's position [positionUs] as of [atNanos], whether it moves, and how fast. */
data class MusicHeardPosition(val positionUs: Long, val atNanos: Long, val playing: Boolean, val speed: Float = 1f)

/**
 * Which frame is heard at [nowNanos], kept by the drawing thread: the stream base and
 * the player's position, both set by other threads, read whole.
 */
class MusicHeardClock(private val defaultDelayMs: Int = 250, private val longestDelayMs: Int = 3000) {
    @Volatile
    var base: MusicStreamBase? = null

    @Volatile
    var heard: MusicHeardPosition? = null

    /** The lead of the written over the heard, in frames, as last measured (smoothed). */
    var delayFrames: Double = Double.NaN
        private set

    /** The player's position now: still when paused, moving at its speed when playing. */
    fun positionUsAt(nowNanos: Long): Long? {
        val h = heard ?: return null
        if (!h.playing) return h.positionUs
        return h.positionUs + ((nowNanos - h.atNanos) / 1000.0 * h.speed).toLong()
    }

    /** The frame being heard, given the frames [written] so far. */
    fun heardFrame(nowNanos: Long, written: Long): Long {
        val b = base
        val p = positionUsAt(nowNanos)
        if (b != null && p != null && b.sampleRate > 0) {
            val frame = b.frame + Math.round((p - b.positionUs) * b.sampleRate / 1_000_000.0)
            val lead = written - frame
            if (lead >= 0 && lead <= longestDelayMs.toLong() * b.sampleRate / 1000) {
                delayFrames = if (delayFrames.isNaN()) lead.toDouble() else delayFrames * 0.9 + lead * 0.1
                return frame
            }
            if (lead < 0 && -lead <= b.sampleRate / 10) return written
        }
        val rate = b?.sampleRate?.takeIf { it > 0 } ?: 44_100
        val lead = if (delayFrames.isNaN()) defaultDelayMs.toLong() * rate / 1000 else Math.round(delayFrames)
        return written - lead
    }
}
