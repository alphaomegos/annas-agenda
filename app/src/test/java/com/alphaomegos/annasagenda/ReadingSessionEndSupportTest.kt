package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four ways a reading stops.
 *
 * Most of what is asked here is one question in four shapes: does this answer
 * touch anything it was not an answer about? A session in progress and a
 * question waiting about an earlier one are separate pieces of time, and 0107
 * is what it costs when a path forgets that.
 */
class ReadingSessionEndSupportTest {

    private val startedAt = 1_000_000L
    private val finishedAt = startedAt + 60 * 60_000L

    private val book = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = 700,
        currentPage = 120,
        shelf = ReadingShelf.NOW,
    )

    private val reading = AppState(
        readingBooks = listOf(book),
        activeReading = ActiveReading(bookId = 1L, startedAtEpochMillis = startedAt, startPage = 120),
    )

    private val olderQuestion = ReadingSession(
        id = 50L,
        bookId = 9L,
        startedAtEpochMillis = 1L,
        durationMinutes = 10,
        startPage = 1,
        endPage = 20,
    )

    private fun finish(
        from: AppState = reading,
        startPage: Int = 120,
        endPage: Int = 180,
        durationMinutes: Int = 55,
        sessionId: Long = 77L,
    ) = stateAfterFinishingReading(
        state = from,
        startPage = startPage,
        endPage = endPage,
        durationMinutes = durationMinutes,
        finishedAtEpochMillis = finishedAt,
        newSessionId = { sessionId },
    )

    /* ---------------- finishing ---------------- */

    @Test
    fun thereIsNothingToFinishWhenNothingIsBeingRead() {
        assertNull(finish(from = AppState(readingBooks = listOf(book))))
    }

    /**
     * A reading of a book that has since been deleted has nothing to be
     * recorded against.
     */
    @Test
    fun aReadingOfAVanishedBookCannotBeFinished() {
        val dangling = reading.copy(readingBooks = emptyList())

        assertNull(finish(from = dangling))
    }

    @Test
    fun theFinishedSessionIsRecordedAndTheReadingEnds() {
        val after = finish()!!

        assertNull(after.activeReading)

        val session = after.readingSessions.single()
        assertEquals(77L, session.id)
        assertEquals(1L, session.bookId)
        assertEquals("the session started when the reading did", startedAt, session.startedAtEpochMillis)
        assertEquals(finishedAt, session.createdAtEpochMillis)
        assertEquals(55, session.durationMinutes)
        assertEquals(120, session.startPage)
        assertEquals(180, session.endPage)
    }

    @Test
    fun theBookMovesToThePageTheUserStoppedOn() {
        assertEquals(180, finish()!!.readingBooks.single().currentPage)
    }

    /**
     * Backwards too. Re-reading a chapter is a thing people do, and the page
     * the user says they are on is the page they are on.
     */
    @Test
    fun theBookCanMoveBackwards() {
        assertEquals(40, finish(startPage = 120, endPage = 40)!!.readingBooks.single().currentPage)
    }

    /**
     * A typo in a page field is a typo, not a reason to store page 7000 of a
     * 700-page book.
     */
    @Test
    fun pagesBeyondTheBookArePulledInsideIt() {
        val after = finish(startPage = -5, endPage = 7000)!!

        val session = after.readingSessions.single()
        assertEquals(0, session.startPage)
        assertEquals(700, session.endPage)
        assertEquals(700, after.readingBooks.single().currentPage)
    }

    /**
     * Zero minutes would later be divided into, for the pace estimate. The
     * reading happened; it gets the smallest duration that is true.
     */
    @Test
    fun aReadingNeverLastsLessThanAMinute() {
        assertEquals(1, finish(durationMinutes = 0)!!.readingSessions.single().durationMinutes)
        assertEquals(1, finish(durationMinutes = -30)!!.readingSessions.single().durationMinutes)
    }

    @Test
    fun theSessionJoinsTheOnesAlreadyRecorded() {
        val withHistory = reading.copy(readingSessions = listOf(olderQuestion))

        val after = finish(from = withHistory)!!

        assertEquals(2, after.readingSessions.size)
        assertEquals(olderQuestion, after.readingSessions.first())
    }

    /**
     * The one this grouping was made to state. Filling in this form is not an
     * answer to a question about some other interrupted reading.
     */
    @Test
    fun finishingDoesNotAnswerAQuestionThatWasAlreadyWaiting() {
        val asked = reading.copy(pendingReadingSession = olderQuestion)

        val after = finish(from = asked)!!

        assertEquals(olderQuestion, after.pendingReadingSession)
        assertEquals("and it is not recorded either", 1, after.readingSessions.size)
    }

    /* ---------------- keeping ---------------- */

    @Test
    fun keepingPutsTheQuestionIntoHistoryUnchanged() {
        val asked = AppState(pendingReadingSession = olderQuestion)

        val after = stateAfterKeepingPendingReadingSession(asked, alwaysFromNowOn = false)

        assertEquals(listOf(olderQuestion), after.readingSessions)
        assertNull(after.pendingReadingSession)
        assertFalse(after.autoRecordInterruptedReading)
    }

    @Test
    fun keepingWithAlwaysStopsTheQuestionBeingAskedAgain() {
        val asked = AppState(pendingReadingSession = olderQuestion)

        val after = stateAfterKeepingPendingReadingSession(asked, alwaysFromNowOn = true)

        assertTrue(after.autoRecordInterruptedReading)
    }

    /**
     * The flag only ever goes on. Once it is set this dialog is not shown at
     * all, so an answer given without the box ticked cannot be an answer that
     * unsets it — there would be no way back.
     */
    @Test
    fun aPlainAnswerNeverUnsetsAlways() {
        val asked = AppState(
            pendingReadingSession = olderQuestion,
            autoRecordInterruptedReading = true,
        )

        val after = stateAfterKeepingPendingReadingSession(asked, alwaysFromNowOn = false)

        assertTrue(after.autoRecordInterruptedReading)
    }

    @Test
    fun keepingWithNothingAskedChangesNothing() {
        val quiet = AppState(readingBooks = listOf(book))

        assertEquals(quiet, stateAfterKeepingPendingReadingSession(quiet, alwaysFromNowOn = true))
    }

    /**
     * Answering a question is not an answer about the book currently open.
     */
    @Test
    fun keepingLeavesAReadingInProgressAlone() {
        val both = reading.copy(pendingReadingSession = olderQuestion)

        val after = stateAfterKeepingPendingReadingSession(both, alwaysFromNowOn = false)

        assertNotNull(after.activeReading)
        assertEquals(1L, after.activeReading?.bookId)
        assertEquals(startedAt, after.activeReading?.startedAtEpochMillis)
    }

    /* ---------------- discarding ---------------- */

    @Test
    fun discardingThrowsTheQuestionAwayAndRecordsNothing() {
        val asked = AppState(pendingReadingSession = olderQuestion)

        val after = stateAfterDiscardingPendingReadingSession(asked)

        assertNull(after.pendingReadingSession)
        assertTrue(after.readingSessions.isEmpty())
    }

    @Test
    fun discardingNeverSetsAlways() {
        val asked = AppState(pendingReadingSession = olderQuestion)

        assertFalse(stateAfterDiscardingPendingReadingSession(asked).autoRecordInterruptedReading)
    }

    @Test
    fun discardingLeavesAReadingInProgressAlone() {
        val both = reading.copy(pendingReadingSession = olderQuestion)

        val after = stateAfterDiscardingPendingReadingSession(both)

        assertEquals(1L, after.activeReading?.bookId)
    }

    /* ---------------- cancelling ---------------- */

    @Test
    fun cancellingEndsTheReadingAndRecordsNothing() {
        val after = stateAfterCancellingReading(reading)

        assertNull(after.activeReading)
        assertTrue(after.readingSessions.isEmpty())
        assertNull("the user walked away; there is nothing to ask about", after.pendingReadingSession)
    }

    @Test
    fun cancellingLeavesTheBookWhereItWas() {
        val after = stateAfterCancellingReading(reading)

        assertEquals(120, after.readingBooks.single().currentPage)
        assertEquals(ReadingShelf.NOW, after.readingBooks.single().shelf)
    }

    /**
     * A question about some earlier reading is a different piece of time and
     * goes on waiting.
     */
    @Test
    fun cancellingDoesNotAnswerAQuestionThatWasAlreadyWaiting() {
        val both = reading.copy(pendingReadingSession = olderQuestion)

        assertEquals(olderQuestion, stateAfterCancellingReading(both).pendingReadingSession)
    }

    @Test
    fun cancellingWithNothingBeingReadChangesNothing() {
        val quiet = AppState(readingBooks = listOf(book))

        assertEquals(quiet, stateAfterCancellingReading(quiet))
    }
}
