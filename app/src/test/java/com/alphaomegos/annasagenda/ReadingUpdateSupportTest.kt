package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Editing an item, and what happens to the picture it used to have.
 *
 * The field-by-field edit has its own tests. What is asked here is the cover's
 * lifetime, which is a rule about ownership: a file whose ref has been
 * replaced has no owner and must go, and a file whose ref is unchanged must be
 * left alone because the item is still using it.
 *
 * Both halves matter. 0014 was the first one going wrong — the file was
 * destroyed while the state still named it, and the old picture stayed on
 * screen until the app was restarted.
 */
class ReadingUpdateSupportTest {

    private val cover = "internal://media_covers/book_1_abc.jpg"

    private val book = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = 700,
        coverUri = cover,
        shelf = ReadingShelf.NOW,
    )

    private val state = AppState(readingBooks = listOf(book))

    private fun updateBook(
        from: AppState = state,
        id: Long = 1L,
        newSessionId: Long = 99L,
        change: (ReadingBook) -> ReadingBook,
    ) = stateAfterUpdatingReadingBook(
        state = from,
        bookId = id,
        nowEpochMillis = 2_000_000L,
        newSessionId = { newSessionId },
        update = change,
    )

    @Test
    fun editingSomethingThatIsNotThereAnswersNothing() {
        assertNull(updateBook(id = 404L) { it })
        assertNull(stateAfterUpdatingReadingMovie(state, 404L) { it })
        assertNull(stateAfterUpdatingReadingSeries(state, 404L) { it })
    }

    @Test
    fun theEditedBookReplacesTheOldOneInPlace() {
        val after = updateBook { it.copy(title = "Dune Messiah") }!!

        assertEquals("Dune Messiah", after.state.readingBooks.single().title)
    }

    /**
     * A new picture means the old file has no owner left.
     */
    @Test
    fun replacingTheCoverNamesTheOldFileForDeletion() {
        val after = updateBook { it.copy(coverUri = "internal://media_covers/book_1_xyz.jpg") }!!

        assertEquals(cover, after.coverToDelete)
    }

    /**
     * The other half, and the one that used to destroy a file somebody was
     * still looking at: an edit that leaves the cover alone must leave the
     * file alone.
     */
    @Test
    fun editingSomethingElseLeavesTheCoverFileAlone() {
        val after = updateBook { it.copy(title = "Dune Messiah") }!!

        assertNull(after.coverToDelete)
        assertEquals(cover, after.state.readingBooks.single().coverUri)
    }

    @Test
    fun clearingTheCoverNamesTheOldFileForDeletion() {
        val after = updateBook { it.copy(coverUri = null) }!!

        assertEquals(cover, after.coverToDelete)
        assertNull(after.state.readingBooks.single().coverUri)
    }

    @Test
    fun aBookThatNeverHadACoverNamesNothing() {
        val plain = state.copy(readingBooks = listOf(book.copy(coverUri = null)))

        val after = updateBook(from = plain) { it.copy(title = "x") }!!

        assertNull(after.coverToDelete)
    }

    /**
     * Editing a book can move it off the Now shelf, and that ends a reading in
     * progress. The rule is stateWithReadingBookChanged's; what is checked
     * here is that an edit goes through it at all.
     */
    @Test
    fun anEditThatMovesABookOffNowEndsTheReading() {
        val reading = state.copy(
            activeReading = ActiveReading(
                bookId = 1L,
                startedAtEpochMillis = 1_000_000L,
                startPage = 10,
            ),
        )

        val after = updateBook(from = reading) { it.copy(shelf = ReadingShelf.DONE) }!!

        assertNull(after.state.activeReading)
        assertNotNull("the time must become a question", after.state.pendingReadingSession)
        assertEquals(99L, after.state.pendingReadingSession?.id)
    }

    @Test
    fun anEditThatLeavesTheBookOnNowKeepsTheReading() {
        val reading = state.copy(
            activeReading = ActiveReading(
                bookId = 1L,
                startedAtEpochMillis = 1_000_000L,
                startPage = 10,
            ),
        )

        val after = updateBook(from = reading) { it.copy(currentPage = 250) }!!

        assertEquals(1L, after.state.activeReading?.bookId)
        assertNull(after.state.pendingReadingSession)
    }

    @Test
    fun aFilmFollowsTheSameCoverRule() {
        val movie = ReadingMovie(id = 2L, title = "Stalker", coverUri = "internal://m.jpg")
        val before = state.copy(readingMovies = listOf(movie))

        val replaced = stateAfterUpdatingReadingMovie(before, 2L) {
            it.copy(coverUri = "internal://m2.jpg")
        }!!
        assertEquals("internal://m.jpg", replaced.coverToDelete)

        val renamed = stateAfterUpdatingReadingMovie(before, 2L) { it.copy(title = "Solaris") }!!
        assertNull(renamed.coverToDelete)
        assertEquals("Solaris", renamed.state.readingMovies.single().title)
    }

    @Test
    fun aSeriesFollowsTheSameCoverRule() {
        val series = ReadingSeries(id = 3L, title = "Twin Peaks", coverUri = "internal://s.jpg")
        val before = state.copy(readingSeries = listOf(series))

        val replaced = stateAfterUpdatingReadingSeries(before, 3L) { it.copy(coverUri = null) }!!
        assertEquals("internal://s.jpg", replaced.coverToDelete)

        val moved = stateAfterUpdatingReadingSeries(before, 3L) { it.copy(currentEpisode = 4) }!!
        assertNull(moved.coverToDelete)
        assertEquals(4, moved.state.readingSeries.single().currentEpisode)
    }

    /**
     * Editing one item must not disturb another.
     */
    @Test
    fun theOtherItemsAreLeftExactlyAsTheyWere() {
        val second = ReadingBook(id = 5L, title = "Solaris", totalPages = 300)
        val before = state.copy(readingBooks = listOf(book, second))

        val after = updateBook(from = before) { it.copy(title = "x") }!!

        assertEquals(second, after.state.readingBooks.last())
    }
}
