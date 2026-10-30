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
    doneSubtasksLast = doneSubtasksLast,
    askBeforeDeleting = askBeforeDeleting,
    undoneHorizonDays = undoneHorizonDays,
    themeMode = themeMode.name,
    palette = palette.name,
    backStyle = backStyle.name,
    mainMenuNarrow = mainMenuNarrow.name,
    dayScreenStyle = dayScreenStyle.name,
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
    readingWaitingPrefs = readingWaitingPrefs.toDto(),
    anthropometryShowForecast = anthropometryShowForecast,
    anthropometryShowEntries = anthropometryShowEntries,
    anthropometryRange = anthropometryRange.name,
    anthropometryCustomFromEpochDay = anthropometryCustomRange?.from?.toEpochDay(),
    anthropometryCustomToEpochDay = anthropometryCustomRange?.to?.toEpochDay(),
    stepDays = stepDays.map { StepDayDto(it.date.toEpochDay(), it.steps, it.source.name) },
    stepsHeightCm = stepsHeightCm,
    profileSex = profileSex?.name,
    profileBirthYear = profileBirthYear,
    weightForecastMethod = weightForecastMethod.name,
    dailyNormKcal = dailyNormKcal,
    anthropometryCarryLast = anthropometryCarryLast,
    stepsRange = stepsRange.name,
    stepsCustomFromEpochDay = stepsCustomRange?.from?.toEpochDay(),
    stepsCustomToEpochDay = stepsCustomRange?.to?.toEpochDay(),
    calendarShowSteps = calendarShowSteps,
    stepsChartStyle = stepsChartStyle.name,
    stepsFriendsBest = stepsFriendsBest,
    stepsWidgetLine = stepsWidgetLine.name,
    stepsWidgetRecord = stepsWidgetRecord,
    stepsGoal = stepsGoal,
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
    metroPrefs = MetroPrefsDto(metroPrefs.style.name, metroPrefs.styleDetails, metroPrefs.eraColors, metroPrefs.loupe, metroPrefs.haptics, metroPrefs.boxSelect, metroPrefs.tips, metroPrefs.zoomButtons, metroPrefs.doubleTapZoom, metroPrefs.frameRoute, metroPrefs.legend, metroPrefs.stationCard, metroPrefs.minimap, metroPrefs.recentRoutes, metroPrefs.routeTimeline, metroPrefs.arrivalTime, metroPrefs.moveWholeInterchange),
    metroRecent = metroRecent.map { MetroRecentRouteDto(it.schemeKey, it.fromStationId, it.toStationId, it.atEpochMillis, it.pinned) },
    musicPrefs = MusicPrefsDto(
        style = musicPrefs.style.name,
        styleDetails = musicPrefs.styleDetails,
        tab = musicPrefs.tab,
        artist = musicPrefs.artist,
        sort = musicPrefs.sort.name,
        artistTab = musicPrefs.artistTab,
        cacheWhilePlaying = musicPrefs.cacheWhilePlaying,
        downloadOnWifiOnly = musicPrefs.downloadOnWifiOnly,
    ),
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
        doneSubtasksLast = doneSubtasksLast,
        askBeforeDeleting = askBeforeDeleting,
        undoneHorizonDays = normalizeUndoneHorizonDays(undoneHorizonDays),
        themeMode = parseAppThemeMode(themeMode),
        palette = parseAppPalette(palette),
        backStyle = parseBackStyle(backStyle),
        mainMenuNarrow = parseMainMenuNarrowStyle(mainMenuNarrow),
        dayScreenStyle = parseDayScreenStyle(dayScreenStyle),
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
        readingWaitingPrefs = readingWaitingPrefs.toDomain(),
        anthropometryShowForecast = anthropometryShowForecast,
        anthropometryShowEntries = anthropometryShowEntries,
        anthropometryRange = anthropometryRangeFromName(anthropometryRange),
        anthropometryCustomRange = dateWindowFromEpochDays(anthropometryCustomFromEpochDay, anthropometryCustomToEpochDay),
        stepDays = normalizedStepDays(
            stepDays.map { dto ->
                StepDay(
                    date = LocalDate.ofEpochDay(dto.dateEpochDay),
                    steps = dto.steps,
                    source = runCatching { StepSource.valueOf(dto.source) }.getOrElse { StepSource.MANUAL },
                )
            }
        ),
        stepsHeightCm = stepsHeightCm?.takeIf { it in STEPS_HEIGHT_RANGE_CM },
        profileSex = profileSex?.let { raw -> runCatching { ProfileSex.valueOf(raw) }.getOrNull() },
        profileBirthYear = profileBirthYear?.takeIf { it in PROFILE_BIRTH_YEARS },
        weightForecastMethod = parseForecastMethod(weightForecastMethod),
        dailyNormKcal = dailyNormKcal?.takeIf { it in DAILY_NORM_RANGE },
        anthropometryCarryLast = anthropometryCarryLast,
        stepsRange = anthropometryRangeFromName(stepsRange),
        stepsCustomRange = dateWindowFromEpochDays(stepsCustomFromEpochDay, stepsCustomToEpochDay),
        calendarShowSteps = calendarShowSteps,
        stepsChartStyle = runCatching { StepsChartStyle.valueOf(stepsChartStyle) }.getOrElse { StepsChartStyle.BARS },
        stepsFriendsBest = stepsFriendsBest,
        stepsWidgetLine = runCatching { StepsWidgetLine.valueOf(stepsWidgetLine) }.getOrElse { StepsWidgetLine.YESTERDAY },
        stepsWidgetRecord = stepsWidgetRecord,
        stepsGoal = stepsGoal.coerceIn(STEPS_GOAL_RANGE),
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
        metroPrefs = MetroPrefs(parseMetroStyle(metroPrefs.style), metroPrefs.styleDetails, metroPrefs.eraColors, metroPrefs.loupe, metroPrefs.haptics, metroPrefs.boxSelect, metroPrefs.tips, metroPrefs.zoomButtons, metroPrefs.doubleTapZoom, metroPrefs.frameRoute, metroPrefs.legend, metroPrefs.stationCard, metroPrefs.minimap, metroPrefs.recentRoutes, metroPrefs.routeTimeline, metroPrefs.arrivalTime, metroPrefs.moveWholeInterchange),
        // A route without a scheme to look it up on is no route.
        metroRecent = metroRecent
            .filter { it.schemeKey.isNotBlank() }
            .map { MetroRecentRoute(it.schemeKey, it.fromStationId, it.toStationId, it.atEpochMillis, it.pinned) },
        // A list or an order this build does not know is all songs, the newest first.
        musicPrefs = MusicPrefs(
            style = parseMetroStyle(musicPrefs.style),
            styleDetails = musicPrefs.styleDetails,
            tab = musicTabKey(parseMusicTab(musicPrefs.tab)),
            artist = musicPrefs.artist?.trim()?.takeIf { it.isNotEmpty() },
            sort = parseMusicSort(musicPrefs.sort),
            artistTab = musicPrefs.artistTab,
            cacheWhilePlaying = musicPrefs.cacheWhilePlaying,
            downloadOnWifiOnly = musicPrefs.downloadOnWifiOnly,
        ),
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
    seasonMinute = seasonMinute,
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
    seasonMinute = seasonMinute.takeIf { it in 0 until 24 * 60 } ?: DEFAULT_SEASON_MINUTE,
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

    // The day counter's correction rides in balance; an older version, not
    // knowing the kind, leaves the counter out rather than misreading it.
    is DayTimerCounter -> CounterDto(
        id = id,
        kind = "DAY_TIMER",
        title = title,
        startEpochDay = startDate.toEpochDay(),
        balance = correction,
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

    "DAY_TIMER" -> startEpochDay?.let { s ->
        DayTimerCounter(id = id, title = title, startDate = LocalDate.ofEpochDay(s), correction = balance ?: 0)
    }

    else -> null
}
