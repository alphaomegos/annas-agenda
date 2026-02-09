package com.alphaomegos.annasagenda

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale


/* ---------------------------
   Main menu + language
---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    vm: AppViewModel,
    onLanguage: () -> Unit,
    onCalendar: () -> Unit,
    onNewTask: () -> Unit,
    onSomeday: () -> Unit,
    onRecurring: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var dataMenuExpanded by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val json = vm.exportBackupJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("OutputStream is null")
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                if (ok) ctx.getString(R.string.toast_exported) else ctx.getString(R.string.toast_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            }

            if (raw.isNullOrBlank()) {
                Toast.makeText(ctx, ctx.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val ok = vm.importBackupJson(raw)
            Toast.makeText(
                ctx,
                if (ok) ctx.getString(R.string.toast_imported) else ctx.getString(R.string.toast_invalid_backup),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onLanguage) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_language),
                            contentDescription = stringResource(R.string.choose_language),
                            tint = Color.Unspecified
                        )
                    }
                    IconButton(onClick = { dataMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.data_menu))
                    }

                    DropdownMenu(
                        expanded = dataMenuExpanded,
                        onDismissRequest = { dataMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_backup_json)) },
                            onClick = {
                                dataMenuExpanded = false
                                exportLauncher.launch("annasagenda-backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_backup_json)) },
                            onClick = {
                                dataMenuExpanded = false
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset_data_menu)) },
                            onClick = {
                                dataMenuExpanded = false
                                confirmReset = true
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_calendar,
                    title = stringResource(R.string.calendar),
                    onClick = onCalendar,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_new_task,
                    title = stringResource(R.string.create_task),
                    onClick = onNewTask,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_someday,
                    title = stringResource(R.string.someday_title),
                    onClick = onSomeday,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_recurring,
                    title = stringResource(R.string.recurring_tasks_title),
                    onClick = onRecurring,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllData()
                    confirmReset = false
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.toast_reset_done),
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}


@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            setAppLanguage(ctx, "en")
            activity?.recreate()
            onBack()
        }) {
            Text(stringResource(R.string.language_english))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            setAppLanguage(ctx, "ru")
            activity?.recreate()
            onBack()
        }) {
            Text(stringResource(R.string.language_russian))
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            setAppLanguage(ctx, "sr-Latn")
            activity?.recreate()
            onBack()
        }) {
            Text(stringResource(R.string.language_serbian))
        }

    }
}

@Composable
fun RecurringTasksScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val locale = appLocale()
    val today = remember { LocalDate.now() }

    val tasksById = remember(state.tasks) { state.tasks.associateBy { it.id } }

    // Template subtasks (the ones user edits on the template task)
    val templateSubtasksByTaskId = remember(state.subtasks) {
        state.subtasks
            .filter { it.originSubtaskId == null }
            .groupBy { it.taskId }
    }

    // 1) repeating TASK templates
    val taskTemplates = remember(state.tasks) {
        state.tasks
            .filter { it.originTaskId == null && it.repeatRule != null }
            .sortedBy { it.id }
    }

    // 2) repeating SUBTASK templates (pair: subtask + its parent template task)
    val subtaskTemplates = remember(state.subtasks, tasksById) {
        state.subtasks
            .filter { it.originSubtaskId == null && it.repeatRule != null }
            .mapNotNull { s ->
                val parent = tasksById[s.taskId] ?: return@mapNotNull null
                if (parent.originTaskId != null) return@mapNotNull null
                s to parent
            }
            .sortedWith(compareBy({ it.second.id }, { it.first.id }))
    }

    var confirmDeleteTaskId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteSubtaskId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.recurring_tasks_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(12.dp))

        if (taskTemplates.isEmpty() && subtaskTemplates.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recurring_tasks_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                if (taskTemplates.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.repeating_tasks_header),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    items(taskTemplates) { t ->
                        val anchor = t.date
                        val rule = t.repeatRule!!
                        val ruleText = repeatRuleText(rule, anchor)

                        val anchorText = anchor?.let {
                            DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(locale)
                                .format(it)
                        } ?: stringResource(R.string.recurring_tasks_no_date)

                        val subs = templateSubtasksByTaskId[t.id].orEmpty().sortedBy { it.id }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.description, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.recurring_tasks_starts_line,
                                        anchorText,
                                        ruleText
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (subs.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    subs.forEach { s ->
                                        Text("• ${s.description}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteTaskId = t.id }) {
                                        Text(stringResource(R.string.recurring_tasks_delete_from_today))
                                    }
                                }
                            }
                        }
                    }
                }

                if (subtaskTemplates.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.repeating_subtasks_header),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    items(subtaskTemplates) { (s, parent) ->
                        val anchor = parent.date
                        val rule = s.repeatRule!!
                        val ruleText = repeatRuleText(rule, anchor)

                        val anchorText = anchor?.let {
                            DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(locale)
                                .format(it)
                        } ?: stringResource(R.string.recurring_tasks_no_date)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(parent.description, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.recurring_tasks_starts_line,
                                        anchorText,
                                        ruleText
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(Modifier.height(8.dp))
                                Text("• ${s.description}", style = MaterialTheme.typography.bodyMedium)

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteSubtaskId = s.id }) {
                                        Text(stringResource(R.string.recurring_tasks_delete_from_today))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }

    if (confirmDeleteTaskId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTaskId = null },
            title = { Text(stringResource(R.string.delete_repeating_task_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_task_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTaskSeriesFrom(confirmDeleteTaskId!!, today)
                    confirmDeleteTaskId = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmDeleteSubtaskId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSubtaskId = null },
            title = { Text(stringResource(R.string.delete_repeating_subtask_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_subtask_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSubtaskSeriesFrom(confirmDeleteSubtaskId!!, today)
                    confirmDeleteSubtaskId = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSubtaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun MenuTile(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier, // IMPORTANT: do not force fillMaxWidth() here
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon area takes all free space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



/* ---------------------------
   Someday screen
   ---------------------------- */
@Composable
fun SomedayScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    val somedayTasksCount = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.count { t ->
            t.date == null && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.someday_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (somedayTasksCount == 0) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.someday_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DateTasksBlock(vm = vm, state = state, date = null)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}



/* ---------------------------
   Task screen (with subtasks)
---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(
    vm: AppViewModel,
    preselectedEpochDay: Long? = null,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var description by rememberSaveable { mutableStateOf("") }
    val maxSubtasks = 30
    val subtasksText = remember { mutableStateListOf<String>() }
    val subtasksColor = remember { mutableStateListOf<Long?>() }
    val initialDate: LocalDate? = remember(preselectedEpochDay) {
        preselectedEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    }
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var taskColor by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.new_task_title), style = MaterialTheme.typography.headlineSmall)
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
                text = "${stringResource(R.string.selected_label)} ${selectedDate?.toString() ?: stringResource(R.string.someday_tag)}",
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
                onSelect = { taskColor = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.subtasks_title), style = MaterialTheme.typography.titleMedium)
                Text("${subtasksText.size}/$maxSubtasks", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (subtasksText.isEmpty()) {
                Text(stringResource(R.string.no_subtasks_yet))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(subtasksText.size) { i ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(
                                colorArgb = subtasksColor[i],
                                onClick = { subtasksColor[i] = nextPaletteColor(subtasksColor[i]) }
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
                            subtasksColor.add(null)
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
                                vm.createSubtask(taskId, txt, colorArgb = subtasksColor[idx])
                            }
                        }
                        onBack()
                    },
                    enabled = description.trim().isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_task))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
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


/* ---------------------------
   Calendar screens
---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDayScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    initialEpochDay: Long,
    onAddTask: (Long) -> Unit,
) {
    val state by vm.state.collectAsState()

    val today = remember { LocalDate.now() }
    var selectedEpochDay by rememberSaveable(initialEpochDay) { mutableStateOf(initialEpochDay) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }

    LaunchedEffect(selectedEpochDay) {
        val d = LocalDate.ofEpochDay(selectedEpochDay)
        vm.ensureGeneratedInRange(d, d)
    }

    val dateText = remember(selectedEpochDay) {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
        selectedDate.format(fmt)
    }

    val dateTasksCount = remember(state.tasks, state.suppressedRecurrences, selectedDate) {
        state.tasks.count { t ->
            t.date == selectedDate && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onAddTask(selectedEpochDay) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.create_task_for_this_day))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { selectedEpochDay -= 1 }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.prev_day))
            }
            OutlinedButton(onClick = { selectedEpochDay = today.toEpochDay() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.today))
            }
            OutlinedButton(onClick = { selectedEpochDay += 1 }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.next_day))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (dateTasksCount == 0) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_tasks_for_this_day))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(stringResource(R.string.tasks_header), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTasksBlock(vm = vm, state = state, date = selectedDate)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarMonthScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSomeday: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val locale = remember { Locale.getDefault() }
    val today = remember { LocalDate.now() }

    val baseIndex = remember { today.year * 12 + (today.monthValue - 1) }
    var monthIndex by rememberSaveable { mutableStateOf(baseIndex) }

    val year = monthIndex / 12
    val month = (monthIndex % 12) + 1
    val yearMonth = remember(monthIndex) { YearMonth.of(year, month) }

    val firstDay = remember(yearMonth) { yearMonth.atDay(1) }
    val daysInMonth = remember(yearMonth) { yearMonth.lengthOfMonth() }

    val recurrenceSignature = remember(state.tasks, state.subtasks, state.suppressedRecurrences) {
        val tSig = state.tasks
            .filter { it.originTaskId == null && it.repeatRule != null && it.date != null }
            .map { it.id to it.repeatRule }
            .hashCode()

        val sSig = state.subtasks
            .filter { it.originSubtaskId == null && it.repeatRule != null }
            .map { it.id to it.repeatRule }
            .hashCode()

        val supSig = state.suppressedRecurrences.hashCode()
        (tSig * 31) + (sSig * 7) + supSig
    }

    LaunchedEffect(yearMonth, recurrenceSignature) {
        vm.ensureGeneratedInRange(firstDay, yearMonth.atEndOfMonth())
    }


    val firstDow = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val headerDays = remember(firstDow) { orderedWeekDays(firstDow) }

    val offset = remember(firstDay, firstDow) {
        val a = firstDay.dayOfWeek.value
        val b = firstDow.value
        ((a - b) + 7) % 7
    }

    // Count tasks + subtasks by date for month cells (ignore suppressed template tasks on their own date).
    val visibleTasks = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences) }
    }

    val taskCountByDate = remember(visibleTasks) {
        visibleTasks.mapNotNull { it.date }.groupingBy { it }.eachCount()
    }

    val taskDateById = remember(visibleTasks) {
        visibleTasks.associate { it.id to it.date }
    }

    val subtaskCountByDate = remember(state.subtasks, taskDateById) {
        val m = mutableMapOf<LocalDate, Int>()
        for (st in state.subtasks) {
            val d = taskDateById[st.taskId] ?: continue
            if (d != null) m[d] = (m[d] ?: 0) + 1
        }
        m
    }

    val itemCountByDate = remember(taskCountByDate, subtaskCountByDate) {
        val m = mutableMapOf<LocalDate, Int>()
        for ((d, c) in taskCountByDate) m[d] = (m[d] ?: 0) + c
        for ((d, c) in subtaskCountByDate) m[d] = (m[d] ?: 0) + c
        m
    }

    val cells = remember(yearMonth, offset, daysInMonth) {
        val out = ArrayList<LocalDate?>(42)
        repeat(offset) { out.add(null) }
        for (d in 1..daysInMonth) out.add(yearMonth.atDay(d))
        while (out.size % 7 != 0) out.add(null)
        out
    }

    val somedayCount = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.count { t ->
            t.date == null && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { monthIndex -= 1 }) { Text(stringResource(R.string.prev_month)) }

            val monthTitle = remember(monthIndex) {
                val fmt = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
                yearMonth.atDay(1).format(fmt).replaceFirstChar { it.titlecase(locale) }
            }
            Text(monthTitle, style = MaterialTheme.typography.titleMedium)

            OutlinedButton(onClick = { monthIndex += 1 }) { Text(stringResource(R.string.next_month)) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            headerDays.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(cells) { date ->
                if (date == null) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val count = itemCountByDate[date] ?: 0
                    val isToday = date == today

                    Surface(
                        tonalElevation = if (isToday) 4.dp else 0.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onOpenDay(date.toEpochDay()) }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                            if (count > 0) {
                                Text(text = count.toString(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onOpenSomeday) {
            Text(stringResource(R.string.someday_count, somedayCount))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}


/* ---------------------------
   Helpers
---------------------------- */
@Composable
private fun appLocale(): Locale {
    val cfg = LocalContext.current.resources.configuration
    return if (Build.VERSION.SDK_INT >= 24) cfg.locales[0] else @Suppress("DEPRECATION") cfg.locale
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatPickerDialog(
    initial: RepeatRule?,
    onDismiss: () -> Unit,
    onConfirm: (RepeatRule?) -> Unit
) {
    val locale = appLocale()

    val defaultDow = remember { LocalDate.now().dayOfWeek }
    val defaultDom = remember { LocalDate.now().dayOfMonth }

    var enabled by remember { mutableStateOf(initial != null) }
    var freq by remember { mutableStateOf(initial?.freq ?: RepeatFreq.WEEKLY) }

    var interval by remember { mutableStateOf((initial?.interval ?: 1).coerceAtLeast(1)) }

    var weekDays by remember {
        mutableStateOf(
            initial?.weekDays?.takeIf { it.isNotEmpty() } ?: setOf(defaultDow)
        )
    }

    var dayOfMonth by remember {
        mutableStateOf((initial?.dayOfMonth ?: defaultDom).coerceIn(1, 31))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Enabled (Switch)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.repeat_enabled),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                // Frequency (segmented buttons) — fixes "vertical sausage"
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(
                        RepeatFreq.DAILY to R.string.repeat_freq_daily,
                        RepeatFreq.WEEKLY to R.string.repeat_freq_weekly,
                        RepeatFreq.MONTHLY to R.string.repeat_freq_monthly
                    )

                    opts.forEachIndexed { index, (f, labelRes) ->
                        SegmentedButton(
                            selected = freq == f,
                            onClick = { freq = f },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = opts.size),
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Interval (stepper)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.repeat_interval),
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { interval = (interval - 1).coerceAtLeast(1) },
                        enabled = enabled && interval > 1
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                    }

                    Text(
                        text = interval.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.widthIn(min = 32.dp),
                        textAlign = TextAlign.Center
                    )

                    FilledTonalIconButton(
                        onClick = { interval += 1 },
                        enabled = enabled
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }

                // Weekly: day chips
                AnimatedVisibility(visible = enabled && freq == RepeatFreq.WEEKLY) {
                    val firstDow = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
                    val ordered = remember(firstDow) { orderedWeekDays(firstDow) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.repeat_weekdays),
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ordered.forEach { dow ->
                                val selected = weekDays.contains(dow)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val newSet = weekDays.toMutableSet()
                                        if (selected) newSet.remove(dow) else newSet.add(dow)
                                        weekDays = newSet
                                    },
                                    enabled = enabled,
                                    label = { Text(dow.getDisplayName(TextStyle.SHORT, locale)) }
                                )
                            }
                        }
                    }
                }

                // Monthly: day-of-month stepper
                AnimatedVisibility(visible = enabled && freq == RepeatFreq.MONTHLY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.repeat_day_of_month),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalIconButton(
                            onClick = { dayOfMonth = (dayOfMonth - 1).coerceAtLeast(1) },
                            enabled = enabled && dayOfMonth > 1
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }

                        Text(
                            text = dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.widthIn(min = 32.dp),
                            textAlign = TextAlign.Center
                        )

                        FilledTonalIconButton(
                            onClick = { dayOfMonth = (dayOfMonth + 1).coerceAtMost(31) },
                            enabled = enabled && dayOfMonth < 31
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!enabled) {
                    onConfirm(null)
                    return@TextButton
                }

                val safeWeekDays =
                    if (freq == RepeatFreq.WEEKLY) weekDays.takeIf { it.isNotEmpty() } ?: setOf(defaultDow)
                    else emptySet()

                onConfirm(
                    RepeatRule(
                        freq = freq,
                        interval = interval.coerceAtLeast(1),
                        weekDays = safeWeekDays,
                        dayOfMonth = if (freq == RepeatFreq.MONTHLY) dayOfMonth.coerceIn(1, 31) else null
                    )
                )
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DateTasksBlock(
    vm: AppViewModel,
    state: AppState,
    date: LocalDate?
) {
    val tasks = remember(state.tasks, state.suppressedRecurrences, date) {
        state.tasks
            .filter { it.date == date }
            .filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences) }
            .sortedWith(compareBy<Task>({ it.order }, { it.id }))
    }
    if (tasks.isEmpty()) return

    var collapsedTaskIds by remember(date) { mutableStateOf<Set<Long>>(emptySet()) }

    var moveTaskId by remember { mutableStateOf<Long?>(null) }
    var showMoveTaskDatePicker by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    var copyTaskId by remember { mutableStateOf<Long?>(null) }
    var showCopyTaskDatePicker by remember { mutableStateOf(false) }

    var copySubtaskId by remember { mutableStateOf<Long?>(null) }
    var showCopySubtaskDatePicker by remember { mutableStateOf(false) }

    var moveSubtaskId by remember { mutableStateOf<Long?>(null) }

    var addSubtaskToTaskId by remember { mutableStateOf<Long?>(null) }
    var newSubtaskText by remember { mutableStateOf("") }
    var newSubtaskColor by remember { mutableStateOf<Long?>(null) }

    var editTaskId by remember { mutableStateOf<Long?>(null) }
    var editTaskText by remember { mutableStateOf("") }
    var editTaskRepeatRule by remember { mutableStateOf<RepeatRule?>(null) }
    var showTaskRepeatPicker by remember { mutableStateOf(false) }

    var editSubtaskId by remember { mutableStateOf<Long?>(null) }
    var editSubtaskText by remember { mutableStateOf("") }
    var editSubtaskRepeatRule by remember { mutableStateOf<RepeatRule?>(null) }
    var showSubtaskRepeatPicker by remember { mutableStateOf(false) }

    val markerShape = remember { RoundedCornerShape(10.dp) }
    val markerAlpha = 0.18f

    tasks.forEach { task ->
        val subtasks = state.subtasks
            .filter { it.taskId == task.id }
            .sortedWith(compareBy<Subtask>({ it.order }, { it.id }))

        val taskBg = task.colorArgb
            ?.toInt()
            ?.let { Color(it).copy(alpha = markerAlpha) }
            ?: Color.Transparent

        val cycleTaskColor = {
            vm.setTaskColor(task.id, nextPaletteColor(task.colorArgb))
        }

        val isTaskCollapsed = collapsedTaskIds.contains(task.id)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(taskBg, markerShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subtasks.isNotEmpty()) {
                TextButton(
                    onClick = {
                        collapsedTaskIds =
                            if (isTaskCollapsed) collapsedTaskIds - task.id else collapsedTaskIds + task.id
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.width(26.dp)
                ) {
                    Text(if (isTaskCollapsed) "▶" else "▼")
                }
            } else {
                Spacer(modifier = Modifier.width(26.dp))
            }

            Checkbox(
                checked = task.isDone,
                onCheckedChange = { vm.toggleTaskDone(task.id) }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
                    .clickable { cycleTaskColor() }
            ) {
                val deco = if (task.isDone) TextDecoration.LineThrough else null
                Text(
                    text = task.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editTaskId = task.id
                            editTaskText = task.description
                            editTaskRepeatRule = task.repeatRule
                            showTaskRepeatPicker = false
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = deco)
                )
            }

            TinyIconButton(onClick = { vm.moveTaskUp(task.id) }, icon = Icons.Default.KeyboardArrowUp, cd = "Move task up")
            TinyIconButton(onClick = { vm.moveTaskDown(task.id) }, icon = Icons.Default.KeyboardArrowDown, cd = "Move task down")
            TinyIconButton(onClick = { moveTaskId = task.id }, icon = Icons.AutoMirrored.Filled.ArrowForward, cd = "Move task")
            TinyIconButton(
                onClick = { copyTaskId = task.id },
                icon = Icons.Default.ContentCopy,
                cd = stringResource(R.string.copy_task)
            )

            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(36.dp)
                    .clickable { cycleTaskColor() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isTaskCollapsed) {
                TextButton(onClick = {
                    addSubtaskToTaskId = task.id
                    newSubtaskText = ""
                    newSubtaskColor = null
                }) {
                    Text(stringResource(R.string.add_subtask))
                }
            }
        }

        if (subtasks.isNotEmpty() && !collapsedTaskIds.contains(task.id)) {
            subtasks.forEach { st ->
                val subBg = st.colorArgb
                    ?.toInt()
                    ?.let { Color(it).copy(alpha = markerAlpha) }
                    ?: Color.Transparent

                val cycleSubtaskColor = {
                    vm.setSubtaskColor(st.id, nextPaletteColor(st.colorArgb))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp)
                        .background(subBg, markerShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = st.isDone,
                        onCheckedChange = { vm.toggleSubtaskDone(st.id) }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .clickable { cycleSubtaskColor() }
                    ) {
                        val stDeco = if (st.isDone) TextDecoration.LineThrough else null
                        Text(
                            text = st.description,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editSubtaskId = st.id
                                    editSubtaskText = st.description
                                    editSubtaskRepeatRule = st.repeatRule
                                    showSubtaskRepeatPicker = false
                                },
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = stDeco)
                        )
                    }

                    TinyIconButton(onClick = { vm.moveSubtaskUp(st.id) }, icon = Icons.Default.KeyboardArrowUp, cd = "Move subtask up")
                    TinyIconButton(onClick = { vm.moveSubtaskDown(st.id) }, icon = Icons.Default.KeyboardArrowDown, cd = "Move subtask down")
                    TinyIconButton(onClick = { moveSubtaskId = st.id }, icon = Icons.AutoMirrored.Filled.ArrowForward, cd = "Move subtask")
                    TinyIconButton(
                        onClick = { copySubtaskId = st.id },
                        icon = Icons.Default.ContentCopy,
                        cd = stringResource(R.string.copy_subtask)
                    )

                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(36.dp)
                            .clickable { cycleSubtaskColor() }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Move task: choose date (Today / Tomorrow / Someday / Pick date)
    if (moveTaskId != null && !showMoveTaskDatePicker) {
        AlertDialog(
            onDismissRequest = { moveTaskId = null },
            title = { Text(stringResource(R.string.move_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.rescheduleTaskToDate(moveTaskId!!, null)
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.move_to_someday)) }

                    TextButton(
                        onClick = {
                            vm.rescheduleTaskToDate(moveTaskId!!, LocalDate.now())
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            vm.rescheduleTaskToDate(moveTaskId!!, LocalDate.now().plusDays(1))
                            moveTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showMoveTaskDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reschedule)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (moveTaskId != null && showMoveTaskDatePicker) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember(moveTaskId, state.tasks) {
            val d = state.tasks.firstOrNull { it.id == moveTaskId }?.date ?: LocalDate.now()
            d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showMoveTaskDatePicker = false
                moveTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.rescheduleTaskToDate(moveTaskId!!, newDate)
                    }
                    showMoveTaskDatePicker = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveTaskDatePicker = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (copyTaskId != null && !showCopyTaskDatePicker) {
        AlertDialog(
            onDismissRequest = { copyTaskId = null },
            title = { Text(stringResource(R.string.copy_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.copyTaskToDate(copyTaskId!!, LocalDate.now())
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            vm.copyTaskToDate(copyTaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopyTaskDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copyTaskId != null && showCopyTaskDatePicker) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopyTaskDatePicker = false
                copyTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.copyTaskToDate(copyTaskId!!, newDate)
                        Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                    showCopyTaskDatePicker = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopyTaskDatePicker = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // Move subtask to another task
    if (moveSubtaskId != null) {
        val sub = state.subtasks.firstOrNull { it.id == moveSubtaskId }
        val currentTaskId = sub?.taskId

        AlertDialog(
            onDismissRequest = { moveSubtaskId = null },
            title = { Text(stringResource(R.string.move_subtask)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    val targets = state.tasks
                        .filter { it.id != currentTaskId }
                        .sortedWith(compareBy<Task>({ it.date?.toEpochDay() ?: Long.MIN_VALUE }, { it.order }, { it.id }))

                    items(targets) { t ->
                        val dText = t.date?.toString() ?: stringResource(R.string.someday_tag)
                        TextButton(
                            onClick = {
                                vm.moveSubtask(moveSubtaskId!!, t.id)
                                moveSubtaskId = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${t.description} • $dText")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveSubtaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Add subtask dialog
    if (addSubtaskToTaskId != null) {
        AlertDialog(
            onDismissRequest = { addSubtaskToTaskId = null },
            title = { Text(stringResource(R.string.add_subtask)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        label = { Text(stringResource(R.string.task_description_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.subtask_color))
                        Spacer(modifier = Modifier.width(10.dp))
                        ColorPickerRow(
                            selected = newSubtaskColor,
                            onSelect = { newSubtaskColor = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val txt = newSubtaskText.trim()
                        if (txt.isNotBlank()) {
                            vm.createSubtask(addSubtaskToTaskId!!, txt, colorArgb = newSubtaskColor)
                        }
                        addSubtaskToTaskId = null
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { addSubtaskToTaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && !showCopySubtaskDatePicker) {
        AlertDialog(
            onDismissRequest = { copySubtaskId = null },
            title = { Text(stringResource(R.string.copy_subtask)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.copySubtaskToDate(copySubtaskId!!, LocalDate.now())
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            vm.copySubtaskToDate(copySubtaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopySubtaskDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { copySubtaskId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && showCopySubtaskDatePicker) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopySubtaskDatePicker = false
                copySubtaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.copySubtaskToDate(copySubtaskId!!, newDate)
                        Toast.makeText(ctx, ctx.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                    }
                    showCopySubtaskDatePicker = false
                    copySubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopySubtaskDatePicker = false
                    copySubtaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }


    // Edit task dialog
    if (editTaskId != null) {
        AlertDialog(
            onDismissRequest = { editTaskId = null; showTaskRepeatPicker = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editTaskText,
                    onValueChange = { editTaskText = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTaskDescription(editTaskId!!, editTaskText)
                    editTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTaskRepeatPicker = true }) { Text(stringResource(R.string.repeat)) }

                    TextButton(onClick = {
                        vm.deleteTask(editTaskId!!)
                        editTaskId = null
                        showTaskRepeatPicker = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editTaskId = null
                        showTaskRepeatPicker = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    // Edit subtask dialog
    if (editSubtaskId != null) {
        AlertDialog(
            onDismissRequest = { editSubtaskId = null; showSubtaskRepeatPicker = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editSubtaskText,
                    onValueChange = { editSubtaskText = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSubtaskDescription(editSubtaskId!!, editSubtaskText)
                    editSubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSubtaskRepeatPicker = true }) { Text(stringResource(R.string.repeat)) }

                    TextButton(onClick = {
                        vm.deleteSubtask(editSubtaskId!!)
                        editSubtaskId = null
                        showSubtaskRepeatPicker = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editSubtaskId = null
                        showSubtaskRepeatPicker = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showTaskRepeatPicker && editTaskId != null) {
        RepeatPickerDialog(
            initial = editTaskRepeatRule,
            onDismiss = { showTaskRepeatPicker = false },
            onConfirm = { rule ->
                editTaskRepeatRule = rule
                vm.setTaskRepeatRule(editTaskId!!, rule)
                showTaskRepeatPicker = false
            }
        )
    }

    if (showSubtaskRepeatPicker && editSubtaskId != null) {
        RepeatPickerDialog(
            initial = editSubtaskRepeatRule,
            onDismiss = { showSubtaskRepeatPicker = false },
            onConfirm = { rule ->
                editSubtaskRepeatRule = rule
                vm.setSubtaskRepeatRule(editSubtaskId!!, rule)
                showSubtaskRepeatPicker = false
            }
        )
    }
}

private fun orderedWeekDays(first: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.values().toList()
    val idx = all.indexOf(first)
    return all.drop(idx) + all.take(idx)
}

@Composable
private fun repeatRuleText(rule: RepeatRule, anchor: LocalDate?): String {
    val interval = rule.interval.coerceAtLeast(1)
    val locale = appLocale()

    return when (rule.freq) {
        RepeatFreq.DAILY -> {
            if (interval == 1) {
                stringResource(R.string.repeat_daily_every_day)
            } else {
                pluralStringResource(R.plurals.repeat_daily_every_n_days, interval, interval)
            }
        }

        RepeatFreq.WEEKLY -> {
            val days = (rule.weekDays.takeIf { it.isNotEmpty() }
                ?: anchor?.let { setOf(it.dayOfWeek) }
                ?: emptySet()
                    ).sortedBy { it.value }

            val names = days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }

            if (interval == 1) {
                stringResource(R.string.repeat_weekly_every_week_on, names)
            } else {
                pluralStringResource(R.plurals.repeat_weekly_every_n_weeks_on, interval, interval, names)
            }
        }

        RepeatFreq.MONTHLY -> {
            val dom = rule.dayOfMonth ?: anchor?.dayOfMonth ?: 1

            if (interval == 1) {
                stringResource(R.string.repeat_monthly_every_month_on_day, dom)
            } else {
                pluralStringResource(R.plurals.repeat_monthly_every_n_months_on_day, interval, interval, dom)
            }
        }
    }
}



private fun isSuppressedTemplateTaskOnItsDate(task: Task, suppressed: Set<String>): Boolean {
    val d = task.date ?: return false
    return task.originTaskId == null && suppressed.contains("T:${task.id}:${d.toEpochDay()}")
}

@Composable
fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title)
    }
}

private val Palette: List<Long> = listOf(
    0xFFE53935L, // red
    0xFFFB8C00L, // orange
    0xFFFDD835L, // yellow
    0xFF43A047L, // green
    0xFF00897BL, // teal
    0xFF1E88E5L, // blue
    0xFF3949ABL, // indigo
    0xFFD81B60L, // pink
)

private fun nextPaletteColor(current: Long?): Long? {
    if (Palette.isEmpty()) return null
    if (current == null) return Palette.first()
    val idx = Palette.indexOf(current)
    return if (idx == -1) Palette.first() else Palette[(idx + 1) % Palette.size]
}

@Composable
private fun ColorDot(
    colorArgb: Long?,
    onClick: () -> Unit,
    size: Dp = 16.dp,
    isSelected: Boolean = false
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val fill = colorArgb?.toInt()?.let { Color(it) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .clickable { onClick() }
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.45f)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun ColorPickerRow(selected: Long?, onSelect: (Long?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDot(
            colorArgb = null,
            onClick = { onSelect(null) },
            size = 22.dp,
            isSelected = selected == null
        )
        Palette.forEach { c ->
            ColorDot(
                colorArgb = c,
                onClick = { onSelect(c) },
                size = 22.dp,
                isSelected = selected == c
            )
        }
    }
}

@Composable
private fun TinyIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    cd: String
) {
    Box(
        modifier = Modifier
            .size(28.dp)              // <-- size of the button (adjust 24..32)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = cd,
            modifier = Modifier.size(22.dp) // <-- size of the arrow itself
        )
    }
}
