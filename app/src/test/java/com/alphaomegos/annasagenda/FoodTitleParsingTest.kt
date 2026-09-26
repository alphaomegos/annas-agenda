package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding the portion inside a meal's name.
 *
 * This is doing on the way in what nobody did on the way out: the portion has
 * always been typed into the title, because there was nowhere else to put it.
 * Reading it back out is what lets the suggestions work on the hundreds of
 * meals already logged, instead of only on the ones typed after today.
 *
 * Most of what is asked here is the other half of that bargain: **what is not
 * a portion.** A parser that is eager here turns "Кофе №3" into three grams of
 * coffee and then offers it back with confident arithmetic.
 */
class FoodTitleParsingTest {

    /* ---------------- the ordinary case ---------------- */

    @Test
    fun aPortionAfterACommaIsFound() {
        val parsed = parseFoodTitle("Помидоры, 500 г")

        assertEquals("Помидоры", parsed.name)
        assertEquals(500, parsed.amount)
        assertEquals("г", parsed.unit)
    }

    @Test
    fun theSpaceBeforeTheUnitIsOptional() {
        assertEquals(500, parseFoodTitle("Помидоры 500г").amount)
        assertEquals("Помидоры", parseFoodTitle("Помидоры 500г").name)
    }

    @Test
    fun theLongerSpellingsAreUnderstood() {
        listOf("500 гр", "500 грамм", "500 грамма", "500 граммов").forEach { written ->
            val parsed = parseFoodTitle("Помидоры, $written")
            assertEquals(written, 500, parsed.amount)
            assertEquals(written, "Помидоры", parsed.name)
        }
    }

    @Test
    fun englishUnitsWork() {
        assertEquals(500, parseFoodTitle("Tomatoes, 500 g").amount)
        assertEquals("g", parseFoodTitle("Tomatoes, 500 g").unit)
        assertEquals(250, parseFoodTitle("Milk 250 ml").amount)
        assertEquals("ml", parseFoodTitle("Milk 250 ml").unit)
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals(500, parseFoodTitle("Помидоры, 500 Г").amount)
        assertEquals(250, parseFoodTitle("Milk 250 ML").amount)
    }

    /* ---------------- units that are worth a thousand ---------------- */

    @Test
    fun kilogramsAndLitresAreAThousand() {
        assertEquals(1000, parseFoodTitle("Арбуз, 1 кг").amount)
        assertEquals(1500, parseFoodTitle("Water 1.5 l").amount)
    }

    /**
     * Written back in the small unit, because the next portion will be
     * hundreds of grams rather than fractions of a kilo.
     */
    @Test
    fun aKiloIsWrittenBackAsGrams() {
        assertEquals("г", parseFoodTitle("Арбуз, 1 кг").unit)
        assertEquals("мл", parseFoodTitle("Вода, 1 л").unit)
        assertEquals("g", parseFoodTitle("Melon, 1 kg").unit)
        assertEquals("ml", parseFoodTitle("Water, 1 l").unit)
    }

    /**
     * The script the user typed in is the script they get back. Somebody who
     * writes "кг" should not suddenly be shown "g".
     */
    @Test
    fun theUnitComesBackInTheScriptItWasTypedIn() {
        assertEquals("г", parseFoodTitle("Помидоры 500 гр").unit)
        assertEquals("g", parseFoodTitle("Tomatoes 500 g").unit)
    }

    @Test
    fun aCommaIsADecimalPoint() {
        assertEquals(500, parseFoodTitle("Арбуз, 0,5 кг").amount)
        assertEquals(500, parseFoodTitle("Арбуз, 0.5 кг").amount)
    }

    @Test
    fun aPortionIsRoundedToAWholeGram() {
        assertEquals(501, parseFoodTitle("Арбуз, 0,5006 кг").amount)
        assertEquals(1, parseFoodTitle("Соль, 1,4 г").amount)
    }

    /* ---------------- what is not a portion ---------------- */

    /**
     * The rule the whole thing rests on. An eager parser turns this into
     * three grams of coffee and then offers it back with arithmetic.
     */
    @Test
    fun aNumberWithNoUnitIsNotAPortion() {
        listOf("Борщ 2", "Кофе №3", "Пицца 4 куска", "Омлет из 3 яиц").forEach { title ->
            assertNull(title, parseFoodTitle(title).amount)
            assertEquals(title, title, parseFoodTitle(title).name)
        }
    }

    /**
     * A unit has to end where it ends. "500 груши" is five hundred pears,
     * not five hundred grams of "уши".
     */
    @Test
    fun aUnitThatIsTheStartOfAWordIsNotAUnit() {
        assertNull(parseFoodTitle("Яблоки 500 груши").amount)
        assertNull(parseFoodTitle("Salad 200 grapes").amount)
    }

    /**
     * Cleaned even when there is no portion, so that "Борщ," and "Борщ" are
     * the same food to everything downstream -- and so that a half-deleted
     * portion still reads as the food it is still about.
     */
    @Test
    fun aNameIsTidiedEvenWhenNoPortionIsFound() {
        assertEquals("Борщ", parseFoodTitle("Борщ,").name)
        assertEquals("Помидоры", parseFoodTitle("Помидоры, ").name)
        assertEquals("Борщ", parseFoodTitle(" — Борщ — ").name)
    }

    @Test
    fun aMealWithNoNumbersAtAllHasNoPortion() {
        val parsed = parseFoodTitle("Борщ")

        assertEquals("Борщ", parsed.name)
        assertNull(parsed.amount)
        assertNull(parsed.unit)
    }

    @Test
    fun aPortionOfNothingIsNotAPortion() {
        assertNull(parseFoodTitle("Вода, 0 г").amount)
        assertNull(parseFoodTitle("Вода, 0,4 г").amount)
    }

    /* ---------------- more than one number ---------------- */

    /**
     * People write the portion at the end.
     */
    @Test
    fun theLastPortionInTheTitleWins() {
        val parsed = parseFoodTitle("2 яйца, 100 г")

        assertEquals(100, parsed.amount)
    }

    @Test
    fun aNumberInTheNameIsLeftInTheName() {
        val parsed = parseFoodTitle("Кефир 3,2%, 250 мл")

        assertEquals(250, parsed.amount)
        assertEquals("Кефир 3,2%", parsed.name)
    }

    /* ---------------- what is left behind ---------------- */

    @Test
    fun danglingPunctuationIsCleanedUp() {
        assertEquals("Помидоры", parseFoodTitle("Помидоры, 500 г").name)
        assertEquals("Помидоры", parseFoodTitle("Помидоры - 500 г").name)
        assertEquals("Помидоры", parseFoodTitle("Помидоры — 500 г").name)
        assertEquals("Помидоры", parseFoodTitle("Помидоры; 500 г;").name)
    }

    @Test
    fun aPortionInTheMiddleLeavesTheTwoHalvesJoined() {
        val parsed = parseFoodTitle("Творог 200 г, 5%")

        assertEquals(200, parsed.amount)
        assertEquals("Творог, 5%", parsed.name)
    }

    /**
     * A meal named only by its portion keeps that wording. "500 г" is a poor
     * name for a meal; no name at all is worse.
     */
    @Test
    fun aTitleThatIsNothingButAPortionKeepsItsWording() {
        val parsed = parseFoodTitle("500 г")

        assertEquals("500 г", parsed.name)
        assertEquals(500, parsed.amount)
    }

    @Test
    fun surroundingSpaceIsIgnored() {
        assertEquals("Помидоры", parseFoodTitle("   Помидоры, 500 г   ").name)
        assertEquals(500, parseFoodTitle("   Помидоры, 500 г   ").amount)
    }

    @Test
    fun anEmptyTitleParsesToNothing() {
        val parsed = parseFoodTitle("   ")

        assertEquals("", parsed.name)
        assertNull(parsed.amount)
    }

    /* ---------------- writing it back ---------------- */

    @Test
    fun aMealIsWrittenBackTheWayItWasRead() {
        assertEquals("Помидоры, 300 г", foodTitleWithAmount("Помидоры", 300, "г"))
        assertEquals("Milk, 250 ml", foodTitleWithAmount("Milk", 250, "ml"))
    }

    /**
     * The round trip, which is what the suggestion actually does: read a meal,
     * change the portion, write it out, read it again.
     */
    @Test
    fun whatIsWrittenBackCanBeReadAgain() {
        val first = parseFoodTitle("Помидоры, 500 г")
        val rewritten = foodTitleWithAmount(first.name, 300, first.unit!!)

        val second = parseFoodTitle(rewritten)

        assertEquals(first.name, second.name)
        assertEquals(300, second.amount)
        assertEquals(first.unit, second.unit)
    }
}
