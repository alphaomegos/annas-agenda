package com.alphaomegos.annasagenda

/**
 * Feature-scoped views of [AppState].
 *
 * Every screen used to collect the whole 22-field AppState, so a change to any
 * field woke up every screen reading it. A slice holds only the fields one
 * feature actually reads, which means an unrelated change produces an equal
 * slice and the StateFlow built from it never emits.
 *
 * Field names deliberately match AppState, so a screen switching to a slice
 * changes only the line where it collects — the body keeps reading
 * `state.foodLog` and friends unchanged.
 *
 * These are plain functions over plain data so the "ignores unrelated changes"
 * property can be asserted by a JVM test rather than inferred.
 */

data class CalorimeterSlice(
    val calorieGoalChanges: List<CalorieGoalChange> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),
    // What the screen shows (6.1) and what it offers (6.2, 6.4), schema 6.
    val calorimeterShowDailyGoal: Boolean = true,
    val calorimeterShowWeeklyGoal: Boolean = true,
    val calorimeterShowPotentialLoss: Boolean = true,
    val foodLibraryVisible: Boolean = false,
    val dietEnabled: Boolean = false,
    val dietPlan: Map<java.time.DayOfWeek, List<DietItem>> = emptyMap(),
)

data class AnthropometrySlice(
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val anthropometryEnabledFieldIds: Set<String> = emptySet(),
    val calorieGoalChanges: List<CalorieGoalChange> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),
    // The runs are here because the month's projection counts them. Adding a
    // run has to move that number, and a slice that did not carry them would
    // not emit when one was written down.
    val runningWorkouts: List<RunningWorkout> = emptyList(),
    // What the screen shows and the range it last showed (schema 6).
    val anthropometryShowForecast: Boolean = true,
    val anthropometryShowEntries: Boolean = false,
    val anthropometryRange: AnthropometryRange = AnthropometryRange.MONTH,
    val anthropometryCustomRange: DateWindow? = null,
)

/**
 * Wraps a single list on purpose: keeping the field name lets CountersScreen
 * go on reading `state.counters`, so switching it over is a one-line diff.
 */
data class CountersSlice(
    val counters: List<Counter> = emptyList(),
)

/**
 * Implements [DateTasksData] because UndoneTasksScreen hands its state to the
 * shared DateTasksBlock, which needs subtasks and counters as well as the two
 * fields the screen reads itself.
 */
data class UndoneSlice(
    override val tasks: List<Task> = emptyList(),
    override val subtasks: List<Subtask> = emptyList(),
    override val suppressedRecurrences: Set<String> = emptySet(),
    override val counters: List<Counter> = emptyList(),
    val undoneLampMuted: Boolean = false,
    val undoneHorizonDays: Int = DEFAULT_UNDONE_HORIZON_DAYS,
) : DateTasksData

/**
 * The question raised when a session ended because its book stopped being read.
 *
 * Carries the title as well as the session, because the dialog names the book
 * and the session only holds its id. Null when nothing is outstanding, which
 * is what the dialog reads as "do not show me".
 */
data class PendingReadingPrompt(
    val session: ReadingSession,
    val bookTitle: String,
)

fun pendingReadingPromptOf(state: AppState): PendingReadingPrompt? {
    val session = state.pendingReadingSession ?: return null

    return PendingReadingPrompt(
        session = session,
        bookTitle = state.readingBooks.firstOrNull { it.id == session.bookId }?.title.orEmpty(),
    )
}

fun calorimeterSliceOf(state: AppState): CalorimeterSlice = CalorimeterSlice(
    calorieGoalChanges = state.calorieGoalChanges,
    foodLog = state.foodLog,
    calorimeterShowDailyGoal = state.calorimeterShowDailyGoal,
    calorimeterShowWeeklyGoal = state.calorimeterShowWeeklyGoal,
    calorimeterShowPotentialLoss = state.calorimeterShowPotentialLoss,
    foodLibraryVisible = state.foodLibraryVisible,
    dietEnabled = state.dietEnabled,
    dietPlan = state.dietPlan,
)

fun anthropometrySliceOf(state: AppState): AnthropometrySlice = AnthropometrySlice(
    anthropometry = state.anthropometry,
    anthropometryEnabledFieldIds = state.anthropometryEnabledFieldIds,
    calorieGoalChanges = state.calorieGoalChanges,
    foodLog = state.foodLog,
    runningWorkouts = state.runningWorkouts,
    anthropometryShowForecast = state.anthropometryShowForecast,
    anthropometryShowEntries = state.anthropometryShowEntries,
    anthropometryRange = state.anthropometryRange,
    anthropometryCustomRange = state.anthropometryCustomRange,
)

fun countersSliceOf(state: AppState): CountersSlice = CountersSlice(
    counters = state.counters,
)

fun undoneSliceOf(state: AppState): UndoneSlice = UndoneSlice(
    tasks = state.tasks,
    subtasks = state.subtasks,
    suppressedRecurrences = state.suppressedRecurrences,
    counters = state.counters,
    undoneLampMuted = state.undoneLampMuted,
    undoneHorizonDays = state.undoneHorizonDays,
)
