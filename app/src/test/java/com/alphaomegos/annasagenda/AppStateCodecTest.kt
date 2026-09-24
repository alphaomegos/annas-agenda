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
            // The task the tombstone names has to be here. Decoding drops
            // tombstones whose template is gone, so a fixture naming a task
            // that does not exist is not a payload the app could ever write —
            // and a round-trip test built on one asserts something untrue.
            tasks = listOf(
                Task(
                    id = 1L,
                    date = LocalDate.ofEpochDay(20432),
                    description = "Water the plants",
                    repeatRule = RepeatRule(freq = RepeatFreq.DAILY),
                )
            ),
            suppressedRecurrences = setOf("T:1:20432"),
            runningPlanApproved = true,
            mainMenuOrder = listOf("calendar", "new_task"),
        )

        val raw = appStateStoreJson.encodeToString(original.toDto())

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        assertEquals(original, (result as AppStateDecodeResult.Success).state)
    }

    /**
     * The interaction that broke the test above, now pinned on purpose rather
     * than relied on by accident: decoding is also where orphaned tombstones
     * are dropped, so a round trip is only lossless for a payload whose
     * references hold.
     */
    @Test
    fun decode_dropsATombstoneWhoseTaskIsNotInThePayload() {
        val orphaned = AppState(suppressedRecurrences = setOf("T:1:20432"))

        val raw = appStateStoreJson.encodeToString(orphaned.toDto())
        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(result is AppStateDecodeResult.Success)
        assertEquals(
            emptySet<String>(),
            (result as AppStateDecodeResult.Success).state.suppressedRecurrences,
        )
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

    /* ---------------- references that no longer point anywhere ------------- */

    /**
     * Also pins the order the two repairs run in. The subtask is dropped
     * because its task is gone, which orphans the tombstone naming it — so
     * pruning has to come second. Pruning first would leave the tombstone
     * behind for one more save, where it could outlive the id it names.
     */
    @Test
    fun decode_dropsASubtaskWithNoTaskAndThenTheTombstoneThatNamedIt() {
        val day = LocalDate.ofEpochDay(20432)
        val damaged = AppState(
            subtasks = listOf(Subtask(id = 10L, taskId = 99L, description = "Orphan")),
            suppressedRecurrences = setOf(subtaskSuppressionKey(10L, day)),
        )

        val result = decodeAppStateJsonOrFailure(appStateStoreJson.encodeToString(damaged.toDto()))

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        val state = (result as AppStateDecodeResult.Success).state
        assertEquals(emptyList<Subtask>(), state.subtasks)
        assertEquals(emptySet<String>(), state.suppressedRecurrences)
    }

    @Test
    fun decode_cutsAPlanRowLooseFromATaskThatIsNotThere() {
        val damaged = AppState(
            runningPlanEntries = listOf(
                RunningPlanEntry(date = LocalDate.ofEpochDay(20432), taskId = 99L)
            ),
        )

        val result = decodeAppStateJsonOrFailure(appStateStoreJson.encodeToString(damaged.toDto()))

        assertTrue(result is AppStateDecodeResult.Success)
        val entry = (result as AppStateDecodeResult.Success).state.runningPlanEntries.single()
        assertNull("a plan row must not hold an id that can be handed out again", entry.taskId)
    }

    /**
     * The menu order is normalised when it is set, but a payload does not have
     * to have come from the setter — an id named twice would put the same item
     * on the menu twice.
     */
    @Test
    fun decode_normalisesTheMainMenuOrder() {
        val raw = """
            {
              "v": $CURRENT_SCHEMA_VERSION,
              "mainMenuOrder": [" calendar ", "", "reading", "calendar", "   "]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(result is AppStateDecodeResult.Success)
        assertEquals(
            listOf("calendar", "reading"),
            (result as AppStateDecodeResult.Success).state.mainMenuOrder,
        )
    }

    /* ---------------- fields the DTO and the domain read differently ------- */

    /**
     * The two day fields of a repeat rule are handled differently and it is
     * not an oversight, so both halves are pinned here.
     *
     * An unreadable week start has a meaning already — "not recorded, use the
     * device's locale" — so it becomes null and the payload is still read.
     */
    @Test
    fun anImpossibleWeekStartFallsBackInsteadOfRefusingThePayload() {
        val raw = """
            {
              "v": $CURRENT_SCHEMA_VERSION,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": {
                    "freq": "WEEKLY", "interval": 1,
                    "weekDaysIso": [1], "weekStartIso": 0
                  }
                }
              ]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        val rule = (result as AppStateDecodeResult.Success).state.tasks.single().repeatRule
        assertNull(rule?.weekStart)
        assertEquals(setOf(DayOfWeek.MONDAY), rule?.weekDays)
    }

    /**
     * An unreadable weekday has no such meaning. Dropping it would turn "every
     * Monday and Wednesday" into "every Monday", quietly and then permanently
     * at the next save, so the payload is refused and stays on disk instead.
     */
    @Test
    fun anImpossibleWeekdayRefusesThePayloadRatherThanLosingTheDay() {
        val raw = """
            {
              "v": $CURRENT_SCHEMA_VERSION,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": { "freq": "WEEKLY", "interval": 1, "weekDaysIso": [1, 0] }
                }
              ]
            }
        """.trimIndent()

        assertTrue(decodeAppStateJsonOrFailure(raw) is AppStateDecodeResult.Failure)
    }

    /**
     * A session written before the field existed has no created-at time. The
     * domain answers that with the start time; the DTO answered it with zero,
     * and all-zero timestamps make "the latest session" mean "whichever comes
     * first in the list" — which is what the remaining-time estimate reads.
     */
    @Test
    fun aSessionWithNoCreatedAtFallsBackToWhenItStarted() {
        val raw = """
            {
              "v": $CURRENT_SCHEMA_VERSION,
              "readingSessions": [
                {
                  "id": 1, "bookId": 7, "startedAtEpochMillis": 1700000000000,
                  "durationMinutes": 30, "startPage": 0, "endPage": 40
                }
              ]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue("expected Success, got $result", result is AppStateDecodeResult.Success)
        val session = (result as AppStateDecodeResult.Success).state.readingSessions.single()
        assertEquals(1700000000000L, session.createdAtEpochMillis)
    }

    @Test
    fun aSessionKeepsTheCreatedAtItWasWrittenWith() {
        val raw = """
            {
              "v": $CURRENT_SCHEMA_VERSION,
              "readingSessions": [
                {
                  "id": 1, "bookId": 7, "startedAtEpochMillis": 1700000000000,
                  "durationMinutes": 30, "startPage": 0, "endPage": 40,
                  "createdAtEpochMillis": 1700000999000
                }
              ]
            }
        """.trimIndent()

        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(result is AppStateDecodeResult.Success)
        assertEquals(
            1700000999000L,
            (result as AppStateDecodeResult.Success).state.readingSessions.single().createdAtEpochMillis,
        )
    }
}
