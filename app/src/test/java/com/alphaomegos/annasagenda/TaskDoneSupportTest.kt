package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /* ---------- ticking a box, which is what the user actually does ---------- */

    private fun sub(id: Long, taskId: Long, isDone: Boolean = false) =
        Subtask(id = id, taskId = taskId, description = "Subtask $id", isDone = isDone)

    @Test
    fun tickingATaskWithNoPartsJustTicksIt() {
        val after = stateAfterTogglingTask(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            subtasks = emptyList(),
            counters = listOf(manual(10L, 5)),
            taskId = 1L,
        )

        assertTrue(after.tasks.single().isDone)
        assertEquals(4, balanceOf(after.counters, 10L))
    }

    @Test
    fun untickingATaskGivesThePointBack() {
        val after = stateAfterTogglingTask(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            subtasks = emptyList(),
            counters = listOf(manual(10L, 4)),
            taskId = 1L,
        )

        assertFalse(after.tasks.single().isDone)
        assertEquals(5, balanceOf(after.counters, 10L))
    }

    /**
     * A task with parts is done because its parts are, so ticking the task
     * ticks them — and unticking it unticks them. The counter moves once, for
     * the task, never once per part.
     */
    @Test
    fun tickingATaskTicksEverythingUnderIt() {
        val after = stateAfterTogglingTask(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L), sub(11L, 1L, isDone = true)),
            counters = listOf(manual(10L, 5)),
            taskId = 1L,
        )

        assertTrue(after.subtasks.all { it.isDone })
        assertEquals("the counter moved once, not once per part", 4, balanceOf(after.counters, 10L))
    }

    @Test
    fun untickingATaskUnticksEverythingUnderIt() {
        val after = stateAfterTogglingTask(
            tasks = listOf(task(1, isDone = true)),
            subtasks = listOf(sub(10L, 1L, isDone = true), sub(11L, 1L, isDone = true)),
            counters = emptyList(),
            taskId = 1L,
        )

        assertTrue(after.subtasks.none { it.isDone })
    }

    @Test
    fun tickingATaskLeavesOtherTasksParts() {
        val after = stateAfterTogglingTask(
            tasks = listOf(task(1), task(2)),
            subtasks = listOf(sub(10L, 1L), sub(20L, 2L)),
            counters = emptyList(),
            taskId = 1L,
        )

        assertTrue(after.subtasks.single { it.id == 10L }.isDone)
        assertFalse(after.subtasks.single { it.id == 20L }.isDone)
    }

    @Test
    fun tickingTheLastPartFinishesTheTaskAndMovesItsCounter() {
        val after = stateAfterTogglingSubtask(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = true), sub(11L, 1L)),
            counters = listOf(manual(10L, 5)),
            subtaskId = 11L,
        )

        assertTrue(after.tasks.single().isDone)
        assertEquals(4, balanceOf(after.counters, 10L))
    }

    /**
     * The other direction, in one write. These two used to be able to come
     * apart, and that is how a balance could be walked upward.
     */
    @Test
    fun untickingOnePartUnfinishesTheTaskAndGivesThePointBack() {
        val after = stateAfterTogglingSubtask(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = true), sub(11L, 1L, isDone = true)),
            counters = listOf(manual(10L, 4)),
            subtaskId = 11L,
        )

        assertFalse(after.tasks.single().isDone)
        assertEquals(5, balanceOf(after.counters, 10L))
    }

    @Test
    fun tickingOneOfSeveralPartsLeavesTheTaskUnfinished() {
        val after = stateAfterTogglingSubtask(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L), sub(11L, 1L)),
            counters = listOf(manual(10L, 5)),
            subtaskId = 10L,
        )

        assertFalse(after.tasks.single().isDone)
        assertEquals("nothing finished, so nothing moved", 5, balanceOf(after.counters, 10L))
    }

    /**
     * A subtask whose task is gone is a dangling row. Ticking it must not
     * invent a task to finish.
     */
    @Test
    fun aPartWithNoTaskChangesNothing() {
        val tasks = listOf(task(1))
        val subtasks = listOf(sub(99L, 404L))

        val after = stateAfterTogglingSubtask(tasks, subtasks, emptyList(), 99L)

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    @Test
    fun anIdThatNamesNothingChangesNothing() {
        val tasks = listOf(task(1))
        val subtasks = listOf(sub(10L, 1L))

        assertSame(tasks, stateAfterTogglingTask(tasks, subtasks, emptyList(), 404L).tasks)
        assertSame(subtasks, stateAfterTogglingSubtask(tasks, subtasks, emptyList(), 404L).subtasks)
    }

    /* ---------- rebuilding a flag after a part has moved ---------- */

    @Test
    fun recomputing_finishesATaskWhoseLastUnfinishedPartLeft() {
        val after = stateWithTaskDoneRecomputed(
            tasks = listOf(task(1, linkedManualCounterId = 10L), task(2)),
            subtasks = listOf(sub(10L, 1L, isDone = true), sub(11L, 2L)),
            counters = listOf(manual(10L, 5)),
        )

        assertTrue(after.tasks.single { it.id == 1L }.isDone)
        assertFalse(after.tasks.single { it.id == 2L }.isDone)
        assertEquals(4, balanceOf(after.counters, 10L))
    }

    @Test
    fun recomputing_unfinishesATaskThatJustGainedAnUnfinishedPart() {
        val after = stateWithTaskDoneRecomputed(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = true), sub(11L, 1L)),
            counters = listOf(manual(10L, 4)),
        )

        assertFalse(after.tasks.single().isDone)
        assertEquals(5, balanceOf(after.counters, 10L))
    }

    /** Nothing to derive it from, so it keeps what it had. */
    @Test
    fun recomputing_leavesATaskWithNoPartsAlone() {
        val done = stateWithTaskDoneRecomputed(listOf(task(1, isDone = true)), emptyList(), emptyList())
        val notDone = stateWithTaskDoneRecomputed(listOf(task(2)), emptyList(), emptyList())

        assertTrue(done.tasks.single().isDone)
        assertFalse(notDone.tasks.single().isDone)
    }

    /* ---------- adding a part to a task ---------- */

    @Test
    fun aNewPartGoesAfterTheOnesAlreadyThere() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1)),
            subtasks = listOf(sub(10L, 1L), sub(11L, 1L).copy(order = 4)),
            counters = emptyList(),
            taskId = 1L,
            description = "third",
            colorArgb = null,
            id = 99L,
        )

        assertEquals(5, after.subtasks.single { it.id == 99L }.order)
        assertEquals(99L, after.createdId)
    }

    @Test
    fun aNewPartIsCountedOnlyAgainstItsOwnTask() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1), task(2)),
            subtasks = listOf(sub(10L, 2L).copy(order = 9)),
            counters = emptyList(),
            taskId = 1L,
            description = "first here",
            colorArgb = null,
            id = 99L,
        )

        assertEquals(0, after.subtasks.single { it.id == 99L }.order)
    }

    @Test
    fun aTaskThatGainsItsFirstPartSaysSo() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1)),
            subtasks = emptyList(),
            counters = emptyList(),
            taskId = 1L,
            description = "  padded  ",
            colorArgb = 0xFF00FF00L,
            id = 99L,
        )

        assertTrue(after.tasks.single().hasSubtasks)
        assertEquals("padded", after.subtasks.single().description)
        assertEquals(0xFF00FF00L, after.subtasks.single().colorArgb)
        assertFalse(after.subtasks.single().isDone)
    }

    /**
     * A task is finished exactly when all its parts are, and its counter has
     * already been paid for that. An unticked part joining a finished task
     * would leave the two disagreeing, and the next tick of that part would
     * finish the task a second time and take another point off.
     */
    @Test
    fun aPartJoiningAFinishedTaskArrivesFinished() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = true)),
            counters = listOf(manual(10L, 4)),
            taskId = 1L,
            description = "one more",
            colorArgb = null,
            id = 99L,
        )

        assertTrue(after.subtasks.single { it.id == 99L }.isDone)
        assertTrue(after.tasks.single().isDone)
        assertEquals("the task did not finish twice", 4, balanceOf(after.counters, 10L))
    }

    @Test
    fun aPartJoiningAnUnfinishedTaskArrivesUnfinished() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = true)),
            counters = listOf(manual(10L, 5)),
            taskId = 1L,
            description = "one more",
            colorArgb = null,
            id = 99L,
        )

        assertFalse(after.subtasks.single { it.id == 99L }.isDone)
        assertFalse(after.tasks.single().isDone)
        assertEquals(5, balanceOf(after.counters, 10L))
    }

    /**
     * A finished task whose parts already disagreed with it — which a payload
     * from an older version can hold — has the disagreement put right, and the
     * counter gets back the point it should never have been charged.
     */
    @Test
    fun aPartJoiningAFinishedTaskWhosePartsDisagreePutsItRight() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1, isDone = true, linkedManualCounterId = 10L)),
            subtasks = listOf(sub(10L, 1L, isDone = false)),
            counters = listOf(manual(10L, 4)),
            taskId = 1L,
            description = "one more",
            colorArgb = null,
            id = 99L,
        )

        assertFalse(after.tasks.single().isDone)
        assertEquals(5, balanceOf(after.counters, 10L))
    }

    /** A part with no task is a dangling row, not an error. */
    @Test
    fun aPartCanBeMadeForATaskThatIsNotThere() {
        val after = stateAfterCreatingSubtask(
            tasks = listOf(task(1)),
            subtasks = emptyList(),
            counters = emptyList(),
            taskId = 404L,
            description = "orphan",
            colorArgb = null,
            id = 99L,
        )

        assertEquals(404L, after.subtasks.single().taskId)
        assertFalse(after.tasks.single().hasSubtasks)
    }
}
