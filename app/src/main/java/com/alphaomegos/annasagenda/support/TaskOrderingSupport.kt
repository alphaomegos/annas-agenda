package com.alphaomegos.annasagenda

import java.time.LocalDate

fun moveTaskWithinDate(
    tasks: List<Task>,
    taskId: Long,
    step: Int,
): List<Task> {
    val victim = tasks.firstOrNull { it.id == taskId } ?: return tasks
    val siblings = tasks
        .filter { it.date == victim.date }
        .sortedWith(compareBy({ it.order }, { it.id }))

    val idx = siblings.indexOfFirst { it.id == taskId }
    val targetIdx = idx + step
    if (idx < 0 || targetIdx !in siblings.indices) return tasks

    val reordered = siblings.toMutableList()
    val tmp = reordered[targetIdx]
    reordered[targetIdx] = reordered[idx]
    reordered[idx] = tmp

    val idToOrder = reordered.mapIndexed { i, task -> task.id to i }.toMap()
    return tasks.map { task ->
        idToOrder[task.id]?.let { task.copy(order = it) } ?: task
    }
}

fun moveSubtaskWithinTask(
    subtasks: List<Subtask>,
    subtaskId: Long,
    step: Int,
): List<Subtask> {
    val victim = subtasks.firstOrNull { it.id == subtaskId } ?: return subtasks
    val siblings = subtasks
        .filter { it.taskId == victim.taskId }
        .sortedWith(compareBy({ it.order }, { it.id }))

    val idx = siblings.indexOfFirst { it.id == subtaskId }
    val targetIdx = idx + step
    if (idx < 0 || targetIdx !in siblings.indices) return subtasks

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
