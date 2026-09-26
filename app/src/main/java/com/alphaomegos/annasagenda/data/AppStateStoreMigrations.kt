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
import java.time.DayOfWeek
import java.util.Locale

/**
 * Brings a stored payload up to the current schema.
 *
 * [weekStartForLegacyRules] is the first day of the week that the repeat rules
 * in this payload were written under, or null when that is not known. See
 * [migrateAppState3To4] — guessing it wrong moves schedules, so it is asked for
 * rather than assumed.
 */
internal fun migrateAppStateRawJson(
    raw: String,
    weekStartForLegacyRules: DayOfWeek? = null,
): JsonElement {
    val element = appStateStoreJson.decodeFromString<JsonElement>(raw)
    val root = element as? JsonObject ?: return element

    var cur = root
    var v = cur["v"]?.jsonPrimitive?.intOrNull ?: 0

    // A payload from the future is refused rather than read as best we can.
    // Decoding drops the fields this build does not know, and the next save
    // stamps the remains with the current version — which destroys the newer
    // data and leaves no trace that it was ever there.
    if (v > CURRENT_SCHEMA_VERSION) {
        throw AppStateTooNewException(
            payloadVersion = v,
            supportedVersion = CURRENT_SCHEMA_VERSION,
        )
    }

    while (v < CURRENT_SCHEMA_VERSION) {
        val from = v

        cur = when (v) {
            0 -> migrateAppState0To1(cur)
            1 -> migrateAppState1To2(cur)
            2 -> migrateAppState2To3(cur)
            3 -> migrateAppState3To4(cur, weekStartForLegacyRules)
            4 -> migrateAppState4To5(cur)
            else -> throw MissingMigrationException(from, CURRENT_SCHEMA_VERSION)
        }

        // Every step has to say what it turned the payload into, and it has to
        // be more than it was. Assuming otherwise is how data gets stamped as
        // converted without being touched: the loop used to fall back to
        // `v + 1`, so a missing branch and a migration that forgot to write "v"
        // both ended with the payload claiming to be current.
        v = cur["v"]?.jsonPrimitive?.intOrNull
            ?: throw MissingMigrationException(from, CURRENT_SCHEMA_VERSION)

        if (v <= from) throw MissingMigrationException(from, CURRENT_SCHEMA_VERSION)
    }

    return cur
}

/**
 * A stored version that no migration handles, or one a migration failed to
 * advance.
 *
 * This is a mistake in this file rather than anything the user did, and it can
 * only appear after [CURRENT_SCHEMA_VERSION] is raised. Refusing the payload
 * sends it to the unreadable-state screen, which keeps it on disk untouched —
 * the alternative is writing the unconverted data back under the new version
 * number, and there is no way back from that.
 */
internal class MissingMigrationException(
    val fromVersion: Int,
    val supportedVersion: Int,
) : IllegalStateException(
    "No migration takes app state from version $fromVersion towards $supportedVersion"
)

/**
 * Nothing to convert, and that is the point.
 *
 * Version 5 adds the log of runs that actually happened. Every field added
 * before this one was additive-with-a-default, so an older build reading a
 * newer payload lost nothing it could not reconstruct, and the version stayed
 * where it was. This one is different: those runs are data nothing else holds,
 * and a build that did not know the key would drop the whole list on its next
 * save without saying anything.
 *
 * Raising the version makes such a build refuse the payload outright — the
 * unreadable-state screen, with the file left exactly as it is. That is worth
 * saying out loud because it has a cost: a rollback to an older APK can no
 * longer read data written after this, and the way back is to restore a backup
 * made before it.
 *
 * Stamping the version is therefore the entire migration. The list defaults to
 * empty and the mode defaults to the plan, which is what every user had.
 */
private fun migrateAppState4To5(obj: JsonObject): JsonObject {
    val m = obj.toMutableMap()
    m["v"] = JsonPrimitive(5)
    return JsonObject(m)
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

/**
 * Freezes the week boundary that existing repeat rules have been using.
 *
 * Until now WEEKLY rules took the first day of the week from the current
 * locale, so switching the app language could move an "every N weeks" rule by a
 * week. Writing the value those rules have been running under into each one
 * keeps every existing schedule exactly where it is.
 *
 * Which value that is depends on where the payload came from, and there is only
 * one case where it is known. Upgrading in place, the app's own language is the
 * language the schedules were built under, so it is the right answer. A payload
 * arriving from somewhere else — a restored archive — was written under a
 * language this device knows nothing about, and the device's own is not
 * evidence of anything. Filling it in there would freeze a guess, and a wrong
 * guess moves every "every N weeks" rule by a week, permanently.
 *
 * So when it is not known, the field is left out. Those rules keep falling back
 * to the current locale, exactly as they did before any of this existed, and
 * the first edit of a rule pins it properly.
 */
private fun migrateAppState3To4(obj: JsonObject, weekStart: DayOfWeek?): JsonObject {
    val m = obj.toMutableMap()

    if (weekStart == null) {
        m["v"] = JsonPrimitive(4)
        return JsonObject(m)
    }

    val weekStartIso = weekStart.value

    listOf("tasks", "subtasks").forEach { field ->
        val items = m[field] as? JsonArray ?: return@forEach

        m[field] = JsonArray(
            items.map { element ->
                val item = element as? JsonObject ?: return@map element
                val rule = item["repeatRule"] as? JsonObject ?: return@map element

                if (rule.containsKey("weekStartIso")) return@map element

                val patchedRule = rule.toMutableMap().apply {
                    put("weekStartIso", JsonPrimitive(weekStartIso))
                }

                JsonObject(
                    item.toMutableMap().apply {
                        put("repeatRule", JsonObject(patchedRule))
                    }
                )
            }
        )
    }

    m["v"] = JsonPrimitive(4)
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