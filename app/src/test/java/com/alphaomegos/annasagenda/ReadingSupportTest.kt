package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The per-shelf view settings, and the "how much longer" estimate.
 *
 * Both are hand-written `when` blocks over four shelves, which is exactly the
 * shape that goes wrong quietly: one branch writing into the wrong field is
 * invisible until somebody notices their sort order moved between tabs.
 */
class ReadingSupportTest {

    @Test
    fun tabPrefs_comeBackFromTheShelfTheyWereStoredOn() {
        ReadingShelf.entries.forEach { shelf ->
            val prefs = ReadingTabPrefs(
                viewMode = ReadingViewMode.WALL,
                sort = ReadingSort(field = ReadingSortField.PAGES, ascending = false),
            )

            val state = readingStateWithTabPrefs(AppState(), shelf, prefs)

            assertEquals("$shelf did not store its own settings", prefs, readingTabPrefsForShelf(state, shelf))
        }
    }

    @Test
    fun tabPrefs_leaveTheOtherThreeShelvesAlone() {
        ReadingShelf.entries.forEach { shelf ->
            val state = readingStateWithTabPrefs(
                AppState(),
                shelf,
                ReadingTabPrefs(viewMode = ReadingViewMode.LIST),
            )

            ReadingShelf.entries.filterNot { it == shelf }.forEach { other ->
                assertEquals(
                    "changing $shelf also changed $other",
                    ReadingTabPrefs(),
                    readingTabPrefsForShelf(state, other),
                )
            }
        }
    }

    @Test
    fun remainingTime_isZeroForABookThatIsFinished() {
        val book = book(totalPages = 300, currentPage = 300)

        assertEquals(0, estimateRemainingReadingMinutes(book, emptyList()))
        assertEquals(0, estimateRemainingReadingHours(book, emptyList()))
    }

    @Test
    fun remainingTime_isUnknownUntilThereIsSomethingToMeasureWith() {
        val book = book(totalPages = 300, currentPage = 100)

        // Nothing read yet.
        assertNull(estimateRemainingReadingMinutes(book, emptyList()))

        // Somebody else's session says nothing about this book.
        assertNull(
            estimateRemainingReadingMinutes(
                book,
                listOf(session(bookId = 999L, startPage = 0, endPage = 50, minutes = 30)),
            )
        )

        // A session that did not move the bookmark gives no speed to work from.
        assertNull(
            estimateRemainingReadingMinutes(
                book,
                listOf(session(startPage = 40, endPage = 40, minutes = 30)),
            )
        )
    }

    @Test
    fun remainingTime_usesThePaceOfTheLatestSession() {
        val book = book(totalPages = 300, currentPage = 100)

        val sessions = listOf(
            // Newest, and deliberately not last in the list: the estimate has
            // to go by when a session was written down, not by where it sits.
            session(startPage = 50, endPage = 100, minutes = 30, createdAt = 2_000L),
            session(startPage = 0, endPage = 50, minutes = 200, createdAt = 1_000L),
        )

        // 200 pages left, 50 pages in 30 minutes.
        assertEquals(120, estimateRemainingReadingMinutes(book, sessions))
        assertEquals(2, estimateRemainingReadingHours(book, sessions))
    }

    @Test
    fun remainingTime_roundsUpRatherThanFlatteringTheReader() {
        val book = book(totalPages = 110, currentPage = 100)
        val sessions = listOf(session(startPage = 0, endPage = 3, minutes = 10))

        // 10 pages left at 3 pages per 10 minutes is 33 and a third.
        assertEquals(34, estimateRemainingReadingMinutes(book, sessions))

        // And any part of an hour is an hour.
        assertEquals(1, estimateRemainingReadingHours(book, sessions))
    }

    @Test
    fun remainingTime_treatsAnInstantSessionAsOneMinute() {
        val book = book(totalPages = 200, currentPage = 100)
        val sessions = listOf(session(startPage = 0, endPage = 50, minutes = 0))

        // Not zero, and not a division by nothing: 100 pages at 50 per minute.
        assertEquals(2, estimateRemainingReadingMinutes(book, sessions))
    }

    private fun book(totalPages: Int, currentPage: Int) = ReadingBook(
        id = 1L,
        title = "Dune",
        totalPages = totalPages,
        currentPage = currentPage,
    )

    private fun session(
        bookId: Long = 1L,
        startPage: Int,
        endPage: Int,
        minutes: Int,
        createdAt: Long = 1_000L,
    ) = ReadingSession(
        id = startPage.toLong() * 1000 + endPage,
        bookId = bookId,
        startedAtEpochMillis = createdAt,
        durationMinutes = minutes,
        startPage = startPage,
        endPage = endPage,
        createdAtEpochMillis = createdAt,
    )
}
