package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.DayOfWeek
import org.junit.Test
import java.time.LocalDate

class AppStateStoreMigrationTest {

    @Test
    fun migrateVersion0_movesDailyGoalKcalIntoCalorieGoalChanges() {
        val raw = """
            {
              "dailyGoalKcal": 1800
            }
        """.trimIndent()

        val root = migrateAppStateRawJson(raw) as JsonObject

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)
        assertFalse(root.containsKey("dailyGoalKcal"))

        val changes = root["calorieGoalChanges"] as? JsonArray
        assertEquals(1, changes?.size)

        val first = changes?.get(0) as? JsonObject
        assertEquals(1800, first?.get("kcal")?.jsonPrimitive?.intOrNull)
        assertEquals(
            LocalDate.now().toEpochDay(),
            first?.get("dateEpochDay")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        )
    }

    /**
     * Existing weekly rules must come out of the migration pinned to the week
     * boundary the device is using right now, so nothing in a schedule that
     * already exists moves.
     */
    @Test
    fun migrateVersion3_recordsTheCurrentWeekStartOnEveryRepeatRule() {
        val raw = """
            {
              "v": 3,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": { "freq": "WEEKLY", "interval": 2, "weekDaysIso": [1] }
                },
                { "id": 2, "order": 1, "description": "No rule" }
              ],
              "subtasks": [
                {
                  "id": 3, "order": 0, "taskId": 1, "description": "Weekly sub",
                  "repeatRule": { "freq": "WEEKLY", "interval": 1, "weekDaysIso": [3] }
                }
              ]
            }
        """.trimIndent()

        val root = migrateAppStateRawJson(raw, DayOfWeek.MONDAY) as JsonObject
        val expected = DayOfWeek.MONDAY.value

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)

        val tasks = root["tasks"] as JsonArray
        val firstRule = (tasks[0] as JsonObject)["repeatRule"] as JsonObject
        assertEquals(expected, firstRule["weekStartIso"]?.jsonPrimitive?.intOrNull)

        // A task without a rule is left exactly as it was.
        assertFalse((tasks[1] as JsonObject).containsKey("repeatRule"))

        val subtasks = root["subtasks"] as JsonArray
        val subRule = (subtasks[0] as JsonObject)["repeatRule"] as JsonObject
        assertEquals(expected, subRule["weekStartIso"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun migrateVersion3_leavesAnAlreadyRecordedWeekStartAlone() {
        val raw = """
            {
              "v": 3,
              "tasks": [
                {
                  "id": 1, "order": 0, "description": "Weekly",
                  "repeatRule": {
                    "freq": "WEEKLY", "interval": 2,
                    "weekDaysIso": [1], "weekStartIso": 7
                  }
                }
              ]
            }
        """.trimIndent()

        val root = migrateAppStateRawJson(raw, DayOfWeek.MONDAY) as JsonObject
        val rule = ((root["tasks"] as JsonArray)[0] as JsonObject)["repeatRule"] as JsonObject

        assertEquals(7, rule["weekStartIso"]?.jsonPrimitive?.intOrNull)
    }


    /**
     * A restored archive was written under somebody else's language, and this
     * device's is not evidence of what that was. Freezing a guess here moves
     * every "every N weeks" rule by a week, permanently — so the field is left
     * out and the rule keeps falling back at evaluation time, exactly as it did
     * before the field existed.
     */
    @Test
    fun migrateVersion3_leavesTheWeekStartOutWhenItIsNotKnown() {
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

        val root = migrateAppStateRawJson(raw, weekStartForLegacyRules = null) as JsonObject
        val rule = ((root["tasks"] as JsonArray)[0] as JsonObject)["repeatRule"] as JsonObject

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)
        assertFalse(
            "a guess must not be frozen into the rule",
            rule.containsKey("weekStartIso"),
        )
        assertEquals(2, rule["interval"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun migrateVersion3_recordsWhicheverWeekStartItIsGiven() {
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

        val root = migrateAppStateRawJson(raw, DayOfWeek.SUNDAY) as JsonObject
        val rule = ((root["tasks"] as JsonArray)[0] as JsonObject)["repeatRule"] as JsonObject

        assertEquals(DayOfWeek.SUNDAY.value, rule["weekStartIso"]?.jsonPrimitive?.intOrNull)
    }

    /**
     * Asserted on the decoded state rather than on the JSON, because the JSON
     * is not what the app runs on. A migration can leave the payload looking
     * exactly right and still produce something the DTO layer drops on the
     * floor — a renamed field, a number where a string is expected — and a test
     * that stops at the JSON says nothing about that.
     */
    @Test
    fun migrateVersion1_convertsDurationHmsTextIntoDurationHhMmText() {
        val raw = """
            {
              "v": 1,
              "runningPlanEntries": [
                {
                  "dateEpochDay": 20000,
                  "distanceKmText": "10.0",
                  "durationHmsText": "01:02:03",
                  "paceText": "06:12"
                }
              ]
            }
        """.trimIndent()

        val entry = decoded(raw).runningPlanEntries.single()

        assertEquals(LocalDate.ofEpochDay(20000), entry.date)
        assertEquals("0103", entry.durationHhMmText)
        assertEquals("10.0", entry.distanceKmText)
        assertEquals("06:12", entry.paceText)
    }

    /**
     * The migration chain has to reach the current version from every version
     * that has ever been written to a device, and it has to arrive at something
     * that still decodes.
     *
     * The failure this guards against is silent by nature. Raising
     * [CURRENT_SCHEMA_VERSION] without adding the branch that gets there used
     * to leave the loop bumping the number on its own, so unconverted data was
     * saved back stamped as current — readable, wrong, and no longer
     * distinguishable from data that had been converted properly. The loop now
     * refuses to invent that step, and this test is what notices.
     */
    @Test
    fun everyStoredVersionHasAMigrationThatReachesTheCurrentSchema() {
        (0 until CURRENT_SCHEMA_VERSION).forEach { version ->
            val raw = """{ "v": $version }"""

            val root = migrateAppStateRawJson(raw) as JsonObject

            assertEquals(
                "version $version does not reach the current schema",
                CURRENT_SCHEMA_VERSION,
                root["v"]?.jsonPrimitive?.intOrNull,
            )

            // And what comes out is still a payload the app can read.
            decoded(raw)
        }
    }

    @Test
    fun migrateVersion2_convertsDurationMinutesTextIntoDurationHhMmText() {
        val raw = """
            {
              "v": 2,
              "runningPlanEntries": [
                {
                  "dateEpochDay": 20000,
                  "distanceKmText": "10.0",
                  "durationMinutesText": "75",
                  "paceText": "07:30"
                }
              ]
            }
        """.trimIndent()

        val root = migrateAppStateRawJson(raw) as JsonObject
        val entry = ((root["runningPlanEntries"] as JsonArray)[0] as JsonObject)

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)
        assertFalse(entry.containsKey("durationMinutesText"))
        assertEquals("0115", entry["durationHhMmText"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun normalizeAnthropometryFieldIdsForStore_returnsDefaultsWhenInputIsEmptyOrInvalid() {
        assertEquals(
            defaultAnthropometryFieldIds(),
            normalizeAnthropometryFieldIdsForStore(emptyList()),
        )

        assertEquals(
            defaultAnthropometryFieldIds(),
            normalizeAnthropometryFieldIdsForStore(listOf("bad", " ", "unknown")),
        )
    }

    @Test
    fun normalizeAnthropometryFieldIdsForStore_keepsValidTrimmedIds() {
        val validId = allAnthropometryFieldIds().first()

        assertEquals(
            setOf(validId),
            normalizeAnthropometryFieldIdsForStore(listOf("  $validId  ", "bad", validId)),
        )
    }

    /**
     * Version 5 adds the log of runs, and converts nothing.
     *
     * The whole point of raising the number is that an older build refuses a
     * payload it would otherwise quietly strip, so the migration's only job is
     * to stamp it — and its other job is to touch nothing else, which is what
     * is checked here.
     */
    @Test
    fun migrateVersion4_stampsTheVersionAndChangesNothingElse() {
        val raw = """
            {
              "v": 4,
              "runningPlanApproved": true,
              "undoneHorizonDays": 90,
              "tasks": [
                { "id": 1, "order": 0, "description": "Пробежка" }
              ]
            }
        """.trimIndent()

        val before = appStateStoreJson.decodeFromString<JsonObject>(raw)
        val root = migrateAppStateRawJson(raw) as JsonObject

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)
        assertEquals(
            "nothing but the version may move",
            before.filterKeys { it != "v" },
            root.filterKeys { it != "v" },
        )
    }

    /**
     * And the state that comes out of it is the state every user already had:
     * on the plan, with nothing logged.
     */
    @Test
    fun aVersionFourPayloadArrivesOnThePlanWithNoRunsLogged() {
        val state = decoded("""{ "v": 4 }""")

        assertEquals(RunningMode.PLAN, state.runningMode)
        assertTrue(state.runningWorkouts.isEmpty())
    }

    /** Migrates and decodes, failing with the reason when the payload does not survive. */
    private fun decoded(raw: String): AppState {
        val result = decodeAppStateJsonOrFailure(raw)

        assertTrue(
            "the migrated payload did not decode: " +
                "${(result as? AppStateDecodeResult.Failure)?.cause}",
            result is AppStateDecodeResult.Success,
        )

        return (result as AppStateDecodeResult.Success).state
    }
}