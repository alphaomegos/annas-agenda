package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The music screen works its lists out again only when what they come from changes (0396), read from the source. */
class MusicScreenRememberTest {
    @Test
    fun theListsAreRemembered() {
        val screen = File(mainSourceRoot(), "com/alphaomegos/annasagenda/screens/music/MusicScreen.kt").readText()
        listOf(
            "val tabs = remember(sidecarIn, lib.members, prefs.artistTab) {",
            "val artists = remember(lib.tracks, locale) {",
            "val shown = remember(tab, phoneShown, lib.tracks, artist, prefs.sort, locale) {",
            "val namesById = remember(lib.members) {",
            "val savePlan = remember(tab, shown, kept, localFiles, unreadable, autoFiles) {",
        ).forEach { assertTrue("not remembered: $it", screen.hasCode(it)) }
    }
}
