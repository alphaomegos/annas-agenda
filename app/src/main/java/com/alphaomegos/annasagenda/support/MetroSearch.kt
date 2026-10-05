package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * A station to pick while typing "from" or "to": its name, its line and
 * whether it is closed, so the list can show it grey.
 *
 * One choice per station (06.10): "Печатники" on lines 10, 11 and D2 is three
 * choices, because which of them is meant changes the route — getting to
 * line 10's from line 11's is a walk of its own. [lineIds] holds the one line.
 */
data class MetroNameChoice(
    val name: String,
    val lineIds: List<Long>,
    val allClosed: Boolean,
    val stationId: Long? = null,
)

/** Case, "ё" and runs of spaces do not matter when a name is typed. */
fun metroNameKey(name: String): String =
    name.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/**
 * The stations [typed] could stand for, best first: names that start with
 * it, then names with a word that starts with it, then names that merely
 * contain it; alphabetical within each, and one name's stations in the
 * scheme's order of lines. Nothing for nothing typed, and nothing once the
 * text is exactly the name of a single station; the name of several still
 * offers those, so the one meant can be picked.
 */
fun metroStationSuggestions(scheme: MetroScheme, typed: String, limit: Int = 8): List<MetroNameChoice> {
    val key = metroNameKey(typed)
    if (key.isEmpty()) return emptyList()

    val byName = scheme.stations.groupBy { metroNameKey(it.name) }
    val exact = byName[key]
    if (exact != null && exact.size == 1) return emptyList()

    val lineOrder = scheme.lines.mapIndexed { i, l -> l.id to i }.toMap()
    return byName.entries
        .mapNotNull { (nameKey, stations) ->
            val rank = when {
                exact != null -> if (nameKey == key) 0 else return@mapNotNull null
                nameKey.startsWith(key) -> 0
                nameKey.split(' ', '-').any { it.startsWith(key) } -> 1
                key in nameKey -> 2
                else -> return@mapNotNull null
            }
            rank to stations
        }
        .sortedWith(compareBy({ it.first }, { metroNameKey(it.second.first().name) }))
        .flatMap { (_, stations) ->
            stations.sortedWith(compareBy({ lineOrder[it.lineId] ?: Int.MAX_VALUE }, { it.id })).map {
                MetroNameChoice(name = it.name, lineIds = listOf(it.lineId), allClosed = it.closure != null, stationId = it.id)
            }
        }
        .take(limit)
}

/**
 * The stations a field stands for (06.10): the one [picked] from the list or
 * on the picture, while the text still names it; otherwise every station of
 * the name typed. A picked station that is closed gives way to the others of
 * its name, as decided 04.10: the route goes through one that is open.
 */
fun metroStationsForField(scheme: MetroScheme, typed: String, picked: Long?): Set<Long> {
    val named = metroStationsForName(scheme, typed)
    if (picked == null || picked !in named) return named
    val station = scheme.stations.first { it.id == picked }
    if (station.closure != null && named.any { id -> scheme.stations.any { it.id == id && it.closure == null } }) return named
    return setOf(picked)
}

/**
 * The stations a typed name stands for, matched as [metroNameKey] matches —
 * so "лубянка" and "Лубянка " find the same station the list offered.
 */
fun metroStationsForName(scheme: MetroScheme, typed: String): Set<Long> {
    val key = metroNameKey(typed)
    if (key.isEmpty()) return emptySet()
    return scheme.stations.filter { metroNameKey(it.name) == key }.mapTo(LinkedHashSet()) { it.id }
}
