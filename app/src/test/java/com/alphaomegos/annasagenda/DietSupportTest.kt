package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The diet: a plan per day of the week, ticked into the ordinary food log.
 *
 * Agreed 02.10: today and past days can be ticked; a past day's unticked
 * dish is shown greyed or not at all, by a setting; nothing happens at
 * midnight, because the day is worked out every time it is shown.
 */
class DietSupportTest {

    private val today = LocalDate.of(2026, 10, 7) // a Wednesday
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private val porridge = DietItem(id = 10, title = "Овсянка, 200 г", kcal = 180)
    private val curd = DietItem(id = 11, title = "Творог 5 %, 200 г", kcal = 242)
    private val tuesdayTea = DietItem(id = 12, title = "Чай", kcal = 0)

    private val plan = mapOf(
        DayOfWeek.WEDNESDAY to listOf(porridge, curd),
        DayOfWeek.THURSDAY to listOf(porridge.copy(id = 20), curd.copy(id = 21)),
        DayOfWeek.TUESDAY to listOf(tuesdayTea),
    )

    private var cursor = 1_000L
    private fun newId() = cursor++

    private fun dishes(date: LocalDate, log: List<FoodEntry> = emptyList(), showPast: Boolean = true) =
        dietDishesOn(date, today, plan, log, showPast)

    /* ---------------- what a day shows ---------------- */

    @Test
    fun aDayShowsThePlanForItsDayOfTheWeekInOrder() {
        assertEquals(listOf(10L, 11L), dishes(today).map { it.item.id })
        assertEquals(listOf(12L), dishes(yesterday).map { it.item.id })
        assertTrue(dishes(today.plusDays(4)).isEmpty()) // Sunday: no plan
    }

    @Test
    fun tickingWritesAnOrdinaryEntryCarryingTheDish() {
        val log = foodLogAfterTickingDish(emptyList(), porridge, today, today, ::newId)

        val entry = log.single()
        assertEquals(porridge.title, entry.title)
        assertEquals(porridge.kcal, entry.kcal)
        assertEquals(porridge.id, entry.dietItemId)
        assertEquals(today, entry.date)
        assertEquals(entry, dishes(today, log).first().eaten)
    }

    @Test
    fun aTickedDishIsNotListedAgainWithTheRestOfTheFood() {
        val typed = FoodEntry(id = 1, date = today, title = "Яблоко", kcal = 50)
        val log = foodLogAfterTickingDish(listOf(typed), porridge, today, today, ::newId)

        assertEquals(listOf(typed), foodOutsideDiet(today, log, dishes(today, log)))
    }

    @Test
    fun tickingTwiceWritesOnce() {
        val once = foodLogAfterTickingDish(emptyList(), porridge, today, today, ::newId)

        assertSame(once, foodLogAfterTickingDish(once, porridge, today, today, ::newId))
    }

    @Test
    fun untickingTakesBackOnlyThatDaysTick() {
        val log = listOf(
            FoodEntry(id = 1, date = today, title = porridge.title, kcal = 180, dietItemId = 10),
            FoodEntry(id = 2, date = today.minusDays(7), title = porridge.title, kcal = 180, dietItemId = 10),
            FoodEntry(id = 3, date = today, title = "Яблоко", kcal = 50),
        )

        assertEquals(listOf(2L, 3L), foodLogAfterUntickingDish(log, 10, today).map { it.id })
        assertSame(log, foodLogAfterUntickingDish(log, 99, today))
    }

    /* ---------------- today, the past, the future ---------------- */

    @Test
    fun aDayAheadShowsThePlanButCannotBeTicked() {
        assertTrue(dishes(tomorrow).none { it.canTick })
        assertTrue(dishes(tomorrow).none { it.faded })
        val log = emptyList<FoodEntry>()
        assertSame(log, foodLogAfterTickingDish(log, porridge.copy(id = 20), tomorrow, today, ::newId))
    }

    @Test
    fun todayIsTickableAndNotFaded() {
        assertTrue(dishes(today).all { it.canTick && !it.faded })
    }

    @Test
    fun aPastDaysUntickedDishIsFadedAndStillTickable() {
        val shown = dishes(yesterday).single()

        assertTrue(shown.faded)
        assertTrue(shown.canTick)
        assertEquals(1, foodLogAfterTickingDish(emptyList(), tuesdayTea, yesterday, today, ::newId).size)
    }

    @Test
    fun withTheSettingOffAPastDayShowsOnlyWhatWasTicked() {
        val lastWednesday = today.minusDays(7)
        val log = listOf(FoodEntry(id = 1, date = lastWednesday, title = curd.title, kcal = 242, dietItemId = 11))

        val shown = dishes(lastWednesday, log, showPast = false)

        assertEquals(listOf(11L), shown.map { it.item.id })
        assertFalse(shown.single().faded)
        // Today is not "past": nothing disappears from it.
        assertEquals(2, dishes(today, showPast = false).size)
    }

    /** No timer, so nothing to miss: the same call gives yesterday's answer tomorrow. */
    @Test
    fun theSameDayLooksTheSameWhicheverDayItIsAskedOn() {
        val past = dietDishesOn(yesterday, today, plan, emptyList(), showPastUnticked = false)
        val later = dietDishesOn(yesterday, today.plusDays(30), plan, emptyList(), showPastUnticked = false)

        assertTrue(past.isEmpty())
        assertEquals(past, later)
    }

    /* ---------------- the plan changes, the past does not ---------------- */

    @Test
    fun aDishTakenOutOfTheDietStaysEatenWhereItWasTicked() {
        val log = foodLogAfterTickingDish(emptyList(), curd, today, today, ::newId)
        val newPlan = dietPlanAfterRemoving(plan, curd.id)

        val shown = dietDishesOn(today, today, newPlan, log, showPastUnticked = true)

        assertEquals(listOf(10L), shown.map { it.item.id })
        assertEquals(log, foodOutsideDiet(today, log, shown))
    }

    @Test
    fun editingADishLeavesWhatWasAlreadyEatenAsItWas() {
        val log = foodLogAfterTickingDish(emptyList(), curd, today, today, ::newId)
        val newPlan = dietPlanAfterEditing(plan, curd.id, "Творог 9 %, 200 г", 318)

        assertEquals("Творог 9 %, 200 г", newPlan.getValue(DayOfWeek.WEDNESDAY)[1].title)
        assertEquals(242, log.single().kcal)
        assertEquals(curd.title, log.single().title)
    }

    /* ---------------- editing the plan ---------------- */

    @Test
    fun addingPutsTheDishAtTheEndOfItsDay() {
        val after = dietPlanAfterAdding(plan, DayOfWeek.WEDNESDAY, "  Кефир, 250 мл ", 100, ::newId)

        assertEquals(listOf("Овсянка, 200 г", "Творог 5 %, 200 г", "Кефир, 250 мл"), after.getValue(DayOfWeek.WEDNESDAY).map { it.title })
        assertSame(plan, dietPlanAfterAdding(plan, DayOfWeek.WEDNESDAY, "   ", 100, ::newId))
    }

    @Test
    fun anEmptiedDayLeavesThePlan() {
        val after = dietPlanAfterRemoving(plan, tuesdayTea.id)

        assertFalse(DayOfWeek.TUESDAY in after)
        assertSame(plan, dietPlanAfterRemoving(plan, 999))
    }

    @Test
    fun movingStaysWithinTheDayAndTheEndsStay() {
        val after = dietPlanAfterMoving(plan, curd.id, -1)

        assertEquals(listOf(11L, 10L), after.getValue(DayOfWeek.WEDNESDAY).map { it.id })
        assertSame(plan, dietPlanAfterMoving(plan, porridge.id, -1))
        assertSame(plan, dietPlanAfterMoving(plan, curd.id, 1))
    }

    /** Ticking Monday's porridge must not tick Tuesday's: copies have their own ids. */
    @Test
    fun copyingADayGivesEveryCopyItsOwnIdAndReplacesTheTarget() {
        val after = dietPlanAfterCopyingDay(plan, DayOfWeek.WEDNESDAY, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY), ::newId)

        val titles = { d: DayOfWeek -> after.getValue(d).map { it.title } }
        assertEquals(titles(DayOfWeek.WEDNESDAY), titles(DayOfWeek.TUESDAY))
        assertEquals(titles(DayOfWeek.WEDNESDAY), titles(DayOfWeek.FRIDAY))
        // The source is untouched, the old Tuesday tea is gone.
        assertEquals(plan.getValue(DayOfWeek.WEDNESDAY), after.getValue(DayOfWeek.WEDNESDAY))
        val ids = after.values.flatten().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun copyingAnEmptyDayClearsTheTargets() {
        val after = dietPlanAfterCopyingDay(plan, DayOfWeek.SUNDAY, setOf(DayOfWeek.TUESDAY), ::newId)

        assertNull(after[DayOfWeek.TUESDAY])
        assertSame(plan, dietPlanAfterCopyingDay(plan, DayOfWeek.TUESDAY, setOf(DayOfWeek.TUESDAY), ::newId))
    }

    @Test
    fun aDaysPlanAddsUp() {
        assertEquals(422, dietKcalFor(plan, DayOfWeek.WEDNESDAY))
        assertEquals(0, dietKcalFor(plan, DayOfWeek.SUNDAY))
    }

    /** Everything the diet hands out comes from the one counter and is counted. */
    @Test
    fun theDishIdsAreCountedByTheIdCounter() {
        val state = AppState(dietPlan = dietPlanAfterAdding(emptyMap(), DayOfWeek.MONDAY, "Суп", 300) { 5_000L })

        assertTrue(nextIdFor(state) > 5_000L)
    }
}
