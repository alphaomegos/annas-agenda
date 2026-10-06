package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.Flight
import com.alphaomegos.annasagenda.model.FlightBase
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * The flights on the world map (06.10): the travel map's own projection,
 * and every route flown as one great-circle arc, however often it was flown.
 */

// Robinson, as tools/travel/make_travel_assets.py projects the outlines: latitude every 5° -> (X, Y).
private val ROBINSON = arrayOf(
    1.0000 to 0.0000, 0.9986 to 0.0620, 0.9954 to 0.1240, 0.9900 to 0.1860,
    0.9822 to 0.2480, 0.9730 to 0.3100, 0.9600 to 0.3720, 0.9427 to 0.4340,
    0.9216 to 0.4958, 0.8962 to 0.5571, 0.8679 to 0.6176, 0.8350 to 0.6769,
    0.7986 to 0.7346, 0.7597 to 0.7903, 0.7186 to 0.8435, 0.6732 to 0.8936,
    0.6213 to 0.9394, 0.5722 to 0.9761, 0.5322 to 1.0000,
)

/** Where ([lat], [lon]) falls on the travel map's grid of [width] — the generator's own formula. */
fun travelGridPoint(lat: Double, lon: Double, width: Int): Pair<Float, Float> {
    val halfH = 0.5072 * width / 2
    val a = min(abs(lat), 90.0) / 5.0
    val i = min(a.toInt(), 17)
    val f = a - i
    val x = ROBINSON[i].first + (ROBINSON[i + 1].first - ROBINSON[i].first) * f
    val y = ROBINSON[i].second + (ROBINSON[i + 1].second - ROBINSON[i].second) * f
    val px = width / 2.0 + x * (lon / 180.0) * (width / 2.0)
    val py = halfH - sign(lat) * y * halfH
    return px.toFloat() to py.toFloat()
}

/**
 * One route on the map: the two airports either way round, how many
 * flights flew it and which, and its arc on the grid as polylines
 * [x0, y0, x1, y1, …] — more than one where it crosses the date line.
 */
data class FlightMapRoute(
    val a: String,
    val b: String,
    val flightIds: List<Long>,
    val polylines: List<FloatArray>,
)

/** Every route of [flights] whose airports the base knows, the most flown last, so it is drawn on top. */
fun flightMapRoutes(flights: List<Flight>, base: FlightBase, width: Int): List<FlightMapRoute> =
    flights.groupBy { listOf(it.from, it.to).sorted() }
        .mapNotNull { (pair, list) ->
            val a = base.airports[pair[0]] ?: return@mapNotNull null
            val b = base.airports[pair[1]] ?: return@mapNotNull null
            if (a.code == b.code) return@mapNotNull null
            val arc = flightArc(a, b)
            val lines = mutableListOf<FloatArray>()
            var run = mutableListOf<Float>()
            arc.forEachIndexed { k, (lat, lon) ->
                if (k > 0 && abs(lon - arc[k - 1].second) > 180) {
                    if (run.size >= 4) lines += run.toFloatArray()
                    run = mutableListOf()
                }
                val (x, y) = travelGridPoint(lat, lon, width)
                run += x; run += y
            }
            if (run.size >= 4) lines += run.toFloatArray()
            FlightMapRoute(a.code, b.code, list.map { it.id }, lines)
        }
        .sortedWith(compareBy({ it.flightIds.size }, { it.a }, { it.b }))

/**
 * Airports as the edit screen offers them for what is typed (06.10): the
 * code itself first, then codes starting with it, then cities — the user's
 * name or the base's — and airport names starting with it, then anything
 * containing it; airports in [preferred] (the ones already flown) first in
 * each kind.
 */
fun flightAirportSuggestions(
    base: FlightBase,
    renamed: Map<String, String>,
    typed: String,
    preferred: Set<String> = emptySet(),
    limit: Int = 6,
): List<String> {
    val q = typed.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val code = q.uppercase()
    return base.airports.values.mapNotNull { a ->
        val city = flightCityName(a.code, base, renamed).lowercase()
        val name = a.name.lowercase()
        val rank = when {
            a.code == code -> 0
            a.code.startsWith(code) -> 1
            city.startsWith(q) || a.city.lowercase().startsWith(q) -> 2
            name.startsWith(q) -> 3
            q.length >= 3 && (city.contains(q) || name.contains(q)) -> 4
            else -> return@mapNotNull null
        }
        Triple(rank, if (a.code in preferred) 0 else 1, a.code)
    }
        .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        .take(limit)
        .map { it.third }
}

/** "🇷🇸": a country's flag from its ISO code; nothing for a code that is not two letters. */
fun flightFlag(country: String): String {
    if (country.length != 2 || !country.all { it in 'A'..'Z' }) return ""
    return country.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
