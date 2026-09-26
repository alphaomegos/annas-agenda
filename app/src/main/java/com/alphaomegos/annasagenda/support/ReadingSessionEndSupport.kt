package com.alphaomegos.annasagenda

/**
 * The four ways a reading stops, and what each one keeps.
 *
 * Three of them are answers the user gives out loud — finish, keep, discard —
 * and the fourth, cancel, is the back gesture. They were four separate blocks
 * inside the view model, and the thing none of them said anywhere a test could
 * read it is what they have in common: **each one decides the fate of exactly
 * one piece of time, and must not touch the other.** A session in progress and
 * a question waiting to be answered are different things about possibly
 * different books, and an answer about one is not an answer about the other.
 *
 * That is the rule these functions exist to state. 0107 is what it looks like
 * when a path forgets it.
 */

/**
 * The user filled in the session form and pressed done.
 *
 * Null when there is nothing to finish — no reading in progress, or a reading
 * pointing at a book that is no longer in the library.
 *
 * Everything the form hands over is treated as a claim to be checked rather
 * than a fact. Pages are pulled inside the book's covers, because a typo in a
 * page field is a typo and not a reason to store page 7000 of a 700-page book.
 * A duration of zero becomes one minute: the user read, the reading happened,
 * and a zero-minute session would later divide into the pace estimate.
 *
 * The book's own page moves to where the user says they stopped — forwards or
 * backwards, because re-reading a chapter is a thing people do.
 *
 * [pendingReadingSession] is deliberately left exactly as it is. If a question
 * about some earlier interrupted reading is still waiting, answering this form
 * is not an answer to it.
 */
fun stateAfterFinishingReading(
    state: AppState,
    startPage: Int,
    endPage: Int,
    durationMinutes: Int,
    finishedAtEpochMillis: Long,
    newSessionId: () -> Long,
): AppState? {
    val active = state.activeReading ?: return null
    val book = state.readingBooks.firstOrNull { it.id == active.bookId } ?: return null

    val lastPage = book.totalPages.coerceAtLeast(1)
    val start = startPage.coerceIn(0, lastPage)
    val end = endPage.coerceIn(0, lastPage)

    val session = ReadingSession(
        id = newSessionId(),
        bookId = book.id,
        startedAtEpochMillis = active.startedAtEpochMillis,
        durationMinutes = durationMinutes.coerceAtLeast(1),
        startPage = start,
        endPage = end,
        createdAtEpochMillis = finishedAtEpochMillis,
    )

    return state.copy(
        readingBooks = state.readingBooks.map { b ->
            if (b.id == book.id) b.copy(currentPage = end.coerceIn(0, b.totalPages)) else b
        },
        readingSessions = state.readingSessions + session,
        activeReading = null,
    )
}

/**
 * "Yes, count it" — the answer to a question about an interrupted reading.
 *
 * The session goes into the history exactly as it was shown, id and all: the id
 * was handed out when the question was raised, so what is stored is what the
 * user looked at and agreed to.
 *
 * [alwaysFromNowOn] only ever turns the flag on. An answer cannot un-say an
 * earlier "stop asking me", because this dialog is no longer shown once that
 * has been said — there would be no way back.
 */
fun stateAfterKeepingPendingReadingSession(
    state: AppState,
    alwaysFromNowOn: Boolean,
): AppState {
    val pending = state.pendingReadingSession ?: return state

    return state.copy(
        readingSessions = state.readingSessions + pending,
        pendingReadingSession = null,
        autoRecordInterruptedReading = state.autoRecordInterruptedReading || alwaysFromNowOn,
    )
}

/**
 * "No, throw it away."
 *
 * There is no "always" on this one, on purpose: an answer that destroys data is
 * not one to start giving on the user's behalf.
 */
fun stateAfterDiscardingPendingReadingSession(state: AppState): AppState =
    state.copy(pendingReadingSession = null)

/**
 * The session screen left without an answer — the back gesture, or Cancel.
 *
 * The reading in progress goes and nothing is recorded, which is the whole
 * point: the user walked away from the form rather than filling it in. This is
 * the only stop that asks nothing, and it is why [stateAfterBeginningReading]
 * cannot simply reuse it — abandoning a form you are looking at is a choice,
 * and having your session replaced by a book you opened somewhere else is not.
 *
 * A question already waiting about some other reading stays waiting.
 */
fun stateAfterCancellingReading(state: AppState): AppState =
    state.copy(activeReading = null)
