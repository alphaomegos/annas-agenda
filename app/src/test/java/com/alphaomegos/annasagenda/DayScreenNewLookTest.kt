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
        assertTrue(rows.hasCode("val next = nextPaletteColor(task.colorArgb)"))
        assertTrue(rows.hasCode(".background(next)"))
    }

    @Test
    fun movingAndCopyingAreUnderTheMenu_upAndDownStayOnTheRow() {
        val newRow = rows.substringAfter("internal fun NewDateTaskRow(")
        assertTrue(newRow.hasCode("DropdownMenu(") && newRow.hasCode("R.string.move_task") && newRow.hasCode("R.string.copy_task"))
        assertTrue(newRow.hasCode("R.string.move_task_up") && newRow.hasCode("R.string.move_task_down"))
    }

    @Test
    fun theOldLookIsTheOldRow() {
        assertTrue(rows.hasCode("DateTaskRow(task, subtasks, isExpanded,"))
        assertTrue(block.hasCode("TaskListSurface(newLook)"))
    }

    /** Subtasks too (0322): in the same surface, set in, the same menu and strip as a task's. */
    @Test
    fun subtasksFollowTheNewLook() {
        val sub = rows.substringAfter("internal fun NewDateSubtaskRow(")
        assertTrue(sub.hasCode("val next = nextPaletteColor(subtask.colorArgb)") && sub.hasCode("DropdownMenu("))
        assertTrue(block.hasCode("SubtaskRowCall(") && block.hasCode("newLook = newLook,"))
    }

    /**
     * The look reaches the loop (0323): 0321 gave the parameter to DateTasksBlock
     * but the rows are drawn in DateTasksBlockContent, which never had it.
     */
    @Test
    fun theLookIsPassedToWhereTheRowsAreDrawn() {
        assertTrue(block.hasCode("    newLook: Boolean = false,\n) {") )
        assertTrue(block.hasCode("        newLook = newLook,\n    )"))
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
        assertTrue(head.hasCode("TopBarBack(onBack)") && head.hasCode("WayHomeButton()"))
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
        assertTrue(!newDialog.hasCode("R.string.task_description_label"))
        assertTrue(newDialog.hasCode("colors = frameless()"))
        assertTrue(dialogs.hasCode("unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent"))
        assertTrue(newDialog.hasCode("Icons.Default.Schedule") && newDialog.hasCode("Icons.Default.Close"))
        for (label in listOf("R.string.repeat", "R.string.task_menu_counter", "R.string.task_tool_copy", "R.string.task_tool_move", "R.string.remove")) {
            assertTrue(label, newDialog.contains(label))
        }
    }

    /** Subtasks too (0326): the dialog without title or frame, its tools as icons; the row's ⋮ repeats and deletes. */
    @Test
    fun subtasksGetTheSameDialogAndMenu() {
        val dialogs = mainSourceFiles().single { it.name == "DateTaskEditDialogs.kt" }.readText()
        val sub = dialogs.substringAfter("internal fun NewEditSubtaskDialog(").substringBefore("internal fun SubtaskDialogCall(")
        assertTrue(!sub.hasCode("R.string.task_description_label") && sub.hasCode("colors = frameless()"))
        for (label in listOf("R.string.repeat", "R.string.task_tool_copy", "R.string.task_tool_move", "R.string.remove")) assertTrue(label, sub.contains(label))
        val subRow = rows.substringAfter("internal fun NewDateSubtaskRow(")
        assertTrue(subRow.hasCode("R.string.repeat") && subRow.hasCode("R.string.remove"))
    }

    /** «Someday» follows the day screen's look (0326): Back and the house on top, the rows in the surface. */
    @Test
    fun somedayFollowsTheLook() {
        val someday = mainSourceFiles().single { it.name == "SomedayScreen.kt" }.readText()
        assertTrue(someday.hasCode("DateTasksBlock(vm = vm, state = state, date = null, newLook = newLook)"))
        assertTrue(someday.hasCode("TopBarBack(onBack)") && someday.hasCode("WayHomeButton()"))
    }

    /** Mini buttons have no card round them (0326), on a narrow screen and a wide one. */
    @Test
    fun miniButtonsAreBare() {
        val tile = mainSourceFiles().single { it.name == "MenuTile.kt" }.readText()
        assertTrue(tile.hasCode("if (!titled) bareColors"))
        val menu = mainSourceFiles().single { it.name == "MainMenuScreen.kt" }.readText()
        assertTrue(menu.hasCode("val miniButtons = narrowStyle == MainMenuNarrowStyle.ICONS"))
    }

    /**
     * Anthropometry in the new look (0329): «+» in the bar instead of the wide
     * button, one line a measurement, the chart filled; and its labels no longer
     * overlap, in either look.
     */
    @Test
    fun anthropometryFollowsTheLook() {
        val a = anthropometrySource()
        assertTrue(a.hasCode("filled = state.newLook,"))
        assertTrue(a.hasCode("if (state.newLook) NewAnthropometryEntryRow("))
        assertTrue(a.hasCode("if (!state.newLook) {\n                Button("))
        assertTrue("the last date is placed by its width", !a.hasCode("plotRight - 72f"))
    }

    /** The ranges fit a folded Fold in one strip (0331); the anthropometry chart picks a day as the steps chart does. */
    @Test
    fun rangesFitAndTheChartPicksADay() {
        val bar = mainSourceFiles().single { it.name == "AnthropometryRangeBar.kt" }.readText()
        assertTrue(bar.hasCode("compact: Boolean = false") && bar.hasCode(".weight(1f)"))
        val a = anthropometrySource()
        assertTrue(a.hasCode("compact = state.newLook,"))
        assertTrue(a.hasCode("detectDragGesturesAfterLongPress(") && a.hasCode("picked?.let { day ->"))
    }

    /** «Undone» and the repeating tasks follow the look (0334). */
    @Test
    fun undoneAndRecurringFollowTheLook() {
        val undone = mainSourceFiles().single { it.name == "UndoneTasksScreen.kt" }.readText()
        assertTrue(undone.hasCode("newLook = look == DayScreenStyle.NEW,"))
        val rec = mainSourceFiles().single { it.name == "RecurringTasksScreen.kt" }.readText()
        assertTrue(rec.hasCode("TopBarBack(onBack)") && rec.hasCode("if (!newLook) Button(onClick = onBack"))
        assertTrue(rec.hasCode("Icons.Default.Delete"))
    }

    /** A notification about tasks opens their day, not the main menu (0343). */
    @Test
    fun aTaskNotificationOpensTheDay() {
        val n = mainSourceFiles().single { it.name == "Notifications.kt" }.readText()
        assertTrue(n.hasCode("day?.let { launch.putExtra(EXTRA_OPEN_EPOCH_DAY, it.toEpochDay()) }"))
        assertTrue(Regex("""openAppIntent\(context, java\.time\.LocalDate\.now\(\)\)""").findAll(n).count() == 2)
    }

    /** The month in the new look (0346): Back and the month on top, ‹ Today ›, no cell frames, no foot. */
    @Test
    fun theMonthFollowsTheLook() {
        val m = mainSourceFiles().single { it.name == "CalendarMonthScreen.kt" }.readText()
        assertTrue(m.hasCode("TopBarBack(onBack)") && m.hasCode("TextButton(onClick = { monthIndex = baseIndex })"))
        assertTrue(m.hasCode(".then(if (newLook) Modifier else Modifier.border(1.dp, cellBorderColor, cellShape))"))
        assertTrue(m.hasCode("if (!newLook) Button("))
    }

    /** «On the run», between marathons, new look (0347): «+» in the bar, no wide button, flat cards. */
    @Test
    fun betweenMarathonsFollowsTheLook() {
        val w = mainSourceFiles().single { it.name == "RunningWorkoutsContent.kt" }.readText()
        assertTrue(w.hasCode("if (!newLook) Button(") && w.hasCode("flat = newLook,"))
        val r = mainSourceFiles().single { it.name == "RunningPlanScreen.kt" }.readText()
        assertTrue(r.hasCode("IconButton(onClick = { workoutAddRequest += 1 })"))
    }

    /** Anthropometry's weight card (0350): beside the chart when wide, under it when not; not twice. */
    @Test
    fun theWeightHasItsCard() {
        val a = anthropometrySource()
        assertTrue(a.hasCode("val wideCard = state.newLook && LocalConfiguration.current.screenWidthDp >= 600"))
        assertTrue(a.hasCode("if (state.newLook && s.axis == AnthropometryAxis.KG) return@items"))
        assertTrue(Regex("""WeightCard\(allEntries""").findAll(a).count() == 2)
    }

    // The screen and its parts (0353: split in two files); a guard reads them as one.
    private fun anthropometrySource(): String =
        mainSourceFiles().filter { it.name == "AnthropometryScreen.kt" || it.name == "AnthropometryParts.kt" }.joinToString("\n") { it.readText() }
}
