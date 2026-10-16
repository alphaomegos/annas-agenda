package com.alphaomegos.annasagenda.screens.music

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alphaomegos.annasagenda.MusicPlaybackService
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.MusicQueue

/**
 * The screen's hold on the player (16.10): a controller of the playback
 * service, null until the service answers. Letting go of it does not stop
 * the music — the service plays on — it only stops this screen from hearing
 * about it.
 */
@Composable
internal fun rememberMusicController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** What is playing, as a screen shows it. */
internal data class MusicNowPlaying(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
)

/** What [controller] is playing, read again on everything the player says. */
@Composable
internal fun rememberMusicNowPlaying(controller: MediaController?): MusicNowPlaying {
    var now by remember { mutableStateOf(MusicNowPlaying()) }
    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            now = MusicNowPlaying()
            return@DisposableEffect onDispose {}
        }
        fun read() {
            val metadata = c.mediaMetadata
            now = MusicNowPlaying(
                trackId = c.currentMediaItem?.mediaId,
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                playing = c.isPlaying,
            )
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                read()
            }
        }
        c.addListener(listener)
        read()
        onDispose { c.removeListener(listener) }
    }
    return now
}

/** A track as the player is given it: streamed from [url], named for the shade and the lock screen. */
internal fun musicMediaItem(track: SidecarTrack, url: String, unknownArtist: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist?.takeIf { it.isNotBlank() } ?: unknownArtist)
                .build(),
        )
        .build()

/** [queue] played from its start, going round it as the web's player does. */
internal fun MediaController.playMusic(queue: MusicQueue, item: (SidecarTrack) -> MediaItem) {
    setMediaItems(queue.tracks.map(item), queue.startIndex, 0L)
    repeatMode = Player.REPEAT_MODE_ALL
    prepare()
    play()
}

/**
 * The bar along the bottom while something plays (16.10): what, by whom,
 * and the three buttons the shade has too.
 */
@Composable
internal fun MusicMiniBar(now: MusicNowPlaying, controller: MediaController?) {
    if (now.trackId == null || controller == null) return
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerHighest)) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    now.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { controller.seekToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.music_previous), tint = colors.onSurface)
            }
            IconButton(onClick = { if (controller.isPlaying) controller.pause() else controller.play() }) {
                Icon(
                    if (now.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (now.playing) R.string.music_pause else R.string.music_play),
                    tint = colors.onSurface,
                )
            }
            IconButton(onClick = { controller.seekToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.music_next), tint = colors.onSurface)
            }
        }
    }
}
