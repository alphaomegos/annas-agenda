package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

/**
 * References to things that are no longer there.
 *
 * The danger is not the dead reference itself but the number in it. Ids are
 * handed out as one past the largest in use, counted over what exists, so the
 * id of a deleted row comes round again — and a stale reference then attaches
 * itself to whatever the user created next.
 */
class ReferentialIntegritySupportTest {

    private val day = LocalDate.of(2026, 3, 23)

    @Test
    fun aSubtaskWhoseTaskIsGoneIsDropped() {
        val state = AppState(
            tasks = listOf(task(1L)),
            subtasks = listOf(subtask(10L, taskId = 1L), subtask(11L, taskId = 99L)),
        )

        val cleaned = stateWithDanglingReferencesCleared(state)

        assertEquals(listOf(10L), cleaned.subtasks.map { it.id })
    }

    @Test
    fun aTaskLinkedToACounterThatIsGoneKeepsEverythingButTheLink() {
        val state = AppState(
            tasks = listOf(task(1L, linkedManualCounterId = 50L)),
            counters = listOf(ManualCounter(id = 7L, title = "Push-ups", balance = 3)),
        )

        val cleaned = stateWithDanglingReferencesCleared(state)
        val cleanedTask = cleaned.tasks.single()

        assertNull(cleanedTask.linkedManualCounterId)
        assertEquals(state.tasks.single().copy(linkedManualCounterId = null), cleanedTask)
    }

    /**
     * A counter of the wrong kind is as good as absent: the balance the link
     * exists to move is only on a manual counter.
     */
    @Test
    fun aTaskLinkedToACounterOfTheWrongKindLosesTheLink() {
        val state = AppState(
            tasks = listOf(task(1L, linkedManualCounterId = 50L)),
            counters = listOf(
                DateRangeCounter(id = 50L, title = "Holiday", startDate = day, endDate = day)
            ),
        )

        assertNull(stateWithDanglingReferencesCleared(state).tasks.single().linkedManualCounterId)
    }

    @Test
    fun aTaskLinkedToACounterThatExistsIsLeftAlone() {
        val state = AppState(
            tasks = listOf(task(1L, linkedManualCounterId = 7L)),
            counters = listOf(ManualCounter(id = 7L, title = "Push-ups", balance = 3)),
        )

        assertSame(state, stateWithDanglingReferencesCleared(state))
    }

    /**
     * The row is the user's plan for that day and stays; only the link to a
     * task that is gone is cut. Keeping it is what let resetting the plan
     * delete a stranger's task once the id came round.
     */
    @Test
    fun aPlanRowPointingAtAMissingTaskKeepsItsNumbersAndLosesTheLink() {
        val state = AppState(
            tasks = listOf(task(1L)),
            runningPlanEntries = listOf(
                RunningPlanEntry(
                    date = day,
                    distanceKmText = "10.0",
                    durationHhMmText = "0100",
                    paceText = "0600",
                    taskId = 99L,
                )
            ),
        )

        val entry = stateWithDanglingReferencesCleared(state).runningPlanEntries.single()

        assertNull(entry.taskId)
        assertEquals("10.0", entry.distanceKmText)
        assertEquals("0100", entry.durationHhMmText)
        assertEquals("0600", entry.paceText)
    }

    @Test
    fun aReadingSessionInProgressOnABookThatIsGoneIsForgotten() {
        val state = AppState(
            readingBooks = listOf(book(1L)),
            activeReading = ActiveReading(bookId = 99L, startedAtEpochMillis = 1_000L, startPage = 10),
        )

        assertNull(stateWithDanglingReferencesCleared(state).activeReading)
    }

    @Test
    fun aReadingSessionInProgressOnABookThatExistsSurvives() {
        val state = AppState(
            readingBooks = listOf(book(1L)),
            activeReading = ActiveReading(bookId = 1L, startedAtEpochMillis = 1_000L, startPage = 10),
        )

        assertSame(state, stateWithDanglingReferencesCleared(state))
    }

    /**
     * Sessions are a record of something that happened, not a link to repair,
     * and deleting a book already takes them with it.
     */
    @Test
    fun finishedSessionsAreNotTouched() {
        val state = AppState(
            readingBooks = listOf(book(1L)),
            readingSessions = listOf(
                ReadingSession(
                    id = 5L,
                    bookId = 99L,
                    startedAtEpochMillis = 1_000L,
                    durationMinutes = 30,
                    startPage = 0,
                    endPage = 20,
                )
            ),
        )

        assertSame(state, stateWithDanglingReferencesCleared(state))
    }

    @Test
    fun aStateWithNothingDanglingIsHandedBackUnchanged() {
        // One instance, asked about itself: assertSame compares identity, and
        // two separate AppState() calls are equal without being the same.
        val state = AppState()

        assertSame(state, stateWithDanglingReferencesCleared(state))
    }

    private fun task(id: Long, linkedManualCounterId: Long? = null) = Task(
        id = id,
        date = day,
        description = "Task $id",
        linkedManualCounterId = linkedManualCounterId,
    )

    private fun subtask(id: Long, taskId: Long) = Subtask(
        id = id,
        taskId = taskId,
        description = "Subtask $id",
    )

    private fun book(id: Long) = ReadingBook(id = id, title = "Dune", totalPages = 400)

    /**
     * A question about a book that is not there cannot be answered, and would
     * put a dialog on screen naming nothing. Deleting a book clears it, so
     * this is about a payload that came from somewhere else.
     */
    @Test
    fun aQuestionAboutAMissingBookIsCleared() {
        val session = ReadingSession(
            id = 900L,
            bookId = 77L,
            startedAtEpochMillis = 1_700_000_000_000L,
            durationMinutes = 60,
            startPage = 0,
            endPage = 10,
        )

        val cleared = stateWithDanglingReferencesCleared(
            AppState(pendingReadingSession = session)
        )

        assertNull(cleared.pendingReadingSession)
    }

    @Test
    fun aQuestionAboutABookThatIsThereIsKept() {
        val session = ReadingSession(
            id = 900L,
            bookId = 7L,
            startedAtEpochMillis = 1_700_000_000_000L,
            durationMinutes = 60,
            startPage = 0,
            endPage = 10,
        )
        val state = AppState(
            readingBooks = listOf(ReadingBook(id = 7L, title = "Dune", totalPages = 600)),
            pendingReadingSession = session,
        )

        assertSame(state, stateWithDanglingReferencesCleared(state))
    }
}
