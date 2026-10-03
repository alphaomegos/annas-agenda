package com.alphaomegos.annasagenda

import java.time.DayOfWeek

/**
 * What the shared task-list components need in order to render a day.
 *
 * Declaring it as a contract lets those components accept either the whole
 * [AppState] or a narrow slice, without every caller having to hold the full
 * state just to satisfy a parameter type.
 */
interface DateTasksData {
    val tasks: List<Task>
    val subtasks: List<Subtask>
    val suppressedRecurrences: Set<String>
    val counters: List<Counter>
}

data class AppState(
    override val tasks: List<Task> = emptyList(),
    override val subtasks: List<Subtask> = emptyList(),

    // Keys like: "T:<originTaskId>:<epochDay>", "S:<originSubtaskId>:<epochDay>"
    override val suppressedRecurrences: Set<String> = emptySet(),

    // Per-day body measurements (any subset of fields can be filled).
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val anthropometryEnabledFieldIds: Set<String> = defaultAnthropometryFieldIds(),

    // Calorimeter
    val calorieGoalChanges: List<CalorieGoalChange> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),

    // Running plan ("On the run")
    val runningPlanApproved: Boolean = false,
    val runningPlanEntries: List<RunningPlanEntry> = emptyList(),
    val runningMode: RunningMode = RunningMode.PLAN,
    val runningWorkouts: List<RunningWorkout> = emptyList(),

    // Counters
    override val counters: List<Counter> = emptyList(),

    // Main menu ordering (stable ids like "calendar", "new_task", ...).
    val mainMenuOrder: List<String> = emptyList(),

    // Hidden main menu items (stable ids like "calendar", "new_task", ...).
    val mainMenuHiddenIds: Set<String> = emptySet(),

    // Light, dark, or whatever the phone is doing.
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,

    // The lowest id never yet handed out. Only ever goes up, so that an id
    // belonging to something deleted is never given to something new — see
    // nextIdFor.
    val idHighWater: Long = 0L,

    // "Undone" lamp state.
    val undoneLampMuted: Boolean = false,

    // How many days back the "Undone" screen and the lamp look for debts.
    // UNDONE_HORIZON_UNLIMITED means no limit.
    val undoneHorizonDays: Int = DEFAULT_UNDONE_HORIZON_DAYS,

    // Reading / media
    val readingBooks: List<ReadingBook> = emptyList(),
    val readingMovies: List<ReadingMovie> = emptyList(),
    val readingSeries: List<ReadingSeries> = emptyList(),
    val readingSessions: List<ReadingSession> = emptyList(),

    // A session that has started and not yet been written down. Saved, so that
    // the process being reclaimed mid-chapter does not throw the time away.
    val activeReading: ActiveReading? = null,

    // A session that ended because its book left the Now shelf, waiting for
    // the user to say whether to keep it. Saved for the same reason as the one
    // above: the question can outlive the process that asked it.
    val pendingReadingSession: ReadingSession? = null,

    // "Don't ask again, just record it." Set from the dialog that asks.
    val autoRecordInterruptedReading: Boolean = false,

    val readingMediaFilter: ReadingMediaFilter = ReadingMediaFilter(),

    val readingPlansPrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingNowPrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingDonePrefs: ReadingTabPrefs = ReadingTabPrefs(),
    val readingAbandonedPrefs: ReadingTabPrefs = ReadingTabPrefs(),

    // --- Schema 6 (0137): settings, all at once, and the diet. ---------------
    // Every default is what the app did before these existed, so the step to
    // schema 6 changes nothing anybody can see. Screens pick them up one by one.

    // Anthropometry: the forecast block (5.1), the list of measurements under
    // the chart (5.3), and the last range chosen, kept across leaving the screen.
    val anthropometryShowForecast: Boolean = true,
    val anthropometryShowEntries: Boolean = false,
    val anthropometryRange: AnthropometryRange = AnthropometryRange.MONTH,
    val anthropometryCustomRange: DateWindow? = null,

    // Calorimeter (6.1).
    val calorimeterShowDailyGoal: Boolean = true,
    val calorimeterShowWeeklyGoal: Boolean = true,
    val calorimeterShowPotentialLoss: Boolean = true,

    // Calendar day marks (6.3). The circle for a measurement is what it drew.
    val calendarBadges: Set<CalendarBadge> = setOf(CalendarBadge.ANTHROPOMETRY),

    // Food library (6.4): picking from categories instead of typing.
    val foodLibraryVisible: Boolean = false,

    // Diet (6.2): a fixed set of dishes per day of the week. See DietItem for
    // why the plan is never written into the food log.
    val dietEnabled: Boolean = false,
    val dietPlan: Map<DayOfWeek, List<DietItem>> = emptyMap(),

    // --- Schema 7 (0140): what the diet, the library and the notifications
    // still needed. Defaults again change nothing anybody can see.

    // Diet: whether a past day still shows the dishes nobody ticked, greyed
    // and tickable. Off, a past day shows only what was eaten.
    val dietShowPastUnticked: Boolean = true,

    // Food library: what the user put on the shelves. Kept for a later
    // backup-to-standard-library export; see FoodLibraryUserItem.
    val foodLibraryUserItems: List<FoodLibraryUserItem> = emptyList(),

    // Daily summaries and task reminders. Default: silence.
    val notifications: NotificationSettings = NotificationSettings(),

    // How the home-screen widget looks (03.10). No schema step: a display
    // setting with a default, which an older build simply does not keep.
    val widgetStyle: WidgetStyle = WidgetStyle(),
) : DateTasksData