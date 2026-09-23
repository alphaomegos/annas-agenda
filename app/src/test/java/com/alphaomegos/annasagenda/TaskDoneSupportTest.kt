package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins that a task's done flag and its linked counter can only move together.
 *
 * The regression worth remembering: the rule was written out four times and
 * two of them moved the flag without the counter, so a balance could be grown
 * out of nothing — delete a subtask until the task falls into "done" for free,
 * then untick the task and collect the +1.
 */
class TaskDoneSupportTest {

    private fun task(
        id: Long,
        isDone: Boolean = false,
        linkedManualCounterId: Long? = null,
    ) = Task(
        id = id,
        date = LocalDate.of(2026, 9, 23),
        description = "Task $id",
        isDone = isDone,
        linkedManualCounterId = linkedManualCounterId,
    )

    private fun manual(id: Long, balance: Int) =
        ManualCounter(id = id, title = "Counter $id", balance = balance)

    private fun balanceOf(counters: List<Counter>, id: Long): Int =
        counters.filterIsInstance<ManualCounter>().single { it.id == id }.balance

    @Test
    fun markingATaskDoneTakesOneOffItsCounter() {
        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            counters = listOf(manual(10L, 5)),
        ) { true }

        assertTrue(result.tasks.single().isDone)
        assertEquals(4, balanceOf(result.counters, 10L))
    }

    @Test
    fun unmarkingATaskPutsTheOneBack() {
        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            counters = listOf(manual(10L, 4)),
        ) { false }

        assertEquals(5, balanceOf(result.counters, 10L))
    }

    /** The half of the rule that used to go missing. */
    @Test
    fun aFlagThatChangesWithoutAToggleStillMovesTheCounter() {
        // What deleteSubtask and recomputeTaskDoneFromSubtasks do: the task
        // becomes done because of what happened to its subtasks, not because
        // anyone ticked it.
        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            counters = listOf(manual(10L, 5)),
        ) { it.id == 1L }

        assertEquals(4, balanceOf(result.counters, 10L))
    }

    @Test
    fun aFlagThatDoesNotChangeMovesNothing() {
        val counters = listOf(manual(10L, 5))

        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            counters = counters,
        ) { true }

        assertEquals(5, balanceOf(result.counters, 10L))
        assertSame("nothing moved, so the list is handed straight back", counters, result.counters)
    }

    @Test
    fun aTaskWithoutALinkedCounterLeavesEveryBalanceAlone() {
        val counters = listOf(manual(10L, 5))

        val result = applyTaskDoneFlags(
            tasks = listOf(task(1)),
            counters = counters,
        ) { true }

        assertTrue(result.tasks.single().isDone)
        assertSame(counters, result.counters)
    }

    @Test
    fun twoTasksOnOneCounterMoveItTwice() {
        val result = applyTaskDoneFlags(
            tasks = listOf(
                task(1, linkedManualCounterId = 10L),
                task(2, linkedManualCounterId = 10L),
            ),
            counters = listOf(manual(10L, 5)),
        ) { true }

        assertEquals(3, balanceOf(result.counters, 10L))
    }

    @Test
    fun movesInOppositeDirectionsCancelOut() {
        val result = applyTaskDoneFlags(
            tasks = listOf(
                task(1, linkedManualCounterId = 10L),
                task(2, isDone = true, linkedManualCounterId = 10L),
            ),
            counters = listOf(manual(10L, 5)),
        ) { it.id == 1L }

        assertEquals(5, balanceOf(result.counters, 10L))
    }

    @Test
    fun onlyTheLinkedCounterMoves() {
        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            counters = listOf(manual(10L, 5), manual(11L, 5)),
        ) { true }

        assertEquals(4, balanceOf(result.counters, 10L))
        assertEquals(5, balanceOf(result.counters, 11L))
    }

    /** A date-range counter has no balance to move, even if a task points at it. */
    @Test
    fun aCounterThatIsNotManualIsLeftUntouched() {
        val range = DateRangeCounter(
            id = 10L,
            title = "Trip",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30),
        )

        val result = applyTaskDoneFlags(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            counters = listOf(range),
        ) { true }

        assertEquals(listOf(range), result.counters)
    }

    /* ---------------- the delta helper on its own ---------------- */

    @Test
    fun aDeltaOfZeroChangesNothing() {
        val counters = listOf(manual(10L, 5))

        assertEquals(5, balanceOf(countersWithManualCounterDelta(counters, 10L, 0), 10L))
    }

    @Test
    fun aDeltaForAnUnknownCounterIsIgnored() {
        val counters = listOf(manual(10L, 5))

        assertEquals(counters, countersWithManualCounterDelta(counters, 999L, -1))
    }

    @Test
    fun aBalanceIsAllowedToGoNegative() {
        // Nothing in the app clamps it, and pretending otherwise here would
        // hide that from whoever changes this next.
        val counters = listOf(manual(10L, 0))

        assertEquals(-1, balanceOf(countersWithManualCounterDelta(counters, 10L, -1), 10L))
    }
}
