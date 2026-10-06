package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.model.FlightBase
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Reading the data export of App in the Air (06.10): the app is gone, its
 * users kept the text file it gave them, and the flight log takes it in.
 *
 * The file has sections — user, devices, accounts, settings, trips — and
 * each trip a "flights:" block whose lines are 17 fields joined by ";":
 *
 *  0 class   1 seat   2 booking reference   3–5 unused   6 how it was added
 *  7 airline code ("*" after it is a mark of AITA's, dropped)   8 number
 *  9 aircraft type   10 from   11 to
 *  12 / 13 departure / arrival, UTC   14 / 15 the same, local
 *  16 when it was added
 *
 * Only scheduled times are in it: the actual ones AITA showed are not.
 * UTC and local together give each clock's offset that day, kept so the
 * time in the air is exact. "None" and empty mean nothing.
 */
data class AitaFlight(
    val line: Int,
    val from: String,
    val to: String,
    val departure: LocalDateTime,
    val arrival: LocalDateTime,
    val departureOffsetMinutes: Int,
    val arrivalOffsetMinutes: Int,
    val airline: String?,
    val number: String?,
    val aircraft: String?,
    val seatClass: String?,
    val seat: String?,
    val booking: String?,
)

data class AitaParse(val flights: List<AitaFlight>, val problems: List<String>) {
    /** Whether the text looked like an AITA export at all, so the screen can say "not this kind of file". */
    val recognised: Boolean get() = flights.isNotEmpty()
}

private val AIRPORT = Regex("[A-Z]{3}")

private fun value(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && it != "None" }

private fun time(s: String?): LocalDateTime? =
    value(s)?.let { runCatching { LocalDateTime.parse(it).truncatedTo(ChronoUnit.MINUTES) }.getOrNull() }

fun parseAitaExport(text: String): AitaParse {
    val flights = mutableListOf<AitaFlight>()
    val problems = mutableListOf<String>()
    var inFlights = false
    text.lineSequence().forEachIndexed { i, raw ->
        val line = raw.trimEnd('\r')
        when {
            line == "flights:" -> { inFlights = true; return@forEachIndexed }
            line.endsWith(":") && !line.contains(';') -> { inFlights = false; return@forEachIndexed }
            line.startsWith("Ownership.") -> { inFlights = false; return@forEachIndexed }
            !inFlights || line.isBlank() -> return@forEachIndexed
        }
        val f = line.split(';')
        val from = value(f.getOrNull(10)); val to = value(f.getOrNull(11))
        val depUtc = time(f.getOrNull(12)); val arrUtc = time(f.getOrNull(13))
        val dep = time(f.getOrNull(14)); val arr = time(f.getOrNull(15))
        if (f.size != 17 || from == null || to == null || !from.matches(AIRPORT) || !to.matches(AIRPORT) ||
            depUtc == null || arrUtc == null || dep == null || arr == null
        ) {
            problems += "line ${i + 1}: $line"
            return@forEachIndexed
        }
        flights += AitaFlight(
            line = i + 1,
            from = from, to = to,
            departure = dep, arrival = arr,
            departureOffsetMinutes = Duration.between(depUtc, dep).toMinutes().toInt(),
            arrivalOffsetMinutes = Duration.between(arrUtc, arr).toMinutes().toInt(),
            airline = value(f[7])?.trimEnd('*')?.uppercase(),
            number = value(f[8]),
            aircraft = value(f[9])?.uppercase(),
            seatClass = value(f[0]),
            seat = value(f[1]),
            booking = value(f[2]),
        )
    }
    return AitaParse(flights, problems)
}

/**
 * What an import would do: the [flights] to add, with ids from [nextId];
 * how many are already in the log — the same route leaving at the same
 * minute — and the airport codes the base does not know (kept as codes).
 */
data class FlightImportPlan(
    val flights: List<Flight>,
    val alreadyThere: Int,
    val unknownAirports: Set<String>,
    val problems: List<String>,
)

fun flightImportPlan(parse: AitaParse, existing: List<Flight>, base: FlightBase, nextId: () -> Long): FlightImportPlan {
    fun key(from: String, to: String, dep: LocalDateTime) = "$from-$to-$dep"
    val seen = existing.mapTo(HashSet()) { key(it.from, it.to, it.departure) }
    var already = 0
    val added = mutableListOf<Flight>()
    parse.flights.sortedBy { it.departure.minusMinutes(it.departureOffsetMinutes.toLong()) }.forEach { a ->
        if (!seen.add(key(a.from, a.to, a.departure))) { already++; return@forEach }
        added += Flight(
            id = nextId(),
            from = a.from, to = a.to,
            departure = a.departure, arrival = a.arrival,
            departureOffsetMinutes = a.departureOffsetMinutes, arrivalOffsetMinutes = a.arrivalOffsetMinutes,
            airline = a.airline, number = a.number, aircraft = a.aircraft,
            seatClass = a.seatClass, seat = a.seat, booking = a.booking,
        )
    }
    val unknown = added.flatMap { listOf(it.from, it.to) }.filter { it !in base.airports }.toSortedSet()
    return FlightImportPlan(added, already, unknown, parse.problems)
}
