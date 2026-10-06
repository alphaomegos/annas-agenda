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

/**
 * One flight in the log (06.10). Times are the airports' own clocks: the
 * scheduled [departure] and [arrival], and the actual ones if the user put
 * them in. [departureOffsetMinutes] / [arrivalOffsetMinutes] are the clocks'
 * offsets from UTC when they are known for certain — an imported log gives
 * them; otherwise the airport's time zone does.
 *
 * Codes, not names: [from] and [to] are IATA airport codes, [airline] an
 * IATA (or ICAO) airline code, [aircraft] an IATA type code; names come from
 * the base and the user's own renames.
 *
 * Flights with a change between them share a [journeyId] — the id of the
 * journey's first flight — kept as data (decided 06.10), so that turning
 * grouping on or off leaves earlier flights as they were unless the user
 * says otherwise. [keptApart] is a flight the user split off the one before:
 * grouping never joins it back.
 */
data class Flight(
    val id: Long,
    val from: String,
    val to: String,
    val departure: java.time.LocalDateTime,
    val arrival: java.time.LocalDateTime,
    val departureOffsetMinutes: Int? = null,
    val arrivalOffsetMinutes: Int? = null,
    val actualDeparture: java.time.LocalDateTime? = null,
    val actualArrival: java.time.LocalDateTime? = null,
    val airline: String? = null,
    val number: String? = null,
    val aircraft: String? = null,
    val seatClass: String? = null,
    val seat: String? = null,
    val booking: String? = null,
    val note: String = "",
    val journeyId: Long? = null,
    val keptApart: Boolean = false,
)

/**
 * The flight section's settings (06.10): whether flights with a change
 * between them are grouped into one journey, whether the map shows above
 * the list, and whether the travel map draws the flights over the countries.
 */
data class FlightPrefs(
    val groupJourneys: Boolean = true,
    val showMap: Boolean = true,
    val showOnTravelMap: Boolean = false,
)
