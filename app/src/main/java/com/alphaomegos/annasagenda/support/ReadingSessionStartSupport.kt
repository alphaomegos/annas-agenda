package com.alphaomegos.annasagenda

/**
 * Starting to read, and what that does to a reading already in progress.
 */
data class ReadingStart(
    val state: AppState,
    /** False only when there is no such book; the caller tells the screen. */
    val started: Boolean,
)

/**
 * Opening a book to read it.
 *
 * Four things can be true when this is called, and each has its own answer.
 *
 * The book is not there: nothing happens and the screen is told so.
 *
 * It is the book already being read: nothing happens and the screen is told
 * yes. Starting a fresh session here would throw away however long had already
 * been counted, which is what used to happen to anyone who left the session
 * screen with the system back gesture and tapped Read again.
 *
 * It is on the Plans shelf: it moves to Now first, because that is what a book
 * being read is.
 *
 * **A different book is being read.** This one had no answer at all until
 * 0107: the session in progress was simply replaced, and an hour of reading
 * went with it, silently. It now ends the way a book leaving the Now shelf
 * ends one — recorded outright if the user has said to stop asking, and
 * otherwise put in front of them as a question. The rule is
 * [readingStopped]'s; this only makes sure the case reaches it.
 *
 * [newSessionId] is a function because the common path ends no session and
 * must not burn an id.
 */
fun stateAfterBeginningReading(
    state: AppState,
    bookId: Long,
    startedAtEpochMillis: Long,
    currentYear: Int,
    newSessionId: () -> Long,
): ReadingStart? {
    val book = state.readingBooks.firstOrNull { it.id == bookId } ?: return null

    if (state.activeReading?.bookId == bookId) {
        return ReadingStart(state, started = true)
    }

    var next = state

    if (book.shelf == ReadingShelf.PLANS) {
        next = stateAfterMovingReadingBookToShelf(
            state = next,
            bookId = bookId,
            shelf = ReadingShelf.NOW,
            currentYear = currentYear,
            nowEpochMillis = startedAtEpochMillis,
            newSessionId = newSessionId,
        ) ?: next
    }

    next = stateWithOtherReadingStopped(next, bookId, startedAtEpochMillis, newSessionId)

    // Read the book again rather than reusing the one found at the top: moving
    // it to Now may have changed it, and the page the session counts from has
    // to be the page the book is actually on.
    val fresh = next.readingBooks.firstOrNull { it.id == bookId } ?: return null

    return ReadingStart(
        state = next.copy(
            activeReading = ActiveReading(
                bookId = bookId,
                startedAtEpochMillis = startedAtEpochMillis,
                startPage = fresh.currentPage.coerceAtLeast(0),
            )
        ),
        started = true,
    )
}

/**
 * Ends a reading of some other book, if one is in progress.
 *
 * A live reading whose book has since disappeared is dropped rather than
 * recorded: there is nothing to record it against, and the session that
 * [stateAfterBeginningReading] is about to start replaces it either way.
 */
private fun stateWithOtherReadingStopped(
    state: AppState,
    startingBookId: Long,
    nowEpochMillis: Long,
    newSessionId: () -> Long,
): AppState {
    val active = state.activeReading ?: return state
    if (active.bookId == startingBookId) return state

    val beingRead = state.readingBooks.firstOrNull { it.id == active.bookId }
        ?: return state.copy(activeReading = null)

    val after = readingStopped(
        active = active,
        book = beingRead,
        pending = state.pendingReadingSession,
        autoRecord = state.autoRecordInterruptedReading,
        nowEpochMillis = nowEpochMillis,
        newSessionId = newSessionId,
    )

    return state.copy(
        activeReading = after.activeReading,
        pendingReadingSession = after.pendingReadingSession,
        readingSessions = state.readingSessions + after.sessionsToRecord,
    )
}
