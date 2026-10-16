package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MusicSort
import com.alphaomegos.annasagenda.model.MusicTab
import com.alphaomegos.annasagenda.model.SidecarMember
import com.alphaomegos.annasagenda.model.SidecarTrack
import java.text.Collator
import java.util.Locale

/*
 * The music of oursidecar played here (16.10, Eduard): signing in to its
 * server, the lists its web shows, the order of a queue, and which copies of
 * its files this phone keeps.
 *
 * The server is the web's own (worker/core/auth.ts, worker/modules/music.ts
 * in that repository): a session is a cookie it sets on signing in, good for
 * thirty days and not renewed by use; everything else is read with it.
 */

/** Where oursidecar lives. */
const val SIDECAR_DEFAULT_SERVER = "https://oursidecar.com"

/** The cookie the server keeps a session in. */
const val SIDECAR_SESSION_COOKIE = "oursidecar_session"

/** How long a session lasts when the server does not say: its own thirty days. */
const val SIDECAR_SESSION_DEFAULT_SECONDS = 60L * 60 * 24 * 30

/** A session as the server handed it over: its token, and when it ends. */
data class SidecarSession(val token: String, val expiresAtMillis: Long)

/**
 * Whether [token] may go into a Cookie header as it is: printable ASCII with
 * nothing that would end the cookie or the header. The server's are hex; a
 * reply that hands over anything else is not believed.
 */
fun sidecarTokenUsable(token: String): Boolean =
    token.isNotEmpty() && token.all { it in '!'..'~' && it != ';' && it != ',' && it != '"' && it != '\\' }

/**
 * The session in the server's Set-Cookie [headers] at [nowMillis] (16.10):
 * the token of its cookie, ending after its Max-Age — after the server's
 * thirty days when it gives none. Null when no header sets that cookie, or
 * the last one to name it clears it (signing out does).
 */
fun sidecarSessionFromCookies(headers: List<String>, nowMillis: Long): SidecarSession? {
    var found: SidecarSession? = null
    for (header in headers) {
        val parts = header.split(';').map { it.trim() }
        val first = parts.first()
        val eq = first.indexOf('=')
        if (eq <= 0 || first.substring(0, eq).trim() != SIDECAR_SESSION_COOKIE) continue
        val value = first.substring(eq + 1).trim()
        val maxAge = parts.drop(1).firstNotNullOfOrNull { attribute ->
            val i = attribute.indexOf('=')
            if (i > 0 && attribute.substring(0, i).trim().equals("Max-Age", ignoreCase = true)) {
                attribute.substring(i + 1).trim().toLongOrNull()
            } else {
                null
            }
        }
        found = if (!sidecarTokenUsable(value) || (maxAge != null && maxAge <= 0)) {
            null
        } else {
            SidecarSession(value, nowMillis + (maxAge ?: SIDECAR_SESSION_DEFAULT_SECONDS) * 1000L)
        }
    }
    return found
}

/** The Cookie header that carries [token]. */
fun sidecarCookieHeader(token: String): String = "$SIDECAR_SESSION_COOKIE=$token"

/** How the sign-in icon looks (16.10). */
enum class SidecarSignInState {
    /** Nobody signed in. */
    SIGNED_OUT,

    /** Signed in, and the server has not said otherwise. */
    SIGNED_IN,

    /** Somebody was signed in, and the session has ended or the server refused it: sign in again. */
    ATTENTION,
}

/**
 * The icon's state at [nowMillis] (16.10): out with no account kept; in with
 * a session still running that the server has not [rejected]; otherwise the
 * account is known and wants its password again.
 */
fun sidecarSignInState(hasAccount: Boolean, session: SidecarSession?, rejected: Boolean, nowMillis: Long): SidecarSignInState = when {
    !hasAccount -> SidecarSignInState.SIGNED_OUT
    session != null && nowMillis < session.expiresAtMillis && !rejected -> SidecarSignInState.SIGNED_IN
    else -> SidecarSignInState.ATTENTION
}

/**
 * The server's address as typed (16.10), made one this app will talk to:
 * "oursidecar.com" or "https://oursidecar.com/" → "https://oursidecar.com".
 * Null for anything but a bare https host (with a port, if it has one): the
 * session goes with every request, so not over plain http, and not to a
 * path that is not the API's.
 */
fun sidecarServerUrl(raw: String): String? {
    var text = raw.trim().trimEnd('/')
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    if (!text.startsWith("https://", ignoreCase = true)) return null
    val host = text.substring("https://".length)
    if (host.isEmpty() || host.contains('/') || host.contains('@') || host.contains('?') || host.contains('#')) return null
    if (host.any { it.isWhitespace() || it.code < 0x21 || it.code > 0x7E }) return null
    val name = host.substringBefore(':')
    val port = host.substringAfter(':', "")
    if (name.isEmpty() || name.startsWith('.') || name.endsWith('.')) return null
    if (host.contains(':') && (port.toIntOrNull() ?: return null) !in 1..65535) return null
    return "https://" + host.lowercase(Locale.ROOT)
}

/** [id] as one segment of a path: everything but letters, digits and `-._~` escaped. */
fun sidecarPathSegment(id: String): String = buildString {
    for (byte in id.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            append(ch)
        } else {
            append('%')
            append("0123456789ABCDEF"[c shr 4])
            append("0123456789ABCDEF"[c and 0xF])
        }
    }
}

fun sidecarSpacePath(sidecarId: String): String = "/api/sidecars/${sidecarPathSegment(sidecarId)}"

fun sidecarTracksPath(sidecarId: String): String = "/api/sidecars/${sidecarPathSegment(sidecarId)}/tracks"

fun sidecarAudioPath(trackId: String): String = "/api/tracks/${sidecarPathSegment(trackId)}/audio"

/** What went wrong talking to the server, as the screens say it. */
enum class SidecarFailure { NO_NETWORK, SIGNED_OUT, WRONG_PASSWORD, TOO_MANY_ATTEMPTS, NOT_FOUND, SERVER, UNEXPECTED }

/**
 * What an answer of [status] means (16.10); null for a success. A 401 is a
 * wrong password while [signingIn], and an ended session otherwise.
 */
fun sidecarFailureOf(status: Int, signingIn: Boolean = false): SidecarFailure? = when {
    status in 200..299 -> null
    status == 401 -> if (signingIn) SidecarFailure.WRONG_PASSWORD else SidecarFailure.SIGNED_OUT
    status == 429 -> SidecarFailure.TOO_MANY_ATTEMPTS
    status == 404 -> SidecarFailure.NOT_FOUND
    status >= 500 -> SidecarFailure.SERVER
    else -> SidecarFailure.UNEXPECTED
}

/** The whole minutes to wait that a Retry-After of [header] seconds asks for; at least one. Null without one. */
fun sidecarRetryMinutes(header: String?): Long? {
    val seconds = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return maxOf(1L, (seconds + 59) / 60)
}

/* ---------------- the lists (16.10, as the web's MusicSection) ---------------- */

/** A person as the lists name them: their nickname, or their address up to the "@". */
fun musicMemberName(member: SidecarMember): String =
    member.nickname?.trim()?.takeIf { it.isNotEmpty() }
        ?: member.email.substringBefore('@').takeIf { it.isNotEmpty() }
        ?: member.email

/** The lists of a sidecar of [members]: all songs, by artist (when [byArtist]), then each person's. */
fun musicTabs(members: List<SidecarMember>, byArtist: Boolean = true): List<MusicTab> =
    listOf(MusicTab.All) + (if (byArtist) listOf(MusicTab.ByArtist) else emptyList()) + members.map { MusicTab.Member(it.id) }

/** How a list is remembered. */
fun musicTabKey(tab: MusicTab): String = when (tab) {
    MusicTab.All -> "all"
    MusicTab.ByArtist -> "artist"
    is MusicTab.Member -> "member:${tab.userId}"
}

/** A remembered list read back; all songs for anything this build does not know. */
fun parseMusicTab(raw: String?): MusicTab = when {
    raw == "artist" -> MusicTab.ByArtist
    raw != null && raw.startsWith("member:") && raw.length > "member:".length -> MusicTab.Member(raw.substring("member:".length))
    else -> MusicTab.All
}

/** The list to show when [chosen] was picked: it, while the sidecar still has it; all songs when not. */
fun musicTabShown(chosen: MusicTab, tabs: List<MusicTab>): MusicTab = if (chosen in tabs) chosen else MusicTab.All

fun parseMusicSort(raw: String?): MusicSort = MusicSort.entries.firstOrNull { it.name == raw } ?: MusicSort.NEWEST

/** Comparing names as people read them: neither case nor accents count. */
private fun musicCollator(locale: Locale): Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

/** The artists of the approved tracks, each once, in the order of [locale]. */
fun musicArtists(tracks: List<SidecarTrack>, locale: Locale): List<String> {
    val collator = musicCollator(locale)
    return tracks.asSequence()
        .filter { it.approvedAt != null }
        .mapNotNull { it.artist?.trim()?.takeIf { name -> name.isNotEmpty() } }
        .distinct()
        .sortedWith { a, b -> collator.compare(a, b) }
        .toList()
}

/** The artist to show when [chosen] was picked: them while they have tracks, else the first who has. */
fun musicArtistShown(chosen: String?, artists: List<String>): String? =
    chosen?.takeIf { it in artists } ?: artists.firstOrNull()

/** The tracks of [tab]: all the approved, one [artist]'s approved, or all one person added. */
fun musicTracksOf(tracks: List<SidecarTrack>, tab: MusicTab, artist: String?): List<SidecarTrack> = when (tab) {
    MusicTab.All -> tracks.filter { it.approvedAt != null }
    MusicTab.ByArtist -> if (artist == null) emptyList() else tracks.filter { it.approvedAt != null && it.artist?.trim() == artist }
    is MusicTab.Member -> tracks.filter { it.createdByUserId == tab.userId }
}

/**
 * [tracks] in [sort]: by when they were added, or by "artist title" in the
 * order of [locale]. Equal ones keep the order they came in.
 */
fun musicSorted(tracks: List<SidecarTrack>, sort: MusicSort, locale: Locale): List<SidecarTrack> = when (sort) {
    MusicSort.NEWEST -> tracks.sortedByDescending { it.createdAt }
    MusicSort.OLDEST -> tracks.sortedBy { it.createdAt }
    MusicSort.AZ, MusicSort.ZA -> {
        val collator = musicCollator(locale)
        val byName = Comparator<SidecarTrack> { a, b -> collator.compare(musicNameKey(a), musicNameKey(b)) }
        tracks.sortedWith(if (sort == MusicSort.AZ) byName else byName.reversed())
    }
}

private fun musicNameKey(track: SidecarTrack): String = "${track.artist ?: ""} ${track.title}"

/* ---------------- the queue ---------------- */

/** What the player is given: the tracks, and the one to begin with. */
data class MusicQueue(val tracks: List<SidecarTrack>, val startIndex: Int)

/**
 * The queue for pressing play on [start] in a list showing [shown] (16.10),
 * as the web's queue.ts: the list's [playable] tracks, beginning at [start]
 * — the player goes round them — or at the top with no [start]. A [start]
 * the list no longer shows plays alone. Null when there is nothing to play.
 */
fun musicQueue(shown: List<SidecarTrack>, start: SidecarTrack?, playable: (SidecarTrack) -> Boolean): MusicQueue? {
    val list = shown.filter(playable)
    if (start == null) return if (list.isEmpty()) null else MusicQueue(list, 0)
    val index = list.indexOfFirst { it.id == start.id }
    if (index >= 0) return MusicQueue(list, index)
    return if (playable(start)) MusicQueue(listOf(start), 0) else null
}

/** A position as the player shows it: "3:07"; minutes go past sixty, as the web's do. */
fun musicTimeLabel(millis: Long): String {
    if (millis < 0) return "0:00"
    val seconds = millis / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/* ---------------- the copies kept on the phone ---------------- */

/**
 * The name a track's kept copy goes under (16.10): its id and its file's
 * etag, so a file replaced on the server is fetched anew rather than the old
 * one played. Null for a track with no file.
 */
fun musicCacheKey(track: SidecarTrack): String? {
    val etag = track.audioEtag?.takeIf { track.hasAudio && it.isNotEmpty() } ?: return null
    return "track:${track.id}:$etag"
}

/** The track a kept copy's [key] belongs to; null for a name that is not one of these. */
fun musicCacheKeyTrackId(key: String): String? =
    key.removePrefix("track:").takeIf { key.startsWith("track:") }?.substringBefore(':')?.takeIf { it.isNotEmpty() }

/**
 * Which of the [kept] copies are no longer wanted (16.10): the track is gone
 * from [tracks], its file was replaced, or it is one of [notKept] — played
 * from a file of the phone's, or not to be kept at all. [tracks] must be the
 * sidecar's whole list as the server last gave it: with none known, nothing
 * is to be judged stale.
 */
fun musicCacheKeysStale(kept: Set<String>, tracks: List<SidecarTrack>, notKept: Set<String>): Set<String> {
    val wanted = tracks.filter { it.id !in notKept }.mapNotNull { musicCacheKey(it) }.toSet()
    return kept - wanted
}

/** What "save all" would fetch: the tracks, and how many bytes they are. */
data class MusicDownloadPlan(val tracks: List<SidecarTrack>, val bytes: Long)

/**
 * The tracks of [tracks] still to be fetched for keeping (16.10): with a
 * file, not kept whole already ([kept]), and not among [notKept]. Each once.
 */
fun musicDownloadPlan(tracks: List<SidecarTrack>, kept: Set<String>, notKept: Set<String>): MusicDownloadPlan {
    val todo = tracks
        .filter { it.id !in notKept }
        .filter { track -> musicCacheKey(track)?.let { it !in kept } == true }
        .distinctBy { it.id }
    return MusicDownloadPlan(todo, todo.sumOf { it.audioSize ?: 0L })
}
