package com.alphaomegos.annasagenda

/**
 * Clears references that point at something no longer there.
 *
 * Ids are handed out as "one past the largest in use", counted over the data
 * that exists. So a reference to a deleted row is not merely dead: the number
 * it holds is handed to whatever the user creates next, and the stale
 * reference silently attaches itself to a stranger. That is not a theory —
 * it is what the running plan did before patch 0016 (resetting the plan
 * deleted somebody else's task) and what a tombstone did before 0017.
 *
 * Every case below clears the link and keeps the row, except one: a subtask
 * whose task is gone cannot be shown anywhere and cannot be re-attached, so it
 * is dropped. Nothing the user can see changes when that happens — but if the
 * task's id came round again, the new task would suddenly grow subtasks
 * nobody wrote.
 *
 * Reading sessions are deliberately not touched. Deleting a book already takes
 * its sessions with it, so an orphaned one only exists in a damaged payload,
 * and a session is a record of something that happened rather than a link to
 * fix — the worst a recycled book id can do is base one estimate on a
 * stranger's reading pace, which the next real session corrects.
 */
fun stateWithDanglingReferencesCleared(state: AppState): AppState {
    val taskIds = state.tasks.mapTo(HashSet()) { it.id }
    val manualCounterIds = state.counters
        .filterIsInstance<ManualCounter>()
        .mapTo(HashSet()) { it.id }
    val bookIds = state.readingBooks.mapTo(HashSet()) { it.id }

    val subtasks = state.subtasks.filter { it.taskId in taskIds }

    val tasks = state.tasks.map { task ->
        val counterId = task.linkedManualCounterId

        if (counterId != null && counterId !in manualCounterIds) {
            task.copy(linkedManualCounterId = null)
        } else {
            task
        }
    }

    val runningPlanEntries = state.runningPlanEntries.map { entry ->
        val taskId = entry.taskId

        if (taskId != null && taskId !in taskIds) {
            entry.copy(taskId = null)
        } else {
            entry
        }
    }

    val activeReading = state.activeReading?.takeIf { it.bookId in bookIds }

    val unchanged = subtasks.size == state.subtasks.size &&
        tasks == state.tasks &&
        runningPlanEntries == state.runningPlanEntries &&
        activeReading == state.activeReading

    if (unchanged) return state

    return state.copy(
        tasks = tasks,
        subtasks = subtasks,
        runningPlanEntries = runningPlanEntries,
        activeReading = activeReading,
    )
}
