package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dragging a book to another shelf while it is being read.
 *
 * The decision itself — what becomes of a live session when its book changes —
 * is readingAfterBookChanged's, and it has its own tests. What is asked here
 * is the half that used to live in the view model: that the decision reaches
 * all three of the fields it is about. A session in progress, a question
 * waiting to be answered and the history move together or the hour goes
 * missing, which is what 0069 was written for.
 */
class ReadingShelfMoveSupportTest {

    private val startedAt = 1_000_000L
    private val now = startedAt + 90 * 60_000L      // полтора часа спустя
    private val year = 2026

    private val book = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = 700,
        currentPage = 120,
        shelf = ReadingShelf.NOW,
    )

    private val reading = AppState(
        readingBooks = listOf(book),
        activeReading = ActiveReading(
            bookId = 1L,
            startedAtEpochMillis = startedAt,
            startPage = 40,
        ),
    )

    private fun move(
        state: AppState,
        shelf: ReadingShelf,
        id: Long = 1L,
        nextSessionId: Long = 99L,
    ) = stateAfterMovingReadingBookToShelf(
        state = state,
        bookId = id,
        shelf = shelf,
        currentYear = year,
        nowEpochMillis = now,
        newSessionId = { nextSessionId },
    )

    @Test
    fun nothingHappensWhenTheBookIsAlreadyOnThatShelf() {
        assertNull(move(reading, ReadingShelf.NOW))
    }

    @Test
    fun nothingHappensForABookThatIsNotThere() {
        assertNull(move(reading, ReadingShelf.DONE, id = 404L))
    }

    @Test
    fun theBookArrivesOnTheNewShelfAndGetsItsYear() {
        val after = move(reading, ReadingShelf.DONE)!!

        assertEquals(ReadingShelf.DONE, after.readingBooks.single().shelf)
        assertEquals(year, after.readingBooks.single().yearRead)
    }

    /**
     * The one this is all for. An hour and a half of reading, then the book is
     * marked finished — and the time is still there, waiting to be kept or
     * thrown away deliberately.
     */
    @Test
    fun readingStopsAndTheTimeBecomesAQuestion() {
        val after = move(reading, ReadingShelf.DONE)!!

        assertNull("the session is no longer live", after.activeReading)

        val asked = after.pendingReadingSession
        assertNotNull("the time must not simply vanish", asked)
        assertEquals(1L, asked!!.bookId)
        assertEquals(90, asked.durationMinutes)
        assertEquals(40, asked.startPage)
        assertEquals(120, asked.endPage)

        assertTrue("nothing is written to history unasked", after.readingSessions.isEmpty())
    }

    /**
     * And when the user has said to stop asking, the same hour goes straight
     * into the history instead.
     */
    @Test
    fun withAutoRecordTheTimeGoesStraightToHistory() {
        val after = move(
            reading.copy(autoRecordInterruptedReading = true),
            ReadingShelf.DONE,
        )!!

        assertNull(after.activeReading)
        assertNull("nothing left to ask about", after.pendingReadingSession)
        assertEquals(1, after.readingSessions.size)
        assertEquals(90, after.readingSessions.single().durationMinutes)
    }

    /**
     * A question nobody has answered yet is recorded rather than overwritten
     * when a second one arrives. Dropping it to make room would be the exact
     * loss this is here to stop.
     */
    @Test
    fun anUnansweredQuestionIsKeptWhenASecondOneArrives() {
        val older = ReadingSession(
            id = 50L,
            bookId = 1L,
            startedAtEpochMillis = 1L,
            durationMinutes = 20,
            startPage = 1,
            endPage = 40,
        )

        val after = move(reading.copy(pendingReadingSession = older), ReadingShelf.DONE)!!

        assertEquals(listOf(older), after.readingSessions)
        assertEquals(99L, after.pendingReadingSession?.id)
    }

    /**
     * Moving some other book must not touch a session on this one.
     */
    @Test
    fun aSessionOnAnotherBookIsLeftAlone() {
        val second = ReadingBook(id = 2L, title = "Solaris", totalPages = 300)
        val before = reading.copy(readingBooks = reading.readingBooks + second)

        val after = move(before, ReadingShelf.DONE, id = 2L)!!

        assertEquals(1L, after.activeReading?.bookId)
        assertNull(after.pendingReadingSession)
        assertTrue(after.readingSessions.isEmpty())
    }

    /**
     * Moving a book onto Now does not end anything; the session follows the
     * page the book is on, because that page is what it counts from.
     */
    @Test
    fun movingOntoNowKeepsTheSessionAndFollowsThePage() {
        val parked = reading.copy(
            readingBooks = listOf(book.copy(shelf = ReadingShelf.PLANS, currentPage = 200)),
        )

        val after = move(parked, ReadingShelf.NOW)!!

        assertEquals(200, after.activeReading?.startPage)
        assertNull(after.pendingReadingSession)
    }

    @Test
    fun aFilmMovesWithoutDisturbingAnySession() {
        val movie = ReadingMovie(id = 7L, title = "Stalker", shelf = ReadingShelf.PLANS)
        val before = reading.copy(readingMovies = listOf(movie))

        val after = stateAfterMovingReadingMovieToShelf(before, 7L, ReadingShelf.DONE, year)!!

        assertEquals(ReadingShelf.DONE, after.readingMovies.single().shelf)
        assertEquals(year, after.readingMovies.single().yearWatched)
        assertEquals(before.activeReading, after.activeReading)
        assertEquals(before.readingBooks, after.readingBooks)
    }

    @Test
    fun aSeriesMovesWithoutDisturbingAnySession() {
        val series = ReadingSeries(id = 8L, title = "Twin Peaks", shelf = ReadingShelf.PLANS)
        val before = reading.copy(readingSeries = listOf(series))

        val after = stateAfterMovingReadingSeriesToShelf(before, 8L, ReadingShelf.DONE, year)!!

        assertEquals(ReadingShelf.DONE, after.readingSeries.single().shelf)
        assertEquals(before.activeReading, after.activeReading)
    }

    @Test
    fun aFilmAlreadyOnThatShelfAnswersNothing() {
        val movie = ReadingMovie(id = 7L, title = "Stalker", shelf = ReadingShelf.DONE)
        val before = reading.copy(readingMovies = listOf(movie))

        assertNull(stateAfterMovingReadingMovieToShelf(before, 7L, ReadingShelf.DONE, year))
        assertNull(stateAfterMovingReadingMovieToShelf(before, 404L, ReadingShelf.NOW, year))
        assertNull(stateAfterMovingReadingSeriesToShelf(before, 404L, ReadingShelf.NOW, year))
    }
}
