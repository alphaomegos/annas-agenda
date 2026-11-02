package com.alphaomegos.annasagenda.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alphaomegos.annasagenda.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** How sending is going, for the phone's list: which track, how far, or waiting (for Wi-Fi, for a network). */
internal data class MusicProposeProgress(val waiting: Boolean, val key: String?, val percent: Int)

/**
 * Sends the phone's proposals to the sidecar (02.11, Eduard): in the background, by
 * WorkManager, over Wi-Fi only when the music settings say so — then the wish waits for
 * Wi-Fi by itself and goes when it comes, the app closed or not. A notification shows how
 * far the track has gone. A track that got there plays from its file on this phone (the
 * same «own file» a track can have), and the phone's list marks it.
 */
internal class MusicProposeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val proposals = MusicPhones.proposalsOf(ctx)
        val connection = SidecarConnections.of(ctx)
        val cookie = connection.cookieHeader() ?: return@withContext Result.success() // signed out: they wait
        var later = false
        var sent = 0
        for (p in proposals.waiting()) {
            if (isStopped) return@withContext Result.retry()
            var shown = -1
            show(ctx, p.title, 0)
            setProgress(workDataOf(KEY_KEY to p.key, KEY_PERCENT to 0))
            val outcome = sendMusicProposal(
                p = p,
                create = { space, title, artist -> connection.createTrack(space, title, artist) },
                audioUrl = { id -> connection.audioUrl(id) },
                cookie = cookie,
                put = SidecarAudioPutHttp,
                open = { ctx.contentResolver.openInputStream(Uri.parse(p.uri)) },
                onTrack = { id -> proposals.update(p.key) { it.copy(trackId = id) } },
                onSent = { bytes ->
                    val percent = ((bytes * 100) / p.sizeBytes.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    if (percent >= shown + 5) {
                        shown = percent
                        show(ctx, p.title, percent)
                    }
                },
            )
            when (outcome) {
                is MusicSendOutcome.Sent -> {
                    proposals.update(p.key) { it.copy(trackId = outcome.trackId, state = MusicProposalState.SENT, error = null) }
                    // The sidecar's track plays from this phone's file: nothing to download.
                    MusicLocalFileStores.of(ctx).set(outcome.trackId, MusicLocalFile(p.uri, p.fileName))
                    sent++
                }
                is MusicSendOutcome.Refused -> proposals.update(p.key) { it.copy(state = MusicProposalState.REFUSED, error = outcome.why) }
                MusicSendOutcome.Later -> later = true
            }
        }
        done(ctx, sent)
        if (later && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    private fun show(ctx: Context, title: String, percent: Int) {
        if (!canPostNotifications(ctx)) return
        channel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MUSIC_SEND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.music_propose_sending))
            .setContentText(title)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    private fun done(ctx: Context, sent: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        if (sent == 0 || !canPostNotifications(ctx)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        channel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_MUSIC_SEND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.music_propose_sent, sent))
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_MUSIC_SEND, ctx.getString(R.string.music_propose_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val WORK = "music-propose"
        private const val KEY_KEY = "key"
        private const val KEY_PERCENT = "percent"
        private const val MAX_ATTEMPTS = 8
        private const val NOTIFICATION_ID = 7301

        /** Sends what waits — now, or when the network the settings ask for comes. */
        fun start(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<MusicProposeWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            // A run already going finishes the list, then this one looks again: nothing waits forgotten.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun progress(context: Context): Flow<MusicProposeProgress?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
                val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: infos.firstOrNull { !it.state.isFinished } ?: return@map null
                MusicProposeProgress(
                    waiting = info.state != WorkInfo.State.RUNNING,
                    key = info.progress.getString(KEY_KEY),
                    percent = info.progress.getInt(KEY_PERCENT, 0),
                )
            }
    }
}

internal const val CHANNEL_MUSIC_SEND = "music_send"
