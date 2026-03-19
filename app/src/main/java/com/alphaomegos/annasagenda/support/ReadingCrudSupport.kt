package com.alphaomegos.annasagenda

fun buildReadingBook(
    id: Long,
    shelf: ReadingShelf,
    title: String,
    totalPages: Int,
    author: String = "",
    coverUri: String? = null,
    createdAtEpochMillis: Long,
    currentYear: Int,
): ReadingBook? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null
    if (totalPages <= 0) return null

    return ReadingBook(
        id = id,
        shelf = shelf,
        author = author.trim(),
        title = cleanTitle,
        coverUri = coverUri,
        totalPages = totalPages,
        currentPage = 0,
        yearRead = if (shelf == ReadingShelf.DONE) currentYear else null,
        yearAbandoned = if (shelf == ReadingShelf.ABANDONED) currentYear else null,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingBookToShelf(
    book: ReadingBook,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingBook {
    return when (shelf) {
        ReadingShelf.DONE -> book.copy(
            shelf = shelf,
            yearRead = currentYear,
            yearAbandoned = null,
        )

        ReadingShelf.ABANDONED -> book.copy(
            shelf = shelf,
            yearRead = null,
            yearAbandoned = currentYear,
        )

        ReadingShelf.PLANS,
        ReadingShelf.NOW -> book.copy(
            shelf = shelf,
            yearRead = null,
            yearAbandoned = null,
        )
    }
}

fun updateReadingBookEntity(
    old: ReadingBook,
    author: String? = null,
    title: String? = null,
    coverUri: String? = null,
    clearCover: Boolean = false,
    totalPages: Int? = null,
    currentPage: Int? = null,
    yearRead: Int? = null,
    yearAbandoned: Int? = null,
    shelf: ReadingShelf? = null,
    currentYear: Int,
): ReadingBook {
    val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: old.title
    val newAuthor = author?.trim() ?: old.author

    val pages = (totalPages ?: old.totalPages).coerceAtLeast(1)
    val newCurrent = (currentPage ?: old.currentPage).coerceIn(0, pages)

    val newShelf = shelf ?: old.shelf

    val newYearRead = when (newShelf) {
        ReadingShelf.DONE -> yearRead ?: old.yearRead ?: currentYear
        else -> null
    }

    val newYearAbandoned = when (newShelf) {
        ReadingShelf.ABANDONED -> yearAbandoned ?: old.yearAbandoned ?: currentYear
        else -> null
    }

    val newCover = when {
        clearCover -> null
        coverUri != null -> coverUri
        else -> old.coverUri
    }

    return old.copy(
        shelf = newShelf,
        author = newAuthor,
        title = newTitle,
        coverUri = newCover,
        totalPages = pages,
        currentPage = newCurrent,
        yearRead = newYearRead,
        yearAbandoned = newYearAbandoned,
    )
}

fun buildReadingMovie(
    id: Long,
    shelf: ReadingShelf,
    title: String,
    releaseYear: Int? = null,
    translation: String = "",
    coverUri: String? = null,
    createdAtEpochMillis: Long,
    currentYear: Int,
): ReadingMovie? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null

    val cleanReleaseYear = releaseYear?.takeIf { it in 1..9999 }
    val cleanTranslation = translation.trim()

    return ReadingMovie(
        id = id,
        shelf = shelf,
        title = cleanTitle,
        coverUri = coverUri,
        releaseYear = cleanReleaseYear,
        translation = cleanTranslation,
        yearWatched = if (shelf == ReadingShelf.DONE) currentYear else null,
        yearAbandoned = if (shelf == ReadingShelf.ABANDONED) currentYear else null,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingMovieToShelf(
    movie: ReadingMovie,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingMovie {
    return when (shelf) {
        ReadingShelf.DONE -> movie.copy(
            shelf = shelf,
            yearWatched = currentYear,
            yearAbandoned = null,
        )

        ReadingShelf.ABANDONED -> movie.copy(
            shelf = shelf,
            yearWatched = null,
            yearAbandoned = currentYear,
        )

        ReadingShelf.PLANS,
        ReadingShelf.NOW -> movie.copy(
            shelf = shelf,
            yearWatched = null,
            yearAbandoned = null,
        )
    }
}

fun updateReadingMovieEntity(
    old: ReadingMovie,
    title: String? = null,
    coverUri: String? = null,
    clearCover: Boolean = false,
    releaseYear: Int? = null,
    clearReleaseYear: Boolean = false,
    translation: String? = null,
    yearWatched: Int? = null,
    yearAbandoned: Int? = null,
    shelf: ReadingShelf? = null,
    currentYear: Int,
): ReadingMovie {
    val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: old.title
    val newShelf = shelf ?: old.shelf

    val newYearWatched = when (newShelf) {
        ReadingShelf.DONE -> yearWatched ?: old.yearWatched ?: currentYear
        else -> null
    }

    val newYearAbandoned = when (newShelf) {
        ReadingShelf.ABANDONED -> yearAbandoned ?: old.yearAbandoned ?: currentYear
        else -> null
    }

    val newCover = when {
        clearCover -> null
        coverUri != null -> coverUri
        else -> old.coverUri
    }

    val newReleaseYear = when {
        clearReleaseYear -> null
        releaseYear != null -> releaseYear.takeIf { it in 1..9999 }
        else -> old.releaseYear
    }

    val newTranslation = translation?.trim() ?: old.translation

    return old.copy(
        shelf = newShelf,
        title = newTitle,
        coverUri = newCover,
        releaseYear = newReleaseYear,
        translation = newTranslation,
        yearWatched = newYearWatched,
        yearAbandoned = newYearAbandoned,
    )
}

fun buildReadingSeries(
    id: Long,
    shelf: ReadingShelf,
    title: String,
    totalSeasons: Int = 1,
    currentSeason: Int = 1,
    currentEpisode: Int = 1,
    coverUri: String? = null,
    createdAtEpochMillis: Long,
    currentYear: Int,
): ReadingSeries? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null

    val safeTotalSeasons = totalSeasons.coerceAtLeast(1)
    val safeCurrentSeason = currentSeason.coerceIn(1, safeTotalSeasons)
    val safeCurrentEpisode = currentEpisode.coerceAtLeast(1)

    return ReadingSeries(
        id = id,
        shelf = shelf,
        title = cleanTitle,
        coverUri = coverUri,
        totalSeasons = safeTotalSeasons,
        currentSeason = safeCurrentSeason,
        currentEpisode = safeCurrentEpisode,
        yearWatched = if (shelf == ReadingShelf.DONE) currentYear else null,
        yearAbandoned = if (shelf == ReadingShelf.ABANDONED) currentYear else null,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingSeriesToShelf(
    series: ReadingSeries,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingSeries {
    return when (shelf) {
        ReadingShelf.DONE -> series.copy(
            shelf = shelf,
            yearWatched = currentYear,
            yearAbandoned = null,
        )

        ReadingShelf.ABANDONED -> series.copy(
            shelf = shelf,
            yearWatched = null,
            yearAbandoned = currentYear,
        )

        ReadingShelf.PLANS,
        ReadingShelf.NOW -> series.copy(
            shelf = shelf,
            yearWatched = null,
            yearAbandoned = null,
        )
    }
}

fun updateReadingSeriesEntity(
    old: ReadingSeries,
    title: String? = null,
    coverUri: String? = null,
    clearCover: Boolean = false,
    totalSeasons: Int? = null,
    currentSeason: Int? = null,
    currentEpisode: Int? = null,
    yearWatched: Int? = null,
    yearAbandoned: Int? = null,
    shelf: ReadingShelf? = null,
    currentYear: Int,
): ReadingSeries {
    val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: old.title
    val newShelf = shelf ?: old.shelf

    val safeTotalSeasons = (totalSeasons ?: old.totalSeasons).coerceAtLeast(1)
    val safeCurrentSeason = (currentSeason ?: old.currentSeason).coerceIn(1, safeTotalSeasons)
    val safeCurrentEpisode = (currentEpisode ?: old.currentEpisode).coerceAtLeast(1)

    val newYearWatched = when (newShelf) {
        ReadingShelf.DONE -> yearWatched ?: old.yearWatched ?: currentYear
        else -> null
    }

    val newYearAbandoned = when (newShelf) {
        ReadingShelf.ABANDONED -> yearAbandoned ?: old.yearAbandoned ?: currentYear
        else -> null
    }

    val newCover = when {
        clearCover -> null
        coverUri != null -> coverUri
        else -> old.coverUri
    }

    return old.copy(
        shelf = newShelf,
        title = newTitle,
        coverUri = newCover,
        totalSeasons = safeTotalSeasons,
        currentSeason = safeCurrentSeason,
        currentEpisode = safeCurrentEpisode,
        yearWatched = newYearWatched,
        yearAbandoned = newYearAbandoned,
    )
}