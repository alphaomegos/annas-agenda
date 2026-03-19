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
    val allItems = buildAllVisibleReadingItems(
        books = state.readingBooks,
        movies = state.readingMovies,
        series = state.readingSeries,
        filter = state.readingMediaFilter
    )

    return if (query.isBlank()) {
        sortReadingItems(
            items = allItems.filter { it.shelf == shelf },
            sort = sort,
            shelf = shelf
        )
    } else {
        searchReadingItems(
            items = allItems,
            query = query
        )
    }
}

private fun buildAllVisibleReadingItems(
    books: List<ReadingBook>,
    movies: List<ReadingMovie>,
    series: List<ReadingSeries>,
    filter: ReadingMediaFilter,
): List<ReadingUiItem> {
    return buildList {
        if (filter.showBooks) {
            addAll(books.map(::ReadingBookItem))
        }
        if (filter.showMovies) {
            addAll(movies.map(::ReadingMovieItem))
        }
        if (filter.showSeries) {
            addAll(series.map(::ReadingSeriesItem))
        }
    }
}

private fun sortReadingItems(
    items: List<ReadingUiItem>,
    sort: ReadingSort,
    shelf: ReadingShelf,
): List<ReadingUiItem> {
    val comparator = when (sort.field) {
        ReadingSortField.AUTHOR ->
            compareBy<ReadingUiItem> { itemAuthorKey(it) }
                .thenBy { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.TITLE ->
            compareBy<ReadingUiItem> { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.PAGES ->
            compareBy<ReadingUiItem> { itemPagesKey(it) }
                .thenBy { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.YEAR ->
            compareBy<ReadingUiItem> { readingItemYearForShelf(it, shelf) ?: Int.MIN_VALUE }
                .thenBy { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }

        ReadingSortField.RELEASE_YEAR ->
            compareBy<ReadingUiItem> { itemReleaseYearKey(it) }
                .thenBy { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }
    }

    val sorted = items.sortedWith(comparator)
    return if (sort.ascending) sorted else sorted.asReversed()
}

private fun searchReadingItems(
    items: List<ReadingUiItem>,
    query: String,
): List<ReadingUiItem> {
    val normalizedQuery = normalizeSearch(query)
    if (normalizedQuery.isBlank()) return items

    fun matches(item: ReadingUiItem): Boolean {
        val byTitle = normalizeSearch(item.title).contains(normalizedQuery)

        return when (item) {
            is ReadingBookItem -> {
                val byAuthor = normalizeSearch(item.book.author).contains(normalizedQuery)
                val byYearRead = item.book.yearRead?.toString()?.contains(normalizedQuery) == true
                val byYearAbandoned = item.book.yearAbandoned?.toString()?.contains(normalizedQuery) == true
                byTitle || byAuthor || byYearRead || byYearAbandoned
            }

            is ReadingMovieItem -> {
                val byYearWatched = item.movie.yearWatched?.toString()?.contains(normalizedQuery) == true
                val byYearAbandoned = item.movie.yearAbandoned?.toString()?.contains(normalizedQuery) == true
                byTitle || byYearWatched || byYearAbandoned
            }

            is ReadingSeriesItem -> {
                val byYearWatched = item.series.yearWatched?.toString()?.contains(normalizedQuery) == true
                val byYearAbandoned = item.series.yearAbandoned?.toString()?.contains(normalizedQuery) == true
                byTitle || byYearWatched || byYearAbandoned
            }
        }
    }

    return items
        .filter(::matches)
        .sortedWith(
            compareBy<ReadingUiItem> { shelfOrder(it.shelf) }
                .thenBy { mediaTypeOrder(it) }
                .thenBy { itemTitleKey(it) }
                .thenBy { it.createdAtEpochMillis }
        )
}

private fun normalizeSearch(value: String): String {
    return value.trim().lowercase(Locale.getDefault())
}

private fun itemAuthorKey(item: ReadingUiItem): String {
    return when (item) {
        is ReadingBookItem -> normalizeSearch(item.book.author)
        is ReadingMovieItem -> normalizeSearch(item.movie.title)
        is ReadingSeriesItem -> normalizeSearch(item.series.title)
    }
}

private fun itemTitleKey(item: ReadingUiItem): String {
    return normalizeSearch(item.title)
}

private fun itemPagesKey(item: ReadingUiItem): Int {
    return when (item) {
        is ReadingBookItem -> item.book.totalPages
        is ReadingMovieItem -> 0
        is ReadingSeriesItem -> item.series.totalSeasons
    }
}

private fun itemReleaseYearKey(item: ReadingUiItem): Int {
    return when (item) {
        is ReadingMovieItem -> item.movie.releaseYear ?: Int.MIN_VALUE
        is ReadingBookItem,
        is ReadingSeriesItem -> Int.MIN_VALUE
    }
}

private fun readingItemYearForShelf(
    item: ReadingUiItem,
    shelf: ReadingShelf,
): Int? {
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

private fun mediaTypeOrder(item: ReadingUiItem): Int {
    return when (item) {
        is ReadingBookItem -> 0
        is ReadingMovieItem -> 1
        is ReadingSeriesItem -> 2
    }
}

private fun shelfOrder(shelf: ReadingShelf): Int {
    return when (shelf) {
        ReadingShelf.PLANS -> 0
        ReadingShelf.NOW -> 1
        ReadingShelf.DONE -> 2
        ReadingShelf.ABANDONED -> 3
    }
}

internal fun buildReadingYearGroups(
    items: List<ReadingUiItem>,
    shelf: ReadingShelf,
): List<ReadingYearGroup> {
    if (items.isEmpty()) return emptyList()

    val result = mutableListOf<ReadingYearGroup>()
    var currentYear: Int? = null
    var currentItems = mutableListOf<ReadingUiItem>()

    fun flushGroup() {
        if (currentItems.isEmpty()) return

        result.add(
            ReadingYearGroup(
                year = currentYear,
                items = currentItems.toList()
            )
        )
        currentItems = mutableListOf()
    }

    for (item in items) {
        val itemYear = readingItemYearForShelf(item, shelf)

        if (currentItems.isEmpty()) {
            currentYear = itemYear
            currentItems.add(item)
            continue
        }

        if (itemYear == currentYear) {
            currentItems.add(item)
        } else {
            flushGroup()
            currentYear = itemYear
            currentItems.add(item)
        }
    }

    flushGroup()
    return result
}