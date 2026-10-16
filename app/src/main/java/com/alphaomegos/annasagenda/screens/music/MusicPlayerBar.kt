package com.alphaomegos.annasagenda.screens.music

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
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
import com.alphaomegos.annasagenda.app.musicPlayerRepeatMode
import com.alphaomegos.annasagenda.app.musicRepeatOf
import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.support.MusicQueue
import com.alphaomegos.annasagenda.support.MusicResumed
import com.alphaomegos.annasagenda.support.musicCacheKey
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyle
import com.alphaomegos.annasagenda.screens.metro.Win95Grey
import com.alphaomegos.annasagenda.screens.metro.metroFrame
import androidx.compose.ui.graphics.Color

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

/** A track of the player's queue, as the queue shows it. */
internal data class MusicQueued(val id: String, val title: String, val artist: String)

/** What is playing, as a screen shows it: the track, the queue it is in, and how the player goes on. */
internal data class MusicNowPlaying(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val index: Int = -1,
    val queue: List<MusicQueued> = emptyList(),
    val shuffle: Boolean = false,
    val repeat: MusicRepeat = MusicRepeat.ALL,
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
                index = c.currentMediaItemIndex,
                queue = (0 until c.mediaItemCount).map { i ->
                    val item = c.getMediaItemAt(i)
                    MusicQueued(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
                },
                shuffle = c.shuffleModeEnabled,
                repeat = musicRepeatOf(c.repeatMode),
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun musicMediaItem(track: SidecarTrack, url: String, unknownArtist: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(url)
        // The kept copy's name: the same track with another file is another copy.
        .setCustomCacheKey(musicCacheKey(track))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist?.takeIf { it.isNotBlank() } ?: unknownArtist)
                .build(),
        )
        .build()

/**
 * [queue] played from its start (16.10). The first time, going round it as
 * the web's player does; after that, as the person last set it.
 */
internal fun MediaController.playMusic(queue: MusicQueue, item: (SidecarTrack) -> MediaItem) {
    if (mediaItemCount == 0) repeatMode = Player.REPEAT_MODE_ALL
    setMediaItems(queue.tracks.map(item), queue.startIndex, 0L)
    prepare()
    play()
}

/** [resumed] put back in the player, paused where it was, going on as it did. */
internal fun MediaController.resumeMusic(resumed: MusicResumed, shuffle: Boolean, repeat: MusicRepeat, item: (SidecarTrack) -> MediaItem) {
    setMediaItems(resumed.queue.tracks.map(item), resumed.queue.startIndex, resumed.positionMillis)
    shuffleModeEnabled = shuffle
    repeatMode = musicPlayerRepeatMode(repeat)
    prepare()
}

/**
 * The bar along the bottom while something plays (16.10): what, by whom,
 * and the three buttons the shade has too — in the section's style: today's
 * raised bar; Windows' grey panel; Norton's cyan status line; the
 * terminal's "> PLAYING:" under a green rule. A tap on the words opens the
 * player.
 */
@Composable
internal fun MusicMiniBar(now: MusicNowPlaying, controller: MediaController?, onOpen: () -> Unit) {
    if (now.trackId == null || controller == null) return
    val style = LocalMetroStyle.current
    val styled = musicStyled()
    val colors = MaterialTheme.colorScheme
    val ground = when {
        !styled -> colors.surfaceContainerHighest
        style == MetroStyle.WIN95 -> Win95Grey
        style == MetroStyle.DOS -> colors.primaryContainer
        else -> colors.background
    }
    val ink = when {
        !styled -> colors.onSurface
        style == MetroStyle.WIN95 -> Color.Black
        style == MetroStyle.DOS -> colors.onPrimaryContainer
        else -> colors.primary
    }
    val title = now.title.orEmpty()
    val shownTitle = if (styled && style == MetroStyle.TERMINAL) "> PLAYING: ${title.uppercase()}" else title
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ground)
            .then(if (styled && style == MetroStyle.WIN95) Modifier.metroFrame(style, details = true) else Modifier),
    ) {
        HorizontalDivider(color = if (styled) ink.copy(alpha = 0.6f) else colors.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.music_player_open), onClick = onOpen),
            ) {
                Text(
                    shownTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (style == MetroStyle.WIN95 || !styled) FontWeight.Medium else null,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (styled) ink.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MusicControl(Icons.Default.SkipPrevious, "|<", stringResource(R.string.music_previous), { controller.seekToPrevious() })
            MusicControl(
                if (now.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (now.playing) "||" else ">",
                stringResource(if (now.playing) R.string.music_pause else R.string.music_play),
                { if (controller.isPlaying) controller.pause() else controller.play() },
            )
            MusicControl(Icons.Default.SkipNext, ">|", stringResource(R.string.music_next), { controller.seekToNext() })
        }
    }
}
