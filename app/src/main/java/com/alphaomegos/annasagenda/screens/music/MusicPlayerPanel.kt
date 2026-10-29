package com.alphaomegos.annasagenda.screens.music

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.musicPlayerRepeatMode
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyle
import com.alphaomegos.annasagenda.support.musicRepeatNext
import com.alphaomegos.annasagenda.support.musicTimeLabel
import kotlinx.coroutines.delay

/**
 * The player opened from the bar at the bottom (16.10): what plays, how far
 * in — to be moved along by a finger — the buttons, shuffle and the way it
 * goes on (round the queue, the one again, stop at the end), and the queue,
 * whose any track a tap plays. Back closes it; the music plays on.
 */
@Composable
internal fun ColumnScope.MusicPlayerPanel(controller: MediaController, now: MusicNowPlaying, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val colors = MaterialTheme.colorScheme

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragged by remember { mutableStateOf<Float?>(null) }
    // The clock of the track, read from the player twice a second while this is open.
    LaunchedEffect(controller) {
        while (true) {
            position = controller.currentPosition.coerceAtLeast(0L)
            duration = controller.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    // The visualizer (29.10): opened under the controls, half the screen high.
    var vizOpen by rememberSaveable { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MusicControl(Icons.Default.KeyboardArrowDown, "v", stringResource(R.string.music_player_close), onClose)
        Spacer(modifier = Modifier.weight(1f))
        MusicControl(Icons.Default.GraphicEq, "~", stringResource(R.string.music_viz_open), { vizOpen = !vizOpen }, lit = vizOpen, on = vizOpen)
    }
    if (vizOpen) MusicVisualizerPanel(controller)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            now.title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            now.artist.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val shownAt = dragged?.toLong() ?: position
        // DOS and the terminal draw it in letters, as their players did; the others slide.
        if (musicStyled() && LocalMetroStyle.current != MetroStyle.WIN95) {
            MusicTextProgress(
                fraction = if (duration > 0L) shownAt.toFloat() / duration else 0f,
                enabled = duration > 0L,
                onSeek = { part -> controller.seekTo((part * duration).toLong()) },
            )
        } else {
            Slider(
                value = shownAt.coerceAtMost(duration).toFloat(),
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let { controller.seekTo(it.toLong()) }
                    dragged = null
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0L,
                colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(musicTimeLabel(shownAt), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(
                if (duration > 0L) musicTimeLabel(duration) else "–:––",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicControl(
                Icons.Default.Shuffle,
                "RND",
                stringResource(if (now.shuffle) R.string.music_shuffle_on else R.string.music_shuffle_off),
                { controller.shuffleModeEnabled = !now.shuffle },
                lit = now.shuffle,
                on = now.shuffle,
            )
            MusicControl(Icons.Default.SkipPrevious, "|<", stringResource(R.string.music_previous), { controller.seekToPrevious() })
            MusicControl(
                if (now.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (now.playing) "||" else ">",
                stringResource(if (now.playing) R.string.music_pause else R.string.music_play),
                { if (controller.isPlaying) controller.pause() else controller.play() },
                big = true,
            )
            MusicControl(Icons.Default.SkipNext, ">|", stringResource(R.string.music_next), { controller.seekToNext() })
            MusicControl(
                if (now.repeat == MusicRepeat.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                when (now.repeat) {
                    MusicRepeat.ALL -> "ALL"
                    MusicRepeat.ONE -> "ONE"
                    MusicRepeat.OFF -> "OFF"
                },
                stringResource(
                    when (now.repeat) {
                        MusicRepeat.ALL -> R.string.music_repeat_all
                        MusicRepeat.ONE -> R.string.music_repeat_one
                        MusicRepeat.OFF -> R.string.music_repeat_off
                    },
                ),
                { controller.repeatMode = musicPlayerRepeatMode(musicRepeatNext(now.repeat)) },
                lit = now.repeat != MusicRepeat.OFF,
                on = now.repeat != MusicRepeat.OFF,
            )
        }
    }

    HorizontalDivider(color = colors.outlineVariant)
    Text(
        stringResource(R.string.music_queue),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = now.index.coerceAtLeast(0))
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(now.queue, key = { i, q -> "$i:${q.id}" }) { i, queued ->
            val current = i == now.index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (current) colors.secondaryContainer else Color.Transparent)
                    .clickable(role = Role.Button) {
                        controller.seekToDefaultPosition(i)
                        controller.play()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        queued.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) colors.onSecondaryContainer else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(queued.artist, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
