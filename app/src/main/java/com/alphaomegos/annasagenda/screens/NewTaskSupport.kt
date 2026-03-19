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