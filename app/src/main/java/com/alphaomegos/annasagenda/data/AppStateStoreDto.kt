package com.alphaomegos.annasagenda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---------------------------
   DTOs (JSON)
---------------------------- */

@Serializable
internal data class AppStateDto(
    @SerialName("v")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    val tasks: List<TaskDto> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
    val suppressedRecurrences: List<String> = emptyList(),

    val anthropometry: List<AnthropometryDto> = emptyList(),
    val anthropometryEnabledFieldIds: List<String> = emptyList(),

    val calorieGoalChanges: List<CalorieGoalChangeDto> = emptyList(),
    val foodLog: List<FoodEntryDto> = emptyList(),

    val runningPlanApproved: Boolean = false,
    val runningPlanEntries: List<RunningPlanEntryDto> = emptyList(),

    val counters: List<CounterDto> = emptyList(),
    val mainMenuOrder: List<String> = emptyList(),
    val mainMenuHiddenIds: List<String> = emptyList(),
    val undoneLampMuted: Boolean = false,

    val travelCountries: List<TravelCountryRecordDto> = emptyList(),

    val readingBooks: List<ReadingBookDto> = emptyList(),
    val readingMovies: List<ReadingMovieDto> = emptyList(),
    val readingSeries: List<ReadingSeriesDto> = emptyList(),
    val readingSessions: List<ReadingSessionDto> = emptyList(),

    val readingMediaFilter: ReadingMediaFilterDto = ReadingMediaFilterDto(),

    val readingPlansPrefs: ReadingTabPrefsDto = ReadingTabPrefsDto(),
    val readingNowPrefs: ReadingTabPrefsDto = ReadingTabPrefsDto(),
    val readingDonePrefs: ReadingTabPrefsDto = ReadingTabPrefsDto(),
    val readingAbandonedPrefs: ReadingTabPrefsDto = ReadingTabPrefsDto(),
)

@Serializable
internal data class CounterDto(
    val id: Long,
    val kind: String,
    val title: String,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val balance: Int? = null,
)

@Serializable
internal data class RunningPlanEntryDto(
    val dateEpochDay: Long,
    val distanceKmText: String = "",
    val durationHhMmText: String = "",
    val paceText: String = "",
    val taskId: Long? = null,
    val isBonus: Boolean = false,
)
@Serializable
internal data class TaskDto(
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
    val linkedManualCounterId: Long? = null,
)

@Serializable
internal data class SubtaskDto(
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
internal data class RepeatRuleDto(
    val freq: String,
    val interval: Int = 1,
    val weekDaysIso: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
)

@Serializable
internal data class AnthropometryDto(
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
internal data class CalorieGoalChangeDto(
    val dateEpochDay: Long,
    val kcal: Int,
)

@Serializable
internal data class FoodEntryDto(
    val id: Long,
    val dateEpochDay: Long,
    val title: String,
    val kcal: Int,
)

@Serializable
internal data class TravelVisitDto(
    val year: Int,
    val month: Int,
    val cities: List<String> = emptyList(),
)

@Serializable
internal data class TravelMapPointDto(
    val x: Float,
    val y: Float,
)

@Serializable
internal data class TravelCountryRecordDto(
    val countryId: String,
    val trips: List<TravelVisitDto> = emptyList(),
    val customName: String? = null,
    val continentOverride: String? = null,
    val customMapPoint: TravelMapPointDto? = null,
    val isUserCreated: Boolean = false,
)


/* ---------------------------
   Reading DTOs
---------------------------- */

@Serializable
internal data class ReadingBookDto(
    val id: Long,
    val shelf: String = "PLANS",
    val author: String = "",
    val title: String,
    val coverUri: String? = null,
    val totalPages: Int,
    val currentPage: Int = 0,
    val yearRead: Int? = null,
    val yearAbandoned: Int? = null,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
internal data class ReadingMovieDto(
    val id: Long,
    val shelf: String = "PLANS",
    val title: String,
    val coverUri: String? = null,
    val releaseYear: Int? = null,
    val translation: String = "",
    val yearWatched: Int? = null,
    val yearAbandoned: Int? = null,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
internal data class ReadingSeriesDto(
    val id: Long,
    val shelf: String = "PLANS",
    val title: String,
    val coverUri: String? = null,
    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,
    val yearWatched: Int? = null,
    val yearAbandoned: Int? = null,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
internal data class ReadingSessionDto(
    val id: Long,
    val bookId: Long,
    val startedAtEpochMillis: Long,
    val durationMinutes: Int,
    val startPage: Int,
    val endPage: Int,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
internal data class ReadingMediaFilterDto(
    val showBooks: Boolean = true,
    val showMovies: Boolean = true,
    val showSeries: Boolean = true,
)

@Serializable
internal data class ReadingSortDto(
    val field: String = "TITLE",
    val ascending: Boolean = true,
)

@Serializable
internal data class ReadingTabPrefsDto(
    val viewMode: String = "GRID",
    val sort: ReadingSortDto = ReadingSortDto(),
)