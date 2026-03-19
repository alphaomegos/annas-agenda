package com.alphaomegos.annasagenda

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