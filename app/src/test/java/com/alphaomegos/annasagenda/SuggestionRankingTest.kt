package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * The one ordering behind both suggestion lists.
 *
 * Each rule is checked against the rule below it, so that the order of the
 * four is what is being tested, not only their presence: "starts with" has
 * to beat a higher count, a higher count has to beat a fresher date, and so
 * on. The last two tests are the reason this file exists — tasks and meals
 * used to carry their own copy of the rule, and nothing said the copies agreed.
 */
class SuggestionRankingTest {

    private data class Item(val text: String, val times: Int, val last: LocalDate?)

    private val march = LocalDate.of(2026, 3, 1)

    private fun ranked(needle: String, vararg items: Item): List<String> =
        items.sortedWith(
            suggestionOrder(needle, text = { it.text }, timesUsed = { it.times }, lastUsedOn = { it.last })
        ).map { it.text }

    /* ---------------- the needle ---------------- */

    @Test
    fun tooShortIsNoNeedle() {
        assertNull(suggestionNeedle(""))
        assertNull(suggestionNeedle("П"))
        // Surrounding space does not count towards the length.
        assertNull(suggestionNeedle("  п  "))
    }

    @Test
    fun theNeedleIsTrimmedAndLowercase() {
        assertEquals("поч", suggestionNeedle("  Поч "))
        assertEquals("по", suggestionNeedle("ПО"))
    }

    /* ---------------- the four rules, each against the next ---------------- */

    @Test
    fun startingWithBeatsBeingUsedMoreOften() {
        assertEquals(
            listOf("Починить колесо", "Не забыть починить колесо"),
            ranked(
                "поч",
                Item("Не забыть починить колесо", times = 9, last = march),
                Item("Починить колесо", times = 1, last = march),
            ),
        )
    }

    @Test
    fun startingWithIgnoresCase() {
        assertEquals(
            listOf("ПОЧТА", "Сходить на почту"),
            ranked(
                "поч",
                Item("Сходить на почту", times = 5, last = march),
                Item("ПОЧТА", times = 1, last = march),
            ),
        )
    }

    @Test
    fun usedMoreOftenBeatsUsedMoreRecently() {
        assertEquals(
            listOf("Борщ", "Бобы"),
            ranked(
                "б",
                Item("Бобы", times = 1, last = march.plusDays(10)),
                Item("Борщ", times = 3, last = march),
            ),
        )
    }

    @Test
    fun usedMoreRecentlyBeatsTheAlphabet() {
        assertEquals(
            listOf("Яблоко", "Абрикос"),
            ranked(
                "о",
                Item("Абрикос", times = 2, last = march),
                Item("Яблоко", times = 2, last = march.plusDays(1)),
            ),
        )
    }

    @Test
    fun noDateIsOlderThanAnyDate() {
        assertEquals(
            listOf("Когда-то давно", "Когда-нибудь"),
            ranked(
                "ко",
                Item("Когда-нибудь", times = 1, last = null),
                Item("Когда-то давно", times = 1, last = LocalDate.of(1990, 1, 1)),
            ),
        )
    }

    @Test
    fun theAlphabetSettlesTheRestIgnoringCase() {
        // Without the last rule these two would keep whatever order the input
        // had, and the input order comes from a map.
        val a = Item("абв", times = 1, last = march)
        val b = Item("Абг", times = 1, last = march)

        assertEquals(listOf("абв", "Абг"), ranked("аб", b, a))
        assertEquals(listOf("абв", "Абг"), ranked("аб", a, b))
    }

    /* ---------------- both lists use it ---------------- */

    @Test
    fun tasksAndMealsWithTheSameHistoryComeOutInTheSameOrder() {
        // The same four names, the same counts, the same days — once as tasks,
        // once as meals. Each case pins one of the four rules.
        val history = listOf(
            "Не забыть суп" to listOf(march, march, march),      // contains, 3 times
            "Суп гороховый" to listOf(march),                   // starts, 1 time, older
            "Суп куриный" to listOf(march.plusDays(5)),         // starts, 1 time, newer
            "Суп грибной" to listOf(march.plusDays(5)),         // same as above but for the name
        )

        var id = 1L
        val tasks = history.flatMap { (text, days) ->
            days.map { Task(id = id++, date = it, description = text) }
        }
        val meals = history.flatMap { (text, days) ->
            days.map { FoodEntry(id = id++, date = it, title = text, kcal = 100) }
        }

        val expected = listOf("Суп грибной", "Суп куриный", "Суп гороховый", "Не забыть суп")

        assertEquals(expected, taskSuggestionsFor("суп", tasks, emptyList()).map { it.description })
        assertEquals(expected, foodSuggestionsFor("суп", meals).map { it.name })
    }

    @Test
    fun bothListsStaySilentBelowTheSameLength() {
        val tasks = listOf(Task(id = 1, date = march, description = "Суп"))
        val meals = listOf(FoodEntry(id = 2, date = march, title = "Суп", kcal = 100))

        val short = "С".padEnd(SUGGESTION_MIN_LENGTH - 1, 'у')
        val long = "Суп".take(SUGGESTION_MIN_LENGTH)

        assertEquals(0, taskSuggestionsFor(short, tasks, emptyList()).size)
        assertEquals(0, foodSuggestionsFor(short, meals).size)
        assertEquals(1, taskSuggestionsFor(long, tasks, emptyList()).size)
        assertEquals(1, foodSuggestionsFor(long, meals).size)
    }
}
