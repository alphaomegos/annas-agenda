package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/** The home-screen widget: today's tasks in day order, ticked from outside the app. */
class TodayWidgetSupportTest {

    private val weekStart = DayOfWeek.MONDAY
    private val today = LocalDate.of(2026, 10, 7)

    private val plain = Task(id = 1, date = today, description = "Магазин", order = 0)
    private val timed = Task(id = 2, date = today, time = LocalTime.of(9, 0), description = "Зарядка", order = 1)
    private val tomorrow = Task(id = 3, date = today.plusDays(1), description = "завтра")
    private val daily = Task(
        id = 4, date = today.minusDays(3), description = "Витамины",
        repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = weekStart),
    )

    private val state = AppState(tasks = listOf(plain, timed, tomorrow, daily))

    @Test
    fun theWidgetShowsTodayInDayOrderRepeatsIncluded() {
        val rows = todayWidgetRows(state, today, weekStart)

        assertEquals(listOf("Зарядка", "Магазин", "Витамины"), rows.map { it.title })
        assertEquals(LocalTime.of(9, 0), rows.first().time)
    }

    @Test
    fun tickingAPlainTaskTicksIt() {
        val r = stateAfterTogglingTaskFromWidget(state, "1@${today.toEpochDay()}", today, weekStart, nextIdFor(state))!!

        assertTrue(r.state.tasks.single { it.id == 1L }.isDone)
        // And again takes it back.
        val back = stateAfterTogglingTaskFromWidget(r.state, "1@${today.toEpochDay()}", today, weekStart, r.nextId)!!
        assertFalse(back.state.tasks.single { it.id == 1L }.isDone)
    }

    /** The occurrence was never drawn: the tick draws it, with an id the counter will not hand out again. */
    @Test
    fun tickingAnUndrawnRepeatDrawsItAndRaisesTheCounter() {
        val key = "4@${today.toEpochDay()}"
        val r = stateAfterTogglingTaskFromWidget(state, key, today, weekStart, nextIdFor(state))!!

        val ticked = r.state.tasks.single { it.originTaskId == 4L && it.date == today }
        assertTrue(ticked.isDone)
        assertTrue(r.state.idHighWater > ticked.id)
        assertTrue(nextIdFor(r.state) > ticked.id)
        assertTrue(todayWidgetRows(r.state, today, weekStart).single { it.key == key }.isDone)
    }

    @Test
    fun aLinkedCounterMovesWithTheTickAsInTheApp() {
        val counter = ManualCounter(id = 50, title = "отжимания", balance = 10)
        val linked = plain.copy(linkedManualCounterId = 50)
        val s = AppState(tasks = listOf(linked), counters = listOf(counter))

        val r = stateAfterTogglingTaskFromWidget(s, "1@${today.toEpochDay()}", today, weekStart, nextIdFor(s))!!

        assertEquals(9, (r.state.counters.single() as ManualCounter).balance)
    }

    @Test
    fun aKeyThatNamesNothingTodayChangesNothing() {
        assertNull(stateAfterTogglingTaskFromWidget(state, "3@${today.plusDays(1).toEpochDay()}", today, weekStart, nextIdFor(state)))
        assertNull(stateAfterTogglingTaskFromWidget(state, "999@${today.toEpochDay()}", today, weekStart, nextIdFor(state)))
    }

    @Test
    fun aRepeatDeletedForTodayIsNeitherShownNorTickable() {
        val s = state.copy(suppressedRecurrences = setOf(taskSuppressionKey(4, today)))

        assertTrue(todayWidgetRows(s, today, weekStart).none { it.title == "Витамины" })
        assertNull(stateAfterTogglingTaskFromWidget(s, "4@${today.toEpochDay()}", today, weekStart, nextIdFor(s)))
    }
}

/** The header and the look, asked for on 03.10. */
class TodayWidgetLookTest {

    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun theHeaderNamesTheDayAndMonthInTheFormThatFollowsANumber() {
        assertEquals("3 октября", formatWidgetDay(day, java.util.Locale.forLanguageTag("ru")))
        assertEquals("3 October", formatWidgetDay(day, java.util.Locale.ENGLISH))
    }

    @Test
    fun theDefaultLookIsTheOneTheWidgetHadAndPercentagesStayPercentages() {
        assertEquals(WidgetStyle(100, WidgetTextColor.SYSTEM, null), AppState().widgetStyle)
        assertEquals(0, normalizedWidgetStyle(WidgetStyle(backgroundPercent = -20)).backgroundPercent)
        assertEquals(100, normalizedWidgetStyle(WidgetStyle(backgroundPercent = 140)).backgroundPercent)
    }
}
