package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explanation behind the (i) has to stay true.
 *
 * It is the one piece of writing in this app that makes checkable promises:
 * it names the units the parser understands and gives an example of a number
 * that is *not* a weight. Prose like that goes stale in silence — somebody
 * adds a unit, or tightens the parser, and the only thing that notices is a
 * user following instructions that stopped being right.
 *
 * So the claims are asked of the parser itself. Both languages, because the
 * two texts were written separately and can drift apart from each other as
 * well as from the code.
 */
class FoodSuggestionsHelpTest {

    private val helpEn = stringResourceText("values", "calorimeter_suggestions_help_text")
    private val helpRu = stringResourceText("values-ru", "calorimeter_suggestions_help_text")

    /** Every unit the two texts name, with a weight the parser should find. */
    private val unitsPromised = listOf(
        "g" to "Tomatoes, 500 g",
        "gr" to "Tomatoes, 500 gr",
        "gram" to "Tomatoes, 500 gram",
        "kg" to "Melon, 1 kg",
        "ml" to "Milk, 250 ml",
        "l" to "Water, 1 l",
        "г" to "Помидоры, 500 г",
        "гр" to "Помидоры, 500 гр",
        "грамм" to "Помидоры, 500 грамм",
        "кг" to "Арбуз, 1 кг",
        "мл" to "Молоко, 250 мл",
        "л" to "Вода, 1 л",
    )

    @Test
    fun theExplanationIsThere() {
        assertNotNull("the English help text is missing", helpEn)
        assertNotNull("the Russian help text is missing", helpRu)
    }

    /**
     * Every unit the text names is a unit the parser reads. Removing one from
     * the parser without removing it from the text fails here.
     */
    @Test
    fun everyUnitTheTextNamesIsOneTheParserReads() {
        unitsPromised.forEach { (unit, example) ->
            assertNotNull("the text names '$unit' but '$example' has no weight in it",
                parseFoodTitle(example).amount)
        }
    }

    /**
     * And the text names them all. Adding a unit to the parser without saying
     * so leaves users guessing; this fails until the sentence is updated.
     */
    @Test
    fun theTextNamesEveryUnitTheParserReads() {
        val russian = listOf("г", "гр", "грамм", "кг", "мл", "л")
        val english = listOf("g", "gr", "gram", "kg", "ml", "l")

        russian.forEach {
            assertTrue("the Russian help does not name '$it'", helpRu!!.contains(it))
        }
        english.forEach {
            assertTrue("the English help does not name '$it'", helpEn!!.contains(it))
        }
    }

    /**
     * The example each text gives of a number that is not a weight. If the
     * parser ever became eager enough to read three grams of coffee out of
     * this, the explanation would be teaching a lie.
     */
    @Test
    fun theExampleOfWhatIsNotAWeightIsStillNotAWeight() {
        assertTrue("the English help lost its counter-example", helpEn!!.contains("Coffee #3"))
        assertTrue("the Russian help lost its counter-example", helpRu!!.contains("Кофе №3"))

        assertNull(parseFoodTitle("Coffee #3").amount)
        assertNull(parseFoodTitle("Кофе №3").amount)
    }

    /**
     * The worked example both texts open with.
     */
    @Test
    fun theWorkedExampleWorks() {
        assertTrue(helpEn!!.contains("Tomatoes, 500 g"))
        assertTrue(helpRu!!.contains("Помидоры, 500 г"))

        assertEquals(500, parseFoodTitle("Tomatoes, 500 g").amount)
        assertEquals("Tomatoes", parseFoodTitle("Tomatoes, 500 g").name)
        assertEquals(500, parseFoodTitle("Помидоры, 500 г").amount)
        assertEquals("Помидоры", parseFoodTitle("Помидоры, 500 г").name)
    }

    /**
     * "Kilograms and litres count as a thousand" is arithmetic, so it can be
     * checked as arithmetic.
     */
    @Test
    fun aKiloAndALitreReallyAreAThousand() {
        assertEquals(1000, parseFoodTitle("Melon, 1 kg").amount)
        assertEquals(1000, parseFoodTitle("Вода, 1 л").amount)
    }

    /**
     * "The calories follow, in proportion" — the sentence that promises the
     * arithmetic the whole feature rests on.
     */
    @Test
    fun theProportionTheTextPromisesIsTheProportionItDoes() {
        val tomatoes = FoodSuggestion(
            name = "Помидоры",
            amount = 500,
            unit = "г",
            kcal = 90,
            timesEaten = 1,
            lastEatenOn = null,
        )

        assertEquals(45, kcalForAmount(tomatoes, 250))
        assertEquals(180, kcalForAmount(tomatoes, 1000))
    }
}
