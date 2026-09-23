package com.alphaomegos.annasagenda

/**
 * The one place that knows a task's done flag and its linked manual counter
 * move together.
 *
 * The rule used to be written out four times, and two of the four only did
 * half of it. toggleTaskDone and toggleSubtaskDone moved the counter;
 * deleteSubtask and recomputeTaskDoneFromSubtasks changed the same flag and
 * left the counter where it was. Since the counter is only ever moved by a
 * delta, a flag that changes without its delta does not go stale — it puts the
 * balance permanently out of step, and the next honest toggle pays the
 * difference in the wrong direction. That is how a counter could be made to
 * grow out of nothing: remove a subtask so the task falls into "done" for free,
 * then untick the task and collect the +1.
 *
 * Expressed as flags-in, lists-out so that a caller cannot take the flags
 * without the counter movement that belongs to them.
 */

/** What [applyTaskDoneFlags] produces: both lists must be written back together. */
data class TasksAndCounters(
    val tasks: List<Task>,
    val counters: List<Counter>,
)

/**
 * Rewrites every task's done flag to [newDoneFor] and moves each linked manual
 * counter by however much the flags actually moved.
 *
 * A task whose flag does not change contributes nothing, so this is safe to run
 * over the whole list — which is what recomputing from subtasks needs.
 */
fun applyTaskDoneFlags(
    tasks: List<Task>,
    counters: List<Counter>,
    newDoneFor: (Task) -> Boolean,
): TasksAndCounters {
    val deltas = mutableMapOf<Long, Int>()

    val newTasks = tasks.map { task ->
        val newDone = newDoneFor(task)
        if (newDone == task.isDone) return@map task

        val counterId = task.linkedManualCounterId
        if (counterId != null) {
            // Done means one fewer left to do.
            deltas[counterId] = (deltas[counterId] ?: 0) + if (newDone) -1 else +1
        }

        task.copy(isDone = newDone)
    }

    if (deltas.isEmpty()) return TasksAndCounters(newTasks, counters)

    return TasksAndCounters(
        tasks = newTasks,
        counters = countersWithManualCounterDeltas(counters, deltas),
    )
}

/** Moves one manual counter. Counters of other kinds have no balance to move. */
fun countersWithManualCounterDelta(
    counters: List<Counter>,
    counterId: Long,
    delta: Int,
): List<Counter> = countersWithManualCounterDeltas(counters, mapOf(counterId to delta))

/** Moves several manual counters in one pass. */
fun countersWithManualCounterDeltas(
    counters: List<Counter>,
    deltas: Map<Long, Int>,
): List<Counter> {
    if (deltas.isEmpty()) return counters

    return counters.map { counter ->
        val delta = deltas[counter.id]

        if (counter is ManualCounter && delta != null && delta != 0) {
            counter.copy(balance = counter.balance + delta)
        } else {
            counter
        }
    }
}
