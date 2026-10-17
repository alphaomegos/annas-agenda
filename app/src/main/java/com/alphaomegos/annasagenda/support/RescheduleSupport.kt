package com.alphaomegos.annasagenda.support

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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
 * Moving a task to Someday takes its time away: a time names a moment on a
 * day, and Someday has no day. Moving it to another day keeps the time.
 *
 * A template's own first day is a day of its series too (17.10). Moving that
 * row used to change the template's date — the day the whole series counts
 * from — and nothing else: the days already drawn stayed where they were, and
 * the series began drawing new ones beside them. Given [newId], it now moves
 * as an occurrence does: a copy of that day goes, the series stays, and the
 * day it left is tombstoned (see [stateAfterMovingTemplateDay]).
 *
 * Returns everything unchanged if [taskId] names nothing.
 */
fun stateAfterReschedulingTask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    taskId: Long,
    newDate: LocalDate?,
    newId: (() -> Long)? = null,
): RescheduleResult {
    val victim = tasks.firstOrNull { it.id == taskId }
        ?: return RescheduleResult(tasks, subtasks, suppressedRecurrences)

    val oldDate = victim.date
    if (newId != null && victim.originTaskId == null && victim.repeatRule != null && oldDate != null && oldDate != newDate) {
        return stateAfterMovingTemplateDay(tasks, subtasks, suppressedRecurrences, victim, newDate, newId)
    }
    val newOrder =
        if (oldDate == newDate) victim.order else nextTaskOrderOn(tasks, newDate)

    val newTime = if (newDate == null) null else victim.time

    val originTaskId = victim.originTaskId
    val leavesItsOwnDay = originTaskId != null && oldDate != null && oldDate != newDate

    if (!leavesItsOwnDay) {
        return RescheduleResult(
            tasks = tasks.map { t ->
                if (t.id == taskId) t.copy(date = newDate, time = newTime, order = newOrder) else t
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
            t.copy(
                date = newDate,
                time = newTime,
                order = newOrder,
                repeatRule = null,
                originTaskId = null,
            )
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

/**
 * The first day of a series moved on its own (17.10): the template stays
 * where it is, the day its rule counts from; its own row there is tombstoned,
 * as a deleted first day is; and a copy of that day — done or not, its time,
 * its parts and theirs — goes to [newDate], a task of its own with no series,
 * as a moved occurrence becomes. To Someday it loses its time.
 *
 * [newId] is called once for the copy and once per part, in that order.
 */
fun stateAfterMovingTemplateDay(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    template: Task,
    newDate: LocalDate?,
    newId: () -> Long,
): RescheduleResult {
    val oldDate = template.date ?: return RescheduleResult(tasks, subtasks, suppressedRecurrences)
    val copyId = newId()
    val copy = template.copy(
        id = copyId,
        date = newDate,
        time = if (newDate == null) null else template.time,
        order = nextTaskOrderOn(tasks, newDate),
        repeatRule = null,
        originTaskId = null,
    )
    val copiedSubtasks = subtasks
        .filter { it.taskId == template.id }
        .sortedWith(compareBy({ it.order }, { it.id }))
        .map { s -> s.copy(id = newId(), taskId = copyId, repeatRule = null, originSubtaskId = null) }

    return RescheduleResult(
        tasks = tasks + copy,
        subtasks = subtasks + copiedSubtasks,
        suppressedRecurrences = suppressedRecurrences + taskSuppressionKey(template.id, oldDate),
    )
}
