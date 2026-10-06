package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The achievements page's numbers (06.10, Eduard's list). */
class FlightAchievementsTest {

    private val stats = FlightStats(
        flights = 120, km = 90_000, minutes = 2000,
        airports = (1..12).map { FlightCount("A$it", 1) },
        countries = listOf(FlightCount("RU", 100), FlightCount("RS", 20)),
        airlines = listOf(FlightCount("SU", 100)),
        aircraft = emptyList(), routes = emptyList(),
        longestByDistance = null, longestByTime = null, years = emptyList(),
        turnarounds = 3, longLayovers = 0, timeTravels = 1,
    )

    private val all = flightAchievements(stats).associateBy { it.id }

    @Test
    fun theListIsEduards() {
        assertEquals(
            listOf(
                "first_flight", "flights_100", "flights_1000", "around_the_earth", "to_the_moon",
                "day_in_the_air", "week_in_the_air", "month_in_the_air", "year_in_the_air",
                "countries_10", "countries_50", "airlines_10", "aircraft_10",
                "airports_10", "airports_100", "airports_1000", "time_killer", "time_traveller", "flight_attendant",
            ),
            flightAchievements(stats).map { it.id },
        )
    }

    @Test
    fun aRepeatableOne_countsHowManyTimesOver() {
        assertEquals(2L, all.getValue("around_the_earth").times) // 90 000 km: round the Earth twice
        assertEquals(0L, all.getValue("to_the_moon").times)
        assertEquals(3L, all.getValue("flight_attendant").times) // one turnaround is enough, three count three
        assertEquals(1L, all.getValue("day_in_the_air").times)
        // A one-off is earned once, however far past its goal.
        assertEquals(1L, all.getValue("flights_100").times)
        assertEquals(1L, all.getValue("airports_10").times)
        assertTrue(!all.getValue("time_killer").taken)
        assertTrue(all.getValue("time_traveller").taken)
    }

    @Test
    fun aLongChange_andAFlightThatLandsBeforeItLeaves() {
        fun ap(code: String, tz: String) = FlightAirport(code, "", 0.0, 0.0, "XX", tz, code, code)
        val base = FlightBase(airports = listOf(ap("NRT", "Asia/Tokyo"), ap("LAX", "America/Los_Angeles"), ap("SVO", "Europe/Moscow")).associateBy { it.code })
        val toTokyo = Flight(1, "SVO", "NRT", LocalDateTime.parse("2019-03-01T19:00"), LocalDateTime.parse("2019-03-02T10:00"))
        // Nine hours in Narita, then on to Los Angeles, landing "before" leaving.
        val toLa = Flight(2, "NRT", "LAX", LocalDateTime.parse("2019-03-02T19:00"), LocalDateTime.parse("2019-03-02T12:00"))
        assertTrue(flightIsTimeTravel(toLa))
        assertTrue(!flightIsTimeTravel(toTokyo))
        assertEquals(1, flightLongLayovers(listOf(toTokyo, toLa), base))
        val s = flightStats(listOf(toTokyo, toLa), base, Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(1, s.longLayovers)
        assertEquals(1, s.timeTravels)
        // Seven hours is no long change.
        assertEquals(0, flightLongLayovers(listOf(toTokyo, toLa.copy(departure = LocalDateTime.parse("2019-03-02T17:00"))), base))
    }

    @Test
    fun theCountriesFlown_areTheStatisticsCountries() {
        assertEquals(setOf("RU", "RS"), flightCountriesFlown(stats))
    }
}
