package com.alphaomegos.annasagenda

/**
 * What is left after an item leaves the media library, and what has to be
 * deleted from disk afterwards.
 *
 * [coverToDelete] is the file the caller should remove once the state is
 * written. It is handed back rather than deleted here because deleting a file
 * needs a Context and this has to stay answerable from a terminal — and
 * because a cover must not be destroyed before the state that stopped
 * referring to it has been saved. 0014 is the version of that going wrong from
 * the other side: the file was replaced before anything noticed.
 */
data class ReadingDeletion(
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
fun stateAfterDeletingReadingBook(state: AppState, bookId: Long): ReadingDeletion? {
    val book = state.readingBooks.firstOrNull { it.id == bookId } ?: return null

    return ReadingDeletion(
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
fun stateAfterDeletingReadingMovie(state: AppState, movieId: Long): ReadingDeletion? {
    val movie = state.readingMovies.firstOrNull { it.id == movieId } ?: return null

    return ReadingDeletion(
        state = state.copy(readingMovies = state.readingMovies.filterNot { it.id == movieId }),
        coverToDelete = movie.coverUri,
    )
}

fun stateAfterDeletingReadingSeries(state: AppState, seriesId: Long): ReadingDeletion? {
    val series = state.readingSeries.firstOrNull { it.id == seriesId } ?: return null

    return ReadingDeletion(
        state = state.copy(readingSeries = state.readingSeries.filterNot { it.id == seriesId }),
        coverToDelete = series.coverUri,
    )
}
