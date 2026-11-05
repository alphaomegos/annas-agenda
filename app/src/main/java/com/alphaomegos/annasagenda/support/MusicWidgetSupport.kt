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
    // The playing track, and which list the queue is (musicWidgetSignature): the list window's light.
    val trackId: String? = null,
    val queueSignature: String? = null,
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
    state.trackId?.let { put("track", it) }
    state.queueSignature?.let { put("signature", it) }
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
    trackId = map["track"],
    queueSignature = map["signature"],
)

/*
 * The list window (05.11, step two): beside the main window on a wide widget,
 * shown and hidden by its own key as the old players' playlist was. On top the
 * music's lists — the phone's, the sidecar's, each person's — one at a time,
 * ◀ ▶ going round them; under it the list's tracks, a page at a time, ▲ ▼.
 * A tap on a track of the list that is playing goes to it; on any other list's
 * it plays that list from there, as a tap in the app does.
 */

/** One list the widget shows and plays: a tab of the music, its playable tracks in the screen's order. */
data class MusicWidgetList(val key: String, val label: String, val tracks: List<MusicWidgetTrack>)

/** A track as the widget needs it: to show it, and to hand it to the player — its address and the kept copy's name. */
data class MusicWidgetTrack(val id: String, val title: String, val artist: String, val uri: String, val cacheKey: String? = null)

/** The lists as text, for the file the music screen leaves the widget: a line a list, a line a track under it. */
fun musicWidgetCatalogText(lists: List<MusicWidgetList>): String = buildString {
    lists.forEach { list ->
        append("L\t").append(field(list.key)).append('\t').append(field(list.label)).append('\n')
        list.tracks.forEach { t ->
            append("T\t").append(field(t.id)).append('\t').append(field(t.title)).append('\t').append(field(t.artist))
                .append('\t').append(field(t.uri)).append('\t').append(field(t.cacheKey ?: "")).append('\n')
        }
    }
}

/** A tab or a line end inside a name would split it: a space instead. */
private fun field(text: String): String = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

/** And back; a line not understood is passed over, a track before any list too. */
fun musicWidgetCatalogOf(text: String): List<MusicWidgetList> {
    val lists = mutableListOf<MusicWidgetList>()
    text.lineSequence().forEach { line ->
        val parts = line.split('\t')
        when {
            parts[0] == "L" && parts.size >= 3 -> lists += MusicWidgetList(parts[1], parts[2], emptyList())
            parts[0] == "T" && parts.size >= 6 && lists.isNotEmpty() -> {
                val track = MusicWidgetTrack(parts[1], parts[2], parts[3], parts[4], parts[5].takeIf { it.isNotEmpty() })
                lists[lists.lastIndex] = lists.last().let { it.copy(tracks = it.tracks + track) }
            }
        }
    }
    return lists
}

/** What a queue is, told short: the player's and a list's are the same queue when this is the same. */
fun musicWidgetSignature(ids: List<String>): String = "${ids.size}:${ids.joinToString("\u0001").hashCode()}"

/** The window's height that is not rows: the case, the list's name, the page keys (dp). */
const val MUSIC_WIDGET_LIST_CHROME_DP = 74

/** A row's height (dp). */
const val MUSIC_WIDGET_ROW_DP = 18

/** How many tracks a page shows in a window [heightDp] high: three at least, fourteen at most. */
fun musicWidgetRows(heightDp: Int): Int = ((heightDp - MUSIC_WIDGET_LIST_CHROME_DP) / MUSIC_WIDGET_ROW_DP).coerceIn(3, 14)

/** The pages [count] tracks take, [rows] a page; an empty list has its one page. */
fun musicWidgetPages(count: Int, rows: Int): Int = if (count <= 0) 1 else (count + rows - 1) / rows

/** The page shown: the playing track's while following it, else the one asked for, within the list. */
fun musicWidgetPage(asked: Int, pages: Int, playingIndex: Int, rows: Int, follow: Boolean): Int =
    if (follow && playingIndex >= 0) (playingIndex / rows).coerceIn(0, pages - 1) else asked.coerceIn(0, pages - 1)

/** The list shown: the one asked for, else the one playing, else the first; -1 with none. */
fun musicWidgetListShown(lists: List<MusicWidgetList>, askedKey: String?, queueSignature: String?): Int {
    val asked = lists.indexOfFirst { it.key == askedKey }
    if (asked >= 0) return asked
    val playing = lists.indexOfFirst { list -> queueSignature != null && musicWidgetSignature(list.tracks.map { it.id }) == queueSignature }
    return if (playing >= 0) playing else if (lists.isEmpty()) -1 else 0
}

/** The list [step] lists on from [index], going round. */
fun musicWidgetListAfter(index: Int, count: Int, step: Int): Int = if (count <= 0) -1 else ((index + step) % count + count) % count

/** A row: its number in the list, the artist, the title — the old players' way. */
fun musicWidgetRowText(index: Int, track: MusicWidgetTrack): String = "${index + 1}. ${track.artist} — ${track.title}"
