package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.isSuppressedTemplateTaskOnItsDate
import com.alphaomegos.annasagenda.util.orderedWeekDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

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
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSomeday: () -> Unit,
) {
    val today = LocalDate.now()

    val baseIndex = today.year * 12 + (today.monthValue - 1)
    var monthIndex by rememberSaveable { mutableIntStateOf(baseIndex) }

    // safer than / and % for negative months
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

    val anthroDates = remember(state.anthropometry) {
        state.anthropometry.filter { it.hasAnyValue() }.map { it.date }.toSet()
    }

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

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = cells,
                key = { index, date ->
                    date?.toEpochDay() ?: (Long.MIN_VALUE + index.toLong())
                }
            ) { _, date ->
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
