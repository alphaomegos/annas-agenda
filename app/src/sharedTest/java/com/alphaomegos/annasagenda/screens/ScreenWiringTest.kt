package com.alphaomegos.annasagenda.screens

import androidx.compose.ui.test.onLast
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithContentDescription
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.awaitWorkingStore
import com.alphaomegos.annasagenda.clearAppStateStoreFile
import com.alphaomegos.annasagenda.TestViewModels
import com.alphaomegos.annasagenda.model.TravelView
import com.alphaomegos.annasagenda.screens.metro.MetroLineEditorScreen
import com.alphaomegos.annasagenda.screens.metro.MetroMapEditorScreen
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.screens.metro.MetroSchemeEditorScreen
import com.alphaomegos.annasagenda.screens.metro.MetroScreen
import com.alphaomegos.annasagenda.screens.metro.MetroStationEditorScreen
import com.alphaomegos.annasagenda.screens.travel.TravelCountryScreen
import com.alphaomegos.annasagenda.screens.travel.FlightEditScreen
import com.alphaomegos.annasagenda.screens.travel.FlightProfileScreen
import com.alphaomegos.annasagenda.screens.travel.FlightsScreen
import com.alphaomegos.annasagenda.screens.travel.TravelScreen
import com.alphaomegos.annasagenda.model.Flight
import java.time.LocalDateTime
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
    private val viewModels = TestViewModels()
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        // As in the view-model tests: viewModelScope must not need the thread
        // the test is holding.
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        clearAppStateStoreFile(app)
        vm = viewModels.appViewModel(app)
        runBlocking { awaitWorkingStore(vm) }
        vm.resetAllData()
    }

    @After
    fun tearDown() {
        // Before resetMain: see TestViewModels.
        viewModels.clear()
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

        // The grid is an aid by the tools since 14.10, and no step of undo.
        val steps = vm.metroHistory.value[id]?.undo?.size ?: 0
        composeRule.onNodeWithText(word(R.string.metro_map_grid)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(scheme(id).mapGrid)
        assertEquals(steps, vm.metroHistory.value[id]?.undo?.size ?: 0)
        composeRule.onNodeWithText(word(R.string.metro_map_grid)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(false, scheme(id).mapGrid)

        // The other ticks live in the Look panel since 08.10.
        composeRule.onNodeWithContentDescription(word(R.string.metro_look)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_transfers_together)).performClick()
        composeRule.waitForIdle()
        assertTrue(scheme(id).transfersTogether)
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

    /* ---------------- flights (06.10) ---------------- */

    private fun twoConnectingFlights(): Pair<Long, Long> {
        val a = vm.addFlight(Flight(0, "TLV", "ATH", LocalDateTime.parse("2024-07-11T04:00"), LocalDateTime.parse("2024-07-11T06:00")))
        val b = vm.addFlight(Flight(0, "ATH", "BEG", LocalDateTime.parse("2024-07-11T08:00"), LocalDateTime.parse("2024-07-11T09:00")))
        return a to b
    }

    @Test
    fun aFlightsNumber_isSavedFromItsScreen() {
        val (a, _) = twoConnectingFlights()
        composeRule.setContent {
            MaterialTheme { FlightEditScreen(vm, a, onBack = {}) }
        }

        field(word(R.string.flights_number)).performTextInput("931")
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        assertEquals("931", vm.state.value.flights.single { it.id == a }.number)
    }

    @Test
    fun groupingTurnedOff_asksAboutTheEarlierFlights_andTakesThemApart() {
        twoConnectingFlights()
        assertTrue("the two flights were not grouped", vm.state.value.flights.all { it.journeyId != null })
        composeRule.setContent {
            MaterialTheme { FlightsScreen(vm, onBack = {}, onAdd = {}, onEdit = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.flights_more)).performClick()
        composeRule.onNodeWithText(word(R.string.flights_group)).performClick()
        composeRule.onNodeWithText(word(R.string.flights_all_of_them)).performClick()
        composeRule.waitForIdle()

        assertEquals(false, vm.state.value.flightPrefs.groupJourneys)
        assertTrue(vm.state.value.flights.all { it.journeyId == null })
    }

    @Test
    fun theProfile_countsTheAchievements_andOpensThem() {
        twoConnectingFlights()
        var opened = false
        composeRule.setContent {
            MaterialTheme { FlightProfileScreen(vm, onBack = {}, onAchievements = { opened = true }) }
        }

        // Two flights earn the first-flight milestone, one of nineteen.
        val label = word(R.string.flights_achievements_count, 1, 19)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        // Below the tiles: off the test's small screen until scrolled to.
        composeRule.onNodeWithText(label).performScrollTo().performClick()

        assertTrue("the achievements did not open", opened)
    }

    /* ---------------- steps (19.10) ---------------- */

    @Test
    fun aDayOfSteps_isTypedInFromTheStepsTab() {
        val today = java.time.LocalDate.now()
        composeRule.setContent {
            MaterialTheme { StepsContent(vm = vm, state = vm.state.value, today = today) }
        }
        val locale = composeRule.activity.resources.configuration.locales[0]
        val todayText = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(today)

        // Today is the first row of days; with no steps yet it reads «—», and any day can be
        // written in. Under the import (0299) it is past the test's small screen: scrolled to —
        // in the list, the one scroller that goes by index; the range chips scroll sideways too.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(todayText))
        composeRule.onNodeWithText(todayText).performClick()
        field(word(R.string.steps_edit_label)).performTextInput("12345")
        composeRule.onNodeWithText(word(R.string.save)).performClick()
        composeRule.waitForIdle()

        val day = vm.state.value.stepDays.single()
        assertEquals(today, day.date)
        assertEquals(12_345, day.steps)
        assertEquals(com.alphaomegos.annasagenda.model.StepSource.MANUAL, day.source)
    }

    /* ---------------- tasks (06.10) ---------------- */

    @Test
    fun doneTasksToTheBottom_isTurnedOnAndOffInSettings() {
        composeRule.setContent {
            MaterialTheme { SettingsScreen(vm, onBack = {}, onLanguage = {}, onOpenDiet = {}) }
        }

        // In «Tasks», below «Appearance» (0292): off the test's small screen
        // until the list is scrolled to it.
        val row = word(R.string.settings_done_tasks_last)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(row))
        composeRule.onNodeWithText(row).performClick()
        composeRule.waitForIdle()
        assertTrue("not turned on", vm.state.value.doneTasksLast)

        composeRule.onNodeWithText(row).performClick()
        composeRule.waitForIdle()
        assertEquals(false, vm.state.value.doneTasksLast)
    }

    @Test
    fun theMapEditorsUndoAndRedo_stepThroughTheEdits() {
        val id = schemeWith("Alpha", "Beta")
        // An edit of the picture (14.10: the grid is no step of undo any more).
        vm.editMetroScheme(id) { it.copy(lineCasing = true) }
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_undo)).performClick()
        composeRule.waitForIdle()
        assertEquals(false, scheme(id).lineCasing)

        composeRule.onNodeWithContentDescription(word(R.string.metro_redo)).performClick()
        composeRule.waitForIdle()
        assertTrue("redo did not bring the edging back", scheme(id).lineCasing)
    }

    @Test
    fun theMapEditorsTools_selectAndFit_doNotChangeTheScheme() {
        val id = schemeWith("Alpha", "Beta")
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }
        val before = scheme(id)

        composeRule.onNodeWithText(word(R.string.metro_tool_select)).performScrollTo().performClick()
        composeRule.onNodeWithText(word(R.string.metro_select_lines)).assertExists()
        composeRule.onNodeWithContentDescription(word(R.string.metro_fit)).performClick()
        composeRule.waitForIdle()
        assertEquals(before, scheme(id))
    }

    @Test
    fun theMapEditorsLayers_aTapHidesALine_andTheRestOfThePictureStays() {
        val id = schemeWith("Alpha", "Beta")
        val line = scheme(id).lines.single().id
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }

        val steps = vm.metroHistory.value[id]?.undo?.size ?: 0
        composeRule.onNodeWithText("1").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(line), scheme(id).hiddenLines)
        // Not a step of undo: what is looked at, not the picture.
        assertEquals(steps, vm.metroHistory.value[id]?.undo?.size ?: 0)

        composeRule.onNodeWithText("1").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<Long>(), scheme(id).hiddenLines)

        composeRule.onNodeWithContentDescription(word(R.string.metro_layers)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_layers_ghost)).assertExists()
    }

    @Test
    fun theMapEditorsSettings_turnTheLoupeOff() {
        val id = schemeWith("Alpha", "Beta")
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_more)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_editor_prefs)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_pref_loupe)).performClick()
        composeRule.waitForIdle()
        assertEquals(false, vm.state.value.metroPrefs.loupe)
        assertTrue(vm.state.value.metroPrefs.haptics)
    }

    @Test
    fun theMetroSectionsStyle_isChosenFromTheEditorsMenu() {
        val id = schemeWith("Alpha", "Beta")
        composeRule.setContent {
            MaterialTheme { MetroMapEditorScreen(vm, id, onBack = {}) }
        }

        composeRule.onNodeWithContentDescription(word(R.string.metro_more)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_style)).performClick()
        composeRule.onNodeWithText(word(R.string.metro_style_win95)).performClick()
        composeRule.waitForIdle()
        assertEquals(MetroStyle.WIN95, vm.state.value.metroPrefs.style)
    }

    // ---- the new look (0343): what the source guards could not check — taps and what they do ----

    /** «Someday» in the new look: its row, «+ Task», and the row's ⋮ deleting after asking. */
    @Test
    fun someday_newLook_plusTask_andTheMenuDeletesAfterAsking() {
        val id = vm.createTaskForDate(date = null, time = null, description = "Купить лампу")
        var created = false
        composeRule.setContent { SomedayScreen(vm = vm, onBack = {}, onCreateTask = { created = true }) }
        composeRule.onNodeWithText("Купить лампу").assertExists()
        composeRule.onNodeWithText(word(R.string.day_new_task)).performClick()
        assertTrue(created)
        composeRule.onNodeWithContentDescription(word(R.string.task_more_actions)).performClick()
        composeRule.onNodeWithText(word(R.string.copy_task)).assertExists()
        composeRule.onNodeWithText(word(R.string.remove)).performClick()
        // Asked first; the task is still there.
        assertTrue(vm.state.value.tasks.any { it.id == id })
        composeRule.onAllNodesWithText(word(R.string.remove)).onLast().performClick()
        composeRule.waitForIdle()
        assertTrue(vm.state.value.tasks.none { it.id == id })
    }

    /** «Ask before deleting tasks» turned off (0359): the row's ⋮ deletes at the tap. */
    @Test
    fun someday_notAsking_theMenuDeletesAtOnce() {
        vm.setAskBeforeDeleting(false)
        val id = vm.createTaskForDate(date = null, time = null, description = "Купить лампу")
        composeRule.setContent { SomedayScreen(vm = vm, onBack = {}, onCreateTask = {}) }
        composeRule.onNodeWithContentDescription(word(R.string.task_more_actions)).performClick()
        composeRule.onNodeWithText(word(R.string.remove)).performClick()
        composeRule.waitForIdle()
        assertTrue(vm.state.value.tasks.none { it.id == id })
        composeRule.onAllNodesWithText(word(R.string.delete_task_title)).assertCountEquals(0)
    }

    /** «Summer 2026» keeps the old Someday: the wide create button, no ⋮. */
    @Test
    fun someday_summer2026_keepsTheOldScreen() {
        vm.setDayScreenStyle(com.alphaomegos.annasagenda.model.DayScreenStyle.SUMMER_2025)
        vm.createTaskForDate(date = null, time = null, description = "Купить лампу")
        composeRule.setContent { SomedayScreen(vm = vm, onBack = {}, onCreateTask = {}) }
        composeRule.onNodeWithText(word(R.string.create_task)).assertExists()
        composeRule.onAllNodesWithContentDescription(word(R.string.task_more_actions)).assertCountEquals(0)
    }

    /**
     * The month in the new look (0358): one title row — ‹ › page the months, a tap on the
     * month brings this month back, the Someday circle opens Someday; no «Someday: N» line.
     */
    @Test
    fun month_newLook_arrowsTheTitleAndTheSomedayCircle() {
        vm.createTaskForDate(date = null, time = null, description = "Купить лампу")
        var someday = false
        composeRule.setContent { CalendarMonthRoute(vm = vm, onBack = {}, onOpenDay = {}, onOpenSomeday = { someday = true }) }
        val backToThisMonth = SemanticsMatcher("goes back to this month") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == word(R.string.calendar_to_this_month)
        }
        fun title() = composeRule.onNode(backToThisMonth).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        val thisMonth = title()
        composeRule.onNodeWithContentDescription(word(R.string.next_month)).performClick()
        composeRule.waitForIdle()
        val next = title()
        assertTrue("the month did not change: $next", next != thisMonth)
        composeRule.onNode(backToThisMonth).performClick()
        composeRule.waitForIdle()
        assertEquals(thisMonth, title())
        composeRule.onAllNodesWithText(word(R.string.someday_count, 1)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(word(R.string.someday_count, 1)).performClick()
        assertTrue(someday)
    }

    /** Anthropometry in the new look: «+» in the bar opens the measurement's input. */
    @Test
    fun anthropometry_newLook_plusOpensTheInput() {
        composeRule.setContent { AnthropometryScreen(vm = vm, onBack = {}) }
        composeRule.onNodeWithContentDescription(word(R.string.enter_data)).performClick()
        composeRule.waitForIdle()
        // The input dialog has a field to type a value into.
        assertTrue(composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    }
}
