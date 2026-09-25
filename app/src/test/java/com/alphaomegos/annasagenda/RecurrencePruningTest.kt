package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Throwing away the occurrences that can be worked out again.
 *
 * Every test here starts from a state the app itself would have produced —
 * a template, then [generateRecurrencesInRange] over a stretch of days — so
 * the fixtures cannot drift away from what actually gets saved.
 */
class RecurrencePruningTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val weekStart = DayOfWeek.MONDAY

    private val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart)

    private fun template(
        id: Long = 1L,
        description: String = "water the plants",
        date: LocalDate = monday,
        rule: RepeatRule? = daily,
        time: LocalTime? = null,
    ) = Task(
        id = id,
        order = 0,
        date = date,
        time = time,
        description = description,
        repeatRule = rule,
    )

    /** The state the app would hold after drawing [days] days from [monday]. */
    private fun materialised(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        suppressed: Set<String> = emptySet(),
        days: Long = 5,
    ): RecurrenceGenerationResult = generateRecurrencesInRange(
        tasks = tasks,
        subtasks = subtasks,
        suppressedRecurrences = suppressed,
        start = monday,
        end = monday.plusDays(days - 1),
        nextId = 1000L,
        defaultWeekStart = weekStart,
    )

    private fun prune(
        generated: RecurrenceGenerationResult,
        suppressed: Set<String> = emptySet(),
        runningPlan: List<RunningPlanEntry> = emptyList(),
        isPrunableDate: (LocalDate) -> Boolean = { true },
    ) = pruneRedundantGeneratedOccurrences(
        tasks = generated.tasks,
        subtasks = generated.subtasks,
        suppressedRecurrences = suppressed,
        runningPlanEntries = runningPlan,
        isPrunableDate = isPrunableDate,
        weekStart = weekStart,
    )

    /** What a day looks like to the user: description, order, done, per date. */
    private fun visibleDays(
        tasks: List<Task>,
        subtasks: List<Subtask>,
    ): Map<LocalDate, List<String>> =
        tasks
            .filter { it.date != null }
            .groupBy { it.date!! }
            .mapValues { (_, dayTasks) ->
                dayTasks
                    .sortedWith(compareBy({ it.order }, { it.id }))
                    .map { t ->
                        val subs = subtasks
                            .filter { it.taskId == t.id }
                            .sortedWith(compareBy({ it.order }, { it.id }))
                            .joinToString(",") { "${it.description}${if (it.isDone) "!" else ""}" }
                        "${t.order}:${t.description}${if (t.isDone) "!" else ""}[$subs]"
                    }
            }

    @Test
    fun anUntouchedOccurrenceIsDroppedBecauseItCanBeWorkedOutAgain() {
        val generated = materialised(listOf(template()))

        // A rule never fires on its own anchor day, so five days drawn give
        // four occurrences, not five.
        assertEquals(4, generated.tasks.count { it.originTaskId != null })

        val result = prune(generated)

        assertEquals(4, result.report.tasksPruned)
        assertEquals(4, result.report.datesPruned)
        assertEquals(0, result.report.tasksKept)
        assertEquals(listOf(1L), result.tasks.map { it.id })
    }

    /**
     * The point of the whole exercise: what was thrown away has to come back
     * byte for byte, ids aside, or the user's day has been rearranged behind
     * their back.
     */
    @Test
    fun whatWasPrunedIsRebuiltIntoTheSameDays() {
        val generated = materialised(
            listOf(
                template(id = 1L, description = "water the plants"),
                template(id = 2L, description = "vitamins", time = LocalTime.of(8, 0)),
                Task(id = 3L, order = 5, date = monday.plusDays(1), description = "dentist"),
            )
        )
        val before = visibleDays(generated.tasks, generated.subtasks)

        val pruned = prune(generated)
        assertTrue(pruned.report.pruned)

        val rebuilt = generateRecurrencesInRange(
            tasks = pruned.tasks,
            subtasks = pruned.subtasks,
            suppressedRecurrences = emptySet(),
            start = monday,
            end = monday.plusDays(4),
            nextId = 5000L,
            defaultWeekStart = weekStart,
        )

        assertEquals(before, visibleDays(rebuilt.tasks, rebuilt.subtasks))
    }

    @Test
    fun aTickedOffOccurrenceStays() {
        val generated = materialised(listOf(template()))
        val victim = generated.tasks.first { it.date == monday.plusDays(2) && it.originTaskId != null }

        val withTick = generated.copy(
            tasks = generated.tasks.map { if (it.id == victim.id) it.copy(isDone = true) else it }
        )

        val result = prune(withTick)

        assertEquals(3, result.report.tasksPruned)
        assertEquals(1, result.report.tasksKept)
        assertTrue(result.tasks.any { it.id == victim.id })
    }

    @Test
    fun aRenamedOccurrenceStays() {
        val generated = materialised(listOf(template()))
        val victim = generated.tasks.first { it.date == monday.plusDays(3) && it.originTaskId != null }

        val edited = generated.copy(
            tasks = generated.tasks.map {
                if (it.id == victim.id) it.copy(description = "water the plants twice") else it
            }
        )

        val result = prune(edited)

        assertTrue(result.tasks.any { it.id == victim.id })
        assertEquals("water the plants twice", result.tasks.first { it.id == victim.id }.description)
    }

    /**
     * Dragging an occurrence up the day is the case that makes a field-by-field
     * "does it look like its template" test wrong: every field still matches,
     * and the day would still come back reordered.
     */
    @Test
    fun anOccurrenceDraggedUpTheDayStays() {
        val generated = materialised(
            listOf(
                template(id = 1L),
                Task(id = 2L, order = 0, date = monday.plusDays(1), description = "dentist"),
            )
        )
        val day = monday.plusDays(1)
        val victim = generated.tasks.first { it.date == day && it.originTaskId != null }
        assertEquals(1, victim.order)

        // The user pulls the repeat above the one-off.
        val reordered = generated.copy(
            tasks = generated.tasks.map {
                when (it.id) {
                    victim.id -> it.copy(order = 0)
                    2L -> it.copy(order = 1)
                    else -> it
                }
            }
        )

        val result = prune(reordered)

        assertTrue("the reordered day is left alone", result.tasks.any { it.id == victim.id })
        assertFalse("other days are still pruned", result.tasks.any {
            it.originTaskId != null && it.date == monday.plusDays(2)
        })
    }

    @Test
    fun anOccurrenceGivenItsOwnTimeStays() {
        val generated = materialised(listOf(template()))
        val victim = generated.tasks.first { it.date == monday.plusDays(1) && it.originTaskId != null }

        val edited = generated.copy(
            tasks = generated.tasks.map {
                if (it.id == victim.id) it.copy(time = LocalTime.of(19, 30)) else it
            }
        )

        assertTrue(prune(edited).tasks.any { it.id == victim.id })
    }

    /**
     * A day the user deleted has a tombstone, so generation refuses to build
     * it. An occurrence still sitting on such a day is something regeneration
     * would not bring back, and dropping it would make it vanish.
     */
    @Test
    fun anOccurrenceOnADeletedDayStays() {
        val generated = materialised(listOf(template()))
        val day = monday.plusDays(2)
        val victim = generated.tasks.first { it.date == day && it.originTaskId != null }
        val tombstone = setOf(taskSuppressionKey(1L, day))

        val result = prune(generated, suppressed = tombstone)

        assertTrue(result.tasks.any { it.id == victim.id })
        assertEquals(1, result.report.tasksKept)
    }

    @Test
    fun anOccurrenceTheRunningPlanPointsAtStays() {
        val generated = materialised(listOf(template()))
        val victim = generated.tasks.first { it.date == monday.plusDays(4) && it.originTaskId != null }

        val result = prune(
            generated,
            runningPlan = listOf(RunningPlanEntry(date = victim.date!!, taskId = victim.id)),
        )

        assertTrue(result.tasks.any { it.id == victim.id })
        assertEquals(1, result.report.tasksKept)
    }

    @Test
    fun onlyTheDatesTheCallerAllowsAreTouched() {
        val generated = materialised(listOf(template()))
        val cutoff = monday.plusDays(2)

        val result = prune(generated, isPrunableDate = { it.isAfter(cutoff) })

        assertEquals(2, result.report.datesExamined)
        assertEquals(2, result.report.tasksPruned)
        assertTrue(result.tasks.any { it.date == monday.plusDays(1) && it.originTaskId != null })
        assertTrue(result.tasks.any { it.date == cutoff && it.originTaskId != null })
    }

    @Test
    fun templatesAreNeverTouched() {
        val generated = materialised(listOf(template(id = 1L), template(id = 2L, description = "run")))

        val result = prune(generated)

        assertEquals(listOf(1L, 2L), result.tasks.map { it.id }.sorted())
    }

    @Test
    fun aStateWithNothingGeneratedIsHandedBackUnchanged() {
        val tasks = listOf(Task(id = 1L, date = monday, description = "dentist"))
        val subtasks = emptyList<Subtask>()

        val result = pruneRedundantGeneratedOccurrences(
            tasks = tasks,
            subtasks = subtasks,
            suppressedRecurrences = emptySet(),
            weekStart = weekStart,
        )

        assertSame(tasks, result.tasks)
        assertSame(subtasks, result.subtasks)
        assertFalse(result.report.pruned)
    }

    /* ---------- subtasks ---------- */

    @Test
    fun anUntouchedOccurrenceTakesItsGeneratedSubtasksWithIt() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(
                Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen"),
                Subtask(id = 11L, order = 1, taskId = 1L, description = "balcony"),
            ),
        )

        assertEquals(8, generated.subtasks.count { it.originSubtaskId != null })

        val result = prune(generated)

        assertEquals(8, result.report.subtasksPruned)
        assertEquals(listOf(10L, 11L), result.subtasks.map { it.id }.sorted())
    }

    @Test
    fun aTickedOffSubtaskKeepsItsWholeDay() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen")),
        )
        val day = monday.plusDays(3)
        val carrier = generated.tasks.first { it.date == day && it.originTaskId != null }
        val sub = generated.subtasks.first { it.taskId == carrier.id }

        val edited = generated.copy(
            subtasks = generated.subtasks.map { if (it.id == sub.id) it.copy(isDone = true) else it }
        )

        val result = prune(edited)

        assertTrue(result.tasks.any { it.id == carrier.id })
        assertTrue(result.subtasks.any { it.id == sub.id })
        assertEquals(3, result.report.datesPruned)
    }

    @Test
    fun aSubtaskDeletedFromOneDayKeepsThatDay() {
        val generated = materialised(
            tasks = listOf(template()),
            subtasks = listOf(
                Subtask(id = 10L, order = 0, taskId = 1L, description = "kitchen"),
                Subtask(id = 11L, order = 1, taskId = 1L, description = "balcony"),
            ),
        )
        val day = monday.plusDays(1)
        val carrier = generated.tasks.first { it.date == day && it.originTaskId != null }
        val victimSub = generated.subtasks.first { it.taskId == carrier.id && it.originSubtaskId == 11L }
        val tombstone = setOf(subtaskSuppressionKey(11L, day))

        val edited = generated.copy(subtasks = generated.subtasks.filterNot { it.id == victimSub.id })

        // The tombstone makes regeneration agree, so the day is derivable again.
        assertEquals(4, prune(edited, suppressed = tombstone).report.datesPruned)

        // Without it, the day as saved is not what generation would produce.
        assertTrue(prune(edited).tasks.any { it.id == carrier.id })
    }

    /* ---------- the awkward shapes ---------- */

    /**
     * Two tasks on one day with the same order are separated by id, and ids
     * are the one thing a rebuild does not preserve. There is no way to say
     * whether such a day would come back the same, so it is left alone.
     */
    @Test
    fun aDayWithTwoTasksSharingAnOrderIsLeftAlone() {
        val generated = materialised(listOf(template()))
        val day = monday.plusDays(2)
        val occurrence = generated.tasks.first { it.date == day && it.originTaskId != null }

        val collided = generated.copy(
            tasks = generated.tasks + Task(
                id = 9000L,
                order = occurrence.order,
                date = day,
                description = "dentist",
            )
        )

        val result = prune(collided)

        assertTrue(result.tasks.any { it.id == occurrence.id })
        assertEquals(3, result.report.datesPruned)
    }

    @Test
    fun aWeeklyRuleOnlyLosesTheDaysItsRuleNames() {
        val weekly = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 1,
            weekDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            weekStart = weekStart,
        )
        val generated = materialised(listOf(template(rule = weekly)), days = 8)

        val result = prune(generated)

        // The anchor Monday does not count; Wednesday and the next Monday do.
        assertEquals(2, result.report.tasksPruned)
        assertEquals(listOf(1L), result.tasks.map { it.id })
    }

    /**
     * A rule never fires on the day it was anchored to, so the anchor day holds
     * the template and nothing else. There is nothing to prune there, and the
     * template itself must not be mistaken for an occurrence of itself.
     */
    @Test
    fun theAnchorDayHoldsOnlyItsTemplate() {
        val generated = materialised(listOf(template()), days = 1)

        assertEquals(listOf(1L), generated.tasks.map { it.id })

        val result = prune(generated)

        assertEquals(1, result.tasks.size)
        assertEquals(null, result.tasks.single().originTaskId)
        assertEquals(daily, result.tasks.single().repeatRule)
        assertFalse(result.report.pruned)
    }
}
