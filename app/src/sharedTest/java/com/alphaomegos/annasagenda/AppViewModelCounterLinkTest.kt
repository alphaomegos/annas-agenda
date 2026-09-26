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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * The exploit, walked end to end.
 *
 * A task could be pushed into "done" by something other than a tick — deleting
 * the last unfinished subtask, or moving it elsewhere — and those paths used to
 * leave the linked counter alone. The next honest untick then paid a +1 that
 * was never earned, and repeating the cycle grew the balance without limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppViewModelCounterLinkTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        // See AppViewModelRecurringRescheduleTest for why the main dispatcher
        // is replaced: a view model loads on viewModelScope, and these tests
        // block the thread that scope would otherwise need.
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
    fun deletingTheLastUnfinishedSubtaskCostsTheCounterItsOne() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val counterId = addCounter(vm, balance = 5)
        val taskId = vm.createTaskForDate(
            date = LocalDate.now(),
            time = null,
            description = "Push-ups",
        )
        vm.setTaskLinkedManualCounter(taskId, counterId)

        val firstSub = vm.createSubtask(taskId = taskId, description = "Set 1")
        val secondSub = vm.createSubtask(taskId = taskId, description = "Set 2")
        vm.toggleSubtaskDone(firstSub)

        assertFalse("precondition: one set is still open", isDone(vm, taskId))
        assertEquals(5, balanceOf(vm, counterId))

        // Removing the only open subtask makes the task done on its own.
        vm.deleteSubtask(secondSub)

        assertTrue(isDone(vm, taskId))
        assertEquals("becoming done costs the counter one", 4, balanceOf(vm, counterId))

        // And unticking gives back exactly that one.
        vm.toggleTaskDone(taskId)

        assertFalse(isDone(vm, taskId))
        assertEquals("the balance must not grow out of nothing", 5, balanceOf(vm, counterId))
    }

    @Test
    fun movingTheLastUnfinishedSubtaskAwayCostsTheCounterItsOne() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        val counterId = addCounter(vm, balance = 5)

        val taskId = vm.createTaskForDate(date = today, time = null, description = "Push-ups")
        vm.setTaskLinkedManualCounter(taskId, counterId)

        val otherTaskId = vm.createTaskForDate(date = today, time = null, description = "Elsewhere")

        val firstSub = vm.createSubtask(taskId = taskId, description = "Set 1")
        val secondSub = vm.createSubtask(taskId = taskId, description = "Set 2")
        vm.toggleSubtaskDone(firstSub)

        assertEquals(5, balanceOf(vm, counterId))

        vm.moveSubtask(secondSub, otherTaskId)

        assertTrue(isDone(vm, taskId))
        assertEquals(4, balanceOf(vm, counterId))
    }

    @Test
    fun anOrdinaryTickAndUntickLeaveTheBalanceWhereItStarted() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val counterId = addCounter(vm, balance = 5)
        val taskId = vm.createTaskForDate(
            date = LocalDate.now(),
            time = null,
            description = "Push-ups",
        )
        vm.setTaskLinkedManualCounter(taskId, counterId)

        vm.toggleTaskDone(taskId)
        assertEquals(4, balanceOf(vm, counterId))

        vm.toggleTaskDone(taskId)
        assertEquals(5, balanceOf(vm, counterId))
    }

    private fun addCounter(vm: AppViewModel, balance: Int): Long {
        vm.addManualCounter("Push-ups", balance)
        return vm.state.value.counters.filterIsInstance<ManualCounter>().single().id
    }

    private fun balanceOf(vm: AppViewModel, counterId: Long): Int =
        vm.state.value.counters
            .filterIsInstance<ManualCounter>()
            .single { it.id == counterId }
            .balance

    private fun isDone(vm: AppViewModel, taskId: Long): Boolean =
        vm.state.value.tasks.single { it.id == taskId }.isDone

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
