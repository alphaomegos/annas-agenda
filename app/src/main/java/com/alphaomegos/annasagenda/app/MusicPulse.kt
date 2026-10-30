package com.alphaomegos.annasagenda

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.alphaomegos.annasagenda.support.MusicHeardClock
import com.alphaomegos.annasagenda.support.MusicSampleRing
import com.alphaomegos.annasagenda.support.MusicStreamBase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * The sound kept for the visualizer (30.10): the samples on their way out and which
 * of them is heard now (support/MusicTap.kt). The player's thread writes [ring] and
 * the stream's [clock] base; the service notes the player's position into [clock]
 * while [listening]; the drawing reads both.
 */
internal object MusicTap {
    val ring = MusicSampleRing()
    val clock = MusicHeardClock()

    // How many visualizers are on screen (the panel and the full screen may overlap a moment).
    private val listeners = AtomicInteger(0)

    /** A visualizer is on screen: samples are kept, the position noted often. */
    val listening: Boolean get() = listeners.get() > 0

    /** Set by the playing service: starts its noting of the position. */
    @Volatile
    var wake: (() -> Unit)? = null

    /** A visualizer came ([on]) or went. */
    fun listen(on: Boolean) {
        if (on) {
            listeners.incrementAndGet()
            wake?.invoke()
        } else {
            listeners.updateAndGet { maxOf(0, it - 1) }
        }
    }
}

/**
 * Passes the sound through unchanged, keeping its samples for the visualizer while
 * one is on screen (30.10; until then it measured each slice's loudness).
 */
@UnstableApi
internal class PulseAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var channels = 1
    private var rate = 44_100

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        rate = inputAudioFormat.sampleRate
        // PCM it can read; anything else goes by untouched (the processor stays inactive).
        return if (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) inputAudioFormat
        else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        keep(inputBuffer)
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    // Left and right of each frame into the ring — the first two channels, a mono sound
    // on both sides — or only counted, when nothing is watching.
    private fun keep(buffer: ByteBuffer) {
        val ring = MusicTap.ring
        val sixteen = encoding == C.ENCODING_PCM_16BIT
        val frames = buffer.remaining() / ((if (sixteen) 2 else 4) * channels)
        if (!MusicTap.listening) {
            ring.skip(frames)
        } else {
            val view = buffer.duplicate().order(ByteOrder.nativeOrder())
            fun next(): Float = if (sixteen) view.short / 32768f else view.float
            repeat(frames) {
                val l = next()
                val r = if (channels >= 2) next() else l
                repeat(channels - 2) { next() }
                ring.put(l, r)
            }
        }
        ring.publish()
    }

    // A flush (a seek, a new stream): the next sample is this stream position.
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        MusicTap.clock.base = MusicStreamBase(MusicTap.ring.cursor, streamMetadata.positionOffsetUs.coerceAtLeast(0L), rate)
    }
}

/** The player's renderers with the pulse listening in the audio sink. */
@UnstableApi
internal class PulseRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setAudioProcessors(arrayOf<AudioProcessor>(PulseAudioProcessor()))
            .build()
}
