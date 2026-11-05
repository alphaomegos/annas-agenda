package com.alphaomegos.annasagenda.support

import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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
    // Schema 7.
    val dietShowPastUnticked: Boolean = true,
    val foodLibraryUserItems: List<FoodLibraryUserItem> = emptyList(),
    // The day's spending and the profile, shown in the new look (01.11): what the weight
    // forecast counts — the norm, steps, runs — and the profile they come from.
    val newLook: Boolean = true,
    val profileSex: ProfileSex? = null,
    val heightCm: Int? = null,
    val profileBirthYear: Int? = null,
    val dailyNormKcal: Int? = null,
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val stepDays: List<StepDay> = emptyList(),
    val runningWorkouts: List<RunningWorkout> = emptyList(),
    // Other sports (05.11): the day's spending counts their kilocalories.
    val sportWorkouts: List<SportWorkout> = emptyList(),
    val runningPlanEntries: List<RunningPlanEntry> = emptyList(),
    val runningDoneTaskIds: Set<Long> = emptySet(),
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
    // Other sports (05.11): the forecast counts them like the runs.
    val sportWorkouts: List<SportWorkout> = emptyList(),
    // What the screen shows and the range it last showed (schema 6).
    val anthropometryShowForecast: Boolean = true,
    val anthropometryShowEntries: Boolean = false,
    val anthropometryRange: AnthropometryRange = AnthropometryRange.MONTH,
    val anthropometryCustomRange: DateWindow? = null,
    // The new forecast (28.10): steps, the profile, and which forecast.
    val stepDays: List<StepDay> = emptyList(),
    val profileSex: ProfileSex? = null,
    val heightCm: Int? = null,
    val profileBirthYear: Int? = null,
    val weightForecastMethod: ForecastMethod = ForecastMethod.NEW,
    // The screens' look (28.10): the new one, or Summer 2026's.
    val newLook: Boolean = true,
    // The plan's runs and which of their tasks are ticked; the own norm (28.10).
    val runningPlanEntries: List<RunningPlanEntry> = emptyList(),
    val runningDoneTaskIds: Set<Long> = emptySet(),
    val dailyNormKcal: Int? = null,
    val anthropometryCarryLast: Boolean = true,
)

/**
 * Wraps a single list on purpose: keeping the field name lets CountersScreen
 * go on reading `state.counters`, so switching it over is a one-line diff.
 */
data class CountersSlice(
    val counters: List<Counter> = emptyList(),
    // The new look (03.11): the counters on a grounded surface, «+ Counter» in the title row.
    val newLook: Boolean = true,
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
    override val doneTasksLast: Boolean = false,
    override val doneSubtasksLast: Boolean = false,
    override val askBeforeDeleting: Boolean = true,
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
    dietShowPastUnticked = state.dietShowPastUnticked,
    foodLibraryUserItems = state.foodLibraryUserItems,
    newLook = state.dayScreenStyle == DayScreenStyle.NEW,
    profileSex = state.profileSex,
    heightCm = state.stepsHeightCm,
    profileBirthYear = state.profileBirthYear,
    dailyNormKcal = state.dailyNormKcal,
    anthropometry = state.anthropometry,
    stepDays = state.stepDays,
    runningWorkouts = state.runningWorkouts,
    sportWorkouts = state.sportWorkouts,
    runningPlanEntries = state.runningPlanEntries,
    runningDoneTaskIds = state.runningPlanEntries.mapNotNull { it.taskId }.toSet()
        .let { linked -> state.tasks.filter { it.isDone && it.id in linked }.map { it.id }.toSet() },
)

fun anthropometrySliceOf(state: AppState): AnthropometrySlice = AnthropometrySlice(
    anthropometry = state.anthropometry,
    anthropometryEnabledFieldIds = state.anthropometryEnabledFieldIds,
    calorieGoalChanges = state.calorieGoalChanges,
    foodLog = state.foodLog,
    runningWorkouts = state.runningWorkouts,
    sportWorkouts = state.sportWorkouts,
    anthropometryShowForecast = state.anthropometryShowForecast,
    anthropometryShowEntries = state.anthropometryShowEntries,
    anthropometryRange = state.anthropometryRange,
    anthropometryCustomRange = state.anthropometryCustomRange,
    stepDays = state.stepDays,
    profileSex = state.profileSex,
    heightCm = state.stepsHeightCm,
    profileBirthYear = state.profileBirthYear,
    weightForecastMethod = state.weightForecastMethod,
    newLook = state.dayScreenStyle == DayScreenStyle.NEW,
    runningPlanEntries = state.runningPlanEntries,
    runningDoneTaskIds = state.runningPlanEntries.mapNotNull { it.taskId }.toSet()
        .let { linked -> state.tasks.filter { it.isDone && it.id in linked }.map { it.id }.toSet() },
    dailyNormKcal = state.dailyNormKcal,
    anthropometryCarryLast = state.anthropometryCarryLast,
)

fun countersSliceOf(state: AppState): CountersSlice = CountersSlice(
    counters = state.counters,
    newLook = state.dayScreenStyle == DayScreenStyle.NEW,
)

fun undoneSliceOf(state: AppState): UndoneSlice = UndoneSlice(
    tasks = state.tasks,
    subtasks = state.subtasks,
    suppressedRecurrences = state.suppressedRecurrences,
    counters = state.counters,
    undoneLampMuted = state.undoneLampMuted,
    undoneHorizonDays = state.undoneHorizonDays,
    doneTasksLast = state.doneTasksLast,
    doneSubtasksLast = state.doneSubtasksLast,
    askBeforeDeleting = state.askBeforeDeleting,
)

/*
 * The menu, the month and the day read slices of their own (05.11, the internals review): with the
 * whole state each was drawn again at every change anywhere — the steps coming in, a meal written
 * down, a track played. A slice holds what its screen reads, under the same names, so the screen
 * reads it as it read the state.
 */

/** What the main menu reads: its order, what is hidden, the narrow style, the undone lamp's settings. */
data class MainMenuSlice(
    val mainMenuHiddenIds: Set<String> = emptySet(),
    val mainMenuNarrow: MainMenuNarrowStyle = MainMenuNarrowStyle.LIST,
    val mainMenuOrder: List<String> = emptyList(),
    val undoneHorizonDays: Int = DEFAULT_UNDONE_HORIZON_DAYS,
    val undoneLampMuted: Boolean = false,
)

/** What the month reads: the tasks for its dots, the badges and what they are made of, the look. */
data class MonthSlice(
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val suppressedRecurrences: Set<String> = emptySet(),
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),
    val stepDays: List<StepDay> = emptyList(),
    val calendarBadges: Set<CalendarBadge> = setOf(CalendarBadge.ANTHROPOMETRY),
    val calendarShowSteps: Boolean = true,
    val dayScreenStyle: DayScreenStyle = DayScreenStyle.NEW,
)

/**
 * What the day reads: the tasks as the shared DateTasksBlock takes them — so it is [DateTasksData],
 * as the undone tasks' slice is — the measurements the day shows, the look.
 */
data class DaySlice(
    override val tasks: List<Task> = emptyList(),
    override val subtasks: List<Subtask> = emptyList(),
    override val suppressedRecurrences: Set<String> = emptySet(),
    override val counters: List<Counter> = emptyList(),
    override val doneTasksLast: Boolean = false,
    override val doneSubtasksLast: Boolean = false,
    override val askBeforeDeleting: Boolean = true,
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val anthropometryEnabledFieldIds: Set<String> = defaultAnthropometryFieldIds(),
    val dayScreenStyle: DayScreenStyle = DayScreenStyle.NEW,
) : DateTasksData

fun mainMenuSliceOf(state: AppState): MainMenuSlice = MainMenuSlice(
    mainMenuHiddenIds = state.mainMenuHiddenIds,
    mainMenuNarrow = state.mainMenuNarrow,
    mainMenuOrder = state.mainMenuOrder,
    undoneHorizonDays = state.undoneHorizonDays,
    undoneLampMuted = state.undoneLampMuted,
)

fun monthSliceOf(state: AppState): MonthSlice = MonthSlice(
    tasks = state.tasks,
    subtasks = state.subtasks,
    suppressedRecurrences = state.suppressedRecurrences,
    anthropometry = state.anthropometry,
    foodLog = state.foodLog,
    stepDays = state.stepDays,
    calendarBadges = state.calendarBadges,
    calendarShowSteps = state.calendarShowSteps,
    dayScreenStyle = state.dayScreenStyle,
)

fun daySliceOf(state: AppState): DaySlice = DaySlice(
    tasks = state.tasks,
    subtasks = state.subtasks,
    suppressedRecurrences = state.suppressedRecurrences,
    counters = state.counters,
    doneTasksLast = state.doneTasksLast,
    doneSubtasksLast = state.doneSubtasksLast,
    askBeforeDeleting = state.askBeforeDeleting,
    anthropometry = state.anthropometry,
    anthropometryEnabledFieldIds = state.anthropometryEnabledFieldIds,
    dayScreenStyle = state.dayScreenStyle,
)
