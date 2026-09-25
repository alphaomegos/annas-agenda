package com.alphaomegos.annasagenda

import java.time.LocalDate

/** The two lists that move together whenever a series is cut short. */
data class TaskListsSnapshot(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
)

/**
 * Every task's `hasSubtasks` set from whether it actually has any.
 *
 * A derived field kept on the row, so every edit that adds or removes a
 * subtask has to remember to put it right. Doing that in one function means
 * the several callers cannot each get it slightly wrong.
 */
fun withHasSubtasksRefreshed(tasks: List<Task>, subtasks: List<Subtask>): List<Task> {
    val idsWithSubs = subtasks.mapTo(mutableSetOf()) { it.taskId }

    return tasks.map { t -> t.copy(hasSubtasks = idsWithSubs.contains(t.id)) }
}

/**
 * Stops a repeating task from [fromDate] onward: the rule goes, and so do the
 * occurrences that have not happened yet.
 *
 * "Has not happened yet" is `not before fromDate`, so today's own occurrence
 * goes too — the user asking to stop a repeat today means starting today, not
 * starting tomorrow. A task with no date at all counts as not yet happened,
 * which is how a template that was never given one is removed rather than
 * stranded with no rule and no way back into the list.
 *
 * The template's rule is cleared rather than the template being deleted
 * outright, when the template's own day is already past: that day is history
 * and the user did not ask to rewrite it. Nothing has to be tombstoned,
 * because with no rule left there is nothing to regenerate.
 *
 * Returns the lists unchanged if [templateTaskId] does not name a template.
 */
fun tasksAfterDeletingTaskSeriesFrom(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    templateTaskId: Long,
    fromDate: LocalDate,
): TaskListsSnapshot {
    val template = tasks.firstOrNull { it.id == templateTaskId && it.originTaskId == null }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val idsToDelete = mutableSetOf<Long>()

    val templateDate = template.date
    if (templateDate == null || !templateDate.isBefore(fromDate)) {
        idsToDelete.add(template.id)
    }

    tasks.filter { it.originTaskId == templateTaskId }.forEach { instance ->
        val date = instance.date
        if (date == null || !date.isBefore(fromDate)) {
            idsToDelete.add(instance.id)
        }
    }

    val remainingTasks = tasks
        .filterNot { it.id in idsToDelete }
        // A no-op when the template itself was one of the deleted, which is
        // the usual case: stopping a repeat from before it began removes it.
        .map { t -> if (t.id == templateTaskId) t.copy(repeatRule = null) else t }

    val remainingSubtasks = subtasks.filterNot { it.taskId in idsToDelete }

    return TaskListsSnapshot(
        tasks = withHasSubtasksRefreshed(remainingTasks, remainingSubtasks),
        subtasks = remainingSubtasks,
    )
}

/**
 * The same for a subtask that repeats on its own: the rule goes, and so do the
 * copies of it on days that have not happened yet.
 *
 * The awkward part is the day that exists only to carry this subtask. A
 * repeating subtask under a task that does not itself repeat makes the
 * generator build a task for the day to hang it on (see
 * generateRecurrencesInRange). Take the subtask away and that task is a row
 * with nothing in it that the user never asked for, so it goes as well — but
 * only when the parent template does not repeat. Where the parent does repeat,
 * the day is the user's own occurrence of it and stays, subtask or no subtask.
 *
 * Returns the lists unchanged if [templateSubtaskId] does not name a template
 * subtask, or if its parent task is not a template.
 */
fun tasksAfterDeletingSubtaskSeriesFrom(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    templateSubtaskId: Long,
    fromDate: LocalDate,
): TaskListsSnapshot {
    val templateSub = subtasks
        .firstOrNull { it.id == templateSubtaskId && it.originSubtaskId == null }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val parentTemplateTask = tasks
        .firstOrNull { it.id == templateSub.taskId && it.originTaskId == null }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val tasksById = tasks.associateBy { it.id }

    val subIdsToDelete = subtasks
        .filter { it.originSubtaskId == templateSubtaskId }
        .filter { instance ->
            val date = tasksById[instance.taskId]?.date
            date == null || !date.isBefore(fromDate)
        }
        .mapTo(mutableSetOf()) { it.id }

    val remainingSubtasks = subtasks
        .filterNot { it.id in subIdsToDelete }
        .map { s -> if (s.id == templateSubtaskId) s.copy(repeatRule = null) else s }

    if (parentTemplateTask.repeatRule != null) {
        return TaskListsSnapshot(
            tasks = withHasSubtasksRefreshed(tasks, remainingSubtasks),
            subtasks = remainingSubtasks,
        )
    }

    val remainingByTask = remainingSubtasks.groupBy { it.taskId }

    val emptyCarrierIds = tasks
        .filter { it.originTaskId == parentTemplateTask.id }
        .filter { instance ->
            val date = instance.date
            (date == null || !date.isBefore(fromDate)) &&
                remainingByTask[instance.id].isNullOrEmpty()
        }
        .mapTo(mutableSetOf()) { it.id }

    if (emptyCarrierIds.isEmpty()) {
        return TaskListsSnapshot(
            tasks = withHasSubtasksRefreshed(tasks, remainingSubtasks),
            subtasks = remainingSubtasks,
        )
    }

    val remainingTasks = tasks.filterNot { it.id in emptyCarrierIds }
    val withoutOrphanedSubtasks = remainingSubtasks.filterNot { it.taskId in emptyCarrierIds }

    return TaskListsSnapshot(
        tasks = withHasSubtasksRefreshed(remainingTasks, withoutOrphanedSubtasks),
        subtasks = withoutOrphanedSubtasks,
    )
}
