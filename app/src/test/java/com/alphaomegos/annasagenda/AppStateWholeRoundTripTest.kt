package com.alphaomegos.annasagenda

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * Everything the app knows, written down and read back.
 *
 * The other round-trip tests each pin one corner. This one pins the whole
 * shape at once, and it is here because of what it protects. Exporting and
 * importing is the app's own way out of trouble — it is what stands between a
 * user and a fresh start when a signing key cannot be found, or a phone is
 * replaced, or a payload turns out to be unreadable. A field that quietly
 * fails to survive the trip is not noticed until the day somebody needs the
 * trip to work.
 *
 * **Every field of AppState below is deliberately not its default.** That is
 * the whole mechanism: a field added to AppState and forgotten in the DTO, or
 * in either direction of the mapping, comes back as its default and fails the
 * comparison. A field added to AppState and forgotten *here* fails nothing,
 * so a new field belongs in this fixture before it belongs anywhere else.
 *
 * The state is built already normalised — valid field ids, tombstones whose
 * templates exist, a plan row pointing at a task that is really there —
 * because decoding tidies, and a fixture that needs tidying would be testing
 * the tidying rather than the trip.
 */
class AppStateWholeRoundTripTest {

    private val day = LocalDate.of(2026, 3, 2)

    private val template = Task(
        id = 1L,
        order = 0,
        date = day,
        time = LocalTime.of(7, 30),
        description = "water the plants",
        colorArgb = 0xFF2E7D32L,
        hasSubtasks = true,
        isDone = false,
        linkedManualCounterId = 60L,
        repeatRule = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 2,
            weekDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            weekStart = DayOfWeek.MONDAY,
        ),
    )

    private val occurrence = Task(
        id = 2L,
        order = 1,
        date = day.plusDays(3),
        time = null,
        description = "water the plants",
        colorArgb = 0xFF2E7D32L,
        hasSubtasks = true,
        isDone = true,
        originTaskId = 1L,
    )

    private val runTask = Task(id = 3L, order = 2, date = day, description = "Run 5 km")

    private val whole = AppState(
        tasks = listOf(template, occurrence, runTask),
        subtasks = listOf(
            Subtask(
                id = 10L,
                order = 0,
                taskId = 1L,
                description = "kitchen",
                colorArgb = 0xFF1E88E5L,
                repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 3, weekStart = DayOfWeek.MONDAY),
            ),
            Subtask(id = 11L, order = 0, taskId = 2L, description = "kitchen", isDone = true, originSubtaskId = 10L),
        ),
        // Both tombstones name something that exists, so decoding keeps them.
        suppressedRecurrences = setOf(
            taskSuppressionKey(1L, day.plusDays(7)),
            subtaskSuppressionKey(10L, day.plusDays(9)),
        ),
        anthropometry = listOf(
            AnthropometryEntry(
                date = day,
                armCm = 30.5,
                chestCm = 90.0,
                underChestCm = 75.5,
                waistCm = 70.0,
                bellyCm = 80.5,
                hipsCm = 95.0,
                thighCm = 55.5,
                weightKg = 62.4,
            )
        ),
        anthropometryEnabledFieldIds = allAnthropometryFieldIds().take(3).toSet(),
        calorieGoalChanges = listOf(
            CalorieGoalChange(date = day, kcal = 1800),
            CalorieGoalChange(date = day.plusDays(10), kcal = 1650),
        ),
        foodLog = listOf(
            FoodEntry(id = 20L, date = day, title = "soup", kcal = 320),
            FoodEntry(id = 21L, date = day, title = "apple", kcal = 80),
            FoodEntry(id = 22L, date = day, title = "Творог 5 %, 200 г", kcal = 242, dietItemId = 90L),
        ),
        runningPlanApproved = true,
        runningPlanEntries = listOf(
            RunningPlanEntry(
                date = day,
                distanceKmText = "5",
                durationHhMmText = "0030",
                paceText = "0600",
                taskId = 3L,
                isBonus = false,
            ),
            RunningPlanEntry(date = day.plusDays(2), distanceKmText = "3", isBonus = true),
        ),
        // Missing from this fixture until 0137, though they have been saved
        // since 0122 — a field forgotten here fails nothing, as the comment
        // at the top says.
        runningMode = RunningMode.BETWEEN,
        runningWorkouts = listOf(
            RunningWorkout(id = 80L, date = day, distanceKm = 8.25, durationMinutes = 47, note = "в парке"),
        ),
        counters = listOf(
            ManualCounter(id = 60L, title = "push-ups", balance = 40),
            DateRangeCounter(
                id = 61L,
                title = "until the trip",
                startDate = day,
                endDate = day.plusDays(60),
            ),
        ),
        mainMenuOrder = listOf("reading", "calendar", "new_task", "running"),
        mainMenuHiddenIds = setOf("someday", "counters"),
        themeMode = AppThemeMode.DARK,
        idHighWater = 900L,
        undoneLampMuted = true,
        undoneHorizonDays = 90,
        readingBooks = listOf(
            ReadingBook(
                id = 70L,
                shelf = ReadingShelf.NOW,
                author = "Frank Herbert",
                title = "Dune",
                coverUri = "internal://media_covers/book_70_9f3a1c02.jpg",
                totalPages = 600,
                currentPage = 240,
                createdAtEpochMillis = 1_700_000_000_000L,
            )
        ),
        readingMovies = listOf(
            ReadingMovie(
                id = 71L,
                shelf = ReadingShelf.DONE,
                title = "Solaris",
                releaseYear = 1972,
                translation = "Солярис",
                yearWatched = 2026,
                createdAtEpochMillis = 1_700_000_000_001L,
            )
        ),
        readingSeries = listOf(
            ReadingSeries(
                id = 72L,
                shelf = ReadingShelf.ABANDONED,
                title = "Twin Peaks",
                totalSeasons = 3,
                currentSeason = 2,
                currentEpisode = 7,
                yearAbandoned = 2026,
                createdAtEpochMillis = 1_700_000_000_002L,
            )
        ),
        readingSessions = listOf(
            ReadingSession(
                id = 73L,
                bookId = 70L,
                startedAtEpochMillis = 1_700_000_100_000L,
                durationMinutes = 45,
                startPage = 200,
                endPage = 240,
                createdAtEpochMillis = 1_700_000_200_000L,
            )
        ),
        activeReading = ActiveReading(
            bookId = 70L,
            startedAtEpochMillis = 1_700_000_300_000L,
            startPage = 240,
        ),
        pendingReadingSession = ReadingSession(
            id = 74L,
            bookId = 70L,
            startedAtEpochMillis = 1_700_000_400_000L,
            durationMinutes = 60,
            startPage = 240,
            endPage = 260,
            createdAtEpochMillis = 1_700_000_500_000L,
        ),
        autoRecordInterruptedReading = true,
        readingMediaFilter = ReadingMediaFilter(showBooks = true, showMovies = false, showSeries = true),
        readingPlansPrefs = ReadingTabPrefs(
            viewMode = ReadingViewMode.WALL,
            sort = ReadingSort(field = ReadingSortField.YEAR, ascending = false),
        ),
        readingNowPrefs = ReadingTabPrefs(
            viewMode = ReadingViewMode.GRID,
            sort = ReadingSort(field = ReadingSortField.TITLE, ascending = false),
        ),
        readingDonePrefs = ReadingTabPrefs(
            viewMode = ReadingViewMode.WALL,
            sort = ReadingSort(field = ReadingSortField.TITLE, ascending = true),
        ),
        readingAbandonedPrefs = ReadingTabPrefs(
            viewMode = ReadingViewMode.WALL,
            sort = ReadingSort(field = ReadingSortField.YEAR, ascending = true),
        ),
        // Schema 6 — every one the opposite of its default.
        anthropometryShowForecast = false,
        anthropometryShowEntries = true,
        anthropometryRange = AnthropometryRange.CUSTOM,
        anthropometryCustomRange = DateWindow(day.minusDays(40), day),
        calorimeterShowDailyGoal = false,
        calorimeterShowWeeklyGoal = false,
        calorimeterShowPotentialLoss = false,
        calendarBadges = setOf(CalendarBadge.FOOD, CalendarBadge.DEBTS),
        foodLibraryVisible = true,
        dietEnabled = true,
        dietPlan = mapOf(
            DayOfWeek.MONDAY to listOf(
                DietItem(id = 90L, title = "Творог 5 %, 200 г", kcal = 242),
                DietItem(id = 91L, title = "Гречка, 150 г", kcal = 165),
            ),
            DayOfWeek.FRIDAY to listOf(DietItem(id = 92L, title = "Суп", kcal = 300)),
        ),
        // Schema 7 — the opposite of the defaults again.
        dietShowPastUnticked = false,
        foodLibraryUserItems = listOf(
            FoodLibraryUserItem(id = 95L, category = FoodCategory.DAIRY, name = "Сырок", amount = 40, unit = "г", kcal = 160),
            FoodLibraryUserItem(id = 96L, category = FoodCategory.DRINKS, name = "Квас", amount = null, unit = null, kcal = 27),
        ),
        notifications = NotificationSettings(
            summaryMinutes = listOf(480, 780, 1200),
            summaryToday = SummaryToday.ALL,
            summaryDebts = false,
            reminderLeadMinutes = 15,
        ),
        // 03.10, no schema step — still every field.
        widgetStyle = WidgetStyle(backgroundPercent = 35, textColor = WidgetTextColor.WHITE, checkColorArgb = 0xFF43A047L),
        // Schema 8 — a scheme with one of everything, nothing at its default.
        metroSchemes = listOf(
            MetroScheme(
                id = 3L,
                city = "Москва",
                lines = listOf(
                    MetroLine(
                        id = 1L, label = "1", name = "Сокольническая", color = 0xFFE42313L,
                        ring = false, carCount = 7, doorsPerCar = 3,
                        trunk = listOf(10L, 11L, 12L),
                        branches = listOf(MetroBranch(id = 2L, fromStationId = 12L, stationIds = listOf(13L))),
                    ),
                    MetroLine(id = 5L, label = "5", name = "Кольцевая", color = 0xFF915133L, ring = true, trunk = listOf(20L, 21L, 22L)),
                ),
                stations = listOf(
                    MetroStation(10L, 1L, "Красные ворота", mapX = 0.25f, mapY = 0.5f),
                    MetroStation(11L, 1L, "Чистые пруды"),
                    MetroStation(12L, 1L, "Лубянка"),
                    MetroStation(13L, 1L, "Охотный ряд"),
                    MetroStation(20L, 5L, "Комсомольская"),
                    MetroStation(21L, 5L, "Курская"),
                    MetroStation(22L, 5L, "Таганская"),
                ),
                transfers = listOf(MetroTransfer(30L, 10L, 20L, minutes = 4)),
                exits = listOf(MetroExit(40L, 12L, "к Детскому миру")),
                hints = listOf(
                    MetroHint(50L, stationId = 10L, fromStationId = 11L, car = 3, door = 2, target = MetroHintTarget.Transfer(20L)),
                    MetroHint(51L, stationId = 12L, fromStationId = 11L, car = 7, door = 1, target = MetroHintTarget.Exit(40L)),
                ),
                segmentTimes = listOf(MetroSegmentTime(10L, 11L, 2)),
                defaultSegmentMinutes = 2,
                defaultTransferMinutes = 5,
                librarySource = "moscow",
            ),
        ),
        metroSelection = "user:3",
    )

    private fun roundTrip(state: AppState): AppState =
        appStateStoreJson
            .decodeFromString<AppStateDto>(appStateStoreJson.encodeToString(state.toDto()))
            .toDomain()

    @Test
    fun theWholeStateSurvivesBeingWrittenDownAndReadBack() {
        assertEquals(whole, roundTrip(whole))
    }

    /**
     * Twice, because an export can be imported onto a device that then
     * exports again. Anything that survives one trip but not two would only
     * show up on the second phone.
     */
    @Test
    fun andSurvivesBeingWrittenDownAndReadBackAgain() {
        assertEquals(whole, roundTrip(roundTrip(whole)))
    }

    /**
     * Guards the fixture rather than the code: a state that happened to equal
     * the default everywhere would pass the test above while proving nothing.
     */
    @Test
    fun theFixtureIsNotJustTheDefaultState() {
        assertNotEquals(AppState(), whole)
        assertTrue(whole.tasks.isNotEmpty())
        assertTrue(whole.subtasks.isNotEmpty())
        assertTrue(whole.suppressedRecurrences.isNotEmpty())
        assertTrue(whole.anthropometry.isNotEmpty())
        assertTrue(whole.calorieGoalChanges.isNotEmpty())
        assertTrue(whole.foodLog.isNotEmpty())
        assertTrue(whole.runningPlanEntries.isNotEmpty())
        assertTrue(whole.counters.isNotEmpty())
        assertTrue(whole.mainMenuOrder.isNotEmpty())
        assertTrue(whole.mainMenuHiddenIds.isNotEmpty())
        assertTrue(whole.readingBooks.isNotEmpty())
        assertTrue(whole.readingMovies.isNotEmpty())
        assertTrue(whole.readingSeries.isNotEmpty())
        assertTrue(whole.readingSessions.isNotEmpty())
        assertTrue(whole.activeReading != null)
        assertTrue(whole.pendingReadingSession != null)
        assertTrue(whole.runningWorkouts.isNotEmpty())
        assertTrue(whole.dietPlan.isNotEmpty())
        assertTrue(whole.foodLog.any { it.dietItemId != null })
        assertTrue(whole.foodLibraryUserItems.isNotEmpty())
        assertTrue(whole.notifications.summaryMinutes.isNotEmpty())
        assertTrue(whole.metroSchemes.single().hints.size == 2)
    }

    /**
     * The archive is JSON somebody may have to read with their eyes on a bad
     * day, so the parts that are not obviously numbers are named.
     */
    @Test
    fun theArchiveNamesWhatItHolds() {
        val json = appStateStoreJson.encodeToString(whole.toDto())

        listOf(
            "tasks", "subtasks", "suppressedRecurrences", "anthropometry",
            "calorieGoalChanges", "foodLog", "runningPlanEntries", "counters",
            "mainMenuOrder", "mainMenuHiddenIds", "themeMode", "idHighWater",
            "readingBooks", "readingMovies", "readingSeries", "readingSessions",
            "activeReading", "pendingReadingSession", "autoRecordInterruptedReading",
            "runningMode", "runningWorkouts",
            "anthropometryShowForecast", "anthropometryShowEntries", "anthropometryRange",
            "calorimeterShowDailyGoal", "calendarBadges", "foodLibraryVisible",
            "dietEnabled", "dietPlan", "dietItemId",
            "dietShowPastUnticked", "foodLibraryUserItems", "notifications",
            "summaryMinutes", "reminderLeadMinutes",
            "widgetStyle", "backgroundPercent", "checkColorArgb",
            "metroSchemes", "metroSelection", "trunk", "branches", "fromStationId", "exitId", "toStationId",
        ).forEach { key ->
            assertTrue("the archive says nothing about $key", json.contains("\"$key\""))
        }
    }
}
