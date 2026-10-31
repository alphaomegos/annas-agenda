package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.alphaomegos.annasagenda.support.days
import com.alphaomegos.annasagenda.support.anthropometryPanClamp
import com.alphaomegos.annasagenda.support.anthropometryPanned
import com.alphaomegos.annasagenda.support.anthropometryCarriedLast
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
import com.alphaomegos.annasagenda.components.ImportIconButton
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

    val baseWindow = remember(range, custom, allEntries, today, page) {
        anthropometryWindowFor(
            range = range,
            today = today,
            custom = custom,
            entries = allEntries,
            page = page,
        )
    }
    // Moved by hand (29.10, new look): the chart slides day by day under the
    // finger and stays where it is let go; never past today. The arrows still
    // page whole periods, from where the page starts.
    var panDays by rememberSaveable(range, custom, page) { mutableIntStateOf(0) }
    val dateWindow = remember(baseWindow, panDays, today) { anthropometryPanned(baseWindow, panDays, today) }
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
                    ImportIconButton(stringResource(R.string.weight_import_button)) { weightImportAsked = true }
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

            // The new look (29.10): the weight on its own card — beside the chart when the
            // screen is wide (the unfolded Fold), under it when not.
            val wideCard = state.newLook && LocalConfiguration.current.screenWidthDp >= 600
            // Nothing measured in the period (29.10): the last measurement before it, level.
            val carried = if (state.anthropometryCarryLast) anthropometryCarriedLast(allEntries, dateWindow) else null
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(if (wideCard) 1.6f else 1f)) {
                    AnthropometryChart(
                        entries = carried?.entries ?: window,
                        series = visibleFieldDefs,
                        // A swipe to the right goes back in time, the way a calendar
                        // pages: what was before is to the left.
                        onSwipeBack = { if (canBack) page -= 1 },
                        onSwipeForward = { if (canForward) page += 1 },
                        filled = state.newLook,
                        onPanDays = if (state.newLook) { d -> panDays = anthropometryPanClamp(baseWindow, panDays + d, today) } else null,
                        windowDays = dateWindow.days(),
                    )
                    carried?.let { c ->
                        Text(
                            stringResource(R.string.anthro_last_measured, formatShortDate(c.lastDate, appLocale())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        )
                    }
                }
                if (wideCard) {
                    Spacer(Modifier.width(12.dp))
                    WeightCard(allEntries, window, newForecast.takeIf { state.weightForecastMethod == ForecastMethod.NEW && state.anthropometryShowForecast }, Modifier.weight(1f))
                }
            }
            if (state.newLook && !wideCard) {
                Spacer(Modifier.height(8.dp))
                WeightCard(allEntries, window, newForecast.takeIf { state.weightForecastMethod == ForecastMethod.NEW && state.anthropometryShowForecast }, Modifier.fillMaxWidth())
            }

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
                            // The new look shows the weight on its own card (29.10); not twice.
                            if (state.newLook && s.axis == AnthropometryAxis.KG) return@items

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
            carryLast = state.anthropometryCarryLast,
            onCarryLast = vm::setAnthropometryCarryLast,
            onDismiss = { showSettings.value = false },
            onSave = { enabledIds, forecast, entries ->
                vm.setAnthropometryEnabledFieldIds(enabledIds)
                vm.setAnthropometryDisplay(showForecast = forecast, showEntries = entries)
                showSettings.value = false
            }
        )
    }
}
