package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.MusicSort
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.support.musicArtists
import com.alphaomegos.annasagenda.support.musicSorted
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The music's lists by name (0396): collation keys made once give the order the collator gives, and keep it stable. */
class MusicSortingTest {
    private val ru = Locale.forLanguageTag("ru")
    private fun t(id: String, artist: String?, title: String) =
        SidecarTrack(id = id, title = title, artist = artist, createdByUserId = "u", approvedAt = 1L)

    @Test
    fun byNameNeitherCaseNorAccentsCountAndEqualsKeepTheirOrder() {
        val tracks = listOf(t("1", "ёлка", "b"), t("2", "Абба", "a"), t("3", "Елка", "B"), t("4", "абба", "A"))
        // «Абба a» and «абба A» are one name to the collator, «ёлка b» and «Елка B» too: they keep their order.
        assertEquals(listOf("2", "4", "1", "3"), musicSorted(tracks, MusicSort.AZ, ru).map { it.id })
        assertEquals(listOf("1", "3", "2", "4"), musicSorted(tracks, MusicSort.ZA, ru).map { it.id })
    }

    @Test
    fun theArtistsComeOnceInTheirOrder() {
        val tracks = listOf(t("1", " Цой ", "x"), t("2", "Агата", "y"), t("3", "Цой", "z"), t("4", null, "w"))
        assertEquals(listOf("Агата", "Цой"), musicArtists(tracks, ru))
    }
}
