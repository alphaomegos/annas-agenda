package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/**
 * The travel section's rules (05.10), on Eduard's own examples where there
 * are any: trips in and out, the user's own countries, the four views and
 * the search.
 */
class TravelSupportTest {

    private val base = listOf(
        TravelBaseCountry("AT", TravelContinent.EUROPE, "Austria"),
        TravelBaseCountry("AZ", TravelContinent.EUROPE, "Azerbaijan"),
        TravelBaseCountry("BG", TravelContinent.EUROPE, "Bulgaria"),
        TravelBaseCountry("BY", TravelContinent.EUROPE, "Belarus"),
        TravelBaseCountry("GE", TravelContinent.EUROPE, "Georgia"),
        TravelBaseCountry("RU", TravelContinent.EUROPE, "Russia"),
        TravelBaseCountry("TR", TravelContinent.EUROPE, "Türkiye"),
        TravelBaseCountry("AQ", TravelContinent.ANTARCTICA, "Antarctica"),
        TravelBaseCountry("JP", TravelContinent.ASIA, "Japan"),
    )
    private val baseMap = base.associateBy { it.code }

    private val ru = mapOf(
        "AT" to "Австрия", "AZ" to "Азербайджан", "BG" to "Болгария", "BY" to "Беларусь",
        "GE" to "Грузия", "RU" to "Россия", "TR" to "Турция", "AQ" to "Антарктида", "JP" to "Япония",
    )

    private fun names(records: List<TravelCountryRecord>) = object : TravelNames {
        override fun nameOf(countryId: String) =
            ru[countryId] ?: records.firstOrNull { it.countryId == countryId }?.customName ?: countryId
        override fun searchNamesOf(countryId: String) = listOfNotNull(nameOf(countryId), baseMap[countryId]?.englishName)
    }

    private val abc = naturalOrder<String>()
    private var cursor = 100L
    private fun newId() = cursor++

    private fun trip(records: List<TravelCountryRecord>, id: String, year: Int, month: Int, vararg cities: String) =
        travelAfterAddingTrip(records, id, year, month, cities.toList(), ::newId)

    /** Eduard's "by years" example, as data. */
    private val trips: List<TravelCountryRecord> = emptyList<TravelCountryRecord>()
        .let { trip(it, "RU", 2026, 3, "Москва", "Тверь") }
        .let { trip(it, "BG", 2026, 2) }
        .let { trip(it, "GE", 2025, 9, "Тбилиси", "Кутаиси", "Батуми") }
        .let { trip(it, "TR", 2025, 9, "Трабзон") }
        .let { trip(it, "AZ", 2024, 5) }
        .let { trip(it, "BY", 2018, 5, "Минск") }
        .let { trip(it, "BY", 2020, 5, "минск ") }
        .let { trip(it, "BY", 2021, 7, "Брест") }

    @Test
    fun aTrip_isAMonthAndTheCitiesTyped_cleaned() {
        val r = trip(emptyList(), "BY", 2018, 5, " Минск ", "", "минск", "Брест  ")
        assertEquals(listOf("Минск", "Брест"), r.single().trips.single().cities)
        assertSame(r, trip(r, "BY", 2018, 13, "x"))
        assertSame(r, trip(r, "BY", 1800, 1))
    }

    @Test
    fun byYears_newestYearAndMonthFirst_sameMonthByName() {
        val groups = travelByYears(trips, names(trips), abc, reversed = false)
        assertEquals(listOf(2026, 2025, 2024, 2021, 2020, 2018), groups.map { it.year })
        assertEquals(listOf("RU", "BG"), groups[0].entries.map { it.countryId })
        assertEquals(listOf("GE", "TR"), groups[1].entries.map { it.countryId }) // Грузия before Турция
        assertEquals(listOf("Тбилиси", "Кутаиси", "Батуми"), groups[1].entries[0].cities)
    }

    @Test
    fun byYears_reversed_oldestFirst() {
        val groups = travelByYears(trips, names(trips), abc, reversed = true)
        assertEquals(2018, groups.first().year)
        assertEquals(listOf(2, 3), groups.last().entries.map { it.month })
    }

    @Test
    fun byCountries_mine_withYearsAndCitiesOnceEach() {
        val rows = travelByCountries(travelAllCountryIds(base, trips), trips, names(trips), abc, onlyMine = true, reversed = false)
        assertEquals(listOf("AZ", "BY", "BG", "GE", "RU", "TR"), rows.map { it.countryId })
        val by = rows.single { it.countryId == "BY" }
        assertEquals(listOf(2018, 2020, 2021), by.years)
        assertEquals(listOf("Брест", "Минск"), by.cities)
    }

    @Test
    fun byCountries_all_andReversed() {
        val rows = travelByCountries(travelAllCountryIds(base, trips), trips, names(trips), abc, onlyMine = false, reversed = true)
        assertEquals(base.size, rows.size)
        assertEquals("JP", rows.first().countryId) // Япония
        assertTrue(rows.single { it.countryId == "AT" }.years.isEmpty())
    }

    @Test
    fun byCities_countsTrips_whateverTheCase() {
        val rows = travelByCities(trips, abc, reversed = false)
        assertEquals(2, rows.single { it.name == "Минск" }.visits)
        assertEquals(1, rows.single { it.name == "Тверь" }.visits)
        assertEquals("Батуми", rows.first().name)
        assertEquals("Трабзон", travelByCities(trips, abc, reversed = true).first().name)
    }

    @Test
    fun byContinents_visitedOutOfAll_andTheCitiesSeen() {
        val continentNames = mapOf(
            TravelContinent.AFRICA to "Африка", TravelContinent.ANTARCTICA to "Антарктида", TravelContinent.ASIA to "Азия",
            TravelContinent.EUROPE to "Европа", TravelContinent.NORTH_AMERICA to "Северная Америка",
            TravelContinent.OCEANIA to "Австралия и Океания", TravelContinent.SOUTH_AMERICA to "Южная Америка",
        )
        val rows = travelByContinents(base, trips, { continentNames.getValue(it) }, abc, reversed = false)
        assertEquals(TravelContinent.OCEANIA, rows.first().continent) // Австралия и Океания
        val europe = rows.single { it.continent == TravelContinent.EUROPE }
        assertEquals(6, europe.visitedCountries)
        assertEquals(7, europe.totalCountries)
        assertEquals(8, europe.cities) // Москва Тверь Тбилиси Кутаиси Батуми Трабзон Минск Брест
        assertEquals(TravelContinentRow(TravelContinent.ASIA, 0, 1, 0), rows.single { it.continent == TravelContinent.ASIA })
    }

    @Test
    fun aContinentMoved_countsThere_andMovedBack_forgetsItWasMoved() {
        val moved = travelAfterSettingContinent(trips, "TR", TravelContinent.ASIA, baseMap)
        assertEquals(TravelContinent.ASIA, travelContinentOf("TR", baseMap, moved))
        val back = travelAfterSettingContinent(moved, "TR", TravelContinent.EUROPE, baseMap)
        assertEquals(null, back.single { it.countryId == "TR" }.continentOverride)
        // A country with no trips, moved, keeps a record of just that — and loses it when moved back.
        val jp = travelAfterSettingContinent(emptyList(), "JP", TravelContinent.EUROPE, baseMap)
        assertEquals(1, jp.size)
        assertTrue(travelAfterSettingContinent(jp, "JP", null, baseMap).isEmpty())
        assertSame(trips, travelAfterSettingContinent(trips, "XX", TravelContinent.ASIA, baseMap))
    }

    @Test
    fun removingTheLastTrip_dropsABaseCountrysRecord_notTheUsersOwnCountry() {
        val one = trip(emptyList(), "BG", 2026, 2)
        assertTrue(travelAfterRemovingTrip(one, one.single().trips.single().id).isEmpty())

        val own = travelAfterAddingUserCountry(emptyList(), " Нарния ", TravelContinent.EUROPE, TravelMapPoint(1f, 2f), ::newId)
        val id = own.single().countryId
        assertTrue(id.startsWith(TRAVEL_USER_COUNTRY_PREFIX))
        assertEquals("Нарния", own.single().customName)
        val withTrip = trip(own, id, 2020, 1)
        val tripId = withTrip.single().trips.single().id
        assertEquals(1, travelAfterRemovingTrip(withTrip, tripId).size)
        assertTrue(travelAfterRemovingUserCountry(withTrip, id).isEmpty())
    }

    @Test
    fun theUsersOwnCountry_isCountedOnItsContinent_andFound() {
        val own = travelAfterAddingUserCountry(trips, "Нарния", TravelContinent.ASIA, TravelMapPoint(1f, 2f), ::newId)
        val id = own.last().countryId
        val all = travelAllCountryIds(base, own)
        assertTrue(id in all)
        val asia = travelByContinents(base, own, { it.name }, abc, false).single { it.continent == TravelContinent.ASIA }
        assertEquals(2, asia.totalCountries)
        assertEquals(listOf(id), travelSearch(all, own, names(own), abc, "нарн"))
    }

    @Test
    fun editingATrip_changesItAlone() {
        val tripId = trips.single { it.countryId == "AZ" }.trips.single().id
        val edited = travelAfterEditingTrip(trips, tripId, 2023, 6, listOf("Баку"))
        assertEquals(TravelTrip(tripId, 2023, 6, listOf("Баку")), edited.single { it.countryId == "AZ" }.trips.single())
        assertSame(edited, travelAfterEditingTrip(edited, tripId, 2023, 6, listOf("Баку")))
        assertSame(trips, travelAfterEditingTrip(trips, 9999, 2023, 6, emptyList()))
    }

    @Test
    fun search_byNameInEitherLanguage_byCity_byYear_alwaysAtoZ() {
        val all = travelAllCountryIds(base, trips)
        val n = names(trips)
        assertEquals(listOf("GE"), travelSearch(all, trips, n, abc, "груз"))
        assertEquals(listOf("GE"), travelSearch(all, trips, n, abc, "georg"))
        assertEquals(listOf("BY"), travelSearch(all, trips, n, abc, "МИНСК"))
        assertEquals(listOf("GE", "TR"), travelSearch(all, trips, n, abc, "2025"))
        assertTrue(travelSearch(all, trips, n, abc, "  ").isEmpty())
    }
}
