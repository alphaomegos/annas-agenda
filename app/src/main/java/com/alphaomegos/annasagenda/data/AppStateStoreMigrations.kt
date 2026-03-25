package com.alphaomegos.annasagenda

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.Locale

internal fun migrateAppStateRawJson(raw: String): JsonElement {
    val element = appStateStoreJson.decodeFromString<JsonElement>(raw)
    val root = element as? JsonObject ?: return element

    var cur = root
    var v = cur["v"]?.jsonPrimitive?.intOrNull ?: 0

    var safety = 0
    while (v < CURRENT_SCHEMA_VERSION && safety < 50) {
        val next = when (v) {
            0 -> migrateAppState0To1(cur)
            1 -> migrateAppState1To2(cur)
            2 -> migrateAppState2To3(cur)
            else -> cur
        }

        cur = next
        val newV = cur["v"]?.jsonPrimitive?.intOrNull
        v = if (newV != null && newV > v) newV else (v + 1)
        safety++
    }

    return cur
}

private fun migrateAppState0To1(obj: JsonObject): JsonObject {
    val m = obj.toMutableMap()

    val dailyGoalKcal = m["dailyGoalKcal"]?.jsonPrimitive?.intOrNull
    val existingChanges = m["calorieGoalChanges"] as? JsonArray

    if (existingChanges.isNullOrEmpty() && dailyGoalKcal != null) {
        val todayEpochDay = LocalDate.now().toEpochDay()
        val change = buildJsonObject {
            put("dateEpochDay", JsonPrimitive(todayEpochDay))
            put("kcal", JsonPrimitive(dailyGoalKcal))
        }
        m["calorieGoalChanges"] = JsonArray(listOf(change))
    }

    m.remove("dailyGoalKcal")
    m["v"] = JsonPrimitive(1)
    return JsonObject(m)
}

private fun migrateAppState1To2(obj: JsonObject): JsonObject {
    val m = obj.toMutableMap()

    val entries = m["runningPlanEntries"] as? JsonArray
    if (entries != null) {
        val converted = entries.map { el ->
            val o = el as? JsonObject ?: return@map el
            val mm = o.toMutableMap()

            if (!mm.containsKey("durationMinutesText")) {
                val legacy = mm["durationHmsText"]?.jsonPrimitive?.contentOrNull
                val minutes = legacy?.let { parseLegacyDurationToMinutes(it) }
                if (minutes != null) {
                    mm["durationMinutesText"] = JsonPrimitive(minutes.toString())
                }
            }

            mm.remove("durationHmsText")
            JsonObject(mm)
        }
        m["runningPlanEntries"] = JsonArray(converted)
    }

    m["v"] = JsonPrimitive(2)
    return JsonObject(m)
}

private fun migrateAppState2To3(obj: JsonObject): JsonObject {
    val m = obj.toMutableMap()

    val entries = m["runningPlanEntries"] as? JsonArray
    if (entries != null) {
        val converted = entries.map { el ->
            val o = el as? JsonObject ?: return@map el
            val mm = o.toMutableMap()

            if (!mm.containsKey("durationHhMmText")) {
                val legacyMinutes = mm["durationMinutesText"]?.jsonPrimitive?.contentOrNull
                val hhMmDigits = legacyMinutes?.toIntOrNull()?.let { minutesToHhMmDigits(it) }
                if (hhMmDigits != null) {
                    mm["durationHhMmText"] = JsonPrimitive(hhMmDigits)
                }
            }

            mm.remove("durationMinutesText")
            JsonObject(mm)
        }
        m["runningPlanEntries"] = JsonArray(converted)
    }

    m["v"] = JsonPrimitive(3)
    return JsonObject(m)
}

private fun parseLegacyDurationToMinutes(raw: String): Int? {
    val s = raw.trim()
    if (s.isBlank()) return null

    s.toIntOrNull()?.let { return it.coerceAtLeast(0) }

    val parts = s.split(":")
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }

    val (h, m, sec) = if (numbers.size == 3) {
        Triple(numbers[0], numbers[1], numbers[2])
    } else {
        Triple(0, numbers[0], numbers[1])
    }

    if (m !in 0..59) return null
    if (sec !in 0..59) return null
    if (h !in 0..99) return null

    val totalSeconds = h * 3600 + m * 60 + sec
    return (totalSeconds + 59) / 60
}

private fun minutesToHhMmDigits(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val h = (safe / 60).coerceIn(0, 99)
    val m = (safe % 60).coerceIn(0, 59)
    return String.format(Locale.US, "%02d%02d", h, m)
}