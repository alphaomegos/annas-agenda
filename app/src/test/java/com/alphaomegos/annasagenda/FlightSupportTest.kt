package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** The flight log's rules (06.10): time, distance, journeys, statistics, trips to suggest. */
class FlightSupportTest {

    private fun ap(code: String, lat: Double, lon: Double, country: String, tz: String, city: String) =
        FlightAirport(code, "", lat, lon, country, tz, city, "$city airport")

    private val base = FlightBase(
        airports = listOf(
            ap("TLV", 32.0114, 34.8867, "IL", "Asia/Jerusalem", "Tel Aviv"),
            ap("ATH", 37.9364, 23.9445, "GR", "Europe/Athens", "Athens"),
            ap("BEG", 44.8184, 20.3091, "RS", "Europe/Belgrade", "Belgrade"),
            ap("AUH", 24.4330, 54.6511, "AE", "Asia/Dubai", "Abu Dhabi"),
            ap("LHR", 51.4700, -0.4543, "GB", "Europe/London", "London"),
            ap("LGW", 51.1537, -0.1821, "GB", "Europe/London", "London"),
            ap("SVO", 55.9726, 37.4146, "RU", "Europe/Moscow", "Moscow"),
        ).associateBy { it.code },
        airlines = mapOf("JU" to "Air Serbia"),
    )

    private fun t(s: String) = LocalDateTime.parse(s)
    private fun fl(id: Long, from: String, to: String, dep: String, arr: String, journey: Long? = null) =
        Flight(id, from, to, t(dep), t(arr), journeyId = journey)

    private val now = Instant.parse("2026-10-06T00:00:00Z")

    @Test
    fun timeInTheAir_isByTheRealClock_actualWhereKnown() {
        // AUH 14:05 (+4) → BEG 17:10 (+1): 6 h 05 min.
        val f = fl(1, "AUH", "BEG", "2024-02-24T14:05", "2024-02-24T17:10")
        assertEquals(365L, flightMinutes(f, base))
        // Left 14:19, landed 17:03: 5 h 44 min, as App in the Air showed.
        assertEquals(344L, flightMinutes(f.copy(actualDeparture = t("2024-02-24T14:19"), actualArrival = t("2024-02-24T17:03")), base))
        // A known offset wins over the zone for the scheduled time; an unknown airport with none gives nothing.
        assertEquals(425L, flightMinutes(f.copy(departureOffsetMinutes = 300), base))
        assertNull(flightMinutes(fl(2, "XXX", "BEG", "2024-02-24T14:05", "2024-02-24T17:10"), base))
    }

    @Test
    fun distance_isTheGreatCircle_andTheArcRunsFromOneEndToTheOther() {
        val km = flightDistanceKm(fl(1, "TLV", "BEG", "2024-01-01T10:00", "2024-01-01T13:00"), base)!!
        assertTrue("TLV–BEG is $km", km in 1850.0..1950.0)
        val arc = flightArc(base.airports.getValue("SVO"), base.airports.getValue("LHR"), segments = 10)
        assertEquals(11, arc.size)
        assertEquals(55.9726, arc.first().first, 1e-6)
        assertEquals(-0.4543, arc.last().second, 1e-6)
        // The great circle bows north of the straight line on the map.
        assertTrue(arc[5].first > (55.9726 + 51.47) / 2)
    }

    @Test
    fun aChangeWithinADay_fromWhereItLanded_makesOneJourney() {
        val a = fl(1, "TLV", "ATH", "2024-07-11T04:42", "2024-07-11T07:00")
        val b = fl(2, "ATH", "BEG", "2024-07-11T12:30", "2024-07-11T14:21")
        val home = fl(3, "BEG", "TLV", "2024-07-20T10:00", "2024-07-20T14:00")
        var log = flightAdded(emptyList(), a, base, group = true)
        log = flightAdded(log, home, base, group = true)
        log = flightAdded(log, b, base, group = true)
        assertEquals(listOf(1L, 1L, null), listOf(1L, 2L, 3L).map { id -> log.first { it.id == id }.journeyId })
        val journeys = flightJourneys(log, base)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), journeys.map { j -> j.flights.map { it.id } })
        // Grouping off: added as they are.
        assertTrue(flightAdded(listOf(a), b, base, group = false).all { it.journeyId == null })
    }

    @Test
    fun anotherAirportOfTheSameCity_isAChangeToo_aLongStopIsNot() {
        val a = fl(1, "SVO", "LGW", "2024-01-01T10:00", "2024-01-01T12:00")
        val b = fl(2, "LHR", "TLV", "2024-01-01T18:00", "2024-01-02T01:00")
        assertTrue(flightConnects(a, b, base))
        assertTrue(!flightConnects(a, b.copy(departure = t("2024-01-03T18:00"), arrival = t("2024-01-04T01:00")), base))
    }

    @Test
    fun splittingOff_makesTwoJourneys_andGroupingNeverJoinsItBack() {
        val log = flightsRegrouped(listOf(
            fl(1, "TLV", "ATH", "2024-07-11T04:00", "2024-07-11T06:00"),
            fl(2, "ATH", "BEG", "2024-07-11T08:00", "2024-07-11T09:00"),
            fl(3, "BEG", "SVO", "2024-07-11T11:00", "2024-07-11T14:00"),
        ), base)
        assertEquals(listOf(1L, 1L, 1L), log.map { it.journeyId })
        val split = flightSplitOff(log, 2, base)
        assertEquals(listOf(null, 2L, 2L), split.map { it.journeyId })
        assertTrue(split.first { it.id == 2L }.keptApart)
        assertEquals(listOf(null, 2L, 2L), flightsRegrouped(split, base).map { it.journeyId })
        assertTrue(flightsUngrouped(split).all { it.journeyId == null })
    }

    @Test
    fun removingTheMiddleFlight_breaksTheJourney() {
        val log = flightsRegrouped(listOf(
            fl(1, "TLV", "ATH", "2024-07-11T04:00", "2024-07-11T06:00"),
            fl(2, "ATH", "BEG", "2024-07-11T08:00", "2024-07-11T09:00"),
            fl(3, "BEG", "SVO", "2024-07-11T11:00", "2024-07-11T14:00"),
        ), base)
        assertEquals(listOf(null, null), flightRemoved(log, 2, base).map { it.journeyId })
        assertEquals(listOf(2L, 2L), flightRemoved(log, 1, base).map { it.journeyId })
    }

    @Test
    fun statistics_countWhatWasFlown_notWhatIsAhead() {
        val log = listOf(
            fl(1, "TLV", "BEG", "2023-12-18T08:55", "2023-12-18T11:10").copy(airline = "JU", aircraft = "320"),
            fl(2, "BEG", "TLV", "2023-12-11T12:10", "2023-12-11T16:00").copy(airline = "JU", aircraft = "320"),
            fl(3, "BEG", "AUH", "2024-02-17T17:45", "2024-02-18T02:00").copy(airline = "WAZ"),
            fl(4, "BEG", "SVO", "2027-01-01T10:00", "2027-01-01T14:00"),
        )
        val s = flightStats(log, base, now)
        assertEquals(3, s.flights)
        assertEquals(listOf(FlightCount("BEG", 3), FlightCount("TLV", 2), FlightCount("AUH", 1)), s.airports)
        assertEquals(listOf(FlightCount("JU", 2), FlightCount("WAZ", 1)), s.airlines)
        assertEquals(listOf(FlightCount("BEG–TLV", 2), FlightCount("AUH–BEG", 1)), s.routes)
        assertEquals(3L, s.longestByDistance?.id)
        assertEquals(listOf(2023, 2024), s.years.map { it.year })
        assertEquals(195L + 170L + 315L, s.minutes)
    }

    @Test
    fun travelSuggestions_areJourneysEnds_notChanges_notHome_notKnownTrips() {
        val log = flightsRegrouped(listOf(
            fl(1, "TLV", "ATH", "2024-07-11T04:00", "2024-07-11T06:00"),
            fl(2, "ATH", "BEG", "2024-07-11T08:00", "2024-07-11T09:00"),
            fl(3, "BEG", "AUH", "2024-02-17T17:45", "2024-02-18T02:00"),
            fl(4, "AUH", "BEG", "2024-02-24T14:05", "2024-02-24T17:10"),
        ), base)
        val home = setOf(flightPlaceKey(base.airports.getValue("BEG")))
        val s = flightTravelSuggestions(log, base, emptyMap(), emptyList(), home, now)
        assertEquals(listOf(FlightTravelSuggestion("AE", "Abu Dhabi", 2024, 2, listOf(3L))), s)
        val known = listOf(TravelCountryRecord("AE", trips = listOf(TravelTrip(1, 2024, 2, listOf("abu dhabi")))))
        assertTrue(flightTravelSuggestions(log, base, emptyMap(), known, home, now).isEmpty())
        // Without a home, Belgrade comes too, once for July and once for February.
        assertEquals(3, flightTravelSuggestions(log, base, emptyMap(), emptyList(), emptySet(), now).size)
    }

    @Test
    fun names_areTheUsersFirst_thenTheBases_thenTheCode() {
        assertEquals("Белград", flightCityName("BEG", base, mapOf("BEG" to "Белград")))
        assertEquals("Belgrade", flightCityName("BEG", base, emptyMap()))
        assertEquals("XXX", flightCityName("XXX", base, emptyMap()))
        assertEquals("Air Serbia", flightAirlineName("JU", base, emptyMap()))
    }
}
