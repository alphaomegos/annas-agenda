package com.alphaomegos.annasagenda.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The half-written task the new-task screen is holding.
 *
 * The subtask rows used to live in a plain `remember`, so turning the phone
 * emptied them — and the draft written a moment later is built from the
 * description and the subtasks together, so the saved draft was replaced by a
 * copy of itself with no subtasks in it. Gone from the screen and from disk at
 * the same time, with the description still sitting there to prove nothing had
 * happened.
 *
 * Saved state travels in a Bundle, so the rows are flattened into strings.
 * That flattening is the part that can silently lose something.
 */
class NewTaskSupportTest {

    @Test
    fun aSubtaskListSurvivesBeingFlattenedAndRebuilt() {
        val rows = listOf(
            EditableNewTaskSubtask(description = "Buy milk", colorArgb = 0xFF00FF00, colorOverridden = true),
            EditableNewTaskSubtask(description = "", colorArgb = null, colorOverridden = false),
            EditableNewTaskSubtask(description = "Долго и по-русски", colorArgb = 0xFFFF0000, colorOverridden = false),
        )

        assertEquals(rows, editableSubtasksFromSavedStrings(editableSubtasksToSavedStrings(rows)))
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList<String>(), editableSubtasksToSavedStrings(emptyList()))
        assertEquals(emptyList<EditableNewTaskSubtask>(), editableSubtasksFromSavedStrings(emptyList()))
    }

    @Test
    fun aRowWithNoColourComesBackWithNoColour() {
        val row = EditableNewTaskSubtask(description = "No colour", colorArgb = null)

        val restored = editableSubtasksFromSavedStrings(
            editableSubtasksToSavedStrings(listOf(row))
        ).single()

        assertNull(restored.colorArgb)
        assertEquals("No colour", restored.description)
    }

    /**
     * A colour chosen by hand is not the same as one inherited from the task:
     * the inherited one follows the task colour when it changes, the chosen one
     * does not. Losing that flag would quietly repaint the row later.
     */
    @Test
    fun aColourChosenByHandStaysChosenByHand() {
        val rows = listOf(
            EditableNewTaskSubtask(description = "inherited", colorArgb = 1L, colorOverridden = false),
            EditableNewTaskSubtask(description = "chosen", colorArgb = 1L, colorOverridden = true),
        )

        val restored = editableSubtasksFromSavedStrings(editableSubtasksToSavedStrings(rows))

        assertEquals(listOf(false, true), restored.map { it.colorOverridden })

        // And the distinction still holds after the task colour is applied.
        val repainted = applyTaskColorToNonOverriddenSubtasks(restored, taskColor = 99L)
        assertEquals(listOf(99L, 1L), repainted.map { it.colorArgb })
    }

    @Test
    fun aTruncatedSaveIsIgnoredRatherThanRestoredWrong() {
        // Three values per row; anything left over is not a row.
        val flat = listOf("Buy milk", "", "0", "half a row")

        val restored = editableSubtasksFromSavedStrings(flat)

        assertEquals(1, restored.size)
        assertEquals("Buy milk", restored.single().description)
    }

    // -- which day the screen opens on ---------------------------------------

    private val today = java.time.LocalDate.of(2026, 3, 23)

    @Test
    fun openedFromTheMainMenuTheScreenOpensOnToday() {
        assertEquals(
            today,
            newTaskInitialDate(preselectedEpochDay = null, startWithoutDate = false, today = today),
        )
    }

    @Test
    fun openedFromADayTheScreenOpensOnThatDay() {
        val picked = java.time.LocalDate.of(2026, 7, 1)

        assertEquals(
            picked,
            newTaskInitialDate(
                preselectedEpochDay = picked.toEpochDay(),
                startWithoutDate = false,
                today = today,
            ),
        )
    }

    @Test
    fun openedFromSomedayTheScreenOpensWithNoDay() {
        assertNull(
            newTaskInitialDate(preselectedEpochDay = null, startWithoutDate = true, today = today),
        )
    }

    /**
     * Every day before 1970 has a negative epoch day, and "no date" used to be
     * signalled by passing a negative one. Tapping the last day of 1969 in the
     * calendar therefore opened the screen with no date at all.
     */
    @Test
    fun aDayBeforeNineteenSeventyIsADayLikeAnyOther() {
        val newYearsEve1969 = java.time.LocalDate.of(1969, 12, 31)
        assertEquals(-1L, newYearsEve1969.toEpochDay())

        assertEquals(
            newYearsEve1969,
            newTaskInitialDate(
                preselectedEpochDay = newYearsEve1969.toEpochDay(),
                startWithoutDate = false,
                today = today,
            ),
        )
    }

    /* ---------------- picking a suggestion ---------------- */

    private fun row(description: String, color: Long? = null) =
        EditableNewTaskSubtask(description = description, colorArgb = color)

    private fun apply(
        current: List<EditableNewTaskSubtask>,
        suggested: List<String>,
        max: Int = 30,
        color: Long? = null,
    ) = subtasksAfterApplyingSuggestion(current, suggested, max, color)

    @Test
    fun aSuggestionFillsAnEmptyListWithItsParts() {
        val after = apply(emptyList(), listOf("Снять колесо", "Заклеить камеру"))

        assertEquals(listOf("Снять колесо", "Заклеить камеру"), after.map { it.description })
    }

    /**
     * The rule the rest of this screen is built on: the draft is sacred. 0043
     * is what it cost when rotation dropped the parts and the autosave wrote
     * the emptier draft over the real one.
     */
    @Test
    fun nothingAlreadyTypedIsThrownAway() {
        val after = apply(listOf(row("Купить клей")), listOf("Снять колесо"))

        assertEquals(listOf("Купить клей", "Снять колесо"), after.map { it.description })
    }

    @Test
    fun whatWasTypedKeepsItsOwnColour() {
        val after = apply(listOf(row("Купить клей", color = 0xFF00FF00)), listOf("Снять колесо"), color = 0xFF0000FF)

        assertEquals(0xFF00FF00, after.first().colorArgb)
        assertEquals(0xFF0000FF, after.last().colorArgb)
    }

    /**
     * Tapping "add a part" and then picking a suggestion is one gesture in the
     * user's head. Leaving the empty row above the suggested ones makes it two.
     */
    @Test
    fun theEmptyRowWaitingToBeFilledInIsDropped() {
        val after = apply(listOf(row("Купить клей"), row("")), listOf("Снять колесо"))

        assertEquals(listOf("Купить клей", "Снять колесо"), after.map { it.description })
    }

    @Test
    fun aPartAlreadyInTheListIsNotAddedTwice() {
        val after = apply(listOf(row("  снять КОЛЕСО ")), listOf("Снять колесо", "Заклеить камеру"))

        assertEquals(listOf("  снять КОЛЕСО ", "Заклеить камеру"), after.map { it.description })
    }

    @Test
    fun aSuggestionThatRepeatsItselfStillAddsOne() {
        val after = apply(emptyList(), listOf("Снять колесо", "снять колесо"))

        assertEquals(listOf("Снять колесо"), after.map { it.description })
    }

    @Test
    fun theListNeverGrowsPastTheLimit() {
        val current = (1..28).map { row("Часть $it") }

        val after = apply(current, listOf("Снять колесо", "Заклеить камеру", "Поставить обратно"), max = 30)

        assertEquals(30, after.size)
        assertEquals("Заклеить камеру", after.last().description)
    }

    @Test
    fun aSuggestionWithNoPartsChangesNothingButTidiesTheBlanks() {
        val after = apply(listOf(row("Купить клей"), row("  ")), emptyList())

        assertEquals(listOf("Купить клей"), after.map { it.description })
    }
}
