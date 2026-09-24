package com.alphaomegos.annasagenda

/**
 * Creating, moving and editing the three kinds of media.
 *
 * The three kinds stay three types with three lists: they hold genuinely
 * different things — pages, a release year, seasons and episodes — and merging
 * them would cost a migration nobody can take back. What was worth merging is
 * the part that is the same in all three, and that lives in ReadingShelfRules:
 * which years a shelf allows, what an edit does to a title, what happens to a
 * cover. Each function below is now the fields it owns, plus those rules.
 */

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

    val years = yearsForShelf(shelf, currentYear)

    return ReadingBook(
        id = id,
        shelf = shelf,
        author = author.trim(),
        title = cleanTitle,
        coverUri = coverUri,
        totalPages = totalPages,
        currentPage = 0,
        yearRead = years.finished,
        yearAbandoned = years.abandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingBookToShelf(
    book: ReadingBook,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingBook {
    val years = yearsForShelf(shelf, currentYear)

    return book.copy(
        shelf = shelf,
        yearRead = years.finished,
        yearAbandoned = years.abandoned,
    )
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
    val newShelf = shelf ?: old.shelf

    val years = resolvedShelfYears(
        shelf = newShelf,
        requested = ShelfYears(finished = yearRead, abandoned = yearAbandoned),
        existing = ShelfYears(finished = old.yearRead, abandoned = old.yearAbandoned),
        currentYear = currentYear,
    )

    // A book is at least one page long, and the bookmark cannot be past the
    // end — shortening a book pulls it back.
    val pages = (totalPages ?: old.totalPages).coerceAtLeast(1)
    val newCurrent = (currentPage ?: old.currentPage).coerceIn(0, pages)

    return old.copy(
        shelf = newShelf,
        author = author?.trim() ?: old.author,
        title = titleAfterEdit(title, old.title),
        coverUri = coverAfterEdit(coverUri, old.coverUri, clearCover),
        totalPages = pages,
        currentPage = newCurrent,
        yearRead = years.finished,
        yearAbandoned = years.abandoned,
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

    val years = yearsForShelf(shelf, currentYear)

    return ReadingMovie(
        id = id,
        shelf = shelf,
        title = cleanTitle,
        coverUri = coverUri,
        releaseYear = releaseYear?.takeIf(::isPossibleReleaseYear),
        translation = translation.trim(),
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingMovieToShelf(
    movie: ReadingMovie,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingMovie {
    val years = yearsForShelf(shelf, currentYear)

    return movie.copy(
        shelf = shelf,
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
    )
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
    val newShelf = shelf ?: old.shelf

    val years = resolvedShelfYears(
        shelf = newShelf,
        requested = ShelfYears(finished = yearWatched, abandoned = yearAbandoned),
        existing = ShelfYears(finished = old.yearWatched, abandoned = old.yearAbandoned),
        currentYear = currentYear,
    )

    val newReleaseYear = when {
        clearReleaseYear -> null
        // A year that cannot be a year is bad input, and bad input must not
        // erase what is already stored. Emptying the field is how the year is
        // removed, and that arrives as clearReleaseYear.
        releaseYear != null -> releaseYear.takeIf(::isPossibleReleaseYear) ?: old.releaseYear
        else -> old.releaseYear
    }

    return old.copy(
        shelf = newShelf,
        title = titleAfterEdit(title, old.title),
        coverUri = coverAfterEdit(coverUri, old.coverUri, clearCover),
        releaseYear = newReleaseYear,
        translation = translation?.trim() ?: old.translation,
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
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

    val years = yearsForShelf(shelf, currentYear)

    val safeTotalSeasons = totalSeasons.coerceAtLeast(1)

    return ReadingSeries(
        id = id,
        shelf = shelf,
        title = cleanTitle,
        coverUri = coverUri,
        totalSeasons = safeTotalSeasons,
        currentSeason = currentSeason.coerceIn(1, safeTotalSeasons),
        currentEpisode = currentEpisode.coerceAtLeast(1),
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun moveReadingSeriesToShelf(
    series: ReadingSeries,
    shelf: ReadingShelf,
    currentYear: Int,
): ReadingSeries {
    val years = yearsForShelf(shelf, currentYear)

    return series.copy(
        shelf = shelf,
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
    )
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
    val newShelf = shelf ?: old.shelf

    val years = resolvedShelfYears(
        shelf = newShelf,
        requested = ShelfYears(finished = yearWatched, abandoned = yearAbandoned),
        existing = ShelfYears(finished = old.yearWatched, abandoned = old.yearAbandoned),
        currentYear = currentYear,
    )

    // The season watched cannot be past the last season there is — shortening
    // a show pulls it back, exactly as shortening a book pulls the bookmark.
    val safeTotalSeasons = (totalSeasons ?: old.totalSeasons).coerceAtLeast(1)

    return old.copy(
        shelf = newShelf,
        title = titleAfterEdit(title, old.title),
        coverUri = coverAfterEdit(coverUri, old.coverUri, clearCover),
        totalSeasons = safeTotalSeasons,
        currentSeason = (currentSeason ?: old.currentSeason).coerceIn(1, safeTotalSeasons),
        currentEpisode = (currentEpisode ?: old.currentEpisode).coerceAtLeast(1),
        yearWatched = years.finished,
        yearAbandoned = years.abandoned,
    )
}

/**
 * Whether a number could be a release year at all.
 *
 * The same bound was written out four times as `it in 1..9999`, twice here and
 * twice in the screen that validates the field before saving.
 */
fun isPossibleReleaseYear(year: Int): Boolean = year in 1..9999
