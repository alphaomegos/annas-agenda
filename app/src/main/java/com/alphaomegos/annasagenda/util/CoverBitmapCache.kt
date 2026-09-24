package com.alphaomegos.annasagenda.util

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decoded covers, kept so a list does not decode the same file over and over.
 *
 * Every row that scrolls into view used to decode its cover from disk again,
 * because the row's produceState starts fresh each time the row re-enters the
 * composition — which in a LazyColumn is every time it comes back on screen.
 * The visible symptom is the placeholder flashing on the way back up a list
 * the user has already looked at.
 *
 * A ref identifies a picture for good: importing a cover always writes a new
 * ref with a fresh token in it (see CoverRefs), so a cached entry can never be
 * a stale version of a cover that has since been replaced. That is what makes
 * caching by ref safe here rather than merely convenient.
 *
 * Bounded by bytes rather than by count, because a wall tile and a details
 * screen ask for very different sizes of the same picture. The size is part of
 * the key for the same reason.
 */
private const val COVER_CACHE_MAX_BYTES = 16 * 1024 * 1024

private val coverCache = object : LruCache<String, ImageBitmap>(
    minOf(COVER_CACHE_MAX_BYTES.toLong(), Runtime.getRuntime().maxMemory() / 8).toInt()
) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        value.width * value.height * 4
}

internal fun coverCacheKey(coverRef: String, targetMaxSidePx: Int): String =
    "$coverRef|$targetMaxSidePx"

internal fun cachedCoverBitmap(coverRef: String, targetMaxSidePx: Int): ImageBitmap? =
    coverCache.get(coverCacheKey(coverRef, targetMaxSidePx))

internal fun rememberCoverBitmap(
    coverRef: String,
    targetMaxSidePx: Int,
    bitmap: ImageBitmap,
) {
    coverCache.put(coverCacheKey(coverRef, targetMaxSidePx), bitmap)
}

/**
 * Forgets one cover, for the moment it stops existing.
 *
 * Not strictly needed — a deleted ref is never asked for again, since a new
 * import invents a new name — but a deleted cover has no business keeping
 * several megabytes alive until the cache decides to evict it.
 */
internal fun forgetCachedCover(coverRef: String) {
    val prefix = "$coverRef|"

    coverCache.snapshot().keys
        .filter { it.startsWith(prefix) }
        .forEach { coverCache.remove(it) }
}
