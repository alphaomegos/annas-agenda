package com.alphaomegos.annasagenda.support

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/** One line of the home-screen widget: a task of today, by its series-and-day key. */
data class WidgetTaskRow(
    val key: String,
    val title: String,
    val time: LocalTime?,
    val isDone: Boolean,
    // A task with subtasks (25.10, Eduard): its progress, done of all, instead
    // of a tick — the subtasks themselves are not rows in the widget.
    val progress: Pair<Int, Int>? = null,
)

/**
 * Today's tasks as the day screen orders them: timed ones by the clock, then
 * the rest by hand; done ones where the app puts them (doneTasksLast). Every
 * one — the list scrolls (25.10, Eduard) — and subtasks never as rows of their
 * own: a task with them shows its progress.
 */
fun todayWidgetRows(state: AppState, today: LocalDate, weekStart: DayOfWeek): List<WidgetTaskRow> {
    val drawn = generateRecurrencesInRange(
        tasks = state.tasks,
        subtasks = state.subtasks,
        suppressedRecurrences = state.suppressedRecurrences,
        start = today,
        end = today,
        nextId = maxOf(nextIdFor(state), state.idHighWater) + 1_000_000L,
        defaultWeekStart = weekStart,
    )
    val subtasksOf = drawn.subtasks.groupBy { it.taskId }
    val todays = drawn.tasks.filter { t ->
        t.date == today && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
    }
    return tasksInDayOrder(todays, state.doneTasksLast).map { task ->
        val subs = subtasksOf[task.id].orEmpty()
        WidgetTaskRow(
            key = notificationTaskKey(task),
            title = task.description,
            time = task.time,
            isDone = task.isDone,
            progress = if (subs.isEmpty()) null else subs.count { it.isDone } to subs.size,
        )
    }
}

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

/**
 * The day in the widget's header: "3 октября", "3 October" — day and month,
 * the month in the form the language uses after a number (the genitive in
 * Russian), no year and no weekday (03.10).
 */
fun formatWidgetDay(date: LocalDate, locale: java.util.Locale): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale))

/**
 * What failed, short enough for a widget's corner (25.10: Anna's tasks widget
 * showed only Glance's «Can't show content», and only her phone did it):
 * the exception, its message, and where in the app it came from.
 */
fun widgetErrorDetail(throwable: Throwable): String {
    val root = generateSequence(throwable) { it.cause }.last()
    val ours = root.stackTrace.firstOrNull { it.className.startsWith("com.alphaomegos") }
        ?: throwable.stackTrace.firstOrNull { it.className.startsWith("com.alphaomegos") }
    val where = ours?.let { " @ ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }.orEmpty()
    return ("${root.javaClass.simpleName}: ${root.message.orEmpty()}$where").take(300)
}
