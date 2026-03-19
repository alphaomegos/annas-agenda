package com.alphaomegos.annasagenda

fun readingTabPrefsForShelf(state: AppState, shelf: ReadingShelf): ReadingTabPrefs {
    return when (shelf) {
        ReadingShelf.PLANS -> state.readingPlansPrefs
        ReadingShelf.NOW -> state.readingNowPrefs
        ReadingShelf.DONE -> state.readingDonePrefs
        ReadingShelf.ABANDONED -> state.readingAbandonedPrefs
    }
}

fun readingStateWithTabPrefs(
    state: AppState,
    shelf: ReadingShelf,
    prefs: ReadingTabPrefs,
): AppState {
    return when (shelf) {
        ReadingShelf.PLANS -> state.copy(readingPlansPrefs = prefs)
        ReadingShelf.NOW -> state.copy(readingNowPrefs = prefs)
        ReadingShelf.DONE -> state.copy(readingDonePrefs = prefs)
        ReadingShelf.ABANDONED -> state.copy(readingAbandonedPrefs = prefs)
    }
}

fun estimateRemainingReadingMinutes(
    book: ReadingBook,
    sessions: List<ReadingSession>,
): Int? {
    val remainingPages = (book.totalPages - book.currentPage).coerceAtLeast(0)
    if (remainingPages == 0) return 0

    val last = sessions
        .asSequence()
        .filter { it.bookId == book.id }
        .maxByOrNull { it.createdAtEpochMillis }
        ?: return null

    val pagesRead = (last.endPage - last.startPage).coerceAtLeast(0)
    val durationMinutes = last.durationMinutes.coerceAtLeast(1)

    if (pagesRead <= 0) return null

    val numerator = remainingPages.toLong() * durationMinutes.toLong()
    val denominator = pagesRead.toLong()
    val minutes = ((numerator + denominator - 1) / denominator).toInt()

    return minutes.coerceAtLeast(0)
}

fun estimateRemainingReadingHours(
    book: ReadingBook,
    sessions: List<ReadingSession>,
): Int? {
    val minutes = estimateRemainingReadingMinutes(book, sessions) ?: return null
    return ((minutes + 59) / 60).coerceAtLeast(0)
}