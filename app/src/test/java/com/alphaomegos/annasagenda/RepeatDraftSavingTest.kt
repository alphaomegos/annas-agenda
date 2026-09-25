package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/**
 * What the repeat dialog has to hand back after a rotation.
 */
class RepeatDraftSavingTest {

    @Test
    fun theChosenDaysComeBack() {
        val chosen = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)

        assertEquals(chosen, weekDaysFromSavedNames(weekDaysToSavedNames(chosen)))
    }

    @Test
    fun everyDayOfTheWeekSurvives() {
        val all = DayOfWeek.entries.toSet()

        assertEquals(all, weekDaysFromSavedNames(weekDaysToSavedNames(all)))
    }

    @Test
    fun choosingNoDaysIsAnAnswerToo() {
        assertEquals(emptyList<String>(), weekDaysToSavedNames(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), weekDaysFromSavedNames(emptyList()))
    }

    @Test
    fun everyFrequencyComesBackAsItself() {
        RepeatFreq.entries.forEach { freq ->
            assertEquals(freq, repeatFreqFromSavedName(freq.name))
        }
    }

    /**
     * Nothing rather than something. A name this build cannot read means the
     * saved draft is not usable, and the dialog then opens on the rule the
     * user last confirmed — which is a real answer of theirs, unlike whatever
     * the first constant in the declaration happens to be.
     */
    @Test
    fun anUnknownNameGivesNothingBack() {
        assertNull(repeatFreqFromSavedName("FORTNIGHTLY"))
        assertNull(repeatFreqFromSavedName(""))
        assertNull(repeatFreqFromSavedName("weekly"))
    }

    @Test
    fun anUnknownDayIsDroppedAndTheRestKept() {
        assertEquals(
            setOf(DayOfWeek.TUESDAY),
            weekDaysFromSavedNames(listOf("TUESDAY", "CATURDAY")),
        )
    }

    /**
     * Names, not positions. If this ever starts saving ordinals, reordering
     * the enum turns every saved Monday into some other day and nothing says
     * so.
     */
    @Test
    fun whatIsWrittenDownIsTheName() {
        assertEquals(listOf("WEDNESDAY"), weekDaysToSavedNames(setOf(DayOfWeek.WEDNESDAY)))
    }
}
