package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * The marks each day of the calendar carries, of those the user switched on.
 *
 * - ANTHROPOMETRY: a measurement with any value was taken that day — the
 *   circle the calendar always drew.
 * - FOOD: something was written down as eaten, diet ticks included.
 * - DEBTS: a task of that day is still not done, by the one definition of a
 *   debt there is (undoneDebt), with no horizon: the calendar shows the month
 *   it is looking at, however old.
 *
 * Days without a mark are not in the map.
 */
fun calendarBadgesByDate(
    enabled: Set<CalendarBadge>,
    anthropometry: List<AnthropometryEntry>,
    foodLog: List<FoodEntry>,
    tasks: List<Task>,
    suppressedRecurrences: Set<String>,
    today: LocalDate,
): Map<LocalDate, Set<CalendarBadge>> {
    if (enabled.isEmpty()) return emptyMap()
    val out = mutableMapOf<LocalDate, MutableSet<CalendarBadge>>()
    fun mark(date: LocalDate, badge: CalendarBadge) {
        out.getOrPut(date) { mutableSetOf() } += badge
    }

    if (CalendarBadge.ANTHROPOMETRY in enabled) {
        anthropometry.filter { it.hasAnyValue() }.forEach { mark(it.date, CalendarBadge.ANTHROPOMETRY) }
    }
    if (CalendarBadge.FOOD in enabled) {
        foodLog.forEach { mark(it.date, CalendarBadge.FOOD) }
    }
    if (CalendarBadge.DEBTS in enabled) {
        undoneDebt(tasks, suppressedRecurrences, today, UNDONE_HORIZON_UNLIMITED)
            .dates.forEach { mark(it, CalendarBadge.DEBTS) }
    }
    return out
}
