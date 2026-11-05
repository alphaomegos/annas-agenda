package com.alphaomegos.annasagenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.support.MUSIC_WIDGET_GHOST
import com.alphaomegos.annasagenda.support.MusicWidgetKey
import com.alphaomegos.annasagenda.support.MusicWidgetState
import com.alphaomegos.annasagenda.support.musicWidgetChronometerBase
import com.alphaomegos.annasagenda.support.musicWidgetLine
import com.alphaomegos.annasagenda.support.musicWidgetStateFromMap
import com.alphaomegos.annasagenda.support.musicWidgetStateToMap

/*
 * The player widget drawn (05.11): plain RemoteViews rather than Glance — the
 * system chronometer that ticks by itself and the line that runs are theirs,
 * and nothing here needs Compose.
 */

/** The extra that asks MainActivity to open the music (05.11, the player widget). */
internal const val EXTRA_OPEN_MUSIC = "com.alphaomegos.annasagenda.OPEN_MUSIC"

/** The playback service is up in this process: a widget drawn without it has nothing playing. */
@Volatile
internal var musicServiceAlive: Boolean = false

private const val MUSIC_WIDGET_PREFS = "music_widget"
private const val MUSIC_WIDGET_REQUEST = 5100

/** The state last drawn, kept for drawing the widget when the player is gone. */
internal fun musicWidgetSaved(context: Context): MusicWidgetState =
    musicWidgetStateFromMap(
        context.applicationContext.getSharedPreferences(MUSIC_WIDGET_PREFS, Context.MODE_PRIVATE).all
            .mapValues { it.value as? String },
    )

/** Draws [state] on every player widget there is, and keeps it for the next time. */
internal fun musicWidgetShow(context: Context, state: MusicWidgetState) {
    val app = context.applicationContext
    val editor = app.getSharedPreferences(MUSIC_WIDGET_PREFS, Context.MODE_PRIVATE).edit().clear()
    musicWidgetStateToMap(state).forEach { (key, value) -> editor.putString(key, value) }
    editor.apply()
    val manager = AppWidgetManager.getInstance(app)
    val ids = manager.getAppWidgetIds(ComponentName(app, MusicWidgetReceiver::class.java))
    if (ids.isNotEmpty()) manager.updateAppWidget(ids, musicWidgetViews(app, state))
}

/** The main window: the mark, the line, the state and the time; the keys under them. */
internal fun musicWidgetViews(context: Context, state: MusicWidgetState): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.music_widget)
    val line = musicWidgetLine(state)
    views.setTextViewText(R.id.music_widget_title, line ?: context.getString(R.string.music_widget_nothing))
    views.setTextViewText(R.id.music_widget_time_ghost, MUSIC_WIDGET_GHOST)
    val position = if (line == null) 0L else state.positionMs
    views.setChronometer(R.id.music_widget_time, musicWidgetChronometerBase(SystemClock.elapsedRealtime(), position), null, state.playing)
    views.setImageViewResource(
        R.id.music_widget_state,
        when {
            state.playing -> R.drawable.ic_mw_play
            state.hasQueue -> R.drawable.ic_mw_pause
            else -> R.drawable.ic_mw_stop
        },
    )
    views.setImageViewResource(R.id.music_widget_play_pause, if (state.playing) R.drawable.ic_mw_pause else R.drawable.ic_mw_play)
    views.setContentDescription(
        R.id.music_widget_play_pause,
        context.getString(if (state.playing) R.string.music_widget_pause else R.string.music_widget_play),
    )
    views.setInt(R.id.music_widget_shuffle, "setColorFilter", lit(context, state.shuffle))
    views.setImageViewResource(R.id.music_widget_repeat, if (state.repeat == MusicRepeat.ONE) R.drawable.ic_mw_repeat_one else R.drawable.ic_mw_repeat)
    views.setInt(R.id.music_widget_repeat, "setColorFilter", lit(context, state.repeat != MusicRepeat.OFF))

    MusicWidgetKey.entries.forEach { key ->
        val target = if (key == MusicWidgetKey.PLAY_PAUSE && !state.hasQueue) openMusic(context) else keyPress(context, key)
        views.setOnClickPendingIntent(keyView(key), target)
    }
    views.setOnClickPendingIntent(R.id.music_widget_display, openMusic(context))
    return views
}

private fun keyView(key: MusicWidgetKey): Int = when (key) {
    MusicWidgetKey.PREVIOUS -> R.id.music_widget_previous
    MusicWidgetKey.PLAY_PAUSE -> R.id.music_widget_play_pause
    MusicWidgetKey.STOP -> R.id.music_widget_stop
    MusicWidgetKey.NEXT -> R.id.music_widget_next
    MusicWidgetKey.SHUFFLE -> R.id.music_widget_shuffle
    MusicWidgetKey.REPEAT -> R.id.music_widget_repeat
}

/** A mode's key lit green when on, dim when off. */
private fun lit(context: Context, on: Boolean): Int =
    context.getColor(if (on) R.color.music_widget_lcd else R.color.music_widget_off)

private fun keyPress(context: Context, key: MusicWidgetKey): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        MUSIC_WIDGET_REQUEST + key.ordinal,
        Intent(context, MusicWidgetReceiver::class.java).setAction(key.action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** The app on its music, opened the way the steps widget opens its tab. */
private fun openMusic(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        MUSIC_WIDGET_REQUEST + 20,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_MUSIC, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
