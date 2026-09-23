package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateStoreRoundTripTest {

    @Test
    fun appStateRoundTrip_preservesImportantTopLevelFields() {
        val validFieldIds = allAnthropometryFieldIds().take(2).toSet()

        val original = AppState(
            suppressedRecurrences = setOf(
                "T:task-1:20432",
                "S:subtask-1:20433",
            ),
            anthropometryEnabledFieldIds = validFieldIds,
            runningPlanApproved = true,
            mainMenuOrder = listOf(
                "calendar",
                "new_task",
                "running",
                "reading",
            ),
            mainMenuHiddenIds = setOf(
                "someday",
                "counters",
            ),
            undoneHorizonDays = 90,
        )

        val json = appStateStoreJson.encodeToString(original.toDto())
        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertTrue(json.contains("runningPlanApproved"))
        assertTrue(json.contains("mainMenuOrder"))
        assertTrue(json.contains("undoneHorizonDays"))
        assertEquals(original, restored)
    }

    /**
     * A session in progress has to survive being written down and read back —
     * that is the entire reason it lives in the state rather than in the view
     * model, where a reclaimed process simply lost it.
     */
    @Test
    fun appStateRoundTrip_keepsAReadingSessionInProgress() {
        val original = AppState(
            readingBooks = listOf(
                ReadingBook(
                    id = 1L,
                    shelf = ReadingShelf.NOW,
                    title = "Book",
                    totalPages = 300,
                    currentPage = 42,
                    createdAtEpochMillis = 1_700_000_000_000L,
                )
            ),
            activeReading = ActiveReading(
                bookId = 1L,
                startedAtEpochMillis = 1_700_000_123_000L,
                startPage = 42,
            ),
        )

        val json = appStateStoreJson.encodeToString(original.toDto())
        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertTrue(json.contains("activeReading"))
        assertEquals(original.activeReading, restored.activeReading)
        assertEquals(original, restored)
    }

    /** A payload written before sessions were saved simply has none. */
    @Test
    fun aPayloadWithoutASessionDecodesToNoSession() {
        val json = """{"v":4,"readingBooks":[],"tasks":[]}"""

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertEquals(null, restored.activeReading)
    }

    @Test
    fun defaultAppStateRoundTrip_isStable() {
        val original = AppState()

        val json = appStateStoreJson.encodeToString(original.toDto())
        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertEquals(original, restored)
    }
}