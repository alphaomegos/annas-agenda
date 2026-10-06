package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import com.alphaomegos.annasagenda.support.*

/**
 * The flight section's shipped files read without a problem (06.10): every
 * airport has a time zone the phone knows and a country the travel base
 * knows, and the codes old App in the Air logs use are all there.
 */
class FlightAssetsTest {

    private fun asset(dir: String, name: String): String {
        val file = listOf("src/main/assets/$dir/$name", "app/src/main/assets/$dir/$name")
            .map(::File).firstOrNull { it.isFile } ?: error("$name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private val airports by lazy { parseFlightAirports(asset("flights", "airports.tsv")) }
    private val airlines by lazy { parseFlightCodes(asset("flights", "airlines.tsv")) }
    private val aircraft by lazy { parseFlightCodes(asset("flights", "aircraft.tsv")) }

    @Test
    fun airportsReadCleanly_withTheOldCodesToo() {
        assertEquals(emptyList<String>(), airports.problems)
        assertTrue(airports.airports.size > 7000)
        listOf("SVO", "BEG", "TLV", "AUH", "KIV", "FRU", "TSE", "SXF").forEach {
            assertTrue("$it missing", it in airports.airports)
        }
        assertEquals("Belgrade", airports.airports.getValue("BEG").city)
        assertEquals("Europe/Moscow", airports.airports.getValue("SVO").timeZone)
    }

    @Test
    fun everyAirportsTimeZone_isOneThePhoneKnows() {
        val bad = airports.airports.values.filter { runCatching { ZoneId.of(it.timeZone) }.isFailure }
        assertEquals(emptyList<String>(), bad.map { "${it.code} ${it.timeZone}" })
    }

    @Test
    fun everyAirportsCountry_isInTheTravelBase() {
        val countries = parseTravelBase(asset("travel", "countries.tsv")).countries.map { it.code }.toSet()
        val bad = airports.airports.values.filter { it.country !in countries }
        assertEquals(emptyList<String>(), bad.map { "${it.code} ${it.country}" })
    }

    @Test
    fun airlinesAndAircraftReadCleanly() {
        assertEquals(emptyList<String>(), airlines.problems)
        assertEquals(emptyList<String>(), aircraft.problems)
        assertEquals("Aeroflot", airlines.names["SU"])
        assertEquals("Pobeda", airlines.names["DP"])
        assertEquals("Wizz Air Abu Dhabi", airlines.names["WAZ"])
        assertEquals("Airbus A320 (sharklets)", aircraft.names["32A"])
        assertEquals("Boeing 777-300ER", aircraft.names["77W"])
    }

    @Test
    fun aBadLineIsReported_andTheRestKept() {
        val p = parseFlightAirports("# c\nSVO\tUUEE\t55.97\t37.41\tRU\tEurope/Moscow\tMoscow\tSheremetyevo\nXX\tbroken\n")
        assertEquals(setOf("SVO"), p.airports.keys)
        assertEquals(1, p.problems.size)
    }
}
