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
}
