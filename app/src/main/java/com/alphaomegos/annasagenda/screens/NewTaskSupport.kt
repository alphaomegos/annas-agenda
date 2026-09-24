package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.NewTaskDraftSubtask
import java.time.LocalDate

data class EditableNewTaskSubtask(
    val description: String = "",
    val colorArgb: Long? = null,
    val colorOverridden: Boolean = false,
)

fun draftSubtasksToEditable(
    subtasks: List<NewTaskDraftSubtask>,
    maxSubtasks: Int,
): List<EditableNewTaskSubtask> {
    return subtasks
        .take(maxSubtasks)
        .map { s ->
            EditableNewTaskSubtask(
                description = s.description,
                colorArgb = s.colorArgb,
                colorOverridden = s.colorOverridden,
            )
        }
}

fun editableSubtasksToDraft(
    subtasks: List<EditableNewTaskSubtask>,
): List<NewTaskDraftSubtask> {
    return subtasks.map { s ->
        NewTaskDraftSubtask(
            description = s.description,
            colorArgb = s.colorArgb,
            colorOverridden = s.colorOverridden,
        )
    }
}

fun applyTaskColorToNonOverriddenSubtasks(
    subtasks: List<EditableNewTaskSubtask>,
    taskColor: Long?,
): List<EditableNewTaskSubtask> {
    return subtasks.map { s ->
        if (s.colorOverridden) s else s.copy(colorArgb = taskColor)
    }
}

fun newEditableSubtask(defaultColor: Long?): EditableNewTaskSubtask {
    return EditableNewTaskSubtask(
        description = "",
        colorArgb = defaultColor,
        colorOverridden = false,
    )
}

/**
 * The half-typed subtask list, flattened for saved instance state.
 *
 * Saved state travels in a Bundle, which will not take a list of arbitrary
 * objects, so each row becomes the three values it actually holds. Strings
 * throughout: a missing colour is an empty string rather than a null, because
 * the values a Saver hands back have to be non-null.
 *
 * This is the half worth testing. Getting it wrong does not fail to compile —
 * it loses what the user typed, and only when they turn the phone.
 */
fun editableSubtasksToSavedStrings(subtasks: List<EditableNewTaskSubtask>): List<String> =
    subtasks.flatMap { row ->
        listOf(
            row.description,
            row.colorArgb?.toString().orEmpty(),
            if (row.colorOverridden) "1" else "0",
        )
    }

fun editableSubtasksFromSavedStrings(flat: List<String>): List<EditableNewTaskSubtask> =
    flat.chunked(3)
        .filter { it.size == 3 }
        .map { row ->
            EditableNewTaskSubtask(
                description = row[0],
                colorArgb = row[1].toLongOrNull(),
                colorOverridden = row[2] == "1",
            )
        }

/**
 * Which day the new-task screen opens on.
 *
 * Three cases, and they used to be squeezed into one nullable Long: a day the
 * user tapped in the calendar, "today" when the screen is opened from the main
 * menu, and "no day at all" when it is opened from Someday.
 *
 * The third was signalled by passing a negative epoch day, which is a real
 * date — every day before 1970 is negative. Tapping one of those in the
 * calendar opened the screen with no date instead of that date. The Someday
 * route has always been a route of its own, so it can simply say so.
 */
fun newTaskInitialDate(
    preselectedEpochDay: Long?,
    startWithoutDate: Boolean,
    today: LocalDate,
): LocalDate? = when {
    startWithoutDate -> null
    preselectedEpochDay == null -> today
    else -> LocalDate.ofEpochDay(preselectedEpochDay)
}
