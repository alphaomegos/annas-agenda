package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.TRAVEL_USER_COUNTRY_PREFIX
import com.alphaomegos.annasagenda.model.TravelBaseCountry
import com.alphaomegos.annasagenda.model.TravelContinent
import com.alphaomegos.annasagenda.model.TravelCountryRecord
import com.alphaomegos.annasagenda.model.TravelMapPoint
import com.alphaomegos.annasagenda.model.TravelTrip

/**
 * The travel section's rules (05.10): trips in and out, the user's own
 * countries, and the four views of what was visited.
 *
 * Names are not decided here — which language a country is called in is the
 * screen's business — so the views take [names]: the name to show and sort
 * by, and the other names a search may also find a country under (its
 * English one). Sorting takes a [Comparator] of names so the screen can hand
 * in the locale's own alphabet ("Ё" after "Е", "Č" after "C").
 *
 * Every edit gives back the same list when it changes nothing.
 */
interface TravelNames {
    fun nameOf(countryId: String): String
    fun searchNamesOf(countryId: String): List<String> = listOf(nameOf(countryId))
}

/** The earliest and latest years a trip may be put in; anything else is a typing slip. */
val TRAVEL_YEARS: IntRange = 1900..2100

/** Cities as typed: trimmed, inner spaces single, blank ones gone, a repeat — whatever its case — once. */
fun travelCleanCities(cities: List<String>): List<String> {
    val seen = HashSet<String>()
    return cities
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() && seen.add(travelKey(it)) }
}

/** Case, "ё" and spaces do not make two cities two. */
fun travelKey(text: String): String = text.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/* ---------------- trips ---------------- */

/** A trip to [countryId]; refused for a month or year that cannot be. */
fun travelAfterAddingTrip(
    records: List<TravelCountryRecord>,
    countryId: String,
    year: Int,
    month: Int,
    cities: List<String>,
    newId: () -> Long,
): List<TravelCountryRecord> {
    if (year !in TRAVEL_YEARS || month !in 1..12 || countryId.isBlank()) return records
    val trip = TravelTrip(id = newId(), year = year, month = month, cities = travelCleanCities(cities))
    val existing = records.firstOrNull { it.countryId == countryId }
    return if (existing == null) {
        records + TravelCountryRecord(countryId = countryId, trips = listOf(trip))
    } else {
        records.map { if (it.countryId == countryId) it.copy(trips = it.trips + trip) else it }
    }
}

fun travelAfterEditingTrip(
    records: List<TravelCountryRecord>,
    tripId: Long,
    year: Int,
    month: Int,
    cities: List<String>,
): List<TravelCountryRecord> {
    if (year !in TRAVEL_YEARS || month !in 1..12) return records
    val clean = travelCleanCities(cities)
    var changed = false
    val next = records.map { r ->
        if (r.trips.none { it.id == tripId }) r
        else r.copy(trips = r.trips.map { t ->
            if (t.id != tripId) t
            else {
                val edited = t.copy(year = year, month = month, cities = clean)
                if (edited != t) changed = true
                edited
            }
        })
    }
    return if (changed) next else records
}

/**
 * A trip gone. A base country left with nothing kept loses its record; the
 * user's own country stays, being a country the user made, not a trip.
 */
fun travelAfterRemovingTrip(records: List<TravelCountryRecord>, tripId: Long): List<TravelCountryRecord> {
    if (records.none { r -> r.trips.any { it.id == tripId } }) return records
    return records
        .map { r -> r.copy(trips = r.trips.filterNot { it.id == tripId }) }
        .filter { it.isUserCountry || it.trips.isNotEmpty() || it.continentOverride != null }
}

/* ---------------- continents ---------------- */

/** The continent [countryId] is counted on: the user's choice, else the base's, else none. */
fun travelContinentOf(
    countryId: String,
    base: Map<String, TravelBaseCountry>,
    records: List<TravelCountryRecord>,
): TravelContinent? =
    records.firstOrNull { it.countryId == countryId }?.continentOverride ?: base[countryId]?.continent

/**
 * A base country moved to [continent], or back to its own when that is null
 * or the same as the base's — so "moved" is only ever stored when it differs.
 */
fun travelAfterSettingContinent(
    records: List<TravelCountryRecord>,
    countryId: String,
    continent: TravelContinent?,
    base: Map<String, TravelBaseCountry>,
): List<TravelCountryRecord> {
    val record = records.firstOrNull { it.countryId == countryId }
    if (record?.isUserCountry == true) {
        // The user's own country always has a continent; it can be changed, not cleared.
        if (continent == null || continent == record.continentOverride) return records
        return records.map { if (it.countryId == countryId) it.copy(continentOverride = continent) else it }
    }
    val baseCountry = base[countryId] ?: return records
    val override = continent?.takeIf { it != baseCountry.continent }
    if (record == null) {
        return if (override == null) records else records + TravelCountryRecord(countryId, continentOverride = override)
    }
    if (record.continentOverride == override) return records
    return records
        .map { if (it.countryId == countryId) it.copy(continentOverride = override) else it }
        .filter { it.isUserCountry || it.trips.isNotEmpty() || it.continentOverride != null }
}

/* ---------------- the user's own countries ---------------- */

/** A country of the user's own (terra incognito): a name, a continent, a place on the map. */
fun travelAfterAddingUserCountry(
    records: List<TravelCountryRecord>,
    name: String,
    continent: TravelContinent,
    point: TravelMapPoint,
    newId: () -> Long,
): List<TravelCountryRecord> {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return records
    return records + TravelCountryRecord(
        countryId = TRAVEL_USER_COUNTRY_PREFIX + newId(),
        continentOverride = continent,
        customName = clean,
        customPoint = point,
    )
}

fun travelAfterEditingUserCountry(
    records: List<TravelCountryRecord>,
    countryId: String,
    name: String,
    point: TravelMapPoint?,
): List<TravelCountryRecord> {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    val record = records.firstOrNull { it.countryId == countryId && it.isUserCountry } ?: return records
    if (clean.isEmpty()) return records
    val edited = record.copy(customName = clean, customPoint = point ?: record.customPoint)
    if (edited == record) return records
    return records.map { if (it.countryId == countryId) edited else it }
}

/** The user's own country, with its trips. */
fun travelAfterRemovingUserCountry(records: List<TravelCountryRecord>, countryId: String): List<TravelCountryRecord> {
    if (records.none { it.countryId == countryId && it.isUserCountry }) return records
    return records.filterNot { it.countryId == countryId }
}

/* ---------------- what was visited ---------------- */

/** Countries with at least one trip. */
fun travelVisited(records: List<TravelCountryRecord>): Set<String> =
    records.filter { it.trips.isNotEmpty() }.mapTo(LinkedHashSet()) { it.countryId }

/** Every country there is: the base's, then the user's own. */
fun travelAllCountryIds(base: List<TravelBaseCountry>, records: List<TravelCountryRecord>): List<String> =
    base.map { it.code } + records.filter { it.isUserCountry }.map { it.countryId }

/* ---------------- view: by years ---------------- */

data class TravelYearEntry(val month: Int, val countryId: String, val cities: List<String>, val tripId: Long)
data class TravelYearGroup(val year: Int, val entries: List<TravelYearEntry>)

/**
 * Every trip under its year. Naturally the newest year first and, inside a
 * year, the latest month first (05.10: "2026 — March, February"); [reversed]
 * turns both round. Trips in the same month go by country name, A to Z.
 */
fun travelByYears(
    records: List<TravelCountryRecord>,
    names: TravelNames,
    collator: Comparator<String>,
    reversed: Boolean,
): List<TravelYearGroup> {
    val entries = records.flatMap { r -> r.trips.map { t -> t to r.countryId } }
    val byName = compareBy<Pair<TravelTrip, String>, String>(collator) { names.nameOf(it.second) }
    return entries
        .groupBy { it.first.year }
        .entries
        .sortedBy { if (reversed) it.key else -it.key }
        .map { (year, trips) ->
            val inYear = trips.sortedWith(
                compareBy<Pair<TravelTrip, String>> { if (reversed) it.first.month else -it.first.month }
                    .then(byName)
                    .thenBy { it.first.id }
            )
            TravelYearGroup(year, inYear.map { (t, id) -> TravelYearEntry(t.month, id, t.cities, t.id) })
        }
}

/* ---------------- view: by countries ---------------- */

/** A country with the years it was visited in and every city seen there. */
data class TravelCountryRow(val countryId: String, val years: List<Int>, val cities: List<String>)

/**
 * Countries by name, A to Z ([reversed]: Z to A): only those with a trip, or
 * all of them when [onlyMine] is off. Years once each, in order; cities once
 * each, A to Z.
 */
fun travelByCountries(
    allCountryIds: List<String>,
    records: List<TravelCountryRecord>,
    names: TravelNames,
    collator: Comparator<String>,
    onlyMine: Boolean,
    reversed: Boolean,
): List<TravelCountryRow> {
    val byId = records.associateBy { it.countryId }
    val ids = if (onlyMine) allCountryIds.filter { byId[it]?.trips?.isNotEmpty() == true } else allCountryIds
    val order = compareBy<String, String>(collator) { names.nameOf(it) }
    return ids.distinct()
        .sortedWith(if (reversed) order.reversed() else order)
        .map { id ->
            val trips = byId[id]?.trips.orEmpty()
            TravelCountryRow(
                countryId = id,
                years = trips.map { it.year }.distinct().sorted(),
                cities = distinctCities(trips.flatMap { it.cities }).sortedWith(collator),
            )
        }
}

private fun distinctCities(cities: List<String>): List<String> {
    val seen = HashSet<String>()
    return cities.filter { seen.add(travelKey(it)) }
}

/* ---------------- view: by cities ---------------- */

/** A city and how many trips took in it — the screen shows the number when it is more than one. */
data class TravelCityRow(val name: String, val visits: Int, val countryIds: List<String>)

/**
 * Every city seen, A to Z ([reversed]: Z to A), a city counted once per trip
 * that took it in. One name is one city, whatever its case — the same name in
 * two countries is rare enough to live with, and [TravelCityRow.countryIds]
 * says where.
 */
fun travelByCities(
    records: List<TravelCountryRecord>,
    collator: Comparator<String>,
    reversed: Boolean,
): List<TravelCityRow> {
    data class Seen(val name: String, var visits: Int, val countries: LinkedHashSet<String>)
    val byKey = LinkedHashMap<String, Seen>()
    records.forEach { r ->
        r.trips.forEach { t ->
            t.cities.forEach { city ->
                val seen = byKey.getOrPut(travelKey(city)) { Seen(city, 0, LinkedHashSet()) }
                seen.visits++
                seen.countries += r.countryId
            }
        }
    }
    val order = compareBy<TravelCityRow, String>(collator) { it.name }
    return byKey.values
        .map { TravelCityRow(it.name, it.visits, it.countries.toList()) }
        .sortedWith(if (reversed) order.reversed() else order)
}

/* ---------------- view: by continents ---------------- */

/**
 * A continent: how many of its countries were visited, out of how many there
 * are — the base's and the user's own — and how many cities were seen there.
 */
data class TravelContinentRow(
    val continent: TravelContinent,
    val visitedCountries: Int,
    val totalCountries: Int,
    val cities: Int,
)

/** Every continent, by [continentName] A to Z ([reversed]: Z to A), visited or not. */
fun travelByContinents(
    base: List<TravelBaseCountry>,
    records: List<TravelCountryRecord>,
    continentName: (TravelContinent) -> String,
    collator: Comparator<String>,
    reversed: Boolean,
): List<TravelContinentRow> {
    val baseMap = base.associateBy { it.code }
    val all = travelAllCountryIds(base, records)
    val byId = records.associateBy { it.countryId }
    val order = compareBy<TravelContinentRow, String>(collator) { continentName(it.continent) }
    return TravelContinent.entries.map { continent ->
        val here = all.filter { travelContinentOf(it, baseMap, records) == continent }
        val visited = here.filter { byId[it]?.trips?.isNotEmpty() == true }
        TravelContinentRow(
            continent = continent,
            visitedCountries = visited.size,
            totalCountries = here.size,
            cities = distinctCities(visited.flatMap { id -> byId.getValue(id).trips.flatMap { it.cities } }).size,
        )
    }.sortedWith(if (reversed) order.reversed() else order)
}

/* ---------------- search ---------------- */

/**
 * Countries [query] finds (05.10): by any of their names, by a city seen
 * there, or by the year of a trip. Always A to Z, whatever the view.
 */
fun travelSearch(
    allCountryIds: List<String>,
    records: List<TravelCountryRecord>,
    names: TravelNames,
    collator: Comparator<String>,
    query: String,
): List<String> {
    val q = travelKey(query)
    if (q.isEmpty()) return emptyList()
    val year = q.toIntOrNull()?.takeIf { q.length == 4 }
    val byId = records.associateBy { it.countryId }
    return allCountryIds.distinct().filter { id ->
        val trips = byId[id]?.trips.orEmpty()
        names.searchNamesOf(id).any { q in travelKey(it) } ||
            trips.any { t -> t.cities.any { q in travelKey(it) } } ||
            (year != null && trips.any { it.year == year })
    }.sortedWith(compareBy(collator) { names.nameOf(it) })
}
