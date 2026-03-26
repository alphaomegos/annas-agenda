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
        )

        val json = appStateStoreJson.encodeToString(original.toDto())
        val restored = appStateStoreJson
            .decodeFromString<AppStateDto>(json)
            .toDomain()

        assertTrue(json.contains("runningPlanApproved"))
        assertTrue(json.contains("mainMenuOrder"))
        assertEquals(original, restored)
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