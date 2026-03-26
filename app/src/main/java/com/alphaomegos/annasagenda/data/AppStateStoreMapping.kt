package com.alphaomegos.annasagenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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
    counters = counters.map { it.toDto() },
    mainMenuOrder = mainMenuOrder,
    mainMenuHiddenIds = mainMenuHiddenIds.toList(),
    undoneLampMuted = undoneLampMuted,
    travelCountries = travelCountries.map { it.toDto() },
    readingBooks = readingBooks.map { it.toDto() },
    readingMovies = readingMovies.map { it.toDto() },
    readingSeries = readingSeries.map { it.toDto() },
    readingSessions = readingSessions.map { it.toDto() },
    readingMediaFilter = readingMediaFilter.toDto(),
    readingPlansPrefs = readingPlansPrefs.toDto(),
    readingNowPrefs = readingNowPrefs.toDto(),
    readingDonePrefs = readingDonePrefs.toDto(),
    readingAbandonedPrefs = readingAbandonedPrefs.toDto(),
)

internal fun normalizeAnthropometryFieldIdsForStore(ids: List<String>): Set<String> {
    val normalized = ids
        .asSequence()
        .map { it.trim() }
        .filter { it in allAnthropometryFieldIds() }
        .toSet()

    return normalized.ifEmpty { defaultAnthropometryFieldIds() }
}

internal fun AppStateDto.toDomain(): AppState = AppState(
    tasks = tasks.map { it.toDomain() },
    subtasks = subtasks.map { it.toDomain() },
    suppressedRecurrences = suppressedRecurrences.toSet(),
    anthropometry = anthropometry.map { it.toDomain() },
    anthropometryEnabledFieldIds = normalizeAnthropometryFieldIdsForStore(anthropometryEnabledFieldIds),
    calorieGoalChanges = calorieGoalChanges.map { it.toDomain() },
    foodLog = foodLog.map { it.toDomain() },
    runningPlanApproved = runningPlanApproved,
    runningPlanEntries = runningPlanEntries.map { it.toDomain() },
    counters = counters.mapNotNull { it.toDomainOrNull() },
    mainMenuOrder = mainMenuOrder,
    mainMenuHiddenIds = mainMenuHiddenIds.toSet(),
    undoneLampMuted = undoneLampMuted,
    travelCountries = travelCountries.mapNotNull { it.toDomainOrNull() },
    readingBooks = readingBooks.mapNotNull { it.toDomainOrNull() },
    readingMovies = readingMovies.mapNotNull { it.toDomainOrNull() },
    readingSeries = readingSeries.mapNotNull { it.toDomainOrNull() },
    readingSessions = readingSessions.mapNotNull { it.toDomainOrNull() },
    readingMediaFilter = readingMediaFilter.toDomain(),
    readingPlansPrefs = readingPlansPrefs.toDomain(),
    readingNowPrefs = readingNowPrefs.toDomain(),
    readingDonePrefs = readingDonePrefs.toDomain(),
    readingAbandonedPrefs = readingAbandonedPrefs.toDomain(),
)

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
)

internal fun RepeatRuleDto.toDomain(): RepeatRule = RepeatRule(
    freq = RepeatFreq.valueOf(freq),
    interval = interval,
    weekDays = weekDaysIso.map { DayOfWeek.of(it) }.toSet(),
    dayOfMonth = dayOfMonth,
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
)

internal fun FoodEntryDto.toDomain(): FoodEntry = FoodEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    title = title,
    kcal = kcal,
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
   Travel mapping helpers
---------------------------- */

internal fun parseTravelContinentOrNull(raw: String?): TravelContinent? =
    raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { TravelContinent.valueOf(it) }.getOrNull() }

internal fun TravelVisit.toDto(): TravelVisitDto = TravelVisitDto(
    year = year,
    month = month,
    cities = cities,
)

internal fun TravelVisitDto.toDomainOrNull(): TravelVisit? {
    if (year <= 0) return null
    if (month !in 1..12) return null

    return TravelVisit(
        year = year,
        month = month,
        cities = cities
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    )
}

internal fun TravelMapPoint.toDto(): TravelMapPointDto = TravelMapPointDto(
    x = x,
    y = y,
)

internal fun TravelMapPointDto.toDomainOrNull(): TravelMapPoint? {
    if (!x.isFinite() || !y.isFinite()) return null
    if (x !in 0f..1f) return null
    if (y !in 0f..1f) return null

    return TravelMapPoint(
        x = x,
        y = y,
    )
}

internal fun TravelCountryRecord.toDto(): TravelCountryRecordDto = TravelCountryRecordDto(
    countryId = countryId,
    trips = trips.map { it.toDto() },
    customName = customName,
    continentOverride = continentOverride?.name,
    customMapPoint = customMapPoint?.toDto(),
    isUserCreated = isUserCreated,
)

internal fun TravelCountryRecordDto.toDomainOrNull(): TravelCountryRecord? {
    val cleanCountryId = countryId.trim()
    if (cleanCountryId.isEmpty()) return null

    val cleanCustomName = customName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return TravelCountryRecord(
        countryId = cleanCountryId,
        trips = trips.mapNotNull { it.toDomainOrNull() },
        customName = cleanCustomName,
        continentOverride = parseTravelContinentOrNull(continentOverride),
        customMapPoint = customMapPoint?.toDomainOrNull(),
        isUserCreated = isUserCreated,
    )
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
        createdAtEpochMillis = createdAtEpochMillis,
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