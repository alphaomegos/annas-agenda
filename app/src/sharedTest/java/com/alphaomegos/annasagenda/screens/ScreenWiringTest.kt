package com.alphaomegos.annasagenda.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.awaitWorkingStore
import com.alphaomegos.annasagenda.clearAppStateStoreFile
import com.alphaomegos.annasagenda.model.TravelView
import com.alphaomegos.annasagenda.screens.metro.MetroLineEditorScreen
import com.alphaomegos.annasagenda.screens.metro.MetroMapEditorScreen
import com.alphaomegos.annasagenda.screens.metro.MetroSchemeEditorScreen
import com.alphaomegos.annasagenda.screens.metro.MetroScreen
import com.alphaomegos.annasagenda.screens.metro.MetroStationEditorScreen
import com.alphaomegos.annasagenda.screens.travel.TravelCountryScreen
import com.alphaomegos.annasagenda.screens.travel.TravelScreen
import com.alphaomegos.annasagenda.screens.travel.travelMonthName
import com.alphaomegos.annasagenda.support.metroLineAdded
import com.alphaomegos.annasagenda.support.metroStationAdded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screens wired to the real view model (05.10): a button does what it says,
 * and a dialog it opens closes again.
 *
 * Written after 0168, where the "+" of a heading was handed to the heading's
 * content slot instead of its click: the dialog it opened reopened on every
 * recomposition and could not be closed, and no test noticed because none
 * pressed a button on a real screen. These press the buttons that change
 * data on the metro and travel screens and in Settings, then look at both
 * the screen and the state.
 *
 * Words are looked up through the activity, so the run on a device in
 * another language finds the same buttons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenWiringTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        // As in the view-model tests: viewModelScope must not need the thread
        // the test is holding.
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        clearAppStateStoreFile(app)
        vm = AppViewModel(app)
        runBlocking { awaitWorkingStore(vm) }
        vm.resetAllData()
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile(app)
        Dispatchers.resetMain()
    }

    private fun word(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun field(label: String) = composeRule.onNode(hasSetTextAction() and hasText(label))

    /** A scheme with one line and the stations named, in order. */
    private fun schemeWith(vararg stations: String): Long {
        val id = vm.createMetroScheme("Testgrad")!!
        vm.editMetroScheme(id) { s ->
            var x = metroLineAdded(s, "1", "Red", 0xFFE42313)
            val line = x.lines.single().id
            stations.forEach { x = metroStationAdded(x, line, null, Int.MAX_VALUE, it) }
            x
        }
        return id
    }

    private fun scheme(id: Long) = vm.state.value.metroSchemes.single { it.id == id }

    /* ---------------- metro ---------------- */

    @Test
    fun theLinesPlus_opensItsDialog_andSavingClosesIt() {
        val id = vm.createMetroScheme("Testgrad")!!
        composeRule.setContent {
            MaterialTheme { MetroSchemeEditorScreen(vm, id, onBack = {}, onOpenLine = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_add_line)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_add_line)).assertExists() // the dialog's title
        field(word(R.string.metro_line_label)).performTextInput("7")
        field(word(R.string.metro_line_name)).performTextInput("Tagansko")
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(word(R.string.metro_add_line)).assertDoesNotExist()
        assertEquals("7", scheme(id).lines.single().label)
        composeRule.onNodeWithText("Tagansko").assertExists()
    }

    @Test
    fun theLinesPlus_cancelClosesTheDialog_andAddsNothing() {
        val id = vm.createMetroScheme("Testgrad")!!
        composeRule.setContent {
            MaterialTheme { MetroSchemeEditorScreen(vm, id, onBack = {}, onOpenLine = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_add_line)).performClick()
        composeRule.onNodeWithText(word(R.string.cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(word(R.string.metro_add_line)).assertDoesNotExist()
        assertTrue(scheme(id).lines.isEmpty())
    }

    @Test
    fun aStationAddedAtTheStart_goesFirst() {
        val id = schemeWith("Beta")
        val line = scheme(id).lines.single().id
        composeRule.setContent {
            MaterialTheme { MetroLineEditorScreen(vm, id, line, onBack = {}, onOpenStation = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_add_station)).performClick()
        field(word(R.string.metro_station_name)).performTextInput("Alpha")
        composeRule.onNodeWithText(word(R.string.metro_add_at_start)).performClick()
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        val s = scheme(id)
        assertEquals(listOf("Alpha", "Beta"), s.lines.single().trunk.map { t -> s.stations.single { it.id == t }.name })
    }

    @Test
    fun anExitAdded_onTheStationsScreen() {
        val id = schemeWith("Alpha", "Beta")
        val station = scheme(id).stations.first().id
        composeRule.setContent {
            MaterialTheme { MetroStationEditorScreen(vm, id, station, onBack = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_add_exit)).performClick()
        field(word(R.string.metro_exit_name)).performTextInput("Park")
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Park"), scheme(id).exits.map { it.name })
        composeRule.onNodeWithText(word(R.string.metro_add_exit)).assertDoesNotExist()
        composeRule.onNodeWithText("Park").assertExists()
    }

    @Test
    fun fromAndTo_giveTheRoute() {
        schemeWith("Alpha", "Beta", "Gamma")
        composeRule.setContent {
            MaterialTheme { MetroScreen(vm, onBack = {}) }
        }

        field(word(R.string.metro_from)).performTextInput("Alpha")
        field(word(R.string.metro_to)).performTextInput("Gamma")
        composeRule.waitForIdle()

        val ride = composeRule.activity.resources.getQuantityString(R.plurals.metro_ride_stations, 2, 2, "Gamma")
        composeRule.onNodeWithText(ride, substring = true).assertExists()
    }

    @Test
    fun theMapEditorsTicks_turnOnTheGridAndTransfersTogether() {
        val id = schemeWith("Alpha", "Beta")
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }

        composeRule.onNodeWithText(word(R.string.metro_map_grid)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_transfers_together)).performClick()
        composeRule.waitForIdle()
        assertTrue(scheme(id).mapGrid)
        assertTrue(scheme(id).transfersTogether)

        composeRule.onNodeWithText(word(R.string.metro_map_grid)).performClick()
        composeRule.waitForIdle()
        assertEquals(false, scheme(id).mapGrid)
    }

    /* ---------------- travel ---------------- */

    @Test
    fun aTripAdded_onTheCountrysCard() {
        composeRule.setContent {
            MaterialTheme { TravelCountryScreen(vm, "BY", onBack = {}) }
        }
        val locale = composeRule.activity.resources.configuration.locales[0]

        composeRule.onNodeWithText(word(R.string.travel_add_trip)).performClick()
        composeRule.onAllNodesWithText(word(R.string.travel_add_trip)).assertCountEquals(2) // the button and the title
        field(word(R.string.travel_year)).performTextClearance()
        field(word(R.string.travel_year)).performTextInput("2018")
        composeRule.onNodeWithText(travelMonthName(5, locale).take(3)).performClick()
        field(word(R.string.travel_city)).performTextInput("Minsk") // not "+"ed: saving takes it too
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        val trip = vm.state.value.travelCountries.single { it.countryId == "BY" }.trips.single()
        assertEquals(listOf(2018, 5), listOf(trip.year, trip.month))
        assertEquals(listOf("Minsk"), trip.cities)
        composeRule.onAllNodesWithText(word(R.string.travel_add_trip)).assertCountEquals(1)
        composeRule.onNodeWithText(travelMonthName(5, locale) + " 2018: Minsk").assertExists()
    }

    @Test
    fun theViewMenu_andTheSearchButton() {
        composeRule.setContent {
            MaterialTheme { TravelScreen(vm, onBack = {}, onOpenCountry = {}) }
        }

        composeRule.onNodeWithText(word(R.string.travel_view_years)).performClick()
        composeRule.onNodeWithText(word(R.string.travel_view_countries)).performClick()
        composeRule.waitForIdle()
        assertEquals(TravelView.COUNTRIES, vm.state.value.travelView.view)
        // "All / mine" is offered in this view only.
        composeRule.onNodeWithContentDescription(word(R.string.travel_all_countries)).assertExists()

        composeRule.onNodeWithContentDescription(word(R.string.travel_search)).performClick()
        field(word(R.string.travel_search_label)).assertExists()
    }

    /* ---------------- settings ---------------- */

    @Test
    fun anotherCityStarted_fromSettings() {
        composeRule.setContent {
            MaterialTheme { SettingsScreen(vm, onBack = {}, onLanguage = {}, onOpenDiet = {}) }
        }

        // The metro row sits low in the list: a lazy list does not compose
        // what is off the screen, so the test scrolls to it first.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(word(R.string.metro_settings_scheme)))
        composeRule.onNodeWithText(word(R.string.metro_settings_scheme)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_create_scheme)).performClick()
        field(word(R.string.metro_city)).performTextInput("Beograd")
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        val schemes = vm.state.value.metroSchemes
        assertEquals(listOf("Beograd"), schemes.map { it.city })
        assertEquals("user:${schemes.single().id}", vm.state.value.metroSelection)
    }
}
