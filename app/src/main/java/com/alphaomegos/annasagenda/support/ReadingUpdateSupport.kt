package com.alphaomegos.annasagenda

/**
 * Editing an item in the media library.
 *
 * The field-by-field part is [updateReadingBookEntity] and its two siblings,
 * which have had tests since 0041. What was left in the view model, three
 * times over, was the part around it: find the item, put the new one back in
 * the list, and — the bit worth writing down — notice that the cover it used
 * to point at is now rubbish.
 *
 * That last one is a lifetime rule, not a formatting detail. A cover whose ref
 * changed has no owner any more, and the file has to go; a cover whose ref did
 * not change must not be touched, because the item is still using it. Getting
 * that backwards is 0014, where the file was destroyed while the state still
 * named it and the old picture stayed on screen until the next launch.
 *
 * The book's version also carries the session rule, because editing a book can
 * move it off the Now shelf and end a reading in progress. See
 * stateWithReadingBookChanged.
 */
fun stateAfterUpdatingReadingBook(
    state: AppState,
    bookId: Long,
    nowEpochMillis: Long,
    newSessionId: () -> Long,
    update: (ReadingBook) -> ReadingBook,
): ReadingChange? {
    val old = state.readingBooks.firstOrNull { it.id == bookId } ?: return null
    val updated = update(old)

    return ReadingChange(
        state = stateWithReadingBookChanged(
            state = state,
            changed = updated,
            books = state.readingBooks.map { b -> if (b.id == bookId) updated else b },
            nowEpochMillis = nowEpochMillis,
            newSessionId = newSessionId,
        ),
        coverToDelete = old.coverUri.takeIf { it != updated.coverUri },
    )
}

fun stateAfterUpdatingReadingMovie(
    state: AppState,
    movieId: Long,
    update: (ReadingMovie) -> ReadingMovie,
): ReadingChange? {
    val old = state.readingMovies.firstOrNull { it.id == movieId } ?: return null
    val updated = update(old)

    return ReadingChange(
        state = state.copy(
            readingMovies = state.readingMovies.map { m -> if (m.id == movieId) updated else m }
        ),
        coverToDelete = old.coverUri.takeIf { it != updated.coverUri },
    )
}

fun stateAfterUpdatingReadingSeries(
    state: AppState,
    seriesId: Long,
    update: (ReadingSeries) -> ReadingSeries,
): ReadingChange? {
    val old = state.readingSeries.firstOrNull { it.id == seriesId } ?: return null
    val updated = update(old)

    return ReadingChange(
        state = state.copy(
            readingSeries = state.readingSeries.map { s -> if (s.id == seriesId) updated else s }
        ),
        coverToDelete = old.coverUri.takeIf { it != updated.coverUri },
    )
}
