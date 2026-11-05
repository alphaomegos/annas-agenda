package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.model.MusicRepeat
import com.alphaomegos.annasagenda.support.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The player widget (0386, Eduard 05.11): what the main window shows and what its keys mean. */
class MusicWidgetTest {
    @Test
    fun theLineIsTheArtistAndTheTitle() {
        assertEquals("Ансамбль — Песня о ветре", musicWidgetLine(MusicWidgetState(title = "Песня о ветре", artist = "Ансамбль")))
        // The media index's «<unknown>» is no artist.
        assertEquals("Песня о ветре", musicWidgetLine(MusicWidgetState(title = " Песня о ветре ", artist = "<unknown>")))
        assertNull(musicWidgetLine(MusicWidgetState(title = "  ", artist = "Ансамбль")))
    }

    @Test
    fun theChronometerCountsFromNowLessThePosition() {
        assertEquals(100_000L - 63_000L, musicWidgetChronometerBase(100_000L, 63_000L))
        assertEquals(100_000L, musicWidgetChronometerBase(100_000L, -5L))
    }

    @Test
    fun everyKeyIsKnownByItsAction() {
        MusicWidgetKey.entries.forEach { assertEquals(it, musicWidgetKeyOf(it.action)) }
        assertEquals(MusicWidgetKey.entries.size, MusicWidgetKey.entries.map { it.action }.toSet().size)
        assertNull(musicWidgetKeyOf("android.appwidget.action.APPWIDGET_UPDATE"))
        assertNull(musicWidgetKeyOf(null))
    }

    @Test
    fun repeatGoesRound() {
        assertEquals(MusicRepeat.ONE, musicRepeatAfter(MusicRepeat.ALL))
        assertEquals(MusicRepeat.OFF, musicRepeatAfter(MusicRepeat.ONE))
        assertEquals(MusicRepeat.ALL, musicRepeatAfter(MusicRepeat.OFF))
    }

    @Test
    fun theKeptStateComesBackAndNonsenseIsTheDefault() {
        val state = MusicWidgetState(
            title = "Песня о ветре", artist = "Ансамбль", playing = true, positionMs = 61_500L,
            shuffle = true, repeat = MusicRepeat.ONE, hasQueue = true,
        )
        assertEquals(state, musicWidgetStateFromMap(musicWidgetStateToMap(state)))
        assertEquals(MusicWidgetState(), musicWidgetStateFromMap(mapOf("position" to "минус", "repeat" to "TWICE", "playing" to null)))
    }
}
