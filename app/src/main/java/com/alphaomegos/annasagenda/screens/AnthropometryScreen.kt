package com.alphaomegos.annasagenda.screens

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryEntry
import com.alphaomegos.annasagenda.AnthropometryRange
import com.alphaomegos.annasagenda.CurvePoint
import com.alphaomegos.annasagenda.DateWindow
import com.alphaomegos.annasagenda.anthropometryEntriesIn
import com.alphaomegos.annasagenda.anthropometryWindowFor
import com.alphaomegos.annasagenda.caloriesBurnedRunning
import com.alphaomegos.annasagenda.dialogs.AnthropometryInputDialog
import com.alphaomegos.annasagenda.AppViewModel
import androidx.compose.ui.graphics.toArgb
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.appExtraColors
import com.alphaomegos.annasagenda.formatShortDate
import com.alphaomegos.annasagenda.smoothCurveSegments
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.formatOneDecimal
import com.alphaomegos.annasagenda.util.formatTwoDecimals
import com.alphaomegos.annasagenda.util.formatSignedOneDecimal
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.KCAL_PER_KG_FAT
import com.alphaomegos.annasagenda.calorieDeficitInRange
import com.alphaomegos.annasagenda.AnthropometryFieldIds
import com.alphaomegos.annasagenda.defaultAnthropometryFieldIds

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

    val allEntries = remember(state.anthropometry) {
        state.anthropometry.sortedBy { it.date }
    }
    val entriesByDate = remember(allEntries) { allEntries.associateBy { it.date } }

    // The chosen range is kept as its name rather than as the enum: a String
    // needs no judgement about what a Bundle will accept, and getting that
    // judgement wrong shows up only as a crash on rotation.
    var rangeName by rememberSaveable { mutableStateOf(AnthropometryRange.MONTH.name) }
    var customFromDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var customToDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val range = remember(rangeName) { AnthropometryRange.valueOf(rangeName) }

    val custom = remember(customFromDay, customToDay) {
        val from = customFromDay
        val to = customToDay
        if (from == null || to == null) null
        else DateWindow(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to))
    }

    val dateWindow = remember(range, custom, allEntries, today) {
        anthropometryWindowFor(
            range = range,
            today = today,
            custom = custom,
            entries = allEntries,
        )
    }

    val window = remember(allEntries, dateWindow) {
        anthropometryEntriesIn(allEntries, dateWindow)
    }

    val showInput = rememberSaveable { mutableStateOf(false) }

    val showSettings = rememberSaveable { mutableStateOf(false) }

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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
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
                selected = range,
                custom = custom,
                onSelect = { rangeName = it.name },
                onCustomPicked = { picked ->
                    customFromDay = picked.from.toEpochDay()
                    customToDay = picked.to.toEpochDay()
                },
            )

            AnthropometryChart(
                entries = window,
                series = visibleFieldDefs,
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
                        item {
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
    if (showSettings.value) {
        AnthropometryFieldsDialog(
            fieldDefs = anthropometryFieldDefs,
            enabledFieldIds = state.anthropometryEnabledFieldIds,
            onDismiss = { showSettings.value = false },
            onSave = { enabledIds ->
                vm.setAnthropometryEnabledFieldIds(enabledIds)
                showSettings.value = false
            }
        )
    }
}

@Composable
private fun AnthropometryChart(
    entries: List<AnthropometryEntry>,
    series: List<AnthropometryFieldDef>,
) {
    val cmUnit = stringResource(R.string.cm_short)
    val kgUnit = stringResource(R.string.kg_short)

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
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

            // Date labels (start/end). Formatted before the draw scope, for
            // the same reason the colours are read before it: a DrawScope is
            // not a composition and cannot ask what language the app is in.
            drawLabel(firstDateLabel, padLeft, size.height - 6f)
            drawLabel(lastDateLabel, plotRight - 72f, size.height - 6f)

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
                drawPath(path, color = s.color, style = Stroke(width = 3f))
            }
        }
    }
}

@Composable
private fun AnthropometryFieldsDialog(
    fieldDefs: List<AnthropometryFieldDef>,
    enabledFieldIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val pendingIds = remember(enabledFieldIds) {
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
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pendingIds.value) }) {
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

