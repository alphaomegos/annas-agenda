package com.alphaomegos.annasagenda

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alphaomegos.annasagenda.app.SIDECAR_HTTP_AGENT
import com.alphaomegos.annasagenda.app.SidecarConnection
import com.alphaomegos.annasagenda.app.SidecarConnections
import com.alphaomegos.annasagenda.app.musicPlayerMemoryFile
import com.alphaomegos.annasagenda.app.musicRepeatOf
import com.alphaomegos.annasagenda.model.MusicPlayerMemory
import com.alphaomegos.annasagenda.support.sidecarIsServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The music of oursidecar played in the background (16.10, Eduard): one
 * player for the app, kept by Android's media session, so that it plays on
 * with the screen off and the app away, and is worked from the notification
 * shade, the lock screen, a headset's buttons and a car's. The sound goes
 * quiet for a call and stops when the headphones come out.
 *
 * Files are streamed from the sidecar's server with the session cookie —
 * only to that server — and played from the phone itself when a track has a
 * file of the phone's (content://).
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Where the player is, written down while it plays — every few seconds,
    // and at every change of track, queue or way of going on — so that the
    // app opened again, after Android let the service go, finds it there.
    private val rememberTick = object : Runnable {
        override fun run() {
            val player = session?.player ?: return
            remember(player)
            if (player.isPlaying) handler.postDelayed(this, MEMORY_TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val connection = SidecarConnections.of(this)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(musicDataSourceFactory(this, connection)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) {
                    remember(player)
                }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    handler.removeCallbacks(rememberTick)
                    if (player.isPlaying) handler.postDelayed(rememberTick, MEMORY_TICK_MS)
                }
            }
        })
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp())
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.music_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_music_note) },
        )
        // Signed out, or another person in (16.10): the music stops and its queue is forgotten —
        // the files are fetched with a session there no longer is, and the queue was someone's.
        scope.launch {
            connection.status
                .map { it.account?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    player.stop()
                    player.clearMediaItems()
                    musicPlayerMemoryFile(this@MusicPlaybackService).forget()
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(rememberTick)
        session?.run {
            remember(player)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** The queue, the track and how far in, for the sidecar whose music it is. An empty player keeps what was. */
    private fun remember(player: Player) {
        if (player.mediaItemCount == 0) return
        val spaceId = SidecarConnections.of(this).status.value.space?.id ?: return
        musicPlayerMemoryFile(this).write(
            MusicPlayerMemory(
                spaceId = spaceId,
                queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                currentId = player.currentMediaItem?.mediaId,
                positionMillis = player.currentPosition.coerceAtLeast(0L),
                shuffle = player.shuffleModeEnabled,
                repeat = musicRepeatOf(player.repeatMode),
            ),
        )
    }

    private companion object {
        const val MEMORY_TICK_MS = 10_000L
    }

    /** The app, as its icon opens it: the one already open comes forward, its place kept. */
    private fun openApp(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Where the player reads a track from (16.10): the phone's own files as
 * they are; the sidecar's server over https, with the session cookie added
 * at the moment of asking — the session may have been renewed since the
 * track was queued — and to that server only.
 */
@OptIn(UnstableApi::class)
internal fun musicDataSourceFactory(context: Context, connection: SidecarConnection): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(SIDECAR_HTTP_AGENT)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val withSession = ResolvingDataSource.Factory(http) { spec ->
        val server = connection.status.value.server
        if (!sidecarIsServerUrl(spec.uri.toString(), server)) {
            spec
        } else {
            // On the player's loading thread, never the main one: a short wait for the sealed token is fine.
            val cookie = runBlocking { connection.cookieHeader() }
            if (cookie == null) spec else spec.withAdditionalHeaders(mapOf("Cookie" to cookie))
        }
    }
    return DefaultDataSource.Factory(context, withSession)
}
