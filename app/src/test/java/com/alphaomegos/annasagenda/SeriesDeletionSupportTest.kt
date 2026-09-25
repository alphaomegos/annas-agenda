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
 * Stopping a repeat from a given day, without touching what already happened.
 *
 * This lived in AppViewModel, where no test on this side of an emulator could
 * reach it — and it is the destructive path, the one where getting a boundary
 * wrong deletes something the user meant to keep.
 */
class SeriesDeletionSupportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val today = monday.plusDays(3)
    private val weekStart = DayOfWeek.MONDAY
    private val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart)

    private fun template(
        id: Long = 1L,
        date: LocalDate? = monday,
        rule: RepeatRule? = daily,
        description: String = "water the plants",
    ) = Task(id = id, order = 0, date = date, description = description, repeatRule = rule)

    /** The state the app itself would hold after drawing a week. */
    private fun materialised(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        days: Long = 7,
    ) = generateRecurrencesInRange(
        tasks = tasks,
        subtasks = subtasks,
        suppressedRecurrences = emptySet(),
        start = monday,
        end = monday.plusDays(days - 1),
        nextId = 1000L,
        defaultWeekStart = weekStart,
    )

    private fun cutTaskSeries(
        generated: RecurrenceGenerationResult,
        templateId: Long = 1L,
        from: LocalDate = today,
    ) = tasksAfterDeletingTaskSeriesFrom(generated.tasks, generated.subtasks, templateId, from)

    /* ---------- a repeating task ---------- */

    @Test
    fun whatAlreadyHappenedIsLeftAlone() {
        val after = cutTaskSeries(materialised(listOf(template())))

        val datesLeft = after.tasks
            .filter { it.originTaskId == 1L }
            .map { it.date }
            .toSet()

        assertEquals(setOf(monday.plusDays(1), monday.plusDays(2)), datesLeft)
    }

    /**
     * Today's own occurrence goes. Asking to stop a repeat today means today,
     * not tomorrow — and the day is still in front of the user when they ask.
     */
    @Test
    fun todaysOccurrenceGoesToo() {
        val after = cutTaskSeries(materialised(listOf(template())))

        assertFalse(after.tasks.any { it.originTaskId == 1L && it.date == today })
    }

    @Test
    fun theRuleIsGoneSoNothingComesBack() {
        val after = cutTaskSeries(materialised(listOf(template(date = monday.minusDays(10)))))

        val survivingTemplate = after.tasks.first { it.id == 1L }
        assertNull(survivingTemplate.repeatRule)

        val rebuilt = generateRecurrencesInRange(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = emptySet(),
            start = monday,
            end = monday.plusDays(30),
            nextId = 9000L,
            defaultWeekStart = weekStart,
        )

        assertEquals(after.tasks.size, rebuilt.tasks.size)
    }

    /**
     * A template whose own day is already past is history: the user asked to
     * stop the repeat, not to erase the day it started on.
     */
    @Test
    fun aTemplateInThePastSurvivesWithoutItsRule() {
        val after = cutTaskSeries(materialised(listOf(template(date = monday))))

        assertTrue(after.tasks.any { it.id == 1L })
        assertNull(after.tasks.first { it.id == 1L }.repeatRule)
    }

    @Test
    fun aTemplateOnOrAfterTheCutoffGoesWithTheRest() {
        val generated = materialised(listOf(template(date = today)))

        val after = cutTaskSeries(generated)

        assertFalse(after.tasks.any { it.id == 1L })
    }

    /**
     * A template that was never given a date has no day to be judged against,
     * and leaving it behind with no rule would strand it in the repeating list
     * with nothing to show.
     */
    @Test
    fun aTemplateWithNoDateGoes() {
        val tasks = listOf(template(date = null))

        val after = tasksAfterDeletingTaskSeriesFrom(tasks, emptyList(), 1L, today)

        assertTrue(after.tasks.isEmpty())
    }

    @Test
    fun theSubtasksOfADeletedDayGoWithIt() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen")),
        )

        val after = cutTaskSeries(generated)

        val survivingTaskIds = after.tasks.mapTo(mutableSetOf()) { it.id }
        assertTrue(after.subtasks.all { it.taskId in survivingTaskIds })
        assertTrue(after.subtasks.any { it.originSubtaskId == 10L })
    }

    @Test
    fun otherTasksAreNotTouched() {
        val other = Task(id = 500L, order = 3, date = today, description = "dentist")
        val generated = materialised(listOf(template(), other))

        val after = cutTaskSeries(generated)

        assertEquals(other, after.tasks.first { it.id == 500L })
    }

    @Test
    fun anIdThatIsNotATemplateChangesNothing() {
        val generated = materialised(listOf(template()))
        val anInstance = generated.tasks.first { it.originTaskId == 1L }

        val byInstance = tasksAfterDeletingTaskSeriesFrom(
            generated.tasks, generated.subtasks, anInstance.id, today,
        )
        val byNothing = tasksAfterDeletingTaskSeriesFrom(
            generated.tasks, generated.subtasks, 999_999L, today,
        )

        assertSame(generated.tasks, byInstance.tasks)
        assertSame(generated.tasks, byNothing.tasks)
    }

    /* ---------- a repeating subtask ---------- */

    private val repeatingSub = Subtask(
        id = 10L,
        order = 0,
        taskId = 1L,
        description = "vitamins",
        repeatRule = daily,
    )

    @Test
    fun aSubtaskSeriesLosesOnlyTheDaysAhead() {
        val generated = materialised(
            tasks = listOf(template(rule = daily)),
            subtasks = listOf(repeatingSub),
        )

        val after = tasksAfterDeletingSubtaskSeriesFrom(
            generated.tasks, generated.subtasks, 10L, today,
        )

        val tasksById = after.tasks.associateBy { it.id }
        val datesWithTheSubtask = after.subtasks
            .filter { it.originSubtaskId == 10L }
            .mapNotNull { tasksById[it.taskId]?.date }
            .toSet()

        assertEquals(setOf(monday.plusDays(1), monday.plusDays(2)), datesWithTheSubtask)
        assertNull(after.subtasks.first { it.id == 10L }.repeatRule)
    }

    /**
     * The day that exists only to carry a repeating subtask is not the user's
     * day: the generator built it to have somewhere to put the subtask, so
     * taking the subtask away has to take the day with it or leave an empty
     * row behind. The code already did this; nothing here changes it. The test
     * is so that it keeps doing it, since the branch is invisible from the
     * screen that triggers it.
     */
    @Test
    fun aDayThatExistedOnlyToCarryTheSubtaskGoesWithIt() {
        val generated = materialised(
            tasks = listOf(template(rule = null)),
            subtasks = listOf(repeatingSub),
        )

        // The generator built a task for each day purely to hang it on.
        assertTrue(generated.tasks.count { it.originTaskId == 1L } > 0)

        val after = tasksAfterDeletingSubtaskSeriesFrom(
            generated.tasks, generated.subtasks, 10L, today,
        )

        val carriersAhead = after.tasks.filter {
            it.originTaskId == 1L && it.date?.isBefore(today) == false
        }
        assertTrue("an empty day was left behind: $carriersAhead", carriersAhead.isEmpty())
        assertTrue(after.tasks.any { it.originTaskId == 1L && it.date == monday.plusDays(1) })
    }

    /**
     * Where the parent repeats, the day is the user's own occurrence of it and
     * stays — with or without the subtask.
     */
    @Test
    fun aDayOfItsOwnRepeatStaysEvenWhenTheSubtaskLeavesIt() {
        val generated = materialised(
            tasks = listOf(template(rule = daily)),
            subtasks = listOf(repeatingSub),
        )

        val after = tasksAfterDeletingSubtaskSeriesFrom(
            generated.tasks, generated.subtasks, 10L, today,
        )

        val dayAhead = after.tasks.firstOrNull { it.originTaskId == 1L && it.date == today }
        assertTrue("the day itself should have stayed", dayAhead != null)
        assertFalse(after.subtasks.any { it.taskId == dayAhead!!.id })
        assertFalse("and it now says it has none", dayAhead!!.hasSubtasks)
    }

    @Test
    fun aDayKeepsItsOtherSubtasks() {
        val other = Subtask(id = 11L, order = 1, taskId = 1L, description = "water")
        val generated = materialised(
            tasks = listOf(template(rule = daily)),
            subtasks = listOf(repeatingSub, other),
        )

        val after = tasksAfterDeletingSubtaskSeriesFrom(
            generated.tasks, generated.subtasks, 10L, today,
        )

        val dayAhead = after.tasks.first { it.originTaskId == 1L && it.date == today }
        assertEquals(
            listOf(11L),
            after.subtasks.filter { it.taskId == dayAhead.id }.map { it.originSubtaskId },
        )
        assertTrue(dayAhead.hasSubtasks)
    }

    @Test
    fun anIdThatIsNotATemplateSubtaskChangesNothing() {
        val generated = materialised(
            tasks = listOf(template(rule = daily)),
            subtasks = listOf(repeatingSub),
        )
        val anInstance = generated.subtasks.first { it.originSubtaskId == 10L }

        val byInstance = tasksAfterDeletingSubtaskSeriesFrom(
            generated.tasks, generated.subtasks, anInstance.id, today,
        )

        assertSame(generated.tasks, byInstance.tasks)
        assertSame(generated.subtasks, byInstance.subtasks)
    }

    /* ---------- the derived flag ---------- */

    @Test
    fun hasSubtasksSaysWhatIsActuallyThere() {
        val tasks = listOf(
            Task(id = 1L, description = "with", hasSubtasks = false),
            Task(id = 2L, description = "without", hasSubtasks = true),
        )
        val subtasks = listOf(Subtask(id = 10L, taskId = 1L, description = "a"))

        val refreshed = withHasSubtasksRefreshed(tasks, subtasks)

        assertTrue(refreshed.first { it.id == 1L }.hasSubtasks)
        assertFalse(refreshed.first { it.id == 2L }.hasSubtasks)
    }
}
