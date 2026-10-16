package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupListing
import com.alphaomegos.annasagenda.model.SidecarBackupSlot

/*
 * This app's backups in the oursidecar account (16.10): the rules of the
 * server Eduard wrote for them (its claude/backups-api.md).
 *
 * A phone signs in once with the account's address and password and gets a
 * token of its own, good until it is revoked — on the web, or by signing
 * out here — and good for backups only. A backup goes up in parts of the
 * size the server names (8 MiB), each sent whole with its length, in any
 * order and again if need be; then it is finished. One not finished in a
 * day is dropped by the server.
 */

/** What the server's tokens for backups begin with. */
const val SIDECAR_BACKUP_TOKEN_PREFIX = "osb_"

/** The longest note the server keeps with a backup, and the longest app version. */
const val SIDECAR_BACKUP_NOTE_MAX = 200
const val SIDECAR_BACKUP_APP_VERSION_MAX = 40

/** How long a phone's name is let be: the server's own limit is not written down, and this is well under any. */
const val SIDECAR_BACKUP_DEVICE_NAME_MAX = 60

fun sidecarBackupDeviceSignInPath(): String = "/api/backup-devices/sign-in"

fun sidecarBackupDevicePath(deviceId: String): String = "/api/backup-devices/${sidecarPathSegment(deviceId)}"

fun sidecarBackupsPath(): String = "/api/backups"

fun sidecarBackupSettingsPath(): String = "/api/backups/settings"

fun sidecarBackupPath(backupId: String): String = "/api/backups/${sidecarPathSegment(backupId)}"

fun sidecarBackupPartPath(backupId: String, part: Int): String = sidecarBackupPath(backupId) + "/parts/$part"

fun sidecarBackupFinishPath(backupId: String): String = sidecarBackupPath(backupId) + "/finish"

fun sidecarBackupDownloadPath(backupId: String): String = sidecarBackupPath(backupId) + "/download"

/** The Authorization header a backup request goes with. */
fun sidecarBearerHeader(token: String): String = "Bearer $token"

/** Whether [token] is one the server gives for backups. */
fun sidecarBackupTokenUsable(token: String?): Boolean =
    token != null && token.startsWith(SIDECAR_BACKUP_TOKEN_PREFIX) && token.length > SIDECAR_BACKUP_TOKEN_PREFIX.length &&
        token.none { it.isWhitespace() || it.code < 0x21 || it.code > 0x7E }

/* ---------------- parts ---------------- */

/** How many parts a backup of [size] bytes goes in: every one [partSize] long but the last. */
fun sidecarBackupPartCount(size: Long, partSize: Long): Int {
    require(size > 0 && partSize > 0)
    return ((size + partSize - 1) / partSize).toInt()
}

/** Where part [part] (from 1) begins in the file, and how long it is. */
fun sidecarBackupPartRange(size: Long, partSize: Long, part: Int): LongRange {
    val count = sidecarBackupPartCount(size, partSize)
    require(part in 1..count)
    val start = (part - 1) * partSize
    val end = minOf(size, start + partSize) - 1
    return start..end
}

/** The parts still to send, in order, of [count] when the server has [received]. */
fun sidecarBackupPartsMissing(count: Int, received: Collection<Int>): List<Int> {
    val have = received.toSet()
    return (1..count).filter { it !in have }
}

/* ---------------- what went wrong ---------------- */

enum class SidecarBackupFailure {
    NO_NETWORK,

    // The phone's token was revoked (on the web, or the account signed out): sign in again.
    SIGNED_OUT,
    WRONG_PASSWORD,
    TOO_MANY_ATTEMPTS,

    // The account is not one the server keeps backups for.
    NOT_ENABLED,

    // Three by hand already, and the server asks which one goes.
    FULL,
    PARTS_MISSING,
    TOO_LARGE,
    BAD_REPLACE,

    // Gone from the server: deleted, or an upload left for a day.
    GONE,
    SERVER,

    // What came down is not what went up: its sha256 differs.
    DAMAGED,

    // Encrypted with a password that is not the one given.
    WRONG_BACKUP_PASSWORD,

    // Backups are to be encrypted and the key is not on the phone: sign in again.
    NO_KEY,

    // Whether to encrypt is not chosen yet: nothing goes up until it is.
    NOT_CHOSEN,
    UNEXPECTED,
}

/**
 * What an answer of [status] with the error [code] of its body means
 * (16.10); null for a success. A 401 is a wrong password while
 * [signingIn], and a revoked token otherwise.
 */
fun sidecarBackupFailureOf(status: Int, code: String?, signingIn: Boolean = false): SidecarBackupFailure? = when {
    status in 200..299 -> null
    status == 401 -> if (signingIn) SidecarBackupFailure.WRONG_PASSWORD else SidecarBackupFailure.SIGNED_OUT
    status == 429 -> SidecarBackupFailure.TOO_MANY_ATTEMPTS
    status == 403 && code == "backups_not_enabled" -> SidecarBackupFailure.NOT_ENABLED
    status == 404 -> SidecarBackupFailure.GONE
    status == 409 && code == "backups_full" -> SidecarBackupFailure.FULL
    status == 409 && code == "parts_missing" -> SidecarBackupFailure.PARTS_MISSING
    status == 400 && code == "too_large" -> SidecarBackupFailure.TOO_LARGE
    status == 400 && code == "bad_replace" -> SidecarBackupFailure.BAD_REPLACE
    status >= 500 -> SidecarBackupFailure.SERVER
    else -> SidecarBackupFailure.UNEXPECTED
}

/* ---------------- the list ---------------- */

/** The backups to show (16.10): the finished ones, by hand first, each kind newest first. */
fun sidecarBackupsShown(listing: SidecarBackupListing): List<SidecarBackup> =
    listing.backups
        .filter { it.ready }
        .sortedWith(compareBy<SidecarBackup> { it.slot != SidecarBackupSlot.MANUAL }.thenByDescending { it.createdAtSeconds }.thenBy { it.id })

/** The backups by hand that a new one may take the place of, the oldest first. */
fun sidecarBackupReplaceChoices(listing: SidecarBackupListing): List<SidecarBackup> =
    listing.backups
        .filter { it.ready && it.slot == SidecarBackupSlot.MANUAL }
        .sortedWith(compareBy<SidecarBackup> { it.createdAtSeconds }.thenBy { it.id })

/** Whether the places by hand are all taken, so that a new one replaces one of them. */
fun sidecarBackupManualFull(listing: SidecarBackupListing): Boolean =
    sidecarBackupReplaceChoices(listing).size >= listing.limits.manual

/** How many of each kind there are: by hand, and made by itself. */
fun sidecarBackupCounts(listing: SidecarBackupListing): Pair<Int, Int> {
    val ready = listing.backups.filter { it.ready }
    return ready.count { it.slot == SidecarBackupSlot.MANUAL } to ready.count { it.slot == SidecarBackupSlot.AUTO }
}

/* ---------------- what goes with a backup ---------------- */

/** A note as the server keeps it: one line, trimmed, at most its length; none when empty. */
fun sidecarBackupNote(raw: String?): String? =
    raw?.replace(Regex("\\s+"), " ")?.trim()?.take(SIDECAR_BACKUP_NOTE_MAX)?.trim()?.takeIf { it.isNotEmpty() }

/** The app's version as it goes with a backup. */
fun sidecarBackupAppVersion(versionName: String): String = versionName.trim().take(SIDECAR_BACKUP_APP_VERSION_MAX)

/** The phone's name as the list on the web shows it: "Samsung SM-F926B", the maker once. */
fun sidecarBackupDeviceName(manufacturer: String?, model: String?): String {
    val maker = manufacturer?.trim().orEmpty()
    val kind = model?.trim().orEmpty()
    val name = when {
        kind.isEmpty() -> maker
        maker.isEmpty() || kind.startsWith(maker, ignoreCase = true) -> kind
        else -> maker.replaceFirstChar { it.uppercaseChar() } + " " + kind
    }
    return name.take(SIDECAR_BACKUP_DEVICE_NAME_MAX).trim().ifEmpty { "Android" }
}

/** Whether [text] is a sha256 as the server takes it: 64 hex digits. */
fun sidecarBackupShaValid(text: String?): Boolean =
    text != null && text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/** [bytes] as the lowercase hex the server keeps a sha256 in. */
fun sidecarHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        append("0123456789abcdef"[v shr 4])
        append("0123456789abcdef"[v and 0xF])
    }
}

/** The bytes [text] spells in hex; null when it is not hex of whole bytes. */
fun sidecarUnhex(text: String): ByteArray? {
    if (text.length % 2 != 0) return null
    val out = ByteArray(text.length / 2)
    for (i in out.indices) {
        val high = Character.digit(text[2 * i], 16)
        val low = Character.digit(text[2 * i + 1], 16)
        if (high < 0 || low < 0) return null
        out[i] = ((high shl 4) or low).toByte()
    }
    return out
}

/** A backup key as it is kept sealed on the phone: the rounds, the salt and the key. */
fun backupKeyText(key: BackupKey): String = "${key.iterations}:${sidecarHex(key.salt)}:${sidecarHex(key.bytes)}"

/** The key [text] keeps; null when it is not one. */
fun backupKeyOf(text: String?): BackupKey? {
    val parts = text?.split(':') ?: return null
    if (parts.size != 3) return null
    val iterations = parts[0].toIntOrNull() ?: return null
    val salt = sidecarUnhex(parts[1]) ?: return null
    val bytes = sidecarUnhex(parts[2]) ?: return null
    return runCatching { BackupKey(salt, iterations, bytes) }.getOrNull()
}

/** When a backup was made, as the list says it: the date and the time, in [zone] and the app's [locale]. */
fun sidecarBackupMoment(epochSeconds: Long, zone: java.time.ZoneId, locale: java.util.Locale): String =
    java.time.format.DateTimeFormatter
        .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
        .withLocale(locale)
        .format(java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone))
