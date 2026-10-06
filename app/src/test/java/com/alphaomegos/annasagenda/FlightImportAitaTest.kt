package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** Taking in App in the Air's data export (06.10), as Eduard's file has it. */
class FlightImportAitaTest {

    private val sample = """
        user: 1
        devices:
        Samsung SM-F926B;aita;android;7.8.4;None;2022-07-17 19:35:11;2024-09-24 18:51:58
        settings:
        country: US

        trips:
        Ownership.MINE;2024-02-24T10:05:00;2024-02-24T16:10:00;AUH;BEG;2024-02-20T10:00:00;2024-02-20T10:00:00;None
        flights:
        None;None;None;None;None;None;search;WAZ;7043;32Q;AUH;BEG;2024-02-24T10:05:00;2024-02-24T16:10:00;2024-02-24T14:05:00;2024-02-24T17:10:00;2024-02-20T10:00:00
        hotels:

        rental cars:

        expenses:


        Ownership.MINE;2016-12-12T11:30:00;2016-12-12T13:45:00;SVO;KRR;2016-12-10T12:58:36;2022-10-26T09:51:05;None
        flights:
        Экономический;3LA;;None;None;None;search;SU;1104;32A;SVO;KRR;2016-12-12T11:30:00;2016-12-12T13:45:00;2016-12-12T14:30:00;2016-12-12T16:45:00;2016-12-10T12:58:36
        None;None;None;None;None;None;search;DP*;802;738;BTS;VKO;2016-06-04T12:15:00;2016-06-04T15:50:00;2016-06-04T14:15:00;2016-06-04T18:50:42;2016-06-01T10:00:00
        broken;line
        hotels:
        None;None;not;a;flight
    """.trimIndent()

    @Test
    fun flightsAreRead_withTheirClocksOffsets() {
        val p = parseAitaExport(sample)
        assertEquals(3, p.flights.size)
        assertEquals(1, p.problems.size)
        val wizz = p.flights[0]
        assertEquals("AUH", wizz.from)
        assertEquals(LocalDateTime.parse("2024-02-24T14:05"), wizz.departure)
        assertEquals(240, wizz.departureOffsetMinutes)
        assertEquals(60, wizz.arrivalOffsetMinutes)
        assertEquals("WAZ", wizz.airline)
        assertEquals("32Q", wizz.aircraft)
        val su = p.flights[1]
        assertEquals("Экономический", su.seatClass)
        assertEquals("3LA", su.seat)
        assertEquals(null, su.booking)
        // "DP*" is Pobeda, seconds are dropped.
        assertEquals("DP", p.flights[2].airline)
        assertEquals(LocalDateTime.parse("2016-06-04T18:50"), p.flights[2].arrival)
    }

    @Test
    fun anotherKindOfFile_isNotRecognised() {
        assertTrue(!parseAitaExport("hello\nworld").recognised)
    }

    @Test
    fun thePlan_skipsWhatIsAlreadyThere_andNamesUnknownAirports() {
        val base = FlightBase(airports = listOf("AUH", "BEG", "SVO", "KRR").associateWith {
            FlightAirport(it, "", 0.0, 0.0, "XX", "UTC", it, it)
        })
        val existing = listOf(Flight(7, "SVO", "KRR", LocalDateTime.parse("2016-12-12T14:30"), LocalDateTime.parse("2016-12-12T16:45")))
        var id = 100L
        val plan = flightImportPlan(parseAitaExport(sample), existing, base) { id++ }
        assertEquals(1, plan.alreadyThere)
        assertEquals(listOf("BTS", "AUH"), plan.flights.map { it.from })
        assertEquals(listOf(100L, 101L), plan.flights.map { it.id })
        assertEquals(setOf("BTS", "VKO"), plan.unknownAirports)
        assertEquals(365L, flightMinutes(plan.flights[1], base))
    }
}
