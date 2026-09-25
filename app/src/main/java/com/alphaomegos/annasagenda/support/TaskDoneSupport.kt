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

/** Tasks, subtasks and counters after a tick. All three move together. */
data class DoneChange(
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
    val counters: List<Counter>,
)

/**
 * Ticking a task, which ticks everything under it.
 *
 * A task with subtasks is not done on its own account — it is done because its
 * parts are — so ticking the task ticks them all, and unticking it unticks
 * them all. The counter moves once, for the task, never for the parts.
 *
 * Nothing changes if [taskId] names nothing.
 */
fun stateAfterTogglingTask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    counters: List<Counter>,
    taskId: Long,
): DoneChange {
    val task = tasks.firstOrNull { it.id == taskId }
        ?: return DoneChange(tasks, subtasks, counters)

    val newDone = !task.isDone

    val applied = applyTaskDoneFlags(tasks, counters) { t ->
        if (t.id == taskId) newDone else t.isDone
    }

    val newSubtasks =
        if (subtasks.none { it.taskId == taskId }) {
            subtasks
        } else {
            subtasks.map { s -> if (s.taskId == taskId) s.copy(isDone = newDone) else s }
        }

    return DoneChange(applied.tasks, newSubtasks, applied.counters)
}

/**
 * Ticking one subtask, which can finish or unfinish the task above it.
 *
 * The task follows its parts: done exactly when all of them are. So unticking
 * one part of a finished task unfinishes the task and gives the counter its
 * point back, in the same write — the two used to be able to come apart, and
 * that is how a balance could be walked upward.
 *
 * Nothing changes if [subtaskId] names nothing, or if it names a subtask whose
 * task is missing: that is a dangling row, and inventing a task to tick would
 * be worse than doing nothing.
 */
fun stateAfterTogglingSubtask(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    counters: List<Counter>,
    subtaskId: Long,
): DoneChange {
    val victim = subtasks.firstOrNull { it.id == subtaskId }
        ?: return DoneChange(tasks, subtasks, counters)

    if (tasks.none { it.id == victim.taskId }) return DoneChange(tasks, subtasks, counters)

    val newSubtasks = subtasks.map { s ->
        if (s.id == subtaskId) s.copy(isDone = !s.isDone) else s
    }

    val siblings = newSubtasks.filter { it.taskId == victim.taskId }
    val allDone = siblings.isNotEmpty() && siblings.all { it.isDone }

    val applied = applyTaskDoneFlags(tasks, counters) { t ->
        if (t.id == victim.taskId) allDone else t.isDone
    }

    return DoneChange(applied.tasks, newSubtasks, applied.counters)
}

/**
 * Every task's flag rebuilt from the subtasks it now has.
 *
 * For after a subtask has moved between tasks, where both the task it left and
 * the one it joined may have changed their minds about being finished.
 *
 * A task with no subtasks keeps its flag: there is nothing to derive it from,
 * and choosing either answer would be inventing one.
 */
fun stateWithTaskDoneRecomputed(
    tasks: List<Task>,
    subtasks: List<Subtask>,
    counters: List<Counter>,
): TasksAndCounters {
    val subsByTask = subtasks.groupBy { it.taskId }

    return applyTaskDoneFlags(tasks, counters) { t ->
        val subs = subsByTask[t.id].orEmpty()
        if (subs.isEmpty()) t.isDone else subs.all { it.isDone }
    }
}
