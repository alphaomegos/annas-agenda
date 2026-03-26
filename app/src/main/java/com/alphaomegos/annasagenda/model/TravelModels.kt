package com.alphaomegos.annasagenda

data class TravelCountrySeed(
    val countryId: String,
    val names: Map<String, String> = emptyMap(),
    val defaultContinent: TravelContinent,
    val mapShapeId: String? = null,
    val centroidX: Float? = null,
    val centroidY: Float? = null,
    val aliases: List<String> = emptyList(),
)

enum class TravelContinent {
    AFRICA,
    ANTARCTICA,
    ASIA,
    EUROPE,
    NORTH_AMERICA,
    OCEANIA,
    SOUTH_AMERICA,
}

enum class TravelViewMode {
    YEARS,
    COUNTRIES,
    CITIES,
    CONTINENTS,
}

enum class TravelSortOrder {
    ASC,
    DESC,
}

enum class TravelCountryFilter {
    ALL,
    MINE,
}

data class TravelVisit(
    val year: Int,
    val month: Int,
    val cities: List<String> = emptyList(),
)

data class TravelMapPoint(
    val x: Float,
    val y: Float,
)

data class TravelCountryRecord(
    val countryId: String,
    val trips: List<TravelVisit> = emptyList(),
    val customName: String? = null,
    val continentOverride: TravelContinent? = null,
    val customMapPoint: TravelMapPoint? = null,
    val isUserCreated: Boolean = false,
)