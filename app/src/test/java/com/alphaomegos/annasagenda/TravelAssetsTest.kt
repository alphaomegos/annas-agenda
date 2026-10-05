package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/**
 * The travel section's shipped files read without a problem, and agree with
 * each other (05.10): every country of the base is on the map, as an outline
 * or a dot, and the map draws nothing the base does not know.
 */
class TravelAssetsTest {

    private fun asset(name: String): String {
        val file = listOf("src/main/assets/travel/$name", "app/src/main/assets/travel/$name")
            .map(::File).firstOrNull { it.isFile } ?: error("$name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private val base by lazy { parseTravelBase(asset("countries.tsv")) }
    private val map by lazy { parseTravelWorldMap(asset("world_map.txt")) }

    @Test
    fun theBaseReadsCleanly_allOfIso3166AndKosovo() {
        assertEquals(emptyList<String>(), base.problems)
        assertEquals(250, base.countries.size)
        assertTrue(base.countries.any { it.code == "XK" })
    }

    @Test
    fun theMapReadsCleanly_andMatchesTheBase() {
        assertEquals(emptyList<String>(), map.problems)
        val codes = base.countries.map { it.code }.toSet()
        assertEquals(codes, map.map.shapes.keys)
    }

    /** The travellers' continents for countries on a border (Eduard, 05.10). */
    @Test
    fun countriesOnABorder_areWhereTravellersCountThem() {
        val by = base.countries.associate { it.code to it.continent }
        listOf("RU", "TR", "CY", "GE", "AM", "AZ").forEach { assertEquals(it, TravelContinent.EUROPE, by[it]) }
        assertEquals(TravelContinent.ASIA, by["KZ"])
        assertEquals(TravelContinent.AFRICA, by["EG"])
        assertEquals(TravelContinent.ANTARCTICA, by["AQ"])
        assertEquals(TravelContinent.SOUTH_AMERICA, by["GF"])
    }

    @Test
    fun aTapInTheMiddleOfBelarus_isBelarus() {
        val b = map.map.shapes.getValue("BY").bounds
        assertEquals("BY", map.map.countryAt((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f, dotRadius = 0f))
    }

    @Test
    fun evenOdd_aLakeIsNotTheCountry_andDotsWinWithinTheirRadius() {
        val parsed = parseTravelWorldMap(
            """
            size 100 100
            AA	0,0,10,0,0,10,-10,0;2,2,6,0,0,6,-6,0
            BB	@50,50
            """.trimIndent()
        )
        assertEquals(emptyList<String>(), parsed.problems)
        val m = parsed.map
        assertEquals("AA", m.countryAt(1f, 1f, 1f))
        assertNull(m.countryAt(5f, 5f, 1f)) // inside the hole
        assertEquals("BB", m.countryAt(51f, 50f, 2f))
        assertNull(m.countryAt(55f, 50f, 2f))
        assertFalse(m.shapes.getValue("AA").contains(20f, 20f))
    }

    @Test
    fun badLines_areProblems_notACrash() {
        assertEquals(2, parseTravelBase("XX\tEUROPE\nYY\tMARS\tNowhere\nZZ\tASIA\tZed").problems.size)
        assertEquals(2, parseTravelWorldMap("size 10 10\nAA\t1,2\nBB\t@x,1").problems.size)
    }
}
