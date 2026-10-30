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
import com.alphaomegos.annasagenda.support.PulseBeats
import com.alphaomegos.annasagenda.support.pulseLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The music's pulse (29.10): the player's own sound, listened to on its way
 * out — no microphone, no permission. The level and a beat count, for the
 * visualizer to draw from.
 */
internal object MusicPulse {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _beats = MutableStateFlow(0L)
    val beats: StateFlow<Long> = _beats.asStateFlow()
    private val detector = PulseBeats()

    fun feed(rms: Float) {
        val l = pulseLevel(rms)
        _level.value = l
        if (detector.feed(l, System.nanoTime())) _beats.value = _beats.value + 1
    }

    fun silence() {
        _level.value = 0f
    }
}

/**
 * The sound kept for the visualizer (30.10): the samples on their way out and which
 * of them is heard now (support/MusicTap.kt). The player's thread writes [ring] and
 * the stream's [clock] base; the service notes the player's position into [clock]
 * while [listening]; the drawing reads both.
 */
internal object MusicTap {
    val ring = MusicSampleRing()
    val clock = MusicHeardClock()

    /** The visualizer is on screen: samples are kept, the position noted often. */
    @Volatile
    var listening = false
        private set

    /** Set by the playing service: starts its noting of the position. */
    @Volatile
    var wake: (() -> Unit)? = null

    fun listen(on: Boolean) {
        listening = on
        if (on) wake?.invoke()
    }
}

/**
 * Passes the sound through unchanged, measuring each slice's loudness on the way, and
 * keeping its samples for the visualizer while one is on screen (30.10).
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
        val view = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        if (encoding == C.ENCODING_PCM_16BIT) {
            while (view.remaining() >= 2) { val v = view.short / 32768.0; sum += v * v; n++ }
        } else {
            while (view.remaining() >= 4) { val v = view.float.toDouble(); sum += v * v; n++ }
        }
        if (n > 0) MusicPulse.feed(kotlin.math.sqrt(sum / n).toFloat())
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
        MusicPulse.silence()
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
