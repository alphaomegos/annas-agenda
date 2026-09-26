package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Who owns the kilocalorie field.
 *
 * The app owns it after a suggestion is picked, and re-prices it when the
 * portion in the name changes — that is the feature. The user owns it the
 * moment they type a number there, and from then on the app leaves it alone.
 *
 * Both halves matter, and the second one more: of the two possible mistakes,
 * silently overwriting a number somebody typed is much the worse.
 */
class FoodDraftTest {

    private val tomatoes = FoodSuggestion(
        name = "Помидоры",
        amount = 500,
        unit = "г",
        kcal = 90,
        timesEaten = 3,
        lastEatenOn = LocalDate.of(2026, 3, 1),
    )

    private val borsch = FoodSuggestion(
        name = "Борщ",
        amount = null,
        unit = null,
        kcal = 320,
        timesEaten = 1,
        lastEatenOn = LocalDate.of(2026, 3, 1),
    )

    /* ---------------- picking ---------------- */

    @Test
    fun pickingFillsBothFields() {
        val state = foodDraftAfterPickingSuggestion(tomatoes)

        assertEquals("Помидоры, 500 г", state.title)
        assertEquals("90", state.kcalText)
        assertNotNull(state.pricedFrom)
    }

    @Test
    fun pickingAFoodWithNoKnownPortionJustFillsTheName() {
        val state = foodDraftAfterPickingSuggestion(borsch)

        assertEquals("Борщ", state.title)
        assertEquals("320", state.kcalText)
    }

    /**
     * The portion goes back into the name the way it came out, so it can be
     * edited in place — which is the gesture the re-pricing exists for.
     */
    @Test
    fun whatIsFilledInCanBeReadBackOut() {
        val state = foodDraftAfterPickingSuggestion(tomatoes)

        val parsed = parseFoodTitle(state.title)

        assertEquals("Помидоры", parsed.name)
        assertEquals(500, parsed.amount)
    }

    /* ---------------- editing the portion ---------------- */

    @Test
    fun changingThePortionRepricesTheMeal() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        val after = foodDraftAfterTitleChange(picked, "Помидоры, 300 г")

        assertEquals("54", after.kcalText)
        assertEquals("Помидоры, 300 г", after.title)
    }

    @Test
    fun theUnitCanChangeToo() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        assertEquals("180", foodDraftAfterTitleChange(picked, "Помидоры, 1 кг").kcalText)
    }

    /**
     * The middle of deleting "500" one digit at a time. Blanking the number on
     * the way through and filling it back in would flicker on every keystroke.
     */
    @Test
    fun aNameWithNoReadablePortionLeavesTheNumberAloneAndKeepsPricing() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        val halfDeleted = foodDraftAfterTitleChange(picked, "Помидоры, ")

        assertEquals("90", halfDeleted.kcalText)
        assertNotNull(halfDeleted.pricedFrom)

        val retyped = foodDraftAfterTitleChange(halfDeleted, "Помидоры, 250 г")

        assertEquals("45", retyped.kcalText)
    }

    @Test
    fun caseAndSpacingInTheNameDoNotBreakThePricing() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        assertEquals("54", foodDraftAfterTitleChange(picked, "  ПОМИДОРЫ , 300 г ").kcalText)
    }

    /**
     * A different food: whatever was priced is no longer about what is
     * written there, so the app stops touching the number.
     */
    @Test
    fun typingADifferentFoodEndsThePricing() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        val after = foodDraftAfterTitleChange(picked, "Огурцы, 300 г")

        assertNull(after.pricedFrom)
        assertEquals("90", after.kcalText)
    }

    @Test
    fun aFoodWithNoKnownPortionIsNeverRepriced() {
        val picked = foodDraftAfterPickingSuggestion(borsch)

        val after = foodDraftAfterTitleChange(picked, "Борщ, 300 г")

        assertEquals("320", after.kcalText)
    }

    @Test
    fun aTitleEditedWithNothingPickedChangesOnlyTheTitle() {
        val typed = FoodDraftState(title = "Пом", kcalText = "12", pricedFrom = null)

        val after = foodDraftAfterTitleChange(typed, "Помидоры, 300 г")

        assertEquals("Помидоры, 300 г", after.title)
        assertEquals("12", after.kcalText)
        assertNull(after.pricedFrom)
    }

    /* ---------------- taking over ---------------- */

    @Test
    fun typingANumberTakesTheFieldOver() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        val mine = foodDraftAfterKcalTyped(picked, "100")

        assertEquals("100", mine.kcalText)
        assertNull(mine.pricedFrom)
    }

    /**
     * The one worth being sure about. Having typed a number, the user changes
     * the portion — and their number stays.
     */
    @Test
    fun onceTakenOverThePortionNoLongerMovesTheNumber() {
        val mine = foodDraftAfterKcalTyped(foodDraftAfterPickingSuggestion(tomatoes), "100")

        val after = foodDraftAfterTitleChange(mine, "Помидоры, 300 г")

        assertEquals("100", after.kcalText)
        assertEquals("Помидоры, 300 г", after.title)
    }

    @Test
    fun pickingAgainHandsTheFieldBack() {
        val mine = foodDraftAfterKcalTyped(foodDraftAfterPickingSuggestion(tomatoes), "100")

        val again = foodDraftAfterPickingSuggestion(tomatoes)

        assertNotNull(again.pricedFrom)
        assertEquals("90", again.kcalText)
        assertEquals("100", mine.kcalText)
    }

    @Test
    fun clearingTheNumberIsAlsoTakingItOver() {
        val picked = foodDraftAfterPickingSuggestion(tomatoes)

        val cleared = foodDraftAfterKcalTyped(picked, "")

        assertEquals("", cleared.kcalText)
        assertNull(cleared.pricedFrom)
        assertEquals("", foodDraftAfterTitleChange(cleared, "Помидоры, 300 г").kcalText)
    }
}
