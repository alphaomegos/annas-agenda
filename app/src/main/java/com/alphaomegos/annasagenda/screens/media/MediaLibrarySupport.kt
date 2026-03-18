package com.alphaomegos.annasagenda.screens.media

import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.ReadingBook
import com.alphaomegos.annasagenda.ReadingMediaFilter
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ReadingMovie
import com.alphaomegos.annasagenda.ReadingSeries
import com.alphaomegos.annasagenda.ReadingShelf
import com.alphaomegos.annasagenda.ReadingSort
import com.alphaomegos.annasagenda.ReadingSortField
import java.util.Locale

internal sealed interface ReadingUiItem {
    val id: Long
    val shelf: ReadingShelf
    val title: String
    val coverUri: String?
    val createdAtEpochMillis: Long
    val stableKey: String
}

internal data class ReadingBookItem(val book: ReadingBook) : ReadingUiItem {
    override val id: Long get() = book.id
    override val shelf: ReadingShelf get() = book.shelf
    override val title: String get() = book.title
    override val coverUri: String? get() = book.coverUri
    override val createdAtEpochMillis: Long get() = book.createdAtEpochMillis
    override val stableKey: String get() = "book-$id"
}

internal data class ReadingMovieItem(val movie: ReadingMovie) : ReadingUiItem {
    override val id: Long get() = movie.id
    override val shelf: ReadingShelf get() = movie.shelf
    override val title: String get() = movie.title
    override val coverUri: String? get() = movie.coverUri
    override val createdAtEpochMillis: Long get() = movie.createdAtEpochMillis
    override val stableKey: String get() = "movie-$id"
}

internal data class ReadingSeriesItem(val series: ReadingSeries) : ReadingUiItem {
    override val id: Long get() = series.id
    override val shelf: ReadingShelf get() = series.shelf
    override val title: String get() = series.title
    override val coverUri: String? get() = series.coverUri
    override val createdAtEpochMillis: Long get() = series.createdAtEpochMillis
    override val stableKey: String get() = "series-$id"
}

internal data class ReadingYearGroup(
    val year: Int?,
    val items: List<ReadingUiItem>,
)

internal fun canReadItem(item: ReadingUiItem): Boolean {
    return item is ReadingBookItem &&
            (item.shelf == ReadingShelf.PLANS || item.shelf == ReadingShelf.NOW)
}

internal fun defaultMediaType(filter: ReadingMediaFilter): ReadingMediaType {
    return when {
        filter.showBooks -> ReadingMediaType.BOOKS
        filter.showMovies -> ReadingMediaType.MOVIES
        filter.showSeries -> ReadingMediaType.SERIES
        else -> ReadingMediaType.BOOKS
    }
}

internal fun buildVisibleReadingItems(
    state: AppState,
    shelf: ReadingShelf,
    sort: ReadingSort,
    query: String,
): List<ReadingUiItem> {
    val allItems = buildList {
        if (state.readingMediaFilter.showBooks) {
            addAll(state.readingBooks.map { ReadingBookItem(it) })
        }
        if (state.readingMediaFilter.showMovies) {
            addAll(state.readingMovies.map { ReadingMovieItem(it) })
        }
        if (state.readingMediaFilter.showSeries) {
            addAll(state.readingSeries.map { ReadingSeriesItem(it) })
        }
    }

    return if (query.isBlank()) {
        sortReadingItems(
            items = allItems.filter { it.shelf == shelf },
            sort = sort,
            shelf = shelf
        )
    } else {
        searchReadingItems(allItems, query)
    }
}

private fun sortReadingItems(
    items: List<ReadingUiItem>,
    sort: ReadingSort,
    shelf: ReadingShelf,
): List<ReadingUiItem> {
    fun s(x: String) = x.trim().lowercase(Locale.getDefault())

    fun authorKey(item: ReadingUiItem): String {
        return when (item) {
            is ReadingBookItem -> s(item.book.author)
            is ReadingMovieItem -> s(item.movie.title)
            is ReadingSeriesItem -> s(item.series.title)
        }
    }

    fun titleKey(item: ReadingUiItem): String = s(item.title)

    fun pagesKey(item: ReadingUiItem): Int {
        return when (item) {
            is ReadingBookItem -> item.book.totalPages
            is ReadingMovieItem -> 0
            is ReadingSeriesItem -> item.series.totalSeasons
        }
    }

    fun yearKey(item: ReadingUiItem): Int {
        return when (shelf) {
            ReadingShelf.DONE -> when (item) {
                is ReadingBookItem -> item.book.yearRead ?: Int.MIN_VALUE
                is ReadingMovieItem -> item.movie.yearWatched ?: Int.MIN_VALUE
                is ReadingSeriesItem -> item.series.yearWatched ?: Int.MIN_VALUE
            }

            ReadingShelf.ABANDONED -> when (item) {
                is ReadingBookItem -> item.book.yearAbandoned ?: Int.MIN_VALUE
                is ReadingMovieItem -> item.movie.yearAbandoned ?: Int.MIN_VALUE
                is ReadingSeriesItem -> item.series.yearAbandoned ?: Int.MIN_VALUE
            }

            ReadingShelf.PLANS,
            ReadingShelf.NOW -> Int.MIN_VALUE
        }
    }

    fun releaseYearKey(item: ReadingUiItem): Int {
        return when (item) {
            is ReadingMovieItem -> item.movie.releaseYear ?: Int.MIN_VALUE
            is ReadingBookItem,
            is ReadingSeriesItem -> Int.MIN_VALUE
        }
    }

    val comparator = when (sort.field) {
        ReadingSortField.AUTHOR ->
            compareBy<ReadingUiItem> { authorKey(it) }
                .thenBy { titleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.TITLE ->
            compareBy<ReadingUiItem> { titleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.PAGES ->
            compareBy<ReadingUiItem> { pagesKey(it) }
                .thenBy { titleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.YEAR ->
            compareBy<ReadingUiItem> { yearKey(it) }
                .thenBy { titleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.RELEASE_YEAR ->
            compareBy<ReadingUiItem> { releaseYearKey(it) }
                .thenBy { titleKey(it) }
                .thenBy { it.createdAtEpochMillis }
    }

    val sorted = items.sortedWith(comparator)
    return if (sort.ascending) sorted else sorted.asReversed()
}
private fun searchReadingItems(
    items: List<ReadingUiItem>,
    query: String,
): List<ReadingUiItem> {
    val q = normalizeSearch(query)
    if (q.isBlank()) return items

    fun shelfOrder(shelf: ReadingShelf): Int {
        return when (shelf) {
            ReadingShelf.PLANS -> 0
            ReadingShelf.NOW -> 1
            ReadingShelf.DONE -> 2
            ReadingShelf.ABANDONED -> 3
        }
    }

    fun mediaTypeOrder(item: ReadingUiItem): Int {
        return when (item) {
            is ReadingBookItem -> 0
            is ReadingMovieItem -> 1
            is ReadingSeriesItem -> 2
        }
    }

    fun matches(item: ReadingUiItem): Boolean {
        val byTitle = normalizeSearch(item.title).contains(q)

        return when (item) {
            is ReadingBookItem -> {
                val byAuthor = normalizeSearch(item.book.author).contains(q)
                val byYearRead = item.book.yearRead?.toString()?.contains(q) == true
                val byYearAbandoned = item.book.yearAbandoned?.toString()?.contains(q) == true
                byTitle || byAuthor || byYearRead || byYearAbandoned
            }

            is ReadingMovieItem -> {
                val byYearWatched = item.movie.yearWatched?.toString()?.contains(q) == true
                val byYearAbandoned = item.movie.yearAbandoned?.toString()?.contains(q) == true
                byTitle || byYearWatched || byYearAbandoned
            }

            is ReadingSeriesItem -> {
                val byYearWatched = item.series.yearWatched?.toString()?.contains(q) == true
                val byYearAbandoned = item.series.yearAbandoned?.toString()?.contains(q) == true
                byTitle || byYearWatched || byYearAbandoned
            }
        }
    }

    return items
        .filter(::matches)
        .sortedWith(
            compareBy<ReadingUiItem> { shelfOrder(it.shelf) }
                .thenBy { mediaTypeOrder(it) }
                .thenBy { normalizeSearch(it.title) }
                .thenBy { it.createdAtEpochMillis }
        )
}

private fun normalizeSearch(value: String): String {
    return value.trim().lowercase(Locale.getDefault())
}

internal fun buildReadingYearGroups(
    items: List<ReadingUiItem>,
    shelf: ReadingShelf,
): List<ReadingYearGroup> {
    if (items.isEmpty()) return emptyList()

    fun yearForItem(item: ReadingUiItem): Int? {
        return when (shelf) {
            ReadingShelf.DONE -> when (item) {
                is ReadingBookItem -> item.book.yearRead
                is ReadingMovieItem -> item.movie.yearWatched
                is ReadingSeriesItem -> item.series.yearWatched
            }

            ReadingShelf.ABANDONED -> when (item) {
                is ReadingBookItem -> item.book.yearAbandoned
                is ReadingMovieItem -> item.movie.yearAbandoned
                is ReadingSeriesItem -> item.series.yearAbandoned
            }

            ReadingShelf.PLANS,
            ReadingShelf.NOW -> null
        }
    }

    val result = mutableListOf<ReadingYearGroup>()

    var currentYear: Int? = null
    var currentItems = mutableListOf<ReadingUiItem>()

    for (item in items) {
        val itemYear = yearForItem(item)

        if (currentItems.isEmpty()) {
            currentYear = itemYear
            currentItems.add(item)
            continue
        }

        if (itemYear == currentYear) {
            currentItems.add(item)
        } else {
            result.add(
                ReadingYearGroup(
                    year = currentYear,
                    items = currentItems.toList()
                )
            )
            currentYear = itemYear
            currentItems = mutableListOf(item)
        }
    }

    if (currentItems.isNotEmpty()) {
        result.add(
            ReadingYearGroup(
                year = currentYear,
                items = currentItems.toList()
            )
        )
    }

    return result
}