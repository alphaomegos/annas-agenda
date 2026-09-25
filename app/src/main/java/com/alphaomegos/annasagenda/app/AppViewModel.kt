package com.alphaomegos.annasagenda

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.alphaomegos.annasagenda.util.AUTO_BACKUP_FILE_NAME
import com.alphaomegos.annasagenda.util.appBackgroundScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.LocalTime


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
        type: ReadingMediaType,
        itemId: Long,
    ): String? {
        val source = coverRef?.takeIf(::isExternalCoverRef) ?: return coverRef

        return importCoverIntoInternalStorage(
            context = appContext,
            sourceUri = source.toUri(),
            mediaKind = coverMediaKind(type),
            itemId = itemId
        ) ?: coverRef
    }

    private suspend fun migrateLegacyMediaCovers(
        state: AppState,
    ): AppState {
        val migratedBooks = state.readingBooks.map { book ->
            val migrated = migrateLegacyCoverRef(book.coverUri, ReadingMediaType.BOOKS, book.id)
            if (migrated == book.coverUri) book else book.copy(coverUri = migrated)
        }

        val migratedMovies = state.readingMovies.map { movie ->
            val migrated = migrateLegacyCoverRef(movie.coverUri, ReadingMediaType.MOVIES, movie.id)
            if (migrated == movie.coverUri) movie else movie.copy(coverUri = migrated)
        }

        val migratedSeries = state.readingSeries.map { series ->
            val migrated = migrateLegacyCoverRef(series.coverUri, ReadingMediaType.SERIES, series.id)
            if (migrated == series.coverUri) series else series.copy(coverUri = migrated)
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

    /** Whether the item is still there — asked before an import and again after. */
    private fun readingMediaExists(type: ReadingMediaType, itemId: Long): Boolean {
        val st = _state.value

        return when (type) {
            ReadingMediaType.BOOKS -> st.readingBooks.any { it.id == itemId }
            ReadingMediaType.MOVIES -> st.readingMovies.any { it.id == itemId }
            ReadingMediaType.SERIES -> st.readingSeries.any { it.id == itemId }
        }
    }

    private fun setReadingMediaCoverRef(
        type: ReadingMediaType,
        itemId: Long,
        coverUri: String?,
        clearCover: Boolean,
    ) {
        when (type) {
            ReadingMediaType.BOOKS ->
                updateReadingBook(bookId = itemId, coverUri = coverUri, clearCover = clearCover)

            ReadingMediaType.MOVIES ->
                updateReadingMovie(movieId = itemId, coverUri = coverUri, clearCover = clearCover)

            ReadingMediaType.SERIES ->
                updateReadingSeries(seriesId = itemId, coverUri = coverUri, clearCover = clearCover)
        }
    }

    /**
     * Copies the picked image into internal storage and points the item at it.
     *
     * The item is checked for twice on purpose. Importing takes long enough for
     * the user to delete the item while it runs, and a cover imported for
     * something that no longer exists is a file nothing will ever mention
     * again — so it is deleted rather than left behind.
     */
    fun setReadingMediaCoverFromPickedUri(
        type: ReadingMediaType,
        itemId: Long,
        sourceUri: Uri,
    ) {
        viewModelScope.launch {
            if (!readingMediaExists(type, itemId)) return@launch

            val importedRef = importCoverIntoInternalStorage(
                context = appContext,
                sourceUri = sourceUri,
                mediaKind = coverMediaKind(type),
                itemId = itemId
            ) ?: return@launch

            if (!readingMediaExists(type, itemId)) {
                cleanupInternalCoverAsync(importedRef)
                return@launch
            }

            setReadingMediaCoverRef(
                type = type,
                itemId = itemId,
                coverUri = importedRef,
                clearCover = false,
            )
        }
    }

    fun removeReadingMediaCover(type: ReadingMediaType, itemId: Long) {
        setReadingMediaCoverRef(type = type, itemId = itemId, coverUri = null, clearCover = true)
    }

    private val newTaskDraftSaveRequests = MutableSharedFlow<NewTaskDraft>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /**
     * Non-null when persisted state exists but could not be read. While this is
     * set, autosave stays off so the unreadable payload is never overwritten.
     */
    private val _storageFailure = MutableStateFlow<AppStateLoadResult.Failed?>(null)
    val storageFailure: StateFlow<AppStateLoadResult.Failed?> = _storageFailure.asStateFlow()

    private var autoSaveStarted = false

    init {
        viewModelScope.launch {
            when (val result = store.load()) {
                is AppStateLoadResult.Failed -> {
                    // Deliberately leave _state at its default and do NOT start
                    // autosave: the payload on disk stays untouched until the
                    // user decides what to do with it.
                    _storageFailure.value = result
                    _isLoaded.value = true
                }

                AppStateLoadResult.Empty -> {
                    nextId = nextIdAfter(_state.value)
                    _isLoaded.value = true
                    beginAutoSaveOnce()
                }

                is AppStateLoadResult.Loaded -> {
                    val loaded = result.state
                    val migrated = migrateLegacyMediaCovers(loaded)

                    _state.value = migrated
                    nextId = nextIdAfter(migrated)

                    if (migrated != loaded) {
                        store.save(migrated)
                    }

                    _isLoaded.value = true
                    beginAutoSaveOnce()
                }
            }
        }
    }

    /**
     * Starts both autosave loops exactly once. Idempotent, because recovery
     * paths may reach it after startup already declined to start them.
     */
    private fun beginAutoSaveOnce() {
        if (autoSaveStarted) return
        autoSaveStarted = true

        startNewTaskDraftAutoSave()
        viewModelScope.launch { startAutoSave() }
    }

    /**
     * Abandons an unreadable payload and continues from an empty state.
     *
     * The quarantined copy written by [AppStateStore.load] is left in place, so
     * this is recoverable afterwards via the normal backup import.
     */
    fun discardCorruptedStateAndStartEmpty() {
        if (_storageFailure.value == null) return

        viewModelScope.launch {
            val empty = AppState()

            _state.value = empty
            nextId = 1L

            store.save(empty)

            _storageFailure.value = null
            beginAutoSaveOnce()
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

        cleanupRemovedInternalCoversAsync(before, empty)

        viewModelScope.launch {
            store.save(empty)
        }
    }

    fun exportBackupJson(): String {
        return store.encodeToJson(_state.value)
    }

    /**
     * The automatic snapshot taken when the app goes to the background.
     *
     * The state is serialised here and now, on the caller's thread, so what
     * lands in the archive is what was on screen when the user left. The write
     * itself goes to a scope that is not tied to the activity: onStop is
     * routinely followed by destroy, and the backup used to be cancelled along
     * with it — silently, since nobody watches a backup that did not happen.
     *
     * Failures are swallowed on purpose. This runs unattended on a detached
     * scope, where an uncaught exception takes the whole process down; a
     * backup that could not be written is not worth a crash on the way out.
     */
    fun writeAutoBackupInBackground() {
        val allowed = shouldWriteAutoBackup(
            isLoaded = _isLoaded.value,
            hasStorageFailure = _storageFailure.value != null,
        )
        if (!allowed) return

        val json = exportBackupJson()
        val context = appContext

        appBackgroundScope.launch {
            runCatching {
                writeBackupToDocuments(
                    context = context,
                    json = json,
                    fileName = AUTO_BACKUP_FILE_NAME,
                )
            }
        }
    }

    suspend fun exportBackupToDocuments() {
        val before = _state.value
        val migrated = migrateLegacyMediaCovers(before)

        if (migrated != before) {
            // Copying covers is file I/O, and the user goes on using the app
            // while it runs — the export is launched without so much as a
            // dialog. Writing the pre-migration snapshot back wholesale undid
            // everything they did in the meantime and then saved that over it.
            // Only the three media lists can have changed here, so only those
            // are carried across.
            _state.update { cur ->
                cur.copy(
                    readingBooks = migrated.readingBooks,
                    readingMovies = migrated.readingMovies,
                    readingSeries = migrated.readingSeries,
                )
            }
            store.save(_state.value)
        }

        val current = _state.value
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

    /**
     * Restores a plain JSON backup. True only once the data is on disk.
     *
     * It used to hand back true immediately and do the work in a coroutine
     * nobody waited for, so "Imported" was shown before anything had been
     * written — and if the write then failed, the app was running on data that
     * existed only in memory while the disk still held the old, with no hint
     * that the two disagreed.
     */
    suspend fun importBackupJson(raw: String): Boolean {
        val decoded = store.decodeFromJson(raw) ?: return false
        return adoptImportedState(decoded)
    }

    suspend fun importBackupPackage(
        appStateJson: String,
        coverEntries: Map<String, ByteArray>,
    ): Boolean {
        val decoded = store.decodeFromJson(appStateJson) ?: return false
        val expectedRefs = collectInternalCoverRefs(decoded)

        // Deliberately NOT deleting covers the archive happens to lack. A
        // state-only archive — which is what the automatic backup is — carries
        // no covers at all, and deleting every cover it does not mention wiped
        // the images for media the restored state still points at. A missing
        // entry means "this archive does not carry the image", not "the image
        // should be destroyed". Covers that the new state no longer references
        // are removed below, by the orphan sweep, which is the correct place.
        coverEntries.forEach { (ref, bytes) ->
            if (ref in expectedRefs) {
                writeInternalCoverBytes(
                    context = appContext,
                    coverRef = ref,
                    bytes = bytes
                )
            }
        }

        val adopted = adoptImportedState(decoded)

        if (!adopted) {
            // The cover files were written for a state that is not being
            // adopted. Anything the current state does not reference is dead
            // weight and goes.
            cleanupRemovedInternalCoversAsync(decoded, _state.value)
        }

        return adopted
    }

    /**
     * Puts a decoded backup in place, on disk first and in memory second.
     *
     * That order is the point. Adopting first and saving afterwards meant a
     * failed save left the app showing data the disk knew nothing about: the
     * next launch silently went back to the old data, and the user had been
     * told the import worked.
     */
    private suspend fun adoptImportedState(decoded: AppState): Boolean {
        val before = _state.value
        val migrated = migrateLegacyMediaCovers(decoded)

        val saved = try {
            store.save(migrated)
            true
        } catch (e: CancellationException) {
            // Being cancelled is not a failed import; it must not be reported
            // as one, and the rest of this must not run on a dead coroutine.
            throw e
        } catch (e: Throwable) {
            false
        }

        if (!saved) return false

        _state.value = migrated
        nextId = nextIdAfter(migrated)

        cleanupRemovedInternalCoversAsync(before, migrated)

        // A successful import is also a recovery: the payload we could not read
        // has just been replaced by one we can.
        _storageFailure.value = null
        beginAutoSaveOnce()

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

    fun setUndoneHorizonDays(days: Int) {
        val normalized = normalizeUndoneHorizonDays(days)

        val cur = _state.value
        if (cur.undoneHorizonDays == normalized) return
        _state.value = cur.copy(undoneHorizonDays = normalized)
    }

    /**
     * Both of these go through [undoneDebt] so the lamp and the screen can
     * never disagree again: the lamp used to ignore tombstones and light red
     * for an occurrence the screen refused to show.
     */
    fun hasUndonePastTasks(today: LocalDate = LocalDate.now()): Boolean =
        !currentUndoneDebt(today).isEmpty

    fun undonePastTaskDates(today: LocalDate = LocalDate.now()): List<LocalDate> =
        currentUndoneDebt(today).dates

    private fun currentUndoneDebt(today: LocalDate): UndoneDebt {
        val cur = _state.value
        return undoneDebt(
            tasks = cur.tasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            today = today,
            horizonDays = cur.undoneHorizonDays,
        )
    }

    /**
     * Materialises recurrences across the Undone horizon.
     *
     * Until this existed, a debt only showed up if the calendar had happened to
     * draw that month, because nothing else called ensureGeneratedInRange. The
     * list therefore reflected browsing history rather than what was left
     * undone. Callers run this before reading the debt.
     *
     * Bounded twice over: by the chosen horizon, and by the first day any
     * repeating template starts. With nothing repeating it walks no days at all.
     */
    fun ensureUndoneHorizonGenerated(today: LocalDate = LocalDate.now()) {
        val cur = _state.value

        val start = undoneGenerationStart(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            today = today,
            horizonDays = cur.undoneHorizonDays,
        ) ?: return

        val end = today.minusDays(1)
        if (start.isAfter(end)) return

        ensureGeneratedInRange(start, end)
    }

    /** Generates the horizon, then reports what is owed. */
    fun prepareUndoneDebt(today: LocalDate = LocalDate.now()): UndoneDebt {
        ensureUndoneHorizonGenerated(today)
        return currentUndoneDebt(today)
    }


    /* ---------------------------
       Reading
    ---------------------------- */



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

        _state.value = st.copy(
            readingBooks = st.readingBooks.filterNot { it.id == bookId },
            readingSessions = st.readingSessions.filterNot { it.bookId == bookId },
            activeReading = st.activeReading?.takeIf { it.bookId != bookId },
        )

        cleanupInternalCoverAsync(book.coverUri)
    }

    fun moveReadingBookToShelf(bookId: Long, shelf: ReadingShelf) {
        val st = _state.value
        val book = st.readingBooks.firstOrNull { it.id == bookId } ?: return
        if (book.shelf == shelf) return

        val stillReading =
            st.activeReading?.takeIf { it.bookId != bookId || shelf == ReadingShelf.NOW }

        val currentYear = LocalDate.now().year

        val updatedBooks = st.readingBooks.map { b ->
            if (b.id != bookId) b
            else moveReadingBookToShelf(b, shelf, currentYear)
        }

        _state.value = st.copy(readingBooks = updatedBooks, activeReading = stillReading)
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

        val active = st.activeReading
        val newActive =
            if (active?.bookId != bookId) {
                active
            } else if (updated.shelf == ReadingShelf.NOW) {
                active.copy(startPage = updated.currentPage)
            } else {
                null
            }

        _state.value = st.copy(
            readingBooks = st.readingBooks.map { b -> if (b.id == bookId) updated else b },
            activeReading = newActive,
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

        // Opening the same book again is not a new session. Starting one from
        // scratch here threw away however long had already been counted, which
        // is what happened to anyone who left the session screen with the
        // system back gesture and tapped Read again.
        if (st.activeReading?.bookId == bookId) return true

        if (book.shelf == ReadingShelf.PLANS) {
            moveReadingBookToShelf(bookId, ReadingShelf.NOW)
        }

        val after = _state.value.readingBooks.firstOrNull { it.id == bookId } ?: return false

        _state.update { cur ->
            cur.copy(
                activeReading = ActiveReading(
                    bookId = bookId,
                    startedAtEpochMillis = startedAtEpochMillis,
                    startPage = after.currentPage.coerceAtLeast(0),
                )
            )
        }
        return true
    }

    fun cancelReading() {
        _state.update { cur -> cur.copy(activeReading = null) }
    }

    // Backward-compatible wrapper.
       fun finishReading(
        startPage: Int,
        endPage: Int,
        durationMinutes: Int,
        finishedAtEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val st = _state.value
        val active = st.activeReading ?: return false
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
            readingSessions = st.readingSessions + session,
            activeReading = null,
        )

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

            // Asking whether the task still exists, rather than whether the row
            // remembers an id: a row whose task was deleted used to be stuck,
            // renaming nothing and never getting a task back.
            val linkedTask = after.taskId?.let { id ->
                _state.value.tasks.firstOrNull { it.id == id }
            }

            when {
                title == null -> {
                    // Keep the approved row (and any existing task) untouched.
                    // Timeout cleanup is handled later by pruneRunningPlanNow().
                }

                linkedTask != null -> {
                    updateTaskDescription(linkedTask.id, title)
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

        // Must read the state fresh. deleteTask() above removed tasks and
        // subtasks; writing back the `st` snapshot taken before the loop put
        // every one of them straight back, leaving orphaned running tasks in
        // the calendar that no longer belonged to any plan entry.
        val expiredDates = expired.map { it.date }.toSet()
        _state.update { cur ->
            cur.copy(
                runningPlanEntries = cur.runningPlanEntries.filterNot { it.date in expiredDates }
            )
        }
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /* ---------------------------
       Feature-scoped state slices

       Declared here, after _state, because property initialisers run in
       declaration order and these read it eagerly.
    ---------------------------- */

    /**
     * A StateFlow over one feature's fields. Because StateFlow only emits
     * distinct values, a change elsewhere in AppState produces an equal slice
     * and no emission — so a screen collecting this does not recompose on
     * changes it does not care about.
     */
    private fun <T> stateSlice(transform: (AppState) -> T): StateFlow<T> =
        _state
            .map(transform)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = transform(_state.value),
            )

    val calorimeter: StateFlow<CalorimeterSlice> = stateSlice(::calorimeterSliceOf)
    val anthropometry: StateFlow<AnthropometrySlice> = stateSlice(::anthropometrySliceOf)
    val counters: StateFlow<CountersSlice> = stateSlice(::countersSliceOf)
    val undoneTasks: StateFlow<UndoneSlice> = stateSlice(::undoneSliceOf)

    /**
     * The session in progress, read straight out of the saved state.
     *
     * It used to be a MutableStateFlow of its own, which is why an hour of
     * reading disappeared whenever Android reclaimed the process: nothing ever
     * wrote it down, and the only way to record a session is the finish dialog,
     * which is reachable only while one is running.
     *
     * Declared here, with the other slices and after _state, because property
     * initialisers run in order: reading _state from further up the class gets
     * a null it has not been assigned yet.
     */
    val activeReading: StateFlow<ActiveReading?> = stateSlice { it.activeReading }

    /**
     * Collected by the activity, above everything else, so it is deliberately
     * one field: wrapping the whole state there would recompose the entire app
     * on every tick of every screen.
     */
    val themeMode: StateFlow<AppThemeMode> = stateSlice { it.themeMode }

    fun setThemeMode(mode: AppThemeMode) {
        if (_state.value.themeMode == mode) return
        _state.value = _state.value.copy(themeMode = mode)
    }

    private var nextId: Long = 1L
    private fun newId(): Long = nextId++

    private fun nextTaskOrderForDate(date: LocalDate?): Int =
        nextTaskOrderOn(_state.value.tasks, date)

    private fun nextSubtaskOrderFor(taskId: Long): Int =
        (_state.value.subtasks.filter { it.taskId == taskId }.maxOfOrNull { it.order } ?: -1) + 1

    private fun refreshHasSubtasks() {
        _state.update { cur ->
            cur.copy(tasks = withHasSubtasksRefreshed(cur.tasks, cur.subtasks))
        }
    }

    /* ---------------------------
       Recurrence (generated instances)
    ---------------------------- */

    /**
     * Materialises recurring occurrences for the given range.
     *
     * The algorithm itself lives in RecurrenceSupport as a pure function, so it
     * can be covered by JVM tests; this only feeds it state and takes the
     * result back. Reading `cur` once is safe precisely because that function
     * touches nothing.
     */
    fun ensureGeneratedInRange(start: LocalDate, end: LocalDate) {
        val cur = _state.value

        val generated = generateRecurrencesInRange(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            start = start,
            end = end,
            nextId = nextId,
        )

        nextId = generated.nextId
        _state.value = cur.copy(
            tasks = generated.tasks,
            subtasks = generated.subtasks,
        )
    }

    /**
     * Records the week boundary the rule was created under, so that switching
     * the app language later cannot move an "every N weeks" schedule.
     */
    private fun withRecordedWeekStart(rule: RepeatRule?): RepeatRule? = when {
        rule == null -> null
        rule.weekStart != null -> rule
        else -> rule.copy(weekStart = currentLocaleWeekStart())
    }

    fun setTaskRepeatRule(taskId: Long, rule: RepeatRule?) {
        val recorded = withRecordedWeekStart(rule)

        val st = _state.value
        val updated = st.tasks.map { t ->
            if (t.id == taskId) t.copy(repeatRule = recorded) else t
        }
        _state.value = st.copy(tasks = updated)
    }

    fun setSubtaskRepeatRule(subtaskId: Long, rule: RepeatRule?) {
        val recorded = withRecordedWeekStart(rule)

        val st = _state.value
        val updated = st.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(repeatRule = recorded) else s
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
            repeatRule = withRecordedWeekStart(repeatRule),
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

    /**
     * Deletes a task. What that means depends on whether it is an occurrence,
     * a template that repeats, or neither; the three answers live in
     * TaskDeletionSupport, where a JVM test can state them.
     */
    fun deleteTask(taskId: Long) {
        _state.update { cur ->
            val after = stateAfterDeletingTask(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                suppressedRecurrences = cur.suppressedRecurrences,
                runningPlanEntries = cur.runningPlanEntries,
                taskId = taskId,
            )
            cur.copy(
                tasks = after.tasks,
                subtasks = after.subtasks,
                suppressedRecurrences = after.suppressedRecurrences,
                runningPlanEntries = after.runningPlanEntries,
            )
        }
    }

    /**
     * Stops a repeat from a day onward. The rules live in SeriesDeletionSupport
     * so they can be stated as tests; this only hands the state over and takes
     * it back, in one update rather than in three.
     */
    fun deleteTaskSeriesFrom(templateTaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        _state.update { cur ->
            val after = tasksAfterDeletingTaskSeriesFrom(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                templateTaskId = templateTaskId,
                fromDate = fromDate,
            )
            cur.copy(tasks = after.tasks, subtasks = after.subtasks)
        }
    }

    fun deleteSubtaskSeriesFrom(templateSubtaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        _state.update { cur ->
            val after = tasksAfterDeletingSubtaskSeriesFrom(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                templateSubtaskId = templateSubtaskId,
                fromDate = fromDate,
            )
            cur.copy(tasks = after.tasks, subtasks = after.subtasks)
        }
    }

    /**
     * Moves a task to another day, or off the calendar. The rules — including
     * what happens when the task being moved is an occurrence of a repeat —
     * live in RescheduleSupport, where a JVM test can state them.
     */
    fun rescheduleTaskToDate(taskId: Long, newDate: LocalDate?) {
        _state.update { cur ->
            val after = stateAfterReschedulingTask(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                suppressedRecurrences = cur.suppressedRecurrences,
                taskId = taskId,
                newDate = newDate,
            )
            cur.copy(
                tasks = after.tasks,
                subtasks = after.subtasks,
                suppressedRecurrences = after.suppressedRecurrences,
            )
        }
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

    /**
     * Deletes a subtask, which can finish the task it was under and move the
     * counter that task is linked to.
     *
     * This used to be three separate writes to the state with reads in
     * between — the shape two earlier bugs in this file had. It is one now,
     * and what it does is stated in TaskDeletionSupport.
     */
    fun deleteSubtask(subtaskId: Long) {
        _state.update { cur ->
            val after = stateAfterDeletingSubtask(
                tasks = cur.tasks,
                subtasks = cur.subtasks,
                suppressedRecurrences = cur.suppressedRecurrences,
                counters = cur.counters,
                subtaskId = subtaskId,
            )
            cur.copy(
                tasks = after.tasks,
                subtasks = after.subtasks,
                suppressedRecurrences = after.suppressedRecurrences,
                counters = after.counters,
            )
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
                newCounters = countersWithManualCounterDelta(newCounters, oldCounterId, +1)
            }
            if (newCounterId != null) {
                newCounters = countersWithManualCounterDelta(newCounters, newCounterId, -1)
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

        val applied = applyTaskDoneFlags(cur.tasks, cur.counters) { t ->
            if (t.id == taskId) newDone else t.isDone
        }

        val hasSubs = cur.subtasks.any { it.taskId == taskId }
        val newSubs = if (!hasSubs) {
            cur.subtasks
        } else {
            cur.subtasks.map { s ->
                if (s.taskId == taskId) s.copy(isDone = newDone) else s
            }
        }

        _state.value = cur.copy(
            tasks = applied.tasks,
            subtasks = newSubs,
            counters = applied.counters
        )
    }

    fun toggleSubtaskDone(subtaskId: Long) {
        val cur = _state.value
        val st0 = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return

        val taskId = st0.taskId
        if (cur.tasks.none { it.id == taskId }) return

        val newSubs = cur.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(isDone = !s.isDone) else s
        }

        val related = newSubs.filter { it.taskId == taskId }
        val allDone = related.isNotEmpty() && related.all { it.isDone }

        val applied = applyTaskDoneFlags(cur.tasks, cur.counters) { t ->
            if (t.id == taskId) allDone else t.isDone
        }

        _state.value = cur.copy(
            tasks = applied.tasks,
            subtasks = newSubs,
            counters = applied.counters
        )
    }

    private fun recomputeTaskDoneFromSubtasks() {
        val cur = _state.value
        val subsByTask = cur.subtasks.groupBy { it.taskId }

        val applied = applyTaskDoneFlags(cur.tasks, cur.counters) { t ->
            val subs = subsByTask[t.id].orEmpty()
            if (subs.isEmpty()) t.isDone else subs.all { it.isDone }
        }

        _state.value = cur.copy(tasks = applied.tasks, counters = applied.counters)
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