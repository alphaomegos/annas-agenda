package com.alphaomegos.annasagenda

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.alphaomegos.annasagenda.util.isExternalCoverRef
import com.alphaomegos.annasagenda.util.resolveStoredCoverFiles
import com.alphaomegos.annasagenda.util.writeBackupToDocuments
import com.alphaomegos.annasagenda.util.writeInternalCoverBytes
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale


class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AppStateStore(app.applicationContext)

    private val newTaskDraftStore = NewTaskDraftStore(app.applicationContext)

    private val appContext
        get() = getApplication<Application>().applicationContext

    private fun cleanupInternalCoverAsync(ref: String?) {
        if (!isInternalCoverRef(ref)) return

        viewModelScope.launch {
            deleteInternalCoverIfAny(appContext, ref)
        }
    }

    private fun cleanupRemovedInternalCoversAsync(before: AppState, after: AppState) {
        val staleRefs = collectInternalCoverRefs(before) - collectInternalCoverRefs(after)
        if (staleRefs.isEmpty()) return

        viewModelScope.launch {
            staleRefs.forEach { ref ->
                deleteInternalCoverIfAny(appContext, ref)
            }
        }
    }

    private fun nextIdAfter(state: AppState): Long {
        val maxId =
            (state.tasks.map { it.id }
                    + state.subtasks.map { it.id }
                    + state.foodLog.map { it.id }
                    + state.counters.map { it.id }
                    + state.readingBooks.map { it.id }
                    + state.readingMovies.map { it.id }
                    + state.readingSeries.map { it.id }
                    + state.readingSessions.map { it.id })
                .maxOrNull() ?: 0L

        return maxId + 1L
    }

    private suspend fun migrateLegacyCoverRef(
        coverRef: String?,
        mediaKind: String,
        itemId: Long,
    ): String? {
        val source = coverRef?.takeIf(::isExternalCoverRef) ?: return coverRef

        return importCoverIntoInternalStorage(
            context = appContext,
            sourceUri = source.toUri(),
            mediaKind = mediaKind,
            itemId = itemId
        ) ?: coverRef
    }

    private suspend fun migrateLegacyMediaCovers(
        state: AppState,
    ): AppState {
        val migratedBooks = state.readingBooks.map { book ->
            val migratedCover = migrateLegacyCoverRef(
                coverRef = book.coverUri,
                mediaKind = "book",
                itemId = book.id
            )
            if (migratedCover == book.coverUri) {
                book
            } else {
                book.copy(coverUri = migratedCover)
            }
        }

        val migratedMovies = state.readingMovies.map { movie ->
            val migratedCover = migrateLegacyCoverRef(
                coverRef = movie.coverUri,
                mediaKind = "movie",
                itemId = movie.id
            )
            if (migratedCover == movie.coverUri) {
                movie
            } else {
                movie.copy(coverUri = migratedCover)
            }
        }

        val migratedSeries = state.readingSeries.map { series ->
            val migratedCover = migrateLegacyCoverRef(
                coverRef = series.coverUri,
                mediaKind = "series",
                itemId = series.id
            )
            if (migratedCover == series.coverUri) {
                series
            } else {
                series.copy(coverUri = migratedCover)
            }
        }

        val changed =
            migratedBooks != state.readingBooks ||
                    migratedMovies != state.readingMovies ||
                    migratedSeries != state.readingSeries

        return if (!changed) {
            state
        } else {
            state.copy(
                readingBooks = migratedBooks,
                readingMovies = migratedMovies,
                readingSeries = migratedSeries
            )
        }
    }

    fun setReadingBookCoverFromPickedUri(bookId: Long, sourceUri: Uri) {
        viewModelScope.launch {
            val existsBefore = _state.value.readingBooks.any { it.id == bookId }
            if (!existsBefore) return@launch

            val importedRef = importCoverIntoInternalStorage(
                context = appContext,
                sourceUri = sourceUri,
                mediaKind = "book",
                itemId = bookId
            ) ?: return@launch

            val existsAfter = _state.value.readingBooks.any { it.id == bookId }
            if (!existsAfter) {
                cleanupInternalCoverAsync(importedRef)
                return@launch
            }

            updateReadingBook(
                bookId = bookId,
                coverUri = importedRef,
                clearCover = false
            )
        }
    }

    fun setReadingMovieCoverFromPickedUri(movieId: Long, sourceUri: Uri) {
        viewModelScope.launch {
            val existsBefore = _state.value.readingMovies.any { it.id == movieId }
            if (!existsBefore) return@launch

            val importedRef = importCoverIntoInternalStorage(
                context = appContext,
                sourceUri = sourceUri,
                mediaKind = "movie",
                itemId = movieId
            ) ?: return@launch

            val existsAfter = _state.value.readingMovies.any { it.id == movieId }
            if (!existsAfter) {
                cleanupInternalCoverAsync(importedRef)
                return@launch
            }

            updateReadingMovie(
                movieId = movieId,
                coverUri = importedRef,
                clearCover = false
            )
        }
    }

    fun setReadingSeriesCoverFromPickedUri(seriesId: Long, sourceUri: Uri) {
        viewModelScope.launch {
            val existsBefore = _state.value.readingSeries.any { it.id == seriesId }
            if (!existsBefore) return@launch

            val importedRef = importCoverIntoInternalStorage(
                context = appContext,
                sourceUri = sourceUri,
                mediaKind = "series",
                itemId = seriesId
            ) ?: return@launch

            val existsAfter = _state.value.readingSeries.any { it.id == seriesId }
            if (!existsAfter) {
                cleanupInternalCoverAsync(importedRef)
                return@launch
            }

            updateReadingSeries(
                seriesId = seriesId,
                coverUri = importedRef,
                clearCover = false
            )
        }
    }

    fun removeReadingBookCover(bookId: Long) {
        updateReadingBook(bookId = bookId, clearCover = true)
    }

    fun removeReadingMovieCover(movieId: Long) {
        updateReadingMovie(movieId = movieId, clearCover = true)
    }

    fun removeReadingSeriesCover(seriesId: Long) {
        updateReadingSeries(seriesId = seriesId, clearCover = true)
    }

    private val newTaskDraftSaveRequests = MutableSharedFlow<NewTaskDraft>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = store.load()
            val migrated = migrateLegacyMediaCovers(loaded)

            _state.value = migrated
            nextId = nextIdAfter(migrated)

            if (migrated != loaded) {
                store.save(migrated)
            }

            _isLoaded.value = true
            startNewTaskDraftAutoSave()
            startAutoSave()
        }
    }

    @OptIn(FlowPreview::class)
    private fun startNewTaskDraftAutoSave() {
        viewModelScope.launch {
            newTaskDraftSaveRequests
                .debounce(350)
                .distinctUntilChanged()
                .collect { newTaskDraftStore.save(it) }
        }
    }

    private fun applyManualCounterDelta(
        counters: List<Counter>,
        counterId: Long,
        delta: Int
    ): List<Counter> {
        return counters.map { c ->
            if (c is ManualCounter && c.id == counterId) c.copy(balance = c.balance + delta) else c
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun startAutoSave() {
        state
            .drop(1)
            .debounce(400)
            .collect { store.save(it) }
    }

    fun resetAllData() {
        val before = _state.value
        val empty = AppState()

        _state.value = empty
        nextId = 1L
        _activeReading.value = null

        cleanupRemovedInternalCoversAsync(before, empty)

        viewModelScope.launch {
            store.save(empty)
        }
    }

    fun exportBackupJson(): String {
        return store.encodeToJson(_state.value)
    }

    suspend fun exportBackupToDocuments() {
        val before = _state.value
        val current = migrateLegacyMediaCovers(before)

        if (current != before) {
            _state.value = current
            store.save(current)
        }

        val json = store.encodeToJson(current)
        val coverFiles = resolveStoredCoverFiles(appContext, current)

        writeBackupToDocuments(
            context = appContext,
            json = json,
            coverFiles = coverFiles
        )
    }
    suspend fun loadNewTaskDraft(): NewTaskDraft? {
        return newTaskDraftStore.load()
    }

    fun queueNewTaskDraftSave(draft: NewTaskDraft) {
        newTaskDraftSaveRequests.tryEmit(draft)
    }

    fun clearNewTaskDraft() {
        newTaskDraftSaveRequests.tryEmit(NewTaskDraft())
        viewModelScope.launch {
            newTaskDraftStore.clear()
        }
    }

    fun importBackupJson(raw: String): Boolean {
        val decoded = store.decodeFromJson(raw) ?: return false

        viewModelScope.launch {
            val migrated = migrateLegacyMediaCovers(decoded)
            val before = _state.value

            _state.value = migrated
            nextId = nextIdAfter(migrated)
            _activeReading.value = null

            cleanupRemovedInternalCoversAsync(before, migrated)
            store.save(migrated)
        }

        return true
    }

    suspend fun importBackupPackage(
        appStateJson: String,
        coverEntries: Map<String, ByteArray>,
    ): Boolean {
        val decoded = store.decodeFromJson(appStateJson) ?: return false
        val before = _state.value

        val expectedRefs = collectInternalCoverRefs(decoded)

        expectedRefs.forEach { ref ->
            if (ref !in coverEntries.keys) {
                deleteInternalCoverIfAny(appContext, ref)
            }
        }

        coverEntries.forEach { (ref, bytes) ->
            if (ref in expectedRefs) {
                writeInternalCoverBytes(
                    context = appContext,
                    coverRef = ref,
                    bytes = bytes
                )
            }
        }

        val migrated = migrateLegacyMediaCovers(decoded)

        _state.value = migrated
        nextId = nextIdAfter(migrated)
        _activeReading.value = null

        cleanupRemovedInternalCoversAsync(before, migrated)
        store.save(migrated)

        return true
    }

    /* ---------------------------
   Main menu ordering / visibility
---------------------------- */
        fun setMainMenuOrder(ids: List<String>) {
        val normalized = normalizeMainMenuOrderIds(ids)

        val cur = _state.value
        if (cur.mainMenuOrder == normalized) return
        _state.value = cur.copy(mainMenuOrder = normalized)
    }

        fun hideMainMenuItem(id: String) {
            val normalizedId = normalizeMainMenuItemId(id) ?: return

        val cur = _state.value
        if (normalizedId in cur.mainMenuHiddenIds) return

        _state.value = cur.copy(
            mainMenuHiddenIds = cur.mainMenuHiddenIds + normalizedId
        )
    }

    fun showAllMainMenuItems() {
        val cur = _state.value
        if (cur.mainMenuHiddenIds.isEmpty()) return

        _state.value = cur.copy(mainMenuHiddenIds = emptySet())
    }

    fun setUndoneLampMuted(value: Boolean) {
        val cur = _state.value
        if (cur.undoneLampMuted == value) return
        _state.value = cur.copy(undoneLampMuted = value)
    }

    fun toggleUndoneLampMuted() {
        val cur = _state.value
        _state.value = cur.copy(undoneLampMuted = !cur.undoneLampMuted)
    }

    fun hasUndonePastTasks(today: LocalDate = LocalDate.now()): Boolean {
        val yesterday = today.minusDays(1)
        return _state.value.tasks.any { task ->
            val date = task.date
            date != null &&
                    !task.isDone &&
                    !date.isAfter(yesterday)
        }
    }

    fun undonePastTaskDates(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val yesterday = today.minusDays(1)
        return _state.value.tasks
            .mapNotNull { task ->
                val date = task.date
                if (date != null && !task.isDone && !date.isAfter(yesterday)) {
                    date
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
    }

    /* ---------------------------
       Travel
    ---------------------------- */

    fun addTravelTrip(
        countryId: String,
        year: Int,
        month: Int,
        cities: List<String>,
    ): Boolean {
        val normalizedCountryId = countryId
            .trim()
            .uppercase()
            .takeIf { it.isNotEmpty() }
            ?: return false

        if (year <= 0) return false
        if (month !in 1..12) return false

        val cleanCities = cities
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val newTrip = TravelVisit(
            year = year,
            month = month,
            cities = cleanCities,
        )

        val cur = _state.value
        val idx = cur.travelCountries.indexOfFirst {
            it.countryId.trim().uppercase() == normalizedCountryId
        }

        val updatedTravelCountries =
            if (idx >= 0) {
                val existing = cur.travelCountries[idx]
                cur.travelCountries.toMutableList().apply {
                    this[idx] = existing.copy(
                        countryId = normalizedCountryId,
                        trips = (existing.trips + newTrip)
                            .sortedWith(compareBy<TravelVisit>({ it.year }, { it.month }))
                    )
                }
            } else {
                cur.travelCountries + TravelCountryRecord(
                    countryId = normalizedCountryId,
                    trips = listOf(newTrip),
                )
            }

        _state.value = cur.copy(travelCountries = updatedTravelCountries)
        return true
    }

    /* ---------------------------
       Reading
    ---------------------------- */

    data class ActiveReading(
        val bookId: Long,
        val startedAtEpochMillis: Long,
        val startPage: Int
    )

    private val _activeReading = MutableStateFlow<ActiveReading?>(null)
    val activeReading: StateFlow<ActiveReading?> = _activeReading.asStateFlow()


    fun setReadingViewMode(shelf: ReadingShelf, mode: ReadingViewMode) {
        val st = _state.value
        val prefs = readingTabPrefsForShelf(st, shelf)
        if (prefs.viewMode == mode) return
        _state.value = readingStateWithTabPrefs(st, shelf, prefs.copy(viewMode = mode))
    }

    fun setReadingSort(shelf: ReadingShelf, field: ReadingSortField, ascending: Boolean) {
        val st = _state.value
        val prefs = readingTabPrefsForShelf(st, shelf)
        val newSort = ReadingSort(field = field, ascending = ascending)
        if (prefs.sort == newSort) return
        _state.value = readingStateWithTabPrefs(st, shelf, prefs.copy(sort = newSort))
    }

    fun setReadingMediaFilter(
        showBooks: Boolean? = null,
        showMovies: Boolean? = null,
        showSeries: Boolean? = null,
    ) {
        val st = _state.value
        val old = st.readingMediaFilter
        val updated = old.copy(
            showBooks = showBooks ?: old.showBooks,
            showMovies = showMovies ?: old.showMovies,
            showSeries = showSeries ?: old.showSeries,
        )
        if (updated == old) return
        _state.value = st.copy(readingMediaFilter = updated)
    }

    fun addReadingBook(
        shelf: ReadingShelf,
        title: String,
        totalPages: Int,
        author: String = "",
        coverUri: String? = null
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val book = buildReadingBook(
            id = newId(),
            shelf = shelf,
            title = title,
            totalPages = totalPages,
            author = author,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = _state.value
        _state.value = st.copy(readingBooks = st.readingBooks + book)
        return book.id
    }

    fun deleteReadingBook(bookId: Long) {
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return

        if (_activeReading.value?.bookId == bookId) {
            _activeReading.value = null
        }

        _state.value = st.copy(
            readingBooks = st.readingBooks.filterNot { it.id == bookId },
            readingSessions = st.readingSessions.filterNot { it.bookId == bookId }
        )

        cleanupInternalCoverAsync(book.coverUri)
    }

    fun moveReadingBookToShelf(bookId: Long, shelf: ReadingShelf) {
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return
        if (book.shelf == shelf) return

        if (_activeReading.value?.bookId == bookId && shelf != ReadingShelf.NOW) {
            _activeReading.value = null
        }

        val currentYear = LocalDate.now().year

        val updatedBooks = st.readingBooks.map { b ->
            if (b.id != bookId) b
            else moveReadingBookToShelf(b, shelf, currentYear)
        }

        _state.value = st.copy(readingBooks = updatedBooks)
    }
    fun updateReadingBook(
        bookId: Long,
        author: String? = null,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        totalPages: Int? = null,
        currentPage: Int? = null,
        yearRead: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
    ) {
        val st = _state.value
        val old = st.readingBooks.firstOrNull { it.id == bookId } ?: return

        val oldCover = old.coverUri

        val currentYear = LocalDate.now().year

        val updated = updateReadingBookEntity(
            old = old,
            author = author,
            title = title,
            coverUri = coverUri,
            clearCover = clearCover,
            totalPages = totalPages,
            currentPage = currentPage,
            yearRead = yearRead,
            yearAbandoned = yearAbandoned,
            shelf = shelf,
            currentYear = currentYear,
        )

        if (_activeReading.value?.bookId == bookId) {
            _activeReading.value =
                if (updated.shelf == ReadingShelf.NOW) {
                    _activeReading.value?.copy(startPage = updated.currentPage)
                } else {
                    null
                }
        }

        _state.value = st.copy(
            readingBooks = st.readingBooks.map { b -> if (b.id == bookId) updated else b }
        )

        if (oldCover != updated.coverUri) {
            cleanupInternalCoverAsync(oldCover)
        }
    }

    fun addReadingMovie(
        shelf: ReadingShelf,
        title: String,
        releaseYear: Int? = null,
        translation: String = "",
        coverUri: String? = null,
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val movie = buildReadingMovie(
            id = newId(),
            shelf = shelf,
            title = title,
            releaseYear = releaseYear,
            translation = translation,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = _state.value
        _state.value = st.copy(readingMovies = st.readingMovies + movie)
        return movie.id
    }

    fun deleteReadingMovie(movieId: Long) {
        val st = _state.value
        val movie = st.readingMovies.firstOrNull { it.id == movieId } ?: return

        _state.value = st.copy(
            readingMovies = st.readingMovies.filterNot { it.id == movieId }
        )

        cleanupInternalCoverAsync(movie.coverUri)
    }

    fun moveReadingMovieToShelf(movieId: Long, shelf: ReadingShelf) {
        val st = _state.value
        val movie = st.readingMovies.firstOrNull { it.id == movieId } ?: return
        if (movie.shelf == shelf) return

        val currentYear = LocalDate.now().year

        val updatedMovies = st.readingMovies.map { m ->
            if (m.id != movieId) m
            else moveReadingMovieToShelf(m, shelf, currentYear)
        }

        _state.value = st.copy(readingMovies = updatedMovies)
    }

    fun updateReadingMovie(
        movieId: Long,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        releaseYear: Int? = null,
        clearReleaseYear: Boolean = false,
        translation: String? = null,
        yearWatched: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
    ) {
        val st = _state.value
        val old = st.readingMovies.firstOrNull { it.id == movieId } ?: return

        val oldCover = old.coverUri

        val currentYear = LocalDate.now().year

        val updated = updateReadingMovieEntity(
            old = old,
            title = title,
            coverUri = coverUri,
            clearCover = clearCover,
            releaseYear = releaseYear,
            clearReleaseYear = clearReleaseYear,
            translation = translation,
            yearWatched = yearWatched,
            yearAbandoned = yearAbandoned,
            shelf = shelf,
            currentYear = currentYear,
        )

        _state.value = st.copy(
            readingMovies = st.readingMovies.map { m -> if (m.id == movieId) updated else m }
        )

        if (oldCover != updated.coverUri) {
            cleanupInternalCoverAsync(oldCover)
        }
    }

    fun addReadingSeries(
        shelf: ReadingShelf,
        title: String,
        totalSeasons: Int = 1,
        currentSeason: Int = 1,
        currentEpisode: Int = 1,
        coverUri: String? = null,
    ): Long? {
        val now = System.currentTimeMillis()
        val currentYear = LocalDate.now().year

        val series = buildReadingSeries(
            id = newId(),
            shelf = shelf,
            title = title,
            totalSeasons = totalSeasons,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            coverUri = coverUri,
            createdAtEpochMillis = now,
            currentYear = currentYear,
        ) ?: return null

        val st = _state.value
        _state.value = st.copy(readingSeries = st.readingSeries + series)
        return series.id
    }

    fun deleteReadingSeries(seriesId: Long) {
        val st = _state.value
        val series = st.readingSeries.firstOrNull { it.id == seriesId } ?: return

        _state.value = st.copy(
            readingSeries = st.readingSeries.filterNot { it.id == seriesId }
        )

        cleanupInternalCoverAsync(series.coverUri)
    }

    fun moveReadingSeriesToShelf(seriesId: Long, shelf: ReadingShelf) {
        val st = _state.value
        val series = st.readingSeries.firstOrNull { it.id == seriesId } ?: return
        if (series.shelf == shelf) return

        val currentYear = LocalDate.now().year

        val updatedSeries = st.readingSeries.map { s ->
            if (s.id != seriesId) s
            else moveReadingSeriesToShelf(s, shelf, currentYear)
        }

        _state.value = st.copy(readingSeries = updatedSeries)
    }

    fun updateReadingSeries(
        seriesId: Long,
        title: String? = null,
        coverUri: String? = null,
        clearCover: Boolean = false,
        totalSeasons: Int? = null,
        currentSeason: Int? = null,
        currentEpisode: Int? = null,
        yearWatched: Int? = null,
        yearAbandoned: Int? = null,
        shelf: ReadingShelf? = null,
    ) {
        val st = _state.value
        val old = st.readingSeries.firstOrNull { it.id == seriesId } ?: return

        val oldCover = old.coverUri

        val currentYear = LocalDate.now().year

        val updated = updateReadingSeriesEntity(
            old = old,
            title = title,
            coverUri = coverUri,
            clearCover = clearCover,
            totalSeasons = totalSeasons,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            yearWatched = yearWatched,
            yearAbandoned = yearAbandoned,
            shelf = shelf,
            currentYear = currentYear,
        )

        _state.value = st.copy(
            readingSeries = st.readingSeries.map { s -> if (s.id == seriesId) updated else s }
        )

        if (oldCover != updated.coverUri) {
            cleanupInternalCoverAsync(oldCover)
        }
    }


    fun beginReading(bookId: Long, startedAtEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return false

        if (book.shelf == ReadingShelf.PLANS) {
            moveReadingBookToShelf(bookId, ReadingShelf.NOW)
        }

        val after = _state.value.readingBooks.firstOrNull { it.id == bookId } ?: return false
        _activeReading.value = ActiveReading(
            bookId = bookId,
            startedAtEpochMillis = startedAtEpochMillis,
            startPage = after.currentPage.coerceAtLeast(0)
        )
        return true
    }

    fun cancelReading() {
        _activeReading.value = null
    }

    // Backward-compatible wrapper.
       fun finishReading(
        startPage: Int,
        endPage: Int,
        durationMinutes: Int,
        finishedAtEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val active = _activeReading.value ?: return false
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == active.bookId } ?: return false

        val pages = book.totalPages.coerceAtLeast(1)
        val start = startPage.coerceIn(0, pages)
        val end = endPage.coerceIn(0, pages)
        val dur = durationMinutes.coerceAtLeast(1)

        val session = ReadingSession(
            id = newId(),
            bookId = book.id,
            startedAtEpochMillis = active.startedAtEpochMillis,
            durationMinutes = dur,
            startPage = start,
            endPage = end,
            createdAtEpochMillis = finishedAtEpochMillis
        )

        val updatedBooks = st.readingBooks.map { b ->
            if (b.id == book.id) b.copy(currentPage = end.coerceIn(0, b.totalPages)) else b
        }

        _state.value = st.copy(
            readingBooks = updatedBooks,
            readingSessions = st.readingSessions + session
        )

        _activeReading.value = null
        return true
    }

    fun estimateRemainingHours(bookId: Long): Int? {
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return null
        return estimateRemainingReadingHours(
            book = book,
            sessions = st.readingSessions,
        )
    }

       private fun formatRunningTaskKmTitle(kmTitle: String): String =
        getApplication<Application>().getString(R.string.running_task_km, kmTitle)

    private fun formatRunningTaskMinutesTitle(minutes: Int): String =
        getApplication<Application>().getString(R.string.running_task_minutes, minutes)

    /* ---------------------------
       Running plan ("On the run")
    ---------------------------- */

    fun updateRunningPlanEntry(
        date: LocalDate,
        distanceKmText: String? = null,
        durationHhMmText: String? = null,
        paceText: String? = null,
    ) {
        val st = _state.value

        val list = st.runningPlanEntries.toMutableList()
        val idx = list.indexOfFirst { it.date == date }
        val base = if (idx >= 0) list[idx] else RunningPlanEntry(date = date)

        val updatedRaw = base.copy(
            distanceKmText = distanceKmText ?: base.distanceKmText,
            durationHhMmText = durationHhMmText ?: base.durationHhMmText,
            paceText = if (st.runningPlanApproved) (paceText ?: base.paceText) else base.paceText,
        )

        val nowEmpty =
            updatedRaw.distanceKmText.isBlank() &&
                    updatedRaw.durationHhMmText.isBlank() &&
                    updatedRaw.paceText.isBlank()

        if (nowEmpty) {
            if (st.runningPlanApproved) {
                if (idx >= 0) {
                    list[idx] = updatedRaw
                } else {
                    return
                }
            } else {
                if (updatedRaw.taskId != null) deleteTask(updatedRaw.taskId)
                if (idx >= 0) list.removeAt(idx)
            }
        } else {
            if (idx >= 0) {
                list[idx] = updatedRaw
            } else {
                list.add(updatedRaw)
            }
        }

        _state.value = _state.value.copy(
            runningPlanEntries = list.sortedBy { it.date }
        )

        if (st.runningPlanApproved) {
            val after = _state.value.runningPlanEntries.firstOrNull { it.date == date } ?: return
            val title = buildRunningPlanTaskTitle(
                entry = after,
                formatKmTitle = ::formatRunningTaskKmTitle,
                formatMinutesTitle = ::formatRunningTaskMinutesTitle,
            )

            when {
                title == null -> {
                    // Keep the approved row (and any existing task) untouched.
                    // Timeout cleanup is handled later by pruneRunningPlanNow().
                }

                after.taskId != null -> {
                    updateTaskDescription(after.taskId, title)
                }

                else -> {
                    val newTaskId = createTaskForDate(
                        date = after.date,
                        time = null,
                        description = title,
                    )

                    _state.value = _state.value.copy(
                        runningPlanEntries = _state.value.runningPlanEntries
                            .map { entry ->
                                if (entry.date == date) {
                                    entry.copy(taskId = newTaskId)
                                } else {
                                    entry
                                }
                            }
                            .sortedBy { it.date }
                    )
                }
            }
        }
    }


    fun addRunningPlanBonusEntry(date: LocalDate): Boolean {
        val st = _state.value
        if (!st.runningPlanApproved) return false
        if (st.runningPlanEntries.any { it.date == date }) return false

        val updated = (st.runningPlanEntries + RunningPlanEntry(
            date = date,
            isBonus = true,
        )).sortedBy { it.date }

        _state.value = st.copy(runningPlanEntries = updated)
        return true
    }


    fun approveRunningPlan() {
        pruneRunningPlanNow()

        val before = _state.value
        val cleaned = before.runningPlanEntries
            .map {
                it.copy(
                    distanceKmText = it.distanceKmText.trim(),
                    durationHhMmText = it.durationHhMmText.trim(),
                    paceText = it.paceText.trim(),
                )
            }
            .filter { it.distanceKmText.isNotBlank() || it.durationHhMmText.isNotBlank() || it.paceText.isNotBlank() }

        val updated = cleaned.map { e0 ->
            var e = e0

            if (e.taskId == null) {
                val title = buildRunningPlanTaskTitle(
                    entry = e,
                    formatKmTitle = ::formatRunningTaskKmTitle,
                    formatMinutesTitle = ::formatRunningTaskMinutesTitle,
                )
                if (title != null) {
                    val id = createTaskForDate(
                        date = e.date,
                        time = null,
                        description = title,
                    )
                    e = e.copy(taskId = id)
                }
            }
            e
        }.sortedBy { it.date }

        _state.value = _state.value.copy(
            runningPlanApproved = true,
            runningPlanEntries = updated,
        )
    }

    fun resetRunningPlan() {
        val ids = _state.value.runningPlanEntries.mapNotNull { it.taskId }
        ids.forEach { deleteTask(it) }

        _state.value = _state.value.copy(
            runningPlanApproved = false,
            runningPlanEntries = emptyList(),
        )
    }

    fun pruneRunningPlanNow() {
        val st = _state.value
        if (!st.runningPlanApproved) return
        if (st.runningPlanEntries.isEmpty()) return

        val today = LocalDate.now()

        val expired = st.runningPlanEntries.filter { e ->
            today.isAfter(e.date.plusDays(1)) && isRunningPlanEntryIncomplete(e)
        }

        if (expired.isEmpty()) return

        expired.mapNotNull { it.taskId }.forEach { deleteTask(it) }

        val expiredDates = expired.map { it.date }.toSet()
        _state.value = st.copy(
            runningPlanEntries = st.runningPlanEntries.filterNot { it.date in expiredDates }
        )
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var nextId: Long = 1L
    private fun newId(): Long = nextId++

    private fun nextTaskOrderForDate(date: LocalDate?): Int =
        (_state.value.tasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1

    private fun nextSubtaskOrderFor(taskId: Long): Int =
        (_state.value.subtasks.filter { it.taskId == taskId }.maxOfOrNull { it.order } ?: -1) + 1

    private fun suppress(key: String) {
        val st = _state.value
        _state.value = st.copy(suppressedRecurrences = st.suppressedRecurrences + key)
    }

    private fun refreshHasSubtasks() {
        val idsWithSubs = _state.value.subtasks.map { it.taskId }.toSet()
        val updatedTasks = _state.value.tasks.map { t ->
            t.copy(hasSubtasks = idsWithSubs.contains(t.id))
        }
        _state.value = _state.value.copy(tasks = updatedTasks)
    }

    private fun refreshHasSubtasksForTask(
        taskId: Long,
        tasks: MutableList<Task>,
        subs: List<Subtask>
    ) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val has = subs.any { it.taskId == taskId }
            tasks[idx] = tasks[idx].copy(hasSubtasks = has)
        }
    }

    /* ---------------------------
       Recurrence (generated instances)
    ---------------------------- */

    private fun matchesRepeat(anchor: LocalDate, date: LocalDate, rule: RepeatRule): Boolean {
        if (!date.isAfter(anchor)) return false
        val interval = rule.interval.coerceAtLeast(1)

        return when (rule.freq) {
            RepeatFreq.DAILY -> {
                val days = ChronoUnit.DAYS.between(anchor, date)
                days % interval == 0L
            }

            RepeatFreq.WEEKLY -> {
                if (rule.weekDays.isNotEmpty() && date.dayOfWeek !in rule.weekDays) return false
                val wf = WeekFields.of(Locale.getDefault())
                val a = anchor.with(wf.dayOfWeek(), 1)
                val d = date.with(wf.dayOfWeek(), 1)
                val weeks = ChronoUnit.WEEKS.between(a, d)
                weeks % interval == 0L
            }

            RepeatFreq.MONTHLY -> {
                val dom = rule.dayOfMonth ?: anchor.dayOfMonth
                if (date.dayOfMonth != dom) return false
                val months =
                    ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
                months % interval == 0L
            }
        }
    }

    fun ensureGeneratedInRange(start: LocalDate, end: LocalDate) {
        val cur = _state.value

        val newTasks = cur.tasks.toMutableList()
        val newSubtasks = cur.subtasks.toMutableList()
        val subtasksByTask = cur.subtasks.groupBy { it.taskId }

        fun isSuppressed(key: String) = cur.suppressedRecurrences.contains(key)

        fun nextTaskOrderIn(date: LocalDate): Int =
            (newTasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1

        fun cloneSubtaskIntoTask(templateSub: Subtask, newTaskId: Long): Subtask {
            val s = templateSub.copy(
                id = newId(),
                taskId = newTaskId,
                isDone = false,
                repeatRule = null,
                originSubtaskId = templateSub.id
            )
            newSubtasks.add(s)
            return s
        }

        fun findGeneratedTask(originTaskId: Long, targetDate: LocalDate): Task? {
            return newTasks.firstOrNull { it.originTaskId == originTaskId && it.date == targetDate }
        }

        fun cloneTaskForDate(templateTask: Task, targetDate: LocalDate): Task {
            val t = templateTask.copy(
                id = newId(),
                order = nextTaskOrderIn(targetDate),
                date = targetDate,
                isDone = false,
                repeatRule = null,
                originTaskId = templateTask.id
            )
            newTasks.add(t)
            return t
        }

        val dateTemplates = cur.tasks.filter { it.originTaskId == null && it.date != null }

        for (t in dateTemplates) {
            val anchor = t.date ?: continue
            val subs = subtasksByTask[t.id].orEmpty().filter { it.originSubtaskId == null }

            val taskRule = t.repeatRule
            if (taskRule != null) {
                var d = start
                while (!d.isAfter(end)) {
                    val epoch = d.toEpochDay()
                    if (matchesRepeat(anchor, d, taskRule)) {
                        val suppressKey = "T:${t.id}:$epoch"
                        if (!isSuppressed(suppressKey)) {
                            val existing = findGeneratedTask(t.id, d)
                            if (existing == null) {
                                val createdTask = cloneTaskForDate(t, d)
                                for (srcSub in subs) {
                                    val suppressSubKey = "S:${srcSub.id}:$epoch"
                                    if (!isSuppressed(suppressSubKey)) {
                                        cloneSubtaskIntoTask(srcSub, createdTask.id)
                                    }
                                }
                                refreshHasSubtasksForTask(createdTask.id, newTasks, newSubtasks)
                            }
                        }
                    }
                    d = d.plusDays(1)
                }
            }

            for (s in subs) {
                val rule = s.repeatRule ?: continue
                var d = start
                while (!d.isAfter(end)) {
                    val epoch = d.toEpochDay()
                    if (matchesRepeat(anchor, d, rule)) {
                        val suppressKey = "S:${s.id}:$epoch"
                        if (!isSuppressed(suppressKey)) {
                            val taskForSub = findGeneratedTask(t.id, d) ?: cloneTaskForDate(t, d)
                            val alreadySub =
                                newSubtasks.any { it.taskId == taskForSub.id && it.originSubtaskId == s.id }
                            if (!alreadySub) {
                                cloneSubtaskIntoTask(s, taskForSub.id)
                                refreshHasSubtasksForTask(taskForSub.id, newTasks, newSubtasks)
                            }
                        }
                    }
                    d = d.plusDays(1)
                }
            }
        }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubtasks)
    }

    fun setTaskRepeatRule(taskId: Long, rule: RepeatRule?) {
        val st = _state.value
        val updated = st.tasks.map { t ->
            if (t.id == taskId) t.copy(repeatRule = rule) else t
        }
        _state.value = st.copy(tasks = updated)
    }

    fun setSubtaskRepeatRule(subtaskId: Long, rule: RepeatRule?) {
        val st = _state.value
        val updated = st.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(repeatRule = rule) else s
        }
        _state.value = st.copy(subtasks = updated)
        refreshHasSubtasks()
    }

    /* ---------------------------
       Tasks
    ---------------------------- */

    fun createTaskForDate(
        date: LocalDate?,
        time: LocalTime?,
        description: String,
        colorArgb: Long? = null,
        hasSubtasks: Boolean = false,
        repeatRule: RepeatRule? = null,
        linkedManualCounterId: Long? = null,
    ): Long {
        val id = newId()
        val task = Task(
            id = id,
            order = nextTaskOrderForDate(date),
            date = date,
            time = time,
            description = description,
            colorArgb = colorArgb,
            hasSubtasks = hasSubtasks,
            linkedManualCounterId = linkedManualCounterId,
            repeatRule = repeatRule,
        )
        _state.value = _state.value.copy(tasks = _state.value.tasks + task)
        return id
    }

    fun updateTaskDescription(taskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = _state.value.tasks.map { t ->
            if (t.id == taskId) t.copy(description = clean) else t
        }
        _state.value = _state.value.copy(tasks = updated)
    }

    fun deleteTask(taskId: Long) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return

        if (victim.originTaskId != null && victim.date != null) {
            val key = "T:${victim.originTaskId}:${victim.date.toEpochDay()}"
            val newTasks = cur.tasks.filterNot { it.id == taskId }
            val newSubs = cur.subtasks.filterNot { it.taskId == taskId }

            _state.value = cur.copy(
                suppressedRecurrences = cur.suppressedRecurrences + key,
                tasks = newTasks,
                subtasks = newSubs
            )
            refreshHasSubtasks()
            return
        }

        if (victim.originTaskId == null && victim.repeatRule != null && victim.date != null) {
            suppress("T:${victim.id}:${victim.date.toEpochDay()}")
            return
        }

        val newTasks = cur.tasks.filterNot { it.id == taskId }
        val newSubs = cur.subtasks.filterNot { it.taskId == taskId }
        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
        refreshHasSubtasks()
    }

    fun deleteTaskSeriesFrom(templateTaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        val cur = _state.value
        val template =
            cur.tasks.firstOrNull { it.id == templateTaskId && it.originTaskId == null } ?: return

        val idsToDelete = mutableSetOf<Long>()

        val td = template.date
        if (td == null || !td.isBefore(fromDate)) {
            idsToDelete.add(template.id)
        }

        cur.tasks.filter { it.originTaskId == templateTaskId }.forEach { inst ->
            val d = inst.date
            if (d == null || !d.isBefore(fromDate)) {
                idsToDelete.add(inst.id)
            }
        }

        val newTasks = cur.tasks
            .filterNot { it.id in idsToDelete }
            .map { t -> if (t.id == templateTaskId) t.copy(repeatRule = null) else t }

        val newSubs = cur.subtasks.filterNot { it.taskId in idsToDelete }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
        refreshHasSubtasks()
    }

    fun deleteSubtaskSeriesFrom(templateSubtaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        val cur = _state.value
        val templateSub =
            cur.subtasks.firstOrNull { it.id == templateSubtaskId && it.originSubtaskId == null }
                ?: return
        val parentTemplateTask =
            cur.tasks.firstOrNull { it.id == templateSub.taskId && it.originTaskId == null }
                ?: return

        val tasksById = cur.tasks.associateBy { it.id }

        val subIdsToDelete = cur.subtasks
            .filter { it.originSubtaskId == templateSubtaskId }
            .filter { inst ->
                val d = tasksById[inst.taskId]?.date
                d == null || !d.isBefore(fromDate)
            }
            .mapTo(mutableSetOf()) { it.id }

        val newSubs = cur.subtasks
            .filterNot { it.id in subIdsToDelete }
            .map { s -> if (s.id == templateSubtaskId) s.copy(repeatRule = null) else s }

        val parentIsRepeating = parentTemplateTask.repeatRule != null
        if (!parentIsRepeating) {
            val remainingByTask = newSubs.groupBy { it.taskId }

            val emptyGeneratedTaskIds = cur.tasks
                .filter { it.originTaskId == parentTemplateTask.id }
                .filter { inst ->
                    val d = inst.date
                    (d == null || !d.isBefore(fromDate)) && remainingByTask[inst.id].isNullOrEmpty()
                }
                .map { it.id }
                .toSet()

            val newTasks =
                if (emptyGeneratedTaskIds.isEmpty()) cur.tasks else cur.tasks.filterNot { it.id in emptyGeneratedTaskIds }
            val newSubs2 =
                if (emptyGeneratedTaskIds.isEmpty()) newSubs else newSubs.filterNot { it.taskId in emptyGeneratedTaskIds }

            _state.value = cur.copy(tasks = newTasks, subtasks = newSubs2)
        } else {
            _state.value = cur.copy(subtasks = newSubs)
        }

        refreshHasSubtasks()
    }

    fun rescheduleTaskToDate(taskId: Long, newDate: LocalDate?) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return

        val oldDate = victim.date
        val newOrder = if (oldDate == newDate) {
            victim.order
        } else {
            nextTaskOrderForDate(newDate)
        }

        if (victim.originTaskId != null && oldDate != null && oldDate != newDate) {
            val taskSuppressKey = "T:${victim.originTaskId}:${oldDate.toEpochDay()}"

            val movedTask = victim.copy(
                date = newDate,
                order = newOrder,
                repeatRule = null,
                originTaskId = null,
            )

            val generatedSubs = cur.subtasks.filter { it.taskId == taskId && it.originSubtaskId != null }
            val subSuppressKeys = generatedSubs.map { sub ->
                "S:${sub.originSubtaskId}:${oldDate.toEpochDay()}"
            }

            val updatedTasks = cur.tasks.map { task ->
                if (task.id == taskId) movedTask else task
            }

            val updatedSubtasks = cur.subtasks.map { sub ->
                if (sub.taskId == taskId && sub.originSubtaskId != null) {
                    sub.copy(
                        repeatRule = null,
                        originSubtaskId = null,
                    )
                } else {
                    sub
                }
            }

            _state.value = cur.copy(
                suppressedRecurrences = cur.suppressedRecurrences + taskSuppressKey + subSuppressKeys,
                tasks = updatedTasks,
                subtasks = updatedSubtasks,
            )
            return
        }

        val updatedTasks = cur.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(date = newDate, order = newOrder)
            } else {
                task
            }
        }

        _state.value = cur.copy(tasks = updatedTasks)
    }

    fun copyTaskToDate(taskId: Long, targetDate: LocalDate) {
        val cur = _state.value
        val srcTask = cur.tasks.firstOrNull { it.id == taskId } ?: return

        val srcSubs = cur.subtasks
            .filter { it.taskId == taskId }
            .sortedWith(compareBy({ it.order }, { it.id }))

        val newTaskId = createTaskForDate(
            date = targetDate,
            time = srcTask.time,
            description = srcTask.description,
            colorArgb = srcTask.colorArgb,
            hasSubtasks = srcSubs.isNotEmpty(),
            linkedManualCounterId = srcTask.linkedManualCounterId,
            repeatRule = null
        )

        for (s in srcSubs) {
            createSubtask(
                taskId = newTaskId,
                description = s.description,
                colorArgb = s.colorArgb
            )
        }
    }

    /* ---------------------------
       Subtasks
    ---------------------------- */

    fun createSubtask(taskId: Long, description: String, colorArgb: Long? = null): Long {
        val id = newId()
        val subtask = Subtask(
            id = id,
            order = nextSubtaskOrderFor(taskId),
            taskId = taskId,
            description = description.trim(),
            colorArgb = colorArgb,
            isDone = false
        )
        _state.value = _state.value.copy(subtasks = _state.value.subtasks + subtask)
        refreshHasSubtasks()

        val parent = _state.value.tasks.firstOrNull { it.id == taskId }
        if (parent?.isDone == true) toggleSubtaskDone(id)

        return id
    }

    fun updateSubtaskDescription(subtaskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = _state.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(description = clean) else s
        }
        _state.value = _state.value.copy(subtasks = updated)
    }

    fun deleteSubtask(subtaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId }

        if (victim?.originSubtaskId != null) {
            val parentTask = cur.tasks.firstOrNull { it.id == victim.taskId }
            val epoch = parentTask?.date?.toEpochDay()
            if (epoch != null) {
                suppress("S:${victim.originSubtaskId}:$epoch")
            }
        }

        val newSubs = cur.subtasks.filterNot { it.id == subtaskId }
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()

        val taskId = victim?.taskId ?: return
        val remaining = newSubs.filter { it.taskId == taskId }
        if (remaining.isNotEmpty()) {
            val allDone = remaining.all { it.isDone }
            val newTasks = _state.value.tasks.map { t ->
                if (t.id == taskId) t.copy(isDone = allDone) else t
            }
            _state.value = _state.value.copy(tasks = newTasks)
        }
    }

    fun copySubtaskToDate(subtaskId: Long, targetDate: LocalDate) {
        val cur = _state.value
        val srcSub = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return
        val parent = cur.tasks.firstOrNull { it.id == srcSub.taskId } ?: return

        val newTaskId = createTaskForDate(
            date = targetDate,
            time = parent.time,
            description = parent.description,
            colorArgb = parent.colorArgb,
            hasSubtasks = true,
            linkedManualCounterId = parent.linkedManualCounterId,
            repeatRule = null
        )

        createSubtask(
            taskId = newTaskId,
            description = srcSub.description,
            colorArgb = srcSub.colorArgb
        )
    }

    fun moveSubtask(subtaskId: Long, targetTaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return

        val newOrder =
            if (victim.taskId == targetTaskId) victim.order else nextSubtaskOrderFor(targetTaskId)

        val updated = cur.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(taskId = targetTaskId, order = newOrder) else s
        }
        _state.value = cur.copy(subtasks = updated)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }

    fun moveTaskUp(taskId: Long) {
        val cur = _state.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = -1)
        if (newTasks == cur.tasks) return
        _state.value = cur.copy(tasks = newTasks)
    }
    fun moveTaskDown(taskId: Long) {
        val cur = _state.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = 1)
        if (newTasks == cur.tasks) return
        _state.value = cur.copy(tasks = newTasks)
    }

    fun moveSubtaskUp(subtaskId: Long) {
        val cur = _state.value
        val newSubs = moveSubtaskWithinTask(cur.subtasks, subtaskId, step = -1)
        if (newSubs == cur.subtasks) return
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }

    fun moveSubtaskDown(subtaskId: Long) {
        val cur = _state.value
        val newSubs = moveSubtaskWithinTask(cur.subtasks, subtaskId, step = 1)
        if (newSubs == cur.subtasks) return
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }

    fun addManualCounter(title: String, balance: Int) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = _state.value
        val counter = ManualCounter(
            id = newId(),
            title = cleanTitle,
            balance = balance,
        )
        _state.value = stateWithAddedCounter(cur, counter)
    }

    fun addDateRangeCounter(title: String, startDate: LocalDate, endDate: LocalDate) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = _state.value
        val counter = DateRangeCounter(
            id = newId(),
            title = cleanTitle,
            startDate = startDate,
            endDate = endDate,
        )
        _state.value = stateWithAddedCounter(cur, counter)
    }

    fun updateManualCounter(counterId: Long, title: String, balance: Int) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = _state.value
        val updated = countersWithUpdatedManualCounter(
            counters = cur.counters,
            counterId = counterId,
            title = cleanTitle,
            balance = balance,
        )
        _state.value = cur.copy(counters = updated)
    }

    fun updateDateRangeCounter(counterId: Long, title: String, startDate: LocalDate, endDate: LocalDate) {
        val cleanTitle = normalizeCounterTitleOrNull(title) ?: return
        val cur = _state.value
        val updated = countersWithUpdatedDateRangeCounter(
            counters = cur.counters,
            counterId = counterId,
            title = cleanTitle,
            startDate = startDate,
            endDate = endDate,
        )
        _state.value = cur.copy(counters = updated)
    }

    fun deleteCounter(counterId: Long) {
        val cur = _state.value
        _state.value = stateWithoutCounter(cur, counterId)
    }

    /* ---------------------------
       Done flags sync (Task <-> Subtasks)
    ---------------------------- */

    fun setTaskLinkedManualCounter(taskId: Long, newCounterId: Long?) {
        val cur = _state.value
        val task = cur.tasks.firstOrNull { it.id == taskId } ?: return
        val oldCounterId = task.linkedManualCounterId
        if (oldCounterId == newCounterId) return

        var newCounters = cur.counters

        if (task.isDone) {
            if (oldCounterId != null) {
                newCounters = applyManualCounterDelta(newCounters, oldCounterId, +1)
            }
            if (newCounterId != null) {
                newCounters = applyManualCounterDelta(newCounters, newCounterId, -1)
            }
        }

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(linkedManualCounterId = newCounterId) else t
        }

        _state.value = cur.copy(
            tasks = newTasks,
            counters = newCounters
        )
    }

    fun toggleTaskDone(taskId: Long) {
        val cur = _state.value
        val task = cur.tasks.firstOrNull { it.id == taskId } ?: return

        val newDone = !task.isDone

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(isDone = newDone) else t
        }

        val hasSubs = cur.subtasks.any { it.taskId == taskId }
        val newSubs = if (!hasSubs) {
            cur.subtasks
        } else {
            cur.subtasks.map { s ->
                if (s.taskId == taskId) s.copy(isDone = newDone) else s
            }
        }

        val counterId = task.linkedManualCounterId
        val newCounters =
            if (counterId != null) applyManualCounterDelta(cur.counters, counterId, if (newDone) -1 else +1)
            else cur.counters

        _state.value = cur.copy(
            tasks = newTasks,
            subtasks = newSubs,
            counters = newCounters
        )
    }

    fun toggleSubtaskDone(subtaskId: Long) {
        val cur = _state.value
        val st0 = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return

        val taskId = st0.taskId
        val task = cur.tasks.firstOrNull { it.id == taskId } ?: return
        val oldDone = task.isDone

        val newSubs = cur.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(isDone = !s.isDone) else s
        }

        val related = newSubs.filter { it.taskId == taskId }
        val allDone = related.isNotEmpty() && related.all { it.isDone }

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(isDone = allDone) else t
        }

        val counterId = task.linkedManualCounterId
        val newCounters =
            if (counterId != null && oldDone != allDone) {
                applyManualCounterDelta(cur.counters, counterId, if (allDone) -1 else +1)
            } else {
                cur.counters
            }

        _state.value = cur.copy(
            tasks = newTasks,
            subtasks = newSubs,
            counters = newCounters
        )
    }

    private fun recomputeTaskDoneFromSubtasks() {
        val cur = _state.value
        val subsByTask = cur.subtasks.groupBy { it.taskId }
        val newTasks = cur.tasks.map { t ->
            val subs = subsByTask[t.id].orEmpty()
            if (subs.isEmpty()) t
            else t.copy(isDone = subs.all { it.isDone })
        }
        _state.value = cur.copy(tasks = newTasks)
    }

    /* ---------------------------
       Colors
    ---------------------------- */

    fun setTaskColor(taskId: Long, colorArgb: Long?) {
        val cur = _state.value

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(colorArgb = colorArgb) else t
        }

        val hasSubs = cur.subtasks.any { it.taskId == taskId }
        val newSubs = if (!hasSubs) {
            cur.subtasks
        } else {
            cur.subtasks.map { s ->
                if (s.taskId == taskId) s.copy(colorArgb = colorArgb) else s
            }
        }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
    }

    fun setSubtaskColor(subtaskId: Long, colorArgb: Long?) {
        val updated = _state.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(colorArgb = colorArgb) else s
        }
        _state.value = _state.value.copy(subtasks = updated)
    }

    /* ---------------------------
   Anthropometry settings
---------------------------- */
    fun setAnthropometryEnabledFieldIds(ids: Set<String>) {
        val normalized = normalizeAnthropometryEnabledFieldIds(ids)

        val cur = _state.value
        if (cur.anthropometryEnabledFieldIds == normalized) return

        _state.value = cur.copy(anthropometryEnabledFieldIds = normalized)
    }

    /* ---------------------------
     Anthropometry
  ---------------------------- */

    fun saveAnthropometryForDate(
        date: LocalDate,
        valuesByFieldId: Map<String, Double?>
    ) {
        val cur = _state.value
        val existing = cur.anthropometry.firstOrNull { it.date == date }

        val entry = mergeAnthropometryEntryForDate(
            date = date,
            existing = existing,
            valuesByFieldId = valuesByFieldId,
        )

        val filtered = cur.anthropometry.filterNot { it.date == date }

        val newList = if (!entry.hasAnyValue()) {
            filtered
        } else {
            (filtered + entry).sortedBy { it.date }
        }

        _state.value = cur.copy(anthropometry = newList)
    }

    /* ---------------------------
       Calorimeter
    ---------------------------- */

    fun setDailyCalorieGoalFrom(date: LocalDate, kcal: Int) {
        val clean = kcal.coerceAtLeast(1)
        val cur = _state.value
        val filtered = cur.calorieGoalChanges.filterNot { it.date == date }
        val newList = (filtered + CalorieGoalChange(date = date, kcal = clean)).sortedBy { it.date }
        _state.value = cur.copy(calorieGoalChanges = newList)
    }

    fun addFoodEntry(date: LocalDate, title: String, kcal: Int): Long {
        val t = title.trim()
        if (t.isBlank()) return -1L
        val k = kcal.coerceAtLeast(1)

        val id = newId()
        val entry = FoodEntry(
            id = id,
            date = date,
            title = t,
            kcal = k
        )
        val cur = _state.value
        _state.value = cur.copy(foodLog = cur.foodLog + entry)
        return id
    }

    fun deleteFoodEntry(entryId: Long) {
        val cur = _state.value
        _state.value = cur.copy(foodLog = cur.foodLog.filterNot { it.id == entryId })
    }
}