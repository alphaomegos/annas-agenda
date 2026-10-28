package com.alphaomegos.annasagenda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day screen's new look (0320–0321): the rows the brief asked for, kept
 * from sliding back — the strip shows the next colour, the rarer actions sit
 * under ⋮, and the old look stays exactly as it was.
 */
class DayScreenNewLookTest {
    private val rows by lazy { mainSourceFiles().single { it.name == "DateTaskRows.kt" }.readText() }
    private val block by lazy { mainSourceFiles().single { it.name == "DateTasksRoute.kt" }.readText() }

    @Test
    fun theStripShowsTheNextColour() {
        assertTrue(rows.contains("val next = nextPaletteColor(task.colorArgb)"))
        assertTrue(rows.contains(".background(next)"))
    }

    @Test
    fun movingAndCopyingAreUnderTheMenu_upAndDownStayOnTheRow() {
        val newRow = rows.substringAfter("internal fun NewDateTaskRow(")
        assertTrue(newRow.contains("DropdownMenu(") && newRow.contains("R.string.move_task") && newRow.contains("R.string.copy_task"))
        assertTrue(newRow.contains("R.string.move_task_up") && newRow.contains("R.string.move_task_down"))
    }

    @Test
    fun theOldLookIsTheOldRow() {
        assertTrue(rows.contains("DateTaskRow(task, subtasks, isExpanded,"))
        assertTrue(block.contains("TaskListSurface(newLook)"))
    }

    /** Subtasks too (0322): in the same surface, set in, the same menu and strip as a task's. */
    @Test
    fun subtasksFollowTheNewLook() {
        val sub = rows.substringAfter("internal fun NewDateSubtaskRow(")
        assertTrue(sub.contains("val next = nextPaletteColor(subtask.colorArgb)") && sub.contains("DropdownMenu("))
        assertTrue(block.contains("SubtaskRowCall(") && block.contains("newLook = newLook,"))
    }

    /**
     * The look reaches the loop (0323): 0321 gave the parameter to DateTasksBlock
     * but the rows are drawn in DateTasksBlockContent, which never had it.
     */
    @Test
    fun theLookIsPassedToWhereTheRowsAreDrawn() {
        assertTrue(block.contains("    newLook: Boolean = false,\n) {") )
        assertTrue(block.contains("        newLook = newLook,\n    )"))
        val content = block.substringAfter("private fun DateTasksBlockContent(").substringBefore(") {")
        assertTrue("DateTasksBlockContent has no newLook", content.contains("newLook: Boolean"))
    }

    /** Every language spells twelve months after a day's number (0324). */
    @Test
    fun twelveMonthsInEveryLanguage() {
        for (dir in listOf("values", "values-ru", "values-b+sr+Latn")) {
            val xml = mainResRoot().resolve(dir).listFiles().orEmpty().filter { it.name.endsWith(".xml") }.joinToString("") { it.readText() }
            val list = Regex("""<string name="months_after_day">([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            assertTrue("$dir has no months_after_day", list != null)
            assertTrue("$dir: ${list!!.split('|').size} months", list.split('|').size == 12)
        }
    }

    /** Back and the house are at the top in the new look; the foot is left to the tasks (0324). */
    @Test
    fun backAndHomeAreAtTheTop() {
        val day = mainSourceFiles().single { it.name == "CalendarDayScreen.kt" }.readText()
        val head = day.substringAfter("private fun NewDayHeader(")
        assertTrue(head.contains("TopBarBack(onBack)") && head.contains("WayHomeButton()"))
    }

    /** The row's ⋮ also repeats, links a counter and deletes (0325), as the task's dialog does. */
    @Test
    fun theRowMenuHasEverythingTheDialogHas() {
        val newRow = rows.substringAfter("internal fun NewDateTaskRow(").substringBefore("internal fun SubtaskRowCall(")
        for (label in listOf("R.string.repeat", "R.string.task_menu_counter", "R.string.remove")) assertTrue(label, newRow.contains(label))
    }

    /** The task's dialog in the new look (0325): its title once, no frame round the text, the time as a clock. */
    @Test
    fun theTaskDialogIsLighter() {
        val dialogs = mainSourceFiles().single { it.name == "DateTaskEditDialogs.kt" }.readText()
        val newDialog = dialogs.substringAfter("internal fun NewEditTaskDialog(").substringBefore("internal fun TaskDialogCall(")
        // No title at all now (0326): the task's words head the dialog.
        assertTrue(!newDialog.contains("R.string.task_description_label"))
        assertTrue(newDialog.contains("colors = frameless()"))
        assertTrue(dialogs.contains("unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent"))
        assertTrue(newDialog.contains("Icons.Default.Schedule") && newDialog.contains("Icons.Default.Close"))
        for (label in listOf("R.string.repeat", "R.string.task_menu_counter", "R.string.task_tool_copy", "R.string.task_tool_move", "R.string.remove")) {
            assertTrue(label, newDialog.contains(label))
        }
    }

    /** Subtasks too (0326): the dialog without title or frame, its tools as icons; the row's ⋮ repeats and deletes. */
    @Test
    fun subtasksGetTheSameDialogAndMenu() {
        val dialogs = mainSourceFiles().single { it.name == "DateTaskEditDialogs.kt" }.readText()
        val sub = dialogs.substringAfter("internal fun NewEditSubtaskDialog(").substringBefore("internal fun SubtaskDialogCall(")
        assertTrue(!sub.contains("R.string.task_description_label") && sub.contains("colors = frameless()"))
        for (label in listOf("R.string.repeat", "R.string.task_tool_copy", "R.string.task_tool_move", "R.string.remove")) assertTrue(label, sub.contains(label))
        val subRow = rows.substringAfter("internal fun NewDateSubtaskRow(")
        assertTrue(subRow.contains("R.string.repeat") && subRow.contains("R.string.remove"))
    }

    /** «Someday» follows the day screen's look (0326): Back and the house on top, the rows in the surface. */
    @Test
    fun somedayFollowsTheLook() {
        val someday = mainSourceFiles().single { it.name == "SomedayScreen.kt" }.readText()
        assertTrue(someday.contains("DateTasksBlock(vm = vm, state = state, date = null, newLook = newLook)"))
        assertTrue(someday.contains("TopBarBack(onBack)") && someday.contains("WayHomeButton()"))
    }

    /** Mini buttons have no card round them (0326), on a narrow screen and a wide one. */
    @Test
    fun miniButtonsAreBare() {
        val tile = mainSourceFiles().single { it.name == "MenuTile.kt" }.readText()
        assertTrue(tile.contains("if (!titled) bareColors"))
        val menu = mainSourceFiles().single { it.name == "MainMenuScreen.kt" }.readText()
        assertTrue(menu.contains("val miniButtons = narrowStyle == MainMenuNarrowStyle.ICONS"))
    }

    /**
     * Anthropometry in the new look (0329): «+» in the bar instead of the wide
     * button, one line a measurement, the chart filled; and its labels no longer
     * overlap, in either look.
     */
    @Test
    fun anthropometryFollowsTheLook() {
        val a = mainSourceFiles().single { it.name == "AnthropometryScreen.kt" }.readText()
        assertTrue(a.contains("filled = state.newLook,"))
        assertTrue(a.contains("if (state.newLook) NewAnthropometryEntryRow("))
        assertTrue(a.contains("if (!state.newLook) {\n                Button("))
        assertTrue("the last date is placed by its width", !a.contains("plotRight - 72f"))
    }
}
