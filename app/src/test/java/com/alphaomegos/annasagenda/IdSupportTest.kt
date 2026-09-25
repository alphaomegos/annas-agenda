package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Where the next id comes from.
 *
 * Everything with an id takes it from one counter, and two things outside
 * those collections keep hold of an id after the thing itself is gone: a
 * running-plan row remembers the task it made, and a tombstone remembers the
 * template whose day it silences. Hand either of those numbers to something
 * new and the plan deletes a stranger's task, or a new repeat is born with
 * holes punched in it by somebody else's deletions.
 */
class IdSupportTest {

    private val day = LocalDate.of(2026, 3, 2)

    @Test
    fun anEmptyStateStartsAtOne() {
        assertEquals(0L, highestIdIn(AppState()))
        assertEquals(1L, nextIdFor(AppState()))
    }

    /**
     * One collection left out of the count is one collection whose newest
     * member gets its id handed to something else on the next launch.
     */
    @Test
    fun everyCollectionThatHasIdsIsCounted() {
        val cases: List<Pair<String, AppState>> = listOf(
            "tasks" to AppState(tasks = listOf(Task(id = 40L, description = "t"))),
            "subtasks" to AppState(subtasks = listOf(Subtask(id = 40L, taskId = 1L, description = "s"))),
            "foodLog" to AppState(foodLog = listOf(FoodEntry(id = 40L, date = day, title = "f", kcal = 1))),
            "counters" to AppState(counters = listOf(ManualCounter(id = 40L, title = "c", balance = 0))),
            "readingBooks" to AppState(readingBooks = listOf(ReadingBook(id = 40L, title = "b", totalPages = 1))),
            "readingMovies" to AppState(readingMovies = listOf(ReadingMovie(id = 40L, title = "m"))),
            "readingSeries" to AppState(readingSeries = listOf(ReadingSeries(id = 40L, title = "s"))),
            "readingSessions" to AppState(
                readingSessions = listOf(
                    ReadingSession(
                        id = 40L,
                        bookId = 1L,
                        startedAtEpochMillis = 1_700_000_000_000L,
                        durationMinutes = 30,
                        startPage = 1,
                        endPage = 20,
                    )
                )
            ),
        )

        cases.forEach { (name, state) ->
            assertEquals("$name is not counted", 40L, highestIdIn(state))
            assertEquals("$name is not counted", 41L, nextIdFor(state))
        }
    }

    @Test
    fun theHighestIdWinsAcrossCollections() {
        val state = AppState(
            tasks = listOf(Task(id = 7L, description = "t")),
            subtasks = listOf(Subtask(id = 99L, taskId = 7L, description = "s")),
            counters = listOf(ManualCounter(id = 12L, title = "c", balance = 0)),
        )

        assertEquals(99L, highestIdIn(state))
        assertEquals(100L, nextIdFor(state))
    }

    /**
     * The case this exists for. Delete the newest thing, restart, and counting
     * what is left hands its id straight to whatever is created next.
     */
    @Test
    fun theMarkKeepsTheCounterAboveSomethingThatWasDeleted() {
        val before = AppState(
            tasks = listOf(Task(id = 1L, description = "kept"), Task(id = 2L, description = "doomed")),
            idHighWater = 3L,
        )

        val after = before.copy(tasks = before.tasks.filterNot { it.id == 2L })

        assertEquals("counting alone would reuse 2", 2L, highestIdIn(after) + 1L)
        assertEquals(3L, nextIdFor(after))
    }

    @Test
    fun theMarkNeverHoldsTheCounterBack() {
        val state = AppState(
            tasks = listOf(Task(id = 500L, description = "t")),
            idHighWater = 3L,
        )

        assertEquals(501L, nextIdFor(state))
    }

    /* ---------- raising the mark ---------- */

    @Test
    fun theMarkGoesUp() {
        val state = AppState(idHighWater = 10L)

        assertEquals(42L, stateWithIdHighWaterAtLeast(state, 42L).idHighWater)
    }

    @Test
    fun theMarkNeverGoesDown() {
        val state = AppState(idHighWater = 10L)

        assertEquals(10L, stateWithIdHighWaterAtLeast(state, 4L).idHighWater)
        assertEquals(10L, stateWithIdHighWaterAtLeast(state, 0L).idHighWater)
        assertEquals(10L, stateWithIdHighWaterAtLeast(state, -7L).idHighWater)
    }

    @Test
    fun aStateThatDoesNotNeedRaisingIsHandedBackAsItIs() {
        val state = AppState(idHighWater = 10L)

        assertSame(state, stateWithIdHighWaterAtLeast(state, 10L))
        assertSame(state, stateWithIdHighWaterAtLeast(state, 9L))
    }

    @Test
    fun nothingElseAboutTheStateChangesWhenTheMarkIsRaised() {
        val state = AppState(
            tasks = listOf(Task(id = 1L, description = "t")),
            undoneHorizonDays = 17,
            themeMode = AppThemeMode.DARK,
            idHighWater = 1L,
        )

        val raised = stateWithIdHighWaterAtLeast(state, 99L)

        assertEquals(state.copy(idHighWater = 99L), raised)
    }

    /**
     * Walked through as it actually happens: create, delete the newest, save,
     * restart. The counter has to come back above what was thrown away.
     */
    @Test
    fun acrossARestartTheCounterDoesNotGoBackwards() {
        var nextId = 1L
        fun newId(): Long = nextId++

        val a = Task(id = newId(), description = "keep")
        val b = Task(id = newId(), description = "delete me")
        var state = AppState(tasks = listOf(a, b))

        // The plan row remembers b even though b is about to go.
        val plan = RunningPlanEntry(date = day, taskId = b.id)
        state = state.copy(runningPlanEntries = listOf(plan))

        state = state.copy(tasks = state.tasks.filterNot { it.id == b.id })

        // What the store writes down.
        val saved = stateWithIdHighWaterAtLeast(state, nextId)

        // Next launch.
        val reloadedNextId = nextIdFor(saved)
        val fresh = Task(id = reloadedNextId, description = "something new")

        assertTrue(
            "the new task took the deleted one's id",
            fresh.id != plan.taskId,
        )
    }
}
