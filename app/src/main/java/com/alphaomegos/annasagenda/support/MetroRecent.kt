package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroRecentRoute

/*
 * The routes looked up lately (15.10): written down a moment after a route is
 * found, listed under the empty fields of that scheme, the newest first and
 * the pinned ones above them all.
 */

/** How many unpinned routes are kept for each scheme (15.10). */
const val METRO_RECENT_KEPT = 6

/** How long a route has to stay found before it is written down: a name being typed is not looked up yet. */
const val METRO_RECENT_SETTLE_MILLIS = 2500L

/** The key a scheme's recent routes are kept under: its selection text. */
fun metroRecentKey(shown: MetroShown): String = metroSelectionText(
    shown.libraryKey?.let { MetroSelection.Library(it) } ?: MetroSelection.User(shown.scheme.id),
)

/** Where [route] starts and where it ends; null for one that goes nowhere. */
fun metroRouteEnds(route: MetroRoute): Pair<Long, Long>? {
    val first = when (val s = route.steps.firstOrNull() ?: return null) {
        is MetroRouteStep.Ride -> s.stationIds.firstOrNull()
        is MetroRouteStep.Change -> s.fromStationId
    } ?: return null
    val last = when (val s = route.steps.last()) {
        is MetroRouteStep.Ride -> s.stationIds.lastOrNull()
        is MetroRouteStep.Change -> s.toStationId
    } ?: return null
    return if (first == last) null else first to last
}

private fun MetroRecentRoute.isOf(schemeKey: String, from: Long, to: Long) =
    this.schemeKey == schemeKey && fromStationId == from && toStationId == to

/**
 * [list] with [from] → [to] on [schemeKey] looked up at [at]: at the front,
 * once, still pinned if it was; of that scheme's unpinned ones only the
 * newest [METRO_RECENT_KEPT] stay. The other way round is a route of its own.
 */
fun metroRecentRemembered(
    list: List<MetroRecentRoute>,
    schemeKey: String,
    from: Long,
    to: Long,
    at: Long,
): List<MetroRecentRoute> {
    val was = list.firstOrNull { it.isOf(schemeKey, from, to) }
    val entry = MetroRecentRoute(schemeKey, from, to, at, pinned = was?.pinned ?: false)
    var unpinned = 0
    return (listOf(entry) + list.filterNot { it.isOf(schemeKey, from, to) }).filter { r ->
        r.schemeKey != schemeKey || r.pinned || ++unpinned <= METRO_RECENT_KEPT
    }
}

/** [list] with [from] → [to] on [schemeKey] pinned, or let go of — when unpinned it is just recent again. */
fun metroRecentPinned(list: List<MetroRecentRoute>, schemeKey: String, from: Long, to: Long, pinned: Boolean): List<MetroRecentRoute> =
    list.map { if (it.isOf(schemeKey, from, to)) it.copy(pinned = pinned) else it }

/** [list] with the recent routes of [schemeKey] cleared; the pinned ones stay. */
fun metroRecentCleared(list: List<MetroRecentRoute>, schemeKey: String): List<MetroRecentRoute> =
    list.filter { it.schemeKey != schemeKey || it.pinned }

/** [list] without anything of [schemeKey] — the scheme is gone. */
fun metroRecentWithout(list: List<MetroRecentRoute>, schemeKey: String): List<MetroRecentRoute> =
    list.filter { it.schemeKey != schemeKey }

/** The routes of [schemeKey] to list: the pinned first, then the newest first. */
fun metroRecentOf(list: List<MetroRecentRoute>, schemeKey: String): List<MetroRecentRoute> =
    list.filter { it.schemeKey == schemeKey }
        .sortedWith(compareByDescending<MetroRecentRoute> { it.pinned }.thenByDescending { it.atEpochMillis })
