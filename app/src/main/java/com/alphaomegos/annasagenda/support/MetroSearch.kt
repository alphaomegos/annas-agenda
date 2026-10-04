package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroScheme

/**
 * A station name to pick while typing "from" or "to": the name, the lines it
 * is on — "Каховская" is on two — and whether every station of that name is
 * closed, so the list can show it grey.
 */
data class MetroNameChoice(
    val name: String,
    val lineIds: List<Long>,
    val allClosed: Boolean,
)

/** Case, "ё" and runs of spaces do not matter when a name is typed. */
fun metroNameKey(name: String): String =
    name.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/**
 * The names [typed] could stand for, best first: names that start with it,
 * then names with a word that starts with it, then names that merely contain
 * it; alphabetical within each. Nothing for nothing typed, and nothing once
 * the text is exactly a name — the field is filled, the list should go.
 */
fun metroStationSuggestions(scheme: MetroScheme, typed: String, limit: Int = 8): List<MetroNameChoice> {
    val key = metroNameKey(typed)
    if (key.isEmpty()) return emptyList()

    val byName = scheme.stations.groupBy { metroNameKey(it.name) }
    if (key in byName) return emptyList()

    val lineOrder = scheme.lines.mapIndexed { i, l -> l.id to i }.toMap()
    return byName.entries
        .mapNotNull { (nameKey, stations) ->
            val rank = when {
                nameKey.startsWith(key) -> 0
                nameKey.split(' ', '-').any { it.startsWith(key) } -> 1
                key in nameKey -> 2
                else -> return@mapNotNull null
            }
            rank to MetroNameChoice(
                name = stations.first().name,
                lineIds = stations.map { it.lineId }.distinct().sortedBy { lineOrder[it] ?: Int.MAX_VALUE },
                allClosed = stations.all { it.closure != null },
            )
        }
        .sortedWith(compareBy({ it.first }, { metroNameKey(it.second.name) }))
        .take(limit)
        .map { it.second }
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
