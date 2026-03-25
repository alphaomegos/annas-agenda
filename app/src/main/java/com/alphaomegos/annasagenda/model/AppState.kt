package com.alphaomegos.annasagenda

data class AppState(
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),

    // Keys like: "T:<originTaskId>:<epochDay>", "S:<originSubtaskId>:<epochDay>"
    val suppressedRecurrences: Set<String> = emptySet(),

    // Per-day body measurements (any subset of fields can be filled).
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val anthropometryEnabledFieldIds: Set<String> = defaultAnthropometryFieldIds(),

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

    // Hidden main menu items (stable ids like "calendar", "new_task", ...).
    val mainMenuHiddenIds: Set<String> = emptySet(),

    // "Undone" lamp state.
    val undoneLampMuted: Boolean = false,

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