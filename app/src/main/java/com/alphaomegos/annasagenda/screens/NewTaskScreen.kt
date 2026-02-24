package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.components.ColorPickerRow
import com.alphaomegos.annasagenda.components.ColorDot
import com.alphaomegos.annasagenda.components.nextPaletteColor
import com.alphaomegos.annasagenda.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.alphaomegos.annasagenda.NewTaskDraft
import com.alphaomegos.annasagenda.NewTaskDraftSubtask
import kotlinx.coroutines.flow.distinctUntilChanged


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(
    vm: AppViewModel,
    preselectedEpochDay: Long? = null,
    onBack: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    val maxSubtasks = 30
    val subtasksText = remember { mutableStateListOf<String>() }
    val subtasksColor = remember { mutableStateListOf<Long?>() }
// Track whether user manually changed a draft subtask color (to avoid overwriting on task color change)
    val subtasksColorOverridden = remember { mutableStateListOf<Boolean>() }
    val initialDate: LocalDate? = remember(preselectedEpochDay) {
        preselectedEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    }
    var selectedDate by rememberSaveable { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var taskColor by rememberSaveable { mutableStateOf<Long?>(null) }

    var draftLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val draft = vm.loadNewTaskDraft()
        val canApply = description.isBlank() && subtasksText.isEmpty() && taskColor == null

        if (draft != null && canApply) {
            description = draft.description
            taskColor = draft.taskColorArgb

            subtasksText.clear()
            subtasksColor.clear()
            subtasksColorOverridden.clear()

            draft.subtasks.take(maxSubtasks).forEach { s ->
                subtasksText.add(s.description)
                subtasksColor.add(s.colorArgb)
                subtasksColorOverridden.add(s.colorOverridden)
            }
        }

        draftLoaded = true
    }

    LaunchedEffect(draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect

        snapshotFlow {
            NewTaskDraft(
                description = description,
                taskColorArgb = taskColor,
                subtasks = subtasksText.indices.map { i ->
                    NewTaskDraftSubtask(
                        description = subtasksText[i],
                        colorArgb = subtasksColor.getOrNull(i),
                        colorOverridden = subtasksColorOverridden.getOrNull(i) ?: false
                    )
                }
            )
        }
            .distinctUntilChanged()
            .collect { vm.queueNewTaskDraftSave(it) }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.new_task_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.when_label), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedDate = null },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.create_no_date)) }

                Button(
                    onClick = { selectedDate = LocalDate.now() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.create_today)) }

                Button(
                    onClick = { selectedDate = LocalDate.now().plusDays(1) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.create_tomorrow)) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pick_date))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.selected_label)} ${
                    selectedDate?.toString() ?: stringResource(
                        R.string.someday_tag
                    )
                }",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.task_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.task_color), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ColorPickerRow(
                selected = taskColor,
                onSelect = { newColor ->
                    taskColor = newColor
                    // Apply the task color to draft subtasks unless the user manually changed that subtask's color.
                    for (i in subtasksColor.indices) {
                        if (i < subtasksColorOverridden.size && !subtasksColorOverridden[i]) {
                            subtasksColor[i] = newColor
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.subtasks_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${subtasksText.size}/$maxSubtasks",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (subtasksText.isEmpty()) {
                Text(stringResource(R.string.no_subtasks_yet))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(subtasksText.size) { i ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(
                                colorArgb = subtasksColor[i],
                                onClick = {
                                    // Cycle color for THIS draft subtask
                                    subtasksColor[i] = nextPaletteColor(subtasksColor[i])
                                    subtasksColorOverridden[i] = true
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = subtasksText[i],
                                onValueChange = { subtasksText[i] = it },
                                label = { Text(stringResource(R.string.subtask_label, i + 1)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                subtasksText.removeAt(i)
                                subtasksColor.removeAt(i)
                                subtasksColorOverridden.removeAt(i)
                            }) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (subtasksText.size < maxSubtasks) {
                            subtasksText.add("")
                            subtasksColor.add(taskColor)           // inherit task color by default
                            subtasksColorOverridden.add(false)     // not overridden yet
                        }
                    },
                    enabled = subtasksText.size < maxSubtasks,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.add_subtask))
                }

                Button(
                    onClick = {
                        val cleanSubtasks = subtasksText.map { it.trim() }
                        val taskId = vm.createTaskForDate(
                            date = selectedDate,
                            time = null,
                            description = description.trim(),
                            colorArgb = taskColor,
                            hasSubtasks = cleanSubtasks.any { it.isNotBlank() }
                        )
                        cleanSubtasks.forEachIndexed { idx, txt ->
                            if (txt.isNotBlank()) {
                                val subColor = subtasksColor.getOrNull(idx) ?: taskColor
                                vm.createSubtask(taskId, txt, colorArgb = subColor)
                            }
                        }
                        vm.clearNewTaskDraft()
                        onBack()
                    },
                    enabled = description.trim().isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_task))
                }
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.back))
        }


        if (showDatePicker) {
            val zone = remember { ZoneId.systemDefault() }
            val initialMillis = remember(selectedDate) {
                val d = selectedDate ?: LocalDate.now()
                d.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            selectedDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectedDate = null
                            showDatePicker = false
                        }) { Text(stringResource(R.string.create_no_date)) }

                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}