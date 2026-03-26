package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CounterCrudSupportTest {

    @Test
    fun normalizeCounterTitleOrNull_trimsAndRejectsBlank() {
        assertEquals("Push-ups", normalizeCounterTitleOrNull("  Push-ups  "))
        assertNull(normalizeCounterTitleOrNull(""))
        assertNull(normalizeCounterTitleOrNull("   "))
    }

    @Test
    fun stateWithAddedCounter_appendsCounterToState() {
        val existing = manualCounter(id = 1, title = "Water", balance = 3)
        val added = dateRangeCounter(
            id = 2,
            title = "Vacation",
            startDate = LocalDate.of(2026, 3, 20),
            endDate = LocalDate.of(2026, 3, 25),
        )

        val original = AppState(counters = listOf(existing))

        val result = stateWithAddedCounter(original, added)

        assertEquals(listOf(existing, added), result.counters)
        assertEquals(original.tasks, result.tasks)
    }

    @Test
    fun countersWithUpdatedManualCounter_updatesOnlyMatchingManualCounter() {
        val original = listOf<Counter>(
            manualCounter(id = 1, title = "Water", balance = 3),
            dateRangeCounter(
                id = 2,
                title = "Trip",
                startDate = LocalDate.of(2026, 3, 1),
                endDate = LocalDate.of(2026, 3, 10),
            ),
            manualCounter(id = 3, title = "Steps", balance = 1000),
        )

        val result = countersWithUpdatedManualCounter(
            counters = original,
            counterId = 3,
            title = "Daily steps",
            balance = 2000,
        )

        assertEquals(
            manualCounter(id = 1, title = "Water", balance = 3),
            result[0],
        )
        assertEquals(original[1], result[1])
        assertEquals(
            manualCounter(id = 3, title = "Daily steps", balance = 2000),
            result[2],
        )
    }

    @Test
    fun countersWithUpdatedDateRangeCounter_updatesOnlyMatchingDateRangeCounter() {
        val original = listOf<Counter>(
            manualCounter(id = 1, title = "Water", balance = 3),
            dateRangeCounter(
                id = 2,
                title = "Trip",
                startDate = LocalDate.of(2026, 3, 1),
                endDate = LocalDate.of(2026, 3, 10),
            ),
        )

        val result = countersWithUpdatedDateRangeCounter(
            counters = original,
            counterId = 2,
            title = "Spring trip",
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 12),
        )

        assertEquals(original[0], result[0])
        assertEquals(
            dateRangeCounter(
                id = 2,
                title = "Spring trip",
                startDate = LocalDate.of(2026, 4, 1),
                endDate = LocalDate.of(2026, 4, 12),
            ),
            result[1],
        )
    }

    @Test
    fun stateWithoutCounter_removesCounterAndUnlinksMatchingTasks() {
        val state = AppState(
            counters = listOf(
                manualCounter(id = 10, title = "Push-ups", balance = 5),
                manualCounter(id = 20, title = "Water", balance = 2),
            ),
            tasks = listOf(
                task(id = 1, linkedManualCounterId = 10),
                task(id = 2, linkedManualCounterId = 20),
                task(id = 3, linkedManualCounterId = null),
            ),
        )

        val result = stateWithoutCounter(state, counterId = 10)

        assertEquals(
            listOf(manualCounter(id = 20, title = "Water", balance = 2)),
            result.counters,
        )

        val byId = result.tasks.associateBy { it.id }
        assertNull(byId.getValue(1).linkedManualCounterId)
        assertEquals(20L, byId.getValue(2).linkedManualCounterId)
        assertNull(byId.getValue(3).linkedManualCounterId)
    }

    @Test
    fun stateWithoutCounter_keepsStateShapeWhenCounterIdIsMissing() {
        val state = AppState(
            counters = listOf(
                manualCounter(id = 10, title = "Push-ups", balance = 5),
            ),
            tasks = listOf(
                task(id = 1, linkedManualCounterId = 10),
            ),
        )

        val result = stateWithoutCounter(state, counterId = 999)

        assertEquals(state, result)
        assertTrue(result.tasks.first().linkedManualCounterId == 10L)
    }

    private fun manualCounter(
        id: Long,
        title: String,
        balance: Int,
    ): ManualCounter = ManualCounter(
        id = id,
        title = title,
        balance = balance,
    )

    private fun dateRangeCounter(
        id: Long,
        title: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): DateRangeCounter = DateRangeCounter(
        id = id,
        title = title,
        startDate = startDate,
        endDate = endDate,
    )

    private fun task(
        id: Long,
        linkedManualCounterId: Long?,
        description: String = "Task $id",
    ): Task = Task(
        id = id,
        order = 0,
        date = null,
        time = null,
        description = description,
        colorArgb = 0,
        hasSubtasks = false,
        isDone = false,
        repeatRule = null,
        originTaskId = null,
        linkedManualCounterId = linkedManualCounterId,
    )
}