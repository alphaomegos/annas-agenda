package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.NewTaskDraftSubtask

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
