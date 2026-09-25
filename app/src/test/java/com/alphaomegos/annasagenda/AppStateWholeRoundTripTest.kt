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
        ).forEach { key ->
            assertTrue("the archive says nothing about $key", json.contains("\"$key\""))
        }
    }
}
