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
}
