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

    /* ---------- typing into one day's row ---------- */

    private val day = LocalDate.of(2026, 3, 10)

    private fun row(
        date: LocalDate = day,
        km: String = "",
        duration: String = "",
        pace: String = "",
        taskId: Long? = null,
    ) = RunningPlanEntry(
        date = date,
        distanceKmText = km,
        durationHhMmText = duration,
        paceText = pace,
        taskId = taskId,
    )

    private fun edit(
        entries: List<RunningPlanEntry>,
        approved: Boolean = false,
        date: LocalDate = day,
        km: String? = null,
        duration: String? = null,
        pace: String? = null,
    ) = runningPlanEntriesAfterEdit(entries, approved, date, km, duration, pace)

    @Test
    fun typingIntoADayThatHasNoRowMakesOne() {
        val after = edit(emptyList(), km = "5")

        assertEquals(1, after.entries.size)
        assertEquals("5", after.entries.single().distanceKmText)
        assertNull(after.orphanedTaskId)
    }

    /**
     * A field nobody sent is a field nobody touched. The screen edits one
     * column at a time and must not have to resend the other two.
     */
    @Test
    fun aFieldThatWasNotSentIsNotCleared() {
        val after = edit(listOf(row(km = "5", duration = "0030")), km = "7")

        assertEquals("7", after.entries.single().distanceKmText)
        assertEquals("0030", after.entries.single().durationHhMmText)
    }

    @Test
    fun theRowsComeBackInDateOrder() {
        val after = edit(
            listOf(row(date = day.plusDays(2), km = "3"), row(date = day.minusDays(1), km = "4")),
            km = "5",
        )

        assertEquals(
            listOf(day.minusDays(1), day, day.plusDays(2)),
            after.entries.map { it.date },
        )
    }

    /**
     * Before the plan is approved the pace column is not the user's to fill:
     * it is what the plan will ask of them, not what they did.
     */
    @Test
    fun paceIsIgnoredUntilThePlanIsApproved() {
        val after = edit(listOf(row(km = "5")), approved = false, pace = "0530")

        assertEquals("", after.entries.single().paceText)
    }

    @Test
    fun paceIsTheirsOnceThePlanIsApproved() {
        val after = edit(listOf(row(km = "5")), approved = true, pace = "0530")

        assertEquals("0530", after.entries.single().paceText)
    }

    /**
     * Emptying a row means two different things either side of approval.
     * Before, the plan is still being written: the row goes, and its task goes
     * with it or it sits in the calendar as a run nobody planned.
     */
    @Test
    fun emptyingARowBeforeApprovalRemovesItAndOrphansItsTask() {
        val after = edit(listOf(row(km = "5", taskId = 77L)), approved = false, km = "")

        assertTrue(after.entries.isEmpty())
        assertEquals(77L, after.orphanedTaskId)
    }

    @Test
    fun emptyingARowBeforeApprovalWithNoTaskOrphansNothing() {
        val after = edit(listOf(row(km = "5")), approved = false, km = "")

        assertTrue(after.entries.isEmpty())
        assertNull(after.orphanedTaskId)
    }

    /** After approval the row is a day of the plan, empty or not. */
    @Test
    fun emptyingARowAfterApprovalKeepsItAndItsTask() {
        val after = edit(listOf(row(km = "5", taskId = 77L)), approved = true, km = "")

        assertEquals(1, after.entries.size)
        assertEquals(77L, after.entries.single().taskId)
        assertEquals("", after.entries.single().distanceKmText)
        assertNull(after.orphanedTaskId)
    }

    @Test
    fun typingNothingIntoADayThatHasNoRowMakesNothing() {
        val entries = listOf(row(date = day.plusDays(1), km = "5"))

        assertSame(entries, edit(entries, approved = true, km = "").entries)
        assertSame(entries, edit(entries, approved = false, km = "").entries)
    }

    @Test
    fun aRowIsEmptyOnlyWhenAllThreeFieldsAre() {
        val kept = edit(listOf(row(km = "5", pace = "0530")), approved = true, km = "")

        assertEquals(1, kept.entries.size)
        assertEquals("0530", kept.entries.single().paceText)
    }

    @Test
    fun otherDaysAreNotTouched() {
        val other = row(date = day.plusDays(1), km = "9", taskId = 5L)

        val after = edit(listOf(other, row(km = "5", taskId = 6L)), approved = false, km = "")

        assertEquals(listOf(other), after.entries)
        assertEquals(6L, after.orphanedTaskId)
    }

    @Test
    fun editingARowKeepsWhateverTaskItHeld() {
        val after = edit(listOf(row(km = "5", taskId = 77L)), approved = true, km = "6")

        assertEquals(77L, after.entries.single().taskId)
    }

    @Test
    fun aBonusRowStaysABonusRow() {
        val bonus = row(km = "5").copy(isBonus = true)

        val after = edit(listOf(bonus), approved = true, km = "6")

        assertTrue(after.entries.single().isBonus)
    }

    /* ---------- approving the plan ---------- */

    @Test
    fun approvingDropsTheBlankDays() {
        val cleaned = runningPlanEntriesCleanedForApproval(
            listOf(row(km = "5"), row(date = day.plusDays(1)), row(date = day.plusDays(2), duration = "0030"))
        )

        assertEquals(listOf(day, day.plusDays(2)), cleaned.map { it.date })
    }

    /** A stray space is not a plan for that day. */
    @Test
    fun approvingTreatsWhitespaceAsNothing() {
        val cleaned = runningPlanEntriesCleanedForApproval(
            listOf(row(km = "   ", duration = " ", pace = "\t"))
        )

        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun approvingTrimsWhatIsKept() {
        val cleaned = runningPlanEntriesCleanedForApproval(
            listOf(row(km = "  5 ", duration = " 0030 ", pace = " 0530 "))
        )

        assertEquals("5", cleaned.single().distanceKmText)
        assertEquals("0030", cleaned.single().durationHhMmText)
        assertEquals("0530", cleaned.single().paceText)
    }

    @Test
    fun approvingPutsTheDaysInOrder() {
        val cleaned = runningPlanEntriesCleanedForApproval(
            listOf(row(date = day.plusDays(3), km = "3"), row(km = "1"), row(date = day.plusDays(1), km = "2"))
        )

        assertEquals(listOf(day, day.plusDays(1), day.plusDays(3)), cleaned.map { it.date })
    }

    @Test
    fun approvingKeepsWhateverTaskARowAlreadyHad() {
        val cleaned = runningPlanEntriesCleanedForApproval(listOf(row(km = "5", taskId = 77L)))

        assertEquals(77L, cleaned.single().taskId)
    }

    /* ---------- the days the plan gives up on ---------- */

    private fun incomplete(date: LocalDate) = row(date = date, km = "5")

    private fun complete(date: LocalDate) =
        row(date = date, km = "5", duration = "0030", pace = "0530")

    /**
     * A run is still the user's to write up on the evening and the morning
     * after. Only from the day past that does the plan stop waiting.
     */
    @Test
    fun aDayIsGivenUpOnOnlyAfterMoreThanAWholeDayHasPassed() {
        val today = day

        assertTrue(expiredRunningPlanEntries(listOf(incomplete(today)), today).isEmpty())
        assertTrue(expiredRunningPlanEntries(listOf(incomplete(today.minusDays(1))), today).isEmpty())
        assertEquals(1, expiredRunningPlanEntries(listOf(incomplete(today.minusDays(2))), today).size)
    }

    @Test
    fun aDayThatWasWrittenUpIsNeverGivenUpOn() {
        val today = day

        assertTrue(expiredRunningPlanEntries(listOf(complete(today.minusDays(30))), today).isEmpty())
    }

    @Test
    fun aDayStillAheadIsNeverGivenUpOn() {
        val today = day

        assertTrue(expiredRunningPlanEntries(listOf(incomplete(today.plusDays(5))), today).isEmpty())
    }

    @Test
    fun onlyTheDaysGivenUpOnComeBack() {
        val today = day
        val old = incomplete(today.minusDays(10))
        val entries = listOf(old, complete(today.minusDays(10)), incomplete(today))

        assertEquals(listOf(old), expiredRunningPlanEntries(entries, today))
    }
}
