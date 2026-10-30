package com.alphaomegos.annasagenda.screens.music

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.alphaomegos.annasagenda.support.MusicAnalyzer
import com.alphaomegos.annasagenda.support.MusicHeardClock
import com.alphaomegos.annasagenda.support.MusicSampleRing
import com.alphaomegos.annasagenda.support.SPECTRUM_VERTEX_FLOATS
import com.alphaomegos.annasagenda.support.SpectrumPeaks
import com.alphaomegos.annasagenda.support.VortexFilaments
import com.alphaomegos.annasagenda.support.spectrumBars
import com.alphaomegos.annasagenda.support.spectrumFloats
import com.alphaomegos.annasagenda.support.wrapPhase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.max

/** The visualizer's pictures (30.10, Eduard), after Windows Media Player's. */
internal enum class VisualizerPreset { CRIMSON_VORTEX, CLASSIC_SPECTRUM, SPECTRUM_FLAME }

/**
 * Draws the visualizer with OpenGL ES 2.0 (30.10): each frame reads the sound being
 * heard now from [ring] at [clock]'s frame, analyses it (MusicAnalyzer) and draws
 * [preset] — the classic spectrum's bars as triangles, the flame and the vortex as
 * full-view shaders with a feedback picture (two textures, read one, write the other)
 * for their trails, the vortex's filaments as strips over it. Everything is made once
 * (again when the context is lost); nothing is allocated per frame. A shader the
 * device refuses goes to [onError] and the view stays black.
 */
internal class MusicGlRenderer(
    private val ring: MusicSampleRing,
    private val clock: MusicHeardClock,
    private val onError: (String) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile
    var preset: VisualizerPreset = VisualizerPreset.CRIMSON_VORTEX

    /** Nothing moves any more: the music is still and every trail has faded. */
    @Volatile
    var settled: Boolean = false
        private set

    private var analyzer: MusicAnalyzer? = null
    private val left = FloatArray(FFT)
    private val right = FloatArray(FFT)
    private var lastNanos = 0L
    private var failed = false
    private var shown: VisualizerPreset? = null
    private var width = 1
    private var height = 1

    // Programs and where their inputs are.
    private var colorProgram = 0
    private var copyProgram = 0
    private var filamentProgram = 0
    private var flameProgram = 0
    private var vortexProgram = 0

    private val quad = floats(8).apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private val bandBytes: ByteBuffer = ByteBuffer.allocateDirect(BAND_TEXELS).order(ByteOrder.nativeOrder())
    private var bandTexture = 0

    // Feedback: two pictures at part of the view's size, read one, draw the other, swap.
    private val targets = IntArray(2)
    private val targetTextures = IntArray(2)
    private var targetW = 0
    private var targetH = 0
    private var current = 0

    // The classic spectrum.
    private val peaks = SpectrumPeaks(BANDS)
    private val barFloats = FloatArray(spectrumFloats(BANDS))
    private val barBuffer = floats(barFloats.size)

    // The vortex.
    private val filaments = VortexFilaments()
    private val filamentBuffer = floats(filaments.vertices.size)
    private var surge = 0f
    private val phase = FloatArray(4)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context (the first, or after one was lost): every GL thing is made again.
        failed = false
        targetW = 0
        try {
            colorProgram = program(MusicShaders.COLOR_VERTEX, MusicShaders.COLOR_FRAGMENT, "aPos", "aColor")
            copyProgram = program(MusicShaders.QUAD_VERTEX, MusicShaders.COPY_FRAGMENT, "aPos")
            filamentProgram = program(MusicShaders.FILAMENT_VERTEX, MusicShaders.FILAMENT_FRAGMENT, "aPos", "aSide")
            flameProgram = program(MusicShaders.QUAD_VERTEX, MusicShaders.FLAME_FRAGMENT, "aPos")
            vortexProgram = program(MusicShaders.QUAD_VERTEX, MusicShaders.VORTEX_FRAGMENT, "aPos")
        } catch (e: IllegalStateException) {
            failed = true
            onError(e.message ?: "shader")
            return
        }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        bandTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bandTexture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        textureParameters()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, BAND_TEXELS, 1, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bandBytes)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        GLES20.glViewport(0, 0, width, height)
        if (!failed) makeTargets(max(64, width / 2), max(64, height / 2))
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(1e-4f, 0.1f)
        lastNanos = now
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (failed) return
        val a = listen(now, dt)
        // Another picture: the old one's trails do not carry over.
        if (preset != shown) {
            shown = preset
            clearTargets()
        }
        when (preset) {
            VisualizerPreset.CLASSIC_SPECTRUM -> drawSpectrum(a, dt)
            VisualizerPreset.SPECTRUM_FLAME -> drawFlame(a, dt)
            VisualizerPreset.CRIMSON_VORTEX -> drawVortex(a, dt)
        }
    }

    // The sound being heard now through the analysis; silence or a pause lets it settle.
    private fun listen(now: Long, dt: Float): MusicAnalyzer {
        val rate = clock.base?.sampleRate?.takeIf { it > 0 } ?: 44_100
        val a = analyzer?.takeIf { it.sampleRate == rate } ?: MusicAnalyzer(rate).also { analyzer = it }
        val playing = clock.heard?.playing == true
        if (playing && ring.read(clock.heardFrame(now, ring.written), FFT, left, right)) a.update(left, right, dt) else a.quiet(dt)
        settled = !playing && a.energy < 0.002f && a.bands.all { it < 0.002f } && peaks.level.all { it < 0.002f } && surge < 0.002f
        return a
    }

    private fun drawSpectrum(a: MusicAnalyzer, dt: Float) {
        peaks.update(a.bands, dt)
        val vertices = spectrumBars(a.bands, peaks.level, width, height, barFloats)
        if (vertices == 0) return
        barBuffer.position(0)
        barBuffer.put(barFloats, 0, vertices * SPECTRUM_VERTEX_FLOATS)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(colorProgram)
        val stride = SPECTRUM_VERTEX_FLOATS * 4
        barBuffer.position(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, stride, barBuffer)
        GLES20.glEnableVertexAttribArray(0)
        barBuffer.position(2)
        GLES20.glVertexAttribPointer(1, 4, GLES20.GL_FLOAT, false, stride, barBuffer)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        GLES20.glDisableVertexAttribArray(1)
    }

    private fun drawFlame(a: MusicAnalyzer, dt: Float) {
        for (i in 0 until BAND_TEXELS) {
            val v = if (i < a.bands.size) a.bands[i] else 0f
            bandBytes.put(i, (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
        }
        bandBytes.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bandTexture)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, BAND_TEXELS, 1, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bandBytes)
        phase[0] = wrapPhase(phase[0] + dt * 9f)
        phase[1] = wrapPhase(phase[1] + dt * 0.7f)
        val p = flameProgram
        beginFeedback(p)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p, "uBands"), 1)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(p, "uPhase"), phase[0], phase[1], 0f, 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uTreble"), a.treble)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uRetain"), exp(-dt / FLAME_TRAIL))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uRise"), dt * 0.12f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uBandsUsed"), a.bands.size.toFloat())
        drawQuad()
        showFeedback()
    }

    private fun drawVortex(a: MusicAnalyzer, dt: Float) {
        surge = max(a.onset, surge * exp(-dt / 0.25f))
        // Phases a turn long: the flow's terms repeat every 4π, the others every 2π.
        phase[0] = wrapPhase(phase[0] + dt * (0.35f + 1.1f * a.bass + 0.6f * surge), period = 4f * Math.PI.toFloat())
        phase[1] = wrapPhase(phase[1] + dt * 0.2f)
        phase[2] = wrapPhase(phase[2] + dt * 0.13f)
        phase[3] = wrapPhase(phase[3] + dt * 0.17f)
        val aspect = width.toFloat() / height
        val p = vortexProgram
        beginFeedback(p)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(p, "uPhase"), phase[0], phase[1], phase[2], phase[3])
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uCenter"), 0.02f * kotlin.math.sin(phase[3]), 0.02f * kotlin.math.cos(phase[2]))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uBass"), a.bass)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uTreble"), a.treble)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uEnergy"), a.energy)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uSurge"), surge)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uRetain"), exp(-dt / VORTEX_TRAIL))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uZoom"), 1f + dt * 60f * (0.006f + 0.02f * a.bass + 0.03f * surge))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uTurn"), dt * 0.25f)
        drawQuad()
        // The filaments leave trails too: wide and faint into the feedback, then sharp on top.
        filaments.update(dt, a.bass, a.middle, a.treble, surge, a.energy, a.waveform, aspect, GLOW_HALF_WIDTH_PX * 2f / height)
        drawFilaments(0.22f)
        showFeedback()
        filaments.update(0f, a.bass, a.middle, a.treble, surge, a.energy, a.waveform, aspect, SHARP_HALF_WIDTH_PX * 2f / height)
        drawFilaments(0.9f)
    }

    private fun drawFilaments(alpha: Float) {
        filamentBuffer.position(0)
        filamentBuffer.put(filaments.vertices)
        GLES20.glUseProgram(filamentProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        filamentBuffer.position(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 12, filamentBuffer)
        GLES20.glEnableVertexAttribArray(0)
        filamentBuffer.position(2)
        GLES20.glVertexAttribPointer(1, 1, GLES20.GL_FLOAT, false, 12, filamentBuffer)
        GLES20.glEnableVertexAttribArray(1)
        val colour = GLES20.glGetUniformLocation(filamentProgram, "uColor")
        for (k in 0 until filaments.count) {
            val c = FILAMENT_COLOURS[k % FILAMENT_COLOURS.size]
            GLES20.glUniform4f(colour, c[0], c[1], c[2], alpha)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, filaments.first(k), filaments.vertexCount)
        }
        GLES20.glDisableVertexAttribArray(1)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // Draw into the picture not being read, with the one last drawn as uPrev.
    private fun beginFeedback(p: Int) {
        val read = current
        val write = 1 - current
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targets[write])
        GLES20.glViewport(0, 0, targetW, targetH)
        GLES20.glUseProgram(p)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTextures[read])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p, "uPrev"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uSize"), width.toFloat(), height.toFloat())
    }

    // The picture just drawn onto the view, and it becomes the one read next time.
    private fun showFeedback() {
        current = 1 - current
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(copyProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTextures[current])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgram, "uTex"), 0)
        drawQuad()
    }

    private fun drawQuad() {
        quad.position(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun makeTargets(w: Int, h: Int) {
        if (w == targetW && h == targetH) return
        if (targetW != 0) {
            GLES20.glDeleteFramebuffers(2, targets, 0)
            GLES20.glDeleteTextures(2, targetTextures, 0)
        }
        targetW = w
        targetH = h
        GLES20.glGenFramebuffers(2, targets, 0)
        GLES20.glGenTextures(2, targetTextures, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTextures[i])
            textureParameters()
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targets[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, targetTextures[i], 0)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            failed = true
            onError("framebuffer: 0x" + Integer.toHexString(status))
        }
    }

    private fun clearTargets() {
        if (targetW == 0) return
        for (i in 0..1) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targets[i])
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun textureParameters() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private companion object {
        const val FFT = 2048
        const val BANDS = 48
        const val BAND_TEXELS = 64
        const val FLAME_TRAIL = 0.12f
        const val VORTEX_TRAIL = 0.35f
        const val GLOW_HALF_WIDTH_PX = 5f
        const val SHARP_HALF_WIDTH_PX = 1.6f
        val FILAMENT_COLOURS = arrayOf(floatArrayOf(1f, 0.95f, 0.88f), floatArrayOf(1f, 0.8f, 0.85f), floatArrayOf(1f, 0.7f, 0.74f))

        fun floats(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        fun shader(type: Int, source: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, source)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw IllegalStateException("shader: $log")
            }
            return s
        }

        fun program(vertex: String, fragment: String, vararg attributes: String): Int {
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
            attributes.forEachIndexed { i, name -> GLES20.glBindAttribLocation(p, i, name) }
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("program: " + GLES20.glGetProgramInfoLog(p))
            return p
        }
    }
}
