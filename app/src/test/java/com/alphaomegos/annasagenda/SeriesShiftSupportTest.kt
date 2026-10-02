package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Moving one day of a series and the rest of the series with it.
 *
 * The case, from 02.10.2026: "water the plants every two days", Monday missed
 * and moved to Tuesday — the Wednesday after it must become Thursday, or the
 * plants get watered on wet soil.
 *
 * As in SeriesEditSupportTest, every state is built by the generator and the
 * expected days are asked of it too: a fresh series with the shifted rule,
 * started on the new day.
 */
class SeriesShiftSupportTest {

    private val weekStart = DayOfWeek.MONDAY

    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = monday.plusDays(1)
    private val rangeEnd = LocalDate.of(2026, 12, 31)

    private val everyTwoDays = RepeatRule(freq = RepeatFreq.DAILY, interval = 2, weekStart = weekStart)

    private var idCursor = 1_000_000L
    private fun newId(): Long = idCursor++

    private data class State(
        val tasks: List<Task>,
        val subtasks: List<Subtask> = emptyList(),
        val suppressed: Set<String> = emptySet(),
    )

    private fun drawn(state: State, from: LocalDate, to: LocalDate = rangeEnd): State {
        val g = generateRecurrencesInRange(
            tasks = state.tasks,
            subtasks = state.subtasks,
            suppressedRecurrences = state.suppressed,
            start = from,
            end = to,
            nextId = ((state.tasks.map { it.id } + state.subtasks.map { it.id })
                .filter { it < 1_000_000L }.maxOrNull() ?: 0L) + 1,
            defaultWeekStart = weekStart,
        )
        return state.copy(tasks = g.tasks, subtasks = g.subtasks)
    }

    private fun shift(state: State, occurrenceId: Long, to: LocalDate): State {
        val r = stateAfterShiftingSeriesFrom(
            tasks = state.tasks,
            subtasks = state.subtasks,
            suppressedRecurrences = state.suppressed,
            occurrenceId = occurrenceId,
            newDate = to,
            newId = ::newId,
            weekStart = weekStart,
        )
        return State(r.tasks, r.subtasks, r.suppressedRecurrences)
    }

    private fun shownOn(state: State, date: LocalDate): List<Task> =
        state.tasks
            .filter { it.date == date }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressed) }

    private fun days(state: State, from: LocalDate, to: LocalDate = rangeEnd): Set<LocalDate> =
        state.tasks
            .filter { it.date != null && !it.date!!.isBefore(from) && !it.date!!.isAfter(to) }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressed) }
            .mapTo(sortedSetOf()) { it.date!! }

    /** What a brand-new series with [rule] starting on [start] would look like. */
    private fun freshSeriesDays(rule: RepeatRule, start: LocalDate): Set<LocalDate> =
        days(drawn(State(listOf(Task(id = 77, date = start, description = "x", repeatRule = rule))), from = start), start)

    /** The watering series, started a week before the missed Monday and drawn ahead. */
    private fun plants(rule: RepeatRule = everyTwoDays, start: LocalDate = monday.minusDays(6)): State =
        drawn(State(listOf(Task(id = 1, date = start, description = "Полить цветы", repeatRule = rule))), from = start)

    private fun occurrenceOn(state: State, date: LocalDate) =
        state.tasks.single { it.date == date && it.originTaskId != null }

    /* ---------------- the case from 02.10 ---------------- */

    @Test
    fun theMissedMondayMovedToTuesdayTakesTheRestOfTheSeriesWithIt() {
        val before = plants()
        val after = drawn(shift(before, occurrenceOn(before, monday).id, tuesday), from = tuesday)

        assertEquals(freshSeriesDays(everyTwoDays, tuesday), days(after, tuesday))
        // Wednesday was the next watering; now it is Thursday.
        assertTrue(shownOn(after, monday.plusDays(2)).isEmpty())
        assertEquals(1, shownOn(after, monday.plusDays(3)).size)
    }

    @Test
    fun theDaysBeforeTheMissedOneStayAsTheyWere() {
        val before = plants()
        val after = shift(before, occurrenceOn(before, monday).id, tuesday)

        val past = { st: State -> st.tasks.filter { it.date!!.isBefore(monday) }.map { it.id to it.date }.toSet() }
        assertEquals(past(before), past(after))
    }

    @Test
    fun theMovedDayIsTheSameTaskOnTheNewDay() {
        val before = plants()
        val missed = occurrenceOn(before, monday)

        val after = drawn(shift(before, missed.id, tuesday), from = tuesday)

        assertEquals(missed.id, shownOn(after, tuesday).single().id)
        assertTrue(shownOn(after, monday).isEmpty())
    }

    @Test
    fun eachDayStillHasOneWatering() {
        val before = plants()
        val after = drawn(shift(before, occurrenceOn(before, monday).id, tuesday), from = tuesday)

        days(after, tuesday).forEach { assertEquals("$it", 1, shownOn(after, it).size) }
    }

    /* ---------------- what was already done ahead ---------------- */

    @Test
    fun aDayTickedAheadMovesWithItsTick() {
        val before = plants()
        val ahead = occurrenceOn(before, monday.plusDays(4))
        val ticked = before.copy(tasks = before.tasks.map { if (it.id == ahead.id) it.copy(isDone = true) else it })

        val after = shift(ticked, occurrenceOn(ticked, monday).id, tuesday)
        val moved = after.tasks.single { it.id == ahead.id }

        assertEquals(monday.plusDays(5), moved.date)
        assertTrue(moved.isDone)
    }

    @Test
    fun aDayDeletedAheadStaysDeletedMovedTheSameWay() {
        val before = plants()
        val deletedDay = monday.plusDays(6)
        val withDeletion = before.copy(
            tasks = before.tasks.filterNot { it.date == deletedDay },
            suppressed = setOf(taskSuppressionKey(1, deletedDay)),
        )

        val after = drawn(shift(withDeletion, occurrenceOn(withDeletion, monday).id, tuesday), from = tuesday)

        assertTrue(shownOn(after, deletedDay.plusDays(1)).isEmpty())
        assertEquals(1, shownOn(after, deletedDay.plusDays(3)).size)
    }

    @Test
    fun subtasksMoveWithTheirDays() {
        val start = monday.minusDays(6)
        val before = drawn(
            State(
                listOf(Task(id = 1, date = start, description = "Полить цветы", repeatRule = everyTwoDays)),
                listOf(Subtask(id = 2, taskId = 1, description = "и подкормить")),
            ),
            from = start,
        )

        val after = drawn(shift(before, occurrenceOn(before, monday).id, tuesday), from = tuesday)

        days(after, tuesday).forEach { day ->
            val task = shownOn(after, day).single()
            assertEquals("$day", listOf("и подкормить"), after.subtasks.filter { it.taskId == task.id }.map { it.description })
        }
    }

    /* ---------------- other rules move as you would expect ---------------- */

    @Test
    fun thursdaysMovedByADayBecomeFridays() {
        val thursdays = RepeatRule(freq = RepeatFreq.WEEKLY, weekDays = setOf(DayOfWeek.THURSDAY), weekStart = weekStart)
        val before = plants(rule = thursdays, start = LocalDate.of(2026, 9, 3))
        val thursday = LocalDate.of(2026, 10, 8)

        val after = drawn(shift(before, occurrenceOn(before, thursday).id, thursday.plusDays(1)), from = thursday)
        val fridays = thursdays.copy(weekDays = setOf(DayOfWeek.FRIDAY))

        assertEquals(freshSeriesDays(fridays, thursday.plusDays(1)), days(after, thursday))
    }

    @Test
    fun twoWeekdaysMoveTogether() {
        assertEquals(
            setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            shiftedRule(
                RepeatRule(freq = RepeatFreq.WEEKLY, weekDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
                1,
                tuesday,
            ).weekDays,
        )
        // Across the end of the week.
        assertEquals(
            setOf(DayOfWeek.MONDAY),
            shiftedRule(RepeatRule(freq = RepeatFreq.WEEKLY, weekDays = setOf(DayOfWeek.SUNDAY)), 1, tuesday).weekDays,
        )
    }

    @Test
    fun aMonthlyDayMovesToTheNewDayOfTheMonth() {
        val fifteenth = RepeatRule(freq = RepeatFreq.MONTHLY, dayOfMonth = 15, weekStart = weekStart)
        val before = plants(rule = fifteenth, start = LocalDate.of(2026, 8, 15))
        val october15 = LocalDate.of(2026, 10, 15)

        val after = drawn(shift(before, occurrenceOn(before, october15).id, october15.plusDays(2)), from = october15)

        assertTrue(days(after, october15).all { it.dayOfMonth == 17 })
        assertEquals(freshSeriesDays(fifteenth.copy(dayOfMonth = 17), october15.plusDays(2)), days(after, october15))
    }

    /* ---------------- saving afterwards changes nothing ---------------- */

    @Test
    fun pruningAndRedrawingAfterAShiftChangesNothingVisible() {
        val before = plants()
        val shifted = drawn(shift(before, occurrenceOn(before, monday).id, tuesday), from = tuesday)

        val pruned = pruneRedundantGeneratedOccurrences(
            tasks = shifted.tasks,
            subtasks = shifted.subtasks,
            suppressedRecurrences = shifted.suppressed,
            runningPlanEntries = emptyList(),
            isPrunableDate = { it.isAfter(tuesday) },
            weekStart = weekStart,
        )
        val redrawn = drawn(State(pruned.tasks, pruned.subtasks, shifted.suppressed), from = tuesday)

        val look = { st: State -> days(st, tuesday).associateWith { d -> shownOn(st, d).map { it.description } } }
        assertEquals(look(shifted), look(redrawn))
    }

    /* ---------------- when not to ask ---------------- */

    @Test
    fun theQuestionIsOnlyForADayOfASeriesMovedToAnotherDay() {
        val before = plants()
        val missed = occurrenceOn(before, monday)
        val plain = Task(id = 500, date = monday, description = "просто задача")
        val tasks = before.tasks + plain

        assertTrue(canShiftSeries(tasks, missed.id, tuesday))
        assertFalse(canShiftSeries(tasks, missed.id, null))       // Someday
        assertFalse(canShiftSeries(tasks, missed.id, monday))     // the same day
        assertFalse(canShiftSeries(tasks, 1L, tuesday))           // the template's own day
        assertFalse(canShiftSeries(tasks, plain.id, tuesday))     // no series
        assertFalse(canShiftSeries(tasks, 999_999L, tuesday))     // nothing
    }

    @Test
    fun withNothingToAskAboutNothingChanges() {
        val before = plants()

        assertSame(before.tasks, shift(before, 1L, tuesday).tasks)
    }
}
