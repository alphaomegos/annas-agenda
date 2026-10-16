package com.alphaomegos.annasagenda.model

/*
 * oursidecar (16.10, Eduard): the private place for two that he and Anna
 * keep on the web, whose music this app plays. Read from its server, never
 * written to: what is here is what the server last said, and nothing in
 * this app's own state or backups depends on it.
 */

/** Who is signed in to oursidecar on this phone. */
data class SidecarAccount(val userId: String, val email: String)

/** A sidecar — a place two people share — that the account belongs to. */
data class SidecarSpace(val id: String, val name: String)

/** One of the people of a sidecar, in the order they joined it. */
data class SidecarMember(
    val id: String,
    val email: String,
    val nickname: String? = null,
    val avatarId: String? = null,
    val joinedAt: Long = 0L,
)

/**
 * A track of a sidecar's music, as its server lists it (16.10).
 *
 * [approvedAt] is null while the other person has not agreed to it yet —
 * such a track is in its adder's list only, not in "All songs". [audioEtag]
 * changes whenever its file is replaced, which is what makes a kept copy
 * stale. [createdAt] and [approvedAt] are the server's seconds.
 */
data class SidecarTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    val createdByUserId: String,
    val createdAt: Long = 0L,
    val approvedAt: Long? = null,
    val hasAudio: Boolean = false,
    val audioSize: Long? = null,
    val audioEtag: String? = null,
    val audioFilename: String? = null,
    val audioContentType: String? = null,
    val pendingApproval: Boolean = false,
)

/** A list of the music section (16.10), as the web shows them: all, one artist's, one person's. */
sealed interface MusicTab {
    /** Every approved track. */
    data object All : MusicTab

    /** The approved tracks of one artist, chosen apart. */
    data object ByArtist : MusicTab

    /** What one person added, waiting ones too. */
    data class Member(val userId: String) : MusicTab
}

/** The orders the web offers (16.10): newest, oldest, by name either way. */
enum class MusicSort { NEWEST, OLDEST, AZ, ZA }
