package com.alphaomegos.annasagenda

import com.alphaomegos.annasagenda.util.isSuppressedTemplateTaskOnItsDate
import java.time.LocalDate

/**
 * The single definition of "an undone debt".
 *
 * It used to exist twice, spelled differently: UndoneTasksScreen excluded
 * repeating templates that had been deleted for their own day, and the lamp in
 * the main menu did not. A deleted occurrence therefore lit the lamp red while
 * the screen it pointed at was empty.
 *
 * Pure, so both callers can share it and so the rules can be pinned by JVM
 * tests instead of being restated in two places.
 */

/** Horizon value meaning "look back as far as there is data". */
const val UNDONE_HORIZON_UNLIMITED = 0

/** What a fresh install looks back by default. */
const val DEFAULT_UNDONE_HORIZON_DAYS = 30

/** Horizons the UI offers. [UNDONE_HORIZON_UNLIMITED] must stay last. */
val UNDONE_HORIZON_CHOICES = listOf(7, 30, 90, 365, UNDONE_HORIZON_UNLIMITED)

fun normalizeUndoneHorizonDays(days: Int): Int =
    if (days < 0) DEFAULT_UNDONE_HORIZON_DAYS else days

data class UndoneDebt(
    val taskIds: Set<Long> = emptySet(),
    val dates: List<LocalDate> = emptyList(),
) {
    val isEmpty: Boolean get() = taskIds.isEmpty()
}

/**
 * Tasks that were due before [today] and are still not done.
 *
 * [horizonDays] bounds how far back to look. Without it the list grows without
 * limit, and — more to the point — what it contained used to depend on which
 * months had been opened in the calendar, because only browsed ranges get
 * their recurrences materialised.
 */
fun undoneDebt(
    tasks: List<Task>,
    suppressedRecurrences: Set<String>,
    today: LocalDate,
    horizonDays: Int,
): UndoneDebt {
    val lastDay = today.minusDays(1)
    val horizon = normalizeUndoneHorizonDays(horizonDays)
    val firstDay =
        if (horizon == UNDONE_HORIZON_UNLIMITED) null else today.minusDays(horizon.toLong())

    val overdue = tasks.filter { task ->
        val date = task.date ?: return@filter false

        !task.isDone &&
            !date.isAfter(lastDay) &&
            (firstDay == null || !date.isBefore(firstDay)) &&
            !isSuppressedTemplateTaskOnItsDate(task, suppressedRecurrences)
    }

    return UndoneDebt(
        taskIds = overdue.mapTo(mutableSetOf()) { it.id },
        dates = overdue.mapNotNull { it.date }.distinct().sorted(),
    )
}
