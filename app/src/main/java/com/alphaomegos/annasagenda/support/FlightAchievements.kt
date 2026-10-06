package com.alphaomegos.annasagenda.support

/**
 * Achievements (06.10): what the flight log has earned. Eduard will name
 * the real set later; these milestones hold the page's place until then and
 * show how one is made — a number from the statistics and the goal for it.
 * The countries flown to are badges of their own (flightCountriesFlown).
 */
data class FlightAchievement(val id: String, val value: Long, val goal: Long) {
    val taken: Boolean get() = value >= goal
}

/** Kilometres round the Equator, and to the Moon. */
const val FLIGHT_EARTH_KM = 40_075L
const val FLIGHT_MOON_KM = 384_400L

fun flightAchievements(stats: FlightStats): List<FlightAchievement> = listOf(
    FlightAchievement("first_flight", stats.flights.toLong(), 1),
    FlightAchievement("flights_100", stats.flights.toLong(), 100),
    FlightAchievement("flights_500", stats.flights.toLong(), 500),
    FlightAchievement("flights_1000", stats.flights.toLong(), 1000),
    FlightAchievement("around_the_earth", stats.km, FLIGHT_EARTH_KM),
    FlightAchievement("to_the_moon", stats.km, FLIGHT_MOON_KM),
    FlightAchievement("day_in_the_air", stats.minutes, 24 * 60),
    FlightAchievement("week_in_the_air", stats.minutes, 7 * 24 * 60),
    FlightAchievement("countries_10", stats.countries.size.toLong(), 10),
    FlightAchievement("countries_50", stats.countries.size.toLong(), 50),
    FlightAchievement("airlines_10", stats.airlines.size.toLong(), 10),
    FlightAchievement("turnarounds_10", stats.turnarounds.toLong(), 10),
)

/** The countries a flight flown left from or landed in. */
fun flightCountriesFlown(stats: FlightStats): Set<String> = stats.countries.map { it.key }.toSet()
