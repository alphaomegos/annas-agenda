package com.alphaomegos.annasagenda.screens.media

import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.ReadingBook
import com.alphaomegos.annasagenda.ReadingMediaFilter
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ReadingMovie
import com.alphaomegos.annasagenda.ReadingSeries
import com.alphaomegos.annasagenda.ReadingShelf
import com.alphaomegos.annasagenda.ReadingSort
import com.alphaomegos.annasagenda.ReadingSortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the media list shows, and what a row is.
 *
 * All of this is pure and none of it was covered. The list screen is the one
 * place where books, films and series appear together, so it is also where a
 * mistake shows up as somebody else's item in somebody else's tab.
 */
class MediaLibrarySupportTest {

    private fun book(id: Long, title: String = "Dune", shelf: ReadingShelf = ReadingShelf.NOW) =
        ReadingBookItem(ReadingBook(id = id, title = title, totalPages = 100, shelf = shelf))

    private fun movie(id: Long, title: String = "Alien", shelf: ReadingShelf = ReadingShelf.NOW) =
        ReadingMovieItem(ReadingMovie(id = id, title = title, shelf = shelf))

    private fun series(id: Long, title: String = "Twin Peaks", shelf: ReadingShelf = ReadingShelf.NOW) =
        ReadingSeriesItem(ReadingSeries(id = id, title = title, shelf = shelf))

    @Test
    fun everyRowKnowsWhichKindOfMediaItIs() {
        assertEquals(ReadingMediaType.BOOKS, mediaTypeOf(book(1L)))
        assertEquals(ReadingMediaType.MOVIES, mediaTypeOf(movie(2L)))
        assertEquals(ReadingMediaType.SERIES, mediaTypeOf(series(3L)))
    }

    @Test
    fun rowsOfDifferentKindsWithTheSameIdAreDifferentRows() {
        // They share the id space, but the list key has to tell them apart or
        // the two swap places on screen.
        assertEquals(
            3,
            listOf(book(1L), movie(1L), series(1L)).map { it.stableKey }.distinct().size,
        )
    }

    @Test
    fun onlyABookOnAShelfYouAreStillOnCanBeRead() {
        assertTrue(canReadItem(book(1L, shelf = ReadingShelf.PLANS)))
        assertTrue(canReadItem(book(1L, shelf = ReadingShelf.NOW)))

        assertFalse(canReadItem(book(1L, shelf = ReadingShelf.DONE)))
        assertFalse(canReadItem(book(1L, shelf = ReadingShelf.ABANDONED)))

        // There is no reading session for something you watch.
        assertFalse(canReadItem(movie(1L, shelf = ReadingShelf.NOW)))
        assertFalse(canReadItem(series(1L, shelf = ReadingShelf.NOW)))
    }

    @Test
    fun theDefaultKindToAddIsOneTheFilterIsShowing() {
        assertEquals(
            ReadingMediaType.MOVIES,
            defaultMediaType(ReadingMediaFilter(showBooks = false, showMovies = true, showSeries = true)),
        )

        assertEquals(
            ReadingMediaType.SERIES,
            defaultMediaType(ReadingMediaFilter(showBooks = false, showMovies = false, showSeries = true)),
        )

        // Nothing shown at all still has to answer something.
        assertEquals(
            ReadingMediaType.BOOKS,
            defaultMediaType(ReadingMediaFilter(showBooks = false, showMovies = false, showSeries = false)),
        )
    }

    @Test
    fun theListShowsOnlyTheShelfBeingLookedAt() {
        val state = AppState(
            readingBooks = listOf(
                ReadingBook(id = 1L, title = "Here", totalPages = 10, shelf = ReadingShelf.NOW),
                ReadingBook(id = 2L, title = "Elsewhere", totalPages = 10, shelf = ReadingShelf.DONE),
            ),
        )

        val visible = buildVisibleReadingItems(
            state = state,
            shelf = ReadingShelf.NOW,
            sort = ReadingSort(field = ReadingSortField.TITLE, ascending = true),
            query = "",
        )

        assertEquals(listOf("Here"), visible.map { it.title })
    }

    /**
     * A search looks across every shelf on purpose — the point of searching is
     * that you do not remember where you put it.
     */
    @Test
    fun aSearchReachesShelvesTheUserIsNotLookingAt() {
        val state = AppState(
            readingBooks = listOf(
                ReadingBook(id = 1L, title = "Dune", totalPages = 10, shelf = ReadingShelf.DONE),
            ),
        )

        val found = buildVisibleReadingItems(
            state = state,
            shelf = ReadingShelf.NOW,
            sort = ReadingSort(),
            query = "dun",
        )

        assertEquals(listOf("Dune"), found.map { it.title })
    }

    @Test
    fun aKindTheFilterHidesDoesNotAppearEvenInASearch() {
        val state = AppState(
            readingMovies = listOf(ReadingMovie(id = 1L, title = "Dune", shelf = ReadingShelf.NOW)),
            readingMediaFilter = ReadingMediaFilter(showBooks = true, showMovies = false, showSeries = true),
        )

        val found = buildVisibleReadingItems(
            state = state,
            shelf = ReadingShelf.NOW,
            sort = ReadingSort(),
            query = "dune",
        )

        assertEquals(emptyList<String>(), found.map { it.title })
    }
}
