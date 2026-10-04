package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
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
 * Editing a repeating task from today on.
 *
 * The case this exists for, from 02.10.2026: a weekly Thursday task called
 * "17:00 созвон с психотерапевтом", to become "Созвон с психотерапевтом" at
 * 17:00 — with every Thursday already past left exactly as it was.
 *
 * Every state here is built by the generator itself, and every check reads
 * the days the way a screen does (template hidden on a day it is tombstoned
 * on), so the tests are about what the user would see.
 */
class SeriesEditSupportTest {

    private val weekStart = DayOfWeek.MONDAY

    /** A Thursday a month back; 02.10.2026 is a Friday. */
    private val firstThursday = LocalDate.of(2026, 9, 3)
    private val today = LocalDate.of(2026, 10, 2)
    private val nextThursday = LocalDate.of(2026, 10, 8)
    private val rangeEnd = LocalDate.of(2026, 12, 31)

    private val oldName = "17:00 созвон с психотерапевтом"
    private val newName = "Созвон с психотерапевтом"
    private val five = LocalTime.of(17, 0)

    private val thursdays = RepeatRule(
        freq = RepeatFreq.WEEKLY,
        weekDays = setOf(DayOfWeek.THURSDAY),
        weekStart = weekStart,
    )

    /** Far above anything the generator hands out below. */
    private var idCursor = 1_000_000L
    private fun newId(): Long = idCursor++

    private data class State(
        val tasks: List<Task>,
        val subtasks: List<Subtask>,
        val suppressed: Set<String> = emptySet(),
    )

    private fun template(
        date: LocalDate = firstThursday,
        rule: RepeatRule = thursdays,
        description: String = oldName,
    ) = Task(id = 1, date = date, description = description, repeatRule = rule)

    private fun drawn(state: State, from: LocalDate = firstThursday, to: LocalDate = rangeEnd): State {
        val g = generateRecurrencesInRange(
            tasks = state.tasks,
            subtasks = state.subtasks,
            suppressedRecurrences = state.suppressed,
            start = from,
            end = to,
            nextId = ((state.tasks.map { it.id } + state.subtasks.map { it.id }).filter { it < 1_000_000L }.maxOrNull() ?: 0L) + 1,
            defaultWeekStart = weekStart,
        )
        return state.copy(tasks = g.tasks, subtasks = g.subtasks)
    }

    private fun edit(
        state: State,
        description: String = newName,
        time: LocalTime? = five,
        from: LocalDate = today,
        id: Long = 1,
        rule: RepeatRule? = null,
    ): State {
        val r = stateAfterEditingTaskSeriesFrom(
            tasks = state.tasks,
            subtasks = state.subtasks,
            suppressedRecurrences = state.suppressed,
            templateTaskId = id,
            fromDate = from,
            description = description,
            time = time,
            newId = ::newId,
            weekStart = weekStart,
            rule = rule,
        )
        return State(r.tasks, r.subtasks, r.suppressedRecurrences)
    }

    /** The tasks a screen shows on [date]. */
    private fun shownOn(state: State, date: LocalDate): List<Task> =
        state.tasks
            .filter { it.date == date }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressed) }

    private fun daysWithTasks(state: State, from: LocalDate = today): Set<LocalDate> =
        state.tasks
            .filter { it.date != null && !it.date!!.isBefore(from) && !it.date!!.isAfter(rangeEnd) }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressed) }
            .mapTo(sortedSetOf()) { it.date!! }

    /* ---------------- the case from 02.10 ---------------- */

    @Test
    fun theDaysBeforeTodayStayExactlyAsTheyWere() {
        val before = drawn(State(listOf(template()), emptyList()))
        val after = drawn(edit(before))

        val past = { s: State -> s.tasks.filter { it.date!!.isBefore(today) }.sortedBy { it.id } }
        assertEquals(past(before).map { it.copy(repeatRule = null) }, past(after).map { it.copy(repeatRule = null) })
        assertTrue(past(after).all { it.description == oldName && it.time == null })
    }

    @Test
    fun everyDayFromTodayHasTheNewNameAndTime() {
        val after = drawn(edit(drawn(State(listOf(template()), emptyList()))))

        val future = daysWithTasks(after).flatMap { shownOn(after, it) }
        assertTrue(future.isNotEmpty())
        assertTrue(future.all { it.description == newName && it.time == five })
    }

    @Test
    fun eachDayStillHasExactlyOneOccurrence() {
        val after = drawn(edit(drawn(State(listOf(template()), emptyList()))))

        daysWithTasks(after).forEach { day -> assertEquals("$day", 1, shownOn(after, day).size) }
    }

    @Test
    fun theOldTemplateKeepsItsDayAndBecomesHistory() {
        val after = edit(drawn(State(listOf(template()), emptyList())))
        val old = after.tasks.first { it.id == 1L }

        assertEquals(firstThursday, old.date)
        assertEquals(oldName, old.description)
        assertNull(old.repeatRule)
    }

    /* ---------------- the same days, for every kind of rule ---------------- */

    /**
     * Before and after the edit the series lands on exactly the same days
     * from today on — the generator asked both times. Includes the rules
     * that take something from their first day without saying so.
     */
    @Test
    fun theSeriesLandsOnTheSameDaysWhateverTheRule() {
        val cases = listOf(
            firstThursday to thursdays,
            firstThursday to thursdays.copy(interval = 2),
            firstThursday to thursdays.copy(weekStart = null),
            LocalDate.of(2026, 9, 1) to RepeatRule(freq = RepeatFreq.DAILY, interval = 3),
            LocalDate.of(2026, 1, 31) to RepeatRule(freq = RepeatFreq.MONTHLY),
            LocalDate.of(2026, 1, 31) to RepeatRule(freq = RepeatFreq.MONTHLY, interval = 2),
            LocalDate.of(2026, 3, 15) to RepeatRule(freq = RepeatFreq.MONTHLY, dayOfMonth = 15),
        )

        cases.forEach { (anchor, rule) ->
            val start = State(listOf(template(date = anchor, rule = rule)), emptyList())
            val before = drawn(start, from = anchor, to = rangeEnd)
            val after = drawn(edit(before), from = anchor, to = rangeEnd)

            assertEquals("$rule from $anchor", daysWithTasks(before), daysWithTasks(after))
        }
    }

    /* ---------------- what the user already did ahead ---------------- */

    @Test
    fun aDayTickedAheadKeepsItsTick() {
        val before = drawn(State(listOf(template()), emptyList()))
        val ticked = before.tasks.first { it.date == LocalDate.of(2026, 10, 15) }
        val withTick = before.copy(tasks = before.tasks.map { if (it.id == ticked.id) it.copy(isDone = true) else it })

        val after = drawn(edit(withTick))
        val sameDay = shownOn(after, ticked.date!!).single()

        assertEquals(ticked.id, sameDay.id)
        assertTrue(sameDay.isDone)
        assertEquals(newName, sameDay.description)
    }

    @Test
    fun aDayRenamedByHandKeepsItsOwnName() {
        val before = drawn(State(listOf(template()), emptyList()))
        val renamed = before.tasks.first { it.date == LocalDate.of(2026, 10, 22) }
        val withRename = before.copy(
            tasks = before.tasks.map { if (it.id == renamed.id) it.copy(description = "перенесли на пятницу") else it }
        )

        val after = drawn(edit(withRename))

        assertEquals("перенесли на пятницу", shownOn(after, renamed.date!!).single().description)
    }

    @Test
    fun aDayDeletedAheadStaysDeleted() {
        val before = drawn(State(listOf(template()), emptyList()))
        val deletedDay = LocalDate.of(2026, 10, 29)
        val withDeletion = before.copy(
            tasks = before.tasks.filterNot { it.date == deletedDay },
            suppressed = setOf(taskSuppressionKey(1, deletedDay)),
        )

        val after = drawn(edit(withDeletion))

        assertTrue(shownOn(after, deletedDay).isEmpty())
    }

    /* ---------------- the new first day ---------------- */

    /** The occurrence already standing there is that day; the template hides behind it. */
    @Test
    fun anOccurrenceAlreadyOnTheNewFirstDayIsWhatTheDayShows() {
        val before = drawn(State(listOf(template()), emptyList()))
        val standing = before.tasks.first { it.date == nextThursday }

        val after = drawn(edit(before))

        assertEquals(standing.id, shownOn(after, nextThursday).single().id)
    }

    /** No occurrence there (the pruning dropped it): the new template is the day. */
    @Test
    fun withNothingOnTheNewFirstDayTheNewTemplateIsTheDay() {
        val before = drawn(State(listOf(template()), emptyList()), to = today)

        val after = drawn(edit(before))
        val shown = shownOn(after, nextThursday).single()

        assertNull(shown.originTaskId)
        assertEquals(newName, shown.description)
        assertEquals(nextThursday, shown.date)
    }

    /* ---------------- subtasks ---------------- */

    @Test
    fun subtasksFollowTheSeriesWithoutDoubling() {
        val sub = Subtask(id = 2, taskId = 1, description = "записать вопросы")
        val before = drawn(State(listOf(template()), listOf(sub)))

        val after = drawn(edit(before))

        daysWithTasks(after).forEach { day ->
            val task = shownOn(after, day).single()
            val subs = after.subtasks.filter { it.taskId == task.id }
            assertEquals("$day", listOf("записать вопросы"), subs.map { it.description })
        }
    }

    /* ---------------- after the edit, everything else still works ---------------- */

    /**
     * The pruning that runs on every save throws away what it can work out
     * again. Run it after the edit and draw the days again: they must come
     * back the same, or saving would rearrange them.
     */
    @Test
    fun pruningAndRedrawingAfterTheEditChangesNothingVisible() {
        val edited = drawn(edit(drawn(State(listOf(template()), emptyList()))))

        val pruned = pruneRedundantGeneratedOccurrences(
            tasks = edited.tasks,
            subtasks = edited.subtasks,
            suppressedRecurrences = edited.suppressed,
            runningPlanEntries = emptyList(),
            isPrunableDate = { !it.isBefore(today) },
            weekStart = weekStart,
        )
        val redrawn = drawn(State(pruned.tasks, pruned.subtasks, edited.suppressed))

        val look = { s: State ->
            daysWithTasks(s).associateWith { d -> shownOn(s, d).map { it.description to it.time } }
        }
        assertEquals(look(edited), look(redrawn))
    }

    /* ---------------- the simple cases ---------------- */

    /** Starting today or later, nothing is history: edited where it stands. */
    @Test
    fun aSeriesThatHasNotStartedYetIsEditedInPlace() {
        val before = drawn(State(listOf(template(date = nextThursday)), emptyList()), from = nextThursday)

        val after = edit(before)

        assertEquals(before.tasks.map { it.id }.sorted(), after.tasks.map { it.id }.sorted())
        assertTrue(after.tasks.all { it.description == newName && it.time == five })
    }

    @Test
    fun takingTheTimeAwayWorksTheSameWay() {
        val timed = drawn(edit(drawn(State(listOf(template()), emptyList()))))
        val newTemplateId = timed.tasks.first { it.originTaskId == null && it.repeatRule != null }.id

        val untimed = drawn(edit(timed, time = null, id = newTemplateId))

        assertTrue(daysWithTasks(untimed).flatMap { shownOn(untimed, it) }.all { it.time == null })
    }

    @Test
    fun aBlankNameOrSomethingThatIsNotASeriesChangesNothing() {
        val plain = Task(id = 9, date = today, description = "просто задача")
        val state = drawn(State(listOf(template(), plain), emptyList()))

        listOf(
            edit(state, description = "   "),
            edit(state, id = 9),
            edit(state, id = 42),
        ).forEach { assertSame(state.tasks, it.tasks) }
    }

    /* ---------------- a new rule ---------------- */

    private val fridays = thursdays.copy(weekDays = setOf(DayOfWeek.FRIDAY))
    private val tuesdaysAndThursdays = thursdays.copy(weekDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))

    /**
     * The days a brand-new series with [rule] would have from today: asked
     * of the generator with a fresh template on the rule's first day, not
     * worked out by hand.
     */
    private fun daysOfAFreshSeries(rule: RepeatRule): Set<LocalDate> {
        val start = firstDayOfShape(rule, today)!!
        val fresh = State(listOf(Task(id = 77, date = start, description = "x", repeatRule = rule)), emptyList())
        return daysWithTasks(drawn(fresh, from = start))
    }

    private fun afterRuleChange(rule: RepeatRule, prepare: (State) -> State = { it }): State =
        drawn(edit(prepare(drawn(State(listOf(template()), emptyList()))), rule = rule))

    @Test
    fun movedToFridaysTheSeriesIsOnFridaysFromTodayAndTheThursdaysAheadAreGone() {
        val after = afterRuleChange(fridays)

        assertEquals(daysOfAFreshSeries(fridays), daysWithTasks(after))
        assertTrue(daysWithTasks(after).none { it.dayOfWeek == DayOfWeek.THURSDAY })
        // Today is a Friday, and "from today" includes it.
        assertEquals(newName, shownOn(after, today).single().description)
    }

    @Test
    fun movedToFridaysThePastThursdaysStay() {
        val before = drawn(State(listOf(template()), emptyList()))
        val after = drawn(edit(before, rule = fridays))

        val past = { st: State -> st.tasks.filter { it.date!!.isBefore(today) }.map { it.id to it.description }.toSet() }
        assertEquals(past(before), past(after))
    }

    /** Twice a week: the Thursdays are kept — with their ticks — and Tuesdays join. */
    @Test
    fun twiceAWeekKeepsTheThursdaysAheadWithTheirTicks() {
        var tickedId = 0L
        val after = afterRuleChange(tuesdaysAndThursdays) { st ->
            val ticked = st.tasks.first { it.date == LocalDate.of(2026, 10, 15) }
            tickedId = ticked.id
            st.copy(tasks = st.tasks.map { if (it.id == ticked.id) it.copy(isDone = true) else it })
        }

        assertEquals(daysOfAFreshSeries(tuesdaysAndThursdays), daysWithTasks(after))
        val thursday = shownOn(after, LocalDate.of(2026, 10, 15)).single()
        assertEquals(tickedId, thursday.id)
        assertTrue(thursday.isDone)
        assertEquals(1, shownOn(after, LocalDate.of(2026, 10, 6)).size)
    }

    @Test
    fun everyOtherWeekDropsTheThursdaysInBetween() {
        val everyOther = thursdays.copy(interval = 2)
        val after = afterRuleChange(everyOther)

        assertEquals(daysOfAFreshSeries(everyOther), daysWithTasks(after))
        assertTrue(shownOn(after, LocalDate.of(2026, 10, 15)).isEmpty())
        assertEquals(1, shownOn(after, LocalDate.of(2026, 10, 22)).size)
    }

    @Test
    fun fromWeeklyToMonthly() {
        val monthly = RepeatRule(freq = RepeatFreq.MONTHLY, dayOfMonth = 15, weekStart = weekStart)
        val after = afterRuleChange(monthly)

        assertEquals(daysOfAFreshSeries(monthly), daysWithTasks(after))
        assertTrue(daysWithTasks(after).all { it.dayOfMonth == 15 })
    }

    /** The same rule handed back unchanged is no change at all. */
    @Test
    fun theSameRuleHandedBackKeepsEveryDayAndEveryId() {
        val before = drawn(State(listOf(template()), emptyList()))
        val viaNull = drawn(edit(before))
        val viaSame = drawn(edit(before, rule = thursdays))

        val look = { st: State -> daysWithTasks(st).associateWith { d -> shownOn(st, d).map { it.description } } }
        assertEquals(look(viaNull), look(viaSame))
        assertEquals(daysWithTasks(before), daysWithTasks(viaSame))
    }

    /** A series that has not started has no history: the template itself moves. */
    @Test
    fun aSeriesThatHasNotStartedMovesToTheNewRule() {
        val before = drawn(State(listOf(template(date = nextThursday)), emptyList()), from = nextThursday)
        val after = drawn(edit(before, rule = fridays), from = nextThursday)

        val t = after.tasks.first { it.id == 1L }
        assertEquals(LocalDate.of(2026, 10, 9), t.date)
        assertEquals(fridays.weekDays, t.repeatRule!!.weekDays)
        assertTrue(daysWithTasks(after).none { it.dayOfWeek == DayOfWeek.THURSDAY })
        daysWithTasks(after).forEach { assertEquals("$it", 1, shownOn(after, it).size) }
    }

    @Test
    fun pruningAndRedrawingAfterANewRuleChangesNothingVisible() {
        val edited = afterRuleChange(tuesdaysAndThursdays)

        val pruned = pruneRedundantGeneratedOccurrences(
            tasks = edited.tasks,
            subtasks = edited.subtasks,
            suppressedRecurrences = edited.suppressed,
            runningPlanEntries = emptyList(),
            isPrunableDate = { !it.isBefore(today) },
            weekStart = weekStart,
        )
        val redrawn = drawn(State(pruned.tasks, pruned.subtasks, edited.suppressed))

        val look = { st: State -> daysWithTasks(st).associateWith { d -> shownOn(st, d).map { it.description to it.time } } }
        assertEquals(look(edited), look(redrawn))
    }

    @Test
    fun theFirstDayOfAShape() {
        assertEquals(today, firstDayOfShape(RepeatRule(freq = RepeatFreq.DAILY, interval = 3), today))
        assertEquals(LocalDate.of(2026, 10, 6), firstDayOfShape(thursdays.copy(weekDays = setOf(DayOfWeek.TUESDAY)), today))
        assertEquals(LocalDate.of(2026, 10, 31), firstDayOfShape(RepeatRule(freq = RepeatFreq.MONTHLY, dayOfMonth = 31), today))
        assertEquals(LocalDate.of(2026, 12, 31), firstDayOfShape(RepeatRule(freq = RepeatFreq.MONTHLY, dayOfMonth = 31), LocalDate.of(2026, 11, 1)))
    }

    /**
     * The repeat picker hands back what it shows, without a week start.
     * Opening it and pressing OK must not count as a new rule.
     */
    @Test
    fun aRuleConfirmedUntouchedInThePickerIsTheSameRule() {
        // Every other week, because only an interval can tell the two paths
        // apart: taken as a new rule, the series would restart on the next
        // Thursday and keep the wrong half of the weeks.
        val everyOther = thursdays.copy(interval = 2)
        val asThePickerReturnsIt = RepeatRule(freq = RepeatFreq.WEEKLY, interval = 2, weekDays = setOf(DayOfWeek.THURSDAY))
        val before = drawn(State(listOf(template(rule = everyOther)), emptyList()))

        val viaNull = drawn(edit(before))
        val viaPicker = drawn(edit(before, rule = asThePickerReturnsIt))

        val look = { st: State -> daysWithTasks(st).associateWith { d -> shownOn(st, d).map { it.id } } }
        assertEquals(daysWithTasks(before), look(viaPicker).keys)
        assertEquals(look(viaNull), look(viaPicker))
    }
}
