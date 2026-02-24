@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import java.util.Locale

private val Context.appStateDataStore by preferencesDataStore(name = "app_state_store")

class AppStateStore(private val context: Context) {

    private val key = stringPreferencesKey("app_state_json")

    fun encodeToJson(state: AppState): String =
        json.encodeToString(state.toDto())

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun decodeFromJson(raw: String): AppState? =
        runCatching {
            val migrated = migrateRawJson(raw)
            val dto = json.decodeFromJsonElement<AppStateDto>(migrated)
            dto.toDomain()
        }.getOrNull()

    private fun migrateRawJson(raw: String): JsonElement {
        val element = json.parseToJsonElement(raw)
        val root = element as? JsonObject ?: return element

        var cur = root
        var v = cur["v"]?.jsonPrimitive?.intOrNull ?: 0

        var safety = 0
        while (v < CURRENT_SCHEMA_VERSION && safety < 50) {
            val next = when (v) {
                0 -> migrate0To1(cur)
                1 -> migrate1To2(cur)
                2 -> migrate2To3(cur)
                else -> cur
            }


            cur = next
            val newV = cur["v"]?.jsonPrimitive?.intOrNull
            v = if (newV != null && newV > v) newV else (v + 1)
            safety++
        }

        return cur
    }

    private fun migrate0To1(obj: JsonObject): JsonObject {
        val m = obj.toMutableMap()

        val dailyGoalKcal = m["dailyGoalKcal"]?.jsonPrimitive?.intOrNull
        val existingChanges = (m["calorieGoalChanges"] as? JsonArray)

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

    private fun migrate1To2(obj: JsonObject): JsonObject {
        val m = obj.toMutableMap()

        // Running plan: rename durationHmsText -> durationMinutesText (whole minutes).
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

                // Drop legacy key if present.
                mm.remove("durationHmsText")
                JsonObject(mm)
            }
            m["runningPlanEntries"] = JsonArray(converted)
        }

        m["v"] = JsonPrimitive(2)
        return JsonObject(m)
    }


    private fun parseLegacyDurationToMinutes(raw: String): Int? {
        val s = raw.trim()
        if (s.isBlank()) return null

        // If it already looks like plain minutes - keep it.
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
        // Round up to whole minutes.
        return (totalSeconds + 59) / 60
    }
    private fun migrate2To3(obj: JsonObject): JsonObject {
        val m = obj.toMutableMap()

        // Running plan: durationMinutesText (whole minutes) -> durationHhMmText (HH:MM digits).
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

    private fun minutesToHhMmDigits(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val h = (safe / 60).coerceIn(0, 99)
        val m = (safe % 60).coerceIn(0, 59)
        return String.format(Locale.US, "%02d%02d", h, m)
    }


    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        val raw = context.appStateDataStore.data.first()[key] ?: return@withContext AppState()
        decodeFromJson(raw) ?: AppState()
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val raw = encodeToJson(state)
        context.appStateDataStore.edit { prefs ->
            prefs[key] = raw
        }
    }

}

/* ---------------------------
   DTOs (JSON)
---------------------------- */

private const val CURRENT_SCHEMA_VERSION = 3

@Serializable
private data class AppStateDto(
    @SerialName("v")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val tasks: List<TaskDto> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
    val suppressedRecurrences: List<String> = emptyList(),
    val anthropometry: List<AnthropometryDto> = emptyList(),
    val calorieGoalChanges: List<CalorieGoalChangeDto> = emptyList(),
    val runningPlanApproved: Boolean = false,
    val runningPlanEntries: List<RunningPlanEntryDto> = emptyList(),

    val foodLog: List<FoodEntryDto> = emptyList(),)


@Serializable
private data class RunningPlanEntryDto(
    val dateEpochDay: Long,
    val distanceKmText: String = "",
    val durationHhMmText: String = "",
    val paceText: String = "",
    val taskId: Long? = null,
)

@Serializable
private data class TaskDto(
    val id: Long,
    val order: Int,
    val dateEpochDay: Long? = null,
    val timeSecondOfDay: Int? = null,
    val description: String,
    val colorArgb: Long? = null,
    val hasSubtasks: Boolean = false,
    val isDone: Boolean = false,
    val repeatRule: RepeatRuleDto? = null,
    val originTaskId: Long? = null,
)

@Serializable
private data class SubtaskDto(
    val id: Long,
    val order: Int,
    val taskId: Long,
    val description: String,
    val colorArgb: Long? = null,
    val isDone: Boolean = false,
    val repeatRule: RepeatRuleDto? = null,
    val originSubtaskId: Long? = null,
)

@Serializable
private data class RepeatRuleDto(
    val freq: String,
    val interval: Int = 1,
    val weekDaysIso: List<Int> = emptyList(), // 1..7 (Mon..Sun)
    val dayOfMonth: Int? = null,
)


@Serializable
private data class AnthropometryDto(
    val dateEpochDay: Long,
    val armCm: Double? = null,
    val chestCm: Double? = null,
    val underChestCm: Double? = null,
    val waistCm: Double? = null,
    val bellyCm: Double? = null,
    val hipsCm: Double? = null,
    val thighCm: Double? = null,
    val weightKg: Double? = null,
)

@Serializable
private data class CalorieGoalChangeDto(
    val dateEpochDay: Long,
    val kcal: Int,
)

@Serializable
private data class FoodEntryDto(
    val id: Long,
    val dateEpochDay: Long,
    val title: String,
    val kcal: Int,
)


/* ---------------------------
   Mapping
---------------------------- */

private fun AppState.toDto(): AppStateDto = AppStateDto(
    tasks = tasks.map { it.toDto() },
    subtasks = subtasks.map { it.toDto() },
    suppressedRecurrences = suppressedRecurrences.toList(),
    anthropometry = anthropometry.map { it.toDto() },
    calorieGoalChanges = calorieGoalChanges.map { it.toDto() },
    foodLog = foodLog.map { it.toDto() },
    runningPlanApproved = runningPlanApproved,
    runningPlanEntries = runningPlanEntries.map { it.toDto() },
    )

private fun AppStateDto.toDomain(): AppState = AppState(
    tasks = tasks.map { it.toDomain() },
    subtasks = subtasks.map { it.toDomain() },
    suppressedRecurrences = suppressedRecurrences.toSet(),
    anthropometry = anthropometry.map { it.toDomain() },
    calorieGoalChanges = calorieGoalChanges.map { it.toDomain() },
    foodLog = foodLog.map { it.toDomain() },
    runningPlanApproved = runningPlanApproved,
    runningPlanEntries = runningPlanEntries.map { it.toDomain() },
    )

private fun Task.toDto(): TaskDto = TaskDto(
    id = id,
    order = order,
    dateEpochDay = date?.toEpochDay(),
    timeSecondOfDay = time?.toSecondOfDay(),
    description = description,
    colorArgb = colorArgb,
    hasSubtasks = hasSubtasks,
    isDone = isDone,
    repeatRule = repeatRule?.toDto(),
    originTaskId = originTaskId
)

private fun TaskDto.toDomain(): Task = Task(
    id = id,
    order = order,
    date = dateEpochDay?.let { LocalDate.ofEpochDay(it) },
    time = timeSecondOfDay?.let { LocalTime.ofSecondOfDay(it.toLong()) },
    description = description,
    colorArgb = colorArgb,
    hasSubtasks = hasSubtasks,
    isDone = isDone,
    repeatRule = repeatRule?.toDomain(),
    originTaskId = originTaskId
)

private fun Subtask.toDto(): SubtaskDto = SubtaskDto(
    id = id,
    order = order,
    taskId = taskId,
    description = description,
    colorArgb = colorArgb,
    isDone = isDone,
    repeatRule = repeatRule?.toDto(),
    originSubtaskId = originSubtaskId
)

private fun SubtaskDto.toDomain(): Subtask = Subtask(
    id = id,
    order = order,
    taskId = taskId,
    description = description,
    colorArgb = colorArgb,
    isDone = isDone,
    repeatRule = repeatRule?.toDomain(),
    originSubtaskId = originSubtaskId
)

private fun RepeatRule.toDto(): RepeatRuleDto = RepeatRuleDto(
    freq = freq.name,
    interval = interval,
    weekDaysIso = weekDays.map { it.value },
    dayOfMonth = dayOfMonth
)

private fun RepeatRuleDto.toDomain(): RepeatRule = RepeatRule(
    freq = RepeatFreq.valueOf(freq),
    interval = interval,
    weekDays = weekDaysIso.map { DayOfWeek.of(it) }.toSet(),
    dayOfMonth = dayOfMonth
)

private fun AnthropometryEntry.toDto(): AnthropometryDto = AnthropometryDto(
    dateEpochDay = date.toEpochDay(),
    armCm = armCm,
    chestCm = chestCm,
    underChestCm = underChestCm,
    waistCm = waistCm,
    bellyCm = bellyCm,
    hipsCm = hipsCm,
    thighCm = thighCm,
    weightKg = weightKg,
)

private fun AnthropometryDto.toDomain(): AnthropometryEntry = AnthropometryEntry(
    date = LocalDate.ofEpochDay(dateEpochDay),
    armCm = armCm,
    chestCm = chestCm,
    underChestCm = underChestCm,
    waistCm = waistCm,
    bellyCm = bellyCm,
    hipsCm = hipsCm,
    thighCm = thighCm,
    weightKg = weightKg,
)

private fun CalorieGoalChange.toDto(): CalorieGoalChangeDto = CalorieGoalChangeDto(
    dateEpochDay = date.toEpochDay(),
    kcal = kcal
)

private fun CalorieGoalChangeDto.toDomain(): CalorieGoalChange = CalorieGoalChange(
    date = LocalDate.ofEpochDay(dateEpochDay),
    kcal = kcal
)

private fun FoodEntry.toDto(): FoodEntryDto = FoodEntryDto(
    id = id,
    dateEpochDay = date.toEpochDay(),
    title = title,
    kcal = kcal
)

private fun FoodEntryDto.toDomain(): FoodEntry = FoodEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    kcal = kcal
)

private fun RunningPlanEntry.toDto(): RunningPlanEntryDto = RunningPlanEntryDto(
    dateEpochDay = date.toEpochDay(),
    distanceKmText = distanceKmText,
    durationHhMmText = durationHhMmText,
    paceText = paceText,
    taskId = taskId,
)

private fun RunningPlanEntryDto.toDomain(): RunningPlanEntry = RunningPlanEntry(
    date = LocalDate.ofEpochDay(dateEpochDay),
    distanceKmText = distanceKmText,
    durationHhMmText = durationHhMmText,
    paceText = paceText,
    taskId = taskId,
)
