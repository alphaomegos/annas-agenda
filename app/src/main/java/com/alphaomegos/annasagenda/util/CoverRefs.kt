package com.alphaomegos.annasagenda.util

import java.util.UUID

/**
 * How a cover image is named, and nothing else.
 *
 * Kept free of Android so the naming rules can be pinned by plain JVM tests —
 * they are the thing everything else about covers is built on.
 *
 * A ref looks like `internal://media_covers/book_17_9f3a1c02.jpg`. The trailing
 * token is what makes it a new name every time a cover is imported, and that
 * matters more than it looks: the ref used to be derived from the media kind
 * and the item id alone, so replacing a cover produced the identical string.
 * The file on disk was overwritten, but the app state came out equal to what it
 * already was — and an equal value is not emitted by a StateFlow, nor does it
 * change the key the image loaders are watching. The old picture stayed on
 * screen while the new one sat on disk, and the one it replaced was already
 * destroyed. A fresh name makes the change visible to all of them at once, and
 * the existing cleanup — which deletes any ref the state no longer mentions —
 * takes the old file away by itself.
 *
 * Refs written before this change carry no token. Nothing parses a ref back
 * into a kind and an id, so they keep working exactly as they are.
 */

internal const val MEDIA_COVER_DIR_NAME = "media_covers"
internal const val MEDIA_COVER_REF_PREFIX = "internal://media_covers/"

fun isInternalCoverRef(ref: String?): Boolean {
    return !ref.isNullOrBlank() && ref.startsWith(MEDIA_COVER_REF_PREFIX)
}

fun isExternalCoverRef(ref: String?): Boolean {
    return !ref.isNullOrBlank() && !isInternalCoverRef(ref)
}

/**
 * A name for a newly imported cover. Never equal to the previous one for the
 * same item, which is the whole point — see the note above.
 */
fun buildInternalCoverRef(
    mediaKind: String,
    itemId: Long,
    token: String = newCoverToken(),
): String {
    val safeKind = sanitizeMediaKind(mediaKind)
    val safeToken = sanitizeCoverToken(token)
    return "$MEDIA_COVER_REF_PREFIX${safeKind}_${itemId}_$safeToken.jpg"
}

fun newCoverToken(): String = UUID.randomUUID().toString().replace("-", "").take(12)

/** The file name a ref points at, or null if the ref is not one of ours. */
internal fun coverFileNameForRef(ref: String?): String? {
    val safeRef = ref?.takeIf(::isInternalCoverRef) ?: return null

    val fileName = safeRef.removePrefix(MEDIA_COVER_REF_PREFIX)
    if (fileName.isBlank()) return null

    // A ref reaches us from a backup someone else wrote, so it is not to be
    // trusted with a path.
    if (fileName.contains('/') || fileName.contains('\\')) return null
    if (fileName == "." || fileName == "..") return null

    return fileName
}

fun zipEntryNameForCoverRef(coverRef: String): String? {
    val fileName = coverFileNameForRef(coverRef) ?: return null
    return "$MEDIA_COVER_DIR_NAME/$fileName"
}

fun coverRefFromZipEntryName(entryName: String): String? {
    val prefix = "$MEDIA_COVER_DIR_NAME/"
    if (!entryName.startsWith(prefix)) return null

    val fileName = entryName.removePrefix(prefix)
    if (fileName.isBlank()) return null

    return coverFileNameForRef("$MEDIA_COVER_REF_PREFIX$fileName")
        ?.let { "$MEDIA_COVER_REF_PREFIX$it" }
}

private fun sanitizeMediaKind(raw: String): String {
    val trimmed = raw.trim().lowercase()
    return trimmed
        .map { ch ->
            when {
                ch in 'a'..'z' -> ch
                ch in '0'..'9' -> ch
                else -> '_'
            }
        }
        .joinToString("")
        .ifBlank { "item" }
}

private fun sanitizeCoverToken(raw: String): String {
    val safe = raw.trim().lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' }
        .take(32)

    return safe.ifBlank { newCoverToken() }
}
