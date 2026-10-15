package com.alphaomegos.annasagenda.support

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * One arrow tap on a task: swaps it with its neighbour among the day's
 * **untimed** tasks, and renumbers the day so that every `order` on it is
 * unique again.
 *
 * Refuses — returns [tasks] itself — whenever [canMoveTask] says no: a task
 * with a time (its place is its time), a move past either end, a task that
 * does not exist. The screen greys the arrows by the same rule; this refusal
 * is for the tap that arrives anyway.
 *
 * The renumbering walks the day in [taskDayOrder], timed tasks included, so a
 * day keeps the property the recurrence pruning relies on: no two tasks share
 * an `order`.
 */
fun moveTaskWithinDate(
    tasks: List<Task>,
    taskId: Long,
    step: Int,
    doneLast: Boolean = false,
): List<Task> {
    if (!canMoveTask(tasks, taskId, step, doneLast)) return tasks
    val victim = tasks.first { it.id == taskId }

    // With done tasks at the bottom (06.10) the swap is with the neighbour as
    // shown — among the tasks done or not done like this one — and the
    // others keep their places in the day's numbering.
    val untimed = untimedSiblings(tasks, victim).toMutableList()
    val slots = untimed.indices.filter { !doneLast || untimed[it].isDone == victim.isDone }
    val idx = slots.indexOfFirst { untimed[it].id == taskId }
    val a = slots[idx]
    val b = slots[idx + step]

    val tmp = untimed[b]
    untimed[b] = untimed[a]
    untimed[a] = tmp

    val timed = tasks
        .filter { it.date == victim.date && it.time != null }
        .sortedWith(taskDayOrder)

    val idToOrder = (timed + untimed).mapIndexed { i, task -> task.id to i }.toMap()
    return tasks.map { task ->
        idToOrder[task.id]?.let { task.copy(order = it) } ?: task
    }
}

/** The order a task's subtasks are read in: as the user arranged them, then by id. */
val subtaskOrder: Comparator<Subtask> = compareBy({ it.order }, { it.id })

/**
 * The order a task's subtasks are shown in (16.10): [subtaskOrder], and with
 * [doneLast] — a setting — the done ones after the rest, each part in that
 * same order.
 */
fun subtaskDisplayOrder(doneLast: Boolean): Comparator<Subtask> =
    if (doneLast) compareBy<Subtask> { it.isDone }.then(subtaskOrder) else subtaskOrder

/**
 * [subtaskId] moved by [step] among its task's subtasks.
 *
 * With done subtasks at the bottom (16.10) the swap is with the neighbour as
 * shown — among the subtasks done or not done like this one — and the
 * others keep their places in the task's numbering, as tasks do in a day.
 */
fun moveSubtaskWithinTask(
    subtasks: List<Subtask>,
    subtaskId: Long,
    step: Int,
    doneLast: Boolean = false,
): List<Subtask> {
    val victim = subtasks.firstOrNull { it.id == subtaskId } ?: return subtasks
    val siblings = subtasks
        .filter { it.taskId == victim.taskId }
        .sortedWith(subtaskOrder)

    val slots = siblings.indices.filter { !doneLast || siblings[it].isDone == victim.isDone }
    val pos = slots.indexOfFirst { siblings[it].id == subtaskId }
    if (pos < 0 || (pos + step) !in slots.indices) return subtasks
    val idx = slots[pos]
    val targetIdx = slots[pos + step]

    val reordered = siblings.toMutableList()
    val tmp = reordered[targetIdx]
    reordered[targetIdx] = reordered[idx]
    reordered[idx] = tmp

    val idToOrder = reordered.mapIndexed { i, subtask -> subtask.id to i }.toMap()
    return subtasks.map { subtask ->
        idToOrder[subtask.id]?.let { subtask.copy(order = it) } ?: subtask
    }
}
/**
 * The tasks a subtask can be moved to.
 *
 * A subtask lives on its task's day, so moving it into a task with no date, or
 * into one whose day has already passed, would either hide it from the day
 * screens or bury it in the past. The dialog filtered for exactly this and
 * said nothing about it, so a user whose only other tasks are in the past was
 * shown an empty list with a Cancel button.
 *
 * Ordered the way the days are read: by date, then by the order inside the
 * day, then by id so the list never shuffles between openings.
 */
fun subtaskMoveTargets(
    tasks: List<Task>,
    currentTaskId: Long?,
    today: LocalDate,
): List<Task> =
    tasks
        .filter { task ->
            val date = task.date

            task.id != currentTaskId && date != null && !date.isBefore(today)
        }
        .sortedWith(
            compareBy(
                { it.date?.toEpochDay() ?: Long.MAX_VALUE },
                { it.order },
                { it.id },
            )
        )
