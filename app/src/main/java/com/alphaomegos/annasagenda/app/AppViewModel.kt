package com.alphaomegos.annasagenda.app

import android.app.Application
import android.net.Uri
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
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
import java.time.LocalDateTime
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*


class AppViewModel(app: Application) : AndroidViewModel(app), PlacesAndFoodActions, BodyActions, RunningActions, TaskFlagsAndColorsActions, DayCountersActions, ReadingActions, MainMenuActions {
    // The parts kept in files of their own (28.10) reach the state through these.
    override val stateFlow: MutableStateFlow<AppState> get() = _state
    override fun nextFreshId(): Long = newId()
    override val metroHistoryFlow: MutableStateFlow<Map<Long, MetroHistory>> get() = _metroHistory
    override val stagedFlightImportFlow: MutableStateFlow<FlightImportPlan?> get() = _stagedFlightImport
    override fun runningCreateTask(date: LocalDate?, time: LocalTime?, description: String): Long =
        createTaskForDate(date = date, time = time, description = description)
    override fun runningDeleteTask(taskId: Long) = deleteTask(taskId)
    override fun runningRetitleTask(taskId: Long, description: String) = updateTaskDescription(taskId, description)
    override fun runningMinutesTitle(minutes: Int): String = formatRunningTaskMinutesTitle(minutes)
    override fun runningKmTitle(kmTitle: String): String = formatRunningTaskKmTitle(kmTitle)
    override fun readingCleanupCover(ref: String?) = cleanupInternalCoverAsync(ref)
    override var stagedFlightTextSlot: String?
        get() = stagedFlightText
        set(value) { stagedFlightText = value }

    // The state file, the draft, the archives and their covers: how they are
    // read and written lives there; when, and in what order, stays here.
    private val persistence = StatePersistence(app.applicationContext)

    /** The metro schemes that ship with the app (assets/metro/), read the first time they are asked for. */
    override val metroLibrary: Map<String, MetroScheme> by lazy { loadMetroLibrary(app.applicationContext) }

    /** The travel country base and world map (assets/travel/), read the first time they are asked for. */
    override val travelBase: List<TravelBaseCountry> by lazy { loadTravelBase(app.applicationContext) }
    val travelMap: TravelWorldMap by lazy { loadTravelMap(app.applicationContext) }

    /** Airports, airlines and aircraft for the flight log (assets/flights/), read the first time they are asked for. */
    override val flightBase: FlightBase by lazy { loadFlightBase(app.applicationContext) }

    // Undo and redo of metro schemes (08.10), by scheme id: memory only.
    private val _metroHistory = MutableStateFlow<Map<Long, MetroHistory>>(emptyMap())
    val metroHistory: StateFlow<Map<Long, MetroHistory>> = _metroHistory.asStateFlow()

    // An App in the Air file read and waiting for "Import?" (06.10). Kept here,
    // not on the screen: a whole log is too big for the saved instance state,
    // and turning the phone must not lose it.
    private var stagedFlightText: String? = null
    private val _stagedFlightImport = MutableStateFlow<FlightImportPlan?>(null)
    val stagedFlightImport: StateFlow<FlightImportPlan?> = _stagedFlightImport.asStateFlow()

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

    private suspend fun migrateLegacyMediaCovers(state: AppState): AppState =
        persistence.migrateLegacyMediaCovers(state)

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
            if (!readingMediaExists(_state.value, type, itemId)) return@launch

            val importedRef = importCoverIntoInternalStorage(
                context = appContext,
                sourceUri = sourceUri,
                mediaKind = coverMediaKind(type),
                itemId = itemId
            ) ?: return@launch

            if (!readingMediaExists(_state.value, type, itemId)) {
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

    /* ---------------------------
       Everything the init block below touches, declared above it

       Property initialisers run in declaration order, and the init block runs
       in that same order with them. What is easy to miss is that the coroutine
       it starts runs there too: viewModelScope is Dispatchers.Main.immediate,
       a view model is built on the main thread, and an immediate dispatcher
       does not post — it runs the body on the spot, inside the constructor.

       So a field declared below the init block does not exist yet when that
       body first touches it. That is how loading crashed with "_state is
       null", rarely and at random: the body suspends on the file read before
       it reaches _state, and by the time the read comes back the constructor
       has normally finished. Normally.
    ---------------------------- */

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var nextId: Long = 1L
    private fun newId(): Long = nextId++

    init {
        LiveAppState.loadingStarted()
        viewModelScope.launch {
            // **There is exactly one outcome this block is not allowed to
            // have: finishing without isLoaded becoming true.** That outcome
            // is a screen that never opens and never says why, and it is what
            // an unexpected throw in here used to produce — the coroutine died
            // with nobody listening, isLoaded stayed false for ever and
            // storageFailure stayed null.
            //
            // So the flag is set in a finally, and it is set nowhere else.
            // load() answers rather than throws now, but everything after it —
            // a cover migration, a write — can still fail, and the rule has to
            // hold for those too.
            try {
                when (val result = persistence.load()) {
                    is AppStateLoadResult.Failed -> {
                        // Deliberately leave _state at its default and do NOT
                        // start autosave: the payload on disk stays untouched
                        // until the user decides what to do with it.
                        _storageFailure.value = result
                    }

                    AppStateLoadResult.Empty -> {
                        nextId = nextIdFor(_state.value)
                        beginAutoSaveOnce()
                    }

                    is AppStateLoadResult.Loaded -> {
                        val loaded = result.state
                        val migrated = migrateLegacyMediaCovers(loaded)

                        _state.value = migrated
                        nextId = nextIdFor(migrated)

                        if (migrated != loaded) {
                            persist(migrated)
                        }

                        beginAutoSaveOnce()
                    }
                }
            } catch (e: CancellationException) {
                // Being cancelled is the view model going away, not a failure
                // to read anything.
                throw e
            } catch (e: Throwable) {
                // Reported rather than swallowed: the storage-failure screen,
                // autosave left off, the payload on disk untouched. That is
                // the right answer whatever went wrong, and it is a great deal
                // better than a blank screen.
                _storageFailure.value = AppStateLoadResult.Corrupted(
                    cause = e,
                    quarantineFile = null,
                )
            } finally {
                _isLoaded.value = true
                // Attached by beginAutoSaveOnce on success; on a failure
                // nobody will be, and the widget must not wait for it.
                LiveAppState.loadingEnded()
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
        startNotificationScheduling()
        LiveAppState.attach(this)
    }

    override fun onCleared() {
        LiveAppState.detach(this)
        super.onCleared()
    }

    /**
     * A tick from the home-screen widget, by the task's series-and-day key.
     * Today's repeats are drawn with ids from this counter, so the one ticked
     * may be an occurrence no screen has drawn yet.
     */
    fun toggleTaskFromWidget(key: String) {
        val cur = _state.value
        val result = stateAfterTogglingTaskFromWidget(cur, key, LocalDate.now(), currentLocaleWeekStart(), nextId)
            ?: return
        nextId = result.nextId
        _state.value = result.state
    }

    /**
     * Keeps the one notification alarm, and the home-screen widget, in step
     * with what they depend on: the tasks, the tombstones, the settings, the
     * horizon and the seasons waited for. Only from here —
     * after the state is loaded and autosave runs — so an alarm is never
     * set from the empty default a moment before the real state arrives.
     */
    @OptIn(FlowPreview::class)
    private fun startNotificationScheduling() {
        val context = appContext
        // Off the main thread, all of it — the second it waits, the alarm and
        // the widget. Nothing here needs Main, and a debounce resuming on Main
        // a second after a JVM test has finished collided with the next
        // test's Dispatchers.setMain (AppViewModelUndoneHorizonTest, 04.10).
        viewModelScope.launch(Dispatchers.Default) {
            _state
                .map { s ->
                    // Of the series, only what a season reminder is made of (15.10):
                    // an episode ticked off does not move the alarm.
                    val seasons = seriesRemindingOfSeasons(s.readingSeries).map { Triple(it.id, it.title, it.nextSeasonDate) }
                    // And what the steps widget shows (25.10): the last days, the record, its options.
                    val steps = listOf(s.stepDays.takeLast(8), s.stepDays.maxOfOrNull { it.steps }, s.stepsWidgetLine, s.stepsWidgetRecord, s.stepsGoal)
                    listOf(s.tasks, s.subtasks, s.suppressedRecurrences, s.notifications, s.undoneHorizonDays, s.widgetStyle, seasons, steps)
                }
                .distinctUntilChanged()
                .debounce(1000)
                .collect {
                    runCatching { scheduleNextNotification(context, _state.value) }
                    // The widget shows today's tasks; it reads them from here.
                    refreshTodayWidgets(context)
                }
        }
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

            persist(empty)

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
                .collect { persistence.saveDraft(it) }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun startAutoSave() {
        state
            .drop(1)
            .debounce(400)
            .collect { persist(it) }
    }

    fun resetAllData() {
        val before = _state.value
        val empty = AppState()

        _state.value = empty
        nextId = 1L

        cleanupRemovedInternalCoversAsync(before, empty)

        viewModelScope.launch {
            persist(empty)
        }
    }

    /**
     * The archive carries the id counter's position too, so that restoring it
     * on another device does not start handing out ids that the archive's own
     * tombstones and plan rows still refer to.
     */
    fun exportBackupJson(): String {
        return persistence.encodeBackup(_state.value, nextId)
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

        persistence.writeAutoBackupInBackground(exportBackupJson())
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
            persist(_state.value)
        }

        persistence.exportToDocuments(_state.value, nextId)
    }
    suspend fun loadNewTaskDraft(): NewTaskDraft? {
        return persistence.loadDraft()
    }

    fun queueNewTaskDraftSave(draft: NewTaskDraft) {
        newTaskDraftSaveRequests.tryEmit(draft)
    }

    fun clearNewTaskDraft() {
        newTaskDraftSaveRequests.tryEmit(NewTaskDraft())
        viewModelScope.launch {
            persistence.clearDraft()
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
    suspend fun importBackupJson(raw: String): ImportOutcome {
        val decoded = persistence.decodeBackup(raw) ?: return ImportOutcome.Failed
        return ImportOutcome(adopted = adoptImportedState(decoded))
    }

    suspend fun importBackupPackage(
        appStateJson: String,
        coverEntries: Map<String, ByteArray>,
    ): ImportOutcome {
        val decoded = persistence.decodeBackup(appStateJson) ?: return ImportOutcome.Failed
        // Covers the archive lacks are not deleted, and a cover that will not
        // write does not fail the import but is counted and said: see
        // StatePersistence.restoreCovers.
        val coversNotWritten = persistence.restoreCovers(decoded, coverEntries)

        val adopted = adoptImportedState(decoded)

        if (!adopted) {
            // The cover files were written for a state that is not being
            // adopted. Anything the current state does not reference is dead
            // weight and goes.
            cleanupRemovedInternalCoversAsync(decoded, _state.value)
        }

        return ImportOutcome(adopted = adopted, coversNotWritten = coversNotWritten)
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
            persist(migrated)
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
        nextId = nextIdFor(migrated)

        cleanupRemovedInternalCoversAsync(before, migrated)

        // A successful import is also a recovery: the payload we could not read
        // has just been replaced by one we can.
        _storageFailure.value = null
        beginAutoSaveOnce()

        return true
    }


       private fun formatRunningTaskKmTitle(kmTitle: String): String =
        getApplication<Application>().getString(R.string.running_task_km, kmTitle)

    private fun formatRunningTaskMinutesTitle(minutes: Int): String =
        getApplication<Application>().getString(R.string.running_task_minutes, minutes)

    /* ---------------------------
       Feature-scoped state slices

       After _state, because property initialisers run in declaration order and
       these read it eagerly. _state is now further up still, above the init
       block — see the note there for the half of this rule that was missing.
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
     * The question about an interrupted session, if one is outstanding.
     *
     * Collected above the navigation graph, because the change that raises it
     * can come from the library list or from a details screen, and the answer
     * must not depend on which one the user happens to still be looking at.
     */
    val pendingReadingPrompt: StateFlow<PendingReadingPrompt?> =
        stateSlice(::pendingReadingPromptOf)

    /**
     * Collected by the activity, above everything else, so it is deliberately
     * one field: wrapping the whole state there would recompose the entire app
     * on every tick of every screen.
     */
    val themeMode: StateFlow<AppThemeMode> = stateSlice { it.themeMode }

    val palette: StateFlow<AppPalette> = stateSlice { it.palette }

    val backStyle: StateFlow<BackStyle> = stateSlice { it.backStyle }

    /* ---------------- steps (19.10) ---------------- */

    /** What a read from Health Connect brought: each day of it, a day with nothing as 0. */
    fun applyReadSteps(read: Map<LocalDate, Int>) {
        _state.update { cur -> cur.copy(stepDays = stepDaysAfterRead(cur.stepDays, read)) }
    }

    /** A day's steps typed in by hand; no later read replaces them. */
    fun typeSteps(date: LocalDate, steps: Int) {
        _state.update { cur -> cur.copy(stepDays = stepDaysWithTyped(cur.stepDays, date, steps)) }
    }

    /** A typed-in day taken back: Health Connect's count returns with the next read. */
    fun untypeSteps(date: LocalDate) {
        _state.update { cur -> cur.copy(stepDays = stepDaysWithoutTyped(cur.stepDays, date)) }
        refreshSteps()
    }

    fun setDailyNorm(kcal: Int?) {
        _state.update { cur -> cur.copy(dailyNormKcal = kcal?.takeIf { it in DAILY_NORM_RANGE }) }
    }

    fun setWeightForecastMethod(method: ForecastMethod) {
        _state.update { cur -> if (cur.weightForecastMethod == method) cur else cur.copy(weightForecastMethod = method) }
    }

    /** The profile (28.10): sex, height and year of birth, one set for the whole app. */
    fun setProfile(sex: ProfileSex?, heightCm: Int?, birthYear: Int?) {
        _state.update { cur ->
            cur.copy(
                profileSex = sex,
                stepsHeightCm = heightCm?.takeIf { it in STEPS_HEIGHT_RANGE_CM },
                profileBirthYear = birthYear?.takeIf { it in PROFILE_BIRTH_YEARS },
            )
        }
    }

    fun setStepsHeight(heightCm: Int?) {
        _state.update { cur -> cur.copy(stepsHeightCm = heightCm?.takeIf { it in STEPS_HEIGHT_RANGE_CM }) }
    }

    fun setStepsRange(range: AnthropometryRange, custom: DateWindow?) {
        _state.update { cur -> cur.copy(stepsRange = range, stepsCustomRange = custom ?: cur.stepsCustomRange) }
    }

    private val _stepsAvailability = MutableStateFlow(StepsAvailability.NONE)
    /** Whether this phone has a Health Connect, as of the last look. */
    val stepsAvailability: StateFlow<StepsAvailability> = _stepsAvailability.asStateFlow()

    private val _stepsImport = MutableStateFlow<StepsImportResult?>(null)
    /** How the last import from a Samsung Health export ended, until its dialog is closed. */
    val stepsImport: StateFlow<StepsImportResult?> = _stepsImport.asStateFlow()

    fun dismissStepsImport() {
        _stepsImport.value = null
    }

    /**
     * The days of a Samsung Health export (19.10), from the file the user
     * picked: empty days filled, an earlier import's days renewed, days from
     * Health Connect or typed in left alone (stepDaysAfterImport).
     */
    fun importSamsungSteps(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                val text = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val read = parseSamsungStepDailyTrend(text)
                if (read.isEmpty()) {
                    StepsImportResult.NotAnExport
                } else {
                    var outcome: StepsImportOutcome? = null
                    _state.update { cur ->
                        val next = stepDaysAfterImport(cur.stepDays, read)
                        outcome = next
                        cur.copy(stepDays = next.days)
                    }
                    StepsImportResult.Done(checkNotNull(outcome))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("AnnaSteps", "importing a Samsung Health export failed", e)
                StepsImportResult.Failed("${e.javaClass.simpleName}: ${e.message}")
            }
            _stepsImport.value = result
        }
    }

    private val _weightImport = MutableStateFlow<WeightImportState?>(null)
    /** Where an import of Samsung Health weights stands (28.10). */
    val weightImport: StateFlow<WeightImportState?> = _weightImport.asStateFlow()

    fun dismissWeightImport() {
        _weightImport.value = null
    }

    /** Reads a Samsung Health weight export; asks when the app and the export disagree on a day. */
    fun readSamsungWeight(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _weightImport.value = try {
                val text = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val read = parseSamsungWeight(text)
                when {
                    read.isEmpty() -> WeightImportState.NotAnExport
                    else -> {
                        val conflicts = weightImportConflicts(_state.value.anthropometry, read)
                        if (conflicts > 0) WeightImportState.Conflicts(read, conflicts) else applyWeightImport(read, preferImported = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w("AnnaWeight", "importing a Samsung Health weight export failed", e)
                WeightImportState.Failed("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** The user's answer to the disagreement: the export's weights, or the app's. */
    fun resolveWeightImport(preferImported: Boolean) {
        val pending = _weightImport.value as? WeightImportState.Conflicts ?: return
        _weightImport.value = applyWeightImport(pending.read, preferImported)
    }

    private fun applyWeightImport(read: Map<LocalDate, Double>, preferImported: Boolean): WeightImportState {
        var outcome: WeightImportOutcome? = null
        _state.update { cur ->
            val next = anthropometryAfterWeightImport(cur.anthropometry, read, preferImported)
            outcome = next
            cur.copy(anthropometry = next.entries)
        }
        return WeightImportState.Done(checkNotNull(outcome))
    }

    private val _stepsReport = MutableStateFlow<StepsReadReport?>(null)
    /** What the last read found, or what stopped it; null before any read this session. */
    val stepsReport: StateFlow<StepsReadReport?> = _stepsReport.asStateFlow()

    private val _stepsAllowed = MutableStateFlow(false)
    /** Whether the app may read steps, as of the last look. */
    val stepsAllowed: StateFlow<Boolean> = _stepsAllowed.asStateFlow()

    /**
     * Looks at Health Connect and, if the app may read steps, reads the last
     * [STEPS_READ_DAYS] days into the kept ones. Called when the app comes
     * back to the front and when the user has just let it read: the watch
     * reaches the phone in its own time, so the latest days keep growing.
     * Not before the saved state is loaded — a read laid over the empty state
     * would be overwritten by the load. Any failure leaves the days as they
     * are; the next return to the app tries again.
     */
    fun refreshSteps() {
        if (!_isLoaded.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = appContext
                val availability = HealthConnectSteps.availability(context)
                _stepsAvailability.value = availability
                val allowed = availability == StepsAvailability.READY && HealthConnectSteps.isAllowed(context)
                _stepsAllowed.value = allowed
                if (allowed) {
                    val read = HealthConnectSteps.readDays(context, LocalDate.now(), STEPS_READ_DAYS)
                    applyReadSteps(read.days)
                    _stepsReport.value = stepsReadReport(LocalDateTime.now(), read.days, read.sources)
                }
                // Steps together (25.10): the freshest days go up, friends come down.
                runCatching { StepsTogether.sync(context, _state.value.stepDays) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Said on the tab, not swallowed (19.10: a read that found
                // nothing and a read that failed looked the same).
                android.util.Log.w("AnnaSteps", "reading steps from Health Connect failed", e)
                _stepsReport.value = StepsReadReport(LocalDateTime.now(), 0, 0, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** The screens' look (28.10): «New» or «Summer 2026», for screens that read nothing else of the state. */
    val dayScreenStyle: StateFlow<DayScreenStyle> = stateSlice { it.dayScreenStyle }

    fun setDayScreenStyle(style: DayScreenStyle) {
        _state.update { cur -> if (cur.dayScreenStyle == style) cur else cur.copy(dayScreenStyle = style) }
    }

    fun setMainMenuNarrow(style: MainMenuNarrowStyle) {
        _state.update { cur -> if (cur.mainMenuNarrow == style) cur else cur.copy(mainMenuNarrow = style) }
    }

    fun setBackStyle(style: BackStyle) {
        if (_state.value.backStyle == style) return
        _state.value = _state.value.copy(backStyle = style)
    }

    fun setPalette(palette: AppPalette) {
        if (_state.value.palette == palette) return
        _state.value = _state.value.copy(palette = palette)
    }

    fun setThemeMode(mode: AppThemeMode) {
        if (_state.value.themeMode == mode) return
        _state.value = _state.value.copy(themeMode = mode)
    }

    /**
     * Writes the state down, with the id counter's position recorded in it.
     *
     * Every save goes through here rather than straight to the store, because
     * the mark cannot be kept in the state itself: several creators snapshot
     * the state, call newId(), and write the snapshot back, which would throw
     * the bump away. The counter in this view model is the live authority for
     * the session; this is where it is handed to the next one.
     *
     * See nextIdFor for why an id must never be given out twice.
     */
    private suspend fun persist(state: AppState) {
        persistence.save(state, nextId)
    }

    private fun nextTaskOrderForDate(date: LocalDate?): Int =
        nextTaskOrderOn(_state.value.tasks, date)

    private fun nextSubtaskOrderFor(taskId: Long): Int =
        nextSubtaskOrderIn(_state.value.subtasks, taskId)

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
    override fun ensureGeneratedInRange(start: LocalDate, end: LocalDate) {
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
        // No refreshHasSubtasks here: a repeat rule does not change which
        // tasks have parts, and the call that used to follow was a second
        // write to the state that could not say anything new.
        _state.value = st.copy(subtasks = updated)
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

    /** Gives a task a time, changes it, or (null) takes it away — see tasksAfterSettingTime. */
    fun setTaskTime(taskId: Long, time: LocalTime?) {
        val cur = _state.value
        val newTasks = tasksAfterSettingTime(cur.tasks, taskId, time)
        if (newTasks === cur.tasks) return
        _state.value = cur.copy(tasks = newTasks)
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

    /**
     * Renames a repeating task and sets or clears its time from [fromDate] on,
     * leaving earlier days as they were — the rule is in SeriesEditSupport.
     */
    fun editTaskSeriesFrom(
        templateTaskId: Long,
        description: String,
        time: LocalTime?,
        rule: RepeatRule?,
        fromDate: LocalDate = LocalDate.now(),
    ) {
        // Read and write rather than update {}: update's lambda is a
        // compare-and-set loop and may run more than once, and newId() is not
        // something to run twice for one edit. Nothing suspends in between.
        val cur = _state.value
        val after = stateAfterEditingTaskSeriesFrom(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            templateTaskId = templateTaskId,
            fromDate = fromDate,
            description = description,
            time = time,
            newId = ::newId,
            weekStart = currentLocaleWeekStart(),
            rule = withRecordedWeekStart(rule),
        )
        if (after.tasks === cur.tasks) return

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
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
    /**
     * Moves one occurrence of a series and everything after it by the same
     * number of days — the rule is in SeriesEditSupport.
     */
    fun shiftTaskSeriesFrom(occurrenceId: Long, newDate: LocalDate) {
        // Read and write rather than update {}: newId() must not run twice.
        val cur = _state.value
        val after = stateAfterShiftingSeriesFrom(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            occurrenceId = occurrenceId,
            newDate = newDate,
            newId = ::newId,
            weekStart = currentLocaleWeekStart(),
        )
        if (after.tasks === cur.tasks) return

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
    }

    fun rescheduleTaskToDate(taskId: Long, newDate: LocalDate?) {
        // Read and write rather than update {}: a series' first day moved
        // alone becomes a copy, and newId() must not run twice.
        val cur = _state.value
        val after = stateAfterReschedulingTask(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            suppressedRecurrences = cur.suppressedRecurrences,
            taskId = taskId,
            newDate = newDate,
            newId = ::newId,
        )
        _state.value = cur.copy(
            tasks = withHasSubtasksRefreshed(after.tasks, after.subtasks),
            subtasks = after.subtasks,
            suppressedRecurrences = after.suppressedRecurrences,
        )
    }

    /**
     * Copies a task to another day, with everything under it. What a copy
     * keeps and what it deliberately leaves behind is stated in CopySupport.
     */
    fun copyTaskToDate(taskId: Long, targetDate: LocalDate) {
        // Read and write rather than update {}: update's lambda is a
        // compare-and-set loop and may run more than once, and newId() is not
        // something to run twice for one copy. Nothing suspends in between.
        val cur = _state.value
        val after = stateAfterCopyingTask(cur.tasks, cur.subtasks, taskId, targetDate, ::newId)

        _state.value = cur.copy(tasks = after.tasks, subtasks = after.subtasks)
    }

    /* ---------------------------
       Subtasks
    ---------------------------- */

    /**
     * Adds a part to a task. The rule about a part joining a task that is
     * already finished lives in TaskDoneSupport, with the reason it matters.
     */
    fun createSubtask(taskId: Long, description: String, colorArgb: Long? = null): Long {
        val id = newId()
        val cur = _state.value

        val after = stateAfterCreatingSubtask(
            tasks = cur.tasks,
            subtasks = cur.subtasks,
            counters = cur.counters,
            taskId = taskId,
            description = description,
            colorArgb = colorArgb,
            id = id,
        )

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )

        return after.createdId
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
        val after = stateAfterCopyingSubtask(cur.tasks, cur.subtasks, subtaskId, targetDate, ::newId)

        _state.value = cur.copy(tasks = after.tasks, subtasks = after.subtasks)
    }

    /**
     * Moves a part to another task. Both tasks can change their minds about
     * being finished and both can move a counter, so it is one write; the
     * rules are in TaskDoneSupport.
     */
    fun moveSubtask(subtaskId: Long, targetTaskId: Long) {
        val cur = _state.value

        val after = stateAfterMovingSubtask(
            cur.tasks, cur.subtasks, cur.counters, subtaskId, targetTaskId,
        )

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

    fun moveTaskUp(taskId: Long) {
        val cur = _state.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = -1, doneLast = cur.doneTasksLast)
        if (newTasks == cur.tasks) return
        _state.value = cur.copy(tasks = newTasks)
    }
    fun moveTaskDown(taskId: Long) {
        val cur = _state.value
        val newTasks = moveTaskWithinDate(cur.tasks, taskId, step = 1, doneLast = cur.doneTasksLast)
        if (newTasks == cur.tasks) return
        _state.value = cur.copy(tasks = newTasks)
    }

    fun moveSubtaskUp(subtaskId: Long) {
        val cur = _state.value
        val after = stateAfterReorderingSubtask(cur.tasks, cur.subtasks, cur.counters, subtaskId, step = -1, doneLast = cur.doneSubtasksLast)

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

    fun moveSubtaskDown(subtaskId: Long) {
        val cur = _state.value
        val after = stateAfterReorderingSubtask(cur.tasks, cur.subtasks, cur.counters, subtaskId, step = 1, doneLast = cur.doneSubtasksLast)

        _state.value = cur.copy(
            tasks = after.tasks,
            subtasks = after.subtasks,
            counters = after.counters,
        )
    }

}
