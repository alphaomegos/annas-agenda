package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 6.3: which marks a calendar day carries, of those switched on. */
class CalendarBadgeSupportTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val d1 = today.minusDays(3)
    private val d2 = today.minusDays(2)
    private val d3 = today.minusDays(1)

    private val anthropometry = listOf(
        AnthropometryEntry(date = d1, waistCm = 70.0),
        AnthropometryEntry(date = d2), // nothing filled in: no mark
    )
    private val food = listOf(FoodEntry(id = 1, date = d2, title = "Суп", kcal = 300))
    private val tasks = listOf(
        Task(id = 10, date = d3, description = "не сделано"),
        Task(id = 11, date = d1, description = "сделано", isDone = true),
        Task(id = 12, date = today, description = "сегодня — ещё не долг"),
    )

    private fun badges(enabled: Set<CalendarBadge>, suppressed: Set<String> = emptySet()) =
        calendarBadgesByDate(enabled, anthropometry, food, tasks, suppressed, today)

    @Test
    fun theDefaultIsTheCircleTheCalendarAlwaysDrew() {
        assertEquals(mapOf(d1 to setOf(CalendarBadge.ANTHROPOMETRY)), badges(AppState().calendarBadges))
    }

    @Test
    fun everyMarkOnShowsEachOnItsOwnDay() {
        val all = badges(CalendarBadge.entries.toSet())

        assertEquals(setOf(CalendarBadge.ANTHROPOMETRY), all[d1])
        assertEquals(setOf(CalendarBadge.FOOD), all[d2])
        assertEquals(setOf(CalendarBadge.DEBTS), all[d3])
        // Today's undone task is not a debt yet.
        assertEquals(null, all[today])
    }

    @Test
    fun aMarkSwitchedOffIsNowhere() {
        val noFood = badges(setOf(CalendarBadge.ANTHROPOMETRY, CalendarBadge.DEBTS))

        assertTrue(noFood.values.none { CalendarBadge.FOOD in it })
        assertTrue(badges(emptySet()).isEmpty())
    }

    /** One definition of a debt: a repeat deleted for its own day is not one. */
    @Test
    fun aDebtFollowsTheUndoneScreensRules() {
        val template = Task(
            id = 20, date = d2, description = "повтор",
            repeatRule = RepeatRule(freq = RepeatFreq.DAILY, interval = 1, weekStart = java.time.DayOfWeek.MONDAY),
        )
        val withTemplate = tasks + template

        val shown = calendarBadgesByDate(setOf(CalendarBadge.DEBTS), emptyList(), emptyList(), withTemplate, setOf(taskSuppressionKey(20, d2)), today)

        assertEquals(setOf(d3), shown.keys)
    }

    /** A debt older than the Undone screen's horizon is still marked on its day. */
    @Test
    fun theCalendarHasNoHorizon() {
        val old = today.minusDays(400)
        val shown = calendarBadgesByDate(
            setOf(CalendarBadge.DEBTS), emptyList(), emptyList(),
            listOf(Task(id = 30, date = old, description = "давно")), emptySet(), today,
        )

        assertEquals(setOf(old), shown.keys)
    }
}
