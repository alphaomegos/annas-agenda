package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    /**
     * Reading a payload is the one moment ids start being handed out again
     * from the live data, so it is also the last chance to throw away a
     * tombstone whose template is gone — before it can silently punch holes in
     * whatever inherits that id.
     */
    @Test
    fun decodingDropsATombstoneWhoseTemplateIsGone() {
        val json = """
            {
              "v": 4,
              "tasks": [ { "id": 5, "order": 0, "description": "Water the plants" } ],
              "subtasks": [ { "id": 7, "order": 0, "taskId": 5, "description": "Balcony" } ],
              "suppressedRecurrences": [
                "T:5:20000", "T:40:20000", "S:7:20000", "S:8:20000", "X:1:20000"
              ]
            }
        """.trimIndent()

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertEquals(
            setOf("T:5:20000", "S:7:20000", "X:1:20000"),
            restored.suppressedRecurrences,
        )
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

    /* ---------- the id counter's position ---------- */

    /**
     * The mark has to survive the store or it is worthless: its whole job is
     * to tell the next launch what the last one had already handed out. See
     * nextIdFor for what goes wrong when an id is given out twice.
     */
    @Test
    fun idHighWater_survivesTheStore() {
        val json = appStateStoreJson.encodeToString(AppState(idHighWater = 4321L).toDto())
        val restored = appStateStoreJson.decodeFromString<AppStateDto>(json).toDomain()

        assertTrue(json.contains("idHighWater"))
        assertEquals(4321L, restored.idHighWater)
    }

    @Test
    fun idHighWater_isAbsentFromAnOlderPayloadAndFallsBackToCounting() {
        val withoutTheField = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[],"subtasks":[]}"""

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(withoutTheField)
            .toDomain()

        assertEquals(0L, restored.idHighWater)
        assertEquals(1L, nextIdFor(restored))
    }

    /** A negative mark is nonsense; it must not make things worse than none. */
    @Test
    fun idHighWater_readsANegativeMarkAsNone() {
        val payload = """{"v":$CURRENT_SCHEMA_VERSION,"idHighWater":-9}"""

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(payload)
            .toDomain()

        assertEquals(0L, restored.idHighWater)
    }

    /**
     * The log of runs that actually happened, and the mode the screen is in.
     *
     * Worth its own round trip rather than a line in the one above: this is
     * the first thing added to the state that is **not** additive-with-a-
     * default, and it is why the schema went to 5. If it did not survive the
     * store there would be no point to any of it.
     */
    @Test
    fun appStateRoundTrip_keepsTheRunsThatHappened() {
        val original = AppState(
            runningMode = RunningMode.BETWEEN,
            runningWorkouts = listOf(
                RunningWorkout(
                    id = 1L,
                    date = LocalDate.of(2026, 9, 20),
                    distanceKm = 10.5,
                    durationMinutes = 58,
                    note = "набережная",
                ),
                RunningWorkout(
                    id = 2L,
                    date = LocalDate.of(2026, 9, 23),
                    distanceKm = 5.0,
                    durationMinutes = 26,
                ),
            ),
        )

        val json = appStateStoreJson.encodeToString(original.toDto())
        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertTrue(json.contains("runningWorkouts"))
        assertTrue("the mode is written by name, not by ordinal", json.contains("BETWEEN"))
        assertEquals(original, restored)
        assertEquals(10.5, restored.runningWorkouts.first().distanceKm, 0.0001)
    }

    /**
     * A payload written before any of this existed has no runs and is on the
     * plan, which is what every user had.
     */
    @Test
    fun aPayloadWithoutRunsDecodesToThePlanAndNothingLogged() {
        val json = appStateStoreJson.encodeToString(AppState().toDto())

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertEquals(RunningMode.PLAN, restored.runningMode)
        assertTrue(restored.runningWorkouts.isEmpty())
    }

    /**
     * A mode this build does not know falls back to the plan rather than
     * failing the whole decode. A mode is a view: guessing wrong costs one
     * tap, and refusing the payload would cost everything else in it.
     */
    @Test
    fun anUnknownRunningModeFallsBackToThePlan() {
        val json = appStateStoreJson.encodeToString(
            AppState().toDto().copy(runningMode = "SOMETHING_LATER")
        )

        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertEquals(RunningMode.PLAN, restored.runningMode)
    }
}
