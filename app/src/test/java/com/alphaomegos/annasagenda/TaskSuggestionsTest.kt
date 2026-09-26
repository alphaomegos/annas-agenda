package com.alphaomegos.annasagenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Offering back what the user has written before.
 *
 * The ordering rules are most of this file, because a suggestion list is only
 * worth having if the thing you meant is near the top — and because a list
 * whose order is not fully determined flickers between launches, which reads
 * as a bug and is one.
 */
class TaskSuggestionsTest {

    private val march = LocalDate.of(2026, 3, 1)
    private var nextId = 1L

    private fun task(
        description: String,
        date: LocalDate? = march,
        originTaskId: Long? = null,
        id: Long = nextId++,
    ) = Task(id = id, date = date, description = description, originTaskId = originTaskId)

    private fun sub(
        taskId: Long,
        description: String,
        order: Int = 0,
        originSubtaskId: Long? = null,
        id: Long = nextId++,
    ) = Subtask(
        id = id,
        order = order,
        taskId = taskId,
        description = description,
        originSubtaskId = originSubtaskId,
    )

    private fun suggest(
        typed: String,
        tasks: List<Task>,
        subtasks: List<Subtask> = emptyList(),
        limit: Int = 5,
    ) = taskSuggestionsFor(typed, tasks, subtasks, limit)

    /* ---------------- when to say nothing ---------------- */

    @Test
    fun oneLetterSuggestsNothing() {
        val tasks = listOf(task("Починить колесо"))

        assertTrue(suggest("П", tasks).isEmpty())
        assertTrue(suggest("", tasks).isEmpty())
        assertTrue(suggest("   ", tasks).isEmpty())
    }

    @Test
    fun somethingNeverWrittenBeforeSuggestsNothing() {
        assertTrue(suggest("баклажан", listOf(task("Починить колесо"))).isEmpty())
    }

    @Test
    fun anEmptyHistorySuggestsNothing() {
        assertTrue(suggest("почин", emptyList()).isEmpty())
    }

    /* ---------------- matching ---------------- */

    @Test
    fun theBeginningOfAWordingIsFound() {
        val tasks = listOf(task("Починить колесо"))

        assertEquals(listOf("Починить колесо"), suggest("почи", tasks).map { it.description })
    }

    @Test
    fun theMiddleOfAWordingIsFoundToo() {
        val tasks = listOf(task("Починить колесо"))

        assertEquals(listOf("Починить колесо"), suggest("колесо", tasks).map { it.description })
    }

    @Test
    fun caseAndSurroundingSpaceDoNotMatter() {
        val tasks = listOf(task("Починить Колесо"))

        assertEquals(1, suggest("  ПОЧИНИТЬ кОлЕсО  ", tasks).size)
    }

    /**
     * The user is typing from the beginning, so the beginning is what they
     * mean — even when the other wording was written more often.
     */
    @Test
    fun aWordingThatStartsWithWhatIsTypedBeatsOneThatMerelyContainsIt() {
        val tasks = listOf(
            task("Не забыть починить колесо"),
            task("Не забыть починить колесо"),
            task("Не забыть починить колесо"),
            task("Починить колесо"),
        )

        assertEquals(
            listOf("Починить колесо", "Не забыть починить колесо"),
            suggest("почин", tasks).map { it.description },
        )
    }

    /* ---------------- the repeats problem ---------------- */

    /**
     * The one that decides whether this feature is usable at all. A daily task
     * has hundreds of generated copies of itself; counting them would pin it
     * to the top of every list for ever and bury the thing written three times
     * on purpose.
     */
    @Test
    fun theHundredsOfCopiesOfARepeatDoNotCount() {
        val template = task("Зарядка")
        val copies = (1..300).map {
            task("Зарядка", date = march.plusDays(it.toLong()), originTaskId = template.id)
        }
        val written = listOf(task("Заряжать ноутбук"), task("Заряжать ноутбук"))

        val out = suggest("заря", listOf(template) + copies + written)

        assertEquals(1, out.single { it.description == "Зарядка" }.timesUsed)
        assertEquals(
            listOf("Заряжать ноутбук", "Зарядка"),
            out.map { it.description },
        )
    }

    @Test
    fun aRepeatIsStillSuggestedThroughItsTemplate() {
        val template = task("Зарядка")
        val copies = (1..5).map { task("Зарядка", originTaskId = template.id) }

        assertEquals(listOf("Зарядка"), suggest("заря", listOf(template) + copies).map { it.description })
    }

    /* ---------------- grouping and counting ---------------- */

    @Test
    fun theSameWordingWrittenTwiceIsOneSuggestion() {
        val tasks = listOf(task("Починить колесо"), task("Починить колесо"))

        val out = suggest("почин", tasks)

        assertEquals(1, out.size)
        assertEquals(2, out.single().timesUsed)
    }

    @Test
    fun theSuggestionCarriesTheDayItWasLastUsed() {
        val tasks = listOf(
            task("Починить колесо", date = LocalDate.of(2026, 1, 1)),
            task("Починить колесо", date = LocalDate.of(2026, 5, 5)),
        )

        assertEquals(LocalDate.of(2026, 5, 5), suggest("почин", tasks).single().lastUsedOn)
    }

    @Test
    fun aWordingOnlyEverWrittenWithoutADayHasNoDay() {
        val tasks = listOf(task("Починить колесо", date = null))

        assertEquals(null, suggest("почин", tasks).single().lastUsedOn)
    }

    /* ---------------- the parts ---------------- */

    /**
     * Retyping a wording is an annoyance; retyping its seven parts is why
     * people stop breaking tasks down at all.
     */
    @Test
    fun theSuggestionBringsThePartsWithIt() {
        val t = task("Починить колесо")
        val subs = listOf(
            sub(t.id, "Снять колесо", order = 0),
            sub(t.id, "Заклеить камеру", order = 1),
            sub(t.id, "Поставить обратно", order = 2),
        )

        assertEquals(
            listOf("Снять колесо", "Заклеить камеру", "Поставить обратно"),
            suggest("почин", listOf(t), subs).single().subtaskDescriptions,
        )
    }

    @Test
    fun thePartsComeBackInTheirOwnOrderNotTheOrderTheyWereStoredIn() {
        val t = task("Починить колесо")
        val subs = listOf(
            sub(t.id, "Поставить обратно", order = 2),
            sub(t.id, "Снять колесо", order = 0),
            sub(t.id, "Заклеить камеру", order = 1),
        )

        assertEquals(
            listOf("Снять колесо", "Заклеить камеру", "Поставить обратно"),
            suggest("почин", listOf(t), subs).single().subtaskDescriptions,
        )
    }

    /**
     * The most recent writing of it is the one whose parts are offered — that
     * is the version the user last thought was right.
     */
    @Test
    fun thePartsComeFromTheMostRecentWritingOfIt() {
        val old = task("Починить колесо", date = LocalDate.of(2026, 1, 1))
        val recent = task("Починить колесо", date = LocalDate.of(2026, 5, 5))
        val subs = listOf(
            sub(old.id, "Отнести в мастерскую"),
            sub(recent.id, "Снять колесо", order = 0),
            sub(recent.id, "Заклеить камеру", order = 1),
        )

        assertEquals(
            listOf("Снять колесо", "Заклеить камеру"),
            suggest("почин", listOf(old, recent), subs).single().subtaskDescriptions,
        )
    }

    @Test
    fun aWordingWithNoPartsBringsNone() {
        assertTrue(suggest("почин", listOf(task("Починить колесо"))).single().subtaskDescriptions.isEmpty())
    }

    @Test
    fun theGeneratedCopiesOfARepeatingPartAreNotOffered() {
        val t = task("Починить колесо")
        val template = sub(t.id, "Снять колесо", order = 0)
        val copies = (1..4).map { sub(t.id, "Снять колесо", order = 0, originSubtaskId = template.id) }

        assertEquals(
            listOf("Снять колесо"),
            suggest("почин", listOf(t), listOf(template) + copies).single().subtaskDescriptions,
        )
    }

    @Test
    fun anotherTasksPartsAreNotBorrowed() {
        val wheel = task("Починить колесо")
        val other = task("Починить кран")
        val subs = listOf(sub(other.id, "Купить прокладку"))

        assertTrue(
            suggest("починить колесо", listOf(wheel, other), subs).single().subtaskDescriptions.isEmpty()
        )
    }

    /* ---------------- ordering and size ---------------- */

    @Test
    fun theMoreOftenWrittenComesFirst() {
        val tasks = listOf(
            task("Почистить зубы"),
            task("Починить колесо"),
            task("Починить колесо"),
        )

        assertEquals(
            listOf("Починить колесо", "Почистить зубы"),
            suggest("почи", tasks).map { it.description },
        )
    }

    @Test
    fun equallyOftenWrittenTheMoreRecentComesFirst() {
        val tasks = listOf(
            task("Почистить зубы", date = LocalDate.of(2026, 1, 1)),
            task("Починить колесо", date = LocalDate.of(2026, 5, 5)),
        )

        assertEquals(
            listOf("Починить колесо", "Почистить зубы"),
            suggest("почи", tasks).map { it.description },
        )
    }

    /**
     * Not a preference — determinism. Without a final tie-break two equal
     * wordings swap places between launches and the list flickers.
     */
    @Test
    fun aCompleteTieIsBrokenAlphabeticallyAndStaysThatWay() {
        val tasks = listOf(task("Почистить зубы"), task("Почистить ботинки"))

        val once = suggest("почи", tasks).map { it.description }
        val again = suggest("почи", tasks.reversed()).map { it.description }

        assertEquals(listOf("Почистить ботинки", "Почистить зубы"), once)
        assertEquals(once, again)
    }

    @Test
    fun theListIsCutToTheLimit() {
        val tasks = (1..20).map { task("Починить вещь $it") }

        assertEquals(5, suggest("почин", tasks).size)
        assertEquals(3, suggest("почин", tasks, limit = 3).size)
    }

    @Test
    fun aBlankWordingIsNeverSuggested() {
        val tasks = listOf(task("   "), task("Починить колесо"))

        assertEquals(listOf("Починить колесо"), suggest("  ", tasks).map { it.description } +
            suggest("почин", tasks).map { it.description })
    }
}
