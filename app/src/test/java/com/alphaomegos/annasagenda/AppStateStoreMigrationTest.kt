package com.alphaomegos.annasagenda

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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