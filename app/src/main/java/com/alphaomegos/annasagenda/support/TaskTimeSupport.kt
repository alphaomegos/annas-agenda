package com.alphaomegos.annasagenda

import java.time.LocalTime

/**
 * The order a day's tasks are read in.
 *
 * 1. Tasks with a time come first, earliest first. A time is a promise about
 *    when, and the day reads top to bottom the way it will be lived.
 * 2. Equal times fall back to the manual order, then to the id, so two tasks
 *    at 09:00 never swap places between launches.
 * 3. Tasks without a time follow, in the order the user arranged them.
 *
 * A task's `order` is still kept when it gets a time — it is just not what
 * places it any more. The hole it leaves among the untimed ones is harmless:
 * the order is relative, nothing counts on it being contiguous.
 */
val taskDayOrder: Comparator<Task> =
    compareBy<Task>({ it.time == null }, { it.time }, { it.order }, { it.id })

/** [tasks] in [taskDayOrder]. */
fun tasksInDayOrder(tasks: List<Task>): List<Task> = tasks.sortedWith(taskDayOrder)

/**
 * Whether the arrows may move this task by [step] within its day.
 *
 * **A task with a time does not move by hand**: its place is its time, and an
 * arrow that moved it would be undone by the next sort, which reads as a
 * broken button. And an untimed task never moves above a timed one — the
 * untimed block is the only part of the day whose order is the user's.
 *
 * The screen greys the arrows by this, and [moveTaskWithinDate] refuses by
 * the same rule, so a stale tap on a button that should have been grey still
 * does nothing.
 */
fun canMoveTask(tasks: List<Task>, taskId: Long, step: Int): Boolean {
    val victim = tasks.firstOrNull { it.id == taskId } ?: return false
    if (victim.time != null) return false

    val untimed = untimedSiblings(tasks, victim)
    val idx = untimed.indexOfFirst { it.id == taskId }
    return idx >= 0 && (idx + step) in untimed.indices
}

internal fun untimedSiblings(tasks: List<Task>, victim: Task): List<Task> =
    tasks
        .filter { it.date == victim.date && it.time == null }
        .sortedWith(compareBy({ it.order }, { it.id }))

/**
 * Gives a task a time, changes it, or takes it away.
 *
 * - **Only a task with a day can have a time.** "Someday at 09:00" names no
 *   moment at all; for a task without a date this returns [tasks] unchanged.
 * - Setting a time leaves `order` alone (see [taskDayOrder]).
 * - **Taking the time away puts the task at the end of the untimed block**, as
 *   a new task would land. Its old `order` may be anywhere — next to tasks
 *   that were arranged around the hole it left — and dropping it back there
 *   would put it somewhere nobody chose.
 *
 * The same time again, or a task that does not exist, returns [tasks] itself,
 * so the caller can skip a write that changes nothing.
 */
fun tasksAfterSettingTime(tasks: List<Task>, taskId: Long, time: LocalTime?): List<Task> {
    val victim = tasks.firstOrNull { it.id == taskId } ?: return tasks
    if (victim.date == null) return tasks
    if (victim.time == time) return tasks

    val changed = if (time == null) {
        victim.copy(time = null, order = nextTaskOrderOn(tasks, victim.date))
    } else {
        victim.copy(time = time)
    }

    return tasks.map { if (it.id == taskId) changed else it }
}

/**
 * A task's time as the day shows it: 24-hour, always two digits each side.
 *
 * Built by hand rather than with a formatter on purpose. A formatter takes
 * the machine's locale, and the picker this time came from is fixed to the
 * 24-hour clock — so the two would disagree on any phone set to AM/PM.
 */
fun formatTaskTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/**
 * A time as one number, for the screens that keep it across a rotation: a
 * LocalTime does not go into a Bundle, an Int does. Seconds are dropped —
 * nothing in the app sets them.
 */
fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

/** The way back from [minuteOfDay]; null in, null out, anything out of range is no time. */
fun timeFromMinuteOfDay(minute: Int?): LocalTime? =
    minute?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }
