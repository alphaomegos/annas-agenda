package com.alphaomegos.annasagenda

enum class ReadingShelf {
    PLANS,
    NOW,
    DONE,
    ABANDONED,
}

enum class ReadingViewMode {
    GRID,
    LIST,
    WALL,
}

enum class ReadingSortField {
    AUTHOR,
    TITLE,
    PAGES,
    YEAR,
    RELEASE_YEAR,
}

enum class ReadingMediaType {
    BOOKS,
    MOVIES,
    SERIES,
}

data class ReadingMediaFilter(
    val showBooks: Boolean = true,
    val showMovies: Boolean = true,
    val showSeries: Boolean = true,
)

data class ReadingSort(
    val field: ReadingSortField = ReadingSortField.TITLE,
    val ascending: Boolean = true,
)

data class ReadingTabPrefs(
    val viewMode: ReadingViewMode = ReadingViewMode.GRID,
    val sort: ReadingSort = ReadingSort(),
)

data class ReadingBook(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val author: String = "",
    val title: String,
    val coverUri: String? = null,

    val totalPages: Int,
    val currentPage: Int = 0,

    // Filled automatically when moved to DONE.
    val yearRead: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

data class ReadingMovie(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val title: String,
    val coverUri: String? = null,

    // Informational fields.
    val releaseYear: Int? = null,
    val translation: String = "",

    // Filled automatically when moved to DONE.
    val yearWatched: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

data class ReadingSeries(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val title: String,
    val coverUri: String? = null,

    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,

    // Filled automatically when moved to DONE.
    val yearWatched: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

/**
 * Reading session attached to a book.
 * Used to compute remaining time estimate based on the latest session speed.
 */
/**
 * A reading session that has started and not yet been written down.
 *
 * Part of AppState, and therefore saved, because it used to live only in the
 * view model: an hour of reading with the screen off was enough for Android to
 * reclaim the process, and the session was simply gone. There is no way to
 * enter one by hand — the finish dialog is only reachable while a session is
 * running — so what was lost could not be put back.
 */
data class ActiveReading(
    val bookId: Long,
    val startedAtEpochMillis: Long,
    val startPage: Int,
)

data class ReadingSession(
    val id: Long,
    val bookId: Long,

    val startedAtEpochMillis: Long,
    val durationMinutes: Int,

    val startPage: Int,
    val endPage: Int,

    val createdAtEpochMillis: Long = startedAtEpochMillis,
)