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

private val Context.appStateDataStore by preferencesDataStore(name = "app_state_store")

class AppStateStore(private val context: Context) {

    private val key = stringPreferencesKey("app_state_json")

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeToJson(state: AppState): String =
        json.encodeToString(state.toDto())

    fun decodeFromJson(raw: String): AppState? =
        runCatching { json.decodeFromString<AppStateDto>(raw).toDomain() }.getOrNull()

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        val raw = context.appStateDataStore.data.first()[key] ?: return@withContext AppState()
        decodeFromJson(raw) ?: AppState()
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(state.toDto())
        context.appStateDataStore.edit { prefs ->
            prefs[key] = raw
        }
    }

}

/* ---------------------------
   DTOs (JSON)
---------------------------- */

@Serializable
private data class AppStateDto(
    val v: Int = 1,
    val tasks: List<TaskDto> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
    val suppressedRecurrences: List<String> = emptyList(),
    val anthropometry: List<AnthropometryDto> = emptyList(),
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


/* ---------------------------
   Mapping
---------------------------- */

private fun AppState.toDto(): AppStateDto = AppStateDto(
    tasks = tasks.map { it.toDto() },
    subtasks = subtasks.map { it.toDto() },
    suppressedRecurrences = suppressedRecurrences.toList(),
    anthropometry = anthropometry.map { it.toDto() },
)

private fun AppStateDto.toDomain(): AppState = AppState(
    tasks = tasks.map { it.toDomain() },
    subtasks = subtasks.map { it.toDomain() },
    suppressedRecurrences = suppressedRecurrences.toSet(),
    anthropometry = anthropometry.map { it.toDomain() },
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