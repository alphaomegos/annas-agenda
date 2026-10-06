package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*

/** "Done tasks to the bottom of the list" (06.10). */
class DoneTasksLastTest {

    private val day = LocalDate.of(2026, 10, 6)
    private fun t(id: Long, order: Int, done: Boolean = false, time: LocalTime? = null) =
        Task(id = id, order = order, date = day, time = time, description = "t$id", isDone = done)

    private val tasks = listOf(
        t(1, 0, done = true),
        t(2, 1),
        t(3, 2, done = true),
        t(4, 3),
        t(5, 9, done = true, time = LocalTime.of(9, 0)),
        t(6, 8, time = LocalTime.of(10, 0)),
    )

    @Test
    fun off_theDayReadsAsBefore() {
        assertEquals(listOf(5L, 6L, 1L, 2L, 3L, 4L), tasksInDayOrder(tasks).map { it.id })
    }

    @Test
    fun on_doneTasksGoDown_eachPartInTheDaysOrder() {
        assertEquals(listOf(6L, 2L, 4L, 5L, 1L, 3L), tasksInDayOrder(tasks, doneLast = true).map { it.id })
    }

    @Test
    fun on_anArrowSwapsWithTheNeighbourAsShown() {
        // 4 goes up past the done 3, to above 2.
        val moved = moveTaskWithinDate(tasks, 4, -1, doneLast = true)
        assertEquals(listOf(6L, 4L, 2L, 5L, 1L, 3L), tasksInDayOrder(moved, doneLast = true).map { it.id })
        // The done ones kept their places among themselves.
        assertEquals(listOf(1L, 3L), tasksInDayOrder(moved, doneLast = true).filter { it.isDone && it.time == null }.map { it.id })
        // Every order on the day is still unique.
        assertEquals(moved.size, moved.map { it.order }.toSet().size)
    }

    @Test
    fun on_theEndsOfEachPartAreEnds() {
        assertTrue(!canMoveTask(tasks, 2, -1, doneLast = true))
        assertTrue(!canMoveTask(tasks, 4, 1, doneLast = true))
        assertTrue(!canMoveTask(tasks, 3, 1, doneLast = true))
        assertTrue(canMoveTask(tasks, 3, -1, doneLast = true))
        assertSame(tasks, moveTaskWithinDate(tasks, 2, -1, doneLast = true))
    }
}
