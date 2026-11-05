package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MusicRepeat

/*
 * The player's widget (05.11, Eduard): a small player on the home screen in
 * the spirit of the old desktop players — the track running along a line, the
 * time in green LCD figures, ⏮ ⏯ ⏹ ⏭, shuffle and repeat — working the app's
 * one player. No name or logo of anybody's player: a letter «A» is the mark.
 * What it shows and what its keys mean are decided here; the drawing is
 * MusicWidgetViews, the keys MusicWidgetReceiver.
 */

/** What the widget shows: the track, whether it plays and where, how the player goes on. */
data class MusicWidgetState(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: MusicRepeat = MusicRepeat.ALL,
    // A queue in the player for the keys to work; without one ⏯ opens the app, where it comes back.
    val hasQueue: Boolean = false,
)

/** The line that runs: «Artist — Title»; the title alone with no artist; null with no track. */
fun musicWidgetLine(state: MusicWidgetState): String? {
    val title = state.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // The media index says «<unknown>» for a file with no artist (03.11).
    val artist = state.artist?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
    return if (artist != null) "$artist — $title" else title
}

/**
 * Where the system chronometer counts up from: now less the position, on the
 * clock it uses (elapsed realtime). It ticks by itself while the music plays,
 * so the widget is drawn at the player's changes only, never every second.
 */
fun musicWidgetChronometerBase(nowElapsedMs: Long, positionMs: Long): Long =
    nowElapsedMs - positionMs.coerceAtLeast(0L)

/** The figures an LCD shows unlit behind the time, as the old players' did. */
const val MUSIC_WIDGET_GHOST = "88:88"

/** The widget's keys, and the action each one's broadcast carries. */
enum class MusicWidgetKey(val action: String) {
    PREVIOUS("com.alphaomegos.annasagenda.music.widget.PREVIOUS"),
    PLAY_PAUSE("com.alphaomegos.annasagenda.music.widget.PLAY_PAUSE"),
    STOP("com.alphaomegos.annasagenda.music.widget.STOP"),
    NEXT("com.alphaomegos.annasagenda.music.widget.NEXT"),
    SHUFFLE("com.alphaomegos.annasagenda.music.widget.SHUFFLE"),
    REPEAT("com.alphaomegos.annasagenda.music.widget.REPEAT"),
}

fun musicWidgetKeyOf(action: String?): MusicWidgetKey? = MusicWidgetKey.entries.firstOrNull { it.action == action }

/** Repeat's key goes round: the whole queue, this track, no repeat, and the queue again. */
fun musicRepeatAfter(repeat: MusicRepeat): MusicRepeat = when (repeat) {
    MusicRepeat.ALL -> MusicRepeat.ONE
    MusicRepeat.ONE -> MusicRepeat.OFF
    MusicRepeat.OFF -> MusicRepeat.ALL
}

/** The state as text, for the small file the widget is drawn from when the player is gone. */
fun musicWidgetStateToMap(state: MusicWidgetState): Map<String, String> = buildMap {
    state.title?.let { put("title", it) }
    state.artist?.let { put("artist", it) }
    put("playing", state.playing.toString())
    put("position", state.positionMs.toString())
    put("shuffle", state.shuffle.toString())
    put("repeat", state.repeat.name)
    put("queue", state.hasQueue.toString())
}

/** And back: anything missing or not understood is the default. */
fun musicWidgetStateFromMap(map: Map<String, String?>): MusicWidgetState = MusicWidgetState(
    title = map["title"],
    artist = map["artist"],
    playing = map["playing"] == "true",
    positionMs = map["position"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
    shuffle = map["shuffle"] == "true",
    repeat = MusicRepeat.entries.firstOrNull { it.name == map["repeat"] } ?: MusicRepeat.ALL,
    hasQueue = map["queue"] == "true",
)
