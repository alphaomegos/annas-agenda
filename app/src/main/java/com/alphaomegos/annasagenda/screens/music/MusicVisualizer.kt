package com.alphaomegos.annasagenda.screens.music

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.session.MediaController
import com.alphaomegos.annasagenda.MusicTap
import com.alphaomegos.annasagenda.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.alphaomegos.annasagenda.support.cycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

/** What each picture is called. */
private val VisualizerPreset.label: Int
    get() = when (this) {
        VisualizerPreset.CRIMSON_VORTEX -> R.string.music_viz_vortex
        VisualizerPreset.CLASSIC_SPECTRUM -> R.string.music_viz_spectrum
        VisualizerPreset.SPECTRUM_FLAME -> R.string.music_viz_flame
        VisualizerPreset.RAINBOW_RIBBON -> R.string.music_viz_ribbon
    }

/**
 * The music drawn as it is heard (30.10, Eduard: «like Windows Media Player's»): an
 * OpenGL ES view (MusicGlRenderer) drawing [preset] from the sound being heard now.
 * While it is on screen the sound is kept for it (MusicTap.listen); it stops drawing
 * when the music has stopped and everything has settled, and with the screen paused.
 * A shader the phone refuses shows as its message on black, not as a crash.
 */
@Composable
internal fun MusicVisualizer(preset: VisualizerPreset, modifier: Modifier = Modifier) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val renderer = remember {
        val main = Handler(Looper.getMainLooper())
        MusicGlRenderer(MusicTap.ring, MusicTap.clock) { message -> main.post { error = message } }
    }
    val view = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    SideEffect { renderer.preset = preset }
    DisposableEffect(lifecycle, view) {
        MusicTap.listen(true)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.onPause()
            MusicTap.listen(false)
        }
    }
    // Still music and nothing left moving: draw only when asked, until it plays again.
    LaunchedEffect(view) {
        while (true) {
            val idle = renderer.settled && MusicTap.clock.heard?.playing != true
            val mode = if (idle) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
            if (view.renderMode != mode) view.renderMode = mode
            delay(250)
        }
    }
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    }
}

/**
 * The visualizer under the player (29.10): half the screen's height, the picture
 * to pick, and a button to fill the screen. While it fills the screen this one
 * stands black: one view drawing at a time.
 */
@Composable
internal fun MusicVisualizerPanel(controller: MediaController) {
    var preset by rememberSaveable { mutableStateOf(VisualizerPreset.CRIMSON_VORTEX) }
    var full by rememberSaveable { mutableStateOf(false) }
    val half = (LocalConfiguration.current.screenHeightDp / 2).dp
    Box(modifier = Modifier.fillMaxWidth().height(half).background(Color.Black)) {
        if (!full) MusicVisualizer(preset, Modifier.fillMaxSize())
        // Light on the black, in either theme; three names may not fit a folded phone: they scroll.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp, end = 56.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            VisualizerPreset.entries.forEach { p ->
                FilterChip(
                    selected = preset == p,
                    onClick = { preset = p },
                    label = { Text(stringResource(p.label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White,
                        selectedLabelColor = Color.White,
                        selectedContainerColor = Color.White.copy(alpha = 0.22f),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = preset == p,
                        borderColor = Color.White.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        IconButton(onClick = { full = true }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.music_viz_full), tint = Color.White)
        }
    }
    if (full) MusicVisualizerFull(preset, controller, onPreset = { preset = it }) { full = false }
}

/**
 * The visualizer filling the screen (29.10): the player's controls drawn over
 * it, gone after 5 seconds untouched; a tap brings them back. A swipe left or
 * right goes to the next picture or back round them (31.10, Eduard), its name
 * shown for a moment; TalkBack has the same as two actions.
 */
@Composable
private fun MusicVisualizerFull(
    preset: VisualizerPreset,
    controller: MediaController,
    onPreset: (VisualizerPreset) -> Unit,
    onClose: () -> Unit,
) {
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableStateOf(0) }
    LaunchedEffect(touched) {
        shown = true
        delay(5_000)
        shown = false
    }
    var swipes by rememberSaveable { mutableStateOf(0) }
    var nameShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(swipes) {
        if (swipes > 0) {
            nameShown = true
            delay(2_500)
            nameShown = false
        }
    }
    val pictures = VisualizerPreset.entries
    val currentPreset by rememberUpdatedState(preset)
    val choose by rememberUpdatedState(onPreset)
    fun step(by: Int) {
        choose(pictures[cycle(pictures.indexOf(currentPreset), pictures.size, by)])
        swipes++
    }
    val nextName = stringResource(R.string.music_viz_next)
    val previousName = stringResource(R.string.music_viz_previous)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    val far = 56.dp.toPx()
                    var travel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travel = 0f },
                        onDragEnd = { if (abs(travel) > far) step(if (travel < 0f) 1 else -1) },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            travel += amount
                        },
                    )
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(nextName) { step(1); true },
                        CustomAccessibilityAction(previousName) { step(-1); true },
                    )
                }
                .clickable { touched++ },
        ) {
            MusicVisualizer(preset, Modifier.fillMaxSize())
            if (nameShown) {
                Text(
                    text = stringResource(preset.label),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (shown) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.music_viz_close), tint = Color.White)
                }
                Row(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                    IconButton(onClick = { touched++; controller.seekToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.music_previous), tint = Color.White)
                    }
                    IconButton(onClick = { touched++; if (controller.isPlaying) controller.pause() else controller.play() }) {
                        Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = stringResource(if (controller.isPlaying) R.string.music_pause else R.string.music_play), tint = Color.White)
                    }
                    IconButton(onClick = { touched++; controller.seekToNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.music_next), tint = Color.White)
                    }
                }
            }
        }
    }
}
