package com.alphaomegos.annasagenda.screens

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
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.dialogs.AnthropometryDayInputDialog
import com.alphaomegos.annasagenda.util.isSuppressedTemplateTaskOnItsDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.AppState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.components.DateTasksBlock

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
        onSaveAnthro = { date, values ->
            vm.saveAnthropometryForDate(
                date = date,
                armCm = values[0],
                chestCm = values[1],
                underChestCm = values[2],
                waistCm = values[3],
                bellyCm = values[4],
                hipsCm = values[5],
                thighCm = values[6],
                weightKg = values[7]
            )
        },
        tasksContent = { date ->
            DateTasksBlock(vm = vm, state = state, date = date)        }
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
    onSaveAnthro: (LocalDate, List<Double?>) -> Unit,
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
    var showAnthroDialog by remember { mutableStateOf(false) }

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
                    tasksContent(selectedDate)
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
                    onSaveAnthro(selectedDate, values)
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