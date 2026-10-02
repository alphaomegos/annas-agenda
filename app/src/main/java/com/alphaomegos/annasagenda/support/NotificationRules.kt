package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When the app speaks up, and what it says. Pure, so that every rule here is
 * a JVM test; the Android side only sets one alarm for the first event and,
 * when it fires, asks this file again.
 *
 * Nothing is kept about what has already been shown. The alarm carries the
 * minute it was set for; the receiver posts what falls on that minute and
 * sets the next alarm strictly after it. A phone switched off over a summary
 * simply misses it — a summary from three hours ago is not news.
 */
sealed interface NotificationEvent {
    val at: LocalDateTime
}

/** One of the day's summaries, at a time the user chose. */
data class SummaryEvent(override val at: LocalDateTime) : NotificationEvent

/**
 * A reminder of a timed task.
 *
 * [taskKey] names the task by its series and day rather than by its id: an
 * occurrence not yet drawn has no id, and drawing it later gives it one the
 * alarm could not have known.
 */
data class ReminderEvent(
    override val at: LocalDateTime,
    val taskKey: String,
    val title: String,
    val time: LocalTime,
) : NotificationEvent

/** How far ahead the next alarm is looked for. Past that, a reschedule is due anyway. */
const val NOTIFICATION_LOOKAHEAD_DAYS = 2L

/** "<series or task id>@<epoch day>": the same task on the same day, however drawn. */
fun notificationTaskKey(task: Task): String =
    "${task.originTaskId ?: task.id}@${task.date?.toEpochDay()}"

/**
 * The tasks of [from]..[to] as the day screen would show them: repeats drawn
 * for those days, a template deleted for its own day left out.
 *
 * The state on disk has the derivable occurrences pruned out, so the
 * receiver, which reads from disk, must draw them itself. Ids handed out here
 * are throwaway — nothing is written back.
 */
fun tasksForNotifications(
    state: AppState,
    from: LocalDate,
    to: LocalDate,
    weekStart: DayOfWeek,
): List<Task> {
    val drawn = generateRecurrencesInRange(
        tasks = state.tasks,
        subtasks = state.subtasks,
        suppressedRecurrences = state.suppressedRecurrences,
        start = from,
        end = to,
        nextId = maxOf(nextIdFor(state), state.idHighWater) + 1_000_000L,
        defaultWeekStart = weekStart,
    )
    return drawn.tasks.filter { t ->
        val d = t.date
        d != null && !d.isBefore(from) && !d.isAfter(to) &&
            !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
    }
}

/** The summary times strictly after [after], for the days ahead. */
fun summaryEventsAfter(after: LocalDateTime, minutes: List<Int>): List<SummaryEvent> {
    val clean = minutes.filter { it in 0 until 24 * 60 }.distinct().sorted()
    if (clean.isEmpty()) return emptyList()
    return (0..NOTIFICATION_LOOKAHEAD_DAYS).flatMap { plus ->
        val day = after.toLocalDate().plusDays(plus)
        clean.map { SummaryEvent(day.atTime(it / 60, it % 60)) }
    }.filter { it.at.isAfter(after) }
}

/**
 * Reminders strictly after [after] for the timed tasks in [tasks] that are
 * not done, [leadMinutes] before their time; none when it is null.
 *
 * A reminder whose moment has already gone by is not sent late: a task
 * written down at 10:03 for 10:10 with a quarter-hour lead was seen being
 * written, and does not need reminding of.
 */
fun reminderEventsAfter(tasks: List<Task>, after: LocalDateTime, leadMinutes: Int?): List<ReminderEvent> {
    if (leadMinutes == null || leadMinutes < 0) return emptyList()
    return tasks.mapNotNull { t ->
        val date = t.date ?: return@mapNotNull null
        val time = t.time ?: return@mapNotNull null
        if (t.isDone) return@mapNotNull null
        val at = date.atTime(time).minusMinutes(leadMinutes.toLong())
        if (!at.isAfter(after)) null else ReminderEvent(at, notificationTaskKey(t), t.description, time)
    }
}

/**
 * Everything due strictly after [after], earliest first; a summary before a
 * reminder that falls on the same minute.
 */
fun upcomingNotificationEvents(
    state: AppState,
    after: LocalDateTime,
    weekStart: DayOfWeek,
): List<NotificationEvent> {
    val settings = state.notifications
    val summaries = summaryEventsAfter(after, settings.summaryMinutes)
    val reminders = if (settings.reminderLeadMinutes == null) {
        emptyList()
    } else {
        // A lead of an hour can put tomorrow's 00:30 reminder tonight: draw a day more.
        val from = after.toLocalDate()
        val to = from.plusDays(NOTIFICATION_LOOKAHEAD_DAYS + 1)
        reminderEventsAfter(tasksForNotifications(state, from, to, weekStart), after, settings.reminderLeadMinutes)
    }
    val horizon = after.plusDays(NOTIFICATION_LOOKAHEAD_DAYS)
    return (summaries + reminders)
        .filter { !it.at.isAfter(horizon) }
        .sortedWith(compareBy<NotificationEvent> { it.at }.thenBy { if (it is SummaryEvent) 0 else 1 })
}

/** What falls exactly on [at] — the minute an alarm was set for. */
fun notificationEventsAt(state: AppState, at: LocalDateTime, weekStart: DayOfWeek): List<NotificationEvent> {
    val minute = at.withSecond(0).withNano(0)
    return upcomingNotificationEvents(state, minute.minusMinutes(1), weekStart).filter { it.at == minute }
}

/** The next minute an alarm is needed for after [after], or null for silence. */
fun nextNotificationAt(state: AppState, after: LocalDateTime, weekStart: DayOfWeek): LocalDateTime? =
    upcomingNotificationEvents(state, after, weekStart).firstOrNull()?.at

/* ---------------- the summary itself ---------------- */

/** One line of the summary: the task, its time if it has one, and whether it is done. */
data class SummaryLine(val title: String, val time: LocalTime?, val isDone: Boolean)

/**
 * What a summary says. [today] is what the settings ask for — all of
 * today's tasks, the ones not done, or none; [debts] how many past tasks are
 * still undone, by the Undone screen's own rule and horizon, or 0 when the
 * settings leave debts out.
 */
data class DailySummary(val today: List<SummaryLine>, val debts: Int)

/**
 * The summary for [today], or null when it would say nothing — an empty
 * notification at 08:00 is noise, and "nothing to do" is what silence means.
 */
fun dailySummaryOf(state: AppState, today: LocalDate, weekStart: DayOfWeek): DailySummary? {
    val settings = state.notifications

    val lines = when (settings.summaryToday) {
        SummaryToday.NONE -> emptyList()
        else -> tasksInDayOrder(tasksForNotifications(state, today, today, weekStart))
            .filter { settings.summaryToday == SummaryToday.ALL || !it.isDone }
            .map { SummaryLine(it.description, it.time, it.isDone) }
    }

    val debts = if (!settings.summaryDebts) {
        0
    } else {
        // The repeats drawn over the horizon, as the Undone screen draws them;
        // everything dated before the first repeat is in the state as it is.
        val start = undoneGenerationStart(state.tasks, state.subtasks, today, state.undoneHorizonDays)
        val drawnRange = if (start != null && start.isBefore(today)) {
            tasksForNotifications(state, start, today.minusDays(1), weekStart)
        } else {
            emptyList()
        }
        val before = state.tasks.filter { t ->
            val d = t.date
            d != null && (start == null || d.isBefore(start))
        }
        val drawn = drawnRange + before
        undoneDebt(drawn, state.suppressedRecurrences, today, state.undoneHorizonDays).taskIds.size
    }

    return if (lines.isEmpty() && debts == 0) null else DailySummary(lines, debts)
}

/**
 * The reminder [taskKey] still stands at [at]: the task is still there, still
 * not done, and still at the time the alarm was set for. Checked when the
 * alarm fires, because the user may have ticked it or moved it since.
 */
fun reminderStillDue(state: AppState, event: ReminderEvent, weekStart: DayOfWeek): Boolean {
    val day = event.at.plusMinutes((state.notifications.reminderLeadMinutes ?: return false).toLong()).toLocalDate()
    return tasksForNotifications(state, day, day, weekStart).any {
        notificationTaskKey(it) == event.taskKey && !it.isDone && it.time == event.time
    }
}
