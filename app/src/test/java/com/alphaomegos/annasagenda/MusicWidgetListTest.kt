package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** The player widget's list window (0388, Eduard 05.11): the lists left for it, the pages, which list is playing. */
class MusicWidgetListTest {
    private fun track(id: String, title: String = "Песня $id") = MusicWidgetTrack(id, title, "Ансамбль", "https://s/a/$id", "k$id")
    private val phone = MusicWidgetList("local", "Локаль", listOf(MusicWidgetTrack("phone:1", "Своя", "Я", "content://m/1")))
    private val all = MusicWidgetList("all", "OURPLACE", listOf(track("a"), track("b"), track("c")))

    @Test
    fun theListsComeBackFromTheirFile() {
        val odd = MusicWidgetList("member:7", "Аня\tи\nВаня", listOf(track("d", "Две\tстроки\nв одной")))
        val back = musicWidgetCatalogOf(musicWidgetCatalogText(listOf(phone, all, odd)))
        assertEquals(listOf(phone, all), back.take(2))
        assertEquals("Аня и Ваня", back[2].label)
        assertEquals("Две строки в одной", back[2].tracks.single().title)
        // Nonsense, and a track before any list, are passed over.
        assertEquals(listOf(all), musicWidgetCatalogOf("T\tx\ty\tz\tu\tk\nчто-то\n" + musicWidgetCatalogText(listOf(all))))
    }

    @Test
    fun aPageIsWhatTheWindowHolds() {
        assertEquals(3, musicWidgetRows(110))
        assertEquals(7, musicWidgetRows(74 + 7 * 18 + 5))
        assertEquals(14, musicWidgetRows(2000))
        assertEquals(1, musicWidgetPages(0, 5))
        assertEquals(3, musicWidgetPages(11, 5))
        // Following the playing track: its page; not following: the one asked for, within the list.
        assertEquals(2, musicWidgetPage(asked = 0, pages = 3, playingIndex = 10, rows = 5, follow = true))
        assertEquals(1, musicWidgetPage(asked = 1, pages = 3, playingIndex = 10, rows = 5, follow = false))
        assertEquals(2, musicWidgetPage(asked = 9, pages = 3, playingIndex = -1, rows = 5, follow = true))
    }

    @Test
    fun theListShownIsTheOneAskedForThenThePlayingOne() {
        val lists = listOf(phone, all)
        val playingAll = musicWidgetSignature(listOf("a", "b", "c"))
        assertEquals(0, musicWidgetListShown(lists, "local", playingAll))
        assertEquals(1, musicWidgetListShown(lists, null, playingAll))
        assertEquals(1, musicWidgetListShown(lists, "member:gone", playingAll))
        assertEquals(0, musicWidgetListShown(lists, null, musicWidgetSignature(listOf("c", "b", "a"))))
        assertEquals(-1, musicWidgetListShown(emptyList(), null, null))
        assertEquals(0, musicWidgetListAfter(1, 2, 1))
        assertEquals(1, musicWidgetListAfter(0, 2, -1))
    }

    @Test
    fun aRowIsNumberedAsTheOldPlayersDid() {
        assertEquals("3. Ансамбль — Песня c", musicWidgetRowText(2, track("c")))
    }

    @Test
    fun theKeptStateKeepsTheTrackAndTheQueue() {
        val state = MusicWidgetState(title = "Песня a", trackId = "a", queueSignature = musicWidgetSignature(listOf("a", "b")))
        assertEquals(state, musicWidgetStateFromMap(musicWidgetStateToMap(state)))
    }
}
