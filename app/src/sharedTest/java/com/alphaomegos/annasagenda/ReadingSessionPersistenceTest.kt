package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An hour of reading with the screen off is long enough for Android to reclaim
 * the process. The session used to live only in the view model, so it went with
 * it — and since the finish dialog is only reachable while a session is
 * running, the time could not be entered afterwards either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReadingSessionPersistenceTest {

    private lateinit var app: Application

    private val startedAt = 1_700_000_000_000L

    @Before
    fun setUp() {
        // See AppViewModelRecurringRescheduleTest for why the main dispatcher
        // is replaced: a view model loads on viewModelScope, and these tests
        // block the thread that scope would otherwise need.
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        clearAppStateStoreFile()
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile()
        Dispatchers.resetMain()
    }

    @Test
    fun aSessionInProgressSurvivesTheViewModelBeingRecreated() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt))

        awaitAutoSave()

        // A new view model over the same store is what the user comes back to
        // after the process has been reclaimed.
        val revived = AppViewModel(app)
        awaitWorkingStore(revived)

        val active = revived.activeReading.value
        assertNotNull("the session must still be there", active)
        assertEquals(bookId, active!!.bookId)
        assertEquals(
            "and it must still know when it started",
            startedAt,
            active.startedAtEpochMillis,
        )
    }

    /**
     * Leaving the session screen with the system back gesture keeps the session
     * running but takes the screen away. Tapping Read again used to start over
     * from zero, throwing away whatever had been counted.
     */
    @Test
    fun openingTheSameBookAgainDoesNotRestartTheClock() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)

        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt))
        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt + 3_600_000L))

        assertEquals(startedAt, vm.activeReading.value!!.startedAtEpochMillis)
    }

    @Test
    fun finishingASessionWritesItDownAndClearsIt() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = startedAt)

        assertTrue(vm.finishReading(startPage = 10, endPage = 40, durationMinutes = 45))

        assertNull(vm.activeReading.value)
        assertEquals(1, vm.state.value.readingSessions.count { it.bookId == bookId })
        assertEquals(40, vm.state.value.readingBooks.single { it.id == bookId }.currentPage)
    }

    @Test
    fun deletingTheBookEndsItsSession() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = startedAt)

        vm.deleteReadingBook(bookId)

        assertNull(vm.activeReading.value)
    }

    /* ---------- a session interrupted by the book leaving Now ---------- */

    /**
     * The claim 0069 makes: the question outlives the process that asked it.
     * Marking a book finished mid-session used to leave nothing behind at all.
     */
    @Test
    fun aQuestionAboutAnInterruptedSessionSurvivesTheViewModelBeingRecreated() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = System.currentTimeMillis() - 3_600_000L)

        vm.moveReadingBookToShelf(bookId, ReadingShelf.DONE)

        assertNull("the session ended", vm.activeReading.value)
        assertNotNull("but it was not thrown away", vm.state.value.pendingReadingSession)

        awaitAutoSave()

        val revived = AppViewModel(app)
        awaitWorkingStore(revived)

        val asked = revived.pendingReadingPrompt.value
        assertNotNull("the question must still be there", asked)
        assertEquals(bookId, asked!!.session.bookId)
        assertEquals(60, asked.session.durationMinutes)
        assertTrue("and it must name the book", asked.bookTitle.isNotBlank())
    }

    @Test
    fun keepingTheSessionWritesItIntoTheHistory() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = System.currentTimeMillis() - 1_800_000L)
        vm.moveReadingBookToShelf(bookId, ReadingShelf.DONE)

        vm.keepPendingReadingSession(alwaysFromNowOn = false)

        assertNull(vm.state.value.pendingReadingSession)
        assertEquals(1, vm.state.value.readingSessions.count { it.bookId == bookId })
        assertEquals(false, vm.state.value.autoRecordInterruptedReading)
    }

    @Test
    fun discardingTheSessionLeavesTheHistoryAlone() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = System.currentTimeMillis() - 1_800_000L)
        vm.moveReadingBookToShelf(bookId, ReadingShelf.DONE)

        vm.discardPendingReadingSession()

        assertNull(vm.state.value.pendingReadingSession)
        assertTrue(vm.state.value.readingSessions.none { it.bookId == bookId })
    }

    /** Once the user has said to stop asking, nothing is asked again. */
    @Test
    fun sayingAlwaysRecordsTheNextOneWithoutAsking() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val first = addBook(vm)
        vm.beginReading(first, startedAtEpochMillis = System.currentTimeMillis() - 1_800_000L)
        vm.moveReadingBookToShelf(first, ReadingShelf.DONE)
        vm.keepPendingReadingSession(alwaysFromNowOn = true)

        val second = addBook(vm)
        vm.beginReading(second, startedAtEpochMillis = System.currentTimeMillis() - 1_800_000L)
        vm.moveReadingBookToShelf(second, ReadingShelf.ABANDONED)

        assertNull("nothing should have been asked", vm.state.value.pendingReadingSession)
        assertEquals(1, vm.state.value.readingSessions.count { it.bookId == second })
    }

    private fun addBook(vm: AppViewModel): Long = requireNotNull(
        vm.addReadingBook(
            shelf = ReadingShelf.NOW,
            title = "Book",
            totalPages = 300,
            author = "Author",
        )
    )

    /**
     * Autosave is debounced by 400 ms, and then it writes a file.
     *
     * Both halves need waiting for, and they need different kinds of waiting.
     * The debounce is a coroutine delay, so `delay` is what moves it: under
     * Robolectric that advances the virtual clock past the four hundred
     * milliseconds without anybody actually waiting. The write that follows is
     * a real file on a real thread, and no amount of virtual time makes it
     * land — hence the second, real wait. See AwaitLoaded for the longer
     * version of why these are not the same thing.
     *
     * This is hardening rather than a fix: the test passed with only the first
     * half, because the write usually beats it. Usually is the problem.
     */
    private suspend fun awaitAutoSave() {
        delay(1500)

        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(500)
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
