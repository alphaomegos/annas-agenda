package com.alphaomegos.annasagenda

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alphaomegos.annasagenda.app.musicPlayerRepeatMode
import com.alphaomegos.annasagenda.app.musicRepeatOf
import com.alphaomegos.annasagenda.support.MusicWidgetKey
import com.alphaomegos.annasagenda.support.MusicWidgetState
import com.alphaomegos.annasagenda.support.musicRepeatAfter
import com.alphaomegos.annasagenda.support.musicWidgetKeyOf

/**
 * The player on the home screen (05.11, Eduard), its main window. Drawn by the
 * playback service at every change it shows (musicWidgetShow), and by Android
 * from the state last kept. Its keys reach the app's one player through a
 * media controller, as the app's own screens do.
 */
class MusicWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Without the service nothing plays, whatever was kept, and ⏯ opens the app.
        val saved = musicWidgetSaved(context)
        val state = if (musicServiceAlive) saved else saved.copy(playing = false, hasQueue = false)
        appWidgetManager.updateAppWidget(appWidgetIds, musicWidgetViews(context, state))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val key = musicWidgetKeyOf(intent.action)
        if (key == null) {
            super.onReceive(context, intent)
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        // The application's context binds: a receiver's own may not bind to a service.
        val token = SessionToken(app, ComponentName(app, MusicPlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.getOrNull()?.let { musicWidgetPress(app, it, key) }
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

/** What the widget shows of [player]. */
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
    )
}
