package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The first view model asked its questions without a device.
 *
 * A probe, like 0084 and 0091 before it, and the first of these to need more
 * than a change of folder.
 *
 * A view model loads itself on `viewModelScope`, which is the main dispatcher,
 * and these tests block their own thread while they wait for that load. On a
 * device that is fine: the test has one thread and the main looper has
 * another. Under Robolectric the test *is* the main thread, so the load went
 * into a queue nothing was left to drain, and both tests failed with
 * "AppViewModel did not finish loading" — which is the polling loop at the
 * bottom of this file, giving up after two seconds.
 *
 * So the main dispatcher is replaced for the duration. An unconfined test
 * dispatcher runs what is launched on it straight away, on whatever thread
 * asked; nothing here is about threading, so nothing here is weakened by
 * that. It is set and reset around every test, and it does the same on a
 * device, which is what keeps this file shareable.
 *
 * What isolates one test from the next is `resetAllData`, not the file
 * deletion in setUp. Deleting the file under a live DataStore changes
 * nothing — the instance keeps what it read in memory — and that was as true
 * on the emulator as it is here. The deletion stays because it costs nothing,
 * but it is not what makes this work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppViewModelRecurringRescheduleTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        clearAppStateStoreFile()
    }

    @After
    fun tearDown() {
        clearAppStateStoreFile()
        Dispatchers.resetMain()
    }

    @Test
    fun rescheduleGeneratedWeeklyOccurrence_movesOnlyThatOccurrence_andSuppressesOriginalDate() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val templateMonday = LocalDate.of(2026, 3, 23)
        val generatedMonday = LocalDate.of(2026, 3, 30)
        val movedSunday = LocalDate.of(2026, 3, 29)

        val templateTaskId = vm.createTaskForDate(
            date = templateMonday,
            time = null,
            description = "Weekly Monday task"
        )

        vm.setTaskRepeatRule(
            templateTaskId,
            RepeatRule(
                freq = RepeatFreq.WEEKLY,
                interval = 1,
                weekDays = setOf(DayOfWeek.MONDAY)
            )
        )

        val templateSubtaskId = vm.createSubtask(
            taskId = templateTaskId,
            description = "Template subtask"
        )

        vm.ensureGeneratedInRange(generatedMonday, generatedMonday)

        val afterGenerate = vm.state.value
        val generatedTask = afterGenerate.tasks.single {
            it.originTaskId == templateTaskId && it.date == generatedMonday
        }
        val generatedSubtask = afterGenerate.subtasks.single {
            it.taskId == generatedTask.id && it.originSubtaskId == templateSubtaskId
        }

        vm.rescheduleTaskToDate(generatedTask.id, movedSunday)

        val afterMove = vm.state.value
        val movedTask = afterMove.tasks.single { it.id == generatedTask.id }
        val movedSubtask = afterMove.subtasks.single { it.id == generatedSubtask.id }

        assertEquals(movedSunday, movedTask.date)
        assertNull(movedTask.originTaskId)
        assertNull(movedTask.repeatRule)

        assertEquals(movedTask.id, movedSubtask.taskId)
        assertNull(movedSubtask.originSubtaskId)
        assertNull(movedSubtask.repeatRule)

        assertTrue(
            afterMove.suppressedRecurrences.contains(
                "T:$templateTaskId:${generatedMonday.toEpochDay()}"
            )
        )
        assertTrue(
            afterMove.suppressedRecurrences.contains(
                "S:$templateSubtaskId:${generatedMonday.toEpochDay()}"
            )
        )

        vm.ensureGeneratedInRange(generatedMonday, generatedMonday)

        val afterRegenerate = vm.state.value

        assertFalse(
            afterRegenerate.tasks.any {
                it.originTaskId == templateTaskId && it.date == generatedMonday
            }
        )

        assertEquals(
            1,
            afterRegenerate.tasks.count { it.id == generatedTask.id && it.date == movedSunday }
        )
        assertEquals(
            1,
            afterRegenerate.subtasks.count { it.id == generatedSubtask.id && it.taskId == generatedTask.id }
        )
    }

    /**
     * The whole path the user actually walks: delete one occurrence, come back
     * to that day, and find it still gone.
     *
     * This needs both halves to be right — deleteTask writing the day's
     * tombstone, and the generator honouring it in the subtask loop. The JVM
     * tests pin the generator; this pins that the two agree.
     */
    @Test
    fun deletedOccurrenceOfATaskWithARepeatingSubtaskDoesNotComeBack() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val anchor = LocalDate.of(2026, 3, 23)
        val deletedDay = anchor.plusDays(2)

        val templateTaskId = vm.createTaskForDate(
            date = anchor,
            time = null,
            description = "Morning"
        )
        vm.setTaskRepeatRule(templateTaskId, RepeatRule(freq = RepeatFreq.DAILY))

        val templateSubtaskId = vm.createSubtask(
            taskId = templateTaskId,
            description = "Exercise"
        )
        vm.setSubtaskRepeatRule(templateSubtaskId, RepeatRule(freq = RepeatFreq.DAILY))

        vm.ensureGeneratedInRange(anchor.plusDays(1), anchor.plusDays(3))

        val occurrence = vm.state.value.tasks.single {
            it.originTaskId == templateTaskId && it.date == deletedDay
        }

        vm.deleteTask(occurrence.id)

        assertFalse(
            "precondition: the occurrence is gone right after deleting it",
            vm.state.value.tasks.any { it.date == deletedDay }
        )

        // Leaving the day and coming back is what used to resurrect it.
        vm.ensureGeneratedInRange(anchor.plusDays(1), anchor.plusDays(3))
        vm.ensureGeneratedInRange(deletedDay, deletedDay)

        assertFalse(
            "a deleted day must stay deleted",
            vm.state.value.tasks.any { it.date == deletedDay }
        )
        assertTrue(
            "the days around it are untouched",
            vm.state.value.tasks.any { it.date == anchor.plusDays(1) } &&
                vm.state.value.tasks.any { it.date == anchor.plusDays(3) }
        )
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