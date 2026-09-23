package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the one definition of "undone debt" that the lamp and the screen now
 * share.
 *
 * The regression worth remembering: the lamp did not exclude a repeating
 * template deleted on its own day, while the screen did. The lamp went red and
 * the screen it pointed at was empty.
 */
class UndoneSupportTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 23)

    private fun task(
        id: Long,
        date: LocalDate?,
        isDone: Boolean = false,
        repeatRule: RepeatRule? = null,
        originTaskId: Long? = null,
    ) = Task(
        id = id,
        date = date,
        description = "Task $id",
        isDone = isDone,
        repeatRule = repeatRule,
        originTaskId = originTaskId,
    )

    private fun debt(
        tasks: List<Task>,
        suppressed: Set<String> = emptySet(),
        horizonDays: Int = DEFAULT_UNDONE_HORIZON_DAYS,
    ) = undoneDebt(
        tasks = tasks,
        suppressedRecurrences = suppressed,
        today = today,
        horizonDays = horizonDays,
    )

    @Test
    fun countsAnOverdueUndoneTask() {
        val result = debt(listOf(task(1, today.minusDays(3))))

        assertEquals(setOf(1L), result.taskIds)
        assertEquals(listOf(today.minusDays(3)), result.dates)
        assertFalse(result.isEmpty)
    }

    @Test
    fun ignoresTasksThatAreDone() {
        val result = debt(listOf(task(1, today.minusDays(3), isDone = true)))

        assertTrue(result.isEmpty)
    }

    @Test
    fun ignoresTodayAndTheFuture() {
        val result = debt(
            listOf(
                task(1, today),
                task(2, today.plusDays(1)),
            )
        )

        assertTrue("a task due today is not a debt yet", result.isEmpty)
    }

    @Test
    fun ignoresTasksWithoutADate() {
        val result = debt(listOf(task(1, null)))

        assertTrue(result.isEmpty)
    }

    /**
     * The bug this whole function exists to remove: the lamp used to count a
     * repeating template that had been deleted for its own day.
     */
    @Test
    fun ignoresARepeatingTemplateDeletedOnItsOwnDay() {
        val anchor = today.minusDays(5)
        val template = task(
            id = 1,
            date = anchor,
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY),
        )

        val result = debt(
            tasks = listOf(template),
            suppressed = setOf(taskSuppressionKey(1L, anchor)),
        )

        assertTrue("lamp and screen must agree here", result.isEmpty)
    }

    @Test
    fun stillCountsARepeatingTemplateThatWasNotDeleted() {
        val anchor = today.minusDays(5)
        val template = task(
            id = 1,
            date = anchor,
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY),
        )

        assertEquals(setOf(1L), debt(listOf(template)).taskIds)
    }

    /* ---------------- horizon ---------------- */

    @Test
    fun horizonExcludesAnythingOlderThanTheWindow() {
        val tasks = listOf(
            task(1, today.minusDays(3)),
            task(2, today.minusDays(29)),
            task(3, today.minusDays(31)),
        )

        assertEquals(setOf(1L, 2L), debt(tasks, horizonDays = 30).taskIds)
    }

    @Test
    fun horizonIncludesItsOwnBoundaryDay() {
        val tasks = listOf(task(1, today.minusDays(30)))

        assertEquals(setOf(1L), debt(tasks, horizonDays = 30).taskIds)
    }

    @Test
    fun unlimitedHorizonKeepsEverythingOverdue() {
        val tasks = listOf(
            task(1, today.minusDays(3)),
            task(2, today.minusDays(4000)),
        )

        assertEquals(
            setOf(1L, 2L),
            debt(tasks, horizonDays = UNDONE_HORIZON_UNLIMITED).taskIds,
        )
    }

    @Test
    fun aNegativeHorizonFallsBackToTheDefaultRatherThanHidingEverything() {
        val tasks = listOf(task(1, today.minusDays(3)))

        assertEquals(DEFAULT_UNDONE_HORIZON_DAYS, normalizeUndoneHorizonDays(-5))
        assertEquals(setOf(1L), debt(tasks, horizonDays = -5).taskIds)
    }

    @Test
    fun datesAreDistinctAndSorted() {
        val tasks = listOf(
            task(1, today.minusDays(2)),
            task(2, today.minusDays(5)),
            task(3, today.minusDays(2)),
        )

        val result = debt(tasks)

        assertEquals(listOf(today.minusDays(5), today.minusDays(2)), result.dates)
        assertEquals(setOf(1L, 2L, 3L), result.taskIds)
    }

    /* ---------------- where generation should start ---------------- */

    private fun subtask(id: Long, taskId: Long, repeatRule: RepeatRule? = null) = Subtask(
        id = id,
        taskId = taskId,
        description = "Subtask $id",
        repeatRule = repeatRule,
    )

    @Test
    fun generationStartIsNullWhenNothingRepeats() {
        val plain = listOf(task(1, today.minusDays(100)))

        assertNull(
            undoneGenerationStart(plain, emptyList(), today, DEFAULT_UNDONE_HORIZON_DAYS)
        )
    }

    @Test
    fun generationNeverStartsBeforeTheTemplateItself() {
        val anchor = today.minusDays(5)
        val tasks = listOf(task(1, anchor, repeatRule = RepeatRule(freq = RepeatFreq.DAILY)))

        // The 30-day horizon reaches further back than the template exists.
        assertEquals(anchor, undoneGenerationStart(tasks, emptyList(), today, 30))
    }

    @Test
    fun generationStartsAtTheHorizonWhenTheTemplateIsOlder() {
        val tasks = listOf(
            task(1, today.minusDays(400), repeatRule = RepeatRule(freq = RepeatFreq.DAILY))
        )

        assertEquals(today.minusDays(30), undoneGenerationStart(tasks, emptyList(), today, 30))
    }

    @Test
    fun unlimitedHorizonStartsAtTheOldestTemplate() {
        val oldest = today.minusDays(400)
        val tasks = listOf(
            task(1, oldest, repeatRule = RepeatRule(freq = RepeatFreq.DAILY)),
            task(2, today.minusDays(10), repeatRule = RepeatRule(freq = RepeatFreq.DAILY)),
        )

        assertEquals(
            oldest,
            undoneGenerationStart(tasks, emptyList(), today, UNDONE_HORIZON_UNLIMITED),
        )
    }

    /** A subtask can repeat while its parent task does not. */
    @Test
    fun aTaskCountsAsRepeatingWhenOnlyItsSubtaskDoes() {
        val anchor = today.minusDays(9)
        val tasks = listOf(task(1, anchor))
        val subtasks = listOf(
            subtask(2, taskId = 1, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))
        )

        assertEquals(anchor, undoneGenerationStart(tasks, subtasks, today, 30))
    }

    @Test
    fun generatedInstancesDoNotCountAsTemplates() {
        val tasks = listOf(
            task(1, today.minusDays(3), originTaskId = 99),
        )

        assertNull(undoneGenerationStart(tasks, emptyList(), today, 30))
    }

    @Test
    fun horizonChoicesOfferedByTheUiAreAllValid() {
        UNDONE_HORIZON_CHOICES.forEach { days ->
            assertEquals(
                "choice $days must survive normalisation",
                days,
                normalizeUndoneHorizonDays(days),
            )
        }
        assertEquals(UNDONE_HORIZON_UNLIMITED, UNDONE_HORIZON_CHOICES.last())
    }
}
