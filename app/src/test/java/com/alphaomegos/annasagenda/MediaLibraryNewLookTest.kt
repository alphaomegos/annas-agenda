package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media library in the new look (0391, Eduard 05.11), read from the
 * sources: one row on top with «+ Add» and home, the bar's controls under it,
 * the cards flat; the summer look keeps its bar.
 */
class MediaLibraryNewLookTest {
    private val media = File(mainSourceRoot(), "com/alphaomegos/annasagenda/screens/media")

    @Test
    fun theLibraryHasTheNewLook() {
        val screen = File(media, "MediaLibraryScreen.kt").readText()
        assertTrue(screen.hasCode("if (st.dayScreenStyle == DayScreenStyle.NEW) {"))
        assertTrue(screen.hasCode("TopBarBack(onBack = onBack)") && screen.hasCode("WayHomeButton()"))
        assertTrue(screen.hasCode("Text(stringResource(R.string.reading_add_item_title))"))
        assertTrue(screen.hasCode("CompositionLocalProvider(LocalMediaFlat provides true) {"))
        assertTrue("the summer look keeps its bar", screen.hasCode("MediaLibraryTopBar("))
    }

    @Test
    fun itsCardsAreFlat() {
        val list = File(media, "MediaLibraryList.kt").readText()
        assertTrue(Regex("""val flat = LocalMediaFlat\.current""").findAll(list).count() == 2)
        assertTrue(list.hasCode("if (flat) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface"))
    }
}
