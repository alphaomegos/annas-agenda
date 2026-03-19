package com.alphaomegos.annasagenda.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import com.alphaomegos.annasagenda.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

const val MEDIA_COVER_MAX_SIDE_PX: Int = 800
const val MEDIA_COVER_JPEG_QUALITY: Int = 84

private const val MEDIA_COVER_DIR_NAME = "media_covers"
private const val MEDIA_COVER_REF_PREFIX = "internal://media_covers/"

data class StoredCoverFile(
    val ref: String,
    val file: File,
)

fun isInternalCoverRef(ref: String?): Boolean {
    return !ref.isNullOrBlank() && ref.startsWith(MEDIA_COVER_REF_PREFIX)
}

fun isExternalCoverRef(ref: String?): Boolean {
    return !ref.isNullOrBlank() && !isInternalCoverRef(ref)
}

fun buildInternalCoverRef(
    mediaKind: String,
    itemId: Long,
): String {
    val safeKind = sanitizeMediaKind(mediaKind)
    return "$MEDIA_COVER_REF_PREFIX${safeKind}_$itemId.jpg"
}

fun collectInternalCoverRefs(state: AppState): Set<String> {
    return buildSet {
        state.readingBooks.mapNotNullTo(this) { it.coverUri?.takeIf(::isInternalCoverRef) }
        state.readingMovies.mapNotNullTo(this) { it.coverUri?.takeIf(::isInternalCoverRef) }
        state.readingSeries.mapNotNullTo(this) { it.coverUri?.takeIf(::isInternalCoverRef) }
    }
}

suspend fun importCoverIntoInternalStorage(
    context: Context,
    sourceUri: Uri,
    mediaKind: String,
    itemId: Long,
): String? = withContext(Dispatchers.IO) {
    val bitmap = runCatching {
        decodeScaledBitmapFromUri(
            context = context,
            uri = sourceUri,
            targetMaxSidePx = MEDIA_COVER_MAX_SIDE_PX
        )
    }.getOrNull() ?: return@withContext null

    val ref = buildInternalCoverRef(
        mediaKind = mediaKind,
        itemId = itemId
    )

    val targetFile = internalCoverFileForRef(
        context = context,
        ref = ref
    ) ?: return@withContext null

    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

    try {
        targetFile.parentFile?.mkdirs()

        val ok = FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, MEDIA_COVER_JPEG_QUALITY, out)
        }

        if (!ok) {
            tempFile.delete()
            return@withContext null
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            return@withContext null
        }

        ref
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

suspend fun loadCoverBitmapForUi(
    context: Context,
    coverRef: String?,
    targetMaxSidePx: Int = MEDIA_COVER_MAX_SIDE_PX,
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (coverRef.isNullOrBlank()) return@withContext null

    runCatching {
        val bitmap = if (isInternalCoverRef(coverRef)) {
            val file = internalCoverFileForRef(context, coverRef)
                ?.takeIf { it.isFile }
                ?: return@runCatching null

            decodeScaledBitmapFromFile(
                file = file,
                targetMaxSidePx = targetMaxSidePx
            )
        } else {
            decodeScaledBitmapFromUri(
                context = context,
                uri = coverRef.toUri(),
                targetMaxSidePx = targetMaxSidePx
            )
        }

        bitmap?.asImageBitmap()
    }.getOrNull()
}

suspend fun deleteInternalCoverIfAny(
    context: Context,
    coverRef: String?,
): Boolean = withContext(Dispatchers.IO) {
    if (!isInternalCoverRef(coverRef)) return@withContext false
    val file = internalCoverFileForRef(context, coverRef) ?: return@withContext false
    if (!file.exists()) return@withContext false
    file.delete()
}

suspend fun resolveStoredCoverFiles(
    context: Context,
    state: AppState,
): List<StoredCoverFile> = withContext(Dispatchers.IO) {
    collectInternalCoverRefs(state)
        .mapNotNull { ref ->
            val file = internalCoverFileForRef(context, ref)
                ?.takeIf { it.isFile }
                ?: return@mapNotNull null

            StoredCoverFile(
                ref = ref,
                file = file
            )
        }
}

suspend fun writeInternalCoverBytes(
    context: Context,
    coverRef: String,
    bytes: ByteArray,
): Boolean = withContext(Dispatchers.IO) {
    if (!isInternalCoverRef(coverRef)) return@withContext false

    val targetFile = internalCoverFileForRef(context, coverRef) ?: return@withContext false
    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

    runCatching {
        targetFile.parentFile?.mkdirs()
        tempFile.writeBytes(bytes)

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            false
        } else {
            true
        }
    }.getOrDefault(false)
}

fun zipEntryNameForCoverRef(coverRef: String): String? {
    if (!isInternalCoverRef(coverRef)) return null
    val fileName = coverRef.removePrefix(MEDIA_COVER_REF_PREFIX)
    if (fileName.isBlank()) return null
    return "$MEDIA_COVER_DIR_NAME/$fileName"
}

fun coverRefFromZipEntryName(entryName: String): String? {
    val prefix = "$MEDIA_COVER_DIR_NAME/"
    if (!entryName.startsWith(prefix)) return null

    val fileName = entryName.removePrefix(prefix)
    if (fileName.isBlank()) return null

    return "$MEDIA_COVER_REF_PREFIX$fileName"
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

private fun internalCoverDir(context: Context): File {
    return File(context.filesDir, MEDIA_COVER_DIR_NAME)
}

private fun internalCoverFileForRef(
    context: Context,
    ref: String?,
): File? {
    val safeRef = ref?.takeIf(::isInternalCoverRef) ?: return null

    val fileName = safeRef.removePrefix(MEDIA_COVER_REF_PREFIX)
    if (fileName.isBlank()) return null
    if (fileName.contains('/') || fileName.contains('\\')) return null

    return File(internalCoverDir(context), fileName)
}

private fun decodeScaledBitmapFromUri(
    context: Context,
    uri: Uri,
    targetMaxSidePx: Int,
): Bitmap? {
    return if (Build.VERSION.SDK_INT >= 28) {
        decodeScaledBitmapFromUriApi28(
            context = context,
            uri = uri,
            targetMaxSidePx = targetMaxSidePx
        )
    } else {
        decodeScaledBitmapFromUriLegacy(
            context = context,
            uri = uri,
            targetMaxSidePx = targetMaxSidePx
        )
    }
}

private fun decodeScaledBitmapFromUriApi28(
    context: Context,
    uri: Uri,
    targetMaxSidePx: Int,
): Bitmap? {
    val source = ImageDecoder.createSource(context.contentResolver, uri)

    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val srcW = info.size.width.coerceAtLeast(1)
        val srcH = info.size.height.coerceAtLeast(1)
        val maxSide = max(srcW, srcH)
        val scale = min(1f, targetMaxSidePx.toFloat() / maxSide.toFloat())

        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

        if (scale < 1f) {
            val dstW = max(1, (srcW * scale).toInt())
            val dstH = max(1, (srcH * scale).toInt())
            decoder.setTargetSize(dstW, dstH)
        }
    }
}

private fun decodeScaledBitmapFromUriLegacy(
    context: Context,
    uri: Uri,
    targetMaxSidePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = calculateInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            targetMaxSidePx = targetMaxSidePx
        )
    }

    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: return null

    return scaleBitmapIfNeeded(
        bitmap = decoded,
        targetMaxSidePx = targetMaxSidePx
    )
}

private fun decodeScaledBitmapFromFile(
    file: File,
    targetMaxSidePx: Int,
): Bitmap? {
    if (!file.isFile) return null

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = calculateInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            targetMaxSidePx = targetMaxSidePx
        )
    }

    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

    return scaleBitmapIfNeeded(
        bitmap = decoded,
        targetMaxSidePx = targetMaxSidePx
    )
}

private fun scaleBitmapIfNeeded(
    bitmap: Bitmap,
    targetMaxSidePx: Int,
): Bitmap {
    val srcW = bitmap.width.coerceAtLeast(1)
    val srcH = bitmap.height.coerceAtLeast(1)
    val maxSide = max(srcW, srcH)

    if (maxSide <= targetMaxSidePx) {
        return bitmap
    }

    val scale = targetMaxSidePx.toFloat() / maxSide.toFloat()
    val dstW = max(1, (srcW * scale).toInt())
    val dstH = max(1, (srcH * scale).toInt())

    val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
    if (scaled != bitmap && !bitmap.isRecycled) {
        bitmap.recycle()
    }
    return scaled
}

private fun calculateInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    targetMaxSidePx: Int,
): Int {
    var sampleSize = 1
    var curW = srcWidth
    var curH = srcHeight

    while (max(curW, curH) > targetMaxSidePx * 2) {
        curW /= 2
        curH /= 2
        sampleSize *= 2
    }

    return sampleSize.coerceAtLeast(1)
}