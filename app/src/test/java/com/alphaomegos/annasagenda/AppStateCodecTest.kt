package com.alphaomegos.annasagenda

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression tests for the decode path.
 *
 * The behaviour being locked down: an unreadable payload must surface as
 * [AppStateDecodeResult.Failure], never as a valid-looking empty [AppState].
 * The old code mapped every failure to null and the caller turned that into
 * `AppState()`, which autosave then wrote over the real data.
 */
class AppStateCodecTest {

    @Test
    fun decode_returnsSuccess_forPayloadWrittenByEncoder() {
        val original = AppState(
            suppressedRecurrences = setOf("T:1:20432"),
            runningPlanApproved = true,
            mainMenuOrder = listOf("calendar", "new_task"),
        )

        val raw = appStateStoreJson.encodeToString(original.toDto())

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        assertEquals(original, (result as AppStateDecodeResult.Success).state)
    }

    @Test
    fun decode_returnsFailure_forTruncatedJson() {
        val result = decodeAppStateJsonOrFailure("""{"v":3,"tasks":[""")

        assertTrue("expected Failure, got $result", result is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_returnsFailure_forJsonThatIsNotAnObject() {
        val result = decodeAppStateJsonOrFailure("""["not", "an", "object"]""")

        assertTrue("expected Failure, got $result", result is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_returnsFailure_forBlankPayload() {
        assertTrue(decodeAppStateJsonOrFailure("") is AppStateDecodeResult.Failure)
        assertTrue(decodeAppStateJsonOrFailure("   \n ") is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_returnsFailure_whenRequiredFieldHasWrongType() {
        // "tasks" must be an array; a string here means the payload is unusable.
        val result = decodeAppStateJsonOrFailure("""{"v":3,"tasks":"nonsense"}""")

        assertTrue("expected Failure, got $result", result is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_returnsFailure_whenTaskIsMissingMandatoryField() {
        // Task.description has no default, so this payload cannot be decoded.
        val result = decodeAppStateJsonOrFailure("""{"v":3,"tasks":[{"id":1,"order":0}]}""")

        assertTrue("expected Failure, got $result", result is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_failureIsDistinguishableFromAnEmptyButValidState() {
        val emptyButValid = decodeAppStateJsonOrFailure(
            appStateStoreJson.encodeToString(AppState().toDto())
        )
        val unreadable = decodeAppStateJsonOrFailure("{ broken")

        assertTrue(emptyButValid is AppStateDecodeResult.Success)
        assertEquals(AppState(), (emptyButValid as AppStateDecodeResult.Success).state)
        assertTrue(unreadable is AppStateDecodeResult.Failure)
    }

    @Test
    fun decode_stillMigratesLegacySchemaVersions() {
        val legacy = """{"dailyGoalKcal": 1800}"""

        val result = decodeAppStateJsonOrFailure(legacy)

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        assertEquals(
            listOf(CalorieGoalChange(date = LocalDate.now(), kcal = 1800)),
            (result as AppStateDecodeResult.Success).state.calorieGoalChanges,
        )
    }
}
