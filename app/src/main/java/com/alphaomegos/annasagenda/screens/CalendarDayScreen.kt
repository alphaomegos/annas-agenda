package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.support.monthAfterDay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import com.alphaomegos.annasagenda.components.WayHomeButton
import com.alphaomegos.annasagenda.components.TopBarBack
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.Icons
import com.alphaomegos.annasagenda.app.AppIcons
import com.alphaomegos.annasagenda.model.CalendarBadge
import com.alphaomegos.annasagenda.model.DayScreenStyle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.dialogs.AnthropometryDayInputDialog
import com.alphaomegos.annasagenda.support.isSuppressedTemplateTaskOnItsDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.model.AppState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.components.DateTasksBlock
import com.alphaomegos.annasagenda.components.WayHomeFootButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDayRoute(
    vm: AppViewModel,
    onBack: () -> Unit,
    initialEpochDay: Long,
    onAddTask: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locale = appLocale()

    CalendarDayContent(
        state = state,
        locale = locale,
        initialEpochDay = initialEpochDay,
        onBack = onBack,
        onAddTask = onAddTask,
        ensureGeneratedInRange = vm::ensureGeneratedInRange,
        onSaveAnthro = { date, valuesByFieldId ->
            vm.saveAnthropometryForDate(
                date = date,
                valuesByFieldId = valuesByFieldId
            )
        },
        tasksContent = { date ->
            DateTasksBlock(vm = vm, state = state, date = date, newLook = state.dayScreenStyle == DayScreenStyle.NEW)        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDayContent(
    state: AppState,
    locale: Locale,
    initialEpochDay: Long,
    onBack: () -> Unit,
    onAddTask: (Long) -> Unit,
    ensureGeneratedInRange: (LocalDate, LocalDate) -> Unit,
    onSaveAnthro: (LocalDate, Map<String, Double?>) -> Unit,
    tasksContent: @Composable (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var selectedEpochDay by rememberSaveable(initialEpochDay) { mutableLongStateOf(initialEpochDay) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }

    LaunchedEffect(selectedEpochDay) {
        val d = LocalDate.ofEpochDay(selectedEpochDay)
        ensureGeneratedInRange(d, d)
    }

    val dateText = remember(selectedDate, locale) {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        selectedDate.format(fmt)
    }

    val dateTasksCount = remember(state.tasks, state.suppressedRecurrences, selectedDate) {
        state.tasks.count { t ->
            t.date == selectedDate && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
        }
    }

    val ctx = LocalContext.current
    val savedMsg = stringResource(R.string.anthropometry_saved)
    var showAnthroDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        if (state.dayScreenStyle == DayScreenStyle.NEW) {
            NewDayHeader(
                date = selectedDate,
                locale = locale,
                onBack = onBack,
                onPrev = { selectedEpochDay -= 1 },
                onToday = { selectedEpochDay = today.toEpochDay() },
                onNext = { selectedEpochDay += 1 },
                onAnthropometry = { showAnthroDialog = true },
                onAddTask = { onAddTask(selectedEpochDay) },
            )
        } else {
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
                OutlinedButton(onClick = { selectedEpochDay = today.toEpochDay() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.today))
                }
                OutlinedButton(onClick = { selectedEpochDay += 1 }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_day))
                }
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
                    // «Tasks» says nothing the list does not (27.10): only in «Summer 2025».
                    if (state.dayScreenStyle != DayScreenStyle.NEW) {
                        Text(
                            stringResource(R.string.tasks_header),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    tasksContent(selectedDate)
                }
            }
        }

        if (showAnthroDialog) {
            val existing = state.anthropometry.firstOrNull { it.date == selectedDate }

            AnthropometryDayInputDialog(
                date = selectedDate,
                initialEntry = existing,
                enabledFieldIds = state.anthropometryEnabledFieldIds,
                onDismiss = { showAnthroDialog = false },
                onSave = { valuesByFieldId ->
                    onSaveAnthro(selectedDate, valuesByFieldId)
                    Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show()
                    showAnthroDialog = false
                }
            )
        }

        // The new look has Back and the house at the top (27.10): the foot is one more row of tasks.
        if (state.dayScreenStyle != DayScreenStyle.NEW) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.back))
                }
                WayHomeFootButton()
            }
        }
    }
}
/**
 * The day screen's head in the new look (27.10, Eduard's brief, then his look
 * at it on the phone): Back at the top left, the house at the top right, the
 * date between them — «27 октября», the weekday and year small under it —
 * and one light row: the day before, today, the day after, then anthropometry
 * as a ruler and «+ Task». Nothing dark and heavy; no «Calendar», «Tasks» or
 * «Anthropometry» words.
 *
 * The month is spelled from the app's own list (months_after_day), not by the
 * platform: on the phones «d MMMM» gave «27 октябрь», the form for a month
 * alone, where after a number Russian has «октября».
 */
@Composable
private fun NewDayHeader(
    date: LocalDate,
    locale: Locale,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
    onAnthropometry: () -> Unit,
    onAddTask: () -> Unit,
) {
    val month = monthAfterDay(stringResource(R.string.months_after_day), date)
    val weekday = remember(date, locale) { date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TopBarBack(onBack)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.day_screen_title, date.dayOfMonth, month),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.day_screen_subtitle, weekday, date.dayOfMonth, month, date.year),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        WayHomeButton()
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_day))
        }
        TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(stringResource(R.string.today))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_day))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onAnthropometry) {
            Icon(AppIcons.calendarBadge(CalendarBadge.ANTHROPOMETRY), contentDescription = stringResource(R.string.anthropometry_title))
        }
        FilledTonalButton(
            onClick = onAddTask,
            contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.day_new_task))
        }
    }
}
