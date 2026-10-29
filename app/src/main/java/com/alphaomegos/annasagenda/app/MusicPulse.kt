package com.alphaomegos.annasagenda

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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

/** Passes the sound through unchanged, measuring each slice's loudness on the way. */
@UnstableApi
internal class PulseAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
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
        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        MusicPulse.silence()
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
