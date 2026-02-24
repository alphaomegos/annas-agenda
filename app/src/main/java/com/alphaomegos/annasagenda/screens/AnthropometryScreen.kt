package com.alphaomegos.annasagenda.screens

import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryEntry
import com.alphaomegos.annasagenda.dialogs.AnthropometryInputDialog
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.formatOneDecimal
import com.alphaomegos.annasagenda.util.formatSignedOneDecimal
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.CalorieGoalChange
import com.alphaomegos.annasagenda.util.appLocale

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
    val today = remember { LocalDate.now() }

    val deficit30 = remember(state) {
        val start30 = today.minusDays(29)
        sumDeficitInRange(state, start30, today)
    }
    val potentialKg = deficit30 / 7800.0

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
                            val okGreen = Color(0xFF2E7D32)
                            val badRed = MaterialTheme.colorScheme.error
                            val color = if (potentialKg >= 0.0) okGreen else badRed

                            val kgText = String.format(appLocale(), "%.2f", potentialKg)
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
                        }

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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
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

private fun goalFor(date: LocalDate, changes: List<CalorieGoalChange>): Int {
    val last = changes
        .filter { !it.date.isAfter(date) }
        .maxByOrNull { it.date }
    return last?.kcal ?: 2000
}

private fun sumDeficitInRange(state: AppState, start: LocalDate, end: LocalDate): Int {
    var d = start
    var total = 0
    while (!d.isAfter(end)) {
        val g = goalFor(d, state.calorieGoalChanges)
        val eaten = state.foodLog.filter { it.date == d }.sumOf { it.kcal }
        total += (g - eaten)
        d = d.plusDays(1)
    }
    return total
}
