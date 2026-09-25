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
 * Dragging a task to another day.
 *
 * The case that carries the weight is an occurrence of a repeat: moving it has
 * to detach it from its template and tombstone the day it left, or the
 * generator puts a fresh copy back where it was and the user has the task
 * twice. None of that was reachable from a terminal before.
 */
class RescheduleSupportTest {

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

    private fun reschedule(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        suppressed: Set<String> = emptySet(),
        taskId: Long,
        to: LocalDate?,
    ) = stateAfterReschedulingTask(tasks, subtasks, suppressed, taskId, to)

    /* ---------- an ordinary task ---------- */

    @Test
    fun anOrdinaryTaskLandsAtTheBottomOfItsNewDay() {
        val tasks = listOf(
            Task(id = 1L, order = 0, date = monday, description = "move me"),
            Task(id = 2L, order = 0, date = monday.plusDays(1), description = "already there"),
            Task(id = 3L, order = 1, date = monday.plusDays(1), description = "and there"),
        )

        val after = reschedule(tasks, taskId = 1L, to = monday.plusDays(1))
        val moved = after.tasks.first { it.id == 1L }

        assertEquals(monday.plusDays(1), moved.date)
        assertEquals(2, moved.order)
    }

    /**
     * Dropping a task back on the day it is already on must not shuffle it to
     * the bottom: nothing the user did asked for that.
     */
    @Test
    fun movingATaskToTheDayItIsAlreadyOnKeepsItsPlace() {
        val tasks = listOf(
            Task(id = 1L, order = 0, date = monday, description = "first"),
            Task(id = 2L, order = 1, date = monday, description = "second"),
        )

        val after = reschedule(tasks, taskId = 1L, to = monday)

        assertEquals(0, after.tasks.first { it.id == 1L }.order)
    }

    @Test
    fun aTaskCanBeMovedOffTheCalendarAltogether() {
        val tasks = listOf(
            Task(id = 1L, order = 5, date = monday, description = "someday"),
            Task(id = 2L, order = 0, date = null, description = "already someday"),
        )

        val after = reschedule(tasks, taskId = 1L, to = null)
        val moved = after.tasks.first { it.id == 1L }

        assertNull(moved.date)
        assertEquals(1, moved.order)
    }

    @Test
    fun anOrdinaryMoveTombstonesNothing() {
        val tasks = listOf(Task(id = 1L, order = 0, date = monday, description = "plain"))

        val after = reschedule(tasks, taskId = 1L, to = monday.plusDays(1))

        assertTrue(after.suppressedRecurrences.isEmpty())
    }

    @Test
    fun anIdThatNamesNothingChangesNothing() {
        val tasks = listOf(Task(id = 1L, order = 0, date = monday, description = "plain"))
        val subtasks = listOf(Subtask(id = 10L, taskId = 1L, description = "a"))

        val after = reschedule(tasks, subtasks, taskId = 404L, to = monday)

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    /* ---------- an occurrence of a repeat ---------- */

    private fun occurrenceOn(generated: RecurrenceGenerationResult, date: LocalDate) =
        generated.tasks.first { it.originTaskId == 1L && it.date == date }

    @Test
    fun aMovedOccurrenceStopsBeingOne() {
        val generated = materialised(listOf(template()))
        val victim = occurrenceOn(generated, monday.plusDays(1))

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )
        val moved = after.tasks.first { it.id == victim.id }

        assertNull("it is nobody's occurrence now", moved.originTaskId)
        assertNull(moved.repeatRule)
        assertEquals(monday.plusDays(3), moved.date)
        assertEquals("water the plants", moved.description)
    }

    @Test
    fun theDayItLeftIsTombstoned() {
        val generated = materialised(listOf(template()))
        val from = monday.plusDays(1)
        val victim = occurrenceOn(generated, from)

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )

        assertTrue(taskSuppressionKey(1L, from) in after.suppressedRecurrences)
    }

    /**
     * The whole point of the tombstone: generate the range again and the day
     * it was dragged off stays empty instead of sprouting a copy.
     */
    @Test
    fun theTaskDoesNotComeBackOnTheDayItWasDraggedOff() {
        val generated = materialised(listOf(template()))
        val from = monday.plusDays(1)
        val victim = occurrenceOn(generated, from)

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )

        val rebuilt = generateRecurrencesInRange(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
            start = monday,
            end = monday.plusDays(4),
            nextId = 9000L,
            defaultWeekStart = weekStart,
        )

        assertFalse(rebuilt.tasks.any { it.originTaskId == 1L && it.date == from })
        assertEquals(
            "the day the user dragged it off is empty",
            0,
            rebuilt.tasks.count { it.date == from },
        )
    }

    @Test
    fun itsGeneratedSubtasksComeWithItAndStopBeingGenerated() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(
                Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen"),
                Subtask(id = 11L, order = 1, taskId = 1L, description = "balcony"),
            ),
        )
        val from = monday.plusDays(1)
        val victim = occurrenceOn(generated, from)

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )

        val itsSubs = after.subtasks.filter { it.taskId == victim.id }
        assertEquals(2, itsSubs.size)
        assertTrue(itsSubs.all { it.originSubtaskId == null && it.repeatRule == null })
        assertEquals(setOf("kitchen", "balcony"), itsSubs.mapTo(mutableSetOf()) { it.description })
    }

    @Test
    fun eachOfItsSubtasksTombstonesTheDayItLeft() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(
                Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen"),
                Subtask(id = 11L, order = 1, taskId = 1L, description = "balcony"),
            ),
        )
        val from = monday.plusDays(1)
        val victim = occurrenceOn(generated, from)

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )

        assertTrue(subtaskSuppressionKey(10L, from) in after.suppressedRecurrences)
        assertTrue(subtaskSuppressionKey(11L, from) in after.suppressedRecurrences)
        assertTrue(taskSuppressionKey(1L, from) in after.suppressedRecurrences)
    }

    @Test
    fun theOtherDaysOfTheRepeatAreUntouched() {
        val generated = materialised(listOf(template()))
        val victim = occurrenceOn(generated, monday.plusDays(1))

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = monday.plusDays(3),
        )

        val stillOccurrences = after.tasks
            .filter { it.originTaskId == 1L }
            .map { it.date }
            .toSet()

        assertEquals(setOf(monday.plusDays(2), monday.plusDays(3), monday.plusDays(4)), stillOccurrences)
        assertEquals(daily, after.tasks.first { it.id == 1L }.repeatRule)
    }

    /**
     * Dropping an occurrence back on its own day is not a move, so it must not
     * tombstone the day — which would delete it the next time the day is drawn.
     */
    @Test
    fun droppingAnOccurrenceBackOnItsOwnDayIsNotAMove() {
        val generated = materialised(listOf(template()))
        val day = monday.plusDays(1)
        val victim = occurrenceOn(generated, day)

        val after = reschedule(
            generated.tasks, generated.subtasks,
            taskId = victim.id, to = day,
        )

        assertTrue(after.suppressedRecurrences.isEmpty())
        assertEquals(1L, after.tasks.first { it.id == victim.id }.originTaskId)
    }

    @Test
    fun anExistingTombstoneIsKept() {
        val generated = materialised(listOf(template()))
        val victim = occurrenceOn(generated, monday.plusDays(1))
        val old = setOf(taskSuppressionKey(1L, monday.plusDays(9)))

        val after = reschedule(
            generated.tasks, generated.subtasks,
            suppressed = old,
            taskId = victim.id, to = monday.plusDays(3),
        )

        assertTrue(after.suppressedRecurrences.containsAll(old))
    }

    /* ---------- where a new row lands ---------- */

    @Test
    fun theNextOrderOnAnEmptyDayIsZero() {
        assertEquals(0, nextTaskOrderOn(emptyList(), monday))
        assertEquals(
            0,
            nextTaskOrderOn(listOf(Task(id = 1L, order = 7, date = monday, description = "x")), monday.plusDays(1)),
        )
    }

    @Test
    fun theNextOrderIgnoresGapsAndCountsFromTheHighest() {
        val tasks = listOf(
            Task(id = 1L, order = 0, date = monday, description = "a"),
            Task(id = 2L, order = 9, date = monday, description = "b"),
            Task(id = 3L, order = 99, date = monday.plusDays(1), description = "elsewhere"),
        )

        assertEquals(10, nextTaskOrderOn(tasks, monday))
    }

    @Test
    fun tasksWithNoDateAreADayOfTheirOwn() {
        val tasks = listOf(
            Task(id = 1L, order = 4, date = null, description = "someday"),
            Task(id = 2L, order = 40, date = monday, description = "dated"),
        )

        assertEquals(5, nextTaskOrderOn(tasks, null))
    }
}
