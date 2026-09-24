package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * "Imported" must mean the data is on disk.
 *
 * importBackupJson used to return true straight away and do the work in a
 * coroutine nobody waited for, so the message was shown before anything had
 * been written — and a write that then failed left the app running on data the
 * disk knew nothing about.
 */
@RunWith(AndroidJUnit4::class)
class ImportPersistsBeforeSuccessInstrumentedTest {

    private lateinit var app: Application

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
    fun aSuccessfulImportIsAlreadyOnDiskWhenItSaysSo() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        vm.createTaskForDate(
            date = LocalDate.now(),
            time = null,
            description = "Replaced by the import",
        )

        val backup = AppStateStore(app).encodeToJson(
            AppState(
                mainMenuOrder = listOf("calendar", "reading"),
                undoneHorizonDays = 90,
            )
        )

        assertTrue(vm.importBackupJson(backup))

        // No waiting, no settling: a fresh view model reads the store directly,
        // and the import has already claimed to be done.
        val revived = AppViewModel(app)
        awaitLoaded(revived)

        assertEquals(listOf("calendar", "reading"), revived.state.value.mainMenuOrder)
        assertEquals(90, revived.state.value.undoneHorizonDays)
        assertTrue(
            "the task from before the import must be gone",
            revived.state.value.tasks.isEmpty(),
        )
    }

    @Test
    fun anUnreadableBackupChangesNothingAndSaysSo() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val taskId = vm.createTaskForDate(
            date = LocalDate.now(),
            time = null,
            description = "Still here afterwards",
        )

        assertFalse(vm.importBackupJson("{ not json at all"))

        assertTrue(vm.state.value.tasks.any { it.id == taskId })
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
