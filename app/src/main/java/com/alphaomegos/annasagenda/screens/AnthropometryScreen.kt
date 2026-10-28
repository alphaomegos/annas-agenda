package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.app.AppIcons
import com.alphaomegos.annasagenda.support.WeightImportState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import com.alphaomegos.annasagenda.support.ageIn
import com.alphaomegos.annasagenda.support.baselineKcal
import com.alphaomegos.annasagenda.support.weightOn
import com.alphaomegos.annasagenda.support.runningPlanRunsDone
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.Info
import com.alphaomegos.annasagenda.components.ProfileDialog
import com.alphaomegos.annasagenda.support.newWeightForecast
import com.alphaomegos.annasagenda.support.WeightForecast
import com.alphaomegos.annasagenda.support.ForecastMethod
import com.alphaomegos.annasagenda.components.TopBarBack
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.alphaomegos.annasagenda.support.canPageAnthropometryBack
import com.alphaomegos.annasagenda.support.canPageAnthropometryForward
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.model.AnthropometryEntry
import com.alphaomegos.annasagenda.support.AnthropometryRange
import com.alphaomegos.annasagenda.support.CurvePoint
import com.alphaomegos.annasagenda.support.DateWindow
import com.alphaomegos.annasagenda.support.anthropometryEntriesIn
import com.alphaomegos.annasagenda.support.anthropometryWindowFor
import com.alphaomegos.annasagenda.support.caloriesBurnedRunning
import com.alphaomegos.annasagenda.dialogs.AnthropometryInputDialog
import com.alphaomegos.annasagenda.dialogs.AnthropometryDayInputDialog
import com.alphaomegos.annasagenda.app.AppViewModel
import androidx.compose.ui.graphics.toArgb
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.appExtraColors
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.support.smoothCurveSegments
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.formatOneDecimal
import com.alphaomegos.annasagenda.util.formatTwoDecimals
import com.alphaomegos.annasagenda.util.formatSignedOneDecimal
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.support.KCAL_PER_KG_FAT
import com.alphaomegos.annasagenda.support.calorieDeficitInRange
import com.alphaomegos.annasagenda.model.AnthropometryFieldIds
import com.alphaomegos.annasagenda.model.defaultAnthropometryFieldIds

enum class AnthropometryAxis { CM, KG }

data class AnthropometryFieldDef(
    val id: String,
    val labelRes: Int,
    val axis: AnthropometryAxis,
    val color: Color,
    val getValue: (AnthropometryEntry) -> Double?
)

val anthropometryFieldDefs = listOf(
    AnthropometryFieldDef(
        id = AnthropometryFieldIds.ARM,
        labelRes = R.string.anthro_arm_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFF1E88E5),
    ) { it.armCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.CHEST,
        labelRes = R.string.anthro_chest_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFFE53935),
    ) { it.chestCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.UNDER_CHEST,
        labelRes = R.string.anthro_under_chest_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFF8E24AA),
    ) { it.underChestCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.WAIST,
        labelRes = R.string.anthro_waist_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFFFB8C00),
    ) { it.waistCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.BELLY,
        labelRes = R.string.anthro_belly_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFF43A047),
    ) { it.bellyCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.HIPS,
        labelRes = R.string.anthro_hips_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFF00ACC1),
    ) { it.hipsCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.THIGH,
        labelRes = R.string.anthro_thigh_cm,
        axis = AnthropometryAxis.CM,
        color = Color(0xFF6D4C41),
    ) { it.thighCm },

    AnthropometryFieldDef(
        id = AnthropometryFieldIds.WEIGHT,
        labelRes = R.string.anthro_weight_kg,
        axis = AnthropometryAxis.KG,
        color = Color(0xFF546E7A),
    ) { it.weightKg },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnthropometryScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val state by vm.anthropometry.collectAsState()
    val today = remember { LocalDate.now() }

    val deficit30 = remember(state) {
        val start30 = today.minusDays(29)
        calorieDeficitInRange(state.calorieGoalChanges, state.foodLog, start30, today)
    }

    // What the running added to it. Kept as its own number rather than folded
    // into the one above, because the screen says it out loud: a projection
    // that moved when a run was written down, with nothing to say why, would
    // look like the arithmetic drifting.
    val burned30 = remember(state) {
        caloriesBurnedRunning(
            workouts = state.runningWorkouts,
            anthropometry = state.anthropometry,
            from = today.minusDays(29),
            to = today,
        )
    }

    val potentialKg = (deficit30 + burned30) / KCAL_PER_KG_FAT
    // The new forecast (28.10) is over the period on screen; the window is below.
    var forecastInfoOpen by rememberSaveable { mutableStateOf(false) }
    var weightImportAsked by rememberSaveable { mutableStateOf(false) }
    val weightImport by vm.weightImport.collectAsState()
    val weightPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readSamsungWeight(uri)
    }
    if (weightImportAsked) {
        AlertDialog(
            onDismissRequest = { weightImportAsked = false },
            title = { Text(stringResource(R.string.weight_import_button)) },
            text = { Text(stringResource(R.string.weight_import_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    weightImportAsked = false
                    weightPicker.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.steps_import_pick)) }
            },
            dismissButton = { TextButton(onClick = { weightImportAsked = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    when (val w = weightImport) {
        null -> Unit
        is WeightImportState.Conflicts -> AlertDialog(
            onDismissRequest = vm::dismissWeightImport,
            title = { Text(stringResource(R.string.weight_import_button)) },
            text = { Text(stringResource(R.string.weight_import_conflicts, w.conflicts)) },
            confirmButton = { TextButton(onClick = { vm.resolveWeightImport(true) }) { Text(stringResource(R.string.weight_import_take_samsung)) } },
            dismissButton = { TextButton(onClick = { vm.resolveWeightImport(false) }) { Text(stringResource(R.string.weight_import_keep_app)) } },
        )
        else -> AlertDialog(
            onDismissRequest = vm::dismissWeightImport,
            title = { Text(stringResource(R.string.weight_import_button)) },
            text = {
                Text(
                    when (w) {
                        is WeightImportState.Done -> stringResource(
                            R.string.weight_import_done,
                            w.outcome.inFile,
                            w.outcome.first?.let { formatShortDate(it, appLocale()) }.orEmpty(),
                            w.outcome.last?.let { formatShortDate(it, appLocale()) }.orEmpty(),
                            w.outcome.added, w.outcome.replaced, w.outcome.kept,
                        )
                        WeightImportState.NotAnExport -> stringResource(R.string.weight_import_not_export)
                        is WeightImportState.Failed -> stringResource(R.string.steps_import_failed, w.reason)
                        is WeightImportState.Conflicts -> ""
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissWeightImport) { Text(stringResource(R.string.ok)) } },
        )
    }
    var profileOpen by rememberSaveable { mutableStateOf(false) }

    val allEntries = remember(state.anthropometry) {
        state.anthropometry.sortedBy { it.date }
    }
    val entriesByDate = remember(allEntries) { allEntries.associateBy { it.date } }

    // The range lives in the saved state since 0138, so leaving the screen
    // and coming back shows the one last chosen rather than a month again.
    // 0 is the window ending today, -1 the one before it. Not kept past the
    // screen: coming back starts on the present again.
    var page by rememberSaveable { mutableIntStateOf(0) }

    val range = state.anthropometryRange
    val custom = state.anthropometryCustomRange

    val dateWindow = remember(range, custom, allEntries, today, page) {
        anthropometryWindowFor(
            range = range,
            today = today,
            custom = custom,
            entries = allEntries,
            page = page,
        )
    }
    val canBack = canPageAnthropometryBack(range, dateWindow, allEntries)
    val canForward = canPageAnthropometryForward(range, page)

    val window = remember(allEntries, dateWindow) {
        anthropometryEntriesIn(allEntries, dateWindow)
    }
    val newForecast = remember(state, dateWindow, today) {
        newWeightForecast(
            from = dateWindow.from,
            to = dateWindow.to,
            today = today,
            sex = state.profileSex,
            heightCm = state.heightCm,
            birthYear = state.profileBirthYear,
            anthropometry = state.anthropometry,
            foodLog = state.foodLog,
            stepDays = state.stepDays,
            workouts = state.runningWorkouts,
            planRuns = runningPlanRunsDone(state.runningPlanEntries, state.runningDoneTaskIds, today),
            normKcal = state.dailyNormKcal,
        )
    }
    // The day's norm as it stands today (28.10): the user's own, or the formula's for the last weight.
    val normToday: Int? = remember(state, today) {
        state.dailyNormKcal ?: run {
            val sex = state.profileSex ?: return@run null
            val h = state.heightCm ?: return@run null
            val y = state.profileBirthYear ?: return@run null
            val w = weightOn(today, state.anthropometry) ?: return@run null
            baselineKcal(sex, w, h, ageIn(today.year, y)).toInt()
        }
    }
    if (forecastInfoOpen) {
        ForecastInfoDialog(
            method = state.weightForecastMethod,
            normKcal = normToday,
            ownNorm = state.dailyNormKcal,
            onNorm = vm::setDailyNorm,
            onMethod = vm::setWeightForecastMethod,
            onProfile = { forecastInfoOpen = false; profileOpen = true },
            onDismiss = { forecastInfoOpen = false },
        )
    }
    if (profileOpen) {
        ProfileDialog(
            sex = state.profileSex,
            heightCm = state.heightCm,
            birthYear = state.profileBirthYear,
            onSave = { sx, h, y -> vm.setProfile(sx, h, y); profileOpen = false },
            onDismiss = { profileOpen = false },
        )
    }

    val showInput = rememberSaveable { mutableStateOf(false) }

    val showSettings = rememberSaveable { mutableStateOf(false) }

    // The measurement being edited from the list, as its epoch day.
    var editingEntryDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val visibleFieldDefs = remember(state.anthropometryEnabledFieldIds) {
        anthropometryFieldDefs
            .filter { it.id in state.anthropometryEnabledFieldIds }
            .ifEmpty { anthropometryFieldDefs }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.anthropometry_title)) },
                navigationIcon = {
                    TopBarBack(onBack = onBack)
                },
                actions = {
                    // The new look (28.10): a measurement is added from here, not
                    // from a wide button under the list.
                    // Weights from a Samsung Health export (28.10), once in a while.
                    IconButton(onClick = { weightImportAsked = true }) {
                        Icon(AppIcons.stepsImport, contentDescription = stringResource(R.string.weight_import_button))
                    }
                    if (state.newLook) {
                        IconButton(onClick = { showInput.value = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.enter_data))
                        }
                    }
                    IconButton(onClick = { showSettings.value = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.anthropometry_settings_open)
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
            AnthropometryRangeBar(
                compact = state.newLook,
                selected = range,
                custom = custom,
                onSelect = {
                    vm.setAnthropometryRange(it)
                    page = 0
                },
                onCustomPicked = { picked ->
                    vm.setAnthropometryCustomRange(picked)
                    page = 0
                },
            )

            if (range != AnthropometryRange.ALL) {
                AnthropometryPager(
                    window = dateWindow,
                    canBack = canBack,
                    canForward = canForward,
                    onBack = { page -= 1 },
                    onForward = { page += 1 },
                )
            }

            AnthropometryChart(
                entries = window,
                series = visibleFieldDefs,
                // A swipe to the right goes back in time, the way a calendar
                // pages: what was before is to the left.
                onSwipeBack = { if (canBack) page -= 1 },
                onSwipeForward = { if (canForward) page += 1 },
                filled = state.newLook,
            )

            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (allEntries.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.anthropometry_empty))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 5.1: the whole forecast block, or none of it.
                        if (state.anthropometryShowForecast) item {
                            if (state.weightForecastMethod == ForecastMethod.NEW) {
                                NewForecastBlock(
                                    forecast = newForecast,
                                    normKcal = normToday,
                                    ownNorm = state.dailyNormKcal != null,
                                    onInfo = { forecastInfoOpen = true },
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val okGreen = appExtraColors.positive
                                        val badRed = MaterialTheme.colorScheme.error
                                        val color = if (potentialKg >= 0.0) okGreen else badRed

                                        val kgText = formatTwoDecimals(potentialKg)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.calorimeter_month_projection),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "$kgText ${stringResource(R.string.kg_short)}",
                                                color = color,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.widthIn(min = 140.dp)
                                            )
                                        }

                                        // Said only when there is something to say. A line
                                        // reading "running included: 0 kcal" on a screen
                                        // belonging to somebody who does not run is noise
                                        // that never goes away.
                                        if (burned30 > 0) {
                                            Text(
                                                text = stringResource(
                                                    R.string.anthropometry_running_included,
                                                    burned30,
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                                            }
                                    IconButton(onClick = { forecastInfoOpen = true }) {
                                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.forecast_info))
                                    }
                                }
                            }
                        }

                        items(visibleFieldDefs) { s ->
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
                                if (s.axis == AnthropometryAxis.KG) stringResource(R.string.kg_short) else stringResource(
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
                                    // The new look says what the difference is (28.10): since the first measurement.
                                    text = if (state.newLook) stringResource(
                                        R.string.anthro_since_first, formatOneDecimal(last.second), unit,
                                        formatSignedOneDecimal(diff), formatShortDate(first.first, appLocale()),
                                    ) else "${formatOneDecimal(last.second)} $unit (${
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

                        // 5.3: every measurement, newest first; a tap opens
                        // the same dialog the day is entered with.
                        if (state.anthropometryShowEntries) {
                            if (!state.newLook) item {
                                Text(
                                    text = stringResource(R.string.anthro_entries_header),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(allEntries.asReversed(), key = { it.date.toEpochDay() }) { e ->
                                if (state.newLook) NewAnthropometryEntryRow(
                                    entry = e,
                                    fields = visibleFieldDefs,
                                    onClick = { editingEntryDay = e.date.toEpochDay() },
                                ) else AnthropometryEntryRow(
                                    entry = e,
                                    fields = visibleFieldDefs,
                                    onClick = { editingEntryDay = e.date.toEpochDay() },
                                )
                            }
                        }
                    }
                }
            }

            if (!state.newLook) {
                Button(
                    onClick = { showInput.value = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.enter_data))
                }
            }
        }
    }

    if (showInput.value) {
        AnthropometryInputDialog(
            entriesByDate = entriesByDate,
            enabledFieldIds = state.anthropometryEnabledFieldIds,
            onDismiss = { showInput.value = false },
            onSave = { date, valuesByFieldId ->
                vm.saveAnthropometryForDate(
                    date = date,
                    valuesByFieldId = valuesByFieldId
                )
                Toast.makeText(ctx, ctx.getString(R.string.anthropometry_saved), Toast.LENGTH_SHORT)
                    .show()
                showInput.value = false
            }
        )
    }
    val editingDay = editingEntryDay
    if (editingDay != null) {
        val date = LocalDate.ofEpochDay(editingDay)
        AnthropometryDayInputDialog(
            date = date,
            initialEntry = entriesByDate[date],
            enabledFieldIds = state.anthropometryEnabledFieldIds,
            onDismiss = { editingEntryDay = null },
            onSave = { valuesByFieldId ->
                vm.saveAnthropometryForDate(date = date, valuesByFieldId = valuesByFieldId)
                editingEntryDay = null
            },
        )
    }

    if (showSettings.value) {
        AnthropometryFieldsDialog(
            fieldDefs = anthropometryFieldDefs,
            enabledFieldIds = state.anthropometryEnabledFieldIds,
            showForecast = state.anthropometryShowForecast,
            showEntries = state.anthropometryShowEntries,
            onDismiss = { showSettings.value = false },
            onSave = { enabledIds, forecast, entries ->
                vm.setAnthropometryEnabledFieldIds(enabledIds)
                vm.setAnthropometryDisplay(showForecast = forecast, showEntries = entries)
                showSettings.value = false
            }
        )
    }
}

/**
 * "‹ 27 июн. – 26 июл. ›" above the chart: which stretch is shown, and the way
 * to the one before or after it. The arrows go grey at the ends — back when
 * nothing older was measured, forward at the window ending today.
 */
@Composable
private fun AnthropometryPager(
    window: DateWindow,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val locale = appLocale()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.anthro_page_back),
            )
        }
        Text(
            text = stringResource(
                R.string.anthro_page_window,
                formatShortDate(window.from, locale),
                formatShortDate(window.to, locale),
            ),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onForward, enabled = canForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.anthro_page_forward),
            )
        }
    }
}

/** How far a finger has to travel sideways on the chart to turn a page. */
private const val CHART_SWIPE_DP = 48

@Composable
private fun AnthropometryChart(
    entries: List<AnthropometryEntry>,
    series: List<AnthropometryFieldDef>,
    onSwipeBack: () -> Unit,
    onSwipeForward: () -> Unit,
    filled: Boolean = false,
) {
    val cmUnit = stringResource(R.string.cm_short)
    val kgUnit = stringResource(R.string.kg_short)

    val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { CHART_SWIPE_DP.dp.toPx() }
    val latestSwipeBack by androidx.compose.runtime.rememberUpdatedState(onSwipeBack)
    val latestSwipeForward by androidx.compose.runtime.rememberUpdatedState(onSwipeForward)

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(Unit) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        when {
                            travelled > swipeThresholdPx -> latestSwipeBack()
                            travelled < -swipeThresholdPx -> latestSwipeForward()
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        travelled += amount
                    },
                )
            }
    ) {
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.anthropometry_empty))
            }
            return@Surface
        }

        // Read before the draw scope: a DrawScope is not a composition, so it
        // cannot reach the theme. These used to be black literals, which is
        // why the chart vanished into a dark background.
        val gridColor = appExtraColors.chartGrid
        val labelArgb = appExtraColors.chartLabel.toArgb()

        val locale = appLocale()
        val firstDateLabel = formatShortDate(entries.first().date, locale)
        val lastDateLabel = formatShortDate(entries.last().date, locale)
        // A day picked on the chart (28.10, as on the steps chart): a tap, or a
        // long press and a slide; the day nearest the finger, its values shown.
        var picked by rememberSaveable(entries.size, entries.first().date.toEpochDay()) { mutableStateOf<Long?>(null) }
        val cmOut = series.any { it.axis == AnthropometryAxis.CM }
        val kgOut = series.any { it.axis == AnthropometryAxis.KG }
        val days = entries.map { it.date.toEpochDay() }
        fun nearestDay(x: Float, width: Int): Long? {
            if (days.isEmpty()) return null
            val l = if (cmOut) 52f else 12f
            val r = width - (if (kgOut) 52f else 12f)
            val lo = days.min()
            val hi = days.max()
            if (hi == lo) return lo
            return days.minByOrNull { d -> kotlin.math.abs(l + (d - lo).toFloat() / (hi - lo) * (r - l) - x) }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .pointerInput(entries, series) {
                    detectTapGestures { o -> nearestDay(o.x, size.width)?.let { picked = if (picked == it) null else it } }
                }
                .pointerInput(entries, series) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o -> picked = nearestDay(o.x, size.width) },
                        onDrag = { change, _ ->
                            change.consume()
                            picked = nearestDay(change.position.x, size.width)
                        },
                    )
                }
        ) {
            val cmValues = series.filter { it.axis == AnthropometryAxis.CM }
                .flatMap { s -> entries.mapNotNull { e -> s.getValue(e) } }
            val kgValues = series.filter { it.axis == AnthropometryAxis.KG }
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

            // A scale is drawn only for an axis something on the chart uses:
            // with weight alone, a column of centimetres described nothing.
            val cmInUse = series.any { it.axis == AnthropometryAxis.CM }
            val kgInUse = series.any { it.axis == AnthropometryAxis.KG }

            val padLeft = if (cmInUse) 52f else 12f
            val padRight = if (kgInUse) 52f else 12f
            val padTop = 10f
            // Room under the plot for the dates (28.10): 24 px was less than the
            // text is tall, and the lowest value sat on the last date.
            val padBottom = 18.dp.toPx()

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
            for (i in 0..2) {
                val t = i / 2f
                val y = plotBottom - t * (plotBottom - padTop)
                drawLine(gridColor, Offset(padLeft, y), Offset(plotRight, y), strokeWidth = 1f)
            }

            // Axis labels/ticks
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 11.dp.toPx()
                color = labelArgb
            }

            fun drawLabel(text: String, x: Float, y: Float) {
                drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
            }

            // The unit with the top value, not a label of its own on top of it (28.10).
            val ticks = listOf(0f, 0.5f, 1f)
            for (t in ticks) {
                val y = plotBottom - t * (plotBottom - padTop)
                val cm = cmMin + (cmMax - cmMin) * t
                val kg = kgMin + (kgMax - kgMin) * t
                val top = t == 1f
                if (cmInUse) drawLabel(formatOneDecimal(cm) + if (top) " $cmUnit" else "", 4f, y + 4f)
                if (kgInUse) drawLabel(formatOneDecimal(kg) + if (top) " $kgUnit" else "", size.width - padRight + 6f, y + 4f)
            }

            // Date labels (start/end). Formatted before the draw scope, for
            // the same reason the colours are read before it: a DrawScope is
            // not a composition and cannot ask what language the app is in.
            drawLabel(firstDateLabel, padLeft, size.height - 4f)
            // The last date ends where the plot does — measured, not guessed at 72 px (28.10).
            drawLabel(lastDateLabel, plotRight - paint.measureText(lastDateLabel), size.height - 4f)

            // Series lines.
            //
            // Drawn as a monotone cubic rather than as the segments joining
            // the dots. The curve's one promise is that between two
            // measurements it stays between their values -- see
            // smoothCurveSegments, and the reason it is not the usual spline.
            series.forEach { s ->
                val points = entries.mapNotNull { e ->
                    val v = s.getValue(e) ?: return@mapNotNull null
                    val x = xFor(e.date)
                    val y = if (s.axis == AnthropometryAxis.CM) yForCm(v) else yForKg(v)
                    CurvePoint(x, y)
                }

                val hops = smoothCurveSegments(points)
                if (hops.isEmpty()) return@forEach

                val path = Path()
                path.moveTo(points.first().x, points.first().y)
                hops.forEach { hop ->
                    path.cubicTo(
                        hop.control1.x, hop.control1.y,
                        hop.control2.x, hop.control2.y,
                        hop.end.x, hop.end.y,
                    )
                }
                // The new look (28.10): a soft fill under the line, fading to nothing.
                if (filled) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, plotBottom)
                        lineTo(points.first().x, plotBottom)
                        close()
                    }
                    drawPath(
                        area,
                        brush = Brush.verticalGradient(listOf(s.color.copy(alpha = 0.22f), s.color.copy(alpha = 0f)), startY = padTop, endY = plotBottom),
                    )
                }
                drawPath(path, color = s.color, style = Stroke(width = 3f))
            }
            picked?.let { day ->
                val e = entries.firstOrNull { it.date.toEpochDay() == day } ?: return@let
                val x = xFor(e.date)
                drawLine(gridColor, Offset(x, padTop), Offset(x, plotBottom), strokeWidth = 2.dp.toPx())
                val values = series.mapNotNull { s ->
                    s.getValue(e)?.let { v -> formatOneDecimal(v) + " " + if (s.axis == AnthropometryAxis.KG) kgUnit else cmUnit }
                }
                val text = formatShortDate(e.date, locale) + "   " + values.joinToString("   ")
                val w = paint.measureText(text)
                drawContext.canvas.nativeCanvas.drawText(text, (x - w / 2).coerceIn(padLeft, (plotRight - w).coerceAtLeast(padLeft)), padTop + 12.dp.toPx(), paint)
            }
        }
    }
}

/** A set of field ids, as a list a Bundle can hold. */
private val fieldIdSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
internal fun AnthropometryFieldsDialog(
    fieldDefs: List<AnthropometryFieldDef>,
    enabledFieldIds: Set<String>,
    showForecast: Boolean,
    showEntries: Boolean,
    onDismiss: () -> Unit,
    onSave: (Set<String>, showForecast: Boolean, showEntries: Boolean) -> Unit,
) {
    var pendingForecast by rememberSaveable(showForecast) { mutableStateOf(showForecast) }
    var pendingEntries by rememberSaveable(showEntries) { mutableStateOf(showEntries) }

    // Saveable: the dialog itself survives turning the phone, so the boxes
    // ticked in it have to as well — otherwise it comes back showing the
    // saved choice as if nothing had been clicked.
    val pendingIds = rememberSaveable(enabledFieldIds, stateSaver = fieldIdSetSaver) {
        mutableStateOf(
            enabledFieldIds.ifEmpty { defaultAnthropometryFieldIds() }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anthropometry_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fieldDefs.forEach { field ->
                    val isChecked = field.id in pendingIds.value
                    val canUncheck = pendingIds.value.size > 1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pendingIds.value = when {
                                    isChecked && canUncheck -> pendingIds.value - field.id
                                    !isChecked -> pendingIds.value + field.id
                                    else -> pendingIds.value
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                pendingIds.value = when {
                                    checked -> pendingIds.value + field.id
                                    canUncheck -> pendingIds.value - field.id
                                    else -> pendingIds.value
                                }
                            }
                        )

                        Text(
                            text = stringResource(field.labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // What the screen shows besides the chart (5.1, 5.3).
                listOf(
                    Triple(R.string.anthro_show_forecast, pendingForecast) { v: Boolean -> pendingForecast = v },
                    Triple(R.string.anthro_show_entries, pendingEntries) { v: Boolean -> pendingEntries = v },
                ).forEach { (labelRes, checked, set) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { set(!checked) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { set(it) })
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pendingIds.value, pendingForecast, pendingEntries) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


/**
 * One measured day in the list under the chart (5.3): the date, then each
 * chosen field that has a value that day. Tapping it opens the day.
 */
@Composable
private fun AnthropometryEntryRow(
    entry: AnthropometryEntry,
    fields: List<AnthropometryFieldDef>,
    onClick: () -> Unit,
) {
    val locale = appLocale()
    val cm = stringResource(R.string.cm_short)
    val kg = stringResource(R.string.kg_short)

    val values = fields.mapNotNull { f ->
        val v = f.getValue(entry) ?: return@mapNotNull null
        Triple(f, v, if (f.axis == AnthropometryAxis.KG) kg else cm)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = formatShortDate(entry.date, locale),
            style = MaterialTheme.typography.labelLarge,
        )
        values.forEach { (f, v, unit) ->
            Text(
                text = stringResource(
                    R.string.anthro_entry_value,
                    stringResource(f.labelRes).substringBefore(','),
                    formatOneDecimal(v),
                    unit,
                ),
                color = f.color,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The new forecast's lines (28.10): «Potential change over the period» and the
 * kilograms, the «i» beside them; under them what they are made of — eaten,
 * steps, runs — and how far the scales disagreed, if weighed twice in the
 * period. Without the profile, a line saying what is missing.
 */
@Composable
private fun NewForecastBlock(forecast: WeightForecast?, normKcal: Int?, ownNorm: Boolean, onInfo: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val count = remember(locale) { java.text.NumberFormat.getIntegerInstance(locale) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.forecast_period_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (forecast != null) {
            val color = if (forecast.changeKg <= 0.0) appExtraColors.positive else MaterialTheme.colorScheme.error
            Text(
                (if (forecast.changeKg > 0) "+" else "") + formatTwoDecimals(forecast.changeKg) + " " + stringResource(R.string.kg_short),
                color = color,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.forecast_info)) }
    }
    // The day's norm, where it can be seen — and changed, under «i» (28.10).
    normKcal?.let { n ->
        Text(
            stringResource(if (ownNorm) R.string.forecast_norm_own else R.string.forecast_norm_formula, count.format(n)),
            style = MaterialTheme.typography.labelMedium,
        )
    }
    if (forecast == null) {
        Text(stringResource(R.string.forecast_need_profile), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        stringResource(
            R.string.forecast_breakdown_days,
            forecast.loggedDays, forecast.days,
            count.format(forecast.eatenKcal),
            count.format(forecast.steps), formatTwoDecimals(forecast.stepsKg),
            formatOneDecimal(forecast.runKm), formatTwoDecimals(forecast.runKg),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The scales against the forecast, marked by how far apart (28.10):
    // within a kilo green, within three amber, beyond that red.
    forecast.discrepancyKg?.let { d ->
        val far = kotlin.math.abs(d)
        val tone = when {
            far <= 1.0 -> appExtraColors.positive
            far <= 3.0 -> androidx.compose.ui.graphics.Color(0xFFB26A00)
            else -> MaterialTheme.colorScheme.error
        }
        Surface(color = tone.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                stringResource(R.string.forecast_discrepancy, (if (d > 0) "+" else "") + formatTwoDecimals(d)),
                style = MaterialTheme.typography.labelMedium,
                color = tone,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** «i» (28.10): which forecast is chosen and how it is worked out; the choice; the profile. */
@Composable
private fun ForecastInfoDialog(
    method: ForecastMethod,
    normKcal: Int?,
    ownNorm: Int?,
    onNorm: (Int?) -> Unit,
    onMethod: (ForecastMethod) -> Unit,
    onProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    var normText by rememberSaveable { mutableStateOf(ownNorm?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forecast_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == ForecastMethod.NEW, onClick = { onMethod(ForecastMethod.NEW) }, label = { Text(stringResource(R.string.day_screen_new)) })
                    FilterChip(selected = method == ForecastMethod.SUMMER_2026, onClick = { onMethod(ForecastMethod.SUMMER_2026) }, label = { Text(stringResource(R.string.day_screen_summer_2025)) })
                }
                Text(
                    stringResource(if (method == ForecastMethod.NEW) R.string.forecast_explain_new else R.string.forecast_explain_summer),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (method == ForecastMethod.NEW) {
                    // The day's norm (28.10): the formula's, or one's own.
                    normKcal?.let { Text(stringResource(R.string.forecast_norm_formula, it.toString()), style = MaterialTheme.typography.labelLarge) }
                    OutlinedTextField(
                        value = normText,
                        onValueChange = { v -> normText = v.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.forecast_norm_own_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onNorm(normText.toIntOrNull()) }) { Text(stringResource(R.string.save)) }
                        TextButton(onClick = { normText = ""; onNorm(null) }) { Text(stringResource(R.string.forecast_norm_reset)) }
                    }
                }
                TextButton(onClick = onProfile) { Text(stringResource(R.string.profile_title)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/**
 * A measurement in the new look (28.10): one line — the date, the values in
 * their colours, «›» — rather than the date over a line per value.
 */
@Composable
private fun NewAnthropometryEntryRow(
    entry: AnthropometryEntry,
    fields: List<AnthropometryFieldDef>,
    onClick: () -> Unit,
) {
    val locale = appLocale()
    val cm = stringResource(R.string.cm_short)
    val kg = stringResource(R.string.kg_short)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatShortDate(entry.date, locale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        fields.forEach { f ->
            val v = f.getValue(entry) ?: return@forEach
            Text(
                formatOneDecimal(v) + " " + if (f.axis == AnthropometryAxis.KG) kg else cm,
                color = f.color,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
