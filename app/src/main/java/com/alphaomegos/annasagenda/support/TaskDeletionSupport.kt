package com.alphaomegos.annasagenda

/** Everything deleting a task can touch. */
data class TaskDeletionResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val suppressedRecurrences: Set<String>,
    val runningPlanEntries: List<RunningPlanEntry>,
)

/** Everything deleting a subtask can touch. Counters, because finishing counts. */
data class SubtaskDeletionResult(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val suppressedRecurrences: Set<String>,
    val counters: List<Counter>,
)

/**
 * Deletes one task, which is three different things depending on what it is.
 *
 * An occurrence of a repeat is removed and its day tombstoned, or the
 * generator puts it straight back the next time that day is drawn.
 *
 * A template that repeats is not removed at all. Its row on its own day is
 * tombstoned instead, so that day looks empty while the rule goes on producing
 * the others — deleting the row in front of you should not silently cancel a
 * schedule set up weeks ago. Cancelling a schedule is what the repeating-tasks
 * screen is for, and it asks first.
 *
 * Anything else is simply removed, with its subtasks.
 *
 * In every case where the task really goes, a running-plan row pointing at it
 * is unhooked: a row that outlives its task holds an id that will be handed
 * out again to something else.
 */
fun stateAfterDeletingTask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    runningPlanEntries: List<RunningPlanEntry>,
    taskId: Long,
): TaskDeletionResult {
    val victim = tasks.firstOrNull { it.id == taskId }
        ?: return TaskDeletionResult(tasks, subtasks, suppressedRecurrences, runningPlanEntries)

    val originTaskId = victim.originTaskId
    val date = victim.date

    if (originTaskId == null && victim.repeatRule != null && date != null) {
        return TaskDeletionResult(
            tasks = tasks,
            subtasks = subtasks,
            suppressedRecurrences = suppressedRecurrences + taskSuppressionKey(victim.id, date),
            runningPlanEntries = runningPlanEntries,
        )
    }

    val remainingSubtasks = subtasks.filterNot { it.taskId == taskId }
    val remainingTasks = withHasSubtasksRefreshed(
        tasks.filterNot { it.id == taskId },
        remainingSubtasks,
    )

    val tombstoned =
        if (originTaskId != null && date != null) {
            suppressedRecurrences + taskSuppressionKey(originTaskId, date)
        } else {
            suppressedRecurrences
        }

    return TaskDeletionResult(
        tasks = remainingTasks,
        subtasks = remainingSubtasks,
        suppressedRecurrences = tombstoned,
        runningPlanEntries = runningPlanEntriesWithoutTask(runningPlanEntries, taskId),
    )
}

/**
 * Deletes one subtask, tombstoning the day it was on if it was generated.
 *
 * The tombstone is keyed on the **template** subtask rather than on this copy,
 * which is what makes the deletion stick: the generator asks whether the
 * template's subtask is wanted on that day, and this copy's own id means
 * nothing to it.
 *
 * Taking the last unfinished subtask away can complete the task, and a task
 * that completes moves its linked counter. That is why the counters come in
 * and go out again: the arithmetic belongs to applyTaskDoneFlags, and doing it
 * separately here is how the counter once drifted upward a point at a time.
 *
 * A task left with no subtasks at all keeps whatever done flag it had. There
 * is nothing left to derive it from, and choosing either answer would be
 * inventing one.
 */
fun stateAfterDeletingSubtask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    suppressedRecurrences: Set<String>,
    counters: List<Counter>,
    subtaskId: Long,
): SubtaskDeletionResult {
    val victim = subtasks.firstOrNull { it.id == subtaskId }
        ?: return SubtaskDeletionResult(tasks, subtasks, suppressedRecurrences, counters)

    val parentDate = tasks.firstOrNull { it.id == victim.taskId }?.date
    val originSubtaskId = victim.originSubtaskId

    val tombstoned =
        if (originSubtaskId != null && parentDate != null) {
            suppressedRecurrences + subtaskSuppressionKey(originSubtaskId, parentDate)
        } else {
            suppressedRecurrences
        }

    val remainingSubtasks = subtasks.filterNot { it.id == subtaskId }
    val refreshedTasks = withHasSubtasksRefreshed(tasks, remainingSubtasks)

    val siblings = remainingSubtasks.filter { it.taskId == victim.taskId }

    if (siblings.isEmpty()) {
        return SubtaskDeletionResult(refreshedTasks, remainingSubtasks, tombstoned, counters)
    }

    val allDone = siblings.all { it.isDone }
    val applied = applyTaskDoneFlags(refreshedTasks, counters) { t ->
        if (t.id == victim.taskId) allDone else t.isDone
    }

    return SubtaskDeletionResult(
        tasks = applied.tasks,
        subtasks = remainingSubtasks,
        suppressedRecurrences = tombstoned,
        counters = applied.counters,
    )
}
