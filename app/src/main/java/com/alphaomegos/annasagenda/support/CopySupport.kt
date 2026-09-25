package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * Copying a task to another day, with everything under it.
 *
 * A copy is a new task, not another occurrence of anything: it has no repeat
 * rule and no template, so editing the original afterwards leaves it alone and
 * the generator has never heard of it. It lands at the bottom of the day it
 * goes to, as any new row does.
 *
 * Nothing carries over that describes progress. The copy is not done, and
 * neither are its parts, because the point of copying a task is to do it
 * again. What does carry over is the link to a manual counter, since the
 * counter is what the task is for.
 *
 * [newId] is called once for the task and once per part, in that order, and
 * only after the task being copied has been found.
 */
fun stateAfterCopyingTask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    taskId: Long,
    targetDate: LocalDate,
    newId: () -> Long,
): TaskListsSnapshot {
    val source = tasks.firstOrNull { it.id == taskId }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val sourceSubtasks = subtasks
        .filter { it.taskId == taskId }
        .sortedWith(compareBy({ it.order }, { it.id }))

    val copiedId = newId()

    val copied = Task(
        id = copiedId,
        order = nextTaskOrderOn(tasks, targetDate),
        date = targetDate,
        time = source.time,
        description = source.description,
        colorArgb = source.colorArgb,
        hasSubtasks = sourceSubtasks.isNotEmpty(),
        isDone = false,
        linkedManualCounterId = source.linkedManualCounterId,
        repeatRule = null,
        originTaskId = null,
    )

    val copiedSubtasks = sourceSubtasks.mapIndexed { index, s ->
        copiedSubtask(s.description, s.colorArgb, copiedId, index, newId())
    }

    return TaskListsSnapshot(tasks + copied, subtasks + copiedSubtasks)
}

/**
 * Copying one part of a task to another day, which needs a task to live in.
 *
 * The new task is a copy of the one the part came from — same wording, same
 * time, same colour, same counter — because a part on its own says too little
 * to stand as a day's entry. "Rinse" is a reminder only under "wash the
 * brushes".
 *
 * Nothing changes if the part, or the task it belongs to, cannot be found.
 */
fun stateAfterCopyingSubtask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    subtaskId: Long,
    targetDate: LocalDate,
    newId: () -> Long,
): TaskListsSnapshot {
    val source = subtasks.firstOrNull { it.id == subtaskId }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val parent = tasks.firstOrNull { it.id == source.taskId }
        ?: return TaskListsSnapshot(tasks, subtasks)

    val carrierId = newId()

    val carrier = Task(
        id = carrierId,
        order = nextTaskOrderOn(tasks, targetDate),
        date = targetDate,
        time = parent.time,
        description = parent.description,
        colorArgb = parent.colorArgb,
        hasSubtasks = true,
        isDone = false,
        linkedManualCounterId = parent.linkedManualCounterId,
        repeatRule = null,
        originTaskId = null,
    )

    val copied = copiedSubtask(source.description, source.colorArgb, carrierId, 0, newId())

    return TaskListsSnapshot(tasks + carrier, subtasks + copied)
}

/**
 * A part of a copy: new, unfinished, tied to nothing it came from.
 *
 * The description is trimmed because creating a subtask has always trimmed,
 * and a copy should not be the one way a stray space gets in.
 */
private fun copiedSubtask(
    description: String,
    colorArgb: Long?,
    taskId: Long,
    order: Int,
    id: Long,
) = Subtask(
    id = id,
    order = order,
    taskId = taskId,
    description = description.trim(),
    colorArgb = colorArgb,
    isDone = false,
    repeatRule = null,
    originSubtaskId = null,
)
