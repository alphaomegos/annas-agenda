package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Offering back a meal that has been eaten before, with its portion and what
 * it was worth.
 *
 * The rule that makes the list short enough to be useful is the grouping: the
 * same food at three different weights is one food eaten three times, not
 * three foods. And the rule that makes the arithmetic honest is that the
 * portion and the kilocalories always come from the **same** entry — taking
 * one from here and the other from there produces a rate that was never true.
 */
class FoodSuggestionsTest {

    private var nextId = 1L
    private val march = LocalDate.of(2026, 3, 1)

    private fun ate(title: String, kcal: Int, date: LocalDate = march) =
        FoodEntry(id = nextId++, date = date, title = title, kcal = kcal)

    private fun suggest(typed: String, log: List<FoodEntry>, limit: Int = 5) =
        foodSuggestionsFor(typed, log, limit)

    /* ---------------- when to say nothing ---------------- */

    @Test
    fun oneLetterSuggestsNothing() {
        val log = listOf(ate("Помидоры, 500 г", 90))

        assertTrue(suggest("П", log).isEmpty())
        assertTrue(suggest(" ", log).isEmpty())
    }

    @Test
    fun aFoodNeverEatenSuggestsNothing() {
        assertTrue(suggest("баклажан", listOf(ate("Помидоры, 500 г", 90))).isEmpty())
    }

    @Test
    fun anEmptyLogSuggestsNothing() {
        assertTrue(suggest("помид", emptyList()).isEmpty())
    }

    /* ---------------- the grouping ---------------- */

    /**
     * The one that keeps the list as short as the kitchen is.
     */
    @Test
    fun theSameFoodAtThreeWeightsIsOneFoodEatenThreeTimes() {
        val log = listOf(
            ate("Помидоры, 500 г", 90),
            ate("Помидоры, 300 г", 54),
            ate("Помидоры, 200 г", 36),
        )

        val out = suggest("помид", log)

        assertEquals(1, out.size)
        assertEquals(3, out.single().timesEaten)
        assertEquals("Помидоры", out.single().name)
    }

    @Test
    fun differentFoodsStayDifferent() {
        val log = listOf(ate("Помидоры, 500 г", 90), ate("Помидоры черри, 100 г", 25))

        assertEquals(2, suggest("помид", log).size)
    }

    /**
     * Matched on the name without the portion. Typing a number should not
     * offer every meal that happened to weigh that much.
     */
    @Test
    fun theWeightIsNotPartOfWhatIsSearched() {
        val log = listOf(ate("Помидоры, 500 г", 90), ate("Огурцы, 500 г", 75))

        assertTrue(suggest("500", log).isEmpty())
    }

    /* ---------------- the portion and its price ---------------- */

    /**
     * Both from the same entry, always. A portion from one meal priced by
     * another is a rate that was never true.
     */
    @Test
    fun theOfferedPortionAndItsCaloriesComeFromTheSameMeal() {
        val log = listOf(
            ate("Помидоры, 500 г", 90, date = LocalDate.of(2026, 1, 1)),
            ate("Помидоры, 300 г", 54, date = LocalDate.of(2026, 5, 5)),
        )

        val out = suggest("помид", log).single()

        assertEquals(300, out.amount)
        assertEquals(54, out.kcal)
        assertEquals("г", out.unit)
    }

    @Test
    fun onTheSameDayTheOneWrittenLastWins() {
        val log = listOf(
            ate("Помидоры, 500 г", 90),
            ate("Помидоры, 300 г", 54),
        )

        val out = suggest("помид", log).single()

        assertEquals(300, out.amount)
        assertEquals(54, out.kcal)
    }

    @Test
    fun aFoodNeverWrittenWithAPortionHasNone() {
        val out = suggest("борщ", listOf(ate("Борщ", 320))).single()

        assertEquals("Борщ", out.name)
        assertNull(out.amount)
        assertEquals(320, out.kcal)
    }

    @Test
    fun theDayItWasLastEatenIsCarried() {
        val log = listOf(
            ate("Помидоры, 500 г", 90, date = LocalDate.of(2026, 1, 1)),
            ate("Помидоры, 300 г", 54, date = LocalDate.of(2026, 5, 5)),
        )

        assertEquals(LocalDate.of(2026, 5, 5), suggest("помид", log).single().lastEatenOn)
    }

    /* ---------------- ordering ---------------- */

    @Test
    fun aNameThatStartsWithWhatIsTypedComesFirst() {
        val log = listOf(
            ate("Салат с помидорами, 200 г", 120),
            ate("Салат с помидорами, 200 г", 120),
            ate("Помидоры, 500 г", 90),
        )

        assertEquals(
            listOf("Помидоры", "Салат с помидорами"),
            suggest("помид", log).map { it.name },
        )
    }

    @Test
    fun theMoreOftenEatenComesFirst() {
        val log = listOf(
            ate("Помидоры черри, 100 г", 25),
            ate("Помидоры, 500 г", 90),
            ate("Помидоры, 300 г", 54),
        )

        assertEquals(
            listOf("Помидоры", "Помидоры черри"),
            suggest("помид", log).map { it.name },
        )
    }

    @Test
    fun aCompleteTieIsBrokenAlphabeticallyAndStaysThatWay() {
        val log = listOf(ate("Помидоры, 500 г", 90), ate("Помело, 200 г", 76))

        val once = suggest("пом", log).map { it.name }
        val again = suggest("пом", log.reversed()).map { it.name }

        assertEquals(listOf("Помело", "Помидоры"), once)
        assertEquals(once, again)
    }

    @Test
    fun theListIsCutToTheLimit() {
        val log = (1..20).map { ate("Салат $it, 100 г", 50) }

        assertEquals(5, suggest("салат", log).size)
        assertEquals(2, suggest("салат", log, limit = 2).size)
    }

    @Test
    fun caseDoesNotMatter() {
        val log = listOf(ate("Помидоры, 500 г", 90))

        assertEquals(1, suggest("ПОМИД", log).size)
    }

    /* ---------------- pricing a different portion ---------------- */

    private val tomatoes = FoodSuggestion(
        name = "Помидоры",
        amount = 500,
        unit = "г",
        kcal = 90,
        timesEaten = 1,
        lastEatenOn = march,
    )

    @Test
    fun halfThePortionIsHalfTheCalories() {
        assertEquals(45, kcalForAmount(tomatoes, 250))
    }

    @Test
    fun theSamePortionIsTheSameCalories() {
        assertEquals(90, kcalForAmount(tomatoes, 500))
    }

    @Test
    fun aBiggerPortionCostsMore() {
        assertEquals(180, kcalForAmount(tomatoes, 1000))
    }

    @Test
    fun theResultIsRoundedToAWholeKilocalorie() {
        assertEquals(18, kcalForAmount(tomatoes, 100))
        assertEquals(6, kcalForAmount(tomatoes, 33))
    }

    /**
     * A food whose portion was never written down has no rate, and inventing
     * one would be worse than leaving the number for the user to type.
     */
    @Test
    fun aFoodWithNoKnownPortionCannotBePriced() {
        assertNull(kcalForAmount(tomatoes.copy(amount = null), 250))
    }

    @Test
    fun nothingIsPricedFromOrIntoAPortionOfNothing() {
        assertNull(kcalForAmount(tomatoes.copy(amount = 0), 250))
        assertNull(kcalForAmount(tomatoes, 0))
        assertNull(kcalForAmount(tomatoes, -100))
    }

    /**
     * Not clamped. A ten-kilo portion is a typo, and thirty thousand
     * kilocalories on the day's total say so; a number quietly held down to
     * something plausible does not.
     */
    @Test
    fun anAbsurdPortionIsPricedAbsurdly() {
        assertEquals(1800, kcalForAmount(tomatoes, 10_000))
    }

    @Test
    fun aFoodWithNoCaloriesStaysFree() {
        assertEquals(0, kcalForAmount(tomatoes.copy(kcal = 0), 250))
    }

    /* ---------------- finding one again by name ---------------- */

    /**
     * The dialog remembers "I am pricing this meal" as a name rather than as
     * the suggestion, because a name survives the phone being turned. This is
     * the other half of that: getting the suggestion back.
     */
    @Test
    fun aFoodCanBeFoundAgainByItsExactName() {
        val log = listOf(ate("Помидоры, 500 г", 90), ate("Помидоры черри, 100 г", 25))

        val found = foodSuggestionForName("Помидоры", log)!!

        assertEquals("Помидоры", found.name)
        assertEquals(500, found.amount)
        assertEquals(90, found.kcal)
    }

    @Test
    fun findingOneAgainIgnoresCase() {
        val log = listOf(ate("Помидоры, 500 г", 90))

        assertEquals("Помидоры", foodSuggestionForName("ПОМИДОРЫ", log)?.name)
    }

    /**
     * Exact, not "contains". Looking up "Помидоры" must not come back with
     * cherry tomatoes and price the meal from the wrong food.
     */
    @Test
    fun findingOneAgainDoesNotSettleForASimilarName() {
        val log = listOf(ate("Помидоры черри, 100 г", 25))

        assertNull(foodSuggestionForName("Помидоры", log))
    }

    @Test
    fun aFoodNeverEatenIsNotFound() {
        assertNull(foodSuggestionForName("Борщ", listOf(ate("Помидоры, 500 г", 90))))
    }
}
