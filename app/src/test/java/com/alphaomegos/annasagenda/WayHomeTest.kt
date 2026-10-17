package com.alphaomegos.annasagenda

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The way home (17.10, Eduard): from Travel, Flights, the profile and its
 * achievements the main menu was four Backs away. A house beside Back goes
 * there at once, on a screen two or more steps from the menu.
 */
class WayHomeTest {

    private fun source(path: String): String = File(mainSourceRoot(), "com/alphaomegos/annasagenda/$path").readText()

    @Test
    fun everyScreenOfTheGraphIsCountedForTheWayHome() {
        val nav = source("app/AppNav.kt")
        // The one composable(...) is the wrapper's own: a screen added past it would have no way home.
        assertEquals(1, Regex("""\bcomposable\(""").findAll(nav).count())
        assertTrue(nav.contains("LocalWayHome provides"))
        assertTrue(nav.contains("popBackStack(Route.MENU, inclusive = false)"))
    }

    @Test
    fun theScreensThatCanBeDeepShowTheHouseBesideBack() {
        listOf(
            "screens/CalendarDayScreen.kt",
            "screens/SomedayScreen.kt",
            "screens/DietScreen.kt",
            "screens/LanguageScreen.kt",
            "screens/media/MediaDetailsCommon.kt",
            "screens/metro/MetroStyle.kt",
            "screens/travel/TravelCountryScreen.kt",
            "screens/travel/FlightsScreen.kt",
            "screens/travel/FlightProfileScreen.kt",
            "screens/travel/FlightAchievementsScreen.kt",
        ).forEach { path ->
            assertTrue("$path has no way home", Regex("""WayHome(Foot)?Button\(\)""").containsMatchIn(source(path)))
        }
    }

    @Test
    fun aFormHasCancelAndNoHouse() {
        // Leaving a form loses what was typed; a reading session going is the same.
        listOf(
            "screens/NewTaskScreen.kt",
            "screens/travel/FlightEditScreen.kt",
            "screens/media/ReadingSessionScreen.kt",
        ).forEach { path ->
            assertTrue("$path offers the way home", !source(path).contains("WayHome"))
        }
    }
}
