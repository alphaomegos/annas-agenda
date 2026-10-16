package com.alphaomegos.annasagenda.model

/*
 * Backups of this app kept in the oursidecar account (16.10, Eduard: "so a
 * lost phone loses nothing"). The account's own, not a sidecar's: the
 * other person of a sidecar never sees them. Three kept by hand and one
 * made by itself every week, each up to 200 MB, on the server Eduard runs.
 */

/** Which of the account's places a backup is in: one of three kept by hand, or the one made by itself. */
enum class SidecarBackupSlot(val wire: String) {
    MANUAL("manual"),
    AUTO("auto"),
}

/** What the server does with a fourth backup by hand: asks which to replace, or replaces the oldest. */
enum class SidecarBackupWhenFull(val wire: String) {
    ASK("ask"),
    REPLACE_OLDEST("replace-oldest"),
}

/**
 * A backup as the server lists it. [ready] is false while its parts are
 * still coming. Times are the server's unix seconds; [sha256] is of the
 * bytes on the server — the encrypted ones, when [encrypted].
 */
data class SidecarBackup(
    val id: String,
    val slot: SidecarBackupSlot,
    val ready: Boolean,
    val size: Long,
    val sha256: String,
    val encrypted: Boolean,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val note: String? = null,
    val createdAtSeconds: Long = 0L,
    val completedAtSeconds: Long? = null,
)

/** How much the account may keep: places by hand and by itself, the largest backup, and the size of a part sent. */
data class SidecarBackupLimits(
    val manual: Int = 3,
    val auto: Int = 1,
    val maxSize: Long = 200L * 1024 * 1024,
    val partSize: Long = 8L * 1024 * 1024,
)

/** The account's backups and its rules, as the server last said. */
data class SidecarBackupListing(
    val backups: List<SidecarBackup> = emptyList(),
    val whenFull: SidecarBackupWhenFull = SidecarBackupWhenFull.ASK,
    val limits: SidecarBackupLimits = SidecarBackupLimits(),
)
