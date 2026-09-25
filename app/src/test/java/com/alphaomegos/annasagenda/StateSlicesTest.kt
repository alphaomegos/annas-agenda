package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The point of a slice is that an unrelated change produces an equal value, so
 * the StateFlow built from it never emits and the screen never recomposes.
 *
 * That is a property of the slice functions themselves, so it can be asserted
 * here on a plain JVM instead of being taken on trust from the ViewModel.
 */
class StateSlicesTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 23)

    private val populated = AppState(
        tasks = listOf(
            Task(id = 1, date = today, description = "Task", hasSubtasks = true),
        ),
        subtasks = listOf(
            Subtask(id = 4, taskId = 1, description = "Subtask"),
        ),
        suppressedRecurrences = setOf("T:1:${today.toEpochDay()}"),
        anthropometryEnabledFieldIds = allAnthropometryFieldIds().take(2).toSet(),
        calorieGoalChanges = listOf(CalorieGoalChange(date = today, kcal = 1800)),
        foodLog = listOf(FoodEntry(id = 2, date = today, title = "Каша", kcal = 300)),
        counters = listOf(ManualCounter(id = 3, title = "Отжимания", balance = 10)),
        undoneLampMuted = true,
        undoneHorizonDays = 90,
        mainMenuHiddenIds = setOf("someday"),
    )

    /* ---------------- unrelated changes are invisible ---------------- */

    @Test
    fun calorimeterSliceIgnoresChangesItDoesNotRead() {
        val changed = populated.copy(
            counters = emptyList(),
            undoneLampMuted = false,
            mainMenuHiddenIds = emptySet(),
            anthropometryEnabledFieldIds = emptySet(),
        )

        assertEquals(calorimeterSliceOf(populated), calorimeterSliceOf(changed))
    }

    @Test
    fun anthropometrySliceIgnoresChangesItDoesNotRead() {
        val changed = populated.copy(
            counters = emptyList(),
            tasks = emptyList(),
            undoneLampMuted = false,
        )

        assertEquals(anthropometrySliceOf(populated), anthropometrySliceOf(changed))
    }

    @Test
    fun countersSliceIgnoresChangesItDoesNotRead() {
        val changed = populated.copy(
            foodLog = emptyList(),
            calorieGoalChanges = emptyList(),
            tasks = emptyList(),
            undoneLampMuted = false,
        )

        assertEquals(countersSliceOf(populated), countersSliceOf(changed))
    }

    /**
     * Note counters and subtasks are NOT unrelated here: UndoneTasksScreen
     * passes its slice to the shared DateTasksBlock, which reads both.
     */
    @Test
    fun undoneSliceIgnoresChangesItDoesNotRead() {
        val changed = populated.copy(
            foodLog = emptyList(),
            calorieGoalChanges = emptyList(),
            anthropometryEnabledFieldIds = emptySet(),
            mainMenuHiddenIds = emptySet(),
        )

        assertEquals(undoneSliceOf(populated), undoneSliceOf(changed))
    }

    @Test
    fun undoneSliceReactsToWhatDateTasksBlockReads() {
        assertNotEquals(
            undoneSliceOf(populated),
            undoneSliceOf(populated.copy(subtasks = emptyList())),
        )
        assertNotEquals(
            undoneSliceOf(populated),
            undoneSliceOf(populated.copy(counters = emptyList())),
        )
    }

    /* ---------------- own changes do come through ---------------- */

    @Test
    fun calorimeterSliceReflectsItsOwnFields() {
        val changed = populated.copy(
            foodLog = populated.foodLog + FoodEntry(
                id = 9,
                date = today,
                title = "Чай",
                kcal = 5,
            )
        )

        assertNotEquals(calorimeterSliceOf(populated), calorimeterSliceOf(changed))
    }

    @Test
    fun anthropometrySliceReflectsItsOwnFields() {
        val changed = populated.copy(anthropometryEnabledFieldIds = emptySet())

        assertNotEquals(anthropometrySliceOf(populated), anthropometrySliceOf(changed))
    }

    @Test
    fun countersSliceReflectsItsOwnFields() {
        val changed = populated.copy(
            counters = listOf(ManualCounter(id = 3, title = "Отжимания", balance = 11))
        )

        assertNotEquals(countersSliceOf(populated), countersSliceOf(changed))
    }

    @Test
    fun undoneSliceReflectsItsOwnFields() {
        assertNotEquals(
            undoneSliceOf(populated),
            undoneSliceOf(populated.copy(undoneLampMuted = false)),
        )
        assertNotEquals(
            undoneSliceOf(populated),
            undoneSliceOf(populated.copy(tasks = emptyList())),
        )
        assertNotEquals(
            undoneSliceOf(populated),
            undoneSliceOf(populated.copy(undoneHorizonDays = 7)),
        )
    }

    /* ---------------- slices carry the values verbatim ---------------- */

    @Test
    fun slicesCopyTheFieldsThroughUnchanged() {
        assertEquals(populated.foodLog, calorimeterSliceOf(populated).foodLog)
        assertEquals(
            populated.calorieGoalChanges,
            calorimeterSliceOf(populated).calorieGoalChanges,
        )
        assertEquals(
            populated.anthropometryEnabledFieldIds,
            anthropometrySliceOf(populated).anthropometryEnabledFieldIds,
        )
        assertEquals(populated.counters, countersSliceOf(populated).counters)
        assertEquals(populated.tasks, undoneSliceOf(populated).tasks)
        assertEquals(populated.subtasks, undoneSliceOf(populated).subtasks)
        assertEquals(populated.counters, undoneSliceOf(populated).counters)
        assertEquals(
            populated.suppressedRecurrences,
            undoneSliceOf(populated).suppressedRecurrences,
        )
        assertEquals(populated.undoneLampMuted, undoneSliceOf(populated).undoneLampMuted)
        assertEquals(populated.undoneHorizonDays, undoneSliceOf(populated).undoneHorizonDays)
    }

    /* ---------- the question about an interrupted session ---------- */

    private fun pendingSession(bookId: Long) = ReadingSession(
        id = 900L,
        bookId = bookId,
        startedAtEpochMillis = 1_700_000_000_000L,
        durationMinutes = 60,
        startPage = 40,
        endPage = 90,
    )

    @Test
    fun pendingReadingPrompt_isNothingWhenNothingIsOutstanding() {
        assertNull(pendingReadingPromptOf(AppState()))
    }

    @Test
    fun pendingReadingPrompt_namesTheBookTheSessionWasAbout() {
        val state = AppState(
            readingBooks = listOf(
                ReadingBook(id = 1L, title = "Dune", totalPages = 600),
                ReadingBook(id = 2L, title = "Solaris", totalPages = 300),
            ),
            pendingReadingSession = pendingSession(bookId = 2L),
        )

        val prompt = pendingReadingPromptOf(state)

        assertEquals("Solaris", prompt?.bookTitle)
        assertEquals(60, prompt?.session?.durationMinutes)
    }

    /**
     * The book can be gone by the time the question is answered — deleting it
     * clears the question, but a payload from elsewhere need not be tidy. The
     * dialog still has to draw.
     */
    @Test
    fun pendingReadingPrompt_survivesABookItCannotFind() {
        val state = AppState(pendingReadingSession = pendingSession(bookId = 99L))

        assertEquals("", pendingReadingPromptOf(state)?.bookTitle)
    }
}
