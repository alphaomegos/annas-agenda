package com.alphaomegos.annasagenda.model

/**
 * An airport of the shipped base (assets/flights/airports.tsv, 06.10): its
 * IATA [code], where it is, the IANA time zone its clocks keep, and the
 * city and name in English — the user may rename the city (decided 06.10),
 * which is kept apart, by code.
 */
data class FlightAirport(
    val code: String,
    val icao: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val timeZone: String,
    val city: String,
    val name: String,
)

/**
 * The shipped reference data for flights: airports by IATA code, airline
 * names by IATA (or, where old logs used it, ICAO) code, aircraft names by
 * IATA type code.
 */
data class FlightBase(
    val airports: Map<String, FlightAirport> = emptyMap(),
    val airlines: Map<String, String> = emptyMap(),
    val aircraft: Map<String, String> = emptyMap(),
)
