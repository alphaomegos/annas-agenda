package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.model.FlightAirport
import com.alphaomegos.annasagenda.model.FlightBase
import com.alphaomegos.annasagenda.model.TravelCountryRecord
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The flight log's rules (06.10): when a flight leaves and lands in real
 * time, how long and how far it is, which flights make one journey, the
 * statistics, and which trips the travel section could be told about.
 * Pure: the reference data comes in as [FlightBase].
 */

/** A change of planes: the next flight leaves the same place within this long (decided 06.10). */
val FLIGHT_CHANGE_LIMIT: Duration = Duration.ofHours(24)

private const val EARTH_RADIUS_KM = 6371.0

/* ---------------- time ---------------- */

private fun zoneOffset(base: FlightBase, code: String, local: LocalDateTime): ZoneOffset? =
    base.airports[code]?.let { a -> runCatching { ZoneId.of(a.timeZone).rules.getOffset(local) }.getOrNull() }

/**
 * When the flight leaves, in real time: the actual departure if the user
 * put one in, else the scheduled one. The offset the log knows for certain
 * wins for the scheduled time; an actual time takes the airport's zone, and
 * the known offset only if the airport is not in the base.
 */
fun flightDepartsAt(f: Flight, base: FlightBase): Instant? =
    instantOf(base, f.from, f.actualDeparture, f.departure, f.departureOffsetMinutes)

/** When the flight lands, in real time; as [flightDepartsAt]. */
fun flightArrivesAt(f: Flight, base: FlightBase): Instant? =
    instantOf(base, f.to, f.actualArrival, f.arrival, f.arrivalOffsetMinutes)

private fun instantOf(base: FlightBase, code: String, actual: LocalDateTime?, scheduled: LocalDateTime, known: Int?): Instant? {
    val knownOffset = known?.let { ZoneOffset.ofTotalSeconds(it * 60) }
    return if (actual != null) {
        (zoneOffset(base, code, actual) ?: knownOffset)?.let { actual.toInstant(it) }
    } else {
        (knownOffset ?: zoneOffset(base, code, scheduled))?.let { scheduled.toInstant(it) }
    }
}

/** Minutes from leaving to landing, actual where known; null if a clock's zone is not known. */
fun flightMinutes(f: Flight, base: FlightBase): Long? {
    val dep = flightDepartsAt(f, base) ?: return null
    val arr = flightArrivesAt(f, base) ?: return null
    return Duration.between(dep, arr).toMinutes().takeIf { it >= 0 }
}

/** Whether the flight has left by [now]: a log's past and its upcoming. */
fun flightIsPast(f: Flight, base: FlightBase, now: Instant): Boolean =
    (flightDepartsAt(f, base) ?: f.departure.toInstant(ZoneOffset.UTC)) <= now

/* ---------------- distance and the arc ---------------- */

/** The great-circle distance between two airports, km. */
fun flightDistanceKm(a: FlightAirport, b: FlightAirport): Double {
    val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
    val dp = p2 - p1; val dl = Math.toRadians(b.lon - a.lon)
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
}

fun flightDistanceKm(f: Flight, base: FlightBase): Double? {
    val a = base.airports[f.from] ?: return null
    val b = base.airports[f.to] ?: return null
    return flightDistanceKm(a, b)
}

/**
 * The great circle from [a] to [b] — the way a plane goes, the arc the map
 * draws (decided 06.10) — as [segments] + 1 points, (latitude, longitude)
 * in degrees, longitude in -180..180: the map splits the arc where it
 * crosses the date line.
 */
fun flightArc(a: FlightAirport, b: FlightAirport, segments: Int = 64): List<Pair<Double, Double>> {
    fun vec(lat: Double, lon: Double): DoubleArray {
        val p = Math.toRadians(lat); val l = Math.toRadians(lon)
        return doubleArrayOf(cos(p) * cos(l), cos(p) * sin(l), sin(p))
    }
    val u = vec(a.lat, a.lon); val v = vec(b.lat, b.lon)
    val dot = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]).coerceIn(-1.0, 1.0)
    val omega = kotlin.math.acos(dot)
    if (omega < 1e-9) return listOf(a.lat to a.lon, b.lat to b.lon)
    return (0..segments).map { k ->
        val t = k.toDouble() / segments
        val s1 = sin((1 - t) * omega) / sin(omega); val s2 = sin(t * omega) / sin(omega)
        val x = s1 * u[0] + s2 * v[0]; val y = s1 * u[1] + s2 * v[1]; val z = s1 * u[2] + s2 * v[2]
        Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to Math.toDegrees(atan2(y, x))
    }
}

/* ---------------- names ---------------- */

/** The city an airport is in: the user's own name for it (06.10), else the base's, else the code. */
fun flightCityName(code: String, base: FlightBase, renamed: Map<String, String>): String =
    renamed[code]?.takeIf { it.isNotBlank() } ?: base.airports[code]?.city ?: code

/** An airline's name: the user's, else the base's, else the code. */
fun flightAirlineName(code: String, base: FlightBase, renamed: Map<String, String>): String =
    renamed[code]?.takeIf { it.isNotBlank() } ?: base.airlines[code] ?: code

/* ---------------- journeys ---------------- */

/** Flights with a change between them (06.10), in the order flown. */
data class FlightJourney(val flights: List<Flight>) {
    val first: Flight get() = flights.first()
    val last: Flight get() = flights.last()
}

private fun departureKey(f: Flight, base: FlightBase): Instant =
    flightDepartsAt(f, base) ?: f.departure.toInstant(ZoneOffset.UTC)

private fun arrivalKey(f: Flight, base: FlightBase): Instant =
    flightArrivesAt(f, base) ?: f.arrival.toInstant(ZoneOffset.UTC)

private fun samePlace(a: String, b: String, base: FlightBase): Boolean {
    if (a == b) return true
    val x = base.airports[a] ?: return false
    val y = base.airports[b] ?: return false
    // Landing at one airport of a city and leaving from another (LGW, LHR).
    return x.country == y.country && x.city.equals(y.city, ignoreCase = true)
}

/** Whether [next] is a change from [prev]: from where it landed, within a day, not kept apart. */
fun flightConnects(prev: Flight, next: Flight, base: FlightBase): Boolean {
    if (next.keptApart || prev.id == next.id || !samePlace(prev.to, next.from, base)) return false
    // Straight back where it came from is a turnaround, not a change (decided 06.10).
    if (samePlace(next.to, prev.from, base)) return false
    return withinADay(prev, next, base)
}

private fun withinADay(prev: Flight, next: Flight, base: FlightBase): Boolean {
    val gap = Duration.between(arrivalKey(prev, base), departureKey(next, base))
    return !gap.isNegative && gap <= FLIGHT_CHANGE_LIMIT
}

/**
 * Turnarounds (decided 06.10): a flight out and the next one straight back
 * to where it left from, within a day — "SVO → KRR → SVO" — as pairs (out,
 * back). Not a change of planes and not a visit; the statistics count them
 * on their own. A flight is in one pair at most.
 */
fun flightTurnarounds(flights: List<Flight>, base: FlightBase): List<Pair<Long, Long>> {
    val ordered = flights.sortedWith(compareBy({ departureKey(it, base) }, { it.id }))
    val used = HashSet<Long>()
    val pairs = mutableListOf<Pair<Long, Long>>()
    ordered.forEachIndexed { i, out ->
        if (out.id in used || samePlace(out.from, out.to, base)) return@forEachIndexed
        // The next flight after it: the one that takes the traveller on.
        val back = ordered.drop(i + 1).firstOrNull { departureKey(it, base) >= arrivalKey(out, base) } ?: return@forEachIndexed
        if (back.id !in used && samePlace(out.to, back.from, base) && samePlace(back.to, out.from, base) && withinADay(out, back, base)) {
            pairs += out.id to back.id
            used += out.id; used += back.id
        }
    }
    return pairs
}

/** The log cut into journeys, each in the order flown, the journeys by their first departure. */
fun flightJourneys(flights: List<Flight>, base: FlightBase): List<FlightJourney> {
    val sorted = flights.sortedWith(compareBy({ departureKey(it, base) }, { it.id }))
    val byJourney = LinkedHashMap<Long, MutableList<Flight>>()
    sorted.forEach { f -> byJourney.getOrPut(f.journeyId ?: -f.id - 1) { mutableListOf() } += f }
    return byJourney.values.map { FlightJourney(it) }
        .sortedWith(compareBy({ departureKey(it.first, base) }, { it.first.id }))
}

/**
 * Each journey's id made its first flight's, and a "journey" of one flight
 * no journey at all; a journey broken in the middle — a flight removed or
 * moved — is cut where its flights no longer connect.
 */
private fun normalised(flights: List<Flight>, base: FlightBase): List<Flight> {
    val newJourney = HashMap<Long, Long?>()
    flights.filter { it.journeyId != null }.groupBy { it.journeyId }.values.forEach { group ->
        val ordered = group.sortedWith(compareBy({ departureKey(it, base) }, { it.id }))
        var run = mutableListOf(ordered.first())
        fun close() {
            val id = if (run.size >= 2) run.first().id else null
            run.forEach { newJourney[it.id] = id }
        }
        for (k in 1 until ordered.size) {
            if (flightConnects(run.last(), ordered[k], base)) run += ordered[k] else { close(); run = mutableListOf(ordered[k]) }
        }
        close()
    }
    return flights.map { f -> if (f.id in newJourney && newJourney[f.id] != f.journeyId) f.copy(journeyId = newJourney[f.id]) else f }
}

/**
 * The flights [ids] joined to the journeys they connect with: the nearest
 * flight before that it changes from, if that one ends its journey, and the
 * nearest after that changes from it, if that one starts its own.
 */
fun flightsJoined(flights: List<Flight>, ids: Collection<Long>, base: FlightBase): List<Flight> {
    val parent = HashMap<Long, Long>()
    fun root(x: Long): Long { var r = x; while (parent[r] != null && parent[r] != r) r = parent.getValue(r); return r }
    fun union(a: Long, b: Long) { val ra = root(a); val rb = root(b); if (ra != rb) parent[rb] = ra }
    flights.forEach { parent[it.id] = it.id }
    flights.filter { it.journeyId != null && it.journeyId in parent }.forEach { union(it.journeyId!!, it.id) }

    val ordered = flights.sortedWith(compareBy({ departureKey(it, base) }, { it.id }))
    val journeyOf = { f: Flight -> root(f.id) }
    fun endsJourney(f: Flight) = ordered.none { it.id != f.id && journeyOf(it) == journeyOf(f) && departureKey(it, base) > departureKey(f, base) }
    fun startsJourney(f: Flight) = ordered.none { it.id != f.id && journeyOf(it) == journeyOf(f) && departureKey(it, base) < departureKey(f, base) }

    ids.toSet().forEach { id ->
        val f = ordered.firstOrNull { it.id == id } ?: return@forEach
        ordered.filter { it.id != id && journeyOf(it) != journeyOf(f) && flightConnects(it, f, base) }
            .maxByOrNull { arrivalKey(it, base) }
            ?.takeIf { endsJourney(it) && startsJourney(f) }
            ?.let { union(it.id, f.id) }
        ordered.filter { it.id != id && journeyOf(it) != journeyOf(f) && flightConnects(f, it, base) }
            .minByOrNull { departureKey(it, base) }
            ?.takeIf { startsJourney(it) && endsJourney(f) }
            ?.let { union(f.id, it.id) }
    }
    val members = flights.groupBy { root(it.id) }
    val first = members.mapValues { (_, g) -> g.minWith(compareBy({ departureKey(it, base) }, { it.id })).id }
    return flights.map { f ->
        val j = if (members.getValue(root(f.id)).size >= 2) first.getValue(root(f.id)) else null
        if (j == f.journeyId) f else f.copy(journeyId = j)
    }
}

/** Every flight grouped afresh (grouping turned on, "the earlier ones too"); kept-apart flights stay apart. */
fun flightsRegrouped(flights: List<Flight>, base: FlightBase): List<Flight> =
    flightsJoined(flights.map { if (it.journeyId == null) it else it.copy(journeyId = null) }, flights.map { it.id }, base)

/** Every journey taken apart (grouping turned off, "the earlier ones too"). */
fun flightsUngrouped(flights: List<Flight>): List<Flight> =
    flights.map { if (it.journeyId == null) it else it.copy(journeyId = null) }

/**
 * [id] split off the flight before it (06.10): it and the flights after it
 * make a journey of their own, and grouping never joins it back.
 */
fun flightSplitOff(flights: List<Flight>, id: Long, base: FlightBase): List<Flight> {
    val f = flights.firstOrNull { it.id == id } ?: return flights
    val journey = f.journeyId
    val marked = flights.map { if (it.id == id) it.copy(keptApart = true) else it }
    if (journey == null) return marked
    val at = departureKey(f, base)
    val after = marked.filter { it.journeyId == journey && (it.id == id || departureKey(it, base) > at) }.map { it.id }.toSet()
    val before = marked.filter { it.journeyId == journey && it.id !in after }
    val beforeId = before.minWithOrNull(compareBy({ departureKey(it, base) }, { it.id }))?.id
    return marked.map { g ->
        when {
            g.id in after -> g.copy(journeyId = if (after.size >= 2) id else null)
            g.journeyId == journey -> g.copy(journeyId = if (before.size >= 2) beforeId else null)
            else -> g
        }
    }
}

/** A new flight in the log, joined to its neighbours when grouping is on. */
fun flightAdded(flights: List<Flight>, flight: Flight, base: FlightBase, group: Boolean): List<Flight> {
    val all = flights + flight.copy(journeyId = null)
    return if (group) flightsJoined(all, listOf(flight.id), base) else all
}

/** Many new flights — an import — joined like one. */
fun flightsAdded(flights: List<Flight>, added: List<Flight>, base: FlightBase, group: Boolean): List<Flight> {
    val all = flights + added.map { it.copy(journeyId = null) }
    return if (group) flightsJoined(all, added.map { it.id }, base) else all
}

/** A flight removed; its journey closes up or falls in two where it no longer connects. */
fun flightRemoved(flights: List<Flight>, id: Long, base: FlightBase): List<Flight> =
    normalised(flights.filter { it.id != id }, base)

/** A flight changed: out of its journey, then back in by the rules, as if just added. */
fun flightEdited(flights: List<Flight>, flight: Flight, base: FlightBase, group: Boolean): List<Flight> {
    if (flights.none { it.id == flight.id }) return flights
    val rest = flightRemoved(flights, flight.id, base)
    val placed = rest + flight.copy(journeyId = null)
    val joined = if (group && !flight.keptApart) flightsJoined(placed, listOf(flight.id), base) else placed
    // Keep the log's order as it was.
    val order = flights.map { it.id }
    return joined.sortedBy { order.indexOf(it.id) }
}

/* ---------------- statistics ---------------- */

data class FlightCount(val key: String, val flights: Int)

data class FlightYear(val year: Int, val flights: Int, val km: Long, val minutes: Long)

/**
 * The numbers of the flights flown by [now] (06.10): how many, how far, how
 * long in the air; the airports, countries, airlines and aircraft, most
 * used first; the routes flown most, either way round; the longest flights;
 * and the same by year.
 */
data class FlightStats(
    val flights: Int,
    val km: Long,
    val minutes: Long,
    val airports: List<FlightCount>,
    val countries: List<FlightCount>,
    val airlines: List<FlightCount>,
    val aircraft: List<FlightCount>,
    val routes: List<FlightCount>,
    val longestByDistance: Flight?,
    val longestByTime: Flight?,
    val years: List<FlightYear>,
    // Turnarounds (06.10): how many, and where to, most first.
    val turnarounds: Int = 0,
    val turnaroundPlaces: List<FlightCount> = emptyList(),
    // Changes of planes that took 8 hours or more (06.10, "time killer").
    val longLayovers: Int = 0,
    // Flights that landed, by the local clocks, before they left (06.10, "time traveller").
    val timeTravels: Int = 0,
)

/** How long a change has to take to count as a long one (06.10). */
val FLIGHT_LONG_LAYOVER: Duration = Duration.ofHours(8)

/** The changes of planes among [flights] that took at least [FLIGHT_LONG_LAYOVER]. */
fun flightLongLayovers(flights: List<Flight>, base: FlightBase): Int {
    val ordered = flights.sortedWith(compareBy({ departureKey(it, base) }, { it.id }))
    return ordered.zipWithNext().count { (a, b) ->
        flightConnects(a, b, base) &&
            Duration.between(arrivalKey(a, base), departureKey(b, base)) >= FLIGHT_LONG_LAYOVER
    }
}

/** Whether [f] landed, by the clocks on the ground, before it took off — Tokyo to Los Angeles. */
fun flightIsTimeTravel(f: Flight): Boolean =
    (f.actualArrival ?: f.arrival) < (f.actualDeparture ?: f.departure)

fun flightStats(flights: List<Flight>, base: FlightBase, now: Instant): FlightStats {
    val flown = flights.filter { flightIsPast(it, base, now) }
    val turns = flightTurnarounds(flown, base)
    fun counted(keys: List<String>) = keys.groupingBy { it }.eachCount()
        .map { FlightCount(it.key, it.value) }
        .sortedWith(compareByDescending<FlightCount> { it.flights }.thenBy { it.key })
    val km = flown.associate { it.id to (flightDistanceKm(it, base) ?: 0.0) }
    val minutes = flown.associate { it.id to (flightMinutes(it, base) ?: 0L) }
    return FlightStats(
        flights = flown.size,
        km = km.values.sum().toLong(),
        minutes = minutes.values.sum(),
        airports = counted(flown.flatMap { listOf(it.from, it.to) }),
        // A country counts once per flight, not twice for a domestic one.
        countries = counted(flown.flatMap { f -> setOfNotNull(base.airports[f.from]?.country, base.airports[f.to]?.country) }),
        airlines = counted(flown.mapNotNull { it.airline?.takeIf(String::isNotBlank) }),
        aircraft = counted(flown.mapNotNull { it.aircraft?.takeIf(String::isNotBlank) }),
        routes = counted(flown.map { listOf(it.from, it.to).sorted().joinToString("–") }),
        longestByDistance = flown.maxByOrNull { km.getValue(it.id) }?.takeIf { km.getValue(it.id) > 0 },
        longestByTime = flown.maxByOrNull { minutes.getValue(it.id) }?.takeIf { minutes.getValue(it.id) > 0 },
        years = flown.groupBy { it.departure.year }.map { (year, list) ->
            FlightYear(year, list.size, list.sumOf { km.getValue(it.id) }.toLong(), list.sumOf { minutes.getValue(it.id) })
        }.sortedBy { it.year },
        turnarounds = turns.size,
        turnaroundPlaces = counted(turns.mapNotNull { (out, _) -> flown.firstOrNull { it.id == out }?.to }),
        longLayovers = flightLongLayovers(flown, base),
        timeTravels = flown.count(::flightIsTimeTravel),
    )
}

/* ---------------- what the travel section could be told ---------------- */

/**
 * A trip the flights suggest (decided 06.10: suggest, never mark by
 * itself): landing in [city] of [country] in [year]-[month] at the end of a
 * journey — a change of planes is not a visit.
 */
data class FlightTravelSuggestion(
    val country: String,
    val city: String,
    val year: Int,
    val month: Int,
    val flightIds: List<Long>,
)

/** The key a home city is kept by: country and the base's city, so a rename does not lose it. */
fun flightPlaceKey(airport: FlightAirport): String = airport.country + ":" + airport.city.lowercase()

/**
 * The trips [flights] suggest that the travel section does not have yet:
 * every journey's last landing, flown by [now], not in a home city, and not
 * already a trip to that country that month naming that city.
 */
fun flightTravelSuggestions(
    flights: List<Flight>,
    base: FlightBase,
    renamed: Map<String, String>,
    travel: List<TravelCountryRecord>,
    homes: Set<String>,
    now: Instant,
): List<FlightTravelSuggestion> {
    val found = LinkedHashMap<Triple<String, String, Pair<Int, Int>>, MutableList<Long>>()
    // A turnaround's far end was not a visit.
    val turnedAround = flightTurnarounds(flights, base).map { it.first }.toSet()
    flightJourneys(flights, base).forEach { journey ->
        val f = journey.last
        if (f.id in turnedAround) return@forEach
        if (!flightIsPast(f, base, now)) return@forEach
        val airport = base.airports[f.to] ?: return@forEach
        if (flightPlaceKey(airport) in homes) return@forEach
        val day = (f.actualArrival ?: f.arrival).toLocalDate()
        val city = flightCityName(f.to, base, renamed)
        val record = travel.firstOrNull { it.countryId == airport.country }
        val known = record?.trips.orEmpty().any { t ->
            t.year == day.year && t.month == day.monthValue && t.cities.any { it.equals(city, ignoreCase = true) }
        }
        if (!known) found.getOrPut(Triple(airport.country, city, day.year to day.monthValue)) { mutableListOf() } += f.id
    }
    return found.map { (k, ids) -> FlightTravelSuggestion(k.first, k.second, k.third.first, k.third.second, ids) }
}
