package com.alphaomegos.annasagenda

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    /* ---------------- a payload from the future ---------------- */

    /**
     * Reading it as best we can is the dangerous option, not the safe one:
     * unknown fields are dropped on the way in, and the next save stamps the
     * remains with the current version. The newer data would be gone with
     * nothing left to show it ever existed.
     */
    @Test
    fun aPayloadFromANewerSchemaIsRefused() {
        val raw = """{"v":${CURRENT_SCHEMA_VERSION + 1},"tasks":[]}"""

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(result is AppStateDecodeResult.Failure)

        val cause = (result as AppStateDecodeResult.Failure).cause
        assertTrue("the reason must be tellable apart", cause is AppStateTooNewException)

        cause as AppStateTooNewException
        assertEquals(CURRENT_SCHEMA_VERSION + 1, cause.payloadVersion)
        assertEquals(CURRENT_SCHEMA_VERSION, cause.supportedVersion)
    }

    @Test
    fun aPayloadOfTheCurrentSchemaIsStillRead() {
        val raw = """{"v":$CURRENT_SCHEMA_VERSION,"tasks":[]}"""

        assertTrue(decodeAppStateJsonOrFailure(raw) is AppStateDecodeResult.Success)
    }

    @Test
    fun anOlderPayloadIsStillMigratedAndRead() {
        val raw = """{"v":0,"dailyGoalKcal":1800}"""

        assertTrue(decodeAppStateJsonOrFailure(raw) is AppStateDecodeResult.Success)
    }

    /**
     * The default matters: decodeFromJson is the import path, and an archive's
     * rules were written under a language this device knows nothing about.
     */
    @Test
    fun decodingWithoutAWeekStartLeavesLegacyRulesUnpinned() {
        val raw = """
            {
              "v": 3,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": { "freq": "WEEKLY", "interval": 2, "weekDaysIso": [1] }
                }
              ]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(result is AppStateDecodeResult.Success)
        val rule = (result as AppStateDecodeResult.Success).state.tasks.single().repeatRule
        assertNull("an imported rule must not be pinned to a guess", rule?.weekStart)
    }

    @Test
    fun decodingWithAWeekStartPinsLegacyRulesToIt() {
        val raw = """
            {
              "v": 3,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": { "freq": "WEEKLY", "interval": 2, "weekDaysIso": [1] }
                }
              ]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw, weekStartForLegacyRules = DayOfWeek.SUNDAY)

        assertTrue(result is AppStateDecodeResult.Success)
        val rule = (result as AppStateDecodeResult.Success).state.tasks.single().repeatRule
        assertEquals(DayOfWeek.SUNDAY, rule?.weekStart)
    }
}
