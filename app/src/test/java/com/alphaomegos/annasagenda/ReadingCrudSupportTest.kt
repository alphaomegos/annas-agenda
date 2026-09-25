package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the media library is allowed to do to a book, a film or a series.
 *
 * These functions are the only place where the rules live — the year is
 * stamped here, the page count is clamped here, a cover is dropped here — and
 * until now none of it was covered by anything that runs on a plain JVM. Every
 * case below is behaviour the app has today; the point is that it stays that
 * way when the screens around it are taken apart.
 */
class ReadingCrudSupportTest {

    private val year = 2026

    // -- a session in progress -----------------------------------------------

    private val startedAt = 1_700_000_000_000L
    private val anHourLater = startedAt + 60 * 60_000L

    private val reading = ActiveReading(
        bookId = 1L,
        startedAtEpochMillis = startedAt,
        startPage = 40,
    )

    private fun book(
        id: Long = 1L,
        shelf: ReadingShelf = ReadingShelf.NOW,
        currentPage: Int = 40,
        totalPages: Int = 600,
    ) = ReadingBook(id = id, title = "Dune", totalPages = totalPages, shelf = shelf, currentPage = currentPage)

    private fun changed(
        active: ActiveReading? = reading,
        book: ReadingBook = book(),
        pending: ReadingSession? = null,
        autoRecord: Boolean = false,
        now: Long = anHourLater,
        idFrom: Long = 900L,
    ) = readingAfterBookChanged(
        active = active,
        changed = book,
        pending = pending,
        autoRecord = autoRecord,
        nowEpochMillis = now,
        newSessionId = { idFrom },
    )

    @Test
    fun readingChange_ignoresAChangeToSomeOtherBook() {
        val after = changed(book = book(id = 2L, shelf = ReadingShelf.DONE))

        assertEquals(reading, after.activeReading)
        assertEquals(emptyList<ReadingSession>(), after.sessionsToRecord)
        assertNull(after.pendingReadingSession)
    }

    @Test
    fun readingChange_survivesWhileItsBookIsOnTheNowShelf() {
        val after = changed()

        assertEquals(reading, after.activeReading)
        assertNull(after.pendingReadingSession)
    }

    /**
     * The page the session counts from follows the book's own page. Editing
     * the page while reading means the earlier number was wrong, so the
     * session should not go on measuring from it.
     */
    @Test
    fun readingChange_followsThePageTheBookIsOn() {
        val after = changed(book = book(currentPage = 120))

        assertEquals(120, after.activeReading?.startPage)
        assertEquals(startedAt, after.activeReading?.startedAtEpochMillis)
    }

    @Test
    fun readingChange_hasNothingToSayWhenThereIsNoSession() {
        val after = changed(active = null, book = book(shelf = ReadingShelf.DONE))

        assertNull(after.activeReading)
        assertNull(after.pendingReadingSession)
        assertEquals(emptyList<ReadingSession>(), after.sessionsToRecord)
    }

    /**
     * The case this was written for: an hour of reading followed by marking
     * the book finished used to leave nothing behind, and say nothing.
     */
    @Test
    fun readingChange_turnsAnInterruptedSessionIntoAQuestion() {
        listOf(ReadingShelf.DONE, ReadingShelf.ABANDONED, ReadingShelf.PLANS).forEach { shelf ->
            val after = changed(book = book(shelf = shelf, currentPage = 90))

            assertNull("$shelf kept reading", after.activeReading)
            assertEquals(emptyList<ReadingSession>(), after.sessionsToRecord)

            val asked = after.pendingReadingSession
            assertEquals("$shelf asked nothing", 1L, asked?.bookId)
            assertEquals(60, asked?.durationMinutes)
            assertEquals(40, asked?.startPage)
            assertEquals(90, asked?.endPage)
            assertEquals(startedAt, asked?.startedAtEpochMillis)
            assertEquals(anHourLater, asked?.createdAtEpochMillis)
        }
    }

    @Test
    fun readingChange_recordsItOutrightOnceTheUserHasSaidToStopAsking() {
        val after = changed(book = book(shelf = ReadingShelf.DONE, currentPage = 90), autoRecord = true)

        assertNull(after.activeReading)
        assertNull(after.pendingReadingSession)
        assertEquals(1, after.sessionsToRecord.size)
        assertEquals(60, after.sessionsToRecord.single().durationMinutes)
    }

    /**
     * A question nobody has answered yet is not thrown away to make room for a
     * second one — losing it is the exact thing this is here to prevent.
     */
    @Test
    fun readingChange_keepsAnOlderQuestionByRecordingIt() {
        val older = ReadingSession(
            id = 11L,
            bookId = 2L,
            startedAtEpochMillis = 1L,
            durationMinutes = 15,
            startPage = 0,
            endPage = 10,
        )

        val after = changed(book = book(shelf = ReadingShelf.DONE), pending = older)

        assertEquals(listOf(older), after.sessionsToRecord)
        assertEquals(900L, after.pendingReadingSession?.id)
    }

    @Test
    fun readingChange_leavesAnOlderQuestionAloneWhileNothingEnds() {
        val older = ReadingSession(
            id = 11L,
            bookId = 2L,
            startedAtEpochMillis = 1L,
            durationMinutes = 15,
            startPage = 0,
            endPage = 10,
        )

        assertEquals(older, changed(pending = older).pendingReadingSession)
        assertEquals(older, changed(book = book(id = 2L), pending = older).pendingReadingSession)
    }

    /**
     * Nought pages is the honest answer when the user never said they had
     * moved: the app cannot know, and inventing a number would put a made-up
     * reading speed into the estimate.
     */
    @Test
    fun readingChange_recordsNoughtPagesWhenTheBookmarkNeverMoved() {
        val after = changed(book = book(shelf = ReadingShelf.DONE))

        assertEquals(40, after.pendingReadingSession?.startPage)
        assertEquals(40, after.pendingReadingSession?.endPage)
    }

    /** A session of no minutes reads as a bug, so there is never one. */
    @Test
    fun readingChange_neverProducesASessionOfNoTime() {
        val instant = changed(book = book(shelf = ReadingShelf.DONE), now = startedAt)
        assertEquals(1, instant.pendingReadingSession?.durationMinutes)

        val clockWentBack = changed(book = book(shelf = ReadingShelf.DONE), now = startedAt - 90_000L)
        assertEquals(1, clockWentBack.pendingReadingSession?.durationMinutes)
    }

    @Test
    fun readingChange_keepsThePagesInsideTheBook() {
        val after = changed(
            active = reading.copy(startPage = 9_000),
            book = book(shelf = ReadingShelf.DONE, currentPage = 9_000, totalPages = 300),
        )

        assertEquals(300, after.pendingReadingSession?.startPage)
        assertEquals(300, after.pendingReadingSession?.endPage)
    }

    // -- books ---------------------------------------------------------------

    @Test
    fun buildReadingBook_refusesABookWithNoTitleOrNoPages() {
        assertNull(newBook(title = "   "))
        assertNull(newBook(totalPages = 0))
        assertNull(newBook(totalPages = -10))
    }

    @Test
    fun buildReadingBook_trimsWhatTheUserTyped() {
        val book = newBook(title = "  Dune  ", author = "  Herbert  ")!!

        assertEquals("Dune", book.title)
        assertEquals("Herbert", book.author)
        assertEquals(0, book.currentPage)
    }

    @Test
    fun buildReadingBook_stampsTheYearOnlyOnTheShelfThatMeansIt() {
        val done = newBook(shelf = ReadingShelf.DONE)!!
        assertEquals(year, done.yearRead)
        assertNull(done.yearAbandoned)

        val abandoned = newBook(shelf = ReadingShelf.ABANDONED)!!
        assertEquals(year, abandoned.yearAbandoned)
        assertNull(abandoned.yearRead)

        val planned = newBook(shelf = ReadingShelf.PLANS)!!
        assertNull(planned.yearRead)
        assertNull(planned.yearAbandoned)
    }

    @Test
    fun moveReadingBookToShelf_keepsOnlyTheYearTheNewShelfCanHave() {
        val finished = newBook(shelf = ReadingShelf.DONE)!!

        val abandoned = moveReadingBookToShelf(finished, ReadingShelf.ABANDONED, year)
        assertNull("a book cannot be both read and abandoned", abandoned.yearRead)
        assertEquals(year, abandoned.yearAbandoned)

        val backToPlans = moveReadingBookToShelf(abandoned, ReadingShelf.PLANS, year)
        assertNull(backToPlans.yearRead)
        assertNull(backToPlans.yearAbandoned)
    }

    @Test
    fun updateReadingBookEntity_willNotLetARequiredTitleBeEmptied() {
        val book = newBook(title = "Dune")!!

        assertEquals("Dune", updateBook(book, title = "").title)
        assertEquals("Dune", updateBook(book, title = "   ").title)
        assertEquals("Messiah", updateBook(book, title = "  Messiah ").title)
    }

    @Test
    fun updateReadingBookEntity_pullsTheCurrentPageDownWithTheTotal() {
        val book = updateBook(newBook(totalPages = 500)!!, currentPage = 400)

        val shortened = updateBook(book, totalPages = 100)

        assertEquals(100, shortened.totalPages)
        assertEquals(
            "reading past the last page is not a state the app should hold",
            100,
            shortened.currentPage,
        )
    }

    @Test
    fun updateReadingBookEntity_keepsAtLeastOnePage() {
        val shrunk = updateBook(newBook(totalPages = 500)!!, totalPages = 0)

        assertEquals(1, shrunk.totalPages)
        assertEquals(0, shrunk.currentPage)
    }

    @Test
    fun updateReadingBookEntity_clearingACoverBeatsReplacingIt() {
        val withCover = updateBook(newBook()!!, coverUri = "internal://media_covers/book_1_aaaa.jpg")

        assertNull(updateBook(withCover, coverUri = "internal://other.jpg", clearCover = true).coverUri)
        assertEquals(
            "internal://media_covers/book_1_aaaa.jpg",
            updateBook(withCover, title = "Something else").coverUri,
        )
    }

    /**
     * Editing a finished book keeps the year it was finished in, while dragging
     * it onto the shelf stamps today's. The two are not the same operation and
     * they do not agree — worth knowing before anything here is rewritten.
     */
    @Test
    fun updateReadingBookEntity_doesNotRestampAYearThatIsAlreadyThere() {
        val finishedInThePast = updateBook(
            newBook(shelf = ReadingShelf.DONE)!!.copy(yearRead = 2019),
            title = "Same book",
        )

        assertEquals(2019, finishedInThePast.yearRead)

        assertEquals(
            year,
            moveReadingBookToShelf(finishedInThePast, ReadingShelf.DONE, year).yearRead,
        )
    }

    @Test
    fun updateReadingBookEntity_dropsBothYearsWhenTheBookLeavesTheShelfThatHadThem() {
        val finished = newBook(shelf = ReadingShelf.DONE)!!

        val reopened = updateBook(finished, shelf = ReadingShelf.NOW)

        assertNull(reopened.yearRead)
        assertNull(reopened.yearAbandoned)
    }

    // -- films ---------------------------------------------------------------

    @Test
    fun buildReadingMovie_takesOnlyAYearThatCouldBeOne() {
        assertNull(newMovie(title = " "))
        assertEquals(1977, newMovie(releaseYear = 1977)!!.releaseYear)
        assertNull("a year out of range is not a year", newMovie(releaseYear = 20255)!!.releaseYear)
        assertNull(newMovie(releaseYear = 0)!!.releaseYear)
        assertEquals("dubbed", newMovie(translation = "  dubbed ")!!.translation)
    }

    @Test
    fun updateReadingMovieEntity_clearsTheReleaseYearOnlyWhenAskedTo() {
        val movie = newMovie(releaseYear = 1977)!!

        assertNull(updateMovie(movie, clearReleaseYear = true).releaseYear)
        assertEquals(1977, updateMovie(movie, title = "Star Wars").releaseYear)
        assertEquals(1980, updateMovie(movie, releaseYear = 1980).releaseYear)
    }

    /**
     * A year that cannot be one is bad input, and bad input must not destroy
     * what is already stored — the same rule the anthropometry fields now
     * follow. Emptying the field is how the year is removed, and that arrives
     * as clearReleaseYear.
     */
    @Test
    fun updateReadingMovieEntity_keepsTheStoredYearWhenTheNewOneIsImpossible() {
        val movie = newMovie(releaseYear = 1977)!!

        assertEquals(1977, updateMovie(movie, releaseYear = 20255).releaseYear)
        assertEquals(1977, updateMovie(movie, releaseYear = 0).releaseYear)
        assertEquals(1977, updateMovie(movie, releaseYear = -5).releaseYear)
    }

    @Test
    fun moveReadingMovieToShelf_keepsOnlyTheYearTheNewShelfCanHave() {
        val watched = newMovie(shelf = ReadingShelf.DONE)!!
        assertEquals(year, watched.yearWatched)

        val abandoned = moveReadingMovieToShelf(watched, ReadingShelf.ABANDONED, year)
        assertNull(abandoned.yearWatched)
        assertEquals(year, abandoned.yearAbandoned)

        val planned = moveReadingMovieToShelf(abandoned, ReadingShelf.PLANS, year)
        assertNull(planned.yearWatched)
        assertNull(planned.yearAbandoned)
    }

    // -- series --------------------------------------------------------------

    @Test
    fun buildReadingSeries_keepsTheSeasonInsideTheShow() {
        val series = newSeries(totalSeasons = 3, currentSeason = 9, currentEpisode = 0)!!

        assertEquals(3, series.totalSeasons)
        assertEquals(3, series.currentSeason)
        assertEquals(1, series.currentEpisode)

        assertEquals(1, newSeries(totalSeasons = 0)!!.totalSeasons)
    }

    @Test
    fun updateReadingSeriesEntity_pullsTheCurrentSeasonDownWithTheTotal() {
        val series = newSeries(totalSeasons = 5, currentSeason = 5)!!

        val shortened = updateReadingSeriesEntity(old = series, totalSeasons = 2, currentYear = year)

        assertEquals(2, shortened.totalSeasons)
        assertEquals(2, shortened.currentSeason)
    }

    @Test
    fun updateReadingSeriesEntity_willNotLetARequiredTitleBeEmptied() {
        val series = newSeries(title = "Twin Peaks")!!

        assertEquals(
            "Twin Peaks",
            updateReadingSeriesEntity(old = series, title = "  ", currentYear = year).title,
        )
    }

    // -- fixtures ------------------------------------------------------------

    private fun newBook(
        shelf: ReadingShelf = ReadingShelf.PLANS,
        title: String = "Dune",
        totalPages: Int = 400,
        author: String = "Herbert",
    ) = buildReadingBook(
        id = 1L,
        shelf = shelf,
        title = title,
        totalPages = totalPages,
        author = author,
        createdAtEpochMillis = 1_000L,
        currentYear = year,
    )

    private fun updateBook(
        old: ReadingBook,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        totalPages: Int? = null,
        currentPage: Int? = null,
        shelf: ReadingShelf? = null,
    ) = updateReadingBookEntity(
        old = old,
        title = title,
        coverUri = coverUri,
        clearCover = clearCover,
        totalPages = totalPages,
        currentPage = currentPage,
        shelf = shelf,
        currentYear = year,
    )

    private fun newMovie(
        shelf: ReadingShelf = ReadingShelf.PLANS,
        title: String = "Star Wars",
        releaseYear: Int? = null,
        translation: String = "",
    ) = buildReadingMovie(
        id = 2L,
        shelf = shelf,
        title = title,
        releaseYear = releaseYear,
        translation = translation,
        createdAtEpochMillis = 1_000L,
        currentYear = year,
    )

    private fun updateMovie(
        old: ReadingMovie,
        title: String? = null,
        releaseYear: Int? = null,
        clearReleaseYear: Boolean = false,
    ) = updateReadingMovieEntity(
        old = old,
        title = title,
        releaseYear = releaseYear,
        clearReleaseYear = clearReleaseYear,
        currentYear = year,
    )

    /**
     * The same rule as for books and films, which is exactly why it is worth
     * asking: three near-identical functions are where one quietly stops
     * matching the other two.
     */
    @Test
    fun moveReadingSeriesToShelf_keepsOnlyTheYearTheNewShelfCanHave() {
        val watched = newSeries(shelf = ReadingShelf.DONE)!!
        assertEquals(year, watched.yearWatched)

        val abandoned = moveReadingSeriesToShelf(watched, ReadingShelf.ABANDONED, year)
        assertNull(abandoned.yearWatched)
        assertEquals(year, abandoned.yearAbandoned)

        val planned = moveReadingSeriesToShelf(abandoned, ReadingShelf.PLANS, year)
        assertNull(planned.yearWatched)
        assertNull(planned.yearAbandoned)

        val watchingAgain = moveReadingSeriesToShelf(planned, ReadingShelf.NOW, year)
        assertNull(watchingAgain.yearWatched)
        assertNull(watchingAgain.yearAbandoned)
    }

    /** Where it was left off is not a year, so a shelf move does not touch it. */
    @Test
    fun moveReadingSeriesToShelf_leavesTheSeasonAndEpisodeWhereTheyWere() {
        val watching = newSeries(totalSeasons = 5, currentSeason = 3, currentEpisode = 7)!!

        val done = moveReadingSeriesToShelf(watching, ReadingShelf.DONE, year)

        assertEquals(3, done.currentSeason)
        assertEquals(7, done.currentEpisode)
    }

    private fun newSeries(
        title: String = "Twin Peaks",
        totalSeasons: Int = 2,
        currentSeason: Int = 1,
        currentEpisode: Int = 1,
        shelf: ReadingShelf = ReadingShelf.NOW,
    ) = buildReadingSeries(
        id = 3L,
        shelf = shelf,
        title = title,
        totalSeasons = totalSeasons,
        currentSeason = currentSeason,
        currentEpisode = currentEpisode,
        createdAtEpochMillis = 1_000L,
        currentYear = year,
    )
}
