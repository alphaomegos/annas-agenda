package com.alphaomegos.annasagenda

/**
 * The largest id anything in [state] is currently using.
 *
 * Every collection that hands out ids from the one counter has to be counted
 * here. Leaving one out means the counter restarts below something that
 * exists, and the next thing created quietly takes its number.
 */
fun highestIdIn(state: AppState): Long =
    listOf(
        state.tasks.maxOfOrNull { it.id },
        state.subtasks.maxOfOrNull { it.id },
        state.foodLog.maxOfOrNull { it.id },
        state.counters.maxOfOrNull { it.id },
        state.readingBooks.maxOfOrNull { it.id },
        state.readingMovies.maxOfOrNull { it.id },
        state.readingSeries.maxOfOrNull { it.id },
        state.readingSessions.maxOfOrNull { it.id },
    ).filterNotNull().maxOrNull() ?: 0L

/**
 * The next id to hand out: past everything that exists, and past everything
 * that ever existed.
 *
 * The second half is the point. Counting what exists is not enough, because
 * things get deleted: delete the newest task, restart the app, and the counter
 * comes back one lower and hands that id to the next thing created. That is
 * harmless until something outside the collections is still holding the old
 * number — and two things are.
 *
 * A running-plan row holds the id of the task it made. Reset the plan and it
 * deletes by that id, which after a restart may belong to something the user
 * wrote themselves.
 *
 * A tombstone holds the id of the template whose day it silences. Reuse that
 * id for a new repeating task and the new task is born with holes in its
 * schedule, punched by somebody else's deletions.
 *
 * So the state also carries a high-water mark, which only ever goes up, and
 * the counter starts from whichever is higher. Pruning generated occurrences
 * on the way to disk (see stateWithDerivableOccurrencesDropped) made the plain
 * count drop far more often than it used to, which is what turned a rare
 * coincidence into something worth closing.
 */
fun nextIdFor(state: AppState): Long =
    maxOf(highestIdIn(state) + 1L, state.idHighWater)

/**
 * The high-water mark raised to at least [nextId], never lowered.
 *
 * Lowering it would be the bug this exists to prevent, so it is refused here
 * rather than trusted not to happen at the call sites.
 */
fun stateWithIdHighWaterAtLeast(state: AppState, nextId: Long): AppState =
    if (state.idHighWater >= nextId) state else state.copy(idHighWater = nextId)
