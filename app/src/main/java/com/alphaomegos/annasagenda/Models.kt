package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class RepeatFreq {
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class RepeatRule(
    val freq: RepeatFreq,
    val interval: Int = 1,                      // every N days/weeks/months
    val weekDays: Set<DayOfWeek> = emptySet(),  // for WEEKLY
    val dayOfMonth: Int? = null,                // for MONTHLY (1..31)
)

data class Task(
    val id: Long,
    val order: Int = 0,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val description: String,
    val colorArgb: Long? = null,
    val hasSubtasks: Boolean = false,
    val isDone: Boolean = false,
    val linkedManualCounterId: Long? = null,

    // Recurrence is defined only on template tasks (originTaskId == null).
    val repeatRule: RepeatRule? = null,

    // If not null -> this task instance was generated from a template task with this id.
    val originTaskId: Long? = null,
)

data class Subtask(
    val id: Long,
    val order: Int = 0,
    val taskId: Long,
    val description: String,
    val colorArgb: Long? = null,
    val isDone: Boolean = false,

    // Recurrence is defined only on template subtasks (originSubtaskId == null).
    val repeatRule: RepeatRule? = null,

    // If not null -> this subtask instance was generated from a template subtask with this id.
    val originSubtaskId: Long? = null,
)

data class AnthropometryEntry(
    val date: LocalDate,
    val armCm: Double? = null,
    val chestCm: Double? = null,
    val underChestCm: Double? = null,
    val waistCm: Double? = null,
    val bellyCm: Double? = null,
    val hipsCm: Double? = null,
    val thighCm: Double? = null,
    val weightKg: Double? = null,
) {
    fun hasAnyValue(): Boolean =
        armCm != null ||
                chestCm != null ||
                underChestCm != null ||
                waistCm != null ||
                bellyCm != null ||
                hipsCm != null ||
                thighCm != null ||
                weightKg != null
}

/** Daily goal changes: new value applies from [date] and all future days. */
data class CalorieGoalChange(
    val date: LocalDate,
    val kcal: Int,
)

data class FoodEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val kcal: Int,
)

/** Planned/actual running entry for a specific date. Values are stored as text to keep UX stable. */
data class RunningPlanEntry(
    val date: LocalDate,
    val distanceKmText: String = "",
    /** User-entered time as 4 digits: HHMM (e.g., "0123" -> displayed as 01:23). */
    val durationHhMmText: String = "",
    /** User-entered pace as 4 digits: MMSS (e.g., "0915" -> displayed as 09'15"). */
    val paceText: String = "",
    val taskId: Long? = null,
)

/* ---------------------------
   Reading / Media
---------------------------- */

enum class ReadingShelf {
    PLANS,
    NOW,
    DONE,
    ABANDONED,
}

enum class ReadingViewMode {
    GRID,
    LIST,
}

enum class ReadingSortField {
    AUTHOR,
    TITLE,
    PAGES,
    YEAR,
    RELEASE_YEAR,
}

enum class ReadingMediaType {
    BOOKS,
    MOVIES,
    SERIES,
}

data class ReadingMediaFilter(
    val showBooks: Boolean = true,
    val showMovies: Boolean = true,
    val showSeries: Boolean = true,
)

data class ReadingSort(
    val field: ReadingSortField = ReadingSortField.TITLE,
    val ascending: Boolean = true,
)

data class ReadingTabPrefs(
    val viewMode: ReadingViewMode = ReadingViewMode.GRID,
    val sort: ReadingSort = ReadingSort(),
)

data class ReadingBook(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val author: String = "",
    val title: String,
    val coverUri: String? = null,

    val totalPages: Int,
    val currentPage: Int = 0,

    // Filled automatically when moved to DONE.
    val yearRead: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

data class ReadingMovie(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val title: String,
    val coverUri: String? = null,

    // Informational fields.
    val releaseYear: Int? = null,
    val translation: String = "",

    // Filled automatically when moved to DONE.
    val yearWatched: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

data class ReadingSeries(
    val id: Long,
    val shelf: ReadingShelf = ReadingShelf.PLANS,

    val title: String,
    val coverUri: String? = null,

    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,

    // Filled automatically when moved to DONE.
    val yearWatched: Int? = null,

    // Filled automatically when moved to ABANDONED.
    val yearAbandoned: Int? = null,

    val createdAtEpochMillis: Long = 0L,
)

/**
 * Reading session attached to a book.
 * Used to compute remaining time estimate based on the latest session speed.
 */
data class ReadingSession(
    val id: Long,
    val bookId: Long,

    val startedAtEpochMillis: Long,
    val durationMinutes: Int,

    val startPage: Int,
    val endPage: Int,

    val createdAtEpochMillis: Long = startedAtEpochMillis,
)

sealed interface Counter {
    val id: Long
    val title: String
}

data class DateRangeCounter(
    override val id: Long,
    override val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) : Counter

data class ManualCounter(
    override val id: Long,
    override val title: String,
    val balance: Int,
) : Counter

data class AppState(
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),

    // Keys like: "T:<originTaskId>:<epochDay>", "S:<originSubtaskId>:<epochDay>"
    val suppressedRecurrences: Set<String> = emptySet(),

    // Per-day body measurements (any subset of fields can be filled).
    val anthropometry: List<AnthropometryEntry> = emptyList(),

    // Calorimeter
    val calorieGoalChanges: List<CalorieGoalChange> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),

    // Running plan ("On the run")
    val runningPlanApproved: Boolean = false,
    val runningPlanEntries: List<RunningPlanEntry> = emptyList(),

    // Counters
    val counters: List<Counter> = emptyList(),

    // Main menu ordering (stable ids like "calendar", "new_task", ...).
    val mainMenuOrder: List<String> = emptyList(),

    // Reading / media
    val readingBooks: List<ReadingBook> = emptyList(),
    val readingMovies: List<ReadingMovie> = emptyList(),
    val readingSeries: List<ReadingSeries> = emptyList(),
    val readingSessions: List<ReadingSession> = emptyList(),

    val readingMediaFilter: ReadingMediaFilter = ReadingMediaFilter(),

    val readingPlansPrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingNowPrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingDonePrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingAbandonedPrefs: ReadingTabPrefs = ReadingTabPrefs(),
)