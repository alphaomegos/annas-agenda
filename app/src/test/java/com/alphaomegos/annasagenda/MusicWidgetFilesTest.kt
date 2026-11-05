package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player widget (0386, Eduard 05.11), read from the files: Android knows
 * the widget; its window holds only views a widget may; the time is the system
 * chronometer and the line runs; the playback service draws it; and nobody's
 * player is named in it — the mark is a letter «A».
 */
class MusicWidgetFilesTest {
    private val res = mainResRoot()

    @Test
    fun androidKnowsTheWidget() {
        val manifest = File(res.parentFile, "AndroidManifest.xml").readText()
        val receiver = Regex("""<receiver\s+android:name="\.MusicWidgetReceiver"[\s\S]*?</receiver>""").find(manifest)?.value
        assertTrue("no MusicWidgetReceiver in the manifest", receiver != null)
        assertTrue(receiver!!.contains("android.appwidget.action.APPWIDGET_UPDATE") && receiver.contains("@xml/music_widget_info"))
        assertTrue(File(res, "xml/music_widget_info.xml").readText().contains("@layout/music_widget"))
    }

    @Test
    fun theWindowHoldsOnlyWhatAWidgetMay() {
        val layout = File(res, "layout/music_widget.xml").readText()
        val allowed = setOf("LinearLayout", "FrameLayout", "TextView", "ImageView", "ImageButton", "Chronometer", "requestFocus")
        val tags = Regex("""<([A-Za-z][A-Za-z.]*)""").findAll(layout).map { it.groupValues[1] }.toSet()
        assertTrue("a widget cannot hold ${tags - allowed}", (tags - allowed).isEmpty())
        assertTrue(layout.contains("<Chronometer") && layout.contains("android:id=\"@+id/music_widget_time\""))
        assertTrue("the line does not run", layout.contains("android:ellipsize=\"marquee\"") && layout.contains("<requestFocus"))
    }

    @Test
    fun theServiceDrawsIt() {
        val service = File(mainSourceRoot(), "com/alphaomegos/annasagenda/app/MusicPlaybackService.kt").readText()
        assertTrue(service.hasCode("musicWidgetShow(this@MusicPlaybackService, musicWidgetStateOf(player))"))
        assertTrue(service.hasCode("musicServiceAlive = true") && service.hasCode("musicServiceAlive = false"))
    }

    /** Eduard: neither the name nor the logo of the player it recalls. */
    @Test
    fun nobodysPlayerIsNamed() {
        val files = listOf(File(res, "layout/music_widget.xml"), File(res, "xml/music_widget_info.xml")) +
            res.listFiles { f -> f.name.startsWith("values") }!!.flatMap { it.listFiles()!!.toList() } +
            File(mainSourceRoot(), "com/alphaomegos/annasagenda/app").listFiles { f -> f.name.startsWith("MusicWidget") }!!.toList()
        files.forEach { assertTrue("${it.name} names it", !it.readText().contains("winamp", ignoreCase = true)) }
    }
}
