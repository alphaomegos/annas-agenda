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
)

data class AnthropometrySlice(
    val anthropometry: List<AnthropometryEntry> = emptyList(),
    val anthropometryEnabledFieldIds: Set<String> = emptySet(),
    val calorieGoalChanges: List<CalorieGoalChange> = emptyList(),
    val foodLog: List<FoodEntry> = emptyList(),
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

fun calorimeterSliceOf(state: AppState): CalorimeterSlice = CalorimeterSlice(
    calorieGoalChanges = state.calorieGoalChanges,
    foodLog = state.foodLog,
)

fun anthropometrySliceOf(state: AppState): AnthropometrySlice = AnthropometrySlice(
    anthropometry = state.anthropometry,
    anthropometryEnabledFieldIds = state.anthropometryEnabledFieldIds,
    calorieGoalChanges = state.calorieGoalChanges,
    foodLog = state.foodLog,
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
