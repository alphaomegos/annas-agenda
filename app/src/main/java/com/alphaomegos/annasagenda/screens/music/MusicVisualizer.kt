package com.alphaomegos.annasagenda.screens.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController
import com.alphaomegos.annasagenda.MusicPulse
import com.alphaomegos.annasagenda.R
import kotlinx.coroutines.delay

/** The pictures the visualizer has (29.10): pulsing waves, a metronome; more later. */
internal enum class VisualizerStyle { WAVES, METRONOME }

/**
 * The music drawn as it plays (29.10, Eduard: «like Windows Media Player's»):
 * made on the fly from the player's pulse — nothing stored, nothing added to
 * the APK but this code. Waves: a ring rises on each beat and spreads, the
 * centre breathes with the level. Metronome: the arm swings to the other side
 * on each beat.
 */
@Composable
internal fun MusicVisualizer(style: VisualizerStyle, modifier: Modifier = Modifier) {
    val level by MusicPulse.level.collectAsState()
    val beats by MusicPulse.beats.collectAsState()
    val now by produceState(0L) { while (true) withFrameNanos { value = it } }
    // The rings' births, in frame time; a ring lives 1.6 s. Lost on rotation: a picture, not data.
    val rings = remember { mutableStateListOf<Long>() }
    var side by remember { mutableStateOf(1f) }
    LaunchedEffect(beats) {
        if (beats > 0) {
            rings.add(now)
            side = -side
        }
    }
    val swing by animateFloatAsState(side * 28f, tween(360), label = "metronome")
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val reach = minOf(size.width, size.height) / 2
        when (style) {
            VisualizerStyle.WAVES -> {
                rings.removeAll { now - it > 1_600_000_000L }
                rings.forEach { born ->
                    val age = ((now - born) / 1_600_000_000f).coerceIn(0f, 1f)
                    drawCircle(
                        color = accent.copy(alpha = (1f - age) * 0.8f),
                        radius = reach * (0.2f + 0.8f * age),
                        center = c,
                        style = Stroke(width = (6.dp.toPx() * (1f - age)).coerceAtLeast(1f)),
                    )
                }
                drawCircle(color = primary.copy(alpha = 0.35f + 0.5f * level), radius = reach * (0.12f + 0.25f * level), center = c)
            }
            VisualizerStyle.METRONOME -> {
                val pivot = Offset(size.width / 2, size.height * 0.88f)
                val arm = size.height * 0.72f
                rotate(degrees = swing, pivot = pivot) {
                    drawLine(primary, pivot, Offset(pivot.x, pivot.y - arm), strokeWidth = 6.dp.toPx())
                    drawCircle(accent, radius = 10.dp.toPx() + 10.dp.toPx() * level, center = Offset(pivot.x, pivot.y - arm * 0.7f))
                }
                drawCircle(primary, radius = 8.dp.toPx(), center = pivot)
            }
        }
    }
}

/**
 * The visualizer under the player (29.10): half the screen's height, the
 * style to pick, and a button to fill the screen.
 */
@Composable
internal fun MusicVisualizerPanel(controller: MediaController) {
    var style by rememberSaveable { mutableStateOf(VisualizerStyle.WAVES) }
    var full by rememberSaveable { mutableStateOf(false) }
    val half = (LocalConfiguration.current.screenHeightDp / 2).dp
    Box(modifier = Modifier.fillMaxWidth().height(half)) {
        MusicVisualizer(style, Modifier.fillMaxSize())
        Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            VisualizerStyle.entries.forEach { s ->
                FilterChip(
                    selected = style == s,
                    onClick = { style = s },
                    label = { Text(stringResource(if (s == VisualizerStyle.WAVES) R.string.music_viz_waves else R.string.music_viz_metronome)) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        IconButton(onClick = { full = true }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.music_viz_full))
        }
    }
    if (full) MusicVisualizerFull(style, controller) { full = false }
}

/**
 * The visualizer filling the screen (29.10): the player's controls drawn over
 * it, gone after 5 seconds untouched; a tap brings them back.
 */
@Composable
private fun MusicVisualizerFull(style: VisualizerStyle, controller: MediaController, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableStateOf(0) }
    LaunchedEffect(touched) {
        shown = true
        delay(5_000)
        shown = false
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { touched++ },
        ) {
            MusicVisualizer(style, Modifier.fillMaxSize())
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
