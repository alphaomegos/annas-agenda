package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Copying a task, or one part of one, to another day.
 *
 * The interesting part is what a copy deliberately does not keep. Carrying a
 * repeat rule across would give the user a second series they never asked for;
 * carrying a done flag across would hand them a day's work already ticked off.
 */
class CopySupportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val target = monday.plusDays(3)

    private fun ids(from: Long = 100L): () -> Long {
        var next = from
        return { next++ }
    }

    private fun task(
        id: Long = 1L,
        order: Int = 0,
        date: LocalDate? = monday,
        description: String = "wash the brushes",
        time: LocalTime? = LocalTime.of(9, 30),
        colorArgb: Long? = 0xFF112233L,
        isDone: Boolean = false,
        counterId: Long? = 50L,
        rule: RepeatRule? = null,
    ) = Task(
        id = id,
        order = order,
        date = date,
        time = time,
        description = description,
        colorArgb = colorArgb,
        hasSubtasks = false,
        isDone = isDone,
        linkedManualCounterId = counterId,
        repeatRule = rule,
    )

    private fun sub(
        id: Long,
        taskId: Long = 1L,
        order: Int = 0,
        description: String = "rinse",
        isDone: Boolean = false,
        colorArgb: Long? = null,
        rule: RepeatRule? = null,
    ) = Subtask(
        id = id,
        order = order,
        taskId = taskId,
        description = description,
        colorArgb = colorArgb,
        isDone = isDone,
        repeatRule = rule,
    )

    private fun copyTask(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        taskId: Long = 1L,
        to: LocalDate = target,
    ) = stateAfterCopyingTask(tasks, subtasks, taskId, to, ids())

    /* ---------- what a copy keeps ---------- */

    @Test
    fun aCopyKeepsWhatTheTaskSays() {
        val after = copyTask(listOf(task()))
        val copied = after.tasks.single { it.id != 1L }

        assertEquals("wash the brushes", copied.description)
        assertEquals(LocalTime.of(9, 30), copied.time)
        assertEquals(0xFF112233L, copied.colorArgb)
        assertEquals(target, copied.date)
    }

    /** The counter is what the task is for, so the copy counts towards it too. */
    @Test
    fun aCopyKeepsTheCounterItCountsTowards() {
        val copied = copyTask(listOf(task(counterId = 50L))).tasks.single { it.id != 1L }

        assertEquals(50L, copied.linkedManualCounterId)
    }

    @Test
    fun theOriginalIsLeftExactlyAsItWas() {
        val original = task()

        val after = copyTask(listOf(original))

        assertEquals(original, after.tasks.single { it.id == 1L })
    }

    /* ---------- what a copy deliberately drops ---------- */

    /**
     * Copying a repeat would give the user a second series they never asked
     * for, and one they would have to find in order to stop.
     */
    @Test
    fun aCopyDoesNotRepeat() {
        val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = DayOfWeek.MONDAY)

        val copied = copyTask(listOf(task(rule = daily))).tasks.single { it.id != 1L }

        assertNull(copied.repeatRule)
        assertNull(copied.originTaskId)
    }

    /** The point of copying a task is to do it again. */
    @Test
    fun aCopyIsNotAlreadyDone() {
        val after = copyTask(
            tasks = listOf(task(isDone = true)),
            subtasks = listOf(sub(10L, isDone = true), sub(11L, order = 1, isDone = true)),
        )

        assertFalse(after.tasks.single { it.id != 1L }.isDone)
        assertTrue(after.subtasks.filter { it.id > 11L }.none { it.isDone })
    }

    /**
     * A copy of an occurrence is a task of its own, not a second occurrence:
     * deleting the series afterwards must not take it with it.
     */
    @Test
    fun aCopyOfAnOccurrenceIsNobodysOccurrence() {
        val occurrence = task(id = 7L).copy(originTaskId = 1L)

        val copied = copyTask(listOf(task(), occurrence), taskId = 7L).tasks.maxBy { it.id }

        assertNull(copied.originTaskId)
    }

    /* ---------- where it lands ---------- */

    @Test
    fun aCopyLandsAtTheBottomOfItsNewDay() {
        val after = copyTask(
            listOf(
                task(),
                task(id = 2L, order = 0, date = target, description = "dentist"),
                task(id = 3L, order = 4, date = target, description = "shopping"),
            )
        )

        assertEquals(5, after.tasks.single { it.id !in setOf(1L, 2L, 3L) }.order)
    }

    @Test
    fun aCopyOntoAnEmptyDayIsFirst() {
        assertEquals(0, copyTask(listOf(task())).tasks.single { it.id != 1L }.order)
    }

    /* ---------- the parts ---------- */

    @Test
    fun aCopyTakesThePartsInTheOrderTheyAreShown() {
        val after = copyTask(
            tasks = listOf(task()),
            subtasks = listOf(
                sub(12L, order = 2, description = "dry"),
                sub(10L, order = 0, description = "rinse"),
                sub(11L, order = 1, description = "shake"),
            ),
        )

        val copiedSubs = after.subtasks
            .filter { it.id >= 100L }
            .sortedBy { it.order }

        assertEquals(listOf("rinse", "shake", "dry"), copiedSubs.map { it.description })
        assertEquals(listOf(0, 1, 2), copiedSubs.map { it.order })
    }

    @Test
    fun thePartsOfTheCopyBelongToTheCopy() {
        val after = copyTask(tasks = listOf(task()), subtasks = listOf(sub(10L)))

        val copiedTask = after.tasks.single { it.id != 1L }
        val copiedSub = after.subtasks.single { it.id >= 100L }

        assertEquals(copiedTask.id, copiedSub.taskId)
        assertTrue(copiedTask.hasSubtasks)
        assertNull(copiedSub.originSubtaskId)
        assertNull(copiedSub.repeatRule)
    }

    @Test
    fun aTaskWithNoPartsSaysSo() {
        assertFalse(copyTask(listOf(task())).tasks.single { it.id != 1L }.hasSubtasks)
    }

    @Test
    fun thePartsOfOtherTasksAreNotCopied() {
        val after = copyTask(
            tasks = listOf(task(), task(id = 2L, description = "other")),
            subtasks = listOf(sub(10L, taskId = 1L), sub(20L, taskId = 2L)),
        )

        assertEquals(1, after.subtasks.count { it.id >= 100L })
    }

    @Test
    fun anIdThatNamesNothingChangesNothing() {
        val tasks = listOf(task())
        val subtasks = listOf(sub(10L))

        val after = stateAfterCopyingTask(tasks, subtasks, 404L, target, ids())

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    /* ---------- copying one part on its own ---------- */

    private fun copySubtask(
        tasks: List<Task>,
        subtasks: List<Subtask>,
        subtaskId: Long = 10L,
        to: LocalDate = target,
    ) = stateAfterCopyingSubtask(tasks, subtasks, subtaskId, to, ids())

    /**
     * A part on its own says too little to stand as a day's entry, so it goes
     * under a copy of the task it came from: "rinse" is a reminder only under
     * "wash the brushes".
     */
    @Test
    fun aPartCopiedOnItsOwnBringsItsTaskAlong() {
        val after = copySubtask(listOf(task()), listOf(sub(10L, description = "rinse")))

        val carrier = after.tasks.single { it.id != 1L }
        val copied = after.subtasks.single { it.id >= 100L }

        assertEquals("wash the brushes", carrier.description)
        assertEquals(LocalTime.of(9, 30), carrier.time)
        assertEquals(50L, carrier.linkedManualCounterId)
        assertEquals(target, carrier.date)
        assertTrue(carrier.hasSubtasks)

        assertEquals("rinse", copied.description)
        assertEquals(carrier.id, copied.taskId)
        assertEquals(0, copied.order)
    }

    @Test
    fun aPartCopiedOnItsOwnLeavesItsSiblingsBehind() {
        val after = copySubtask(
            listOf(task()),
            listOf(sub(10L, description = "rinse"), sub(11L, order = 1, description = "dry")),
        )

        assertEquals(listOf("rinse"), after.subtasks.filter { it.id >= 100L }.map { it.description })
    }

    @Test
    fun aPartCopiedOnItsOwnIsNotAlreadyDone() {
        val after = copySubtask(listOf(task(isDone = true)), listOf(sub(10L, isDone = true)))

        assertFalse(after.tasks.single { it.id != 1L }.isDone)
        assertFalse(after.subtasks.single { it.id >= 100L }.isDone)
    }

    @Test
    fun aPartCopiedOnItsOwnDoesNotRepeat() {
        val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = DayOfWeek.MONDAY)
        val after = copySubtask(listOf(task(rule = daily)), listOf(sub(10L, rule = daily)))

        assertNull(after.tasks.single { it.id != 1L }.repeatRule)
        assertNull(after.subtasks.single { it.id >= 100L }.repeatRule)
    }

    @Test
    fun aPartWhoseTaskIsMissingChangesNothing() {
        val tasks = listOf(task())
        val subtasks = listOf(sub(99L, taskId = 404L))

        val after = stateAfterCopyingSubtask(tasks, subtasks, 99L, target, ids())

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    @Test
    fun aPartIdThatNamesNothingChangesNothing() {
        val tasks = listOf(task())
        val subtasks = listOf(sub(10L))

        val after = stateAfterCopyingSubtask(tasks, subtasks, 404L, target, ids())

        assertSame(tasks, after.tasks)
        assertSame(subtasks, after.subtasks)
    }

    /* ---------- ids ---------- */

    /** Ids are asked for only when there is something to make. */
    @Test
    fun noIdIsSpentOnACopyThatDoesNotHappen() {
        var handedOut = 0
        val counting = { handedOut++; 500L }

        stateAfterCopyingTask(listOf(task()), emptyList(), 404L, target, counting)
        stateAfterCopyingSubtask(listOf(task()), emptyList(), 404L, target, counting)

        assertEquals(0, handedOut)
    }

    @Test
    fun oneIdForTheTaskAndOnePerPart() {
        var handedOut = 0
        val counting = { handedOut++; (500 + handedOut).toLong() }

        stateAfterCopyingTask(
            listOf(task()),
            listOf(sub(10L), sub(11L, order = 1)),
            1L,
            target,
            counting,
        )

        assertEquals(3, handedOut)
    }
}
