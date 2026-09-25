package com.alphaomegos.annasagenda

import java.time.LocalDate

/** The three things moving one task to another day can touch. */
data class RescheduleResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val suppressedRecurrences: Set<String>,
)

/** Where a new row lands on a day: after everything already there. */
fun nextTaskOrderOn(tasks: List<Task>, date: LocalDate?): Int =
    (tasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1

/**
 * Moves one task to another day — or off the calendar entirely, which is what
 * a null date means here and which the app calls "Someday".
 *
 * An ordinary task just changes its date and goes to the bottom of the day it
 * lands on. Moving it back to the day it is already on keeps its position,
 * rather than dropping it to the bottom for no reason the user can see.
 *
 * An occurrence of a repeat is the interesting one. Dragged to another day it
 * stops being an occurrence: it keeps its description and its subtasks but
 * loses its link to the template, because a template's occurrence is defined
 * by the day the rule names and this one is no longer on that day. At the same
 * time the day it left is tombstoned, for the task and for each of its
 * generated subtasks — otherwise the generator, which only asks whether a day
 * that the rule names is missing its occurrence, would helpfully build a fresh
 * one there and the user would end up with the task twice.
 *
 * That detachment is one-way and deliberate: the moved task is now the user's
 * own, and editing the template afterwards leaves it alone.
 *
 * Returns everything unchanged if [taskId] names nothing.
 */
fun stateAfterReschedulingTask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    taskId: Long,
    newDate: LocalDate?,
): RescheduleResult {
    val victim = tasks.firstOrNull { it.id == taskId }
        ?: return RescheduleResult(tasks, subtasks, suppressedRecurrences)

    val oldDate = victim.date
    val newOrder =
        if (oldDate == newDate) victim.order else nextTaskOrderOn(tasks, newDate)

    val originTaskId = victim.originTaskId
    val leavesItsOwnDay = originTaskId != null && oldDate != null && oldDate != newDate

    if (!leavesItsOwnDay) {
        return RescheduleResult(
            tasks = tasks.map { t ->
                if (t.id == taskId) t.copy(date = newDate, order = newOrder) else t
            },
            subtasks = subtasks,
            suppressedRecurrences = suppressedRecurrences,
        )
    }

    val generatedSubs = subtasks.filter { it.taskId == taskId && it.originSubtaskId != null }

    val tombstones = generatedSubs
        .mapNotNull { sub -> sub.originSubtaskId?.let { subtaskSuppressionKey(it, oldDate) } }
        .toSet() + taskSuppressionKey(originTaskId, oldDate)

    val movedTasks = tasks.map { t ->
        if (t.id == taskId) {
            t.copy(date = newDate, order = newOrder, repeatRule = null, originTaskId = null)
        } else {
            t
        }
    }

    val movedSubtasks = subtasks.map { sub ->
        if (sub.taskId == taskId && sub.originSubtaskId != null) {
            sub.copy(repeatRule = null, originSubtaskId = null)
        } else {
            sub
        }
    }

    return RescheduleResult(
        tasks = movedTasks,
        subtasks = movedSubtasks,
        suppressedRecurrences = suppressedRecurrences + tombstones,
    )
}
