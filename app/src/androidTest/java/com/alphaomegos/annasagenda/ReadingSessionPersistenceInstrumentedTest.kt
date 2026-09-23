package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
@RunWith(AndroidJUnit4::class)
class ReadingSessionPersistenceInstrumentedTest {

    private lateinit var app: Application

    private val startedAt = 1_700_000_000_000L

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        clearAppStateStoreFile()
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile()
    }

    @Test
    fun aSessionInProgressSurvivesTheViewModelBeingRecreated() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt))

        awaitAutoSave()

        // A new view model over the same store is what the user comes back to
        // after the process has been reclaimed.
        val revived = AppViewModel(app)
        awaitLoaded(revived)

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
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = addBook(vm)

        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt))
        assertTrue(vm.beginReading(bookId, startedAtEpochMillis = startedAt + 3_600_000L))

        assertEquals(startedAt, vm.activeReading.value!!.startedAtEpochMillis)
    }

    @Test
    fun finishingASessionWritesItDownAndClearsIt() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
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
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = addBook(vm)
        vm.beginReading(bookId, startedAtEpochMillis = startedAt)

        vm.deleteReadingBook(bookId)

        assertNull(vm.activeReading.value)
    }

    private fun addBook(vm: AppViewModel): Long = requireNotNull(
        vm.addReadingBook(
            shelf = ReadingShelf.NOW,
            title = "Book",
            totalPages = 300,
            author = "Author",
        )
    )

    /** Autosave is debounced by 400 ms; give it room and then some. */
    private suspend fun awaitAutoSave() {
        delay(1500)
    }

    private suspend fun awaitLoaded(vm: AppViewModel) {
        repeat(100) {
            if (vm.isLoaded.value) return
            delay(20)
        }
        error("AppViewModel did not finish loading")
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
