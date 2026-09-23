package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunningPlanSupportTest {

    @Test
    fun parseRunningDurationToMinutes_returnsNullForBlankOrNonDigitInput() {
        assertNull(parseRunningDurationToMinutes(""))
        assertNull(parseRunningDurationToMinutes("   "))
        assertNull(parseRunningDurationToMinutes("abc"))
    }

    @Test
    fun parseRunningDurationToMinutes_parsesMinutesAndHhMmFormats() {
        assertEquals(75, parseRunningDurationToMinutes("75"))
        assertEquals(75, parseRunningDurationToMinutes("0115"))
        assertEquals(75, parseRunningDurationToMinutes("01:15"))
        assertEquals(83, parseRunningDurationToMinutes("123"))
    }

    @Test
    fun parseRunningKm_and_formatRunningKmForTitle_handleTrimAndComma() {
        assertEquals(10.5, parseRunningKm(" 10,5 ")!!, 0.0001)
        assertEquals(10.0, parseRunningKm("10.0")!!, 0.0001)
        assertNull(parseRunningKm("abc"))

        assertEquals("10", formatRunningKmForTitle("10.0"))
        assertEquals("10.5", formatRunningKmForTitle("10,5"))
        assertEquals("abc", formatRunningKmForTitle(" abc "))
    }

    @Test
    fun isRunningPlanEntryIncomplete_detectsMissingOrInvalidFields() {
        val complete = entry(
            distanceKmText = "10.0",
            durationHhMmText = "0115",
            paceText = "07:30",
        )
        assertFalse(isRunningPlanEntryIncomplete(complete))

        val badKm = complete.copy(distanceKmText = "abc")
        assertTrue(isRunningPlanEntryIncomplete(badKm))

        val badDuration = complete.copy(durationHhMmText = "")
        assertTrue(isRunningPlanEntryIncomplete(badDuration))

        val zeroDuration = complete.copy(durationHhMmText = "0000")
        assertTrue(isRunningPlanEntryIncomplete(zeroDuration))

        val badPace = complete.copy(paceText = "730")
        assertTrue(isRunningPlanEntryIncomplete(badPace))
    }

    @Test
    fun buildRunningPlanTaskTitle_prefersDistanceOtherwiseUsesMinutes() {
        val withDistance = entry(
            distanceKmText = "10,0",
            durationHhMmText = "0115",
            paceText = "07:30",
        )

        val distanceTitle = buildRunningPlanTaskTitle(
            entry = withDistance,
            formatKmTitle = { "Run $it km" },
            formatMinutesTitle = { "Run $it min" },
        )

        assertEquals("Run 10 km", distanceTitle)

        val withoutDistance = withDistance.copy(distanceKmText = " ")
        val minutesTitle = buildRunningPlanTaskTitle(
            entry = withoutDistance,
            formatKmTitle = { "Run $it km" },
            formatMinutesTitle = { "Run $it min" },
        )

        assertEquals("Run 75 min", minutesTitle)
    }

    @Test
    fun buildRunningPlanTaskTitle_returnsNullWhenNothingUsableExists() {
        val entry = entry(
            distanceKmText = " ",
            durationHhMmText = "",
            paceText = "07:30",
        )

        val title = buildRunningPlanTaskTitle(
            entry = entry,
            formatKmTitle = { "Run $it km" },
            formatMinutesTitle = { "Run $it min" },
        )

        assertNull(title)
    }

    /* ---------------- a row must not outlive its task ---------------- */

    /**
     * Ids are handed out as "one past the largest in use", so a number freed by
     * a deletion comes back after a restart. A plan row still holding that
     * number then points at somebody else's task — and the plan deletes and
     * renames what it points at.
     */
    @Test
    fun deletingATaskTakesItsReferenceOutOfThePlan() {
        val date = LocalDate.of(2026, 9, 23)
        val entries = listOf(
            entry(date, "5", "", "", taskId = 57L),
            entry(date.plusDays(1), "7", "", "", taskId = 58L),
        )

        val result = runningPlanEntriesWithoutTask(entries, 57L)

        assertNull("the row keeps its numbers but forgets the task", result[0].taskId)
        assertEquals("5", result[0].distanceKmText)
        assertEquals(58L, result[1].taskId)
    }

    @Test
    fun aPlanThatNeverMentionedTheTaskIsHandedBackUnchanged() {
        val entries = listOf(entry(LocalDate.of(2026, 9, 23), "5", "", "", taskId = 58L))

        assertSame(entries, runningPlanEntriesWithoutTask(entries, 57L))
    }

    @Test
    fun everyRowPointingAtTheSameTaskForgetsIt() {
        val date = LocalDate.of(2026, 9, 23)
        val entries = listOf(
            entry(date, "5", "", "", taskId = 57L),
            entry(date.plusDays(1), "7", "", "", taskId = 57L),
        )

        assertTrue(runningPlanEntriesWithoutTask(entries, 57L).all { it.taskId == null })
    }

    private fun entry(
        date: LocalDate = LocalDate.of(2026, 3, 20),
        distanceKmText: String,
        durationHhMmText: String,
        paceText: String,
        taskId: Long? = null,
    ): RunningPlanEntry = RunningPlanEntry(
        date = date,
        distanceKmText = distanceKmText,
        durationHhMmText = durationHhMmText,
        paceText = paceText,
        taskId = taskId,
    )
}