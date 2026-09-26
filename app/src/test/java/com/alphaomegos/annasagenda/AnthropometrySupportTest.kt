package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Writing down one day's measurements over what is already there.
 *
 * The rule this function encodes is the one that destroyed data before patch
 * 0013: a field the dialog did not mention keeps its value, and a field it
 * mentions with no value is an erasure. Those two have to stay distinct —
 * collapsing them is exactly what wiped a weight when the user came back to
 * add a waist measurement.
 *
 * The function itself has never had a test.
 */
class AnthropometrySupportTest {

    private val day = LocalDate.of(2026, 3, 23)

    private val existing = AnthropometryEntry(
        date = day,
        weightKg = 72.5,
        waistCm = 80.0,
    )

    @Test
    fun aFieldNobodyMentionedKeepsWhatItHad() {
        val merged = mergeAnthropometryEntryForDate(
            date = day,
            existing = existing,
            valuesByFieldId = mapOf(AnthropometryFieldIds.WAIST to 79.0),
        )

        assertEquals("the waist is what was written", 79.0, merged.waistCm)
        assertEquals("the weight was not mentioned and must survive", 72.5, merged.weightKg)
    }

    @Test
    fun aFieldMentionedWithNoValueIsErased() {
        val merged = mergeAnthropometryEntryForDate(
            date = day,
            existing = existing,
            valuesByFieldId = mapOf(AnthropometryFieldIds.WEIGHT to null),
        )

        assertNull("an empty field means erase, and only when it was sent", merged.weightKg)
        assertEquals(80.0, merged.waistCm)
    }

    @Test
    fun everyFieldCanBeWrittenAndEveryFieldCanBeErased() {
        val all = allAnthropometryFieldIds().associateWith { 42.0 }

        val written = mergeAnthropometryEntryForDate(day, existing = null, valuesByFieldId = all)

        val values = listOf(
            written.armCm, written.chestCm, written.underChestCm, written.waistCm,
            written.bellyCm, written.hipsCm, written.thighCm, written.weightKg,
        )
        assertEquals(List(8) { 42.0 }, values)

        val erased = mergeAnthropometryEntryForDate(
            date = day,
            existing = written,
            valuesByFieldId = allAnthropometryFieldIds().associateWith { null },
        )

        val cleared = listOf(
            erased.armCm, erased.chestCm, erased.underChestCm, erased.waistCm,
            erased.bellyCm, erased.hipsCm, erased.thighCm, erased.weightKg,
        )
        assertEquals(List(8) { null }, cleared)
    }

    @Test
    fun anEntryForADayThatHadNoneStartsFromNothing() {
        val merged = mergeAnthropometryEntryForDate(
            date = day,
            existing = null,
            valuesByFieldId = mapOf(AnthropometryFieldIds.WEIGHT to 70.0),
        )

        assertEquals(day, merged.date)
        assertEquals(70.0, merged.weightKg)
        assertNull(merged.waistCm)
    }

    /** One decimal is what the screens show, so it is what gets stored. */
    @Test
    fun valuesAreRoundedToOneDecimalOnTheWayIn() {
        val merged = mergeAnthropometryEntryForDate(
            date = day,
            existing = null,
            valuesByFieldId = mapOf(
                AnthropometryFieldIds.WEIGHT to 72.449,
                AnthropometryFieldIds.WAIST to 79.95,
            ),
        )

        assertEquals(72.4, merged.weightKg)
        assertEquals(80.0, merged.waistCm)
    }

    @Test
    fun roundingLeavesNullAlone() {
        assertNull(roundAnthropometryValue1(null))
        assertEquals(0.0, roundAnthropometryValue1(0.04))
        assertEquals(-1.2, roundAnthropometryValue1(-1.23))
    }

    @Test
    fun theFieldsOnScreenAreTheKnownOnes() {
        assertEquals(
            setOf(AnthropometryFieldIds.WEIGHT, AnthropometryFieldIds.WAIST),
            normalizeAnthropometryEnabledFieldIds(
                listOf("  weight ", "waist", "not_a_field", "")
            ),
        )
    }

    @Test
    fun turningEveryFieldOffTurnsThemAllBackOn() {
        // An empty set would be a screen with nothing on it and no way back.
        assertEquals(defaultAnthropometryFieldIds(), normalizeAnthropometryEnabledFieldIds(emptyList()))
        assertEquals(
            defaultAnthropometryFieldIds(),
            normalizeAnthropometryEnabledFieldIds(listOf("nonsense", "  ")),
        )
    }

    /* ---------------- the day's place in the list ---------------- */

    private val day1 = LocalDate.of(2026, 9, 1)
    private val day2 = LocalDate.of(2026, 9, 15)
    private val day3 = LocalDate.of(2026, 9, 30)

    private fun weighed(date: LocalDate, kg: Double) =
        AnthropometryEntry(date = date, weightKg = kg)

    private fun save(
        entries: List<AnthropometryEntry>,
        date: LocalDate,
        values: Map<String, Double?>,
    ) = anthropometryAfterSavingDate(entries, date, values)

    @Test
    fun aDayThatWasNotThereIsAdded() {
        val after = save(emptyList(), day2, mapOf(AnthropometryFieldIds.WEIGHT to 71.0))

        assertEquals(listOf(weighed(day2, 71.0)), after)
    }

    @Test
    fun savingOverADayReplacesItRatherThanAddingASecondOne() {
        val before = listOf(weighed(day2, 71.0))

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to 70.5))

        assertEquals(1, after.size)
        assertEquals(70.5, after.single().weightKg!!, 0.001)
    }

    /**
     * The 0013 rule, reaching this far: a field the form did not send keeps
     * what the day already had.
     */
    @Test
    fun aFieldTheFormDidNotSendSurvivesTheSave() {
        val before = listOf(AnthropometryEntry(date = day2, weightKg = 71.0, waistCm = 80.0))

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WAIST to 79.0))

        assertEquals(71.0, after.single().weightKg!!, 0.001)
        assertEquals(79.0, after.single().waistCm!!, 0.001)
    }

    /**
     * Clearing every field is how a day is deleted. An empty entry kept in the
     * list would draw a point on the chart with nothing in it and keep the day
     * in the history for ever.
     */
    @Test
    fun aDayLeftWithNothingOnItIsRemoved() {
        val before = listOf(weighed(day1, 72.0), weighed(day2, 71.0))

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to null))

        assertEquals(listOf(weighed(day1, 72.0)), after)
    }

    @Test
    fun clearingADayThatWasNotThereAddsNothing() {
        val before = listOf(weighed(day1, 72.0))

        assertEquals(before, save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to null)))
    }

    /**
     * Everything downstream reads this list as a sequence rather than sorting
     * it again.
     */
    @Test
    fun theListStaysInDateOrder() {
        val before = listOf(weighed(day1, 72.0), weighed(day3, 70.0))

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to 71.0))

        assertEquals(listOf(day1, day2, day3), after.map { it.date })
    }

    @Test
    fun anOutOfOrderListComesBackInOrder() {
        val before = listOf(weighed(day3, 70.0), weighed(day1, 72.0))

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to 71.0))

        assertEquals(listOf(day1, day2, day3), after.map { it.date })
    }

    @Test
    fun theOtherDaysAreLeftExactlyAsTheyWere() {
        val other = AnthropometryEntry(date = day1, weightKg = 72.0, armCm = 30.0)
        val before = listOf(other)

        val after = save(before, day2, mapOf(AnthropometryFieldIds.WEIGHT to 71.0))

        assertEquals(other, after.first())
    }

    @Test
    fun theSavedValueIsRoundedLikeEveryOtherOne() {
        val after = save(emptyList(), day2, mapOf(AnthropometryFieldIds.WEIGHT to 71.2649))

        assertEquals(71.3, after.single().weightKg!!, 0.0001)
    }
}
