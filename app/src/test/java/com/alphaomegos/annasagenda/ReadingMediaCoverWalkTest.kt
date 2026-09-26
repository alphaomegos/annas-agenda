package com.alphaomegos.annasagenda

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walk that offers every cover in the library to the migration, and the
 * question asked either side of a cover import.
 *
 * What the migration itself does needs a Context and a real file and stays in
 * the view model. What is asked here is the walk: three lists, and every item
 * offered with its own kind and its own id. Get that pairing wrong for one
 * list and the file is written under the wrong word — `movie_17_…` for a book —
 * which is a perfectly good file that nothing ever looks for again. It was
 * written out three times by hand until 0109.
 */
class ReadingMediaCoverWalkTest {

    private val book = ReadingBook(id = 1L, title = "Dune", totalPages = 700, coverUri = "file:///a.jpg")
    private val movie = ReadingMovie(id = 2L, title = "Stalker", coverUri = "file:///b.jpg")
    private val series = ReadingSeries(id = 3L, title = "Twin Peaks", coverUri = "file:///c.jpg")

    private val library = AppState(
        readingBooks = listOf(book),
        readingMovies = listOf(movie),
        readingSeries = listOf(series),
    )

    /** Everything the walk offered, in the order it offered it. */
    private class Recorder {
        val seen = mutableListOf<Triple<String?, ReadingMediaType, Long>>()

        suspend fun migrate(ref: String?, type: ReadingMediaType, id: Long): String? {
            seen += Triple(ref, type, id)
            return ref
        }
    }

    @Test
    fun everyItemIsOfferedWithItsOwnKindAndItsOwnId() {
        val recorder = Recorder()

        runBlocking { stateWithCoverRefsMigrated(library, recorder::migrate) }

        assertEquals(
            listOf(
                Triple("file:///a.jpg", ReadingMediaType.BOOKS, 1L),
                Triple("file:///b.jpg", ReadingMediaType.MOVIES, 2L),
                Triple("file:///c.jpg", ReadingMediaType.SERIES, 3L),
            ),
            recorder.seen,
        )
    }

    /**
     * An item without a cover is offered too. Deciding that null is not worth
     * migrating is the migration's business, not the walk's.
     */
    @Test
    fun anItemWithNoCoverIsOfferedAsWell() {
        val recorder = Recorder()
        val bare = AppState(readingBooks = listOf(book.copy(coverUri = null)))

        runBlocking { stateWithCoverRefsMigrated(bare, recorder::migrate) }

        assertEquals(listOf(Triple(null, ReadingMediaType.BOOKS, 1L)), recorder.seen)
    }

    @Test
    fun aNewRefReplacesTheOldOneOnThatItemOnly() {
        val after = runBlocking {
            stateWithCoverRefsMigrated(library) { ref, type, id ->
                if (type == ReadingMediaType.MOVIES) "internal://media_covers/movie_${id}_x.jpg" else ref
            }
        }

        assertEquals("internal://media_covers/movie_2_x.jpg", after.readingMovies.single().coverUri)
        assertEquals("file:///a.jpg", after.readingBooks.single().coverUri)
        assertEquals("file:///c.jpg", after.readingSeries.single().coverUri)
    }

    @Test
    fun allThreeListsCanChangeAtOnce() {
        val after = runBlocking {
            stateWithCoverRefsMigrated(library) { _, type, id ->
                "internal://media_covers/${coverMediaKind(type)}_${id}_x.jpg"
            }
        }

        assertEquals("internal://media_covers/book_1_x.jpg", after.readingBooks.single().coverUri)
        assertEquals("internal://media_covers/movie_2_x.jpg", after.readingMovies.single().coverUri)
        assertEquals("internal://media_covers/series_3_x.jpg", after.readingSeries.single().coverUri)
    }

    /**
     * Nothing else about the item may move while its cover does.
     */
    @Test
    fun onlyTheCoverChanges() {
        val after = runBlocking {
            stateWithCoverRefsMigrated(library) { _, _, _ -> "internal://x.jpg" }
        }

        assertEquals(book.copy(coverUri = "internal://x.jpg"), after.readingBooks.single())
        assertEquals(movie.copy(coverUri = "internal://x.jpg"), after.readingMovies.single())
        assertEquals(series.copy(coverUri = "internal://x.jpg"), after.readingSeries.single())
    }

    /**
     * And nothing outside the library may move at all. The caller writes the
     * three media lists back over live state, so anything else this touched
     * would be silently carried across with them.
     */
    @Test
    fun theRestOfTheStateIsUntouched() {
        val busy = library.copy(
            tasks = listOf(Task(id = 10L, description = "Купить хлеб")),
            idHighWater = 1088L,
        )

        val after = runBlocking {
            stateWithCoverRefsMigrated(busy) { _, _, _ -> "internal://x.jpg" }
        }

        assertEquals(busy.tasks, after.tasks)
        assertEquals(1088L, after.idHighWater)
    }

    /**
     * When the migration changes nothing, the caller gets back exactly what it
     * passed in — it compares the two to decide whether anything needs saving.
     */
    @Test
    fun aLibraryWithNothingToMigrateComesBackAsItself() {
        val after = runBlocking { stateWithCoverRefsMigrated(library) { ref, _, _ -> ref } }

        assertSame(library, after)
    }

    @Test
    fun anEmptyLibraryComesBackAsItself() {
        val empty = AppState()

        assertSame(empty, runBlocking { stateWithCoverRefsMigrated(empty) { _, _, _ -> "x" } })
    }

    /* ---------------- is the item still there ---------------- */

    /**
     * Asked before a cover import and again after, because importing takes
     * long enough for the user to delete the item while it runs.
     */
    @Test
    fun anItemIsFoundOnlyInItsOwnList() {
        assertTrue(readingMediaExists(library, ReadingMediaType.BOOKS, 1L))
        assertTrue(readingMediaExists(library, ReadingMediaType.MOVIES, 2L))
        assertTrue(readingMediaExists(library, ReadingMediaType.SERIES, 3L))

        assertFalse("a film's id is not a book's", readingMediaExists(library, ReadingMediaType.BOOKS, 2L))
        assertFalse(readingMediaExists(library, ReadingMediaType.MOVIES, 1L))
        assertFalse(readingMediaExists(library, ReadingMediaType.SERIES, 1L))
    }

    @Test
    fun anItemThatHasBeenDeletedIsNotFound() {
        val after = AppState(readingMovies = listOf(movie), readingSeries = listOf(series))

        assertFalse(readingMediaExists(after, ReadingMediaType.BOOKS, 1L))
    }

    @Test
    fun nothingIsFoundInAnEmptyLibrary() {
        ReadingMediaType.entries.forEach { type ->
            assertFalse(type.name, readingMediaExists(AppState(), type, 1L))
        }
    }
}
