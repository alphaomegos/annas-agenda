package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regression tests for state mutations that used to be written back from a
 * snapshot taken before an intermediate mutation, silently undoing it.
 *
 * These are not thread races — everything in AppViewModel runs on the main
 * dispatcher. They are lost updates within a single call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppViewModelStaleStateTest {

    private lateinit var app: Application

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

    /**
     * deleteSubtask() called suppress() and then overwrote the state with a
     * snapshot captured before it, discarding the tombstone. The user deleted a
     * recurring subtask for one day and it came back the next time that day was
     * generated.
     */
    @Test
    fun deleteGeneratedRecurringSubtask_keepsTombstone_soItIsNotRegenerated() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val anchorMonday = LocalDate.of(2026, 3, 23)
        val targetMonday = LocalDate.of(2026, 3, 30)

        val templateTaskId = vm.createTaskForDate(
            date = anchorMonday,
            time = null,
            description = "Template task"
        )

        // The recurrence lives on the SUBTASK here, not on the task.
        val templateSubtaskId = vm.createSubtask(
            taskId = templateTaskId,
            description = "Weekly subtask"
        )
        vm.setSubtaskRepeatRule(
            templateSubtaskId,
            RepeatRule(
                freq = RepeatFreq.WEEKLY,
                interval = 1,
                weekDays = setOf(DayOfWeek.MONDAY)
            )
        )

        vm.ensureGeneratedInRange(targetMonday, targetMonday)

        val generatedSubtask = vm.state.value.subtasks.single {
            it.originSubtaskId == templateSubtaskId
        }

        vm.deleteSubtask(generatedSubtask.id)

        val expectedKey = "S:$templateSubtaskId:${targetMonday.toEpochDay()}"
        assertTrue(
            "tombstone $expectedKey was dropped; " +
                "suppressedRecurrences = ${vm.state.value.suppressedRecurrences}",
            vm.state.value.suppressedRecurrences.contains(expectedKey)
        )

        // Navigating back to that day must not bring the subtask back.
        vm.ensureGeneratedInRange(targetMonday, targetMonday)

        assertFalse(
            "deleted recurring subtask was regenerated",
            vm.state.value.subtasks.any { it.originSubtaskId == templateSubtaskId }
        )
    }

    /**
     * pruneRunningPlanNow() deleted the tasks linked to expired plan entries and
     * then wrote back a snapshot taken before those deletions, restoring every
     * task it had just removed. The plan entry disappeared but its task stayed
     * in the calendar forever, with nothing left to clean it up.
     */
    @Test
    fun pruningExpiredRunningPlanEntry_alsoRemovesItsTask() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        // Far enough in the past to count as expired, and incomplete (distance
        // only, no duration or pace), which is what prune looks for.
        val pastDate = LocalDate.now().minusDays(5)

        vm.updateRunningPlanEntry(date = pastDate, distanceKmText = "5")
        vm.approveRunningPlan()

        val approvedEntry = vm.state.value.runningPlanEntries.single { it.date == pastDate }
        val linkedTaskId = requireNotNull(approvedEntry.taskId) {
            "approveRunningPlan() did not create a task for the entry"
        }
        assertTrue(vm.state.value.tasks.any { it.id == linkedTaskId })

        vm.pruneRunningPlanNow()

        assertTrue(
            "expired plan entry was not pruned",
            vm.state.value.runningPlanEntries.none { it.date == pastDate }
        )
        assertFalse(
            "task of the pruned entry was resurrected and is now orphaned",
            vm.state.value.tasks.any { it.id == linkedTaskId }
        )
    }

    /**
     * Guards the ordinary path: pruning must not disturb unrelated tasks.
     */
    @Test
    fun pruningExpiredRunningPlanEntry_leavesUnrelatedTasksAlone() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val unrelatedTaskId = vm.createTaskForDate(
            date = LocalDate.now(),
            time = null,
            description = "Unrelated task"
        )

        val pastDate = LocalDate.now().minusDays(5)
        vm.updateRunningPlanEntry(date = pastDate, distanceKmText = "5")
        vm.approveRunningPlan()

        vm.pruneRunningPlanNow()

        assertEquals(
            1,
            vm.state.value.tasks.count { it.id == unrelatedTaskId }
        )
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
