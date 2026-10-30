package com.alphaomegos.annasagenda.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.app.*

/*
 * The store's mapping, second half (29.10, split from AppStateStoreMapping.kt, then 1 025 lines): reading, media and what follows them. Moved unchanged.
 */
/* ---------------------------
   Reading mapping helpers
---------------------------- */

internal fun parseReadingShelf(raw: String): ReadingShelf =
    runCatching { ReadingShelf.valueOf(raw) }.getOrElse { ReadingShelf.PLANS }

/** Only a series waits for its next season (15.10): a book or a film said to is planned. */
internal fun ReadingShelf.notWaiting(): ReadingShelf = if (this == ReadingShelf.WAITING) ReadingShelf.PLANS else this

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
        shelf = parseReadingShelf(shelf).notWaiting(),
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
        shelf = parseReadingShelf(shelf).notWaiting(),
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
    nextSeasonEpochDay = nextSeasonDate?.toEpochDay(),
    remindNextSeason = remindNextSeason,
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
        nextSeasonDate = nextSeasonEpochDay?.let { runCatching { java.time.LocalDate.ofEpochDay(it) }.getOrNull() },
        remindNextSeason = remindNextSeason,
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
            labelSide = it.labelSide,
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
    gridDivisions = gridDivisions,
    transfersTogether = transfersTogether,
    labelsOncePerInterchange = labelsOncePerInterchange,
    glue = glue,
    snapAngles = snapAngles,
    labelScale = labelScale,
    lineScale = lineScale,
    stationScale = stationScale,
    labelsMode = labelsMode.name,
    showTransferLinks = showTransferLinks,
    lineCasing = lineCasing,
    transferCapsules = transferCapsules,
    roundCorners = roundCorners,
    tickStations = tickStations,
    lineBadges = lineBadges,
    alignGuides = alignGuides,
    hiddenLines = hiddenLines,
    lockedLines = lockedLines,
    ghostHidden = ghostHidden,
    labelsAuto = labelsAuto,
    labelsAvoidCrowd = labelsAvoidCrowd,
    bundleTracks = bundleTracks,
    bundleStriped = bundleStriped,
    labelsWrap = labelsWrap,
    bends = bends.map { b -> MetroBendDto(b.aStationId, b.bStationId, b.points.map { it.x }, b.points.map { it.y }) },
    underlay = underlay?.let { MetroUnderlayDto(it.lat, it.lon, it.x, it.y, it.metersPerUnit, it.rotationDeg) },
    underlayShown = underlayShown,
    underlayOpacity = underlayOpacity,
    underlaySource = underlaySource.name,
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
            MetroStation(
                it.id, it.lineId, it.name, it.mapX, it.mapY, metroClosureFromDto(it.closed, it.expectedOpeningEpochDay),
                // A side the picture does not know is chosen by itself.
                labelSide = it.labelSide?.takeIf { s -> s in 0..METRO_LABEL_HIDDEN },
            )
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
        gridDivisions = gridDivisions.takeIf { it in METRO_GRID_DIVISIONS } ?: 2,
        transfersTogether = transfersTogether,
        labelsOncePerInterchange = labelsOncePerInterchange,
        snapAngles = snapAngles,
        // Sizes within what the panel offers; anything else is as before.
        labelScale = labelScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        lineScale = lineScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        stationScale = stationScale.takeIf { it.isFinite() }?.coerceIn(METRO_SCALE_MIN, METRO_SCALE_MAX) ?: 1f,
        labelsMode = MetroLabelsMode.entries.firstOrNull { it.name == labelsMode } ?: MetroLabelsMode.AUTO,
        showTransferLinks = showTransferLinks,
        lineCasing = lineCasing,
        transferCapsules = transferCapsules,
        roundCorners = roundCorners,
        tickStations = tickStations,
        lineBadges = lineBadges,
        alignGuides = alignGuides,
        hiddenLines = hiddenLines.distinct(),
        lockedLines = lockedLines.distinct(),
        ghostHidden = ghostHidden,
        labelsAuto = labelsAuto,
        labelsAvoidCrowd = labelsAvoidCrowd,
        bundleTracks = bundleTracks,
        bundleStriped = bundleStriped,
        labelsWrap = labelsWrap,
        // Glue at 8 or 12 points; anything else is off.
        glue = glue.takeIf { it == 8 || it == 12 } ?: 0,
        // A bend's points come in pairs; one whose x and y do not pair up, or are not numbers, is dropped.
        bends = bends.mapNotNull { b ->
            if (b.x.size != b.y.size || b.x.isEmpty()) return@mapNotNull null
            val pts = b.x.zip(b.y).map { (x, y) -> MetroMapPoint(x, y) }
            if (pts.any { !it.x.isFinite() || !it.y.isFinite() }) null else MetroBend(b.aStationId, b.bStationId, pts)
        },
        // A map laid where no map can be — off the Earth, at no scale — is no map.
        underlay = underlay?.let { u ->
            val ok = u.lat.isFinite() && u.lon.isFinite() && u.lat in -85.0..85.0 && u.lon in -180.0..180.0 &&
                u.x.isFinite() && u.y.isFinite() && u.metersPerUnit.isFinite() && u.metersPerUnit > 0f &&
                u.rotationDeg.isFinite()
            if (ok) MetroUnderlay(u.lat, u.lon, u.x, u.y, u.metersPerUnit, u.rotationDeg) else null
        },
        underlayShown = underlayShown,
        underlayOpacity = underlayOpacity.takeIf { it.isFinite() }?.coerceIn(METRO_UNDERLAY_MIN_OPACITY, 1f) ?: 0.5f,
        underlaySource = MetroTileSource.entries.firstOrNull { it.name == underlaySource } ?: MetroTileSource.CARTO_LIGHT,
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
