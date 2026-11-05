package com.alphaomegos.annasagenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.support.MUSIC_WIDGET_GHOST
import com.alphaomegos.annasagenda.support.MusicWidgetKey
import com.alphaomegos.annasagenda.support.MusicWidgetList
import com.alphaomegos.annasagenda.support.MusicWidgetState
import com.alphaomegos.annasagenda.support.musicWidgetCatalogOf
import com.alphaomegos.annasagenda.support.musicWidgetCatalogText
import com.alphaomegos.annasagenda.support.musicWidgetChronometerBase
import com.alphaomegos.annasagenda.support.musicWidgetLine
import com.alphaomegos.annasagenda.support.musicWidgetListAfter
import com.alphaomegos.annasagenda.support.musicWidgetListShown
import com.alphaomegos.annasagenda.support.musicWidgetPage
import com.alphaomegos.annasagenda.support.musicWidgetPages
import com.alphaomegos.annasagenda.support.musicWidgetRowText
import com.alphaomegos.annasagenda.support.musicWidgetRows
import com.alphaomegos.annasagenda.support.musicWidgetSignature
import com.alphaomegos.annasagenda.support.musicWidgetStateFromMap
import com.alphaomegos.annasagenda.support.musicWidgetStateToMap
import java.io.File
import java.text.NumberFormat

/*
 * The player widget drawn (05.11): plain RemoteViews rather than Glance — the
 * system chronometer that ticks by itself and the line that runs are theirs,
 * and nothing here needs Compose.
 */

/** The extra that asks MainActivity to open the music (05.11, the player widget). */
internal const val EXTRA_OPEN_MUSIC = "com.alphaomegos.annasagenda.OPEN_MUSIC"

/** And the music on its visualizer, full screen (05.11, step three: the widget's own picture cannot move). */
internal const val EXTRA_OPEN_VISUALIZER = "com.alphaomegos.annasagenda.OPEN_VISUALIZER"

/** The playback service is up in this process: a widget drawn without it has nothing playing. */
@Volatile
internal var musicServiceAlive: Boolean = false

/** The list window's row asks for its list and its track by these (05.11, step two). */
internal const val EXTRA_MUSIC_WIDGET_LIST = "com.alphaomegos.annasagenda.music.widget.LIST"
internal const val EXTRA_MUSIC_WIDGET_TRACK = "com.alphaomegos.annasagenda.music.widget.TRACK"

private const val MUSIC_WIDGET_PREFS = "music_widget"
private const val MUSIC_WIDGET_WINDOWS = "music_widget_windows"
private const val MUSIC_WIDGET_LISTS_FILE = "music-widget-lists.tsv"
private const val MUSIC_WIDGET_REQUEST = 5100
private const val MUSIC_WIDGET_LIST_REQUEST = 6000

/** Narrower than this (dp), a widget is the main window alone; wider, the list window comes beside it. */
private const val MUSIC_WIDGET_WIDE_DP = 330

/** The list window's keys (05.11, step two), and the actions their broadcasts carry. */
internal enum class MusicWidgetListKey(val action: String) {
    PREVIOUS_LIST("com.alphaomegos.annasagenda.music.widget.PREVIOUS_LIST"),
    NEXT_LIST("com.alphaomegos.annasagenda.music.widget.NEXT_LIST"),
    PAGE_UP("com.alphaomegos.annasagenda.music.widget.PAGE_UP"),
    PAGE_DOWN("com.alphaomegos.annasagenda.music.widget.PAGE_DOWN"),
    TOGGLE("com.alphaomegos.annasagenda.music.widget.TOGGLE_LIST"),
    PLAY_ROW("com.alphaomegos.annasagenda.music.widget.PLAY_ROW"),
}

internal fun musicWidgetListKeyOf(action: String?): MusicWidgetListKey? = MusicWidgetListKey.entries.firstOrNull { it.action == action }

/** One widget's list window: the list it shows, the page, whether it follows the playing track, whether it is open. */
private data class MusicWidgetWindow(val list: String?, val page: Int, val follow: Boolean, val open: Boolean)

private fun windowOf(context: Context, id: Int): MusicWidgetWindow {
    val prefs = context.getSharedPreferences(MUSIC_WIDGET_WINDOWS, Context.MODE_PRIVATE)
    return MusicWidgetWindow(prefs.getString("$id.list", null), prefs.getInt("$id.page", 0), prefs.getBoolean("$id.follow", true), prefs.getBoolean("$id.open", true))
}

private fun keepWindow(context: Context, id: Int, window: MusicWidgetWindow) {
    context.getSharedPreferences(MUSIC_WIDGET_WINDOWS, Context.MODE_PRIVATE).edit()
        .putString("$id.list", window.list)
        .putInt("$id.page", window.page)
        .putBoolean("$id.follow", window.follow)
        .putBoolean("$id.open", window.open)
        .apply()
}

/** Widgets taken off the home screen take their windows with them. */
internal fun musicWidgetForget(context: Context, ids: IntArray) {
    val editor = context.applicationContext.getSharedPreferences(MUSIC_WIDGET_WINDOWS, Context.MODE_PRIVATE).edit()
    ids.forEach { id -> listOf("list", "page", "follow", "open").forEach { editor.remove("$id.$it") } }
    editor.apply()
}

@Volatile
private var listsRead: Pair<Long, List<MusicWidgetList>>? = null

/** The music screen's lists, as it last left them for the widget; none before the music was ever opened. */
internal fun musicWidgetLists(context: Context): List<MusicWidgetList> {
    val file = File(context.applicationContext.noBackupFilesDir, MUSIC_WIDGET_LISTS_FILE)
    if (!file.exists()) return emptyList()
    val stamp = file.lastModified() * 31 + file.length()
    listsRead?.let { (at, lists) -> if (at == stamp) return lists }
    val lists = runCatching { musicWidgetCatalogOf(file.readText()) }.getOrDefault(emptyList())
    listsRead = stamp to lists
    return lists
}

/** Left by the music screen (05.11): this phone's own, so in the no-backup files, written whole or not at all. */
internal fun musicWidgetCatalogWrite(context: Context, lists: List<MusicWidgetList>) {
    val dir = context.applicationContext.noBackupFilesDir
    val part = File(dir, "$MUSIC_WIDGET_LISTS_FILE.part")
    part.writeText(musicWidgetCatalogText(lists))
    part.renameTo(File(dir, MUSIC_WIDGET_LISTS_FILE))
    listsRead = null
}

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
    manager.getAppWidgetIds(ComponentName(app, MusicWidgetReceiver::class.java)).forEach { id ->
        manager.updateAppWidget(id, musicWidgetViews(app, state, id, manager.getAppWidgetOptions(id)))
    }
}

/** Every widget drawn again from what was kept: as the service left it, or stopped without the service. */
internal fun musicWidgetRefresh(context: Context) {
    val saved = musicWidgetSaved(context)
    musicWidgetShow(context, if (musicServiceAlive) saved else saved.copy(playing = false, hasQueue = false))
}

/**
 * A key of the list window pressed on widget [id]: another list, another
 * page, the window opened or closed — or a track played, its list then shown
 * following it. The window kept, every widget drawn again.
 */
internal fun musicWidgetWindowPress(context: Context, id: Int, key: MusicWidgetListKey, listKey: String? = null) {
    val app = context.applicationContext
    val state = musicWidgetSaved(app)
    val window = windowOf(app, id)
    val lists = musicWidgetLists(app)
    val at = musicWidgetListShown(lists, window.list, state.queueSignature)
    val next = when (key) {
        MusicWidgetListKey.TOGGLE -> window.copy(open = !window.open)
        MusicWidgetListKey.PREVIOUS_LIST, MusicWidgetListKey.NEXT_LIST -> {
            val to = musicWidgetListAfter(at, lists.size, if (key == MusicWidgetListKey.NEXT_LIST) 1 else -1)
            window.copy(list = lists.getOrNull(to)?.key, page = 0, follow = true)
        }
        // A step from the page seen, which following the track may have moved.
        MusicWidgetListKey.PAGE_UP, MusicWidgetListKey.PAGE_DOWN -> {
            val list = lists.getOrNull(at)
            val seen = if (list == null) 0 else pageOf(list, state, window, rowsOf(AppWidgetManager.getInstance(app).getAppWidgetOptions(id)))
            window.copy(list = list?.key, page = seen + if (key == MusicWidgetListKey.PAGE_DOWN) 1 else -1, follow = false)
        }
        MusicWidgetListKey.PLAY_ROW -> window.copy(list = listKey ?: window.list, follow = true)
    }
    keepWindow(app, id, next)
    musicWidgetRefresh(app)
}

private fun rowsOf(options: Bundle?): Int = musicWidgetRows(options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0)

/** Where in [list] the playing track is: -1 when the queue is another list's. */
private fun playingIndexOf(list: MusicWidgetList, state: MusicWidgetState): Int {
    val ids = list.tracks.map { it.id }
    return if (state.queueSignature != null && musicWidgetSignature(ids) == state.queueSignature) ids.indexOf(state.trackId) else -1
}

private fun pageOf(list: MusicWidgetList, state: MusicWidgetState, window: MusicWidgetWindow, rows: Int): Int =
    musicWidgetPage(window.page, musicWidgetPages(list.tracks.size, rows), playingIndexOf(list, state), rows, window.follow)

/** The main window: the mark, the line, the state and the time; the keys under them. Wide, the list window beside it. */
internal fun musicWidgetViews(context: Context, state: MusicWidgetState, id: Int, options: Bundle?): RemoteViews {
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
    views.setOnClickPendingIntent(R.id.music_widget_visualizer, openVisualizer(context))

    // The list window (step two): on a wide widget only, opened and closed by its own key.
    val wide = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0) >= MUSIC_WIDGET_WIDE_DP
    val window = windowOf(context, id)
    views.setViewVisibility(R.id.music_widget_list_key, if (wide) View.VISIBLE else View.GONE)
    views.setInt(R.id.music_widget_list_key, "setColorFilter", lit(context, window.open))
    views.setOnClickPendingIntent(R.id.music_widget_list_key, listPress(context, id, MusicWidgetListKey.TOGGLE))
    val open = wide && window.open
    views.setViewVisibility(R.id.music_widget_list, if (open) View.VISIBLE else View.GONE)
    if (open) drawList(context, views, state, id, window, rowsOf(options))
    return views
}

private fun drawList(context: Context, views: RemoteViews, state: MusicWidgetState, id: Int, window: MusicWidgetWindow, rows: Int) {
    listOf(
        R.id.music_widget_list_previous to MusicWidgetListKey.PREVIOUS_LIST,
        R.id.music_widget_list_next to MusicWidgetListKey.NEXT_LIST,
        R.id.music_widget_page_up to MusicWidgetListKey.PAGE_UP,
        R.id.music_widget_page_down to MusicWidgetListKey.PAGE_DOWN,
    ).forEach { (view, key) -> views.setOnClickPendingIntent(view, listPress(context, id, key)) }
    views.removeAllViews(R.id.music_widget_rows)
    val lists = musicWidgetLists(context)
    val list = lists.getOrNull(musicWidgetListShown(lists, window.list, state.queueSignature))
    if (list == null) {
        views.setTextViewText(R.id.music_widget_list_name, "")
        views.setTextViewText(R.id.music_widget_page, "")
        views.addView(R.id.music_widget_rows, row(context, context.getString(R.string.music_widget_no_lists), current = false, press = null))
        return
    }
    val numbers = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    val pages = musicWidgetPages(list.tracks.size, rows)
    val page = pageOf(list, state, window, rows)
    views.setTextViewText(R.id.music_widget_list_name, list.label)
    views.setTextViewText(R.id.music_widget_page, numbers.format(page + 1) + "/" + numbers.format(pages))
    if (list.tracks.isEmpty()) {
        views.addView(R.id.music_widget_rows, row(context, context.getString(R.string.music_widget_list_empty), current = false, press = null))
        return
    }
    val playing = playingIndexOf(list, state)
    val first = page * rows
    list.tracks.subList(first, minOf(first + rows, list.tracks.size)).forEachIndexed { i, track ->
        val index = first + i
        views.addView(R.id.music_widget_rows, row(context, musicWidgetRowText(index, track), index == playing, rowPress(context, id, i, list.key, track.id)))
    }
}

/** A row of the list: lit white on green as the old players lit the playing track. */
private fun row(context: Context, text: String, current: Boolean, press: PendingIntent?): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.music_widget_row)
    row.setTextViewText(R.id.music_widget_row, text)
    row.setTextColor(R.id.music_widget_row, context.getColor(if (current) R.color.music_widget_current else R.color.music_widget_lcd))
    if (current) row.setInt(R.id.music_widget_row, "setBackgroundColor", context.getColor(R.color.music_widget_current_ground))
    if (press != null) row.setOnClickPendingIntent(R.id.music_widget_row, press)
    return row
}

private fun listPress(context: Context, id: Int, key: MusicWidgetListKey): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        MUSIC_WIDGET_LIST_REQUEST + (id % 1000) * 32 + key.ordinal,
        Intent(context, MusicWidgetReceiver::class.java).setAction(key.action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private fun rowPress(context: Context, id: Int, row: Int, listKey: String, trackId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        MUSIC_WIDGET_LIST_REQUEST + (id % 1000) * 32 + 8 + row,
        Intent(context, MusicWidgetReceiver::class.java)
            .setAction(MusicWidgetListKey.PLAY_ROW.action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(EXTRA_MUSIC_WIDGET_LIST, listKey)
            .putExtra(EXTRA_MUSIC_WIDGET_TRACK, trackId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

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

/** The app on its music's visualizer, full screen. */
private fun openVisualizer(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        MUSIC_WIDGET_REQUEST + 21,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_VISUALIZER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
