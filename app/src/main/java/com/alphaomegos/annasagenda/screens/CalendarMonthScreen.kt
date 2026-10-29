package com.alphaomegos.annasagenda.screens

import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import com.alphaomegos.annasagenda.components.TopBarBack
import com.alphaomegos.annasagenda.model.DayScreenStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Check
import com.alphaomegos.annasagenda.support.stepsThousands
import com.alphaomegos.annasagenda.support.monthCellHeightPx
import com.alphaomegos.annasagenda.support.stepsCellChoice
import java.text.NumberFormat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.alphaomegos.annasagenda.app.AppIcons
import com.alphaomegos.annasagenda.model.AppState
import com.alphaomegos.annasagenda.model.CalendarBadge
import com.alphaomegos.annasagenda.support.calendarBadgesByDate
import com.alphaomegos.annasagenda.support.calendarItemCountsByDate
import com.alphaomegos.annasagenda.support.somedayTaskCount
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.appExtraColors
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.orderedWeekDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

@Composable
fun CalendarMonthRoute(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSomeday: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = appLocale()

    CalendarMonthContent(
        state = state,
        locale = locale,
        ensureGeneratedInRange = vm::ensureGeneratedInRange,
        onSetBadges = vm::setCalendarBadges,
        onSetShowSteps = vm::setCalendarShowSteps,
        onBack = onBack,
        onOpenDay = onOpenDay,
        onOpenSomeday = onOpenSomeday,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarMonthContent(
    state: AppState,
    locale: Locale,
    ensureGeneratedInRange: (LocalDate, LocalDate) -> Unit,
    onSetBadges: (Set<CalendarBadge>) -> Unit,
    onSetShowSteps: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSomeday: () -> Unit,
) {
    val today = LocalDate.now()

    val baseIndex = today.year * 12 + (today.monthValue - 1)
    var monthIndex by rememberSaveable { mutableIntStateOf(baseIndex) }

    val yearMonth = remember(monthIndex) {
        val y = Math.floorDiv(monthIndex, 12)
        val m = monthIndex - y * 12 + 1
        YearMonth.of(y, m)
    }

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
        ensureGeneratedInRange(firstDay, yearMonth.atEndOfMonth())
    }

    val firstDow = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val headerDays = remember(firstDow) { orderedWeekDays(firstDow) }

    val offset = remember(firstDay, firstDow) {
        val a = firstDay.dayOfWeek.value
        val b = firstDow.value
        ((a - b) + 7) % 7
    }

    // 19.10: each day's steps, for the line under its number.
    val stepsByDate = remember(state.stepDays) { state.stepDays.associate { it.date to it.steps } }

    // 6.3: the marks switched on, day by day.
    val badgesByDate = remember(
        state.calendarBadges, state.anthropometry, state.foodLog, state.tasks, state.suppressedRecurrences, today,
    ) {
        calendarBadgesByDate(
            enabled = state.calendarBadges,
            anthropometry = state.anthropometry,
            foodLog = state.foodLog,
            tasks = state.tasks,
            suppressedRecurrences = state.suppressedRecurrences,
            today = today,
        )
    }
    var showBadgeSettings by rememberSaveable { mutableStateOf(false) }

    val itemCountByDate = remember(state.tasks, state.subtasks, state.suppressedRecurrences) {
        calendarItemCountsByDate(state.tasks, state.subtasks, state.suppressedRecurrences)
    }

    val cells = remember(yearMonth, offset, daysInMonth) {
        val out = ArrayList<LocalDate?>(42)
        repeat(offset) { out.add(null) }
        for (d in 1..daysInMonth) out.add(yearMonth.atDay(d))
        while (out.size % 7 != 0) out.add(null)
        out
    }

    val somedayCount = remember(state.tasks, state.suppressedRecurrences) {
        somedayTaskCount(state.tasks, state.suppressedRecurrences)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val newLook = state.dayScreenStyle == DayScreenStyle.NEW
        val monthTitleText = remember(monthIndex, locale) {
            yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
        }
        if (newLook) {
            // The new look (29.10), as the day screen: Back at the top left, the
            // month as the title, ⚙ at the right; then one light row of ‹ Today ›.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TopBarBack(onBack)
                Text(
                    text = monthTitleText,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showBadgeSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.calendar_badges_open))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { monthIndex -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_month))
                }
                TextButton(onClick = { monthIndex = baseIndex }) { Text(stringResource(R.string.today)) }
                IconButton(onClick = { monthIndex += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                }
            }
        } else Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.calendar_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = { showBadgeSettings = true },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.calendar_badges_open))
            }
        }
        Spacer(modifier = Modifier.height(if (newLook) 4.dp else 12.dp))

        if (!newLook) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { monthIndex -= 1 }) { Text(stringResource(R.string.prev_month)) }

            val monthTitle = remember(monthIndex, locale) {
                val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
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

        // Grid now fills all remaining height, and each row gets equal height.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val spacing = 6.dp
            val rowCount = max(1, cells.size / 7)

            val availableHeight = this.maxHeight // make receiver usage explicit (kills the warning)
            // In whole pixels, rounded down (25.10): each cell and each gap is laid out
            // in whole pixels, so a height in fractions of them rounded up six times
            // over came to a few pixels more than the screen — the last week's lower
            // edge cut off on the folded Fold 3.
            val density = LocalDensity.current
            val cellHeightCandidate = with(density) {
                monthCellHeightPx(availableHeight.toPx().toInt(), spacing.roundToPx(), rowCount).toDp()
            }
            val cellHeight = if (cellHeightCandidate < 44.dp) 44.dp else cellHeightCandidate

            val cellShape = MaterialTheme.shapes.medium
            val cellBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                itemsIndexed(
                    items = cells,
                    key = { index, date ->
                        date?.toEpochDay() ?: (Long.MIN_VALUE + index.toLong())
                    }
                ) { _, date ->
                    if (date == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .then(if (newLook) Modifier else Modifier.border(1.dp, cellBorderColor, cellShape))
                        )
                    } else {
                        val count = itemCountByDate[date] ?: 0
                        val isToday = date == today
                        val marks = badgesByDate[date].orEmpty()
                        val steps = if (state.calendarShowSteps) stepsByDate[date] ?: 0 else 0

                        Surface(
                            tonalElevation = if (isToday) 4.dp else 0.dp,
                            // The new look (29.10): no frames; today in the accent's light tone.
                            color = if (newLook && isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = cellShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .then(if (newLook) Modifier else Modifier.border(1.dp, cellBorderColor, cellShape))
                                .clickable { onOpenDay(date.toEpochDay()) }
                        ) {
                            // The day's number at the top, its steps right under it — at
                            // the same height in every cell, whatever is below (25.10) —
                            // and the tasks and marks at the bottom.
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                if (steps > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    CalendarStepsLine(steps, locale)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (count > 0) {
                                        // A tick says what the number is (25.10): the day's tasks.
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    // Every mark a small picture; the measurement's
                                    // ruler keeps the green its circle had (03.10).
                                    val measured = appExtraColors.dayMarker
                                    CalendarBadge.entries
                                        .filter { it in marks }
                                        .forEach { badge ->
                                            Icon(
                                                imageVector = AppIcons.calendarBadge(badge),
                                                contentDescription = null,
                                                tint = when (badge) {
                                                    CalendarBadge.DEBTS -> MaterialTheme.colorScheme.error
                                                    CalendarBadge.ANTHROPOMETRY -> measured
                                                    CalendarBadge.FOOD -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                }
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

        if (!newLook) Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.back)) }
    }

    if (showBadgeSettings) {
        CalendarBadgesDialog(
            initial = state.calendarBadges,
            initialSteps = state.calendarShowSteps,
            onDismiss = { showBadgeSettings = false },
            onSave = { badges, steps ->
                onSetBadges(badges)
                onSetShowSteps(steps)
                showBadgeSettings = false
            },
        )
    }
}

/** 6.3: which marks the calendar shows. Written on OK; survives turning the phone. */
@Composable
internal fun CalendarBadgesDialog(
    initial: Set<CalendarBadge>,
    initialSteps: Boolean,
    onDismiss: () -> Unit,
    onSave: (Set<CalendarBadge>, Boolean) -> Unit,
) {
    var steps by rememberSaveable { mutableStateOf(initialSteps) }
    // Names joined into one string: a Bundle takes it as is.
    var pickedText by rememberSaveable { mutableStateOf(initial.joinToString(",") { it.name }) }
    val picked = pickedText.split(',').mapNotNull { n -> CalendarBadge.entries.firstOrNull { it.name == n } }.toSet()
    fun toggle(b: CalendarBadge) {
        val next = if (b in picked) picked - b else picked + b
        pickedText = CalendarBadge.entries.filter { it in next }.joinToString(",") { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_badges_title)) },
        text = {
            Column {
                CalendarBadge.entries.forEach { badge ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(badge) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = badge in picked, onCheckedChange = { toggle(badge) })
                        Icon(
                            imageVector = AppIcons.calendarBadge(badge),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                when (badge) {
                                    CalendarBadge.ANTHROPOMETRY -> R.string.calendar_badge_anthropometry
                                    CalendarBadge.FOOD -> R.string.calendar_badge_food
                                    CalendarBadge.DEBTS -> R.string.calendar_badge_debts
                                }
                            )
                        )
                    }
                }
                // Steps (19.10): a number rather than a mark, so a switch of its own.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { steps = !steps },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = steps, onCheckedChange = { steps = it })
                    Icon(imageVector = AppIcons.steps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.calendar_steps_option))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked, steps) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
/**
 * A day's steps under its number (19.10). Usually five digits, in a cell a
 * seventh of the screen wide: the whole number where it fits, as the phone's
 * language writes it (12 345, 12,345), and in thousands where it does not
 * (12,3k) — measured, not guessed, so a wide Fold gets the whole number and
 * its cover screen the short one.
 */
@Composable
private fun CalendarStepsLine(steps: Int, locale: Locale) {
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val full = remember(steps, locale) { NumberFormat.getIntegerInstance(locale).format(steps) }
    val thousands = remember(steps, locale) {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(stepsThousands(steps))
    }
    val short = stringResource(R.string.steps_thousands, thousands)
    val spoken = stringResource(R.string.steps_calendar_description, full)
    // What fits, best first (25.10: on the folded Fold 3 not even «12,3k» did
    // beside the figure): the whole number, thousands, whole thousands.
    val whole = stringResource(R.string.steps_thousands, Math.round(steps / 1000.0).toString())
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        val density = LocalDensity.current
        val room = with(density) { maxWidth.toPx() }
        val figure = with(density) { 12.dp.toPx() }
        // The figure always (25.10: without it nobody could tell what the number
        // was): beside the number where both fit, above it where they do not.
        val (beside, text) = stepsCellChoice(
            listOf(full, short, whole),
            fitsBeside = { measurer.measure(it, style).size.width + figure <= room },
            fitsAlone = { measurer.measure(it, style).size.width <= room },
        )
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = AppIcons.steps, contentDescription = null, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = text, style = style, maxLines = 1, softWrap = false)
            }
        } else {
            Column {
                Icon(imageVector = AppIcons.steps, contentDescription = null, modifier = Modifier.size(10.dp))
                Text(text = text, style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}
