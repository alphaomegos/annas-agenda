package com.alphaomegos.annasagenda.app

import android.app.Application
import android.net.Uri
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

/** Reading: books, series, movies, sessions (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface ReadingActions : AppStateHost {
    // A cover the app made itself is deleted in the background (29.10): the view model's own scope and context.
    fun readingCleanupCover(ref: String?)

    /* ---------------------------
       Reading
    ---------------------------- */



    fun setReadingViewMode(shelf: ReadingShelf, mode: ReadingViewMode) {
        val st = stateFlow.value
        val prefs = readingTabPrefsForShelf(st, shelf)
        if (prefs.viewMode == mode) return
        stateFlow.value = readingStateWithTabPrefs(st, shelf, prefs.copy(viewMode = mode))
    }

    fun setReadingSort(shelf: ReadingShelf, field: ReadingSortField, ascending: Boolean) {
        val st = stateFlow.value
        val prefs = readingTabPrefsForShelf(st, shelf)
        val newSort = ReadingSort(field = field, ascending = ascending)
        if (prefs.sort == newSort) return
        stateFlow.value = readingStateWithTabPrefs(st, shelf, prefs.copy(sort = newSort))
    }

    fun setReadingMediaFilter(
        showBooks: Boolean? = null,
        showMovies: Boolean? = null,
        showSeries: Boolean? = null,
    ) {
        val st = stateFlow.value
        val old = st.readingMediaFilter
        val updated = old.copy(
            showBooks = showBooks ?: old.showBooks,
            showMovies = showMovies ?: old.showMovies,
            showSeries = showSeries ?: old.showSeries,
        )
        if (updated == old) return
        stateFlow.value = st.copy(readingMediaFilter = updated)
    }

    fun addReadingBook(
        shelf: ReadingShelf,
        title: String,
        totalPages: Int,
        author: String = "",
        coverUri: String? = null
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val book = buildReadingBook(
            id = nextFreshId(),
            shelf = shelf,
            title = title,
            totalPages = totalPages,
            author = author,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = stateFlow.value
        stateFlow.value = st.copy(readingBooks = st.readingBooks + book)
        return book.id
    }

    fun deleteReadingBook(bookId: Long) {
        val deletion = stateAfterDeletingReadingBook(stateFlow.value, bookId) ?: return

        stateFlow.value = deletion.state
        readingCleanupCover(deletion.coverToDelete)
    }

    fun moveReadingBookToShelf(bookId: Long, shelf: ReadingShelf) {
        val moved = stateAfterMovingReadingBookToShelf(
            state = stateFlow.value,
            bookId = bookId,
            shelf = shelf,
            currentYear = LocalDate.now().year,
            nowEpochMillis = System.currentTimeMillis(),
            newSessionId = ::nextFreshId,
        ) ?: return

        stateFlow.value = moved
    }

    fun updateReadingBook(
        bookId: Long,
        author: String? = null,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        totalPages: Int? = null,
        currentPage: Int? = null,
        yearRead: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
    ) {
        val currentYear = LocalDate.now().year

        val change = stateAfterUpdatingReadingBook(
            state = stateFlow.value,
            bookId = bookId,
            nowEpochMillis = System.currentTimeMillis(),
            newSessionId = ::nextFreshId,
        ) { old ->
            updateReadingBookEntity(
                old = old,
                author = author,
                title = title,
                coverUri = coverUri,
                clearCover = clearCover,
                totalPages = totalPages,
                currentPage = currentPage,
                yearRead = yearRead,
                yearAbandoned = yearAbandoned,
                shelf = shelf,
                currentYear = currentYear,
            )
        } ?: return

        stateFlow.value = change.state
        readingCleanupCover(change.coverToDelete)
    }

    fun addReadingMovie(
        shelf: ReadingShelf,
        title: String,
        releaseYear: Int? = null,
        translation: String = "",
        coverUri: String? = null,
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val movie = buildReadingMovie(
            id = nextFreshId(),
            shelf = shelf,
            title = title,
            releaseYear = releaseYear,
            translation = translation,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = stateFlow.value
        stateFlow.value = st.copy(readingMovies = st.readingMovies + movie)
        return movie.id
    }

    fun deleteReadingMovie(movieId: Long) {
        val deletion = stateAfterDeletingReadingMovie(stateFlow.value, movieId) ?: return

        stateFlow.value = deletion.state
        readingCleanupCover(deletion.coverToDelete)
    }

    fun moveReadingMovieToShelf(movieId: Long, shelf: ReadingShelf) {
        val moved = stateAfterMovingReadingMovieToShelf(
            state = stateFlow.value,
            movieId = movieId,
            shelf = shelf,
            currentYear = LocalDate.now().year,
        ) ?: return

        stateFlow.value = moved
    }

    fun updateReadingMovie(
        movieId: Long,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        releaseYear: Int? = null,
        clearReleaseYear: Boolean = false,
        translation: String? = null,
        yearWatched: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
    ) {
        val currentYear = LocalDate.now().year

        val change = stateAfterUpdatingReadingMovie(stateFlow.value, movieId) { old ->
            updateReadingMovieEntity(
                old = old,
                title = title,
                coverUri = coverUri,
                clearCover = clearCover,
                releaseYear = releaseYear,
                clearReleaseYear = clearReleaseYear,
                translation = translation,
                yearWatched = yearWatched,
                yearAbandoned = yearAbandoned,
                shelf = shelf,
                currentYear = currentYear,
            )
        } ?: return

        stateFlow.value = change.state

        readingCleanupCover(change.coverToDelete)
    }

    fun addReadingSeries(
        shelf: ReadingShelf,
        title: String,
        totalSeasons: Int = 1,
        currentSeason: Int = 1,
        currentEpisode: Int = 1,
        coverUri: String? = null,
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val series = buildReadingSeries(
            id = nextFreshId(),
            shelf = shelf,
            title = title,
            totalSeasons = totalSeasons,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = stateFlow.value
        stateFlow.value = st.copy(readingSeries = st.readingSeries + series)
        return series.id
    }

    fun deleteReadingSeries(seriesId: Long) {
        val deletion = stateAfterDeletingReadingSeries(stateFlow.value, seriesId) ?: return

        stateFlow.value = deletion.state
        readingCleanupCover(deletion.coverToDelete)
    }

    fun moveReadingSeriesToShelf(seriesId: Long, shelf: ReadingShelf) {
        val moved = stateAfterMovingReadingSeriesToShelf(
            state = stateFlow.value,
            seriesId = seriesId,
            shelf = shelf,
            currentYear = LocalDate.now().year,
        ) ?: return

        stateFlow.value = moved
    }

    fun updateReadingSeries(
        seriesId: Long,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        totalSeasons: Int? = null,
        currentSeason: Int? = null,
        currentEpisode: Int? = null,
        yearWatched: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
        nextSeasonDate: LocalDate? = null,
        clearNextSeasonDate: Boolean = false,
        remindNextSeason: Boolean? = null,
    ) {
        val currentYear = LocalDate.now().year

        val change = stateAfterUpdatingReadingSeries(stateFlow.value, seriesId) { old ->
            updateReadingSeriesEntity(
                old = old,
                title = title,
                coverUri = coverUri,
                clearCover = clearCover,
                totalSeasons = totalSeasons,
                currentSeason = currentSeason,
                currentEpisode = currentEpisode,
                yearWatched = yearWatched,
                yearAbandoned = yearAbandoned,
                shelf = shelf,
                currentYear = currentYear,
                nextSeasonDate = nextSeasonDate,
                clearNextSeasonDate = clearNextSeasonDate,
                remindNextSeason = remindNextSeason,
            )
        } ?: return

        stateFlow.value = change.state

        readingCleanupCover(change.coverToDelete)
    }


    /**
     * Read then write, rather than `stateFlow.update`: the rule below may hand
     * out a session id, and an update block is a compare-and-set loop that can
     * run its lambda more than once.
     */
    fun beginReading(bookId: Long, startedAtEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val start = stateAfterBeginningReading(
            state = stateFlow.value,
            bookId = bookId,
            startedAtEpochMillis = startedAtEpochMillis,
            currentYear = LocalDate.now().year,
            newSessionId = ::nextFreshId,
        ) ?: return false

        stateFlow.value = start.state
        return start.started
    }

    /**
     * The four ways a reading stops. What each one keeps, and — more easily got
     * wrong — what it must leave alone, is in ReadingSessionEndSupport.
     */
    fun cancelReading() {
        stateFlow.update { cur -> stateAfterCancellingReading(cur) }
    }

    fun keepPendingReadingSession(alwaysFromNowOn: Boolean) {
        stateFlow.update { cur -> stateAfterKeepingPendingReadingSession(cur, alwaysFromNowOn) }
    }

    fun discardPendingReadingSession() {
        stateFlow.update { cur -> stateAfterDiscardingPendingReadingSession(cur) }
    }

    /** False when there is nothing to finish; the screen says so. */
    fun finishReading(
        startPage: Int,
        endPage: Int,
        durationMinutes: Int,
        finishedAtEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val next = stateAfterFinishingReading(
            state = stateFlow.value,
            startPage = startPage,
            endPage = endPage,
            durationMinutes = durationMinutes,
            finishedAtEpochMillis = finishedAtEpochMillis,
            newSessionId = ::nextFreshId,
        ) ?: return false

        stateFlow.value = next
        return true
    }

    /**
     * What the user has written before that looks like what they are writing
     * now.
     *
     * Asked for rather than collected. The new-task screen deliberately
     * subscribes only to the counters — collecting the whole state woke it for
     * every task, every meal and every page turned in a book — and the history
     * does not change while somebody is typing a new task, so a snapshot taken
     * when they stop typing is as good as a subscription and costs nothing.
     */
    fun taskSuggestions(typed: String, limit: Int = 5): List<TaskSuggestion> {
        val cur = stateFlow.value
        return taskSuggestionsFor(typed, cur.tasks, cur.subtasks, limit)
    }

    fun estimateRemainingHours(bookId: Long): Int? {
        val st = stateFlow.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return null
        return estimateRemainingReadingHours(
            book = book,
            sessions = st.readingSessions,
        )
    }
}
