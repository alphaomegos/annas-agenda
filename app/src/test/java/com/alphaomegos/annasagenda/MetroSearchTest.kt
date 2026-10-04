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
        assertEquals(listOf("Каховская", "Новокузнецкая", "Улица Старокачаловская"), names("ка"))
        assertEquals(listOf("Новокузнецкая"), names("куз"))
        assertEquals(listOf("Улица Старокачаловская"), names("стар"))
    }

    @Test
    fun aNameOnTwoLines_isOneChoice_withBothLinesInTheSchemesOrder() {
        val choice = metroStationSuggestions(scheme, "Кахов").single()
        assertEquals(listOf(2L, 11L), choice.lineIds)
        assertEquals(false, choice.allClosed) // one of the two is open
    }

    @Test
    fun everyStationOfTheNameClosed_isMarked() {
        assertTrue(metroStationSuggestions(scheme, "улица").single().allClosed)
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
        assertTrue(names("каховская").isEmpty())
        assertEquals(setOf(1L, 3L), metroStationsForName(scheme, "каховская"))
    }

    @Test
    fun theListIsCut() {
        assertEquals(1, metroStationSuggestions(scheme, "а", limit = 1).size)
    }
}
