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
