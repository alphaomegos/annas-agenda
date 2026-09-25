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
    undoneHorizonDays = undoneHorizonDays,
    themeMode = themeMode.name,
    idHighWater = idHighWater,
    readingBooks = readingBooks.map { it.toDto() },
    readingMovies = readingMovies.map { it.toDto() },
    readingSeries = readingSeries.map { it.toDto() },
    readingSessions = readingSessions.map { it.toDto() },
    activeReading = activeReading?.toDto(),
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
        counters = counters.mapNotNull { it.toDomainOrNull() },
        // Normalised on the way in like the other preferences here: the
        // setter normalises, but a payload does not have to have come from it.
        mainMenuOrder = normalizeMainMenuOrderIds(mainMenuOrder),
        mainMenuHiddenIds = mainMenuHiddenIds.toSet(),
        undoneLampMuted = undoneLampMuted,
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
        readingMediaFilter = readingMediaFilter.toDomain(),
        readingPlansPrefs = readingPlansPrefs.toDomain(),
        readingNowPrefs = readingNowPrefs.toDomain(),
        readingDonePrefs = readingDonePrefs.toDomain(),
        readingAbandonedPrefs = readingAbandonedPrefs.toDomain(),
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
