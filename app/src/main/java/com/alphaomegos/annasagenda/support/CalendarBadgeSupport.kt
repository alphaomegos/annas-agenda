package com.alphaomegos.annasagenda.support

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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

/**
 * How many things a calendar day holds: its tasks and their subtasks, a
 * repeat's template deleted for its own day not counted (it is not shown).
 * Days with nothing are not in the map.
 */
fun calendarItemCountsByDate(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
): Map<LocalDate, Int> {
    val visible = tasks.filterNot { isSuppressedTemplateTaskOnItsDate(it, suppressedRecurrences) }
    val dateOf = visible.mapNotNull { t -> t.date?.let { t.id to it } }.toMap()
    val out = mutableMapOf<LocalDate, Int>()
    dateOf.values.forEach { d -> out[d] = (out[d] ?: 0) + 1 }
    subtasks.forEach { st -> dateOf[st.taskId]?.let { d -> out[d] = (out[d] ?: 0) + 1 } }
    return out
}

/** The tasks waiting for "someday": no day, and not a hidden template. */
fun somedayTaskCount(tasks: List<Task>, suppressedRecurrences: Set<String>): Int =
    tasks.count { it.date == null && !isSuppressedTemplateTaskOnItsDate(it, suppressedRecurrences) }
