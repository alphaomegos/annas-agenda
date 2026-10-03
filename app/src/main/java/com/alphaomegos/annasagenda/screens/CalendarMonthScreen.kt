package com.alphaomegos.annasagenda.screens

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
import com.alphaomegos.annasagenda.AppIcons
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.CalendarBadge
import com.alphaomegos.annasagenda.calendarBadgesByDate
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.appExtraColors
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.isSuppressedTemplateTaskOnItsDate
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

    val visibleTasks = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.filterNot { isSuppressedTemplateTaskOnItsDate(it, state.suppressedRecurrences) }
    }

    val taskCountByDate = remember(visibleTasks) {
        visibleTasks.mapNotNull { it.date }.groupingBy { it }.eachCount()
    }

    val taskDateById = remember(visibleTasks) {
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
        Box(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(modifier = Modifier.height(12.dp))

        Row(
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
            val usable = (availableHeight - spacing * (rowCount - 1)).coerceAtLeast(0.dp)
            val cellHeightCandidate = usable / rowCount
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
                                .border(1.dp, cellBorderColor, cellShape)
                        )
                    } else {
                        val count = itemCountByDate[date] ?: 0
                        val isToday = date == today
                        val marks = badgesByDate[date].orEmpty()

                        Surface(
                            tonalElevation = if (isToday) 4.dp else 0.dp,
                            shape = cellShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .border(1.dp, cellBorderColor, cellShape)
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
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (count > 0) {
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

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.back)) }
    }

    if (showBadgeSettings) {
        CalendarBadgesDialog(
            initial = state.calendarBadges,
            onDismiss = { showBadgeSettings = false },
            onSave = {
                onSetBadges(it)
                showBadgeSettings = false
            },
        )
    }
}

/** 6.3: which marks the calendar shows. Written on OK; survives turning the phone. */
@Composable
private fun CalendarBadgesDialog(
    initial: Set<CalendarBadge>,
    onDismiss: () -> Unit,
    onSave: (Set<CalendarBadge>) -> Unit,
) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}