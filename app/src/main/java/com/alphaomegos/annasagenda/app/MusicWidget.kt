package com.alphaomegos.annasagenda

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alphaomegos.annasagenda.app.musicPlayerRepeatMode
import com.alphaomegos.annasagenda.app.musicRepeatOf
import com.alphaomegos.annasagenda.support.MusicWidgetKey
import com.alphaomegos.annasagenda.support.MusicWidgetList
import com.alphaomegos.annasagenda.support.MusicWidgetState
import com.alphaomegos.annasagenda.support.MusicWidgetTrack
import com.alphaomegos.annasagenda.support.musicRepeatAfter
import com.alphaomegos.annasagenda.support.musicWidgetKeyOf
import com.alphaomegos.annasagenda.support.musicWidgetSignature

/**
 * The player on the home screen (05.11, Eduard): the main window, and on a
 * wide widget the list window beside it. Drawn by the playback service at
 * every change it shows (musicWidgetShow), and by Android from the state last
 * kept. Its keys reach the app's one player through a media controller, as
 * the app's own screens do.
 */
class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Without the service nothing plays, whatever was kept, and ⏯ opens the app.
        val saved = musicWidgetSaved(context)
        val state = if (musicServiceAlive) saved else saved.copy(playing = false, hasQueue = false)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, musicWidgetViews(context, state, id, appWidgetManager.getAppWidgetOptions(id)))
        }
    }

    // Made wider or narrower: the list window comes or goes, a page holds more rows or fewer.
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        musicWidgetRefresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        musicWidgetForget(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val listKey = musicWidgetListKeyOf(intent.action)
        if (listKey != null) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            if (listKey != MusicWidgetListKey.PLAY_ROW) {
                musicWidgetWindowPress(app, id, listKey)
                return
            }
            val list = musicWidgetLists(app).firstOrNull { it.key == intent.getStringExtra(EXTRA_MUSIC_WIDGET_LIST) }
            val trackId = intent.getStringExtra(EXTRA_MUSIC_WIDGET_TRACK)
            if (list == null || trackId == null) return
            musicWidgetWindowPress(app, id, listKey, list.key)
            withPlayer(app) { musicWidgetPlayRow(it, list, trackId) }
            return
        }
        val key = musicWidgetKeyOf(intent.action)
        if (key == null) {
            super.onReceive(context, intent)
            return
        }
        withPlayer(app) { musicWidgetPress(app, it, key) }
    }

    /** [act] on the app's player, through a controller let go a moment after. */
    private fun withPlayer(app: Context, act: (Player) -> Unit) {
        val pending = goAsync()
        // The application's context binds: a receiver's own may not bind to a service.
        val token = SessionToken(app, ComponentName(app, MusicPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.getOrNull()?.let(act)
                // Let go a moment later, the commands on their way to the session by then.
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        MediaController.releaseFuture(future)
                        pending.finish()
                    },
                    MUSIC_WIDGET_RELEASE_MS,
                )
            },
            ContextCompat.getMainExecutor(app),
        )
    }
}

private const val MUSIC_WIDGET_RELEASE_MS = 1_000L

/** What a key does to the player: the old players' keys. */
internal fun musicWidgetPress(context: Context, player: Player, key: MusicWidgetKey) {
    if (player.mediaItemCount == 0) {
        // The service was let go and its queue with it: the widget says so, and ⏯ opens the app.
        musicWidgetShow(context, musicWidgetSaved(context).copy(playing = false, hasQueue = false))
        return
    }
    when (key) {
        MusicWidgetKey.PREVIOUS -> player.seekToPrevious()
        MusicWidgetKey.NEXT -> player.seekToNext()
        MusicWidgetKey.PLAY_PAUSE -> if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        // The old players' stop: silence, and back to the start of the track.
        MusicWidgetKey.STOP -> {
            player.pause()
            player.seekTo(player.currentMediaItemIndex, 0L)
        }
        MusicWidgetKey.SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
        MusicWidgetKey.REPEAT -> player.repeatMode = musicPlayerRepeatMode(musicRepeatAfter(musicRepeatOf(player.repeatMode)))
    }
}

/**
 * A track of the list window tapped (step two): the queue already that list,
 * the player goes to it; another list, that list is the queue from there —
 * as a tap in the app plays its list, going round it the first time.
 */
internal fun musicWidgetPlayRow(player: Player, list: MusicWidgetList, trackId: String) {
    val index = list.tracks.indexOfFirst { it.id == trackId }
    if (index < 0) return
    val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
    if (queue == list.tracks.map { it.id }) {
        player.seekTo(index, 0L)
    } else {
        if (player.mediaItemCount == 0) player.repeatMode = Player.REPEAT_MODE_ALL
        player.setMediaItems(list.tracks.map(::musicWidgetMediaItem), index, 0L)
    }
    player.prepare()
    player.play()
}

/** A track of the lists file as the player is given it: the same item the music screen makes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun musicWidgetMediaItem(track: MusicWidgetTrack): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(track.uri)
        .setCustomCacheKey(track.cacheKey)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
        .build()

/** What the widget shows of [player]: with the playing track and which list the queue is, for the list window. */
internal fun musicWidgetStateOf(player: Player): MusicWidgetState {
    val meta = player.mediaMetadata
    return MusicWidgetState(
        title = meta.title?.toString(),
        artist = meta.artist?.toString(),
        playing = player.isPlaying,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        shuffle = player.shuffleModeEnabled,
        repeat = musicRepeatOf(player.repeatMode),
        hasQueue = player.mediaItemCount > 0,
        trackId = player.currentMediaItem?.mediaId,
        queueSignature = musicWidgetSignature((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }),
    )
}
