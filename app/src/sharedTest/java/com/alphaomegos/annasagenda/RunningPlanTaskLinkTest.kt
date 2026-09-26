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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * A running plan row remembers the id of the task it created. Nothing checked
 * that the task was still there, and ids are handed out as "one past the
 * largest in use" — so a number freed by a deletion comes back after a restart,
 * and the row ends up pointing at whatever the user created next.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RunningPlanTaskLinkTest {

    private lateinit var app: Application

    private val date: LocalDate = LocalDate.now().plusDays(3)

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
    fun deletingTheTaskFromTheCalendarUnlinksThePlanRow() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val taskId = approvedRowTaskId(vm)

        vm.deleteTask(taskId)

        val row = row(vm)
        assertNotNull("the row itself stays", row)
        assertNull("but it must not remember a task that is gone", row!!.taskId)
        assertEquals("5", row.distanceKmText)
    }

    /**
     * The invariant that makes the plan safe: a row may only ever name a task
     * that is actually there. Reset and rename both act on whatever the row
     * names, so as long as this holds, neither can reach a stranger — which is
     * what a reused id used to hand them.
     */
    @Test
    fun noPlanRowNamesATaskThatIsGone() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val taskId = approvedRowTaskId(vm)
        vm.deleteTask(taskId)

        val liveTaskIds = vm.state.value.tasks.map { it.id }.toSet()
        val named = vm.state.value.runningPlanEntries.mapNotNull { it.taskId }

        assertTrue(
            "the plan names tasks that do not exist: ${named - liveTaskIds}",
            named.all { it in liveTaskIds },
        )
    }

    /** A row whose task was deleted used to be stuck: no rename, no new task. */
    @Test
    fun editingARowWhoseTaskWasDeletedGivesItANewTask() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val taskId = approvedRowTaskId(vm)
        vm.deleteTask(taskId)

        vm.updateRunningPlanEntry(date, distanceKmText = "7")

        val row = row(vm)!!
        assertNotNull("the row must get a task again", row.taskId)
        assertNotEquals(taskId, row.taskId)

        val task = vm.state.value.tasks.single { it.id == row.taskId }
        assertEquals(date, task.date)
        assertTrue(
            "and its title must follow the new distance: ${task.description}",
            task.description.contains("7"),
        )
    }

    private fun approvedRowTaskId(vm: AppViewModel): Long {
        vm.updateRunningPlanEntry(date, distanceKmText = "5")
        vm.approveRunningPlan()

        val row = row(vm)
        assertNotNull("approving must create the task", row?.taskId)
        return row!!.taskId!!
    }

    private fun row(vm: AppViewModel): RunningPlanEntry? =
        vm.state.value.runningPlanEntries.firstOrNull { it.date == date }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
