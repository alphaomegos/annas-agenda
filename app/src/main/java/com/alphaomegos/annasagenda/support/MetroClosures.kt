package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.model.MetroClosure
import com.alphaomegos.annasagenda.model.MetroScheme
import java.time.LocalDate

/**
 * Temporarily closed stations, transfers and exits (04.10).
 *
 * Closing and opening are edits like any other. The expected opening day is
 * a reminder, not a switch: on that day, and every day after until
 * answered, the metro screen asks whether it has opened or the date moves
 * (decided 04.10). Nothing opens by itself, because the date is a guess and
 * a station shown open while it is still shut sends somebody to a locked
 * door.
 */
sealed interface MetroClosable {
    data class Station(val id: Long) : MetroClosable
    data class Transfer(val id: Long) : MetroClosable
    data class Exit(val id: Long) : MetroClosable
}

/** The closure of [target], or null when it is open or not there. */
fun metroClosureOf(scheme: MetroScheme, target: MetroClosable): MetroClosure? = when (target) {
    is MetroClosable.Station -> scheme.stations.firstOrNull { it.id == target.id }?.closure
    is MetroClosable.Transfer -> scheme.transfers.firstOrNull { it.id == target.id }?.closure
    is MetroClosable.Exit -> scheme.exits.firstOrNull { it.id == target.id }?.closure
}

/**
 * [target] closed with [closure], or opened when it is null. The same scheme
 * comes back when nothing changes, or [target] is not there.
 */
fun metroClosureSet(scheme: MetroScheme, target: MetroClosable, closure: MetroClosure?): MetroScheme = when (target) {
    is MetroClosable.Station -> {
        val old = scheme.stations.firstOrNull { it.id == target.id }
        if (old == null || old.closure == closure) scheme
        else scheme.copy(stations = scheme.stations.map { if (it.id == target.id) it.copy(closure = closure) else it })
    }
    is MetroClosable.Transfer -> {
        val old = scheme.transfers.firstOrNull { it.id == target.id }
        if (old == null || old.closure == closure) scheme
        else scheme.copy(transfers = scheme.transfers.map { if (it.id == target.id) it.copy(closure = closure) else it })
    }
    is MetroClosable.Exit -> {
        val old = scheme.exits.firstOrNull { it.id == target.id }
        if (old == null || old.closure == closure) scheme
        else scheme.copy(exits = scheme.exits.map { if (it.id == target.id) it.copy(closure = closure) else it })
    }
}

/** Something whose expected opening day has come: what it is, and that day. */
data class MetroClosureDue(val target: MetroClosable, val expectedOpening: LocalDate)

/**
 * What to ask about on [today]: every closure whose expected opening is today
 * or already past, the longest overdue first; stations, then transfers, then
 * exits on the same day. A closure with no date is never asked about — the
 * user said they do not know.
 */
fun metroClosuresDue(scheme: MetroScheme, today: LocalDate): List<MetroClosureDue> {
    val due = mutableListOf<MetroClosureDue>()
    fun add(target: MetroClosable, closure: MetroClosure?) {
        val day = closure?.expectedOpening ?: return
        if (!day.isAfter(today)) due += MetroClosureDue(target, day)
    }
    scheme.stations.forEach { add(MetroClosable.Station(it.id), it.closure) }
    scheme.transfers.forEach { add(MetroClosable.Transfer(it.id), it.closure) }
    scheme.exits.forEach { add(MetroClosable.Exit(it.id), it.closure) }
    return due.sortedBy { it.expectedOpening } // stable: the kinds keep their order
}

/** Of [stationIds], the ones that are closed — for "Лубянка is closed until 20.10" before routing. */
fun metroClosedStations(scheme: MetroScheme, stationIds: Set<Long>): List<Long> =
    scheme.stations.filter { it.id in stationIds && it.closure != null }.map { it.id }
