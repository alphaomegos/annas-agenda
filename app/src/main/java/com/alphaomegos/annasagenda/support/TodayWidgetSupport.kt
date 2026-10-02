package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** One line of the home-screen widget: a task of today, by its series-and-day key. */
data class WidgetTaskRow(
    val key: String,
    val title: String,
    val time: LocalTime?,
    val isDone: Boolean,
)

/** Today's tasks as the day screen orders them: timed ones by the clock, then the rest by hand. */
fun todayWidgetRows(state: AppState, today: LocalDate, weekStart: DayOfWeek): List<WidgetTaskRow> =
    tasksInDayOrder(tasksForNotifications(state, today, today, weekStart))
        .map { WidgetTaskRow(notificationTaskKey(it), it.description, it.time, it.isDone) }

/** What a tick from the widget produced, and where the id counter now stands. */
data class WidgetToggleResult(val state: AppState, val nextId: Long)

/**
 * A tick from the widget, by the task's key.
 *
 * Today's repeats are drawn first, with real ids from [nextId], because the
 * one ticked may be an occurrence nobody has drawn yet — the state on disk
 * keeps none it can derive. Then it is the same toggle as in the app,
 * subtasks and a linked counter included. Null when the key names nothing
 * today: the widget was showing yesterday, or the task has gone.
 */
fun stateAfterTogglingTaskFromWidget(
    state: AppState,
    key: String,
    today: LocalDate,
    weekStart: DayOfWeek,
    nextId: Long,
): WidgetToggleResult? {
    val drawn = generateRecurrencesInRange(
        tasks = state.tasks,
        subtasks = state.subtasks,
        suppressedRecurrences = state.suppressedRecurrences,
        start = today,
        end = today,
        nextId = nextId,
        defaultWeekStart = weekStart,
    )
    val target = drawn.tasks.firstOrNull {
        it.date == today &&
            notificationTaskKey(it) == key &&
            !isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences)
    } ?: return null

    val after = stateAfterTogglingTask(drawn.tasks, drawn.subtasks, state.counters, target.id)
    val next = state.copy(tasks = after.tasks, subtasks = after.subtasks, counters = after.counters)
    return WidgetToggleResult(stateWithIdHighWaterAtLeast(next, drawn.nextId), drawn.nextId)
}
