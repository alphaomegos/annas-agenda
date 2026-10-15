package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Picking a station by typing its name (04.10). */
class MetroSearchTest {

    private val scheme = MetroScheme(
        id = 1,
        city = "Москва",
        lines = listOf(
            MetroLine(id = 2, label = "2", name = "Замоскворецкая", color = 0, trunk = listOf(1, 2)),
            MetroLine(id = 11, label = "11", name = "БКЛ", color = 0, trunk = listOf(3, 4)),
        ),
        stations = listOf(
            MetroStation(1, 2, "Каховская"),
            MetroStation(2, 2, "Новокузнецкая"),
            MetroStation(3, 11, "Каховская", closure = MetroClosure()),
            MetroStation(4, 11, "Улица Старокачаловская", closure = MetroClosure()),
        ),
    )

    private fun names(typed: String) = metroStationSuggestions(scheme, typed).map { it.name }

    @Test
    fun startsWith_thenAWordStartsWith_thenContains() {
        assertEquals(listOf("Каховская", "Каховская", "Новокузнецкая", "Улица Старокачаловская"), names("ка"))
        assertEquals(listOf("Новокузнецкая"), names("куз"))
        assertEquals(listOf("Улица Старокачаловская"), names("стар"))
    }

    @Test
    fun aNameOnTwoLines_isAChoicePerStation_inTheSchemesOrderOfLines() {
        val choices = metroStationSuggestions(scheme, "Кахов")
        assertEquals(listOf(1L, 3L), choices.map { it.stationId })
        assertEquals(listOf(listOf(2L), listOf(11L)), choices.map { it.lineIds })
        assertEquals(listOf(false, true), choices.map { it.allClosed })
    }

    @Test
    fun aClosedStation_isMarked() {
        assertTrue(metroStationSuggestions(scheme, "улица").single().allClosed)
    }

    @Test
    fun theFullNameOfSeveralStations_stillOffersThem_soOneCanBePicked() {
        assertEquals(listOf(1L, 3L), metroStationSuggestions(scheme, "каховская").map { it.stationId })
        assertTrue(names("новокузнецкая").isEmpty())
    }

    @Test
    fun aPickedStation_standsForTheField_whileTheTextNamesIt() {
        val open = scheme.copy(stations = scheme.stations.map { if (it.id == 3L) it.copy(closure = null) else it })
        assertEquals(setOf(3L), metroStationsForField(open, "Каховская", 3))
        assertEquals(setOf(1L, 3L), metroStationsForField(open, "Каховская", null))
        // The text changed: the pick no longer counts.
        assertEquals(setOf(2L), metroStationsForField(open, "Новокузнецкая", 3))
        // Picked but closed, with another of the name open: the open one is used.
        assertEquals(setOf(1L, 3L), metroStationsForField(scheme, "Каховская", 3))
        // Picked and closed, and the only one: kept, so the screen can say so.
        assertEquals(setOf(4L), metroStationsForField(scheme, "Улица Старокачаловская", 4))
    }

    @Test
    fun caseYoAndSpaces_doNotMatter() {
        val yo = scheme.copy(stations = scheme.stations + MetroStation(9, 2, "Тёплый   Стан"))
        assertEquals(listOf("Тёплый   Стан"), metroStationSuggestions(yo, "ТЕПЛЫЙ С").map { it.name })
        assertEquals(setOf(9L), metroStationsForName(yo, " тёплый стан "))
    }

    @Test
    fun nothingTyped_orTheNameComplete_offersNothing() {
        assertTrue(names("  ").isEmpty())
        assertTrue(names("новокузнецкая").isEmpty())
        assertEquals(setOf(1L, 3L), metroStationsForName(scheme, "каховская"))
    }

    @Test
    fun theListIsCut() {
        assertEquals(1, metroStationSuggestions(scheme, "а", limit = 1).size)
    }

    @Test
    fun pickingTheStationOfOneLine_countsTheWalkToIt() {
        // 06.10: Кучино → Печатники (10). "Печатники" alone ends on line 11,
        // where the train arrives; the pick of line 10 adds the walk there.
        val net = MetroScheme(
            id = 1, city = "Москва",
            lines = listOf(
                MetroLine(id = 11, label = "11", name = "", color = 0, trunk = listOf(1, 2)),
                MetroLine(id = 10, label = "10", name = "", color = 0, trunk = listOf(3, 4)),
            ),
            stations = listOf(
                MetroStation(1, 11, "Кучино"),
                MetroStation(2, 11, "Печатники"),
                MetroStation(3, 10, "Печатники"),
                MetroStation(4, 10, "Кожуховская"),
            ),
            transfers = listOf(MetroTransfer(1, 2, 3)),
        )
        val from = metroStationsForField(net, "Кучино", null)
        assertEquals(3, metroRoute(net, from, metroStationsForField(net, "Печатники", null))!!.minutes)
        val exact = metroRoute(net, from, metroStationsForField(net, "Печатники", 3))!!
        assertEquals(6, exact.minutes)
        assertEquals(1, exact.transfers)
    }

    /** Finding a station on the editor's picture (15.10): every station on its own, an exact name too. */
    @Test
    fun findingListsEachStationAndAnExactNameAsWell() {
        assertEquals(listOf(1L, 3L), metroStationsMatching(scheme, "Каховская").map { it.id })
        assertEquals(listOf(1L, 3L, 2L, 4L), metroStationsMatching(scheme, "ка").map { it.id })
        assertEquals(listOf(4L), metroStationsMatching(scheme, "стар").map { it.id })
        assertTrue(metroStationsMatching(scheme, "  ").isEmpty())
    }
}
