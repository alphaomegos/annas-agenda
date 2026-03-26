package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskOrderingSupportTest {

    private val day1 = LocalDate.of(2026, 3, 20)
    private val day2 = LocalDate.of(2026, 3, 21)

    @Test
    fun moveTaskWithinDate_movesTaskUpWithinSameDate_onlySiblingOrdersChange() {
        val original = listOf(
            task(id = 3, order = 2, date = day1),
            task(id = 4, order = 7, date = day2),
            task(id = 1, order = 0, date = day1),
            task(id = 2, order = 1, date = day1),
        )

        val result = moveTaskWithinDate(
            tasks = original,
            taskId = 2L,
            step = -1,
        )

        val byId = result.associateBy { it.id }

        assertEquals(1, byId.getValue(1L).order)
        assertEquals(0, byId.getValue(2L).order)
        assertEquals(2, byId.getValue(3L).order)

        assertEquals(7, byId.getValue(4L).order)
        assertEquals(day2, byId.getValue(4L).date)
    }

    @Test
    fun moveTaskWithinDate_returnsOriginalList_whenMoveIsOutOfBounds() {
        val original = listOf(
            task(id = 1, order = 0, date = day1),
            task(id = 2, order = 1, date = day1),
            task(id = 3, order = 0, date = day2),
        )

        val result = moveTaskWithinDate(
            tasks = original,
            taskId = 1L,
            step = -1,
        )

        assertEquals(original, result)
    }

    @Test
    fun moveTaskWithinDate_returnsOriginalList_whenTaskIdIsUnknown() {
        val original = listOf(
            task(id = 1, order = 0, date = day1),
            task(id = 2, order = 1, date = day1),
        )

        val result = moveTaskWithinDate(
            tasks = original,
            taskId = 999L,
            step = 1,
        )

        assertEquals(original, result)
    }

    @Test
    fun moveSubtaskWithinTask_movesSubtaskDownWithinSameParent_onlySiblingOrdersChange() {
        val original = listOf(
            subtask(id = 30, order = 2, taskId = 100),
            subtask(id = 40, order = 5, taskId = 200),
            subtask(id = 10, order = 0, taskId = 100),
            subtask(id = 20, order = 1, taskId = 100),
        )

        val result = moveSubtaskWithinTask(
            subtasks = original,
            subtaskId = 20L,
            step = 1,
        )

        val byId = result.associateBy { it.id }

        assertEquals(0, byId.getValue(10L).order)
        assertEquals(2, byId.getValue(20L).order)
        assertEquals(1, byId.getValue(30L).order)

        assertEquals(5, byId.getValue(40L).order)
        assertEquals(200L, byId.getValue(40L).taskId)
    }

    @Test
    fun moveSubtaskWithinTask_returnsOriginalList_whenMoveIsOutOfBounds() {
        val original = listOf(
            subtask(id = 10, order = 0, taskId = 100),
            subtask(id = 20, order = 1, taskId = 100),
            subtask(id = 30, order = 0, taskId = 200),
        )

        val result = moveSubtaskWithinTask(
            subtasks = original,
            subtaskId = 20L,
            step = 1,
        )

        assertEquals(original, result)
    }

    private fun task(
        id: Long,
        order: Int,
        date: LocalDate?,
        description: String = "Task $id",
    ): Task = Task(
        id = id,
        order = order,
        date = date,
        time = null,
        description = description,
        colorArgb = 0,
        hasSubtasks = false,
        isDone = false,
        repeatRule = null,
        originTaskId = null,
        linkedManualCounterId = null,
    )

    private fun subtask(
        id: Long,
        order: Int,
        taskId: Long,
        description: String = "Subtask $id",
    ): Subtask = Subtask(
        id = id,
        order = order,
        taskId = taskId,
        description = description,
        colorArgb = 0,
        isDone = false,
        repeatRule = null,
        originSubtaskId = null,
    )
}