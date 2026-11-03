package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.SidecarTrack

/*
 * The phone's own music (02.11, Eduard: the «Local» tab): what the system's media
 * index knows of the audio files on the phone, in the folders the person chose,
 * less what they hid. Played, sorted and queued like the sidecar's tracks — each
 * becomes one, with an id of its own kind («phone:…»).
 */

/** A piece of music on the phone, as the media index knows it. */
data class PhoneTrack(
    val mediaId: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    // Where it lies, from the storage's root, with a slash at the end: «Music/Albums/».
    val folder: String,
    val fileName: String,
    val mimeType: String?,
    // Seconds.
    val addedAt: Long,
)

/** Its name across rescans — where it lies and what it is called: the index's own id can change. */
val PhoneTrack.key: String get() = folder + fileName

const val PHONE_TRACK_PREFIX = "phone:"

/** A phone track's id among the lists' tracks. */
fun phoneTrackId(track: PhoneTrack): String = PHONE_TRACK_PREFIX + track.key

fun isPhoneTrackId(id: String): Boolean = id.startsWith(PHONE_TRACK_PREFIX)

/** As one of the lists' tracks, so it sorts, shows, queues and plays like the sidecar's. */
fun PhoneTrack.asTrack(): SidecarTrack = SidecarTrack(
    id = phoneTrackId(this),
    title = title,
    artist = artist,
    createdByUserId = "",
    createdAt = addedAt,
    approvedAt = addedAt,
    hasAudio = true,
    audioSize = sizeBytes,
    audioFilename = fileName,
    audioContentType = mimeType,
)

/** A folder holding music, and how many tracks lie right in it. */
data class PhoneFolder(val path: String, val tracks: Int)

/** The folders the index found music in, by name. */
fun phoneFolders(all: List<PhoneTrack>): List<PhoneFolder> =
    all.groupingBy { it.folder }.eachCount().map { (path, n) -> PhoneFolder(path, n) }.sortedBy { it.path.lowercase() }

/** Whether [folder] is one of [chosen] or lies inside one: a chosen folder brings its subfolders. */
fun phoneFolderChosen(folder: String, chosen: Set<String>): Boolean = chosen.any { folder.startsWith(it) }

/** What the Local tab lists: the tracks in the chosen folders, less the hidden ones. */
fun phoneTracksShown(all: List<PhoneTrack>, chosen: Set<String>, hidden: Set<String>): List<PhoneTrack> =
    all.filter { phoneFolderChosen(it.folder, chosen) && it.key !in hidden }

/** The hidden ones still on the phone, for the list to bring them back from; by title. */
fun phoneTracksHidden(all: List<PhoneTrack>, hidden: Set<String>): List<PhoneTrack> =
    all.filter { it.key in hidden }.sortedBy { it.title.lowercase() }

/**
 * A folder from a file's full path on phones before Android 10, which have no
 * relative path in the index: [root] (the storage's own) taken off, the file's
 * name too. Outside the root, the path's own folder.
 */
fun phoneFolderOf(path: String, root: String): String {
    val folder = path.substringBeforeLast('/', "") + "/"
    val base = root.trimEnd('/') + "/"
    return if (folder.startsWith(base)) folder.removePrefix(base) else folder.trimStart('/')
}

/** The index's «<unknown>» for a missing tag: no artist at all. */
fun phoneArtist(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }

/** A file name without its extension, for a track the index has no title for. */
fun phoneTitleOf(rawTitle: String?, fileName: String): String =
    rawTitle?.trim()?.takeIf { it.isNotEmpty() } ?: fileName.substringBeforeLast('.', fileName)

/*
 * The sidecar's tracks the phone already has (03.11, Eduard): one whose title and
 * artist match a piece of music on the phone plays from that file — nothing
 * downloaded, nothing kept twice. Matching ignores case, «ё», punctuation and spacing;
 * a remaster or a live version (other words in brackets) is another track. A track the
 * person gave a file of its own keeps it.
 */

/** A title and an artist as matching compares them. */
fun musicMatchKey(title: String, artist: String?): String {
    fun norm(s: String?): String = (s ?: "").lowercase(java.util.Locale.ROOT).replace('ё', 'е')
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    return norm(artist) + "|" + norm(title)
}

/**
 * For each of [tracks] that has no file of its own ([own]), the phone's piece of music with
 * the same title and artist — of several, the biggest file (the better copy).
 */
fun phoneMatches(tracks: List<SidecarTrack>, phone: List<PhoneTrack>, own: Set<String>): Map<String, PhoneTrack> {
    if (phone.isEmpty()) return emptyMap()
    val byKey = phone.groupBy { musicMatchKey(it.title, it.artist) }.mapValues { (_, same) -> same.maxBy { it.sizeBytes } }
    return tracks.asSequence()
        .filter { it.id !in own && !isPhoneTrackId(it.id) }
        .mapNotNull { t -> byKey[musicMatchKey(t.title, t.artist)]?.let { t.id to it } }
        .toMap()
}
