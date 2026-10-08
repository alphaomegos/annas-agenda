package com.alphaomegos.annasagenda.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.app.*

/* ---------------------------
   Mapping
---------------------------- */

internal fun AppState.toDto(): AppStateDto = AppStateDto(
    tasks = tasks.map { it.toDto() },
    subtasks = subtasks.map { it.toDto() },
    suppressedRecurrences = suppressedRecurrences.toList(),
    anthropometry = anthropometry.map { it.toDto() },
    anthropometryEnabledFieldIds = anthropometryEnabledFieldIds.toList(),
    calorieGoalChanges = calorieGoalChanges.map { it.toDto() },
    foodLog = foodLog.map { it.toDto() },
    runningPlanApproved = runningPlanApproved,
    runningPlanEntries = runningPlanEntries.map { it.toDto() },
    runningMode = runningMode.name,
    runningWorkouts = runningWorkouts.map { it.toDto() },
    counters = counters.map { it.toDto() },
    mainMenuOrder = mainMenuOrder,
    mainMenuHiddenIds = mainMenuHiddenIds.toList(),
    undoneLampMuted = undoneLampMuted,
    doneTasksLast = doneTasksLast,
    undoneHorizonDays = undoneHorizonDays,
    themeMode = themeMode.name,
    idHighWater = idHighWater,
    readingBooks = readingBooks.map { it.toDto() },
    readingMovies = readingMovies.map { it.toDto() },
    readingSeries = readingSeries.map { it.toDto() },
    readingSessions = readingSessions.map { it.toDto() },
    activeReading = activeReading?.toDto(),
    pendingReadingSession = pendingReadingSession?.toDto(),
    autoRecordInterruptedReading = autoRecordInterruptedReading,
    readingMediaFilter = readingMediaFilter.toDto(),
    readingPlansPrefs = readingPlansPrefs.toDto(),
    readingNowPrefs = readingNowPrefs.toDto(),
    readingDonePrefs = readingDonePrefs.toDto(),
    readingAbandonedPrefs = readingAbandonedPrefs.toDto(),
    anthropometryShowForecast = anthropometryShowForecast,
    anthropometryShowEntries = anthropometryShowEntries,
    anthropometryRange = anthropometryRange.name,
    anthropometryCustomFromEpochDay = anthropometryCustomRange?.from?.toEpochDay(),
    anthropometryCustomToEpochDay = anthropometryCustomRange?.to?.toEpochDay(),
    calorimeterShowDailyGoal = calorimeterShowDailyGoal,
    calorimeterShowWeeklyGoal = calorimeterShowWeeklyGoal,
    calorimeterShowPotentialLoss = calorimeterShowPotentialLoss,
    // In declaration order, so the same set always writes the same list.
    calendarBadges = CalendarBadge.entries.filter { it in calendarBadges }.map { it.name },
    foodLibraryVisible = foodLibraryVisible,
    dietEnabled = dietEnabled,
    dietPlan = dietPlan.entries
        .sortedBy { it.key.value }
        .map { (day, items) -> DietDayDto(dayOfWeekIso = day.value, items = items.map { it.toDto() }) },
    dietShowPastUnticked = dietShowPastUnticked,
    foodLibraryUserItems = foodLibraryUserItems.map { it.toDto() },
    notifications = notifications.toDto(),
    widgetStyle = WidgetStyleDto(
        backgroundPercent = widgetStyle.backgroundPercent,
        textColor = widgetStyle.textColor.name,
        checkColorArgb = widgetStyle.checkColorArgb,
    ),
    metroSchemes = metroSchemes.map { it.toDto() },
    metroSelection = metroSelection,
    travelCountries = travelCountries.map { r ->
        TravelCountryDto(
            countryId = r.countryId,
            trips = r.trips.map { TravelTripDto(it.id, it.year, it.month, it.cities) },
            continent = r.continentOverride?.name,
            name = r.customName,
            mapX = r.customPoint?.x,
            mapY = r.customPoint?.y,
        )
    },
    travelView = TravelViewDto(view = travelView.view.name, reversed = travelView.reversed, onlyMine = travelView.onlyMine),
    flights = flights.map { it.toDto() },
    flightPrefs = FlightPrefsDto(flightPrefs.groupJourneys, flightPrefs.showMap, flightPrefs.showOnTravelMap),
    flightCityNames = flightCityNames,
    flightAirlineNames = flightAirlineNames,
    flightHomes = flightHomes.sorted(),
)

internal fun normalizeAnthropometryFieldIdsForStore(ids: List<String>): Set<String> {
    val normalized = ids
        .asSequence()
        .map { it.trim() }
        .filter { it in allAnthropometryFieldIds() }
        .toSet()

    return normalized.ifEmpty { defaultAnthropometryFieldIds() }
}

/**
 * Every payload the app reads comes through here, and this is also the moment
 * ids start being handed out again from whatever the data says is in use. So
 * it is the one place where a reference to something that is gone can still be
 * cleared before the number it holds is handed to something new.
 *
 * Order matters: dangling references are cleared first, tombstones are pruned
 * against what is left. Dropping a subtask can orphan its tombstones, and
 * pruning before that would leave them behind for one more save.
 */
internal fun AppStateDto.toDomain(): AppState {
    val decoded = AppState(
        tasks = tasks.map { it.toDomain() },
        subtasks = subtasks.map { it.toDomain() },
        suppressedRecurrences = suppressedRecurrences.toSet(),
        anthropometry = anthropometry.map { it.toDomain() },
        anthropometryEnabledFieldIds = normalizeAnthropometryFieldIdsForStore(anthropometryEnabledFieldIds),
        calorieGoalChanges = calorieGoalChanges.map { it.toDomain() },
        foodLog = foodLog.map { it.toDomain() },
        runningPlanApproved = runningPlanApproved,
        runningPlanEntries = runningPlanEntries.map { it.toDomain() },
        // An unknown word falls back to the plan, which is what every user
        // had before this existed. A mode is a view, so guessing wrong costs
        // one tap rather than any data.
        runningMode = runningModeFromName(runningMode),
        runningWorkouts = runningWorkouts.map { it.toDomain() },
        counters = counters.mapNotNull { it.toDomainOrNull() },
        // Normalised on the way in like the other preferences here: the
        // setter normalises, but a payload does not have to have come from it.
        mainMenuOrder = normalizeMainMenuOrderIds(mainMenuOrder),
        mainMenuHiddenIds = mainMenuHiddenIds.toSet(),
        undoneLampMuted = undoneLampMuted,
        doneTasksLast = doneTasksLast,
        undoneHorizonDays = normalizeUndoneHorizonDays(undoneHorizonDays),
        themeMode = parseAppThemeMode(themeMode),
        // Never below zero: a payload claiming a negative mark would make
        // nextIdFor no safer than counting, but it must not make it worse.
        idHighWater = idHighWater.coerceAtLeast(0L),
        readingBooks = readingBooks.mapNotNull { it.toDomainOrNull() },
        readingMovies = readingMovies.mapNotNull { it.toDomainOrNull() },
        readingSeries = readingSeries.mapNotNull { it.toDomainOrNull() },
        readingSessions = readingSessions.mapNotNull { it.toDomainOrNull() },
        activeReading = activeReading?.toDomain(),
        pendingReadingSession = pendingReadingSession?.toDomainOrNull(),
        autoRecordInterruptedReading = autoRecordInterruptedReading,
        readingMediaFilter = readingMediaFilter.toDomain(),
        readingPlansPrefs = readingPlansPrefs.toDomain(),
        readingNowPrefs = readingNowPrefs.toDomain(),
        readingDonePrefs = readingDonePrefs.toDomain(),
        readingAbandonedPrefs = readingAbandonedPrefs.toDomain(),
        anthropometryShowForecast = anthropometryShowForecast,
        anthropometryShowEntries = anthropometryShowEntries,
        anthropometryRange = anthropometryRangeFromName(anthropometryRange),
        anthropometryCustomRange = dateWindowFromEpochDays(anthropometryCustomFromEpochDay, anthropometryCustomToEpochDay),
        calorimeterShowDailyGoal = calorimeterShowDailyGoal,
        calorimeterShowWeeklyGoal = calorimeterShowWeeklyGoal,
        calorimeterShowPotentialLoss = calorimeterShowPotentialLoss,
        // A name this build does not know is dropped rather than guessed at;
        // the worst it costs is one mark the user switches back on.
        calendarBadges = calendarBadges
            .mapNotNull { name -> CalendarBadge.entries.firstOrNull { it.name == name } }
            .toSet(),
        foodLibraryVisible = foodLibraryVisible,
        dietEnabled = dietEnabled,
        dietPlan = dietPlanFromDto(dietPlan),
        dietShowPastUnticked = dietShowPastUnticked,
        // A shelf this build does not know drops the food rather than
        // putting it on a wrong shelf.
        foodLibraryUserItems = foodLibraryUserItems.mapNotNull { it.toDomainOrNull() },
        notifications = notifications.toDomain(),
        widgetStyle = normalizedWidgetStyle(
            WidgetStyle(
                backgroundPercent = widgetStyle.backgroundPercent,
                textColor = WidgetTextColor.entries.firstOrNull { it.name == widgetStyle.textColor }
                    ?: WidgetTextColor.SYSTEM,
                checkColorArgb = widgetStyle.checkColorArgb,
            )
        ),
        // A second scheme with an id already taken is dropped: the selection
        // and the editor find a scheme by its id.
        metroSchemes = metroSchemes.map { it.toDomain() }.distinctBy { it.id },
        metroSelection = metroSelection,
        travelCountries = travelCountriesFromDto(travelCountries),
        travelView = TravelViewPrefs(
            view = TravelView.entries.firstOrNull { it.name == travelView.view } ?: TravelView.YEARS,
            reversed = travelView.reversed,
            onlyMine = travelView.onlyMine,
        ),
        flights = flightsFromDto(flights),
        flightPrefs = FlightPrefs(flightPrefs.groupJourneys, flightPrefs.showMap, flightPrefs.showOnTravelMap),
        flightCityNames = flightCityNames.filter { it.key.isNotBlank() && it.value.isNotBlank() },
        flightAirlineNames = flightAirlineNames.filter { it.key.isNotBlank() && it.value.isNotBlank() },
        flightHomes = flightHomes.filter { it.isNotBlank() }.toSet(),
    )

    val whole = stateWithDanglingReferencesCleared(decoded)

    return whole.copy(
        suppressedRecurrences = pruneOrphanedSuppressions(
            suppressedRecurrences = whole.suppressedRecurrences,
            tasks = whole.tasks,
            subtasks = whole.subtasks,
        ),
    )
}

internal fun Task.toDto(): TaskDto = TaskDto(
    id = id,
    order = order,
    dateEpochDay = date?.toEpochDay(),
    timeSecondOfDay = time?.toSecondOfDay(),
    description = description,
    colorArgb = colorArgb,
    hasSubtasks = hasSubtasks,
    isDone = isDone,
    repeatRule = repeatRule?.toDto(),
    originTaskId = originTaskId,
    linkedManualCounterId = linkedManualCounterId,
)

internal fun TaskDto.toDomain(): Task = Task(
    id = id,
    order = order,
    date = dateEpochDay?.let { LocalDate.ofEpochDay(it) },
    time = timeSecondOfDay?.let { LocalTime.ofSecondOfDay(it.toLong()) },
    description = description,
    colorArgb = colorArgb,
    hasSubtasks = hasSubtasks,
    isDone = isDone,
    repeatRule = repeatRule?.toDomain(),
    originTaskId = originTaskId,
    linkedManualCounterId = linkedManualCounterId,
)

internal fun Subtask.toDto(): SubtaskDto = SubtaskDto(
    id = id,
    order = order,
    taskId = taskId,
    description = description,
    colorArgb = colorArgb,
    isDone = isDone,
    repeatRule = repeatRule?.toDto(),
    originSubtaskId = originSubtaskId,
)

internal fun SubtaskDto.toDomain(): Subtask = Subtask(
    id = id,
    order = order,
    taskId = taskId,
    description = description,
    colorArgb = colorArgb,
    isDone = isDone,
    repeatRule = repeatRule?.toDomain(),
    originSubtaskId = originSubtaskId,
)

internal fun RepeatRule.toDto(): RepeatRuleDto = RepeatRuleDto(
    freq = freq.name,
    interval = interval,
    weekDaysIso = weekDays.map { it.value },
    dayOfMonth = dayOfMonth,
    weekStartIso = weekStart?.value,
)

/**
 * The two day fields are treated differently on purpose.
 *
 * [RepeatRuleDto.weekStartIso] out of range becomes null, which is a state the
 * rule already has a meaning for: "not recorded, fall back to the device's
 * locale". Nothing is lost by taking that route.
 *
 * [RepeatRuleDto.weekDaysIso] has no such state. Dropping an unreadable day
 * would turn "every Monday and Wednesday" into "every Monday" — quietly, and
 * permanently at the next save. So an impossible day refuses the whole payload
 * instead, which leaves it on disk and quarantined, and is the same choice the
 * app makes everywhere else it cannot read something without losing it.
 */
internal fun RepeatRuleDto.toDomain(): RepeatRule = RepeatRule(
    freq = RepeatFreq.valueOf(freq),
    interval = interval,
    weekDays = weekDaysIso
        .map { iso ->
            require(iso in 1..7) { "Repeat rule names an impossible weekday: $iso" }
            DayOfWeek.of(iso)
        }
        .toSet(),
    dayOfMonth = dayOfMonth,
    weekStart = weekStartIso
        ?.takeIf { it in 1..7 }
        ?.let { DayOfWeek.of(it) },
)

internal fun AnthropometryEntry.toDto(): AnthropometryDto = AnthropometryDto(
    dateEpochDay = date.toEpochDay(),
    armCm = armCm,
    chestCm = chestCm,
    underChestCm = underChestCm,
    waistCm = waistCm,
    bellyCm = bellyCm,
    hipsCm = hipsCm,
    thighCm = thighCm,
    weightKg = weightKg,
)

internal fun AnthropometryDto.toDomain(): AnthropometryEntry = AnthropometryEntry(
    date = LocalDate.ofEpochDay(dateEpochDay),
    armCm = armCm,
    chestCm = chestCm,
    underChestCm = underChestCm,
    waistCm = waistCm,
    bellyCm = bellyCm,
    hipsCm = hipsCm,
    thighCm = thighCm,
    weightKg = weightKg,
)

internal fun CalorieGoalChange.toDto(): CalorieGoalChangeDto = CalorieGoalChangeDto(
    dateEpochDay = date.toEpochDay(),
    kcal = kcal,
)

internal fun CalorieGoalChangeDto.toDomain(): CalorieGoalChange = CalorieGoalChange(
    date = LocalDate.ofEpochDay(dateEpochDay),
    kcal = kcal,
)

internal fun FoodEntry.toDto(): FoodEntryDto = FoodEntryDto(
    id = id,
    dateEpochDay = date.toEpochDay(),
    title = title,
    kcal = kcal,
    dietItemId = dietItemId,
)

internal fun FoodEntryDto.toDomain(): FoodEntry = FoodEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    kcal = kcal,
    dietItemId = dietItemId,
)

internal fun DietItem.toDto(): DietItemDto = DietItemDto(id = id, title = title, kcal = kcal)

internal fun DietItemDto.toDomain(): DietItem = DietItem(id = id, title = title, kcal = kcal)

/** Unknown names read back as a month, the range the chart always opened on. */
internal fun anthropometryRangeFromName(name: String): AnthropometryRange =
    AnthropometryRange.entries.firstOrNull { it.name == name } ?: AnthropometryRange.MONTH

/** Both ends or nothing: half a custom range is no range. */
internal fun dateWindowFromEpochDays(from: Long?, to: Long?): DateWindow? =
    if (from == null || to == null) null else DateWindow(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to))

/**
 * The diet by day of the week. A day number outside 1..7 is dropped rather
 * than guessed at; two entries for the same day are joined, in the order
 * written, so nothing typed in is lost to a duplicate.
 */
internal fun dietPlanFromDto(days: List<DietDayDto>): Map<DayOfWeek, List<DietItem>> =
    days
        .filter { it.dayOfWeekIso in 1..7 }
        .groupBy { DayOfWeek.of(it.dayOfWeekIso) }
        .mapValues { (_, sameDay) -> sameDay.flatMap { d -> d.items.map { it.toDomain() } } }
        .filterValues { it.isNotEmpty() }

internal fun FoodLibraryUserItem.toDto(): FoodLibraryUserItemDto = FoodLibraryUserItemDto(
    id = id,
    category = category.name,
    name = name,
    amount = amount,
    unit = unit,
    kcal = kcal,
)

internal fun FoodLibraryUserItemDto.toDomainOrNull(): FoodLibraryUserItem? {
    val shelf = FoodCategory.entries.firstOrNull { it.name == category } ?: return null
    return FoodLibraryUserItem(id = id, category = shelf, name = name, amount = amount, unit = unit, kcal = kcal)
}

internal fun NotificationSettings.toDto(): NotificationSettingsDto = NotificationSettingsDto(
    summaryMinutes = summaryMinutes,
    summaryToday = summaryToday.name,
    summaryDebts = summaryDebts,
    reminderLeadMinutes = reminderLeadMinutes,
)

/**
 * Times outside the day are dropped, the rest sorted and without repeats;
 * a reminder lead that is not one of the choices is read as "off" — a
 * reminder at a moment nobody chose is worse than none.
 */
internal fun NotificationSettingsDto.toDomain(): NotificationSettings = NotificationSettings(
    summaryMinutes = summaryMinutes.filter { it in 0 until 24 * 60 }.distinct().sorted(),
    summaryToday = SummaryToday.entries.firstOrNull { it.name == summaryToday } ?: SummaryToday.UNDONE,
    summaryDebts = summaryDebts,
    reminderLeadMinutes = reminderLeadMinutes?.takeIf { it in REMINDER_LEAD_CHOICES },
)

internal fun runningModeFromName(name: String): RunningMode =
    RunningMode.entries.firstOrNull { it.name == name } ?: RunningMode.PLAN

internal fun RunningWorkout.toDto(): RunningWorkoutDto = RunningWorkoutDto(
    id = id,
    dateEpochDay = date.toEpochDay(),
    distanceKm = distanceKm,
    durationMinutes = durationMinutes,
    note = note,
)

internal fun RunningWorkoutDto.toDomain(): RunningWorkout = RunningWorkout(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    distanceKm = distanceKm,
    durationMinutes = durationMinutes,
    note = note,
)

internal fun RunningPlanEntry.toDto(): RunningPlanEntryDto = RunningPlanEntryDto(
    dateEpochDay = date.toEpochDay(),
    distanceKmText = distanceKmText,
    durationHhMmText = durationHhMmText,
    paceText = paceText,
    taskId = taskId,
    isBonus = isBonus,
)

internal fun RunningPlanEntryDto.toDomain(): RunningPlanEntry = RunningPlanEntry(
    date = LocalDate.ofEpochDay(dateEpochDay),
    distanceKmText = distanceKmText,
    durationHhMmText = durationHhMmText,
    paceText = paceText,
    taskId = taskId,
    isBonus = isBonus,
)

internal fun Counter.toDto(): CounterDto = when (this) {
    is DateRangeCounter -> CounterDto(
        id = id,
        kind = "DATE_RANGE",
        title = title,
        startEpochDay = startDate.toEpochDay(),
        endEpochDay = endDate.toEpochDay(),
    )

    is ManualCounter -> CounterDto(
        id = id,
        kind = "MANUAL",
        title = title,
        balance = balance,
    )
}

internal fun CounterDto.toDomainOrNull(): Counter? = when (kind) {
    "DATE_RANGE" -> {
        val s = startEpochDay ?: return null
        val e = endEpochDay ?: return null
        DateRangeCounter(
            id = id,
            title = title,
            startDate = LocalDate.ofEpochDay(s),
            endDate = LocalDate.ofEpochDay(e)
        )
    }

    "MANUAL" -> ManualCounter(
        id = id,
        title = title,
        balance = balance ?: 0
    )

    else -> null
}

/* ---------------------------
   Reading mapping helpers
---------------------------- */

internal fun parseReadingShelf(raw: String): ReadingShelf =
    runCatching { ReadingShelf.valueOf(raw) }.getOrElse { ReadingShelf.PLANS }

internal fun parseReadingViewMode(raw: String): ReadingViewMode =
    runCatching { ReadingViewMode.valueOf(raw) }.getOrElse { ReadingViewMode.GRID }

internal fun parseReadingSortField(raw: String): ReadingSortField =
    runCatching { ReadingSortField.valueOf(raw) }.getOrElse { ReadingSortField.TITLE }

internal fun ReadingBook.toDto(): ReadingBookDto = ReadingBookDto(
    id = id,
    shelf = shelf.name,
    author = author,
    title = title,
    coverUri = coverUri,
    totalPages = totalPages,
    currentPage = currentPage,
    yearRead = yearRead,
    yearAbandoned = yearAbandoned,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun ReadingBookDto.toDomainOrNull(): ReadingBook? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null
    val pages = totalPages
    if (pages <= 0) return null

    val cur = currentPage.coerceIn(0, pages)
    return ReadingBook(
        id = id,
        shelf = parseReadingShelf(shelf),
        author = author,
        title = cleanTitle,
        coverUri = coverUri,
        totalPages = pages,
        currentPage = cur,
        yearRead = yearRead,
        yearAbandoned = yearAbandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

internal fun ReadingMovie.toDto(): ReadingMovieDto = ReadingMovieDto(
    id = id,
    shelf = shelf.name,
    title = title,
    coverUri = coverUri,
    releaseYear = releaseYear,
    translation = translation,
    yearWatched = yearWatched,
    yearAbandoned = yearAbandoned,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun ReadingMovieDto.toDomainOrNull(): ReadingMovie? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null

    return ReadingMovie(
        id = id,
        shelf = parseReadingShelf(shelf),
        title = cleanTitle,
        coverUri = coverUri,
        releaseYear = releaseYear,
        translation = translation.trim(),
        yearWatched = yearWatched,
        yearAbandoned = yearAbandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

internal fun ReadingSeries.toDto(): ReadingSeriesDto = ReadingSeriesDto(
    id = id,
    shelf = shelf.name,
    title = title,
    coverUri = coverUri,
    totalSeasons = totalSeasons,
    currentSeason = currentSeason,
    currentEpisode = currentEpisode,
    yearWatched = yearWatched,
    yearAbandoned = yearAbandoned,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun ReadingSeriesDto.toDomainOrNull(): ReadingSeries? {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null

    val safeTotalSeasons = totalSeasons.coerceAtLeast(1)
    val safeCurrentSeason = currentSeason.coerceIn(1, safeTotalSeasons)
    val safeCurrentEpisode = currentEpisode.coerceAtLeast(1)

    return ReadingSeries(
        id = id,
        shelf = parseReadingShelf(shelf),
        title = cleanTitle,
        coverUri = coverUri,
        totalSeasons = safeTotalSeasons,
        currentSeason = safeCurrentSeason,
        currentEpisode = safeCurrentEpisode,
        yearWatched = yearWatched,
        yearAbandoned = yearAbandoned,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

internal fun ReadingSession.toDto(): ReadingSessionDto = ReadingSessionDto(
    id = id,
    bookId = bookId,
    startedAtEpochMillis = startedAtEpochMillis,
    durationMinutes = durationMinutes,
    startPage = startPage,
    endPage = endPage,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun ReadingSessionDto.toDomainOrNull(): ReadingSession? {
    if (durationMinutes <= 0) return null
    if (startPage < 0) return null
    if (endPage < 0) return null
    return ReadingSession(
        id = id,
        bookId = bookId,
        startedAtEpochMillis = startedAtEpochMillis,
        durationMinutes = durationMinutes,
        startPage = startPage,
        endPage = endPage,
        // The domain falls back to the start time when this is not known; the
        // DTO fell back to zero, and a payload written before the field
        // existed decodes through the DTO. All-zero timestamps make
        // "the latest session" mean "whichever came first in the list", and
        // that is what the remaining-time estimate reads.
        createdAtEpochMillis = createdAtEpochMillis.takeIf { it > 0L } ?: startedAtEpochMillis,
    )
}

internal fun ReadingMediaFilter.toDto(): ReadingMediaFilterDto = ReadingMediaFilterDto(
    showBooks = showBooks,
    showMovies = showMovies,
    showSeries = showSeries,
)

internal fun ReadingMediaFilterDto.toDomain(): ReadingMediaFilter = ReadingMediaFilter(
    showBooks = showBooks,
    showMovies = showMovies,
    showSeries = showSeries,
)

internal fun ReadingSort.toDto(): ReadingSortDto = ReadingSortDto(
    field = field.name,
    ascending = ascending,
)

internal fun ReadingSortDto.toDomain(): ReadingSort = ReadingSort(
    field = parseReadingSortField(field),
    ascending = ascending,
)

internal fun ReadingTabPrefs.toDto(): ReadingTabPrefsDto = ReadingTabPrefsDto(
    viewMode = viewMode.name,
    sort = sort.toDto(),
)

internal fun ReadingTabPrefsDto.toDomain(): ReadingTabPrefs = ReadingTabPrefs(
    viewMode = parseReadingViewMode(viewMode),
    sort = sort.toDomain(),
)

internal fun ActiveReading.toDto(): ActiveReadingDto = ActiveReadingDto(
    bookId = bookId,
    startedAtEpochMillis = startedAtEpochMillis,
    startPage = startPage,
)

internal fun ActiveReadingDto.toDomain(): ActiveReading = ActiveReading(
    bookId = bookId,
    startedAtEpochMillis = startedAtEpochMillis,
    startPage = startPage.coerceAtLeast(0),
)

/* ---------------- metro ---------------- */

internal fun MetroScheme.toDto(): MetroSchemeDto = MetroSchemeDto(
    id = id,
    city = city,
    lines = lines.map { l ->
        MetroLineDto(
            id = l.id,
            label = l.label,
            name = l.name,
            color = l.color,
            ring = l.ring,
            carCount = l.carCount,
            doorsPerCar = l.doorsPerCar,
            trunk = l.trunk,
            branches = l.branches.map { MetroBranchDto(it.id, it.fromStationId, it.stationIds) },
        )
    },
    stations = stations.map {
        MetroStationDto(
            it.id, it.lineId, it.name, it.mapX, it.mapY,
            closed = it.closure != null,
            expectedOpeningEpochDay = it.closure?.expectedOpening?.toEpochDay(),
        )
    },
    transfers = transfers.map {
        MetroTransferDto(
            it.id, it.aStationId, it.bStationId, it.minutes,
            closed = it.closure != null,
            expectedOpeningEpochDay = it.closure?.expectedOpening?.toEpochDay(),
        )
    },
    exits = exits.map {
        MetroExitDto(
            it.id, it.stationId, it.name,
            closed = it.closure != null,
            expectedOpeningEpochDay = it.closure?.expectedOpening?.toEpochDay(),
        )
    },
    hints = hints.map { h ->
        MetroHintDto(
            id = h.id,
            stationId = h.stationId,
            fromStationId = h.fromStationId,
            car = h.car,
            door = h.door,
            toStationId = (h.target as? MetroHintTarget.Transfer)?.toStationId,
            exitId = (h.target as? MetroHintTarget.Exit)?.exitId,
        )
    },
    segmentTimes = segmentTimes.map { MetroSegmentTimeDto(it.aStationId, it.bStationId, it.minutes) },
    defaultSegmentMinutes = defaultSegmentMinutes,
    defaultTransferMinutes = defaultTransferMinutes,
    librarySource = librarySource,
    mapGrid = mapGrid,
    transfersTogether = transfersTogether,
    labelsOncePerInterchange = labelsOncePerInterchange,
    glue = glue,
    snapAngles = snapAngles,
    labelScale = labelScale,
    lineScale = lineScale,
    stationScale = stationScale,
    labelsMode = labelsMode.name,
    showTransferLinks = showTransferLinks,
    bends = bends.map { b -> MetroBendDto(b.aStationId, b.bStationId, b.points.map { it.x }, b.points.map { it.y }) },
)

/**
 * A scheme read back, tidied the way an edit leaves it: anything pointing at
 * a station, transfer or exit that is not there is dropped (reconciled), a
 * hint that names both targets or neither is dropped, and a default below
 * one minute or a train of no cars falls back to the usual.
 */
internal fun MetroSchemeDto.toDomain(): MetroScheme {
    val scheme = MetroScheme(
        id = id,
        city = city,
        lines = lines.map { l ->
            MetroLine(
                id = l.id,
                label = l.label,
                name = l.name,
                color = l.color,
                ring = l.ring,
                carCount = l.carCount.takeIf { it >= 1 } ?: 8,
                doorsPerCar = l.doorsPerCar.takeIf { it >= 1 } ?: 4,
                trunk = l.trunk,
                branches = l.branches.map { MetroBranch(it.id, it.fromStationId, it.stationIds) },
            )
        },
        stations = stations.map {
            MetroStation(it.id, it.lineId, it.name, it.mapX, it.mapY, metroClosureFromDto(it.closed, it.expectedOpeningEpochDay))
        },
        transfers = transfers.map {
            MetroTransfer(
                it.id, it.aStationId, it.bStationId, it.minutes?.coerceAtLeast(0),
                metroClosureFromDto(it.closed, it.expectedOpeningEpochDay),
            )
        },
        exits = exits.map { MetroExit(it.id, it.stationId, it.name, metroClosureFromDto(it.closed, it.expectedOpeningEpochDay)) },
        hints = hints.mapNotNull { h ->
            val target = when {
                h.toStationId != null && h.exitId == null -> MetroHintTarget.Transfer(h.toStationId)
                h.exitId != null && h.toStationId == null -> MetroHintTarget.Exit(h.exitId)
                else -> return@mapNotNull null
            }
            MetroHint(h.id, h.stationId, h.fromStationId, h.car, h.door, target)
        },
        segmentTimes = segmentTimes.filter { it.minutes >= 1 }.map { MetroSegmentTime(it.aStationId, it.bStationId, it.minutes) },
        defaultSegmentMinutes = defaultSegmentMinutes.takeIf { it >= 1 } ?: METRO_DEFAULT_SEGMENT_MINUTES,
        defaultTransferMinutes = defaultTransferMinutes.takeIf { it >= 1 } ?: METRO_DEFAULT_TRANSFER_MINUTES,
        librarySource = librarySource,
        mapGrid = mapGrid,
        transfersTogether = transfersTogether,
        labelsOncePerInterchange = labelsOncePerInterchange,
        snapAngles = snapAngles,
        // Sizes within what the panel offers; anything else is as before.
        labelScale = labelScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        lineScale = lineScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        stationScale = stationScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        labelsMode = MetroLabelsMode.entries.firstOrNull { it.name == labelsMode } ?: MetroLabelsMode.AUTO,
        showTransferLinks = showTransferLinks,
        // Glue at 8 or 12 points; anything else is off.
        glue = glue.takeIf { it == 8 || it == 12 } ?: 0,
        // A bend's points come in pairs; one whose x and y do not pair up, or are not numbers, is dropped.
        bends = bends.mapNotNull { b ->
            if (b.x.size != b.y.size || b.x.isEmpty()) return@mapNotNull null
            val pts = b.x.zip(b.y).map { (x, y) -> MetroMapPoint(x, y) }
            if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) null else MetroBend(b.aStationId, b.bStationId, pts)
        },
    )
    return reconciled(scheme, scheme)
}

/** Closed or not; a day this build cannot read leaves it closed with no day, never open. */
private fun metroClosureFromDto(closed: Boolean, expectedOpeningEpochDay: Long?): MetroClosure? {
    if (!closed) return null
    val day = expectedOpeningEpochDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
    return MetroClosure(day)
}

/* ---------------- travel ---------------- */

/**
 * The travel records read back. A trip with a month or year that cannot be
 * is dropped, the rest of the country kept; a continent this build does not
 * know is no continent — the base's applies — except for the user's own
 * country, which needs one and gets Europe rather than vanish; a user's
 * country without a place on the map keeps its trips and has no dot; a
 * second record for the same country is joined into the first.
 */
internal fun travelCountriesFromDto(dtos: List<TravelCountryDto>): List<TravelCountryRecord> {
    val out = LinkedHashMap<String, TravelCountryRecord>()
    dtos.forEach { d ->
        val id = d.countryId.trim()
        if (id.isEmpty()) return@forEach
        val trips = d.trips
            .filter { it.year in TRAVEL_YEARS && it.month in 1..12 }
            .map { TravelTrip(it.id, it.year, it.month, travelCleanCities(it.cities)) }
        val continent = TravelContinent.entries.firstOrNull { it.name == d.continent }
        val user = id.startsWith(TRAVEL_USER_COUNTRY_PREFIX)
        val record = TravelCountryRecord(
            countryId = id,
            trips = trips,
            continentOverride = continent ?: if (user) TravelContinent.EUROPE else null,
            customName = if (user) d.name?.trim()?.takeIf { it.isNotEmpty() } ?: id else null,
            customPoint = if (user && d.mapX != null && d.mapY != null) TravelMapPoint(d.mapX, d.mapY) else null,
        )
        val before = out[id]
        out[id] = if (before == null) record else before.copy(trips = before.trips + record.trips)
    }
    return out.values.filter { it.isUserCountry || it.trips.isNotEmpty() || it.continentOverride != null }
}

/* ---------------- flights ---------------- */

internal fun Flight.toDto(): FlightDto = FlightDto(
    id = id, from = from, to = to,
    departure = departure.toString(), arrival = arrival.toString(),
    departureOffsetMinutes = departureOffsetMinutes, arrivalOffsetMinutes = arrivalOffsetMinutes,
    actualDeparture = actualDeparture?.toString(), actualArrival = actualArrival?.toString(),
    airline = airline, number = number, aircraft = aircraft,
    seatClass = seatClass, seat = seat, booking = booking, note = note,
    journeyId = journeyId, keptApart = keptApart,
)

private fun flightTime(s: String?): java.time.LocalDateTime? =
    s?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

/**
 * The flight log read back. A flight whose times cannot be read, or with no
 * airports, is dropped; a second flight with an id already taken too. A
 * journey pointing at a flight that is not there is forgotten, and so is
 * an offset beyond any clock on Earth (±18 h).
 */
internal fun flightsFromDto(dtos: List<FlightDto>): List<Flight> {
    val flights = dtos.mapNotNull { d ->
        val dep = flightTime(d.departure) ?: return@mapNotNull null
        val arr = flightTime(d.arrival) ?: return@mapNotNull null
        if (d.from.isBlank() || d.to.isBlank()) return@mapNotNull null
        fun offset(m: Int?) = m?.takeIf { it in -18 * 60..18 * 60 }
        Flight(
            id = d.id, from = d.from.trim().uppercase(), to = d.to.trim().uppercase(),
            departure = dep, arrival = arr,
            departureOffsetMinutes = offset(d.departureOffsetMinutes), arrivalOffsetMinutes = offset(d.arrivalOffsetMinutes),
            actualDeparture = flightTime(d.actualDeparture), actualArrival = flightTime(d.actualArrival),
            airline = d.airline?.takeIf { it.isNotBlank() }, number = d.number?.takeIf { it.isNotBlank() },
            aircraft = d.aircraft?.takeIf { it.isNotBlank() },
            seatClass = d.seatClass?.takeIf { it.isNotBlank() }, seat = d.seat?.takeIf { it.isNotBlank() },
            booking = d.booking?.takeIf { it.isNotBlank() }, note = d.note,
            journeyId = d.journeyId, keptApart = d.keptApart,
        )
    }.distinctBy { it.id }
    val ids = flights.mapTo(HashSet()) { it.id }
    return flights.map { if (it.journeyId != null && it.journeyId !in ids) it.copy(journeyId = null) else it }
}
