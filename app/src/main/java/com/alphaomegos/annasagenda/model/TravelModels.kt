package com.alphaomegos.annasagenda.model

/**
 * The continents the travel section counts by (05.10). Stored by name, like
 * every enum here: renaming a constant is a change to stored data.
 */
enum class TravelContinent {
    AFRICA,
    ANTARCTICA,
    ASIA,
    EUROPE,
    NORTH_AMERICA,
    OCEANIA,
    SOUTH_AMERICA,
}

/**
 * A country of the shipped base (assets/travel/countries.tsv): its ISO 3166-1
 * alpha-2 [code], the continent it is counted on unless the user says
 * otherwise, and its English name. Other languages take the name the system
 * gives the code (decided 05.10); the English one is the fallback.
 */
data class TravelBaseCountry(
    val code: String,
    val continent: TravelContinent,
    val englishName: String,
)

/**
 * One trip to a country: a month — year and month, always (05.10) — and the
 * cities seen, in the order they were typed, none if none were.
 */
data class TravelTrip(
    val id: Long,
    val year: Int,
    val month: Int,
    val cities: List<String> = emptyList(),
)

/** A place on the world map's grid (assets/travel/world_map.txt), for a country of the user's own. */
data class TravelMapPoint(val x: Float, val y: Float)

/**
 * What the user keeps about one country: its trips, the continent if they
 * moved it, and — for a country of their own, "terra incognito" (05.10) —
 * its name and where it is on the map.
 *
 * [countryId] is the ISO code of a base country, or "user:<n>" for one of
 * the user's own; see [isUserCountry]. A base country with nothing kept has
 * no record at all.
 */
data class TravelCountryRecord(
    val countryId: String,
    val trips: List<TravelTrip> = emptyList(),
    val continentOverride: TravelContinent? = null,
    val customName: String? = null,
    val customPoint: TravelMapPoint? = null,
) {
    val isUserCountry: Boolean get() = countryId.startsWith(TRAVEL_USER_COUNTRY_PREFIX)
}

const val TRAVEL_USER_COUNTRY_PREFIX = "user:"

/** The four ways the travel list can be cut (05.10). */
enum class TravelView { YEARS, COUNTRIES, CITIES, CONTINENTS }

/**
 * How the travel screen was left: the view, whether its order is turned
 * round from the natural one — newest year first, A to Z — and, for the
 * view by countries, all of them or only those with a trip.
 */
data class TravelViewPrefs(
    val view: TravelView = TravelView.YEARS,
    val reversed: Boolean = false,
    val onlyMine: Boolean = true,
)
