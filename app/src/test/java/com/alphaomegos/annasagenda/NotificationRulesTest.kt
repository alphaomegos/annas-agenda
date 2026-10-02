package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Daily summaries and task reminders, as agreed on 02.10: the user picks
 * the times and what the summary lists; one reminder lead for every timed
 * task; the default is silence.
 */
class NotificationRulesTest {

    private val weekStart = DayOfWeek.MONDAY
    private val today = LocalDate.of(2026, 10, 7)
    private val morning = today.atTime(7, 30)

    private fun at(h: Int, m: Int = 0, day: LocalDate = today): LocalDateTime = day.atTime(h, m)

    private fun settings(
        minutes: List<Int> = emptyList(),
        todayWhat: SummaryToday = SummaryToday.UNDONE,
        debts: Boolean = true,
        lead: Int? = null,
    ) = NotificationSettings(minutes, todayWhat, debts, lead)

    private val call = Task(id = 1, date = today, time = LocalTime.of(17, 0), description = "Созвон", order = 0)
    private val shop = Task(id = 2, date = today, description = "Магазин", order = 1)
    private val doneThing = Task(id = 3, date = today, time = LocalTime.of(9, 0), description = "Зарядка", isDone = true, order = 2)

    /* ---------------- silence by default ---------------- */

    @Test
    fun aFreshStateNeverSetsAnAlarm() {
        val state = AppState(tasks = listOf(call, shop))

        assertNull(nextNotificationAt(state, morning, weekStart))
        assertTrue(upcomingNotificationEvents(state, morning, weekStart).isEmpty())
    }

    /* ---------------- summaries ---------------- */

    @Test
    fun summariesComeAtTheChosenTimesTodayAndTheDaysAfter() {
        val events = summaryEventsAfter(morning, listOf(13 * 60, 8 * 60, 20 * 60))

        assertEquals(listOf(at(8), at(13), at(20), at(8, day = today.plusDays(1))), events.take(4).map { it.at })
    }

    /** Strictly after: the summary that has just fired is not set again. */
    @Test
    fun theMinuteJustFiredIsNotTheNextAlarm() {
        val state = AppState(notifications = settings(minutes = listOf(8 * 60, 13 * 60)))

        assertEquals(at(13), nextNotificationAt(state, at(8), weekStart))
        assertEquals(at(8, day = today.plusDays(1)), nextNotificationAt(state, at(13), weekStart))
    }

    @Test
    fun undoneOnlyListsWhatIsLeftInDayOrder() {
        val state = AppState(tasks = listOf(shop, call, doneThing), notifications = settings())

        val summary = dailySummaryOf(state, today, weekStart)!!

        assertEquals(listOf("Созвон", "Магазин"), summary.today.map { it.title })
        assertEquals(LocalTime.of(17, 0), summary.today.first().time)
    }

    @Test
    fun allListsTheDoneOnesTooMarkedDone() {
        val state = AppState(tasks = listOf(shop, call, doneThing), notifications = settings(todayWhat = SummaryToday.ALL))

        val summary = dailySummaryOf(state, today, weekStart)!!

        assertEquals(listOf("Зарядка", "Созвон", "Магазин"), summary.today.map { it.title })
        assertTrue(summary.today.first().isDone)
    }

    @Test
    fun debtsAreCountedByTheUndoneScreensRules() {
        val old = Task(id = 9, date = today.minusDays(3), description = "долг")
        val tooOld = Task(id = 10, date = today.minusDays(60), description = "давно")
        val state = AppState(
            tasks = listOf(old, tooOld),
            undoneHorizonDays = 30,
            notifications = settings(todayWhat = SummaryToday.NONE),
        )

        assertEquals(1, dailySummaryOf(state, today, weekStart)!!.debts)
    }

    /** A repeat missed yesterday is a debt even though no screen drew it yet. */
    @Test
    fun aMissedRepeatIsADebtWithoutHavingBeenDrawn() {
        val daily = Task(
            id = 20, date = today.minusDays(5), description = "витамины",
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart),
            isDone = true,
        )
        val state = AppState(tasks = listOf(daily), notifications = settings(todayWhat = SummaryToday.NONE))

        // The template's own day was done; the four days after it were not.
        assertEquals(4, dailySummaryOf(state, today, weekStart)!!.debts)
    }

    @Test
    fun debtsSwitchedOffAreNotCounted() {
        val state = AppState(
            tasks = listOf(Task(id = 9, date = today.minusDays(3), description = "долг"), shop),
            notifications = settings(debts = false),
        )

        assertEquals(0, dailySummaryOf(state, today, weekStart)!!.debts)
    }

    /** A summary with nothing to say is not shown at all. */
    @Test
    fun nothingToSayIsSilence() {
        val state = AppState(tasks = listOf(doneThing), notifications = settings())

        assertNull(dailySummaryOf(state, today, weekStart))
        assertNull(dailySummaryOf(AppState(notifications = settings(todayWhat = SummaryToday.NONE, debts = false), tasks = listOf(shop)), today, weekStart))
    }

    /* ---------------- reminders ---------------- */

    @Test
    fun aReminderComesTheChosenMinutesBeforeTheTask() {
        val state = AppState(tasks = listOf(call, shop, doneThing), notifications = settings(lead = 15))

        val events = upcomingNotificationEvents(state, morning, weekStart).filterIsInstance<ReminderEvent>()

        // Only the timed task that is not done.
        assertEquals(listOf(at(16, 45)), events.map { it.at })
        assertEquals("Созвон", events.single().title)
    }

    @Test
    fun atTheTimeMeansAtTheTime() {
        val state = AppState(tasks = listOf(call), notifications = settings(lead = 0))

        assertEquals(at(17), nextNotificationAt(state, morning, weekStart))
    }

    @Test
    fun aReminderWhoseMomentHasGoneIsNotSentLate() {
        val state = AppState(tasks = listOf(call), notifications = settings(lead = 15))

        assertNull(nextNotificationAt(state, at(16, 50), weekStart))
    }

    /** An hour's lead puts tomorrow's 00:30 reminder tonight. */
    @Test
    fun aLeadCanReachBackIntoTheDayBefore() {
        val late = Task(id = 5, date = today.plusDays(1), time = LocalTime.of(0, 30), description = "ночной рейс")
        val state = AppState(tasks = listOf(late), notifications = settings(lead = 60))

        assertEquals(at(23, 30), nextNotificationAt(state, at(22), weekStart))
    }

    /** Repeats are drawn for the reminder, though the state on disk has them pruned. */
    @Test
    fun aRepeatsTimeIsRemindedOnEveryDay() {
        val pills = Task(
            id = 30, date = today.minusDays(10), time = LocalTime.of(21, 0), description = "таблетки",
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart),
        )
        val state = AppState(tasks = listOf(pills), notifications = settings(lead = 5))

        val reminders = upcomingNotificationEvents(state, morning, weekStart).filterIsInstance<ReminderEvent>()

        assertEquals(listOf(at(20, 55), at(20, 55, today.plusDays(1))), reminders.map { it.at })
        assertEquals("30@${today.toEpochDay()}", reminders.first().taskKey)
    }

    @Test
    fun aRepeatDeletedForADayIsNotRemindedThatDay() {
        val pills = Task(
            id = 30, date = today.minusDays(10), time = LocalTime.of(21, 0), description = "таблетки",
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart),
        )
        val state = AppState(
            tasks = listOf(pills),
            suppressedRecurrences = setOf(taskSuppressionKey(30, today)),
            notifications = settings(lead = 5),
        )

        assertEquals(at(20, 55, today.plusDays(1)), nextNotificationAt(state, morning, weekStart))
    }

    /* ---------------- when the alarm fires ---------------- */

    @Test
    fun aSummaryAndAReminderOnOneMinuteBothFireSummaryFirst() {
        val state = AppState(tasks = listOf(call), notifications = settings(minutes = listOf(17 * 60), lead = 0))

        val due = notificationEventsAt(state, at(17), weekStart)

        assertEquals(2, due.size)
        assertTrue(due.first() is SummaryEvent)
    }

    @Test
    fun aReminderTickedOrMovedSinceIsDropped() {
        val state = AppState(tasks = listOf(call), notifications = settings(lead = 15))
        val event = notificationEventsAt(state, at(16, 45), weekStart).single() as ReminderEvent

        assertTrue(reminderStillDue(state, event, weekStart))
        assertFalse(reminderStillDue(state.copy(tasks = listOf(call.copy(isDone = true))), event, weekStart))
        assertFalse(reminderStillDue(state.copy(tasks = listOf(call.copy(time = LocalTime.of(18, 0)))), event, weekStart))
        assertFalse(reminderStillDue(state.copy(notifications = settings()), event, weekStart))
    }

    @Test
    fun nothingIsLookedForBeyondTheLookahead() {
        val far = Task(id = 7, date = today.plusDays(5), time = LocalTime.of(12, 0), description = "далеко")
        val state = AppState(tasks = listOf(far), notifications = settings(lead = 0))

        assertNull(nextNotificationAt(state, morning, weekStart))
    }
}
