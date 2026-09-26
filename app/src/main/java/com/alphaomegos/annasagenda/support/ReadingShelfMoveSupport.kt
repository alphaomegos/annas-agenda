package com.alphaomegos.annasagenda

/**
 * The state after something happened to a book, with the session in progress
 * dealt with.
 *
 * [readingAfterBookChanged] already decides what becomes of a live session
 * when its book changes; what lived in the view model was the other half —
 * putting that decision into the three fields it touches. Three fields, and
 * all three have to move together: the session in progress, the question
 * waiting to be answered, and the history. Splitting that across a rule and
 * its caller is how the caller ends up applying two of the three.
 *
 * [newSessionId] is a function because the far more common path ends no
 * session and must not burn an id.
 */
fun stateWithReadingBookChanged(
    state: AppState,
    changed: ReadingBook,
    books: List<ReadingBook>,
    nowEpochMillis: Long,
    newSessionId: () -> Long,
): AppState {
    val after = readingAfterBookChanged(
        active = state.activeReading,
        changed = changed,
        pending = state.pendingReadingSession,
        autoRecord = state.autoRecordInterruptedReading,
        nowEpochMillis = nowEpochMillis,
        newSessionId = newSessionId,
    )

    return state.copy(
        readingBooks = books,
        activeReading = after.activeReading,
        pendingReadingSession = after.pendingReadingSession,
        readingSessions = state.readingSessions + after.sessionsToRecord,
    )
}

/**
 * Moving a book to another shelf, session and all.
 *
 * Null when there is nothing to do — no such book, or it is already on that
 * shelf. The caller then leaves the state exactly as it was rather than
 * writing an identical one.
 *
 * Dragging a book out of Now is the moment an hour of reading used to
 * disappear, which is what 0069 was about. The rule for that lives in
 * readingAfterBookChanged; this is what makes sure a shelf move actually goes
 * through it.
 */
fun stateAfterMovingReadingBookToShelf(
    state: AppState,
    bookId: Long,
    shelf: ReadingShelf,
    currentYear: Int,
    nowEpochMillis: Long,
    newSessionId: () -> Long,
): AppState? {
    val book = state.readingBooks.firstOrNull { it.id == bookId } ?: return null
    if (book.shelf == shelf) return null

    val moved = moveReadingBookToShelf(book, shelf, currentYear)

    return stateWithReadingBookChanged(
        state = state,
        changed = moved,
        books = state.readingBooks.map { b -> if (b.id == bookId) moved else b },
        nowEpochMillis = nowEpochMillis,
        newSessionId = newSessionId,
    )
}

/**
 * A film has no session to end, so moving it is the shelf and the year and
 * nothing else.
 */
fun stateAfterMovingReadingMovieToShelf(
    state: AppState,
    movieId: Long,
    shelf: ReadingShelf,
    currentYear: Int,
): AppState? {
    val movie = state.readingMovies.firstOrNull { it.id == movieId } ?: return null
    if (movie.shelf == shelf) return null

    return state.copy(
        readingMovies = state.readingMovies.map { m ->
            if (m.id == movieId) moveReadingMovieToShelf(m, shelf, currentYear) else m
        }
    )
}

fun stateAfterMovingReadingSeriesToShelf(
    state: AppState,
    seriesId: Long,
    shelf: ReadingShelf,
    currentYear: Int,
): AppState? {
    val series = state.readingSeries.firstOrNull { it.id == seriesId } ?: return null
    if (series.shelf == shelf) return null

    return state.copy(
        readingSeries = state.readingSeries.map { s ->
            if (s.id == seriesId) moveReadingSeriesToShelf(s, shelf, currentYear) else s
        }
    )
}
