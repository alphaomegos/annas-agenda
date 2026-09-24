package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Covers the recurrence engine on a plain JVM.
 *
 * Until this logic was extracted from AppViewModel it could only be reached
 * through an instrumented test on a device, which is why almost none of it was
 * covered. A Monday week start is passed explicitly everywhere so results do
 * not depend on the machine's locale.
 */
class RecurrenceSupportTest {

    private val iso: DayOfWeek = DayOfWeek.MONDAY

    private val monday = LocalDate.of(2026, 3, 23)
    private val nextMonday = LocalDate.of(2026, 3, 30)
    private val mondayAfter = LocalDate.of(2026, 4, 6)

    private fun task(
        id: Long,
        date: LocalDate?,
        repeatRule: RepeatRule? = null,
        originTaskId: Long? = null,
        order: Int = 0,
        description: String = "Task $id",
    ) = Task(
        id = id,
        order = order,
        date = date,
        description = description,
        repeatRule = repeatRule,
        originTaskId = originTaskId,
    )

    private fun subtask(
        id: Long,
        taskId: Long,
        repeatRule: RepeatRule? = null,
        originSubtaskId: Long? = null,
    ) = Subtask(
        id = id,
        taskId = taskId,
        description = "Subtask $id",
        repeatRule = repeatRule,
        originSubtaskId = originSubtaskId,
    )

    private fun generate(
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        suppressed: Set<String> = emptySet(),
        start: LocalDate,
        end: LocalDate,
        nextId: Long = 1000L,
    ) = generateRecurrencesInRange(
        tasks = tasks,
        subtasks = subtasks,
        suppressedRecurrences = suppressed,
        start = start,
        end = end,
        nextId = nextId,
        defaultWeekStart = iso,
    )

    /* ---------------- matchesRepeat ---------------- */

    @Test
    fun anchorDayItselfIsNeverAnOccurrence() {
        val rule = RepeatRule(freq = RepeatFreq.DAILY)

        assertFalse(matchesRepeat(monday, monday, rule, iso))
    }

    @Test
    fun dateBeforeAnchorIsNeverAnOccurrence() {
        val rule = RepeatRule(freq = RepeatFreq.DAILY)

        assertFalse(matchesRepeat(monday, monday.minusDays(1), rule, iso))
    }

    @Test
    fun dailyRuleMatchesEveryFollowingDay() {
        val rule = RepeatRule(freq = RepeatFreq.DAILY)

        assertTrue(matchesRepeat(monday, monday.plusDays(1), rule, iso))
        assertTrue(matchesRepeat(monday, monday.plusDays(30), rule, iso))
    }

    @Test
    fun dailyRuleHonoursInterval() {
        val everyThirdDay = RepeatRule(freq = RepeatFreq.DAILY, interval = 3)

        assertFalse(matchesRepeat(monday, LocalDate.of(2026, 3, 25), everyThirdDay, iso))
        assertTrue(matchesRepeat(monday, LocalDate.of(2026, 3, 26), everyThirdDay, iso))
    }

    @Test
    fun zeroOrNegativeIntervalIsTreatedAsOne() {
        val broken = RepeatRule(freq = RepeatFreq.DAILY, interval = 0)

        assertTrue(matchesRepeat(monday, monday.plusDays(1), broken, iso))
    }

    @Test
    fun weeklyRuleOnlyMatchesSelectedWeekdays() {
        val rule = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            weekDays = setOf(DayOfWeek.MONDAY),
        )

        assertTrue(matchesRepeat(monday, nextMonday, rule, iso))
        assertFalse(matchesRepeat(monday, nextMonday.plusDays(1), rule, iso))
    }

    @Test
    fun weeklyRuleHonoursInterval() {
        val everyOtherWeek = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 2,
            weekDays = setOf(DayOfWeek.MONDAY),
        )

        assertFalse(matchesRepeat(monday, nextMonday, everyOtherWeek, iso))
        assertTrue(matchesRepeat(monday, mondayAfter, everyOtherWeek, iso))
    }

    @Test
    fun monthlyRuleFallsBackToAnchorDayOfMonth() {
        val anchor = LocalDate.of(2026, 1, 15)
        val rule = RepeatRule(freq = RepeatFreq.MONTHLY)

        assertTrue(matchesRepeat(anchor, LocalDate.of(2026, 2, 15), rule, iso))
        assertFalse(matchesRepeat(anchor, LocalDate.of(2026, 2, 16), rule, iso))
    }

    @Test
    fun monthlyRuleHonoursIntervalAndExplicitDayOfMonth() {
        val anchor = LocalDate.of(2026, 1, 15)
        val everyOtherMonth = RepeatRule(
            freq = RepeatFreq.MONTHLY,
            interval = 2,
            dayOfMonth = 15,
        )

        assertFalse(matchesRepeat(anchor, LocalDate.of(2026, 2, 15), everyOtherMonth, iso))
        assertTrue(matchesRepeat(anchor, LocalDate.of(2026, 3, 15), everyOtherMonth, iso))
    }

    /* ---------------- where the week starts ---------------- */

    @Test
    fun startOfWeekWalksBackToTheChosenFirstDay() {
        val sunday = LocalDate.of(2026, 3, 22)

        assertEquals(LocalDate.of(2026, 3, 16), startOfWeek(sunday, DayOfWeek.MONDAY))
        assertEquals(sunday, startOfWeek(sunday, DayOfWeek.SUNDAY))
        assertEquals(monday, startOfWeek(monday, DayOfWeek.MONDAY))
    }

    /**
     * The bug this field exists for. Anchor on a Sunday, target the Monday nine
     * days later, every two weeks: the two week boundaries disagree about how
     * many weeks have passed, so the same rule fires under one and not the
     * other. Switching the app language used to flip this.
     */
    @Test
    fun aWeeklyIntervalDependsOnWhereTheWeekStarts() {
        val anchorSunday = LocalDate.of(2026, 3, 22)
        val targetMonday = LocalDate.of(2026, 3, 30)
        val rule = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 2,
            weekDays = setOf(DayOfWeek.MONDAY),
        )

        assertTrue(matchesRepeat(anchorSunday, targetMonday, rule, DayOfWeek.MONDAY))
        assertFalse(matchesRepeat(anchorSunday, targetMonday, rule, DayOfWeek.SUNDAY))
    }

    @Test
    fun theRuleSOwnWeekStartWinsOverTheLocale() {
        val anchorSunday = LocalDate.of(2026, 3, 22)
        val targetMonday = LocalDate.of(2026, 3, 30)
        val pinnedToMonday = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 2,
            weekDays = setOf(DayOfWeek.MONDAY),
            weekStart = DayOfWeek.MONDAY,
        )

        // Same rule, opposite locale defaults, same answer.
        assertTrue(matchesRepeat(anchorSunday, targetMonday, pinnedToMonday, DayOfWeek.SUNDAY))
        assertTrue(matchesRepeat(anchorSunday, targetMonday, pinnedToMonday, DayOfWeek.MONDAY))
    }

    @Test
    fun aRuleWithoutARecordedWeekStartStillFollowsTheLocale() {
        val anchorSunday = LocalDate.of(2026, 3, 22)
        val targetMonday = LocalDate.of(2026, 3, 30)
        val legacy = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            interval = 2,
            weekDays = setOf(DayOfWeek.MONDAY),
            weekStart = null,
        )

        assertTrue(matchesRepeat(anchorSunday, targetMonday, legacy, DayOfWeek.MONDAY))
        assertFalse(matchesRepeat(anchorSunday, targetMonday, legacy, DayOfWeek.SUNDAY))
    }

    @Test
    fun weekStartDoesNotAffectDailyOrMonthlyRules() {
        val daily = RepeatRule(freq = RepeatFreq.DAILY, interval = 3)
        val monthly = RepeatRule(freq = RepeatFreq.MONTHLY, interval = 2, dayOfMonth = 15)
        val anchorMonthly = LocalDate.of(2026, 1, 15)

        assertEquals(
            matchesRepeat(monday, LocalDate.of(2026, 3, 26), daily, DayOfWeek.MONDAY),
            matchesRepeat(monday, LocalDate.of(2026, 3, 26), daily, DayOfWeek.SUNDAY),
        )
        assertEquals(
            matchesRepeat(anchorMonthly, LocalDate.of(2026, 3, 15), monthly, DayOfWeek.MONDAY),
            matchesRepeat(anchorMonthly, LocalDate.of(2026, 3, 15), monthly, DayOfWeek.SUNDAY),
        )
    }

    /* ---------------- generateRecurrencesInRange ---------------- */

    @Test
    fun generatesOneOccurrencePerMatchingDay() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))

        val result = generate(
            tasks = listOf(template),
            start = monday.plusDays(1),
            end = monday.plusDays(3),
        )

        val generated = result.tasks.filter { it.originTaskId == 1L }
        assertEquals(3, generated.size)
        assertEquals(
            listOf(monday.plusDays(1), monday.plusDays(2), monday.plusDays(3)),
            generated.mapNotNull { it.date }.sorted(),
        )
    }

    @Test
    fun generatedOccurrencesCarryNoRuleAndPointAtTheirTemplate() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))

        val result = generate(
            tasks = listOf(template),
            start = monday.plusDays(1),
            end = monday.plusDays(1),
        )

        val generated = result.tasks.single { it.originTaskId == 1L }
        assertNull("a generated instance must not repeat by itself", generated.repeatRule)
        assertEquals(1L, generated.originTaskId)
        assertFalse(generated.isDone)
        assertEquals(template.description, generated.description)
    }

    @Test
    fun generationIsIdempotent() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1)

        val first = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            start = monday.plusDays(1),
            end = monday.plusDays(5),
        )

        val second = generateRecurrencesInRange(
            tasks = first.tasks,
            subtasks = first.subtasks,
            suppressedRecurrences = emptySet(),
            start = monday.plusDays(1),
            end = monday.plusDays(5),
            nextId = first.nextId,
            defaultWeekStart = iso,
        )

        assertEquals(first.tasks, second.tasks)
        assertEquals(first.subtasks, second.subtasks)
        assertEquals(first.nextId, second.nextId)
    }

    @Test
    fun suppressedTaskOccurrenceIsNotGenerated() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val skipped = monday.plusDays(2)

        val result = generate(
            tasks = listOf(template),
            suppressed = setOf(taskSuppressionKey(1L, skipped)),
            start = monday.plusDays(1),
            end = monday.plusDays(3),
        )

        val dates = result.tasks.filter { it.originTaskId == 1L }.mapNotNull { it.date }
        assertEquals(listOf(monday.plusDays(1), monday.plusDays(3)), dates.sorted())
    }

    @Test
    fun templateSubtasksAreClonedIntoGeneratedTask() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            start = monday.plusDays(1),
            end = monday.plusDays(1),
        )

        val generatedTask = result.tasks.single { it.originTaskId == 1L }
        val generatedSub = result.subtasks.single { it.originSubtaskId == 2L }

        assertEquals(generatedTask.id, generatedSub.taskId)
        assertNull(generatedSub.repeatRule)
        assertTrue("hasSubtasks must be set on the clone", generatedTask.hasSubtasks)
    }

    @Test
    fun suppressedSubtaskOccurrenceIsSkippedButItsTaskIsStillGenerated() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1)
        val day = monday.plusDays(1)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            suppressed = setOf(subtaskSuppressionKey(2L, day)),
            start = day,
            end = day,
        )

        assertNotNull(result.tasks.singleOrNull { it.originTaskId == 1L })
        assertTrue(result.subtasks.none { it.originSubtaskId == 2L })
    }

    /**
     * A subtask can repeat on its own, without its parent task repeating. The
     * parent is then cloned only to hold it.
     */
    @Test
    fun recurringSubtaskPullsInAParentTaskEvenWhenTheTaskDoesNotRepeat() {
        val template = task(1, monday, repeatRule = null)
        val weeklySub = subtask(
            id = 2,
            taskId = 1,
            repeatRule = RepeatRule(
                freq = RepeatFreq.WEEKLY,
                weekDays = setOf(DayOfWeek.MONDAY),
            ),
        )

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(weeklySub),
            start = nextMonday,
            end = nextMonday,
        )

        val generatedTask = result.tasks.single { it.originTaskId == 1L }
        assertEquals(nextMonday, generatedTask.date)
        assertEquals(1, result.subtasks.count { it.originSubtaskId == 2L })
    }

    @Test
    fun nothingIsGeneratedOutsideTheRequestedRange() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))

        val result = generate(
            tasks = listOf(template),
            start = monday.plusDays(10),
            end = monday.plusDays(11),
        )

        val dates = result.tasks.filter { it.originTaskId == 1L }.mapNotNull { it.date }
        assertEquals(listOf(monday.plusDays(10), monday.plusDays(11)), dates.sorted())
    }

    @Test
    fun templateWithoutADateIsIgnored() {
        val template = task(1, date = null, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))

        val result = generate(
            tasks = listOf(template),
            start = monday,
            end = monday.plusDays(5),
        )

        assertEquals(listOf(template), result.tasks)
    }

    @Test
    fun idsAreHandedOutFromNextIdAndTheCounterAdvances() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            start = monday.plusDays(1),
            end = monday.plusDays(2),
            nextId = 500L,
        )

        val newIds = (result.tasks.map { it.id } + result.subtasks.map { it.id })
            .filter { it >= 500L }

        assertEquals(4, newIds.size) // two tasks + two subtasks
        assertTrue(newIds.all { it < result.nextId })
        assertEquals(504L, result.nextId)
    }

    @Test
    fun alreadyGeneratedOccurrenceIsNotDuplicated() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val day = monday.plusDays(1)
        val existing = task(99, day, originTaskId = 1L)

        val result = generate(
            tasks = listOf(template, existing),
            start = day,
            end = day,
        )

        assertEquals(1, result.tasks.count { it.originTaskId == 1L && it.date == day })
        assertEquals(1000L, result.nextId)
    }

    /* ---------------- a deleted day stays deleted ---------------- */

    /**
     * The bug this guard exists for.
     *
     * The task loop has always honoured the day's tombstone. The subtask loop
     * did not: it built a carrier task for the repeating subtask without ever
     * asking whether the day had been deleted. So deleting an occurrence undid
     * itself the next time that day was drawn, and no amount of deleting made
     * it stick.
     */
    @Test
    fun aDeletedDayIsNotRebuiltByARepeatingSubtask() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))
        val deletedDay = monday.plusDays(2)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            suppressed = setOf(taskSuppressionKey(1L, deletedDay)),
            start = monday.plusDays(1),
            end = monday.plusDays(4),
        )

        assertTrue(
            "the deleted day must not come back as a carrier for the subtask",
            result.tasks.none { it.originTaskId == 1L && it.date == deletedDay },
        )

        val idsOnDeletedDay = result.tasks.filter { it.date == deletedDay }.map { it.id }.toSet()
        assertTrue(
            "and nothing may be hanging off it either",
            result.subtasks.none { it.taskId in idsOnDeletedDay },
        )

        assertEquals(
            "the other three days are unaffected",
            listOf(monday.plusDays(1), monday.plusDays(3), monday.plusDays(4)),
            result.tasks.filter { it.originTaskId == 1L }.mapNotNull { it.date }.sorted(),
        )
    }

    /**
     * The same day, but the task itself does not repeat — only its subtask
     * does, so every occurrence exists purely as a carrier. Deleting one has to
     * stick here too.
     */
    @Test
    fun aDeletedDayStaysDeletedWhenOnlyTheSubtaskRepeats() {
        val template = task(1, monday)
        val sub = subtask(2, taskId = 1, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))
        val deletedDay = monday.plusDays(2)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            suppressed = setOf(taskSuppressionKey(1L, deletedDay)),
            start = monday.plusDays(1),
            end = monday.plusDays(3),
        )

        assertTrue(
            result.tasks.none { it.originTaskId == 1L && it.date == deletedDay },
        )
        assertEquals(
            listOf(monday.plusDays(1), monday.plusDays(3)),
            result.tasks.filter { it.originTaskId == 1L }.mapNotNull { it.date }.sorted(),
        )
    }

    /** The guard must not swallow days that were never deleted. */
    @Test
    fun aRepeatingSubtaskStillBuildsItsCarrierOnDaysThatWereNotDeleted() {
        val template = task(1, monday)
        val sub = subtask(2, taskId = 1, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            start = monday.plusDays(1),
            end = monday.plusDays(3),
        )

        assertEquals(3, result.tasks.count { it.originTaskId == 1L })
        assertEquals(3, result.subtasks.count { it.originSubtaskId == 2L })
    }

    /**
     * Deleting a single repeating subtask is a different thing from deleting
     * the day: the task occurrence has to survive it.
     */
    @Test
    fun deletingOnlyTheSubtaskLeavesTheTaskOccurrenceStanding() {
        val template = task(1, monday, RepeatRule(freq = RepeatFreq.DAILY))
        val sub = subtask(2, taskId = 1, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))
        val day = monday.plusDays(2)

        val result = generate(
            tasks = listOf(template),
            subtasks = listOf(sub),
            suppressed = setOf(subtaskSuppressionKey(2L, day)),
            start = monday.plusDays(1),
            end = monday.plusDays(3),
        )

        val occurrence = result.tasks.firstOrNull { it.originTaskId == 1L && it.date == day }
        assertNotNull("the day itself was not deleted", occurrence)
        assertTrue(
            "only the subtask was",
            result.subtasks.none { it.taskId == occurrence!!.id && it.originSubtaskId == 2L },
        )
    }

    /* ---------------- tombstones must not outlive their template ---------------- */

    /**
     * Ids are handed out as "one past the largest in use", recomputed from the
     * live data whenever a payload is read. A tombstone left behind by a
     * deleted template therefore lands on whatever inherits that id, and the
     * new task quietly loses those days with nothing on screen to explain it.
     */
    @Test
    fun aTombstoneWhoseTaskIsGoneIsDropped() {
        val keys = setOf(
            taskSuppressionKey(40L, monday),
            taskSuppressionKey(41L, monday),
        )

        val kept = pruneOrphanedSuppressions(
            suppressedRecurrences = keys,
            tasks = listOf(task(41L, monday, RepeatRule(freq = RepeatFreq.DAILY))),
            subtasks = emptyList(),
        )

        assertEquals(setOf(taskSuppressionKey(41L, monday)), kept)
    }

    @Test
    fun aTombstoneWhoseSubtaskIsGoneIsDropped() {
        val keys = setOf(
            subtaskSuppressionKey(2L, monday),
            subtaskSuppressionKey(3L, monday),
        )

        val kept = pruneOrphanedSuppressions(
            suppressedRecurrences = keys,
            tasks = listOf(task(1L, monday)),
            subtasks = listOf(subtask(3L, taskId = 1L, repeatRule = RepeatRule(freq = RepeatFreq.DAILY))),
        )

        assertEquals(setOf(subtaskSuppressionKey(3L, monday)), kept)
    }

    /**
     * A task that no longer repeats still owns its tombstones: its subtask may
     * be the thing that repeats, and then those days were deleted on purpose.
     */
    @Test
    fun aTombstoneOfATaskThatStillExistsIsKept() {
        val keys = setOf(taskSuppressionKey(1L, monday))

        val kept = pruneOrphanedSuppressions(
            suppressedRecurrences = keys,
            tasks = listOf(task(1L, monday)),
            subtasks = emptyList(),
        )

        assertEquals(keys, kept)
    }

    /** A payload from a newer version may hold keys this one cannot read. */
    @Test
    fun aKeyInAnUnknownShapeIsLeftAlone() {
        val keys = setOf(
            "X:1:20000",
            "T:not-a-number:20000",
            "T:1",
            "T:1:not-a-day",
            "",
        )

        assertEquals(
            keys,
            pruneOrphanedSuppressions(keys, tasks = emptyList(), subtasks = emptyList()),
        )
    }

    @Test
    fun aSetWithNothingOrphanedIsHandedBackUnchanged() {
        val keys = setOf(taskSuppressionKey(1L, monday))

        assertSame(
            keys,
            pruneOrphanedSuppressions(
                suppressedRecurrences = keys,
                tasks = listOf(task(1L, monday)),
                subtasks = emptyList(),
            ),
        )
    }

    @Test
    fun anEmptySetIsHandedBackUnchanged() {
        val keys = emptySet<String>()

        assertSame(keys, pruneOrphanedSuppressions(keys, emptyList(), emptyList()))
    }
}
