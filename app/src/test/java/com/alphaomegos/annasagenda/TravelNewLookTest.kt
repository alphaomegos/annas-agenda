package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Travel and flights in the new look (0393, Eduard 05.11), read from the
 * sources: the shared frame is one row — Back, the title, the screen's keys,
 * home — and every one of the six screens uses it in the new look, its
 * content shared with the summer look's app bar.
 */
class TravelNewLookTest {
    private val root = File(mainSourceRoot(), "com/alphaomegos/annasagenda")

    @Test
    fun theFrameIsOneRow() {
        val frame = File(root, "components/NewLookScreen.kt").readText()
        assertTrue(frame.hasCode("TopBarBack(onBack)") && frame.hasCode("if (home) WayHomeButton()"))
        assertTrue(frame.hasCode("FittingText(") && frame.hasCode(".statusBarsPadding()") && frame.hasCode(".navigationBarsPadding()"))
    }

    @Test
    fun everyTravelScreenHasIt() {
        listOf("TravelScreen.kt", "TravelCountryScreen.kt", "FlightsScreen.kt", "FlightEditScreen.kt", "FlightProfileScreen.kt", "FlightAchievementsScreen.kt").forEach { name ->
            val screen = File(root, "screens/travel/$name").readText()
            assertTrue("$name has no new look", screen.hasCode("if (state.dayScreenStyle == DayScreenStyle.NEW) {") && screen.hasCode("NewLookScreen("))
            assertTrue("$name does not share its content", screen.hasCode("body(PaddingValues(0.dp))") && screen.hasCode("body(inner)"))
            assertTrue("$name lost the summer look's bar", screen.hasCode("CenterAlignedTopAppBar("))
            // 0403: content moved out of the Scaffold cannot return from it any more.
            assertTrue("$name returns from a Scaffold it is no longer in", !screen.contains("return@Scaffold"))
        }
    }
}
