package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the measurement dialog has to hand back after a rotation.
 *
 * Every case here is one the user can produce with a keyboard, which is the
 * only reason any of them is here.
 */
class TypedFieldsSavingTest {

    private fun roundTrip(fields: Map<String, String>) =
        typedFieldsFromSavedStrings(typedFieldsToSavedStrings(fields))

    @Test
    fun whatWasTypedComesBack() {
        val typed = mapOf(
            AnthropometryFieldIds.WEIGHT to "72.4",
            AnthropometryFieldIds.CHEST to "91",
            AnthropometryFieldIds.ARM to "",
        )

        assertEquals(typed, roundTrip(typed))
    }

    /**
     * Cleared is not the same as absent: one says the user wants no value
     * there, the other says the field was never on screen. Dropping empty
     * strings on the way out would turn the first into the second.
     */
    @Test
    fun aClearedFieldStaysAField() {
        val cleared = mapOf("a" to "", "b" to "")

        assertEquals(cleared, roundTrip(cleared))
        assertEquals(listOf("a", "", "b", ""), typedFieldsToSavedStrings(cleared))
    }

    /**
     * Half-typed text is the normal case, not the edge one — the phone turns
     * while a thumb is on the keyboard.
     */
    @Test
    fun halfTypedTextSurvivesExactly() {
        val halfway = mapOf("weight" to "17.", "chest" to "-", "arm" to "0,5 см")

        assertEquals(halfway, roundTrip(halfway))
    }

    @Test
    fun theOrderTheFieldsAreDrawnInIsKept() {
        val ordered = linkedMapOf("c" to "3", "a" to "1", "b" to "2")

        assertEquals(
            listOf("c", "a", "b"),
            roundTrip(ordered).keys.toList(),
        )
    }

    @Test
    fun nothingTypedIsNothingSaved() {
        assertEquals(emptyList<String>(), typedFieldsToSavedStrings(emptyMap()))
        assertEquals(emptyMap<String, String>(), typedFieldsFromSavedStrings(emptyList()))
    }

    /**
     * A flattened list always has an even length when this code wrote it. If
     * something else ever hands over an odd one, the last id has no value and
     * guessing "" for it would invent an answer the user never gave.
     */
    @Test
    fun aTruncatedListLosesOnlyItsTail() {
        assertEquals(
            mapOf("a" to "1"),
            typedFieldsFromSavedStrings(listOf("a", "1", "b")),
        )
    }
}
