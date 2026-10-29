package com.alphaomegos.annasagenda.app

import android.app.Application
import android.net.Uri
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

/**
 * The view model's state and the little it shares with the parts of it kept
 * in files of their own (28.10, Eduard: «разбить AppViewModel»). A part is an
 * interface whose methods have bodies; AppViewModel implements it, so every
 * call — vm.addFlight(…), vm::setMetroPrefs — reads as before.
 */
interface AppStateHost {
    val stateFlow: MutableStateFlow<AppState>
    fun nextFreshId(): Long
    // The next id to give out, raised when recurrences are drawn (29.10, TasksActions).
    var idCounter: Long
}

/** Metro, travel, flights, the food library and the diet: the view model's tail, moved here unchanged. */
interface PlacesAndFoodActions : AppStateHost {
    val metroHistoryFlow: MutableStateFlow<Map<Long, MetroHistory>>
    val stagedFlightImportFlow: MutableStateFlow<FlightImportPlan?>
    var stagedFlightTextSlot: String?
    val metroLibrary: Map<String, MetroScheme>
    val travelBase: List<TravelBaseCountry>
    val flightBase: FlightBase

    /* ---------------- metro (04.10) ---------------- */

    /**
     * One edit of the user's scheme [schemeId], as a rule from
     * support/MetroEditing.kt or MetroClosures.kt. An edit that gives back the
     * same scheme changes nothing and writes nothing.
     */
    fun editMetroScheme(schemeId: Long, edit: (MetroScheme) -> MetroScheme) {
        val cur = stateFlow.value
        val scheme = cur.metroSchemes.firstOrNull { it.id == schemeId } ?: return
        val edited = edit(scheme)
        if (edited === scheme || edited == scheme) return
        stateFlow.value = cur.copy(metroSchemes = metroSchemesAfterSaving(cur.metroSchemes, edited.copy(id = schemeId)))
        val history = metroHistoryFlow.value
        metroHistoryFlow.value = history + (schemeId to (history[schemeId] ?: MetroHistory()).recorded(scheme))
    }

    /**
     * The editor's view of [schemeId] changed (14.10) — its layers, its aids
     * to drawing (grid, angles of 45°, guides): saved like any edit, but no
     * step of undo — how the picture is looked at and drawn, not the picture.
     */
    fun setMetroView(schemeId: Long, edit: (MetroScheme) -> MetroScheme) {
        val cur = stateFlow.value
        val scheme = cur.metroSchemes.firstOrNull { it.id == schemeId } ?: return
        val edited = edit(scheme)
        if (edited == scheme) return
        stateFlow.value = cur.copy(metroSchemes = metroSchemesAfterSaving(cur.metroSchemes, edited.copy(id = schemeId)))
    }

    /** The scheme as it was before its last edit (08.10); nothing when there is nothing to undo. */
    fun undoMetroScheme(schemeId: Long) = stepMetroScheme(schemeId) { h, cur -> h.undone(cur) }

    /** The edit undone last, done again. */
    fun redoMetroScheme(schemeId: Long) = stepMetroScheme(schemeId) { h, cur -> h.redone(cur) }

    private fun stepMetroScheme(schemeId: Long, step: (MetroHistory, MetroScheme) -> Pair<MetroScheme, MetroHistory>?) {
        val cur = stateFlow.value
        val scheme = cur.metroSchemes.firstOrNull { it.id == schemeId } ?: return
        val history = metroHistoryFlow.value[schemeId] ?: return
        val (stepped, next) = step(history, scheme) ?: return
        // Undo takes the picture back, not how it is looked at (14.10): layers and aids stay.
        val shown = metroViewKept(stepped, scheme)
        stateFlow.value = cur.copy(metroSchemes = metroSchemesAfterSaving(cur.metroSchemes, shown.copy(id = schemeId)))
        metroHistoryFlow.value = metroHistoryFlow.value + (schemeId to next)
    }

    /** The music section's own settings (16.10). */
    fun setMusicPrefs(edit: (MusicPrefs) -> MusicPrefs) {
        val cur = stateFlow.value
        val prefs = edit(cur.musicPrefs)
        if (prefs != cur.musicPrefs) stateFlow.value = cur.copy(musicPrefs = prefs)
    }

    /** The metro section's own settings (15.10). */
    fun setMetroPrefs(edit: (MetroPrefs) -> MetroPrefs) {
        val cur = stateFlow.value
        val prefs = edit(cur.metroPrefs)
        if (prefs != cur.metroPrefs) stateFlow.value = cur.copy(metroPrefs = prefs)
    }

    /**
     * [from] → [to] looked up on the scheme kept under [schemeKey] (15.10):
     * written down, unless the user turned recent routes off.
     */
    fun rememberMetroRoute(schemeKey: String, from: Long, to: Long) {
        val cur = stateFlow.value
        if (!cur.metroPrefs.recentRoutes) return
        val next = metroRecentRemembered(cur.metroRecent, schemeKey, from, to, System.currentTimeMillis())
        if (next != cur.metroRecent) stateFlow.value = cur.copy(metroRecent = next)
    }

    /** A recent route pinned to the top, or let go of (15.10). */
    fun pinMetroRoute(schemeKey: String, from: Long, to: Long, pinned: Boolean) {
        val cur = stateFlow.value
        val next = metroRecentPinned(cur.metroRecent, schemeKey, from, to, pinned)
        if (next != cur.metroRecent) stateFlow.value = cur.copy(metroRecent = next)
    }

    /** The recent routes of a scheme cleared, the pinned ones kept (15.10). */
    fun clearMetroRecent(schemeKey: String) {
        val cur = stateFlow.value
        val next = metroRecentCleared(cur.metroRecent, schemeKey)
        if (next != cur.metroRecent) stateFlow.value = cur.copy(metroRecent = next)
    }

    /** A new, empty scheme for [city], chosen at once; its id, or null for a blank name. */
    fun createMetroScheme(city: String): Long? {
        val cur = stateFlow.value
        val id = metroNewSchemeId(cur.metroSchemes)
        val scheme = metroSchemeRenamed(MetroScheme(id = id, city = ""), city)
        if (scheme.city.isEmpty()) return null
        stateFlow.value = cur.copy(
            metroSchemes = cur.metroSchemes + scheme,
            metroSelection = metroSelectionText(MetroSelection.User(id)),
        )
        return id
    }

    fun removeMetroScheme(schemeId: Long) {
        val cur = stateFlow.value
        if (cur.metroSchemes.none { it.id == schemeId }) return
        stateFlow.value = cur.copy(
            metroSchemes = cur.metroSchemes.filterNot { it.id == schemeId },
            metroSelection = metroSelectionAfterRemoving(cur.metroSelection, schemeId),
            // Its routes go with it (15.10).
            metroRecent = metroRecentWithout(cur.metroRecent, metroSelectionText(MetroSelection.User(schemeId))),
        )
    }

    /**
     * The user's own copy of library scheme [key], chosen at once — the one
     * they already have, if they made one before, so a second tap on the
     * pencil does not make a second copy. Its id, or null when there is no
     * such library scheme.
     */
    fun copyMetroLibraryScheme(key: String): Long? {
        val cur = stateFlow.value
        val existing = cur.metroSchemes.firstOrNull { it.librarySource == key }
        val id = existing?.id ?: run {
            val library = metroLibrary[key] ?: return null
            val copy = metroSchemeCopied(library, metroNewSchemeId(cur.metroSchemes), key)
            stateFlow.value = cur.copy(metroSchemes = cur.metroSchemes + copy)
            copy.id
        }
        setMetroSelection(MetroSelection.User(id))
        return id
    }

    /** Which scheme the metro screen shows (Settings). */
    fun setMetroSelection(selection: MetroSelection) {
        val cur = stateFlow.value
        val text = metroSelectionText(selection)
        if (cur.metroSelection != text) stateFlow.value = cur.copy(metroSelection = text)
    }

    /* ---------------- travel (05.10) ---------------- */

    private fun setTravelCountries(next: List<TravelCountryRecord>) {
        val cur = stateFlow.value
        if (next !== cur.travelCountries && next != cur.travelCountries) stateFlow.value = cur.copy(travelCountries = next)
    }

    fun addTravelTrip(countryId: String, year: Int, month: Int, cities: List<String>) {
        // Read, take an id, write: never nextFreshId() inside an update loop.
        setTravelCountries(travelAfterAddingTrip(stateFlow.value.travelCountries, countryId, year, month, cities, ::nextFreshId))
    }

    fun editTravelTrip(tripId: Long, year: Int, month: Int, cities: List<String>) =
        setTravelCountries(travelAfterEditingTrip(stateFlow.value.travelCountries, tripId, year, month, cities))

    fun removeTravelTrip(tripId: Long) =
        setTravelCountries(travelAfterRemovingTrip(stateFlow.value.travelCountries, tripId))

    fun setTravelContinent(countryId: String, continent: TravelContinent?) = setTravelCountries(
        travelAfterSettingContinent(stateFlow.value.travelCountries, countryId, continent, travelBase.associateBy { it.code })
    )

    /** A country of the user's own; its id, or null for a blank name. */
    fun addTravelUserCountry(name: String, continent: TravelContinent, point: TravelMapPoint): String? {
        val before = stateFlow.value.travelCountries
        val after = travelAfterAddingUserCountry(before, name, continent, point, ::nextFreshId)
        if (after === before) return null
        setTravelCountries(after)
        return after.last().countryId
    }

    fun editTravelUserCountry(countryId: String, name: String, point: TravelMapPoint?) =
        setTravelCountries(travelAfterEditingUserCountry(stateFlow.value.travelCountries, countryId, name, point))

    fun removeTravelUserCountry(countryId: String) =
        setTravelCountries(travelAfterRemovingUserCountry(stateFlow.value.travelCountries, countryId))

    fun setTravelView(prefs: TravelViewPrefs) {
        val cur = stateFlow.value
        if (cur.travelView != prefs) stateFlow.value = cur.copy(travelView = prefs)
    }

    /* ---------------- flights (06.10) ---------------- */

    private fun setFlights(next: List<Flight>) {
        val cur = stateFlow.value
        if (next !== cur.flights && next != cur.flights) stateFlow.value = cur.copy(flights = next)
    }

    /** A new flight; its id. The id in [flight] is ignored. */
    fun addFlight(flight: Flight): Long {
        // Read, take an id, write: never nextFreshId() inside an update loop.
        val id = nextFreshId()
        val cur = stateFlow.value
        setFlights(flightAdded(cur.flights, flight.copy(id = id), flightBase, cur.flightPrefs.groupJourneys))
        return id
    }

    fun editFlight(flight: Flight) {
        val cur = stateFlow.value
        setFlights(flightEdited(cur.flights, flight, flightBase, cur.flightPrefs.groupJourneys))
    }

    fun removeFlight(id: Long) = setFlights(flightRemoved(stateFlow.value.flights, id, flightBase))

    /** [id] split off the flight before it, for good (06.10). */
    fun splitFlight(id: Long) = setFlights(flightSplitOff(stateFlow.value.flights, id, flightBase))

    /**
     * Grouping turned on or off (06.10); [earlierToo] is the answer to "and
     * the flights already in the log?" — regroup them all, or take every
     * journey apart.
     */
    fun setFlightGrouping(on: Boolean, earlierToo: Boolean) {
        val cur = stateFlow.value
        val flights = when {
            !earlierToo -> cur.flights
            on -> flightsRegrouped(cur.flights, flightBase)
            else -> flightsUngrouped(cur.flights)
        }
        stateFlow.value = cur.copy(flights = flights, flightPrefs = cur.flightPrefs.copy(groupJourneys = on))
    }

    fun setFlightPrefs(prefs: FlightPrefs) {
        val cur = stateFlow.value
        if (cur.flightPrefs != prefs) stateFlow.value = cur.copy(flightPrefs = prefs)
    }

    /** The user's own name for the city of airport [code]; blank brings back the base's. */
    fun renameFlightCity(code: String, name: String) {
        val cur = stateFlow.value
        val next = if (name.isBlank()) cur.flightCityNames - code else cur.flightCityNames + (code to name.trim())
        if (next != cur.flightCityNames) stateFlow.value = cur.copy(flightCityNames = next)
    }

    /** The user's own name for airline [code]; blank brings back the base's. */
    fun renameFlightAirline(code: String, name: String) {
        val cur = stateFlow.value
        val next = if (name.isBlank()) cur.flightAirlineNames - code else cur.flightAirlineNames + (code to name.trim())
        if (next != cur.flightAirlineNames) stateFlow.value = cur.copy(flightAirlineNames = next)
    }

    /**
     * A file read for import: what it would do is shown by [stagedFlightImport]
     * until [confirmFlightImport] or [cancelFlightImport]. False when the text
     * is not App in the Air's export.
     */
    fun stageFlightImport(text: String): Boolean {
        val parse = parseAitaExport(text)
        if (!parse.recognised) return false
        var fake = 0L
        stagedFlightTextSlot = text
        stagedFlightImportFlow.value = flightImportPlan(parse, stateFlow.value.flights, flightBase) { fake++ }
        return true
    }

    fun cancelFlightImport() {
        stagedFlightTextSlot = null
        stagedFlightImportFlow.value = null
    }

    /** The staged file imported; how many flights were added. */
    fun confirmFlightImport(): Int {
        val text = stagedFlightTextSlot
        cancelFlightImport()
        if (text == null) return 0
        val cur = stateFlow.value
        val plan = flightImportPlan(parseAitaExport(text), cur.flights, flightBase, ::nextFreshId)
        if (plan.flights.isEmpty()) return 0
        setFlights(flightsAdded(cur.flights, plan.flights, flightBase, cur.flightPrefs.groupJourneys))
        return plan.flights.size
    }

    /** 6.4: whether the add-a-meal dialog offers the food library. */
    fun setFoodLibraryVisible(visible: Boolean) {
        val cur = stateFlow.value
        if (cur.foodLibraryVisible != visible) stateFlow.value = cur.copy(foodLibraryVisible = visible)
    }

    /** A food of the user's own on a library shelf; the portion is read out of the name. */
    fun addFoodLibraryUserItem(category: FoodCategory, typed: String, kcal: Int) {
        val cur = stateFlow.value
        // Parsed first, so a name that reads as nothing does not use up an id.
        val parsed = foodLibraryUserItemFrom(0L, category, typed, kcal) ?: return
        val item = parsed.copy(id = nextFreshId())
        stateFlow.value = cur.copy(foodLibraryUserItems = cur.foodLibraryUserItems + item)
    }

    fun removeFoodLibraryUserItem(id: Long) {
        val cur = stateFlow.value
        val next = cur.foodLibraryUserItems.filterNot { it.id == id }
        if (next.size != cur.foodLibraryUserItems.size) stateFlow.value = cur.copy(foodLibraryUserItems = next)
    }

    /** 6.2: the diet on or off, and whether past days keep showing unticked dishes. */
    fun setDietSettings(enabled: Boolean, showPastUnticked: Boolean) {
        val cur = stateFlow.value
        val next = cur.copy(dietEnabled = enabled, dietShowPastUnticked = showPastUnticked)
        if (next != cur) stateFlow.value = next
    }

    /** A dish of [date]'s plan eaten: an ordinary food entry carrying its id. */
    fun tickDietDish(itemId: Long, date: LocalDate) {
        val cur = stateFlow.value
        val item = cur.dietPlan[date.dayOfWeek]?.firstOrNull { it.id == itemId } ?: return
        val log = foodLogAfterTickingDish(cur.foodLog, item, date, LocalDate.now(), ::nextFreshId)
        if (log !== cur.foodLog) stateFlow.value = cur.copy(foodLog = log)
    }

    fun untickDietDish(itemId: Long, date: LocalDate) {
        val cur = stateFlow.value
        val log = foodLogAfterUntickingDish(cur.foodLog, itemId, date)
        if (log !== cur.foodLog) stateFlow.value = cur.copy(foodLog = log)
    }

    fun addDietDish(day: java.time.DayOfWeek, title: String, kcal: Int) {
        val cur = stateFlow.value
        val plan = dietPlanAfterAdding(cur.dietPlan, day, title, kcal, ::nextFreshId)
        if (plan !== cur.dietPlan) stateFlow.value = cur.copy(dietPlan = plan)
    }

    fun editDietDish(itemId: Long, title: String, kcal: Int) {
        val cur = stateFlow.value
        val plan = dietPlanAfterEditing(cur.dietPlan, itemId, title, kcal)
        if (plan !== cur.dietPlan) stateFlow.value = cur.copy(dietPlan = plan)
    }

    fun removeDietDish(itemId: Long) {
        val cur = stateFlow.value
        val plan = dietPlanAfterRemoving(cur.dietPlan, itemId)
        if (plan !== cur.dietPlan) stateFlow.value = cur.copy(dietPlan = plan)
    }

    fun moveDietDish(itemId: Long, step: Int) {
        val cur = stateFlow.value
        val plan = dietPlanAfterMoving(cur.dietPlan, itemId, step)
        if (plan !== cur.dietPlan) stateFlow.value = cur.copy(dietPlan = plan)
    }

    fun copyDietDay(from: java.time.DayOfWeek, to: Set<java.time.DayOfWeek>) {
        val cur = stateFlow.value
        val plan = dietPlanAfterCopyingDay(cur.dietPlan, from, to, ::nextFreshId)
        if (plan !== cur.dietPlan) stateFlow.value = cur.copy(dietPlan = plan)
    }

    fun setDailyCalorieGoalFrom(date: LocalDate, kcal: Int) {
        val clean = kcal.coerceAtLeast(1)
        val cur = stateFlow.value
        val filtered = cur.calorieGoalChanges.filterNot { it.date == date }
        val newList = (filtered + CalorieGoalChange(date = date, kcal = clean)).sortedBy { it.date }
        stateFlow.value = cur.copy(calorieGoalChanges = newList)
    }

    fun addFoodEntry(date: LocalDate, title: String, kcal: Int): Long {
        val t = title.trim()
        if (t.isBlank()) return -1L
        val k = kcal.coerceAtLeast(1)

        val id = nextFreshId()
        val entry = FoodEntry(
            id = id,
            date = date,
            title = t,
            kcal = k
        )
        val cur = stateFlow.value
        stateFlow.value = cur.copy(foodLog = cur.foodLog + entry)
        return id
    }

    fun deleteFoodEntry(entryId: Long) {
        val cur = stateFlow.value
        stateFlow.value = cur.copy(foodLog = cur.foodLog.filterNot { it.id == entryId })
    }
}
