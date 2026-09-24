package com.alphaomegos.annasagenda

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val root = migrateAppStateRawJson(raw) as JsonObject
        val entry = ((root["runningPlanEntries"] as JsonArray)[0] as JsonObject)

        assertEquals(CURRENT_SCHEMA_VERSION, root["v"]?.jsonPrimitive?.intOrNull)
        assertFalse(entry.containsKey("durationHmsText"))
        assertFalse(entry.containsKey("durationMinutesText"))
        assertEquals("0103", entry["durationHhMmText"]?.jsonPrimitive?.contentOrNull)
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
}