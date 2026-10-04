package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * A task with a time: where it sits in its day, and what the arrows may do.
 *
 * Most of this file is about the boundary between the two blocks of a day —
 * timed on top, by the clock; untimed below, by hand — because that boundary
 * is where an arrow could do something the next sort would undo.
 */
class TaskTimeSupportTest {

    private val day = LocalDate.of(2026, 3, 2)
    private val otherDay = day.plusDays(1)
    private val nine = LocalTime.of(9, 0)
    private val noon = LocalTime.of(12, 0)

    private fun task(id: Long, order: Int, time: LocalTime? = null, date: LocalDate? = day) =
        Task(id = id, order = order, date = date, time = time, description = "t$id")

    private fun ids(tasks: List<Task>) = tasks.map { it.id }

    /** The day as the screen would draw it. */
    private fun shown(tasks: List<Task>, date: LocalDate? = day) =
        ids(tasksInDayOrder(tasks.filter { it.date == date }))

    /* ---------------- the order of a day ---------------- */

    @Test
    fun timedTasksComeFirstByTheClockThenTheRestByHand() {
        val tasks = listOf(
            task(1, order = 0),
            task(2, order = 1, time = noon),
            task(3, order = 2),
            task(4, order = 3, time = nine),
        )

        assertEquals(listOf(4L, 2L, 1L, 3L), shown(tasks))
    }

    /** Two tasks at 09:00 never swap places between launches. */
    @Test
    fun equalTimesFallBackToTheManualOrderThenTheId() {
        val tasks = listOf(
            task(5, order = 1, time = nine),
            task(6, order = 0, time = nine),
            task(8, order = 2, time = nine),
            task(7, order = 2, time = nine),
        )

        assertEquals(listOf(6L, 5L, 7L, 8L), shown(tasks))
    }

    /* ---------------- what the arrows may do ---------------- */

    @Test
    fun aTaskWithATimeDoesNotMoveByHand() {
        val tasks = listOf(task(1, order = 0, time = nine), task(2, order = 1, time = noon))

        assertFalse(canMoveTask(tasks, 2, step = -1))
        assertFalse(canMoveTask(tasks, 1, step = 1))
        assertSame(tasks, moveTaskWithinDate(tasks, 2, step = -1))
    }

    /** The first untimed task sits right under the timed block and stays there. */
    @Test
    fun anUntimedTaskNeverMovesAboveATimedOne() {
        val tasks = listOf(task(1, order = 0, time = nine), task(2, order = 1), task(3, order = 2))

        assertFalse(canMoveTask(tasks, 2, step = -1))
        assertSame(tasks, moveTaskWithinDate(tasks, 2, step = -1))
    }

    @Test
    fun untimedTasksStillMoveAmongThemselves() {
        val tasks = listOf(task(1, order = 0, time = nine), task(2, order = 1), task(3, order = 2))

        assertTrue(canMoveTask(tasks, 3, step = -1))
        assertEquals(listOf(1L, 3L, 2L), shown(moveTaskWithinDate(tasks, 3, step = -1)))
    }

    /**
     * After a move every `order` on the day is unique again, timed tasks
     * included — the recurrence pruning refuses a day where two share one.
     */
    @Test
    fun aMoveLeavesEveryOrderOnTheDayUnique() {
        // A timed task holding order 1, the untimed ones around it: the hole
        // a task leaves when it gets a time.
        val tasks = listOf(task(1, order = 0), task(2, order = 1, time = nine), task(3, order = 2))

        val after = moveTaskWithinDate(tasks, 3, step = -1)

        assertEquals(after.size, after.map { it.order }.toSet().size)
        assertEquals(listOf(2L, 3L, 1L), shown(after))
    }

    @Test
    fun aMoveLeavesOtherDaysAlone() {
        val elsewhere = task(9, order = 0, date = otherDay)
        val tasks = listOf(task(1, order = 0), task(2, order = 1), elsewhere)

        val after = moveTaskWithinDate(tasks, 2, step = -1)

        assertSame(elsewhere, after.first { it.id == 9L })
    }

    @Test
    fun theEndsOfTheUntimedBlockAndMissingTasksDoNotMove() {
        val tasks = listOf(task(1, order = 0), task(2, order = 1))

        assertFalse(canMoveTask(tasks, 2, step = 1))
        assertFalse(canMoveTask(tasks, 1, step = -1))
        assertFalse(canMoveTask(tasks, 42, step = 1))
    }

    /* ---------------- setting and clearing the time ---------------- */

    @Test
    fun settingATimeKeepsTheOrderAndLiftsTheTaskToTheTop() {
        val tasks = listOf(task(1, order = 0), task(2, order = 1), task(3, order = 2))

        val after = tasksAfterSettingTime(tasks, 3, nine)

        assertEquals(2, after.first { it.id == 3L }.order)
        assertEquals(nine, after.first { it.id == 3L }.time)
        assertEquals(listOf(3L, 1L, 2L), shown(after))
    }

    /** Back to the end of the untimed block, as a new task would land. */
    @Test
    fun clearingTheTimeDropsTheTaskToTheEndOfTheUntimedOnes() {
        val tasks = listOf(task(1, order = 0, time = nine), task(2, order = 1), task(3, order = 2))

        val after = tasksAfterSettingTime(tasks, 1, null)

        assertNull(after.first { it.id == 1L }.time)
        assertEquals(listOf(2L, 3L, 1L), shown(after))
    }

    /** "Someday at 09:00" names no moment. */
    @Test
    fun aTaskWithoutADayCannotHaveATime() {
        val tasks = listOf(task(1, order = 0, date = null))

        assertSame(tasks, tasksAfterSettingTime(tasks, 1, nine))
    }

    @Test
    fun theSameTimeOrAMissingTaskChangesNothing() {
        val tasks = listOf(task(1, order = 0, time = nine))

        assertSame(tasks, tasksAfterSettingTime(tasks, 1, nine))
        assertSame(tasks, tasksAfterSettingTime(tasks, 42, noon))
    }

    /* ---------------- moving to another day ---------------- */

    @Test
    fun movingToAnotherDayKeepsTheTime() {
        val tasks = listOf(task(1, order = 0, time = nine))

        val after = stateAfterReschedulingTask(tasks, emptyList(), emptySet(), 1, otherDay)

        assertEquals(nine, after.tasks.single().time)
    }

    @Test
    fun movingToSomedayTakesTheTimeAway() {
        val tasks = listOf(task(1, order = 0, time = nine))

        val after = stateAfterReschedulingTask(tasks, emptyList(), emptySet(), 1, null)

        assertNull(after.tasks.single().time)
    }

    /** An occurrence moved to Someday is detached and loses its time all the same. */
    @Test
    fun anOccurrenceMovedToSomedayLosesItsTimeToo() {
        val occurrence = Task(id = 2, date = day, time = nine, description = "x", originTaskId = 1)

        val after = stateAfterReschedulingTask(listOf(occurrence), emptyList(), emptySet(), 2, null)

        assertNull(after.tasks.single().time)
        assertNull(after.tasks.single().originTaskId)
    }

    /* ---------------- repeats ---------------- */

    /** Built by the generator itself, not drawn by hand. */
    @Test
    fun everyOccurrenceOfARepeatCarriesTheTemplatesTime() {
        val template = Task(
            id = 1,
            date = day,
            time = nine,
            description = "vitamins",
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = DayOfWeek.MONDAY),
        )

        val generated = generateRecurrencesInRange(
            tasks = listOf(template),
            subtasks = emptyList(),
            suppressedRecurrences = emptySet(),
            start = day,
            end = day.plusDays(3),
            nextId = 100L,
            defaultWeekStart = DayOfWeek.MONDAY,
        )

        val occurrences = generated.tasks.filter { it.originTaskId == 1L }
        // A rule never fires on its own anchor day: four days drawn, three copies.
        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.time == nine })
    }

    /* ---------------- showing and keeping a time ---------------- */

    @Test
    fun aTimeIsShownOnTheTwentyFourHourClockWithTwoDigitsEachSide() {
        assertEquals("09:05", formatTaskTime(LocalTime.of(9, 5)))
        assertEquals("00:00", formatTaskTime(LocalTime.MIDNIGHT))
        assertEquals("23:59", formatTaskTime(LocalTime.of(23, 59)))
    }

    /** Not a formatter, so the machine's language cannot change the digits. */
    @Test
    fun theShownTimeDoesNotDependOnTheMachinesLanguage() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("21:30", formatTaskTime(LocalTime.of(21, 30)))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    /** What survives a rotation on the new task screen comes back as it went in. */
    @Test
    fun aTimeSurvivesTheTripThroughMinutesOfTheDay() {
        listOf(LocalTime.MIDNIGHT, LocalTime.of(9, 5), LocalTime.of(23, 59)).forEach { t ->
            assertEquals(t, timeFromMinuteOfDay(minuteOfDay(t)))
        }
        assertNull(timeFromMinuteOfDay(null))
        assertNull(timeFromMinuteOfDay(-1))
        assertNull(timeFromMinuteOfDay(24 * 60))
    }
}
