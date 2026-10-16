package com.alphaomegos.annasagenda

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.alphaomegos.annasagenda.support.sidecarIsServerUrl
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
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp())
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.music_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_music_note) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
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
