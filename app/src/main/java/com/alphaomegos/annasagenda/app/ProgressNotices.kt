package com.alphaomegos.annasagenda.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.canPostNotifications
import com.alphaomegos.annasagenda.support.progressPercent

/**
 * A sending's progress as a notification (03.11, Eduard): the backup going up to
 * oursidecar and a track proposed to the sidecar — a bar and «45 %», quiet, kept while it
 * goes; at the end a word that it is done, or nothing. Nothing at all when notifications
 * are not allowed.
 */
internal object ProgressNotices {

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    fun show(context: Context, channel: String, channelName: Int, id: Int, title: String, what: String?, done: Long, of: Long) {
        if (!canPostNotifications(context)) return
        ensureChannel(context, channel, channelName)
        val percent = progressPercent(done, of)
        val text = listOfNotNull(what, context.getString(R.string.progress_percent, percent)).joinToString(" — ")
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, of <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** The end: [title] said once (done), or the notification gone when there is nothing to say. */
    @SuppressLint("MissingPermission") // checked by canPostNotifications
    fun finished(context: Context, channel: String, channelName: Int, id: Int, title: String?) {
        val nm = NotificationManagerCompat.from(context)
        if (title == null || !canPostNotifications(context)) {
            nm.cancel(id)
            return
        }
        ensureChannel(context, channel, channelName)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        nm.notify(id, n)
    }

    private fun ensureChannel(context: Context, channel: String, channelName: Int) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(channel, context.getString(channelName), NotificationManager.IMPORTANCE_LOW),
        )
    }
}
