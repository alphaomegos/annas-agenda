package com.alphaomegos.annasagenda

import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

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
    onAnthropometry: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val dataMenuExpanded = remember { mutableStateOf(false) }
    val confirmReset = remember { mutableStateOf(false) }

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
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()
            }

            if (raw.isNullOrBlank()) {
                Toast.makeText(ctx, ctx.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT)
                    .show()
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

    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }

    val langTag = remember(locale) {
        val language = locale.language
        when (language) {
            "sr" -> "sr-Latn"
            "gil" -> "gil"
            "ru" -> "ru"
            else -> "en"
        }
    }

    val langIconRes = when (langTag) {
        "ru" -> R.drawable.ic_langflag_ru
        "sr-Latn" -> R.drawable.ic_langflag_sr_latn
        "gil" -> R.drawable.ic_langflag_gil
        else -> R.drawable.ic_langflag_en
    }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onLanguage) {
                        Icon(
                            painter = painterResource(langIconRes),
                            contentDescription = stringResource(R.string.choose_language),
                            tint = Color.Unspecified
                        )
                    }
                    IconButton(onClick = { dataMenuExpanded.value = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.data_menu)
                        )
                    }

                    DropdownMenu(
                        expanded = dataMenuExpanded.value,
                        onDismissRequest = { dataMenuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_backup_json)) },
                            onClick = {
                                dataMenuExpanded.value = false
                                exportLauncher.launch("annasagenda-backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_backup_json)) },
                            onClick = {
                                dataMenuExpanded.value = false
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset_data_menu)) },
                            onClick = {
                                dataMenuExpanded.value = false
                                confirmReset.value = true
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_anthropometry,
                    title = stringResource(R.string.anthropometry_title),
                    onClick = onAnthropometry,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_soon,
                    title = stringResource(R.string.coming_soon),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

        }
    }
    if (confirmReset.value) {
        AlertDialog(
            onDismissRequest = { confirmReset.value = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllData()
                    confirmReset.value = false
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
                TextButton(onClick = { confirmReset.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.choose_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                    iconRes = R.drawable.ic_lang_en,
                    title = stringResource(R.string.language_english),
                    onClick = {
                        setAppLanguage(ctx, "en")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_lang_ru,
                    title = stringResource(R.string.language_russian),
                    onClick = {
                        setAppLanguage(ctx, "ru")
                        activity?.recreate()
                        onBack()
                    },
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
                    iconRes = R.drawable.ic_lang_sr_latn,
                    title = stringResource(R.string.language_serbian),
                    onClick = {
                        setAppLanguage(ctx, "sr-Latn")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_lang_ki,
                    title = stringResource(R.string.language_kiribati),
                    onClick = {
                        setAppLanguage(ctx, "gil")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
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

    val confirmDeleteTaskId = remember { mutableStateOf<Long?>(null) }
    val confirmDeleteSubtaskId = remember { mutableStateOf<Long?>(null) }

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
                                        Text(
                                            "• ${s.description}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteTaskId.value = t.id }) {
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
                                Text(
                                    parent.description,
                                    style = MaterialTheme.typography.titleMedium
                                )
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
                                Text(
                                    "• ${s.description}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteSubtaskId.value = s.id }) {
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

    if (confirmDeleteTaskId.value != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTaskId.value = null },
            title = { Text(stringResource(R.string.delete_repeating_task_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_task_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTaskSeriesFrom(confirmDeleteTaskId.value!!, today)
                    confirmDeleteTaskId.value = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDeleteTaskId.value = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmDeleteSubtaskId.value != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSubtaskId.value = null },
            title = { Text(stringResource(R.string.delete_repeating_subtask_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_subtask_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSubtaskSeriesFrom(confirmDeleteSubtaskId.value!!, today)
                    confirmDeleteSubtaskId.value = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSubtaskId.value = null }) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            }
        )
    }
}


/* ---------------------------
   Anthropometry screen
---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnthropometryDayInputDialog(
    date: LocalDate,
    initialEntry: AnthropometryEntry?,
    onDismiss: () -> Unit,
    onSave: (List<Double?>) -> Unit
) {
    fun init(v: Double?) = v?.let {
        val s = String.format(Locale.US, "%.1f", it)
        if (s.endsWith(".0")) s.dropLast(2) else s
    } ?: ""

    var t0 by remember { mutableStateOf(init(initialEntry?.armCm)) }
    var t1 by remember { mutableStateOf(init(initialEntry?.chestCm)) }
    var t2 by remember { mutableStateOf(init(initialEntry?.underChestCm)) }
    var t3 by remember { mutableStateOf(init(initialEntry?.waistCm)) }
    var t4 by remember { mutableStateOf(init(initialEntry?.bellyCm)) }
    var t5 by remember { mutableStateOf(init(initialEntry?.hipsCm)) }
    var t6 by remember { mutableStateOf(init(initialEntry?.thighCm)) }
    var t7 by remember { mutableStateOf(init(initialEntry?.weightKg)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = date.toString(), style = MaterialTheme.typography.labelLarge)
                Text(text = stringResource(R.string.anthropometry_hint))

                OutlinedTextField(
                    t0,
                    { t0 = it },
                    label = { Text(stringResource(R.string.anthro_arm_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t1,
                    { t1 = it },
                    label = { Text(stringResource(R.string.anthro_chest_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t2,
                    { t2 = it },
                    label = { Text(stringResource(R.string.anthro_under_chest_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t3,
                    { t3 = it },
                    label = { Text(stringResource(R.string.anthro_waist_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t4,
                    { t4 = it },
                    label = { Text(stringResource(R.string.anthro_belly_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t5,
                    { t5 = it },
                    label = { Text(stringResource(R.string.anthro_hips_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t6,
                    { t6 = it },
                    label = { Text(stringResource(R.string.anthro_thigh_cm)) },
                    singleLine = true
                )
                OutlinedTextField(
                    t7,
                    { t7 = it },
                    label = { Text(stringResource(R.string.anthro_weight_kg)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = listOf(t0, t1, t2, t3, t4, t5, t6, t7).map { parseDecimalOrNull(it) }
                onSave(values)
            }) { Text(stringResource(R.string.anthropometry_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun parseDecimalOrNull(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return t.replace(',', '.').toDoubleOrNull()
}


private enum class AnthroAxis { CM, KG }

private data class AnthroSeries(
    val labelRes: Int,
    val axis: AnthroAxis,
    val color: Color,
    val getValue: (AnthropometryEntry) -> Double?
)

private inline fun <T, R : Any> List<T>.lastNotNullOfOrNullCompat(transform: (T) -> R?): R? {
    for (i in indices.reversed()) {
        val v = transform(this[i])
        if (v != null) return v
    }
    return null
}

private inline fun <T, R : Any> List<T>.firstNotNullOfOrNullCompat(transform: (T) -> R?): R? {
    for (i in indices) {
        val v = transform(this[i])
        if (v != null) return v
    }
    return null
}


private val anthroSeries = listOf(
    AnthroSeries(R.string.anthro_arm_cm, AnthroAxis.CM, Color(0xFF1E88E5)) { it.armCm },
    AnthroSeries(R.string.anthro_chest_cm, AnthroAxis.CM, Color(0xFFE53935)) { it.chestCm },
    AnthroSeries(
        R.string.anthro_under_chest_cm,
        AnthroAxis.CM,
        Color(0xFF8E24AA)
    ) { it.underChestCm },
    AnthroSeries(R.string.anthro_waist_cm, AnthroAxis.CM, Color(0xFFFB8C00)) { it.waistCm },
    AnthroSeries(R.string.anthro_belly_cm, AnthroAxis.CM, Color(0xFF43A047)) { it.bellyCm },
    AnthroSeries(R.string.anthro_hips_cm, AnthroAxis.CM, Color(0xFF00ACC1)) { it.hipsCm },
    AnthroSeries(R.string.anthro_thigh_cm, AnthroAxis.CM, Color(0xFF6D4C41)) { it.thighCm },
    AnthroSeries(R.string.anthro_weight_kg, AnthroAxis.KG, Color(0xFF546E7A)) { it.weightKg },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnthropometryScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    val allEntries = remember(state.anthropometry) {
        state.anthropometry.sortedBy { it.date }
    }
    val entriesByDate = remember(allEntries) { allEntries.associateBy { it.date } }

    val windowSize = 10
    var windowEnd by remember(allEntries.size) {
        mutableIntStateOf(
            (allEntries.size - 1).coerceAtLeast(
                0
            )
        )
    }

    LaunchedEffect(allEntries.size) {
        windowEnd = (allEntries.size - 1).coerceAtLeast(0)
    }

    val window = remember(allEntries, windowEnd) {
        if (allEntries.isEmpty()) emptyList()
        else {
            val end = windowEnd.coerceIn(0, allEntries.lastIndex)
            val start = (end - windowSize + 1).coerceAtLeast(0)
            allEntries.subList(start, end + 1)
        }
    }

    val showInput = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.anthropometry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
            AnthropometryChart(
                entries = window,
                canGoOlder = allEntries.size > window.size && window.firstOrNull()?.date != allEntries.firstOrNull()?.date,
                canGoNewer = allEntries.isNotEmpty() && window.lastOrNull()?.date != allEntries.lastOrNull()?.date,
                onGoOlder = { windowEnd = (windowEnd - 1).coerceAtLeast(0) },
                onGoNewer = { windowEnd = (windowEnd + 1).coerceAtMost(allEntries.lastIndex) },
            )

            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (allEntries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.anthropometry_empty))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(anthroSeries) { s ->
                            val first: Pair<LocalDate, Double>? =
                                allEntries.firstNotNullOfOrNullCompat { e ->
                                    s.getValue(e)?.let { v -> Pair(e.date, v) }
                                }

                            val last: Pair<LocalDate, Double>? =
                                allEntries.lastNotNullOfOrNullCompat { e ->
                                    s.getValue(e)?.let { v -> Pair(e.date, v) }
                                }
                            if (first == null || last == null) return@items

                            val diff = last.second - first.second
                            val unit =
                                if (s.axis == AnthroAxis.KG) stringResource(R.string.kg_short) else stringResource(
                                    R.string.cm_short
                                )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(s.labelRes).substringBefore(','),
                                    color = s.color,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${formatOneDecimal(last.second)} $unit (${
                                        formatSignedOneDecimal(
                                            diff
                                        )
                                    }) · ${last.first}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.widthIn(min = 140.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showInput.value = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.enter_data))
            }
        }
    }

    if (showInput.value) {
        AnthropometryInputDialog(
            entriesByDate = entriesByDate,
            onDismiss = { showInput.value = false },
            onSave = { date, values ->
                vm.saveAnthropometryForDate(
                    date = date,
                    armCm = values.getOrNull(0),
                    chestCm = values.getOrNull(1),
                    underChestCm = values.getOrNull(2),
                    waistCm = values.getOrNull(3),
                    bellyCm = values.getOrNull(4),
                    hipsCm = values.getOrNull(5),
                    thighCm = values.getOrNull(6),
                    weightKg = values.getOrNull(7),
                )
                Toast.makeText(ctx, ctx.getString(R.string.anthropometry_saved), Toast.LENGTH_SHORT)
                    .show()
                showInput.value = false
            }
        )
    }
}

@Composable
private fun AnthropometryChart(
    entries: List<AnthropometryEntry>,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onGoOlder: () -> Unit,
    onGoNewer: () -> Unit,
) {
    val density = LocalDensity.current
    val dragAcc = remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 48.dp.toPx() }

    val cmUnit = stringResource(R.string.cm_short)
    val kgUnit = stringResource(R.string.kg_short)

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(entries, canGoOlder, canGoNewer) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragAcc.floatValue = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAcc.floatValue += dragAmount
                        if (dragAcc.floatValue <= -thresholdPx) {
                            if (canGoOlder) onGoOlder()
                            dragAcc.floatValue = 0f
                        } else if (dragAcc.floatValue >= thresholdPx) {
                            if (canGoNewer) onGoNewer()
                            dragAcc.floatValue = 0f
                        }
                    }
                )
            }
    ) {
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.anthropometry_empty))
            }
            return@Surface
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)) {
            val cmValues = anthroSeries.filter { it.axis == AnthroAxis.CM }
                .flatMap { s -> entries.mapNotNull { e -> s.getValue(e) } }
            val kgValues = anthroSeries.filter { it.axis == AnthroAxis.KG }
                .flatMap { s -> entries.mapNotNull { e -> s.getValue(e) } }

            fun bounds(vals: List<Double>): Pair<Double, Double> {
                if (vals.isEmpty()) return 0.0 to 1.0
                val min = vals.minOrNull()!!
                val max = vals.maxOrNull()!!
                if (min == max) return (min - 1.0) to (max + 1.0)
                val pad = (max - min) * 0.10
                return (min - pad) to (max + pad)
            }

            val (cmMin, cmMax) = bounds(cmValues)
            val (kgMin, kgMax) = bounds(kgValues)

            val minDay = entries.minOf { it.date.toEpochDay() }
            val maxDayRaw = entries.maxOf { it.date.toEpochDay() }
            val maxDay = if (maxDayRaw == minDay) minDay + 1 else maxDayRaw

            val padLeft = 52f
            val padRight = 52f
            val padTop = 10f
            val padBottom = 24f

            val plotRight = size.width - padRight
            val plotBottom = size.height - padBottom

            fun xFor(date: LocalDate): Float {
                val t = (date.toEpochDay() - minDay).toFloat() / (maxDay - minDay).toFloat()
                return padLeft + t * (plotRight - padLeft)
            }

            fun yForCm(v: Double): Float {
                val t = ((v - cmMin) / (cmMax - cmMin)).toFloat()
                return plotBottom - t * (plotBottom - padTop)
            }

            fun yForKg(v: Double): Float {
                val t = ((v - kgMin) / (kgMax - kgMin)).toFloat()
                return plotBottom - t * (plotBottom - padTop)
            }

            // Grid lines (3)
            val gridColor = Color.Black.copy(alpha = 0.08f)
            for (i in 0..2) {
                val t = i / 2f
                val y = plotBottom - t * (plotBottom - padTop)
                drawLine(gridColor, Offset(padLeft, y), Offset(plotRight, y), strokeWidth = 1f)
            }

            // Axis labels/ticks
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 11.dp.toPx()
                color = android.graphics.Color.argb(180, 0, 0, 0)
            }

            fun drawLabel(text: String, x: Float, y: Float) {
                drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
            }

            drawLabel(cmUnit, 4f, 12.dp.toPx())
            drawLabel(kgUnit, size.width - padRight + 6f, 12.dp.toPx())

            val ticks = listOf(0f, 0.5f, 1f)
            for (t in ticks) {
                val y = plotBottom - t * (plotBottom - padTop)
                val cm = cmMin + (cmMax - cmMin) * t
                val kg = kgMin + (kgMax - kgMin) * t
                drawLabel(formatOneDecimal(cm), 4f, y + 4f)
                drawLabel(formatOneDecimal(kg), size.width - padRight + 6f, y + 4f)
            }

            // Date labels (start/end)
            drawLabel(entries.first().date.toString(), padLeft, size.height - 6f)
            drawLabel(entries.last().date.toString(), plotRight - 72f, size.height - 6f)

            // Series lines
            anthroSeries.forEach { s ->
                val points = entries.mapNotNull { e ->
                    val v = s.getValue(e) ?: return@mapNotNull null
                    val x = xFor(e.date)
                    val y = if (s.axis == AnthroAxis.CM) yForCm(v) else yForKg(v)
                    Offset(x, y)
                }
                if (points.size < 2) return@forEach

                val path = Path()
                path.moveTo(points.first().x, points.first().y)
                for (p in points.drop(1)) {
                    path.lineTo(p.x, p.y)
                }
                drawPath(path, color = s.color, style = Stroke(width = 3f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnthropometryInputDialog(
    entriesByDate: Map<LocalDate, AnthropometryEntry>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, List<Double?>) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    var date by remember { mutableStateOf(LocalDate.now()) }

    fun fillFromEntry(e: AnthropometryEntry?): List<String> = listOf(
        e?.armCm?.let { formatOneDecimal(it) } ?: "",
        e?.chestCm?.let { formatOneDecimal(it) } ?: "",
        e?.underChestCm?.let { formatOneDecimal(it) } ?: "",
        e?.waistCm?.let { formatOneDecimal(it) } ?: "",
        e?.bellyCm?.let { formatOneDecimal(it) } ?: "",
        e?.hipsCm?.let { formatOneDecimal(it) } ?: "",
        e?.thighCm?.let { formatOneDecimal(it) } ?: "",
        e?.weightKg?.let { formatOneDecimal(it) } ?: "",
    )

    var fields by remember { mutableStateOf(fillFromEntry(entriesByDate[date])) }

    val showDatePicker = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = date.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showDatePicker.value = true }) {
                        Text(stringResource(R.string.pick_date))
                    }
                }

                OutlinedTextField(
                    value = fields[0],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[0] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_arm_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[1],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[1] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_chest_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[2],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[2] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_under_chest_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[3],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[3] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_waist_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[4],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[4] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_belly_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[5],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[5] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_hips_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[6],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[6] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_thigh_cm)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields[7],
                    onValueChange = { newText ->
                        fields = fields.toMutableList().also { it[7] = newText }
                    },
                    label = { Text(stringResource(R.string.anthro_weight_kg)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = fields.map { parseOneDecimalOrNull(it) }
                onSave(date, values)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showDatePicker.value) {
        val initialMillis = remember(date) {
            date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        fields = fillFromEntry(entriesByDate[date])
                    }
                    showDatePicker.value = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun parseOneDecimalOrNull(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return t.replace(',', '.').toDoubleOrNull()
}

private fun formatOneDecimal(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    val s = String.format(Locale.US, "%.1f", r)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

private fun formatSignedOneDecimal(v: Double): String {
    val r = (v * 10.0).roundToInt() / 10.0
    val sign = if (r > 0) "+" else ""
    val s = String.format(Locale.US, "%.1f", r)
    val trimmed = if (s.endsWith(".0")) s.dropLast(2) else s
    return sign + trimmed
}


@Composable
private fun MenuTile(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(22.dp)
    val colorsEnabled = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val colorsDisabled = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .alpha(if (enabled) 1f else 0.55f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

    if (enabled) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colorsEnabled
        ) { content() }
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = colorsDisabled
        ) { content() }
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
                LazyColumn(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)) {
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
    var selectedEpochDay by rememberSaveable(initialEpochDay) { mutableLongStateOf(initialEpochDay) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }

    LaunchedEffect(selectedEpochDay) {
        val d = LocalDate.ofEpochDay(selectedEpochDay)
        vm.ensureGeneratedInRange(d, d)
    }

    val dateText = remember(selectedEpochDay) {
        val fmt =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
        selectedDate.format(fmt)
    }

    val dateTasksCount = remember(state.tasks, state.suppressedRecurrences, selectedDate) {
        state.tasks.count { t ->
            t.date == selectedDate && !isSuppressedTemplateTaskOnItsDate(
                t,
                state.suppressedRecurrences
            )
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

        var showAnthroDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        val savedMsg = stringResource(R.string.anthropometry_saved)


        OutlinedButton(
            onClick = { showAnthroDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.anthropometry_title))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { selectedEpochDay -= 1 }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.prev_day))
            }
            OutlinedButton(
                onClick = { selectedEpochDay = today.toEpochDay() },
                modifier = Modifier.weight(1f)
            ) {
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
                    Text(
                        stringResource(R.string.tasks_header),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DateTasksBlock(vm = vm, state = state, date = selectedDate)
                }
            }
        }

        if (showAnthroDialog) {
            val existing = state.anthropometry.firstOrNull { it.date == selectedDate }

            AnthropometryDayInputDialog(
                date = selectedDate,
                initialEntry = existing,
                onDismiss = { showAnthroDialog = false },
                onSave = { values ->
                    vm.saveAnthropometryForDate(
                        date = selectedDate,
                        armCm = values[0],
                        chestCm = values[1],
                        underChestCm = values[2],
                        waistCm = values[3],
                        bellyCm = values[4],
                        hipsCm = values[5],
                        thighCm = values[6],
                        weightKg = values[7]
                    )
                    Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show()
                    showAnthroDialog = false
                }
            )
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

    val baseIndex = today.year * 12 + (today.monthValue - 1)
    var monthIndex by rememberSaveable { mutableIntStateOf(baseIndex) }

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

    val anthroDates = remember(state.anthropometry) {
        state.anthropometry
            .filter { it.hasAnyValue() }
            .map { it.date }
            .toSet()
    }

    // Count tasks + subtasks by date for month cells (ignore suppressed template tasks on their own date).
    val visibleTasks = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences) }
    }

    val taskCountByDate = remember(visibleTasks) {
        visibleTasks.mapNotNull { it.date }.groupingBy { it }.eachCount()
    }

    val taskDateById = remember(visibleTasks) {
        // keep only tasks that actually have a date, so values are non-null
        visibleTasks.mapNotNull { t -> t.date?.let { d -> t.id to d } }.toMap()
    }

    val subtaskCountByDate: Map<LocalDate, Int> = remember(state.subtasks, taskDateById) {
        val m = mutableMapOf<LocalDate, Int>()
        for (st in state.subtasks) {
            val d = taskDateById[st.taskId] ?: continue
            m[d] = (m[d] ?: 0) + 1
        }
        m
    }

    val itemCountByDate = remember(taskCountByDate, subtaskCountByDate) {
        val m = mutableMapOf<LocalDate, Int>()
        for ((d, c) in taskCountByDate.entries) m[d] = (m[d] ?: 0) + c
        for ((d, c) in subtaskCountByDate.entries) m[d] = (m[d] ?: 0) + c
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
        Text(
            text = stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.headlineSmall
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(cells) { date ->
                if (date == null) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val count = itemCountByDate[date] ?: 0
                    val isToday = date == today
                    val hasAnthro = anthroDates.contains(date)


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
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasAnthro) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val stroke = 2.dp.toPx()
                                        drawCircle(
                                            color = Color(0xFFB7E6B0),
                                            radius = (size.minDimension - stroke) / 2f,
                                            style = Stroke(width = stroke)
                                        )
                                    }
                                }
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelMedium
                                )
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
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.back)) }
    }
}


/* ---------------------------
   Helpers
---------------------------- */
@Composable
private fun appLocale(): Locale {
    val cfg = LocalContext.current.resources.configuration
    return cfg.locales[0]
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

    var interval by remember { mutableIntStateOf((initial?.interval ?: 1).coerceAtLeast(1)) }

    var weekDays by remember {
        mutableStateOf(
            initial?.weekDays?.takeIf { it.isNotEmpty() } ?: setOf(defaultDow)
        )
    }

    var dayOfMonth by remember {
        mutableIntStateOf((initial?.dayOfMonth ?: defaultDom).coerceIn(1, 31))
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
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = opts.size
                            ),
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
                    if (freq == RepeatFreq.WEEKLY) weekDays.takeIf { it.isNotEmpty() } ?: setOf(
                        defaultDow
                    )
                    else emptySet()

                onConfirm(
                    RepeatRule(
                        freq = freq,
                        interval = interval.coerceAtLeast(1),
                        weekDays = safeWeekDays,
                        dayOfMonth = if (freq == RepeatFreq.MONTHLY) dayOfMonth.coerceIn(
                            1,
                            31
                        ) else null
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
            .sortedWith(compareBy({ it.order }, { it.id }))
    }
    if (tasks.isEmpty()) return

    var collapsedTaskIds by remember(date) { mutableStateOf<Set<Long>>(emptySet()) }

    var moveTaskId by remember { mutableStateOf<Long?>(null) }
    val showMoveTaskDatePicker = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    var copyTaskId by remember { mutableStateOf<Long?>(null) }
    val showCopyTaskDatePicker = remember { mutableStateOf(false) }

    var copySubtaskId by remember { mutableStateOf<Long?>(null) }
    val showCopySubtaskDatePicker = remember { mutableStateOf(false) }

    val moveSubtaskId = remember { mutableStateOf<Long?>(null) }

    val addSubtaskToTaskId = remember { mutableStateOf<Long?>(null) }
    var newSubtaskText by remember { mutableStateOf("") }
    var newSubtaskColor by remember { mutableStateOf<Long?>(null) }

    var editTaskId by remember { mutableStateOf<Long?>(null) }
    val editTaskText = remember { mutableStateOf("") }
    val editTaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showTaskRepeatPicker = remember { mutableStateOf(false) }

    var editSubtaskId by remember { mutableStateOf<Long?>(null) }
    val editSubtaskText = remember { mutableStateOf("") }
    val editSubtaskRepeatRule = remember { mutableStateOf<RepeatRule?>(null) }
    val showSubtaskRepeatPicker = remember { mutableStateOf(false) }

    val markerShape = remember { RoundedCornerShape(10.dp) }
    val markerAlpha = 0.18f

    tasks.forEach { task ->
        val subtasks = state.subtasks
            .filter { it.taskId == task.id }
            .sortedWith(compareBy({ it.order }, { it.id }))

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
                            editTaskText.value = task.description
                            editTaskRepeatRule.value = task.repeatRule
                            showTaskRepeatPicker.value = false
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = deco)
                )
            }

            TinyIconButton(
                onClick = { vm.moveTaskUp(task.id) },
                icon = Icons.Default.KeyboardArrowUp,
                cd = "Move task up"
            )
            TinyIconButton(
                onClick = { vm.moveTaskDown(task.id) },
                icon = Icons.Default.KeyboardArrowDown,
                cd = "Move task down"
            )
            TinyIconButton(
                onClick = { moveTaskId = task.id },
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                cd = "Move task"
            )
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
                    addSubtaskToTaskId.value = task.id
                    newSubtaskText = ""
                    newSubtaskColor = task.colorArgb
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
                                    editSubtaskText.value = st.description
                                    editSubtaskRepeatRule.value = st.repeatRule
                                    showSubtaskRepeatPicker.value = false
                                },
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = stDeco)
                        )
                    }

                    TinyIconButton(
                        onClick = { vm.moveSubtaskUp(st.id) },
                        icon = Icons.Default.KeyboardArrowUp,
                        cd = "Move subtask up"
                    )
                    TinyIconButton(
                        onClick = { vm.moveSubtaskDown(st.id) },
                        icon = Icons.Default.KeyboardArrowDown,
                        cd = "Move subtask down"
                    )
                    TinyIconButton(
                        onClick = { moveSubtaskId.value = st.id },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        cd = "Move subtask"
                    )
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
    if (moveTaskId != null && !showMoveTaskDatePicker.value) {
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
                        onClick = { showMoveTaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reschedule)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    moveTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (moveTaskId != null && showMoveTaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis = remember(moveTaskId, state.tasks) {
            val d = state.tasks.firstOrNull { it.id == moveTaskId }?.date ?: LocalDate.now()
            d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showMoveTaskDatePicker.value = false
                moveTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.rescheduleTaskToDate(moveTaskId!!, newDate)
                    }
                    showMoveTaskDatePicker.value = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMoveTaskDatePicker.value = false
                    moveTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (copyTaskId != null && !showCopyTaskDatePicker.value) {
        AlertDialog(
            onDismissRequest = { copyTaskId = null },
            title = { Text(stringResource(R.string.copy_task)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.copyTaskToDate(copyTaskId!!, LocalDate.now())
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            vm.copyTaskToDate(copyTaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            copyTaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopyTaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copyTaskId != null && showCopyTaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis =
            remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopyTaskDatePicker.value = false
                copyTaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.copyTaskToDate(copyTaskId!!, newDate)
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showCopyTaskDatePicker.value = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopyTaskDatePicker.value = false
                    copyTaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // Move subtask to another task
    if (moveSubtaskId.value != null) {
        val sub = state.subtasks.firstOrNull { it.id == moveSubtaskId.value }
        val currentTaskId = sub?.taskId

        AlertDialog(
            onDismissRequest = { moveSubtaskId.value = null },
            title = { Text(stringResource(R.string.move_subtask)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    val targets = state.tasks
                        .filter { it.id != currentTaskId }
                        .sortedWith(
                            compareBy(
                                { it.date?.toEpochDay() ?: Long.MIN_VALUE },
                                { it.order },
                                { it.id })
                        )

                    items(targets) { t ->
                        val dText = t.date?.toString() ?: stringResource(R.string.someday_tag)
                        TextButton(
                            onClick = {
                                vm.moveSubtask(moveSubtaskId.value!!, t.id)
                                moveSubtaskId.value = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${t.description} • $dText")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    moveSubtaskId.value = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Add subtask dialog
    if (addSubtaskToTaskId.value != null) {
        AlertDialog(
            onDismissRequest = { addSubtaskToTaskId.value = null },
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
                            vm.createSubtask(
                                addSubtaskToTaskId.value!!,
                                txt,
                                colorArgb = newSubtaskColor
                            )
                        }
                        addSubtaskToTaskId.value = null
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    addSubtaskToTaskId.value = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && !showCopySubtaskDatePicker.value) {
        AlertDialog(
            onDismissRequest = { copySubtaskId = null },
            title = { Text(stringResource(R.string.copy_subtask)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            vm.copySubtaskToDate(copySubtaskId!!, LocalDate.now())
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_today)) }

                    TextButton(
                        onClick = {
                            vm.copySubtaskToDate(copySubtaskId!!, LocalDate.now().plusDays(1))
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.toast_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                            copySubtaskId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.schedule_tomorrow)) }

                    TextButton(
                        onClick = { showCopySubtaskDatePicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_date)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copySubtaskId = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (copySubtaskId != null && showCopySubtaskDatePicker.value) {
        val zone = remember { ZoneId.systemDefault() }
        val initialMillis =
            remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showCopySubtaskDatePicker.value = false
                copySubtaskId = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        vm.copySubtaskToDate(copySubtaskId!!, newDate)
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showCopySubtaskDatePicker.value = false
                    copySubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCopySubtaskDatePicker.value = false
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
            onDismissRequest = { editTaskId = null; showTaskRepeatPicker.value = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editTaskText.value,
                    onValueChange = { editTaskText.value = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTaskDescription(editTaskId!!, editTaskText.value)
                    editTaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTaskRepeatPicker.value = true }) {
                        Text(
                            stringResource(R.string.repeat)
                        )
                    }

                    TextButton(onClick = {
                        vm.deleteTask(editTaskId!!)
                        editTaskId = null
                        showTaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editTaskId = null
                        showTaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    // Edit subtask dialog
    if (editSubtaskId != null) {
        AlertDialog(
            onDismissRequest = { editSubtaskId = null; showSubtaskRepeatPicker.value = false },
            title = { Text(stringResource(R.string.task_description_label)) },
            text = {
                OutlinedTextField(
                    value = editSubtaskText.value,
                    onValueChange = { editSubtaskText.value = it },
                    label = { Text(stringResource(R.string.task_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSubtaskDescription(editSubtaskId!!, editSubtaskText.value)
                    editSubtaskId = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSubtaskRepeatPicker.value = true }) {
                        Text(
                            stringResource(R.string.repeat)
                        )
                    }

                    TextButton(onClick = {
                        vm.deleteSubtask(editSubtaskId!!)
                        editSubtaskId = null
                        showSubtaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.remove)) }

                    TextButton(onClick = {
                        editSubtaskId = null
                        showSubtaskRepeatPicker.value = false
                    }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showTaskRepeatPicker.value && editTaskId != null) {
        RepeatPickerDialog(
            initial = editTaskRepeatRule.value,
            onDismiss = { showTaskRepeatPicker.value = false },
            onConfirm = { rule ->
                editTaskRepeatRule.value = rule
                vm.setTaskRepeatRule(editTaskId!!, rule)
                showTaskRepeatPicker.value = false
            }
        )
    }

    if (showSubtaskRepeatPicker.value && editSubtaskId != null) {
        RepeatPickerDialog(
            initial = editSubtaskRepeatRule.value,
            onDismiss = { showSubtaskRepeatPicker.value = false },
            onConfirm = { rule ->
                editSubtaskRepeatRule.value = rule
                vm.setSubtaskRepeatRule(editSubtaskId!!, rule)
                showSubtaskRepeatPicker.value = false
            }
        )
    }
}

private fun orderedWeekDays(first: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries.toList()
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
                pluralStringResource(
                    R.plurals.repeat_weekly_every_n_weeks_on,
                    interval,
                    interval,
                    names
                )
            }
        }

        RepeatFreq.MONTHLY -> {
            val dom = rule.dayOfMonth ?: anchor?.dayOfMonth ?: 1

            if (interval == 1) {
                stringResource(R.string.repeat_monthly_every_month_on_day, dom)
            } else {
                pluralStringResource(
                    R.plurals.repeat_monthly_every_n_months_on_day,
                    interval,
                    interval,
                    dom
                )
            }
        }
    }
}


private fun isSuppressedTemplateTaskOnItsDate(task: Task, suppressed: Set<String>): Boolean {
    val d = task.date ?: return false
    return task.originTaskId == null && suppressed.contains("T:${task.id}:${d.toEpochDay()}")
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
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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