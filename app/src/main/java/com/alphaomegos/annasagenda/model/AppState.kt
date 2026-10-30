package com.alphaomegos.annasagenda.model

import java.time.DayOfWeek
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

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

    /** Done tasks go to the bottom of their day (06.10, a setting). */
    val doneTasksLast: Boolean get() = false

    /** Done subtasks go to the bottom of their task (16.10, a setting of its own). */
    val doneSubtasksLast: Boolean get() = false

    /** Deleting a task or a subtask asks first (30.10, a setting; on unless turned off). */
    val askBeforeDeleting: Boolean get() = true
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
    val palette: AppPalette = AppPalette.GREEN,
    val backStyle: BackStyle = BackStyle.ARROW,
    val mainMenuNarrow: MainMenuNarrowStyle = MainMenuNarrowStyle.LIST,
    val dayScreenStyle: DayScreenStyle = DayScreenStyle.NEW,

    // The lowest id never yet handed out. Only ever goes up, so that an id
    // belonging to something deleted is never given to something new — see
    // nextIdFor.
    val idHighWater: Long = 0L,

    // "Undone" lamp state.
    val undoneLampMuted: Boolean = false,

    // Done tasks at the bottom of their day (06.10). A setting with a
    // default, so no schema step.
    override val doneTasksLast: Boolean = false,
    // Done subtasks at the bottom of their task (16.10). A setting of its
    // own, with a default, so no schema step.
    override val doneSubtasksLast: Boolean = false,
    // Deleting a task or a subtask asks first (30.10, Eduard: a setting —
    // off, they go at the tap). With a default, so no schema step.
    override val askBeforeDeleting: Boolean = true,

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
    // The shelf of series waiting for the next season (15.10). A setting.
    val readingWaitingPrefs: ReadingTabPrefs = ReadingTabPrefs(),

    // --- Schema 6 (0137): settings, all at once, and the diet. ---------------
    // Every default is what the app did before these existed, so the step to
    // schema 6 changes nothing anybody can see. Screens pick them up one by one.

    // Anthropometry: the forecast block (5.1), the list of measurements under
    // the chart (5.3), and the last range chosen, kept across leaving the screen.
    val anthropometryShowForecast: Boolean = true,
    val anthropometryShowEntries: Boolean = false,
    val anthropometryRange: AnthropometryRange = AnthropometryRange.MONTH,
    val anthropometryCustomRange: DateWindow? = null,
    // Steps (19.10): the days kept, the walker's height for distance and
    // energy, and the range the Steps tab last showed.
    val stepDays: List<StepDay> = emptyList(),
    // The user's height — one for the whole app since 28.10 (the profile):
    // steps' distance, the energy formulas, anthropometry. Named for where it
    // was first asked; the store keeps the name so nothing moves.
    val stepsHeightCm: Int? = null,
    // The rest of the profile (28.10): sex and year of birth, for the
    // body's own energy use in the weight forecast.
    val profileSex: ProfileSex? = null,
    val profileBirthYear: Int? = null,
    // How anthropometry's forecast is worked out (28.10): the new energy balance, or Summer 2026's.
    val weightForecastMethod: ForecastMethod = ForecastMethod.NEW,
    // The user's own daily norm (28.10), instead of the formula's; null = the formula.
    val dailyNormKcal: Int? = null,
    // A period with no measurement shows the last one before it as a level line (29.10).
    val anthropometryCarryLast: Boolean = true,
    val stepsRange: AnthropometryRange = AnthropometryRange.MONTH,
    val stepsCustomRange: DateWindow? = null,
    // A day's steps on the month, under its number (19.10); on unless turned off.
    val calendarShowSteps: Boolean = true,
    val stepsChartStyle: StepsChartStyle = StepsChartStyle.BARS,
    // The period's best of each friend on the chart too (29.10), beside one's own.
    val stepsFriendsBest: Boolean = true,
    // The steps widget (25.10): its second line, whether it shows the record, and the day's goal.
    val stepsWidgetLine: StepsWidgetLine = StepsWidgetLine.YESTERDAY,
    val stepsWidgetRecord: Boolean = true,
    val stepsGoal: Int = 10_000,

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

    // --- Schema 8 (04.10): the metro. The schemes the user wrote down are
    // data nothing else holds — the reason for the number. Which scheme the
    // metro screen uses is a setting that rides along; see MetroSelection.
    val metroSchemes: List<MetroScheme> = emptyList(),
    val metroSelection: String? = null,

    // --- Schema 9 (05.10): travel. The trips and the user's own countries
    // are data nothing else holds; how the screen was left rides along.
    val travelCountries: List<TravelCountryRecord> = emptyList(),
    val travelView: TravelViewPrefs = TravelViewPrefs(),

    // --- Schema 10 (06.10): flights. The log, and the user's own names for
    // cities and airlines and their home cities, are data nothing else
    // holds; the section's settings ride along.
    val flights: List<Flight> = emptyList(),
    val flightPrefs: FlightPrefs = FlightPrefs(),
    // By airport code: the city as the user names it.
    val flightCityNames: Map<String, String> = emptyMap(),
    // By airline code.
    val flightAirlineNames: Map<String, String> = emptyMap(),
    // Places never suggested to the travel section (flightPlaceKey).
    val flightHomes: Set<String> = emptySet(),
    // The metro section's own settings (15.10): how its editor behaves. Settings.
    val metroPrefs: MetroPrefs = MetroPrefs(),
    // The routes looked up lately, of every scheme (15.10): schema 14.
    val metroRecent: List<MetroRecentRoute> = emptyList(),
    // The music section's own settings (16.10). Settings.
    val musicPrefs: MusicPrefs = MusicPrefs(),
) : DateTasksData