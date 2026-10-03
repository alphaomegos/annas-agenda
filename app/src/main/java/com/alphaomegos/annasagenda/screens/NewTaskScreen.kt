package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import com.alphaomegos.annasagenda.util.appLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.components.ColorPickerRow
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.components.PickTimeDialog
import com.alphaomegos.annasagenda.formatTaskTime
import com.alphaomegos.annasagenda.minuteOfDay
import com.alphaomegos.annasagenda.timeFromMinuteOfDay
import com.alphaomegos.annasagenda.components.ColorDot
import com.alphaomegos.annasagenda.components.nextPaletteColor
import com.alphaomegos.annasagenda.R
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.alphaomegos.annasagenda.NewTaskDraft
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(
    vm: AppViewModel,
    preselectedEpochDay: Long? = null,
    startWithoutDate: Boolean = false,
    onBack: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    val maxSubtasks = 30

    // Saveable, and not as a nicety. The description already survived rotation
    // while the subtasks did not, and the draft that gets written a moment
    // later is built from both — so turning the phone replaced a saved draft
    // with a copy of itself that had no subtasks in it, and the typed ones were
    // gone from the screen and from disk at once.
    val subtasks = rememberSaveable(saver = editableSubtasksSaver) {
        mutableStateListOf<EditableNewTaskSubtask>()
    }

    val initialDate: LocalDate? = remember(preselectedEpochDay, startWithoutDate) {
        newTaskInitialDate(
            preselectedEpochDay = preselectedEpochDay,
            startWithoutDate = startWithoutDate,
            today = LocalDate.now(),
        )
    }

    var selectedDate by rememberSaveable { mutableStateOf(initialDate) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Minutes since midnight: a LocalTime does not go into a Bundle.
    var selectedTimeMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val selectedTime = timeFromMinuteOfDay(selectedTimeMinute)
    var taskColor by rememberSaveable { mutableStateOf<Long?>(null) }

    var draftLoaded by remember { mutableStateOf(false) }

    // Only the counters are read here. Collecting the whole AppState woke this
    // screen up for every task, every meal and every page turned in a book.
    val state by vm.counters.collectAsState()

    var linkedManualCounterId by rememberSaveable { mutableStateOf<Long?>(null) }
    val showCounterPicker = rememberSaveable { mutableStateOf(false) }

    val manualCounters = state.counters.filterIsInstance<com.alphaomegos.annasagenda.ManualCounter>()
    val selectedCounterTitle = manualCounters.firstOrNull { it.id == linkedManualCounterId }?.title

    LaunchedEffect(Unit) {
        val draft = vm.loadNewTaskDraft()
        val canApply = description.isBlank() && subtasks.isEmpty() && taskColor == null

        if (draft != null && canApply) {
            description = draft.description
            taskColor = draft.taskColorArgb

            subtasks.clear()
            subtasks.addAll(
                draftSubtasksToEditable(
                    subtasks = draft.subtasks,
                    maxSubtasks = maxSubtasks,
                )
            )
        }

        draftLoaded = true
    }

    LaunchedEffect(draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect

        snapshotFlow {
            NewTaskDraft(
                description = description,
                taskColorArgb = taskColor,
                subtasks = editableSubtasksToDraft(subtasks.toList())
            )
        }
            .distinctUntilChanged()
            .collect { vm.queueNewTaskDraftSave(it) }
    }

    if (showCounterPicker.value) {
        AlertDialog(
            onDismissRequest = { showCounterPicker.value = false },
            title = { Text(stringResource(R.string.attach_counter)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    item {
                        TextButton(onClick = {
                            linkedManualCounterId = null
                            showCounterPicker.value = false
                        }) {
                            Text(stringResource(R.string.no_counter))
                        }
                    }

                    items(manualCounters.size) { i ->
                        val c = manualCounters[i]
                        TextButton(onClick = {
                            linkedManualCounterId = c.id
                            showCounterPicker.value = false
                        }) {
                            Text(c.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCounterPicker.value = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    val today = remember { LocalDate.now() }
    val tomorrow = today.plusDays(1)
    val locale = appLocale()
    val canSave = description.trim().isNotBlank()

    fun save() {
        val cleanSubtasks = subtasks.map { it.description.trim() }
        val taskId = vm.createTaskForDate(
            date = selectedDate,
            // A time chosen and then the day taken away (Someday) is no
            // time: there is no day to hold it.
            time = if (selectedDate != null) selectedTime else null,
            description = description.trim(),
            colorArgb = taskColor,
            linkedManualCounterId = linkedManualCounterId,
            hasSubtasks = cleanSubtasks.any { it.isNotBlank() }
        )
        cleanSubtasks.forEachIndexed { idx, txt ->
            if (txt.isNotBlank()) {
                val subColor = subtasks.getOrNull(idx)?.colorArgb ?: taskColor
                vm.createSubtask(taskId, txt, colorArgb = subColor)
            }
        }
        vm.clearNewTaskDraft()
        onBack()
    }

    // 03.10, "as in serious apps": the two actions in the top bar, where the
    // keyboard never covers them; the day as a row of chips that shows which
    // one is chosen (so no "Selected: 2026-10-03" line); no headings over
    // things whose buttons already say what they are.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.new_task_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = canSave) {
                        Text(stringResource(R.string.save_task))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                // The keyboard takes its height off the form, which scrolls
                // and keeps the field being typed in on screen.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The day: three fixed choices, then any other day and the time.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NewTaskChip(
                    label = stringResource(R.string.create_no_date),
                    selected = selectedDate == null,
                    onClick = { selectedDate = null },
                    modifier = Modifier.weight(1f),
                )
                NewTaskChip(
                    label = stringResource(R.string.create_today),
                    selected = selectedDate == today,
                    onClick = { selectedDate = today },
                    modifier = Modifier.weight(1f),
                )
                NewTaskChip(
                    label = stringResource(R.string.create_tomorrow),
                    selected = selectedDate == tomorrow,
                    onClick = { selectedDate = tomorrow },
                    modifier = Modifier.weight(1f),
                )
            }

            val otherDay = selectedDate?.takeIf { it != today && it != tomorrow }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewTaskChip(
                    // A day other than the three above names itself here.
                    label = otherDay?.let { formatNewTaskDay(it, locale) }
                        ?: stringResource(R.string.new_task_date_chip),
                    selected = otherDay != null,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                // Only a task with a day can have a time.
                NewTaskChip(
                    label = selectedTime?.let { formatTaskTime(it) }
                        ?: stringResource(R.string.new_task_time_chip),
                    selected = selectedTime != null && selectedDate != null,
                    enabled = selectedDate != null,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
                if (selectedTime != null && selectedDate != null) {
                    IconButton(onClick = { selectedTimeMinute = null }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_task_time))
                    }
                }
            }

            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Keyed on the typed text and nothing else: the history cannot
                // change while this screen is open, so there is nothing to
                // subscribe to and no reason to look again until the text moves.
                val suggestions = remember(description) { vm.taskSuggestions(description) }

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TaskSuggestionList(
                        suggestions = suggestions,
                        onPick = { picked ->
                            description = picked.description

                            val merged = subtasksAfterApplyingSuggestion(
                                current = subtasks.toList(),
                                suggested = picked.subtaskDescriptions,
                                maxSubtasks = maxSubtasks,
                                defaultColor = taskColor,
                            )

                            subtasks.clear()
                            subtasks.addAll(merged)
                        },
                    )
                }
            }

            // The colour: the word and the dots on one line; the dots scroll
            // sideways on a narrow screen rather than wrapping.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.new_task_color_short),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ColorPickerRow(
                        selected = taskColor,
                        onSelect = { newColor ->
                            taskColor = newColor

                            val updated = applyTaskColorToNonOverriddenSubtasks(
                                subtasks = subtasks.toList(),
                                taskColor = newColor,
                            )
                            subtasks.clear()
                            subtasks.addAll(updated)
                        }
                    )
                }
            }

            // A counter only where there is one to attach; the button says
            // what is attached, so it needs no heading and no "not attached".
            if (manualCounters.isNotEmpty()) {
                NewTaskChip(
                    label = selectedCounterTitle
                        ?.let { stringResource(R.string.new_task_counter_attached, it) }
                        ?: stringResource(R.string.attach_counter),
                    selected = selectedCounterTitle != null,
                    onClick = { showCounterPicker.value = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Subtasks: the heading carries the count and the add button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.subtasks_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${subtasks.size}/$maxSubtasks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        if (subtasks.size < maxSubtasks) {
                            subtasks.add(newEditableSubtask(taskColor))
                        }
                    },
                    enabled = subtasks.size < maxSubtasks,
                ) {
                    Text(stringResource(R.string.new_task_add_subtask_short))
                }
            }

            // A plain column: the whole form scrolls, and thirty rows is the most there can be.
            subtasks.forEachIndexed { i, subtask ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(
                        colorArgb = subtask.colorArgb,
                        onClick = {
                            subtasks[i] = subtask.copy(
                                colorArgb = nextPaletteColor(subtask.colorArgb),
                                colorOverridden = true,
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = subtask.description,
                        onValueChange = { newText ->
                            subtasks[i] = subtask.copy(description = newText)
                        },
                        label = { Text(stringResource(R.string.subtask_label, i + 1)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { subtasks.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                    }
                }
            }
        }

        if (showTimePicker) {
            PickTimeDialog(
                initialTime = selectedTime,
                onDismiss = { showTimePicker = false },
                onPicked = { picked -> selectedTimeMinute = minuteOfDay(picked) },
            )
        }

        if (showDatePicker) {
            PickDateDialog(
                initialDate = selectedDate,
                onDismiss = { showDatePicker = false },
                onPicked = { picked -> selectedDate = picked },
                extraDismissAction = {
                    TextButton(
                        onClick = {
                            selectedDate = null
                            showDatePicker = false
                        }
                    ) { Text(stringResource(R.string.create_no_date)) }
                },
            )
        }
    }
}

/**
 * One choice on the new-task form, all of them the same shape (03.10): a
 * chip as tall as a button needs to be and no taller, filled when chosen.
 * The words keep the button's size; only the padding around them went.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTaskChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier.height(40.dp),
    )
}

/** A day on its chip: "12 окт.", "12 Oct" — short, in the app's language. */
private fun formatNewTaskDay(date: LocalDate, locale: java.util.Locale): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", locale))

/**
 * Keeps the half-typed subtask list across a rotation.
 *
 * Saved state travels in a Bundle, so the list is flattened into the three
 * values each row actually holds — text, colour, and whether that colour was
 * chosen by hand — and rebuilt from them.
 */
private val editableSubtasksSaver =
    listSaver<SnapshotStateList<EditableNewTaskSubtask>, String>(
        save = { list -> editableSubtasksToSavedStrings(list.toList()) },
        restore = { flat ->
            mutableStateListOf<EditableNewTaskSubtask>().apply {
                addAll(editableSubtasksFromSavedStrings(flat))
            }
        },
    )
