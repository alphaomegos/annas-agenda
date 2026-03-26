package com.alphaomegos.annasagenda

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
class AppViewModelRecurringRescheduleInstrumentedTest {

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