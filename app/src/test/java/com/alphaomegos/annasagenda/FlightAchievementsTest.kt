package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.support.*

/** The achievements page's numbers (06.10). */
class FlightAchievementsTest {

    private val stats = FlightStats(
        flights = 120, km = 50_000, minutes = 2000,
        airports = emptyList(),
        countries = listOf(FlightCount("RU", 100), FlightCount("RS", 20)),
        airlines = listOf(FlightCount("SU", 100)),
        aircraft = emptyList(), routes = emptyList(),
        longestByDistance = null, longestByTime = null, years = emptyList(),
        turnarounds = 12,
    )

    @Test
    fun aMilestoneIsTaken_whenTheNumberReachesItsGoal() {
        val a = flightAchievements(stats).associateBy { it.id }
        assertTrue(a.getValue("first_flight").taken)
        assertTrue(a.getValue("flights_100").taken)
        assertTrue(!a.getValue("flights_500").taken)
        assertTrue(a.getValue("around_the_earth").taken)
        assertTrue(!a.getValue("to_the_moon").taken)
        assertTrue(a.getValue("day_in_the_air").taken)
        assertTrue(a.getValue("turnarounds_10").taken)
        assertEquals(12, a.size)
    }

    @Test
    fun theCountriesFlown_areTheStatisticsCountries() {
        assertEquals(setOf("RU", "RS"), flightCountriesFlown(stats))
    }
}
