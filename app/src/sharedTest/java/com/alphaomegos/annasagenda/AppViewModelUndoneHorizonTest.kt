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
import java.time.LocalDate

/**
 * The behaviour this patch exists for: what is owed must not depend on which
 * months were opened in the calendar.
 *
 * Nothing here ever calls ensureGeneratedInRange for a calendar range — that is
 * the point. The debt has to appear anyway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppViewModelUndoneHorizonTest {

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

    @Test
    fun aRepeatingTaskIsOwedEvenIfItsMonthWasNeverOpened() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        val anchor = today.minusDays(10)

        val templateId = vm.createTaskForDate(
            date = anchor,
            time = null,
            description = "Daily thing"
        )
        vm.setTaskRepeatRule(templateId, RepeatRule(freq = RepeatFreq.DAILY))

        // Nothing has drawn a calendar month, so no occurrence exists yet.
        assertTrue(
            "precondition: no occurrences materialised",
            vm.state.value.tasks.none { it.originTaskId == templateId }
        )

        val debt = vm.prepareUndoneDebt(today)

        assertFalse("the missed occurrences should now be owed", debt.isEmpty)
        assertTrue(
            "every owed day must be in the past",
            debt.dates.all { it.isBefore(today) }
        )
        assertTrue(
            "the days between the anchor and today should be owed",
            debt.dates.contains(today.minusDays(1))
        )
    }

    @Test
    fun theLampAgreesWithTheScreenWithoutAnyCalendarVisit() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        val templateId = vm.createTaskForDate(
            date = today.minusDays(7),
            time = null,
            description = "Daily thing"
        )
        vm.setTaskRepeatRule(templateId, RepeatRule(freq = RepeatFreq.DAILY))

        vm.ensureUndoneHorizonGenerated(today)

        assertTrue(vm.hasUndonePastTasks(today))
        assertEquals(
            vm.undonePastTaskDates(today),
            vm.prepareUndoneDebt(today).dates,
        )
    }

    @Test
    fun theHorizonBoundsWhatIsOwed() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        val templateId = vm.createTaskForDate(
            date = today.minusDays(60),
            time = null,
            description = "Daily thing"
        )
        vm.setTaskRepeatRule(templateId, RepeatRule(freq = RepeatFreq.DAILY))

        vm.setUndoneHorizonDays(7)
        val narrow = vm.prepareUndoneDebt(today)

        assertTrue(
            "nothing older than the horizon may be owed",
            narrow.dates.all { !it.isBefore(today.minusDays(7)) }
        )

        vm.setUndoneHorizonDays(30)
        val wider = vm.prepareUndoneDebt(today)

        assertTrue(
            "a wider horizon must owe at least as much",
            wider.dates.size > narrow.dates.size
        )
    }

    @Test
    fun generatingTheHorizonTwiceChangesNothing() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        val templateId = vm.createTaskForDate(
            date = today.minusDays(10),
            time = null,
            description = "Daily thing"
        )
        vm.setTaskRepeatRule(templateId, RepeatRule(freq = RepeatFreq.DAILY))

        vm.ensureUndoneHorizonGenerated(today)
        val afterFirst = vm.state.value

        vm.ensureUndoneHorizonGenerated(today)

        assertEquals(afterFirst.tasks, vm.state.value.tasks)
        assertEquals(afterFirst.subtasks, vm.state.value.subtasks)
    }

    @Test
    fun nothingIsGeneratedWhenNothingRepeats() = runBlocking {
        val vm = AppViewModel(app)
        awaitWorkingStore(vm)
        vm.resetAllData()

        val today = LocalDate.now()
        vm.createTaskForDate(
            date = today.minusDays(3),
            time = null,
            description = "One-off"
        )

        val before = vm.state.value.tasks
        vm.ensureUndoneHorizonGenerated(today)

        assertEquals(before, vm.state.value.tasks)
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
