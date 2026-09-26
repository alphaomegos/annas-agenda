package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Deleting one row out of a day.
 *
 * Three different things wear the same button. Getting them confused either
 * cancels a schedule the user set up weeks ago, or undoes the deletion the
 * moment the day is drawn again — and neither is visible from the screen.
 */
class TaskDeletionSupportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val weekStart = DayOfWeek.MONDAY
    private val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart)

    private fun template(id: Long = 1L) = Task(
        id = id,
        order = 0,
        date = monday,
        description = "water the plants",
        repeatRule = daily,
    )

    private fun materialised(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
    ) = generateRecurrencesInRange(
        tasks = tasks,
        subtasks = subtasks,
        suppressedRecurrences = emptySet(),
        start = monday,
        end = monday.plusDays(4),
        nextId = 1000L,
        defaultWeekStart = weekStart,
    )

    private fun deleteTask(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        suppressed: Set<String> = emptySet(),
        plan: List<RunningPlanEntry> = emptyList(),
        taskId: Long,
    ) = stateAfterDeletingTask(tasks, subtasks, suppressed, plan, taskId)

    /* ---------- an occurrence ---------- */

    @Test
    fun anOccurrenceGoesAndItsDayIsTombstoned() {
        val generated = materialised(listOf(template()))
        val day = monday.plusDays(2)
        val victim = generated.tasks.first { it.originTaskId == 1L && it.date == day }

        val after = deleteTask(generated.tasks, generated.subtasks, taskId = victim.id)

        assertFalse(after.tasks.any { it.id == victim.id })
        assertTrue(taskSuppressionKey(1L, day) in after.suppressedRecurrences)
    }

    /** The tombstone is only worth anything if generating again respects it. */
    @Test
    fun aDeletedOccurrenceStaysDeleted() {
        val generated = materialised(listOf(template()))
        val day = monday.plusDays(2)
        val victim = generated.tasks.first { it.originTaskId == 1L && it.date == day }

        val after = deleteTask(generated.tasks, generated.subtasks, taskId = victim.id)

        val rebuilt = generateRecurrencesInRange(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
            start = monday,
            end = monday.plusDays(4),
            nextId = 9000L,
            defaultWeekStart = weekStart,
        )

        assertEquals(0, rebuilt.tasks.count { it.date == day })
    }

    @Test
    fun anOccurrenceTakesItsSubtasksWithIt() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen")),
        )
        val victim = generated.tasks.first { it.originTaskId == 1L && it.date == monday.plusDays(2) }

        val after = deleteTask(generated.tasks, generated.subtasks, taskId = victim.id)

        assertFalse(after.subtasks.any { it.taskId == victim.id })
    }

    /* ---------- a template that repeats ---------- */

    /**
     * Deleting the row in front of you is not the same as cancelling the
     * schedule behind it. The template stays and the rule keeps running; only
     * its own day goes quiet.
     */
    @Test
    fun aRepeatingTemplateIsHiddenOnItsOwnDayRatherThanDeleted() {
        val generated = materialised(listOf(template()))

        val after = deleteTask(generated.tasks, generated.subtasks, taskId = 1L)

        val survivor = after.tasks.first { it.id == 1L }
        assertEquals(daily, survivor.repeatRule)
        assertTrue(taskSuppressionKey(1L, monday) in after.suppressedRecurrences)
    }

    @Test
    fun theOtherDaysOfThatRepeatGoOnBeingProduced() {
        val tasks = listOf(template())

        val after = deleteTask(tasks, taskId = 1L)

        val rebuilt = generateRecurrencesInRange(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
            start = monday,
            end = monday.plusDays(4),
            nextId = 9000L,
            defaultWeekStart = weekStart,
        )

        assertEquals(4, rebuilt.tasks.count { it.originTaskId == 1L })
    }

    /** A template with no day of its own has no row to hide, so it is deleted. */
    @Test
    fun aRepeatingTemplateWithNoDateIsDeletedOutright() {
        val tasks = listOf(Task(id = 1L, description = "someday, repeatedly", repeatRule = daily))

        val after = deleteTask(tasks, taskId = 1L)

        assertTrue(after.tasks.isEmpty())
        assertTrue(after.suppressedRecurrences.isEmpty())
    }

    /* ---------- an ordinary task ---------- */

    @Test
    fun anOrdinaryTaskAndItsSubtasksSimplyGo() {
        val tasks = listOf(
            Task(id = 1L, order = 0, date = monday, description = "dentist", hasSubtasks = true),
            Task(id = 2L, order = 1, date = monday, description = "stays"),
        )
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "call"),
            Subtask(id = 11L, taskId = 2L, description = "keep me"),
        )

        val after = deleteTask(tasks, subtasks, taskId = 1L)

        assertEquals(listOf(2L), after.tasks.map { it.id })
        assertEquals(listOf(11L), after.subtasks.map { it.id })
        assertTrue(after.suppressedRecurrences.isEmpty())
    }

    @Test
    fun aTaskThatLosesItsSubtasksIsToldSo() {
        val tasks = listOf(
            Task(id = 1L, date = monday, description = "gone", hasSubtasks = true),
            Task(id = 2L, date = monday, description = "stays", hasSubtasks = true),
        )
        val subtasks = listOf(Subtask(id = 10L, taskId = 1L, description = "only one"))

        val after = deleteTask(tasks, subtasks, taskId = 1L)

        assertFalse(after.tasks.first { it.id == 2L }.hasSubtasks)
    }

    /**
     * A plan row holding the id of a task that no longer exists is a
     * reference waiting to be handed to something else.
     */
    @Test
    fun aRunningPlanRowLetsGoOfADeletedTask() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "5 km"))
        val plan = listOf(
            RunningPlanEntry(date = monday, distanceKmText = "5", taskId = 1L),
            RunningPlanEntry(date = monday.plusDays(1), taskId = 77L),
        )

        val after = deleteTask(tasks, plan = plan, taskId = 1L)

        assertNull(after.runningPlanEntries.first().taskId)
        assertEquals("5", after.runningPlanEntries.first().distanceKmText)
        assertEquals(77L, after.runningPlanEntries[1].taskId)
    }

    @Test
    fun anIdThatNamesNothingChangesNothing() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "x"))

        val after = deleteTask(tasks, taskId = 404L)

        assertSame(tasks, after.tasks)
    }

    /* ---------- subtasks ---------- */

    private fun deleteSubtask(
        tasks: List<Task>,
        subtasks: List<Subtask>,
        suppressed: Set<String> = emptySet(),
        counters: List<Counter> = emptyList(),
        subtaskId: Long,
    ) = stateAfterDeletingSubtask(tasks, subtasks, suppressed, counters, subtaskId)

    /**
     * Keyed on the template subtask, not on the copy. The generator asks about
     * the template; this copy's own id means nothing to it, so a tombstone
     * carrying that id would not stop anything.
     */
    @Test
    fun aGeneratedSubtaskTombstonesItsTemplateAndItsDay() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen")),
        )
        val day = monday.plusDays(2)
        val carrier = generated.tasks.first { it.originTaskId == 1L && it.date == day }
        val victim = generated.subtasks.first { it.taskId == carrier.id }

        val after = deleteSubtask(generated.tasks, generated.subtasks, subtaskId = victim.id)

        assertTrue(subtaskSuppressionKey(10L, day) in after.suppressedRecurrences)
        assertFalse(after.subtasks.any { it.id == victim.id })
    }

    @Test
    fun aDeletedGeneratedSubtaskStaysDeleted() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen")),
        )
        val day = monday.plusDays(2)
        val carrier = generated.tasks.first { it.originTaskId == 1L && it.date == day }
        val victim = generated.subtasks.first { it.taskId == carrier.id }

        val after = deleteSubtask(generated.tasks, generated.subtasks, subtaskId = victim.id)

        val rebuilt = generateRecurrencesInRange(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
            start = monday,
            end = monday.plusDays(4),
            nextId = 9000L,
            defaultWeekStart = weekStart,
        )

        assertEquals(0, rebuilt.subtasks.count { it.taskId == carrier.id })
    }

    @Test
    fun anOrdinarySubtaskTombstonesNothing() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "x", hasSubtasks = true))
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "a"),
            Subtask(id = 11L, taskId = 1L, description = "b"),
        )

        val after = deleteSubtask(tasks, subtasks, subtaskId = 10L)

        assertTrue(after.suppressedRecurrences.isEmpty())
        assertEquals(listOf(11L), after.subtasks.map { it.id })
    }

    @Test
    fun takingTheLastUnfinishedSubtaskAwayFinishesTheTask() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "x", hasSubtasks = true))
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "done", isDone = true),
            Subtask(id = 11L, taskId = 1L, description = "not done", isDone = false),
        )

        val after = deleteSubtask(tasks, subtasks, subtaskId = 11L)

        assertTrue(after.tasks.first { it.id == 1L }.isDone)
    }

    /**
     * Finishing a task moves the counter it is linked to. This is the
     * arithmetic that once let the counter climb a point at a time, because
     * the flag and the counter were changed in two different places.
     */
    @Test
    fun aTaskThatFinishesThisWayMovesItsCounter() {
        val tasks = listOf(
            Task(id = 1L, date = monday, description = "push-ups", hasSubtasks = true, linkedManualCounterId = 50L),
        )
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "set one", isDone = true),
            Subtask(id = 11L, taskId = 1L, description = "set two", isDone = false),
        )
        val counters = listOf(ManualCounter(id = 50L, title = "push-ups", balance = 5))

        val after = deleteSubtask(tasks, subtasks, counters = counters, subtaskId = 11L)

        assertTrue(after.tasks.first { it.id == 1L }.isDone)
        assertEquals(4, (after.counters.first { it.id == 50L } as ManualCounter).balance)
    }

    @Test
    fun aTaskThatWasAlreadyFinishedDoesNotMoveItsCounterTwice() {
        val tasks = listOf(
            Task(id = 1L, date = monday, description = "push-ups", hasSubtasks = true, isDone = true, linkedManualCounterId = 50L),
        )
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "set one", isDone = true),
            Subtask(id = 11L, taskId = 1L, description = "set two", isDone = true),
        )
        val counters = listOf(ManualCounter(id = 50L, title = "push-ups", balance = 5))

        val after = deleteSubtask(tasks, subtasks, counters = counters, subtaskId = 11L)

        assertEquals(5, (after.counters.first { it.id == 50L } as ManualCounter).balance)
    }

    /**
     * With nothing left to derive it from, the flag stays where it was.
     * Choosing either answer would be inventing one.
     */
    @Test
    fun aTaskLeftWithNoSubtasksKeepsItsFlagAndItsCounter() {
        val tasks = listOf(
            Task(id = 1L, date = monday, description = "x", hasSubtasks = true, linkedManualCounterId = 50L),
        )
        val subtasks = listOf(Subtask(id = 10L, taskId = 1L, description = "only one"))
        val counters = listOf(ManualCounter(id = 50L, title = "c", balance = 5))

        val after = deleteSubtask(tasks, subtasks, counters = counters, subtaskId = 10L)

        val task = after.tasks.first { it.id == 1L }
        assertFalse(task.isDone)
        assertFalse("and it now says it has none", task.hasSubtasks)
        assertEquals(5, (after.counters.first { it.id == 50L } as ManualCounter).balance)
    }

    @Test
    fun aSubtaskIdThatNamesNothingChangesNothing() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "x"))
        val subtasks = listOf(Subtask(id = 10L, taskId = 1L, description = "a"))

        val after = deleteSubtask(tasks, subtasks, subtaskId = 404L)

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    /* ---------- the counter that is deliberately not refunded ---------- */

    /**
     * A done task has already taken one off its manual counter, and deleting
     * the task does not give it back. The counter says how much is left to do;
     * the work was done, and tidying the row out of the calendar afterwards
     * does not undo it.
     *
     * There is nothing here to assert that against, and that is the point:
     * this function has no counters to move. The compiler is the guard —
     * a refund cannot be added without widening TaskDeletionResult, and
     * widening it means editing the file that explains why it is narrow.
     * What is checked instead is the neighbour that could go wrong quietly:
     * another task pointing at the same counter must come through untouched.
     */
    @Test
    fun deletingADoneTaskTakesOnlyItsOwnLinkWithIt() {
        val done = Task(
            id = 1L,
            date = monday,
            description = "push-ups",
            isDone = true,
            linkedManualCounterId = 10L,
        )
        val sameCounter = Task(
            id = 2L,
            date = monday,
            description = "squats",
            isDone = true,
            linkedManualCounterId = 10L,
        )

        val after = deleteTask(tasks = listOf(done, sameCounter), taskId = 1L)

        assertEquals(listOf(2L), after.tasks.map { it.id })
        assertEquals(sameCounter, after.tasks.single())
    }

    /**
     * The other half of the pair: taking the last unfinished part away moves
     * the parent's flag, so here the counter does move.
     */
    @Test
    fun deletingTheLastUnfinishedPartMovesTheCounter() {
        val parent = Task(
            id = 1L,
            date = monday,
            description = "push-ups",
            hasSubtasks = true,
            linkedManualCounterId = 10L,
        )
        val subtasks = listOf(
            Subtask(id = 10L, taskId = 1L, description = "left", isDone = true),
            Subtask(id = 11L, taskId = 1L, description = "right", isDone = false),
        )

        val after = deleteSubtask(
            tasks = listOf(parent),
            subtasks = subtasks,
            counters = listOf(ManualCounter(id = 10L, title = "Push-ups", balance = 5)),
            subtaskId = 11L,
        )

        assertTrue("the parent falls into done", after.tasks.single().isDone)
        assertEquals(
            4,
            after.counters.filterIsInstance<ManualCounter>().single().balance,
        )
    }
}
