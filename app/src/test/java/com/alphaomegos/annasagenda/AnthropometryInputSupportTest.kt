package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the difference between "left empty" and "cannot be read".
 *
 * The dialog used to collapse both into null, and null means erase. So a
 * weight typed as "72.5 kg" — unit included, on a full text keyboard, into a
 * field pre-filled with the saved value — deleted that day's weight and said
 * "Saved".
 */
class AnthropometryInputSupportTest {

    @Test
    fun anEmptyFieldMeansEraseTheMeasurement() {
        assertEquals(AnthropometryFieldInput.Cleared, parseAnthropometryField(""))
        assertEquals(AnthropometryFieldInput.Cleared, parseAnthropometryField("   "))
    }

    @Test
    fun aPlainNumberIsRead() {
        assertEquals(
            AnthropometryFieldInput.Value(72.5),
            parseAnthropometryField("72.5"),
        )
    }

    @Test
    fun aCommaWorksLikeADot() {
        assertEquals(
            AnthropometryFieldInput.Value(72.5),
            parseAnthropometryField("72,5"),
        )
    }

    @Test
    fun surroundingSpaceIsIgnored() {
        assertEquals(
            AnthropometryFieldInput.Value(72.5),
            parseAnthropometryField("  72.5  "),
        )
    }

    /** The exact thing that used to erase the day. */
    @Test
    fun aNumberWithAUnitIsInvalidRatherThanEmpty() {
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("72.5 kg"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("72,5 кг"))
    }

    @Test
    fun rubbishIsInvalid() {
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("abc"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("-"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("."))
    }

    /**
     * These three parse happily with toDoubleOrNull and then travel into the
     * chart, where a single one of them turns every path into nothing.
     */
    @Test
    fun nonFiniteValuesAreRejected() {
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("NaN"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("Infinity"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("-Infinity"))
    }

    @Test
    fun zeroAndNegativeAreNotMeasurements() {
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("0"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("-5"))
    }

    @Test
    fun theUpperBoundIsInclusiveAndAnythingAboveItIsRejected() {
        assertEquals(
            AnthropometryFieldInput.Value(ANTHROPOMETRY_MAX_VALUE),
            parseAnthropometryField("999"),
        )
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("999.1"))
        assertEquals(AnthropometryFieldInput.Invalid, parseAnthropometryField("1000000"))
    }

    /* ---------------- a whole dialog's worth ---------------- */

    @Test
    fun aDialogWhereEverythingReadsIsValid() {
        val parsed = parseAnthropometryInputs(
            mapOf(
                AnthropometryFieldIds.WEIGHT to "72.5",
                AnthropometryFieldIds.WAIST to "",
            )
        )

        assertTrue(parsed.isValid)
        assertEquals(72.5, parsed.values[AnthropometryFieldIds.WEIGHT])
        assertTrue(
            "an empty field is still an instruction: erase it",
            AnthropometryFieldIds.WAIST in parsed.values,
        )
        assertNull(parsed.values[AnthropometryFieldIds.WAIST])
    }

    @Test
    fun oneUnreadableFieldMakesTheWholeSaveInvalid() {
        val parsed = parseAnthropometryInputs(
            mapOf(
                AnthropometryFieldIds.WEIGHT to "72.5 kg",
                AnthropometryFieldIds.WAIST to "80",
            )
        )

        assertFalse(parsed.isValid)
        assertEquals(setOf(AnthropometryFieldIds.WEIGHT), parsed.invalidFieldIds)
    }

    /**
     * Belt and braces: even a caller that ignored invalidFieldIds must not be
     * able to erase the stored value, because the unreadable field is not in
     * the map at all and merge leaves absent fields alone.
     */
    @Test
    fun anUnreadableFieldIsNotHandedOverAsAnErase() {
        val parsed = parseAnthropometryInputs(
            mapOf(AnthropometryFieldIds.WEIGHT to "72.5 kg")
        )

        assertFalse(AnthropometryFieldIds.WEIGHT in parsed.values)

        val existing = AnthropometryEntry(
            date = LocalDate.of(2026, 9, 20),
            weightKg = 72.5,
        )
        val merged = mergeAnthropometryEntryForDate(
            date = existing.date,
            existing = existing,
            valuesByFieldId = parsed.values,
        )

        assertEquals(72.5, merged.weightKg)
    }

    @Test
    fun clearingAFieldStillErasesTheStoredValue() {
        val parsed = parseAnthropometryInputs(
            mapOf(AnthropometryFieldIds.WEIGHT to "")
        )

        val existing = AnthropometryEntry(
            date = LocalDate.of(2026, 9, 20),
            weightKg = 72.5,
        )
        val merged = mergeAnthropometryEntryForDate(
            date = existing.date,
            existing = existing,
            valuesByFieldId = parsed.values,
        )

        assertNull("an empty field is how a measurement is removed", merged.weightKg)
    }
}
