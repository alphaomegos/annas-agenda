package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's pictures are lossless WebP, not PNG (19.10, Eduard: «Пережми в
 * png, я одобряю»; agreed 30.09 as lossless, not q90).
 *
 * The conversion took 11.8 MB of drawables to 8.2 MB, and every one of them
 * decodes to exactly the pixels the PNG held (checked when converting). This
 * keeps it that way: a PNG dropped in later is the 400 KB the conversion took
 * out, and a lossy WebP is a picture that is no longer the one Eduard drew.
 *
 * Lossless and lossy WebP differ in their first chunk: VP8L is lossless, VP8
 * (with a space) is lossy, and VP8X is the extended form that wraps either —
 * the converter writes none, so none is allowed.
 */
class DrawablesAreLosslessWebpTest {

    private val pictures: List<File> by lazy {
        mainResRoot().listFiles().orEmpty()
            .filter { it.isDirectory && (it.name.startsWith("drawable") || it.name.startsWith("mipmap")) }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.isFile }
    }

    @Test
    fun noPictureIsAPng() {
        val pngs = pictures.filter { it.name.endsWith(".png", ignoreCase = true) }.map { "${it.parentFile.name}/${it.name}" }
        assertTrue("convert these to lossless WebP: $pngs", pngs.isEmpty())
    }

    @Test
    fun everyWebpIsLossless() {
        val webps = pictures.filter { it.name.endsWith(".webp") }
        // The menu, the languages, the filters, the lamps, the placeholders and the launcher.
        assertTrue("only ${webps.size} WebP pictures found; is this the resource tree?", webps.size >= 50)
        val notLossless = webps.mapNotNull { file ->
            val head = file.inputStream().use { it.readNBytes(16) }
            val riff = String(head, 0, 4, Charsets.US_ASCII)
            val form = String(head, 8, 4, Charsets.US_ASCII)
            val chunk = String(head, 12, 4, Charsets.US_ASCII)
            if (riff == "RIFF" && form == "WEBP" && chunk == "VP8L") null else "${file.parentFile.name}/${file.name} ($chunk)"
        }
        assertEquals("not lossless WebP: $notLossless", emptyList<String>(), notLossless)
    }
}
