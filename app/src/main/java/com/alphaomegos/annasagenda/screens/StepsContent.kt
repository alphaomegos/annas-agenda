package com.alphaomegos.annasagenda.screens

import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import com.alphaomegos.annasagenda.app.StepsTogether
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberUpdatedState
import com.alphaomegos.annasagenda.support.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.alphaomegos.annasagenda.model.StepsChartStyle
import com.alphaomegos.annasagenda.app.AppIcons
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.IconToggleButton
import com.alphaomegos.annasagenda.support.StepsImportResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.app.HealthConnectSteps
import com.alphaomegos.annasagenda.app.StepsAvailability
import com.alphaomegos.annasagenda.model.AppState
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepSource
import com.alphaomegos.annasagenda.support.AnthropometryRange
import com.alphaomegos.annasagenda.support.STEPS_HEIGHT_RANGE_CM
import com.alphaomegos.annasagenda.support.StepsReadReport
import com.alphaomegos.annasagenda.support.StepsSourceKind
import com.alphaomegos.annasagenda.support.stepsSourceKind
import com.alphaomegos.annasagenda.support.stepMedal
import com.alphaomegos.annasagenda.support.stepRecords
import com.alphaomegos.annasagenda.support.stepsDistanceKm
import com.alphaomegos.annasagenda.support.stepsKcal
import com.alphaomegos.annasagenda.support.stepsListDays
import com.alphaomegos.annasagenda.support.stepsWindowFor
import com.alphaomegos.annasagenda.support.weightOn
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * The Steps tab of «On the run» (19.10), laid out like anthropometry: the
 * range, the chart, and every day of the range under it, newest first.
 *
 * A day can be written over by hand (it is marked so, and the mark taken
 * back returns Health Connect's count); the three best days of all wear gold,
 * silver and bronze. With a height, each day also shows its distance; with a
 * height and a weight from anthropometry, the calories walking burned.
 */
@Composable
internal fun StepsContent(
    vm: AppViewModel,
    state: AppState,
    today: LocalDate,
    importAsked: Boolean = false,
    onImportAskDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val availability by vm.stepsAvailability.collectAsState()
    val context = LocalContext.current
    val together by StepsTogether.of(context).collectAsState()
    val allowed by vm.stepsAllowed.collectAsState()
    val report by vm.stepsReport.collectAsState()
    val imported by vm.stepsImport.collectAsState()
    // Samsung Health's export (19.10): the file is picked, the view model reads it.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSamsungSteps(uri)
    }

    // Opening the tab reads again: whether Health Connect is there, whether
    // the app may read, and the latest days.
    LaunchedEffect(Unit) { vm.refreshSteps() }

    // The page of the range shown (19.10): 0 holds today, −1 the period before.
    var page by rememberSaveable { mutableIntStateOf(0) }
    val window = stepsPage(state.stepsRange, today, state.stepsCustomRange, state.stepDays, page)
    val listDays = stepsListDays(window, today)
    val canBack = canPageStepsBack(state.stepsRange, window, state.stepDays)
    val canForward = canPageStepsForward(state.stepsRange, page)
    val byDate = remember(state.stepDays) { state.stepDays.associateBy { it.date } }
    val records = remember(state.stepDays) { stepRecords(state.stepDays) }

    val locale = LocalConfiguration.current.locales[0]
    val count = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    val kmFmt = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }

    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var heightOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!allowed) {
            item { StepsSourceCard(availability = availability, onAllowed = vm::refreshSteps) }
        }
        item {
            AnthropometryRangeBar(
                selected = state.stepsRange,
                custom = state.stepsCustomRange,
                onSelect = {
                    page = 0
                    vm.setStepsRange(it, null)
                },
                onCustomPicked = {
                    page = 0
                    vm.setStepsRange(AnthropometryRange.CUSTOM, it)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            // The whole period on the chart, days still to come empty; the
            // total and the average over the days that have been.
            val chartDays = stepsChartDays(if (state.stepsRange == AnthropometryRange.ALL) DateWindow(window.from, today) else window)
            // The user and each friend shown (25.10), each in their colour;
            // a friend's days reach back thirty days, older pages are the user's alone.
            val own = StepsSeries(chartDays.map { byDate[it]?.steps ?: 0 }, MaterialTheme.colorScheme.primary, stringResource(R.string.steps_legend_me))
            val friendsShown = together.friends.mapNotNull { f ->
                val pref = together.prefs[f.id] ?: StepFriendPref(0)
                if (!pref.shown) null else StepsSeries(chartDays.map { f.days[it] ?: 0 }, STEP_FRIEND_COLORS[pref.color.coerceIn(0, 3)], f.name)
            }
            val series = (if (together.selfShown || together.friends.isEmpty()) listOf(own) else emptyList()) + friendsShown
            val ceiling = niceStepsCeiling(series.flatMap { it.values }.maxOrNull() ?: 0)
            // The day picked on the chart (25.10): a tap, or a long press then a slide.
            var picked by rememberSaveable(page, state.stepsRange) { mutableStateOf<Int?>(null) }
            StepsChart(
                series = series,
                axis = stepsAxisLabels(state.stepsRange, chartDays, locale),
                // The period's best day of the user's own, marked across (not for «all»).
                best = if (state.stepsRange == AnthropometryRange.ALL) null else own.values.maxOrNull()?.takeIf { it > 0 },
                bestLabel = { count.format(it) },
                picked = picked,
                onPick = { picked = if (picked == it) null else it },
                style = state.stepsChartStyle,
                ceiling = ceiling,
                labels = listOf(0, ceiling / 2, ceiling).map { count.format(it) },
                onSwipeBack = { if (canBack) page -= 1 },
                onSwipeForward = { if (canForward) page += 1 },
            )
            picked?.let { i -> chartDays.getOrNull(i) }?.let { d ->
                StepsLoupe(dateFmt.format(d), series.map { Triple(it.name, it.color, count.format(it.values.getOrElse(picked!!) { 0 })) })
            }
            if (together.friends.isNotEmpty()) {
                StepsLegend(
                    me = stringResource(R.string.steps_legend_me),
                    meShown = together.selfShown,
                    meColor = MaterialTheme.colorScheme.primary,
                    friends = together.friends.map { f ->
                        val pref = together.prefs[f.id] ?: StepFriendPref(0)
                        Triple(f, pref.shown, STEP_FRIEND_COLORS[pref.color.coerceIn(0, 3)])
                    },
                    onToggle = { id, shown -> StepsTogether.setShown(context, id, shown) },
                )
            }
            if (state.stepsRange != AnthropometryRange.ALL) {
                StepsPager(
                    title = stepsPageTitle(state.stepsRange, window, locale),
                    canBack = canBack,
                    canForward = canForward,
                    onBack = { page -= 1 },
                    onForward = { page += 1 },
                )
            }
            val total = listDays.sumOf { (byDate[it]?.steps ?: 0).toLong() }
            val average = if (listDays.isEmpty()) 0L else total / listDays.size
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.steps_total_average, count.format(total), count.format(average)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Bars or a line (19.10), as anthropometry offers: the choice is kept.
                StepsChartStyle.entries.forEach { style ->
                    IconToggleButton(checked = state.stepsChartStyle == style, onCheckedChange = { vm.setStepsChartStyle(style) }) {
                        Icon(
                            imageVector = if (style == StepsChartStyle.BARS) AppIcons.stepsChartBars else AppIcons.stepsChartLine,
                            contentDescription = stringResource(
                                if (style == StepsChartStyle.BARS) R.string.steps_chart_bars else R.string.steps_chart_line
                            ),
                            tint = if (state.stepsChartStyle == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            val height = state.stepsHeightCm
            TextButton(onClick = { heightOpen = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    if (height != null) stringResource(R.string.steps_height_value, height)
                    else stringResource(R.string.steps_height_unset)
                )
            }
            // The best day of all (19.10), up here beside the height: the gold
            // medal's day, whatever range is shown.
            records.firstOrNull()?.let { best ->
                val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
                Text(
                    text = "🥇 " + stringResource(R.string.steps_record, count.format(best.steps), dates.format(best.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(listDays, key = { it.toEpochDay() }) { date ->
            val day = byDate[date]
            StepsDayRow(
                date = date,
                day = day,
                medal = day?.let { stepMedal(it, records) },
                dateText = dateFmt.format(date),
                countText = day?.let { count.format(it.steps) },
                distanceText = day?.takeIf { it.steps > 0 }?.let { d ->
                    val height = state.stepsHeightCm ?: return@let null
                    val km = kmFmt.format(stepsDistanceKm(d.steps, height))
                    val kcal = weightOn(date, state.anthropometry)?.let { stepsKcal(d.steps, height, it).roundToInt() }
                    if (kcal != null) stringResource(R.string.steps_km_kcal, km, kcal) else stringResource(R.string.steps_km, km)
                },
                onClick = { editing = date.toEpochDay() },
            )
        }
    }

    editing?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        val day = byDate[date]
        StepsEditDialog(
            title = stringResource(R.string.steps_edit_title, dateFmt.format(date)),
            initial = day?.steps,
            typed = day?.source == StepSource.MANUAL,
            onSave = { steps ->
                vm.typeSteps(date, steps)
                editing = null
            },
            onTakeBack = {
                vm.untypeSteps(date)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (importAsked) {
        AlertDialog(
            onDismissRequest = onImportAskDone,
            title = { Text(stringResource(R.string.steps_import_button)) },
            text = { Text(stringResource(R.string.steps_import_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    onImportAskDone()
                    // Any file: a phone's file manager names a CSV's type in more ways than one.
                    importer.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.steps_import_pick)) }
            },
            dismissButton = { TextButton(onClick = onImportAskDone) { Text(stringResource(R.string.cancel)) } },
        )
    }

    imported?.let { result ->
        val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
        AlertDialog(
            onDismissRequest = vm::dismissStepsImport,
            title = { Text(stringResource(R.string.steps_import_title)) },
            text = {
                Text(
                    when (result) {
                        is StepsImportResult.Done -> with(result.outcome) {
                            stringResource(
                                R.string.steps_import_done,
                                count.format(inFile),
                                first?.let(dates::format).orEmpty(),
                                last?.let(dates::format).orEmpty(),
                                count.format(added),
                                count.format(updated),
                                count.format(leftAlone),
                            )
                        }
                        StepsImportResult.NotAnExport -> stringResource(R.string.steps_import_not_export)
                        is StepsImportResult.Failed -> stringResource(R.string.steps_import_failed, result.reason)
                    }
                )
            },
            confirmButton = { TextButton(onClick = vm::dismissStepsImport) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (heightOpen) {
        StepsHeightDialog(
            initial = state.stepsHeightCm,
            onSave = {
                vm.setStepsHeight(it)
                heightOpen = false
            },
            onDismiss = { heightOpen = false },
        )
    }
}

/**
 * Where the steps would come from, while the app may not read them yet:
 * Health Connect's own permission screen, the store if it is missing, or —
 * on a phone without it — the plain fact that days can still be typed in.
 */
@Composable
private fun StepsSourceCard(availability: StepsAvailability, onAllowed: () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (availability) {
                StepsAvailability.READY -> {
                    Text(stringResource(R.string.steps_connect_body), style = MaterialTheme.typography.bodyMedium)
                    // Composed only where Health Connect is there, so the
                    // library is never reached below Android 9.
                    val ask = rememberLauncherForActivityResult(HealthConnectSteps.permissionContract()) { onAllowed() }
                    Button(onClick = { ask.launch(setOf(HealthConnectSteps.readPermission)) }) {
                        Text(stringResource(R.string.steps_connect_button))
                    }
                }
                StepsAvailability.NEEDS_INSTALL -> {
                    val context = LocalContext.current
                    Text(stringResource(R.string.steps_install_body), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HealthConnectSteps.STORE_LINK)))
                        } catch (_: ActivityNotFoundException) {
                            // No store on this phone: nothing to open, the text says what is missing.
                        }
                    }) { Text(stringResource(R.string.steps_install_button)) }
                }
                StepsAvailability.NONE ->
                    Text(stringResource(R.string.steps_none_here), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * What the last read from Health Connect found — when, on how many days, from
 * which apps — or what stopped it (19.10: a read that found nothing looked
 * exactly like one that failed). Found nothing, it says where steps get into
 * Health Connect in the first place.
 */
@Composable
internal fun StepsReportLine(report: StepsReadReport) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }.format(report.at)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (report.error != null) {
            Text(
                stringResource(R.string.steps_read_failed, time, report.error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        val names = report.sources.map { pkg ->
            when (stepsSourceKind(pkg)) {
                StepsSourceKind.SAMSUNG_HEALTH -> stringResource(R.string.steps_source_samsung)
                StepsSourceKind.THIS_PHONE -> stringResource(R.string.steps_source_phone)
                StepsSourceKind.FITBIT -> "Fitbit"
                StepsSourceKind.GOOGLE_FIT -> "Google Fit"
                StepsSourceKind.OTHER -> pkg
            }
        }
        Text(
            stringResource(
                R.string.steps_read_found,
                time,
                report.daysWithSteps,
                report.daysRead,
                if (names.isEmpty()) stringResource(R.string.steps_read_no_sources) else names.joinToString(", "),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report.daysWithSteps == 0) {
            Text(
                stringResource(R.string.steps_read_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How far a finger travels sideways on the chart to turn a page, as on anthropometry's. */
private const val STEPS_SWIPE_DP = 48

/**
 * A bar a day, or a line through them, oldest on the left, over a scale of
 * three round values (19.10): 0, half the top, the top. A swipe to the right
 * goes back a page, the way a calendar pages — what was before is to the left.
 */
@Composable
private fun StepsChart(
    series: List<StepsSeries>,
    axis: List<Pair<Int, String>>,
    best: Int?,
    bestLabel: (Int) -> String,
    picked: Int?,
    onPick: (Int) -> Unit,
    style: StepsChartStyle,
    ceiling: Int,
    labels: List<String>,
    onSwipeBack: () -> Unit,
    onSwipeForward: () -> Unit,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bestColor = MaterialTheme.colorScheme.tertiary
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val description = stringResource(R.string.steps_chart_description)
    var travelled by remember { mutableStateOf(0f) }
    // The gesture is set up once; what a swipe does is read when it ends.
    val back by rememberUpdatedState(onSwipeBack)
    val forward by rememberUpdatedState(onSwipeForward)
    val pickNow by rememberUpdatedState(onPick)
    // A slide after a long press shows each day it passes, not toggles them.
    var scrubbed by remember { mutableStateOf<Int?>(null) }
    val scrubTo: androidx.compose.ui.input.pointer.PointerInputScope.(Float, Int) -> Unit = { x, days ->
        val paint = Paint().apply { textSize = 10.dp.toPx() }
        // The same edge the drawing keeps: the gestures sit inside the chart's padding too.
        val left = (labels.maxOfOrNull { paint.measureText(it) } ?: 0f) + 6.dp.toPx()
        val width = size.width - left
        if (width > 0f) {
            val i = ((x - left) / width * days).toInt().coerceIn(0, days - 1)
            if (i != scrubbed && i != picked) pickNow(i)
            scrubbed = i
        }
    }
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(12.dp)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onDragEnd = {
                            val enough = STEPS_SWIPE_DP.dp.toPx()
                            when {
                                travelled > enough -> back()
                                travelled < -enough -> forward()
                            }
                        },
                    ) { _, delta -> travelled += delta }
                }
                .pointerInput(labels, series.firstOrNull()?.values?.size) {
                    val days = series.firstOrNull()?.values?.size ?: 0
                    fun dayAt(x: Float): Int? {
                        if (days == 0) return null
                        val paint = Paint().apply { textSize = 10.dp.toPx() }
                        val left = (labels.maxOfOrNull { paint.measureText(it) } ?: 0f) + 6.dp.toPx()
                        val width = size.width - left
                        if (width <= 0f) return null
                        return ((x - left) / width * days).toInt().coerceIn(0, days - 1)
                    }
                    detectTapGestures(onTap = { o -> dayAt(o.x)?.let(pickNow) })
                }
                .pointerInput(labels, series.firstOrNull()?.values?.size) {
                    val days = series.firstOrNull()?.values?.size ?: 0
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o -> if (days > 0) scrubTo(o.x, days) },
                        onDrag = { change, _ -> if (days > 0) scrubTo(change.position.x, days) },
                    )
                },
        ) {
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 10.dp.toPx()
                color = labelArgb
            }
            // The scale's labels on the left, as wide as the widest of them.
            val left = (labels.maxOfOrNull { paint.measureText(it) } ?: 0f) + 6.dp.toPx()
            val top = 6.dp.toPx()
            // A strip at the bottom for the days' labels.
            val strip = if (axis.isEmpty()) 0f else 14.dp.toPx()
            val floor = size.height - strip
            val plotHeight = floor - top
            val plotWidth = size.width - left
            listOf(0f, 0.5f, 1f).forEachIndexed { i, t ->
                val y = floor - t * plotHeight
                drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
                labels.getOrNull(i)?.let { drawContext.canvas.nativeCanvas.drawText(it, 0f, y + 3.dp.toPx(), paint) }
            }
            val days = series.firstOrNull()?.values?.size ?: 0
            if (days == 0 || ceiling <= 0) return@Canvas
            val slot = plotWidth / days
            axis.forEach { (i, text) ->
                val w = paint.measureText(text)
                val x = (left + i * slot + slot / 2 - w / 2).coerceIn(left, size.width - w)
                drawContext.canvas.nativeCanvas.drawText(text, x, size.height, paint)
            }
            picked?.takeIf { it in 0 until days }?.let { i ->
                val x = left + i * slot + slot / 2
                drawLine(grid, Offset(x, top), Offset(x, floor), strokeWidth = 2.dp.toPx())
            }
            best?.takeIf { it <= ceiling }?.let { b ->
                val y = floor - plotHeight * b / ceiling
                drawLine(
                    bestColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val text = "🥇 " + bestLabel(b)
                drawContext.canvas.nativeCanvas.drawText(text, size.width - paint.measureText(text), y - 3.dp.toPx(), paint)
            }
            if (style == StepsChartStyle.LINE) {
                // A line through each day's middle for each person; a day of nothing is on the floor.
                series.forEach { s ->
                    val path = Path()
                    s.values.forEachIndexed { i, n ->
                        val x = left + i * slot + slot / 2
                        val y = floor - plotHeight * n / ceiling
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = s.color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
                }
                return@Canvas
            }
            // Bars: the people side by side within each day.
            series.forEachIndexed { k, s ->
                s.values.forEachIndexed { i, n ->
                    if (n <= 0) return@forEachIndexed
                    val (x, w) = stepsSubBar(left + i * slot, slot, k, series.size)
                    val h = plotHeight * n / ceiling
                    drawRect(color = s.color, topLeft = Offset(x, floor - h), size = Size(w, h))
                }
            }
        }
    }
}

/** The day picked on the chart (25.10): its date and each person's count, in their colour. */
@Composable
private fun StepsLoupe(date: String, values: List<Triple<String, androidx.compose.ui.graphics.Color, String>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = MaterialTheme.typography.labelLarge)
        values.forEach { (name, color, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                Text(if (values.size > 1) "$name $value" else value, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One person's days on the chart, in their colour. */
internal data class StepsSeries(val values: List<Int>, val color: androidx.compose.ui.graphics.Color, val name: String = "")

/**
 * Who is on the chart (25.10): the user and each friend, a chip each with
 * their colour; a tap shows or hides them, and the choice is kept.
 */
@Composable
private fun StepsLegend(
    me: String,
    meShown: Boolean,
    meColor: androidx.compose.ui.graphics.Color,
    friends: List<Triple<com.alphaomegos.annasagenda.model.StepFriend, Boolean, androidx.compose.ui.graphics.Color>>,
    onToggle: (String?, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val people = listOf(Triple<String?, String, Pair<Boolean, androidx.compose.ui.graphics.Color>>(null, me, meShown to meColor)) +
            friends.map { (f, shown, color) -> Triple(f.id, f.name, shown to color) }
        people.forEach { (id, name, look) ->
            FilterChip(
                selected = look.first,
                onClick = { onToggle(id, !look.first) },
                label = { Text(name) },
                leadingIcon = { Box(modifier = Modifier.size(10.dp).background(look.second, CircleShape)) },
            )
        }
    }
}

/** The page's name under the chart, with the arrows to the one before and after. */
@Composable
private fun StepsPager(title: String, canBack: Boolean, canForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.anthro_page_back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onForward, enabled = canForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.anthro_page_forward))
        }
    }
}

/** «октябрь 2026», «2026», «4-й квартал 2026»; a week or one's own range by its first and last day. */
@Composable
private fun stepsPageTitle(range: AnthropometryRange, window: DateWindow, locale: java.util.Locale): String = when (range) {
    AnthropometryRange.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(window.from)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    AnthropometryRange.YEAR -> window.from.year.toString()
    AnthropometryRange.QUARTER -> stringResource(R.string.steps_quarter, stepsQuarterOf(window), window.from.year)
    else -> stringResource(R.string.anthro_page_window, formatShortDate(window.from, locale), formatShortDate(window.to, locale))
}

@Composable
private fun StepsDayRow(
    date: LocalDate,
    day: StepDay?,
    medal: Int?,
    dateText: String,
    countText: String?,
    distanceText: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dateText, style = MaterialTheme.typography.bodyLarge)
            if (distanceText != null) {
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (medal != null) {
            val medalName = stringResource(
                when (medal) {
                    1 -> R.string.steps_medal_gold
                    2 -> R.string.steps_medal_silver
                    else -> R.string.steps_medal_bronze
                }
            )
            Text(
                text = when (medal) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" },
                modifier = Modifier.semantics { contentDescription = medalName },
            )
        }
        if (day?.source == StepSource.MANUAL) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.steps_typed_mark),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = countText ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (countText == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A day's steps by hand; for a day already typed in, also the way back to Health Connect's count. */
@Composable
private fun StepsEditDialog(
    title: String,
    initial: Int?,
    typed: Boolean,
    onSave: (Int) -> Unit,
    onTakeBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    val value = text.filter { it.isDigit() }.take(7).toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(7) },
                    label = { Text(stringResource(R.string.steps_edit_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (typed) {
                    TextButton(onClick = onTakeBack) { Text(stringResource(R.string.steps_take_back)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The walker's height, for the distance and the calories; empty takes it away. */
@Composable
private fun StepsHeightDialog(initial: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = text.isEmpty() || (value != null && value in STEPS_HEIGHT_RANGE_CM)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.steps_height_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.steps_height_label)) },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
