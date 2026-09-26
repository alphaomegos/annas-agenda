package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opening a book to read it, with another one already open.
 *
 * The last case is why this file exists. Until 0107 starting a second reading
 * overwrote the first, and however long it had been running was gone — no
 * record, no question, nothing said. It is the same loss 0069 was written to
 * stop, on a path nobody had looked at.
 */
class ReadingSessionStartSupportTest {

    private val startedA = 1_000_000L
    private val startedB = startedA + 45 * 60_000L   // сорок пять минут спустя
    private val year = 2026

    private val bookA = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = 700,
        currentPage = 120,
        shelf = ReadingShelf.NOW,
    )

    private val bookB = ReadingBook(
        id = 2L,
        title = "Solaris",
        totalPages = 300,
        currentPage = 30,
        shelf = ReadingShelf.NOW,
    )

    private val readingA = AppState(
        readingBooks = listOf(bookA, bookB),
        activeReading = ActiveReading(bookId = 1L, startedAtEpochMillis = startedA, startPage = 40),
    )

    private fun begin(
        state: AppState,
        bookId: Long,
        at: Long = startedB,
        sessionId: Long = 77L,
    ) = stateAfterBeginningReading(
        state = state,
        bookId = bookId,
        startedAtEpochMillis = at,
        currentYear = year,
        newSessionId = { sessionId },
    )

    @Test
    fun aBookThatIsNotThereCannotBeRead() {
        assertNull(begin(readingA, 404L))
    }

    @Test
    fun readingStartsAtThePageTheBookIsOn() {
        val after = begin(AppState(readingBooks = listOf(bookB)), 2L)!!

        assertTrue(after.started)
        assertEquals(2L, after.state.activeReading?.bookId)
        assertEquals(30, after.state.activeReading?.startPage)
        assertEquals(startedB, after.state.activeReading?.startedAtEpochMillis)
    }

    /**
     * Opening the same book again is not a new session. Restarting the clock
     * here is what used to happen to anyone who left the session screen with
     * the system back gesture and tapped Read again.
     */
    @Test
    fun openingTheSameBookAgainChangesNothingAtAll() {
        val after = begin(readingA, 1L)!!

        assertTrue(after.started)
        assertEquals(readingA, after.state)
        assertEquals(startedA, after.state.activeReading?.startedAtEpochMillis)
    }

    @Test
    fun aBookOnPlansMovesToNowFirst() {
        val planned = AppState(readingBooks = listOf(bookB.copy(shelf = ReadingShelf.PLANS)))

        val after = begin(planned, 2L)!!

        assertEquals(ReadingShelf.NOW, after.state.readingBooks.single().shelf)
        assertEquals(2L, after.state.activeReading?.bookId)
    }

    /**
     * The one this was written for.
     */
    @Test
    fun startingAnotherBookTurnsTheFirstReadingIntoAQuestion() {
        val after = begin(readingA, 2L)!!

        assertEquals("the new book is the one being read", 2L, after.state.activeReading?.bookId)

        val asked = after.state.pendingReadingSession
        assertNotNull("the forty-five minutes must not vanish", asked)
        assertEquals(1L, asked!!.bookId)
        assertEquals(45, asked.durationMinutes)
        assertEquals(40, asked.startPage)
        assertEquals(120, asked.endPage)

        assertTrue("nothing goes to history unasked", after.state.readingSessions.isEmpty())
    }

    @Test
    fun withAutoRecordTheFirstReadingGoesStraightToHistory() {
        val after = begin(readingA.copy(autoRecordInterruptedReading = true), 2L)!!

        assertEquals(2L, after.state.activeReading?.bookId)
        assertNull(after.state.pendingReadingSession)
        assertEquals(1, after.state.readingSessions.size)
        assertEquals(45, after.state.readingSessions.single().durationMinutes)
    }

    /**
     * A question nobody has answered yet is recorded rather than overwritten,
     * the same as on every other path that ends a reading.
     */
    @Test
    fun anUnansweredQuestionSurvivesTheNewOne() {
        val older = ReadingSession(
            id = 50L,
            bookId = 1L,
            startedAtEpochMillis = 1L,
            durationMinutes = 10,
            startPage = 1,
            endPage = 20,
        )

        val after = begin(readingA.copy(pendingReadingSession = older), 2L)!!

        assertEquals(listOf(older), after.state.readingSessions)
        assertEquals(77L, after.state.pendingReadingSession?.id)
    }

    /**
     * A reading pointing at a book that is no longer in the library has
     * nothing to be recorded against. It goes, rather than becoming a question
     * about a book the user cannot see.
     */
    @Test
    fun aReadingOfAVanishedBookIsDroppedRatherThanAskedAbout() {
        val dangling = AppState(
            readingBooks = listOf(bookB),
            activeReading = ActiveReading(
                bookId = 999L,
                startedAtEpochMillis = startedA,
                startPage = 1,
            ),
        )

        val after = begin(dangling, 2L)!!

        assertEquals(2L, after.state.activeReading?.bookId)
        assertNull(after.state.pendingReadingSession)
        assertTrue(after.state.readingSessions.isEmpty())
    }

    /**
     * Moving the new book onto Now must not be what ends the old reading —
     * the two books are different, and the shelf move is about the new one.
     * Checked because both steps happen in one call and could easily end up
     * recording the same session twice.
     */
    @Test
    fun aPlannedSecondBookStillEndsTheFirstReadingExactlyOnce() {
        val planned = readingA.copy(
            readingBooks = listOf(bookA, bookB.copy(shelf = ReadingShelf.PLANS)),
            autoRecordInterruptedReading = true,
        )

        val after = begin(planned, 2L)!!

        assertEquals(1, after.state.readingSessions.size)
        assertEquals(1L, after.state.readingSessions.single().bookId)
        assertEquals(2L, after.state.activeReading?.bookId)
    }
}
