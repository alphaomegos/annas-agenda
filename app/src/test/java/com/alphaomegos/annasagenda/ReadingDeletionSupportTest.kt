package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What goes with a deleted book, film or series.
 *
 * The interesting half is the book: three other things point at it, and all
 * three have to let go at the same moment, in the same write. This project has
 * produced that bug in four different places already — a running plan pointing
 * at a deleted task, a subtask without its task, a counter link with no
 * counter, a session with no book — which is why it is asked here one field at
 * a time rather than trusted to reading.
 */
class ReadingDeletionSupportTest {

    private val book = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = 700,
        coverUri = "internal://media_covers/book_1_abc.jpg",
    )

    private val otherBook = ReadingBook(id = 2L, title = "Solaris", totalPages = 300)

    private fun session(id: Long, bookId: Long) = ReadingSession(
        id = id,
        bookId = bookId,
        startedAtEpochMillis = 1_000L,
        durationMinutes = 30,
        startPage = 1,
        endPage = 20,
    )

    private val state = AppState(
        readingBooks = listOf(book, otherBook),
        readingSessions = listOf(session(10L, 1L), session(11L, 2L), session(12L, 1L)),
        activeReading = ActiveReading(bookId = 1L, startedAtEpochMillis = 5_000L, startPage = 20),
        pendingReadingSession = session(13L, 1L),
    )

    @Test
    fun theBookLeavesAndTheOtherOneStays() {
        val after = stateAfterDeletingReadingBook(state, 1L)!!.state

        assertEquals(listOf(otherBook), after.readingBooks)
    }

    @Test
    fun itsSessionsGoWithIt() {
        val after = stateAfterDeletingReadingBook(state, 1L)!!.state

        assertEquals(listOf(session(11L, 2L)), after.readingSessions)
    }

    @Test
    fun aSessionInProgressOnThatBookStops() {
        val after = stateAfterDeletingReadingBook(state, 1L)!!.state

        assertNull(after.activeReading)
    }

    /**
     * A question about a book that no longer exists has no answer worth
     * having. Leaving it would put a dialog on screen asking what to do about
     * an hour spent reading something that is not in the library any more.
     */
    @Test
    fun theQuestionWaitingAboutThatBookGoesToo() {
        val after = stateAfterDeletingReadingBook(state, 1L)!!.state

        assertNull(after.pendingReadingSession)
    }

    /**
     * The other half of the same rule: deleting one book must not disturb a
     * session or a question that belongs to another.
     */
    @Test
    fun anotherBooksSessionAndQuestionAreLeftAlone() {
        val onOtherBook = state.copy(
            activeReading = ActiveReading(bookId = 2L, startedAtEpochMillis = 5_000L, startPage = 1),
            pendingReadingSession = session(13L, 2L),
        )

        val after = stateAfterDeletingReadingBook(onOtherBook, 1L)!!.state

        assertEquals(2L, after.activeReading?.bookId)
        assertEquals(2L, after.pendingReadingSession?.bookId)
    }

    /**
     * The cover is handed back rather than deleted, and it matters that it is
     * handed back only once the state no longer points at it.
     */
    @Test
    fun theCoverIsNamedForTheCallerToDelete() {
        val deletion = stateAfterDeletingReadingBook(state, 1L)!!

        assertEquals("internal://media_covers/book_1_abc.jpg", deletion.coverToDelete)
        assertTrue(deletion.state.readingBooks.none { it.coverUri == deletion.coverToDelete })
    }

    @Test
    fun aBookWithNoCoverNamesNothingToDelete() {
        val deletion = stateAfterDeletingReadingBook(state, 2L)!!

        assertNull(deletion.coverToDelete)
    }

    /**
     * Nothing happens at all for an id that is not there — not an empty
     * change, nothing. The caller can then leave the state exactly as it was
     * rather than writing an identical one and waking every screen.
     */
    @Test
    fun deletingSomethingThatIsNotThereAnswersNothing() {
        assertNull(stateAfterDeletingReadingBook(state, 999L))
        assertNull(stateAfterDeletingReadingMovie(state, 999L))
        assertNull(stateAfterDeletingReadingSeries(state, 999L))
    }

    @Test
    fun aFilmTakesOnlyItsCover() {
        val movie = ReadingMovie(id = 3L, title = "Stalker", coverUri = "internal://x.jpg")
        val before = state.copy(readingMovies = listOf(movie))

        val deletion = stateAfterDeletingReadingMovie(before, 3L)!!

        assertEquals(emptyList<ReadingMovie>(), deletion.state.readingMovies)
        assertEquals("internal://x.jpg", deletion.coverToDelete)
        assertEquals(before.readingBooks, deletion.state.readingBooks)
        assertEquals(before.readingSessions, deletion.state.readingSessions)
        assertSame(before.activeReading, deletion.state.activeReading)
    }

    @Test
    fun aSeriesTakesOnlyItsCover() {
        val series = ReadingSeries(id = 4L, title = "Twin Peaks")
        val before = state.copy(readingSeries = listOf(series))

        val deletion = stateAfterDeletingReadingSeries(before, 4L)!!

        assertEquals(emptyList<ReadingSeries>(), deletion.state.readingSeries)
        assertNull(deletion.coverToDelete)
        assertEquals(before.readingBooks, deletion.state.readingBooks)
        assertEquals(before.readingSessions, deletion.state.readingSessions)
    }
}
