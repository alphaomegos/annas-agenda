package com.alphaomegos.annasagenda

/**
 * The word that identifies a kind of media in a cover file name.
 *
 * It ends up inside the ref — `internal://media_covers/book_17_9f3a1c02.jpg` —
 * and it was written out as a literal in six places, three for importing a
 * cover and three for migrating an old one. A typo in any of them would have
 * produced a file that nothing ever finds again.
 *
 * These three words are effectively on disk in every user's cover folder.
 * Changing one does not break the app — nothing parses a ref back into a kind
 * and an id — but it does mean covers imported before and after the change
 * live under different names, so there is no reason to change them.
 */
fun coverMediaKind(type: ReadingMediaType): String = when (type) {
    ReadingMediaType.BOOKS -> "book"
    ReadingMediaType.MOVIES -> "movie"
    ReadingMediaType.SERIES -> "series"
}

/**
 * Whether an item of this kind is still in the library.
 *
 * Asked twice around a cover import, before and after, because importing takes
 * long enough for the user to delete the item while it runs and a cover
 * imported for something that no longer exists is a file nothing will ever
 * mention again.
 */
fun readingMediaExists(state: AppState, type: ReadingMediaType, itemId: Long): Boolean =
    when (type) {
        ReadingMediaType.BOOKS -> state.readingBooks.any { it.id == itemId }
        ReadingMediaType.MOVIES -> state.readingMovies.any { it.id == itemId }
        ReadingMediaType.SERIES -> state.readingSeries.any { it.id == itemId }
    }

/**
 * Walks every cover ref in the library past [migrate], and puts back whatever
 * comes out.
 *
 * What [migrate] does is the view model's business — it copies a picture that
 * still lives in someone else's app into this one's storage, which needs a
 * Context and a real file. What is here is the walk, which is where the
 * mistakes are: three lists, and every item has to be offered with **its own
 * kind and its own id**. Hand a book to the walk as a film and the file is
 * written as `movie_17_…`, which is a perfectly good file that nothing will
 * ever look for again. That pairing was written out three times by hand and
 * now has a test.
 *
 * An item whose ref comes back unchanged is left as the object it already was.
 * The caller compares the result with what it passed in to decide whether
 * anything needs saving, so this is not load-bearing — but nothing is gained
 * by rebuilding the whole library to store the same strings in it.
 */
suspend fun stateWithCoverRefsMigrated(
    state: AppState,
    migrate: suspend (coverRef: String?, type: ReadingMediaType, itemId: Long) -> String?,
): AppState {
    val books = state.readingBooks.map { book ->
        val migrated = migrate(book.coverUri, ReadingMediaType.BOOKS, book.id)
        if (migrated == book.coverUri) book else book.copy(coverUri = migrated)
    }

    val movies = state.readingMovies.map { movie ->
        val migrated = migrate(movie.coverUri, ReadingMediaType.MOVIES, movie.id)
        if (migrated == movie.coverUri) movie else movie.copy(coverUri = migrated)
    }

    val series = state.readingSeries.map { one ->
        val migrated = migrate(one.coverUri, ReadingMediaType.SERIES, one.id)
        if (migrated == one.coverUri) one else one.copy(coverUri = migrated)
    }

    val changed = books != state.readingBooks ||
        movies != state.readingMovies ||
        series != state.readingSeries

    return if (!changed) {
        state
    } else {
        state.copy(readingBooks = books, readingMovies = movies, readingSeries = series)
    }
}
