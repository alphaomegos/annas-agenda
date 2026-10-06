package com.alphaomegos.annasagenda.support

/**
 * Achievements (06.10, Eduard's list): what the flight log has earned — a
 * number from the statistics and the goal for it. A [repeatable] one counts
 * how many times over it was earned: round the Earth twice is "×2". The
 * countries flown to are badges of their own (flightCountriesFlown).
 *
 * Each has a picture, assets/achievements/<id>.png (made elsewhere; the
 * trophy stands in until it is there), and a line of text to go with it.
 */
data class FlightAchievement(val id: String, val value: Long, val goal: Long, val repeatable: Boolean = false) {
    val taken: Boolean get() = value >= goal
    /** How many times over: 0 not yet, 1 earned, more for a repeatable one earned again. */
    val times: Long get() = when {
        !taken -> 0
        repeatable -> value / goal
        else -> 1
    }
}

/** Kilometres round the Equator, and to the Moon. */
const val FLIGHT_EARTH_KM = 40_075L
const val FLIGHT_MOON_KM = 384_400L

private const val DAY_MIN = 24L * 60

fun flightAchievements(stats: FlightStats): List<FlightAchievement> {
    val flights = stats.flights.toLong()
    return listOf(
        FlightAchievement("first_flight", flights, 1),
        FlightAchievement("flights_100", flights, 100),
        FlightAchievement("flights_1000", flights, 1000),
        FlightAchievement("around_the_earth", stats.km, FLIGHT_EARTH_KM, repeatable = true),
        FlightAchievement("to_the_moon", stats.km, FLIGHT_MOON_KM, repeatable = true),
        FlightAchievement("day_in_the_air", stats.minutes, DAY_MIN, repeatable = true),
        FlightAchievement("week_in_the_air", stats.minutes, 7 * DAY_MIN, repeatable = true),
        FlightAchievement("month_in_the_air", stats.minutes, 30 * DAY_MIN, repeatable = true),
        FlightAchievement("year_in_the_air", stats.minutes, 365 * DAY_MIN, repeatable = true),
        FlightAchievement("countries_10", stats.countries.size.toLong(), 10),
        FlightAchievement("countries_50", stats.countries.size.toLong(), 50),
        FlightAchievement("airlines_10", stats.airlines.size.toLong(), 10),
        FlightAchievement("aircraft_10", stats.aircraft.size.toLong(), 10),
        FlightAchievement("airports_10", stats.airports.size.toLong(), 10),
        FlightAchievement("airports_100", stats.airports.size.toLong(), 100),
        FlightAchievement("airports_1000", stats.airports.size.toLong(), 1000),
        FlightAchievement("time_killer", stats.longLayovers.toLong(), 1, repeatable = true),
        FlightAchievement("time_traveller", stats.timeTravels.toLong(), 1, repeatable = true),
        FlightAchievement("flight_attendant", stats.turnarounds.toLong(), 1, repeatable = true),
    )
}

/** The countries a flight flown left from or landed in. */
fun flightCountriesFlown(stats: FlightStats): Set<String> = stats.countries.map { it.key }.toSet()
