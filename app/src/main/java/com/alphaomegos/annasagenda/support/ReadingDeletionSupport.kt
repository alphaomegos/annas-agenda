package com.alphaomegos.annasagenda

/**
 * A new state, and the cover file that nothing points at any more.
 *
 * Two things produce one: deleting an item, and editing one in a way that
 * changes its cover. Both leave a file behind that has become rubbish, and
 * both have to answer the same question about when it is safe to go.
 *
 * [coverToDelete] is handed back rather than deleted here because deleting a
 * file needs a Context and this has to stay answerable from a terminal — and
 * because a cover must not be destroyed before the state that stopped
 * referring to it has been written. 0014 is the version of that going wrong
 * from the other side: the file was replaced before anything noticed, and the
 * old picture stayed on screen until the app was restarted.
 */
data class ReadingChange(
    val state: AppState,
    val coverToDelete: String?,
)

/**
 * Deleting a book takes everything that only made sense while it existed.
 *
 * Four fields, not one: the book, the sessions recorded against it, the
 * session in progress if it is that book's, and the question waiting to be
 * answered about an interrupted session if it is that book's. A question about
 * a book that no longer exists has no answer worth having — its whole history
 * has just gone with it.
 *
 * This is written out here because it is the shape this project keeps getting
 * wrong: a removal that remembers the obvious field and forgets one of the
 * three that point at it. 0016 was a running plan left pointing at a deleted
 * task, 0033 was four kinds of dangling reference cleaned up at decode, and a
 * running plan entry pointing at a task that no longer existed turned up in
 * real data this week.
 */
fun stateAfterDeletingReadingBook(state: AppState, bookId: Long): ReadingChange? {
    val book = state.readingBooks.firstOrNull { it.id == bookId } ?: return null

    return ReadingChange(
        state = state.copy(
            readingBooks = state.readingBooks.filterNot { it.id == bookId },
            readingSessions = state.readingSessions.filterNot { it.bookId == bookId },
            activeReading = state.activeReading?.takeIf { it.bookId != bookId },
            pendingReadingSession = state.pendingReadingSession?.takeIf { it.bookId != bookId },
        ),
        coverToDelete = book.coverUri,
    )
}

/**
 * A film has no sessions and nothing points at it, so only two things happen:
 * it leaves the list, and its cover becomes rubbish.
 *
 * Kept as its own function rather than folded in with the book's. They differ
 * in what else has to go, and the day a film gains a history of its own — a
 * date watched, a rewatch — this is where that belongs, rather than in a
 * branch inside a shared one.
 */
fun stateAfterDeletingReadingMovie(state: AppState, movieId: Long): ReadingChange? {
    val movie = state.readingMovies.firstOrNull { it.id == movieId } ?: return null

    return ReadingChange(
        state = state.copy(readingMovies = state.readingMovies.filterNot { it.id == movieId }),
        coverToDelete = movie.coverUri,
    )
}

fun stateAfterDeletingReadingSeries(state: AppState, seriesId: Long): ReadingChange? {
    val series = state.readingSeries.firstOrNull { it.id == seriesId } ?: return null

    return ReadingChange(
        state = state.copy(readingSeries = state.readingSeries.filterNot { it.id == seriesId }),
        coverToDelete = series.coverUri,
    )
}
