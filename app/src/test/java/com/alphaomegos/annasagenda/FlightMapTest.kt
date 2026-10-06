package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The flights on the world map and the airports offered while typing (06.10). */
class FlightMapTest {

    private fun ap(code: String, lat: Double, lon: Double, city: String, name: String = "$city airport") =
        FlightAirport(code, "", lat, lon, "XX", "UTC", city, name)

    private val base = FlightBase(
        airports = listOf(
            ap("SVO", 55.97, 37.41, "Moscow", "Sheremetyevo"),
            ap("VKO", 55.59, 37.26, "Moscow", "Vnukovo"),
            ap("BEG", 44.82, 20.31, "Belgrade"),
            ap("NRT", 35.76, 140.39, "Tokyo"),
            ap("LAX", 33.94, -118.41, "Los Angeles"),
        ).associateBy { it.code },
    )

    private fun fl(id: Long, from: String, to: String) =
        Flight(id, from, to, LocalDateTime.parse("2024-01-01T10:00"), LocalDateTime.parse("2024-01-01T12:00"))

    @Test
    fun theProjection_isTheMapGenerators() {
        val (x, y) = travelGridPoint(0.0, 0.0, 8000)
        assertEquals(4000f, x, 0.01f)
        assertEquals(0.5072f * 8000 / 2, y, 0.01f)
        val (xe, yn) = travelGridPoint(90.0, 180.0, 8000)
        assertEquals(4000f + 0.5322f * 4000, xe, 0.5f)
        assertEquals(0f, yn, 0.01f)
    }

    @Test
    fun aRouteIsOneArc_howeverOftenFlown_eitherWayRound() {
        val routes = flightMapRoutes(listOf(fl(1, "SVO", "BEG"), fl(2, "BEG", "SVO"), fl(3, "SVO", "NRT"), fl(4, "SVO", "XXX")), base, 8000)
        assertEquals(listOf("NRT" to "SVO", "BEG" to "SVO"), routes.map { it.a to it.b })
        assertEquals(listOf(1L, 2L), routes.last().flightIds)
        assertEquals(1, routes.first().polylines.size)
    }

    @Test
    fun anArcOverTheDateLine_isCutInTwo() {
        val route = flightMapRoutes(listOf(fl(1, "NRT", "LAX")), base, 8000).single()
        assertEquals(2, route.polylines.size)
        assertTrue(route.polylines.all { it.size >= 4 })
    }

    @Test
    fun airportsOfferedWhileTyping() {
        assertEquals(listOf("SVO"), flightAirportSuggestions(base, emptyMap(), "svo"))
        // A city: both Moscow airports, the one flown before first.
        assertEquals(listOf("VKO", "SVO"), flightAirportSuggestions(base, emptyMap(), "mos", preferred = setOf("VKO")))
        assertEquals(listOf("BEG"), flightAirportSuggestions(base, mapOf("BEG" to "Белград"), "бел"))
        assertEquals(listOf("VKO"), flightAirportSuggestions(base, emptyMap(), "vnuk"))
        assertTrue(flightAirportSuggestions(base, emptyMap(), " ").isEmpty())
    }

    @Test
    fun flags() {
        assertEquals("🇷🇸", flightFlag("RS"))
        assertEquals("", flightFlag("user:1"))
    }
}
