package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RunningWorkout
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.formatRunningPace
import com.alphaomegos.annasagenda.formatShortDate
import com.alphaomegos.annasagenda.runningWorkoutPace
import com.alphaomegos.annasagenda.runningWorkoutTotals
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.formatOneDecimal
import java.time.LocalDate

/**
 * Between races: nothing planned, runs written down after they happen.
 *
 * Newest first, which is the opposite of the plan above it and deliberate.
 * A plan is read forwards because it is about what is coming; a log is read
 * backwards because the thing you want is what you just did.
 */
@Composable
internal fun RunningWorkoutsContent(
    workouts: List<RunningWorkout>,
    today: LocalDate,
    onAdd: (LocalDate, String, String, String) -> Boolean,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = appLocale()

    var showAdd by rememberSaveable { mutableStateOf(false) }

    val newestFirst = remember(workouts) { workouts.sortedByDescending { it.date } }

    val totals = remember(workouts) {
        runningWorkoutTotals(
            workouts = workouts,
            from = workouts.minOfOrNull { it.date } ?: today,
            to = workouts.maxOfOrNull { it.date } ?: today,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (workouts.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.running_workout_totals,
                    context.resources.getQuantityString(
                        R.plurals.running_workout_count,
                        totals.runs,
                        totals.runs,
                    ),
                    formatOneDecimal(totals.distanceKm),
                    formatRunningMinutes(totals.minutes),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }

        Box(modifier = Modifier.weight(1f)) {
            if (newestFirst.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.running_workouts_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = newestFirst, key = { it.id }) { workout ->
                        RunningWorkoutRow(
                            workout = workout,
                            dateLabel = formatShortDate(workout.date, locale),
                            onDelete = { onDelete(workout.id) },
                        )
                    }
                }
            }
        }

        Button(
            onClick = { showAdd = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.running_workout_add))
        }
    }

    if (showAdd) {
        AddRunningWorkoutDialog(
            today = today,
            onDismiss = { showAdd = false },
            onAdd = { date, distance, duration, note ->
                val added = onAdd(date, distance, duration, note)
                if (added) showAdd = false
                added
            },
        )
    }
}

@Composable
private fun RunningWorkoutRow(
    workout: RunningWorkout,
    dateLabel: String,
    onDelete: () -> Unit,
) {
    val pace = remember(workout) { runningWorkoutPace(workout) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dateLabel, style = MaterialTheme.typography.labelMedium)

                // Only the numbers that exist. A run logged without a
                // distance says so by not mentioning one, rather than by
                // claiming nought kilometres.
                val distanceLabel =
                    if (workout.distanceKm > 0.0) {
                        formatOneDecimal(workout.distanceKm) + " " +
                            stringResource(R.string.running_hint_distance)
                    } else {
                        null
                    }

                val timeLabel =
                    if (workout.durationMinutes > 0) {
                        formatRunningMinutes(workout.durationMinutes)
                    } else {
                        null
                    }

                val parts = listOfNotNull(distanceLabel, timeLabel, pace?.let { formatRunningPace(it) })

                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (workout.note.isNotBlank()) {
                    Text(
                        text = workout.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.running_workout_delete),
                )
            }
        }
    }
}

@Composable
private fun AddRunningWorkoutDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (LocalDate, String, String, String) -> Boolean,
) {
    val locale = appLocale()

    var date by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var distance by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.running_workout_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.running_workout_date) + ": " +
                            formatShortDate(LocalDate.ofEpochDay(date), locale)
                    )
                }

                OutlinedTextField(
                    value = distance,
                    onValueChange = {
                        distance = it
                        refused = false
                    },
                    label = { Text(stringResource(R.string.running_workout_distance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = duration,
                    onValueChange = {
                        duration = it
                        refused = false
                    },
                    label = { Text(stringResource(R.string.running_workout_duration)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.running_workout_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Said only after a refusal, not as a warning beforehand: the
                // rule is "one number or the other", and a dialog that
                // complains before anything has been typed is complaining
                // about being opened.
                if (refused) {
                    Text(
                        text = stringResource(R.string.running_workout_needs_a_number),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val added = onAdd(LocalDate.ofEpochDay(date), distance, duration, note)
                    refused = !added
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showPicker) {
        PickDateDialog(
            initialDate = LocalDate.ofEpochDay(date),
            onDismiss = { showPicker = false },
            onPicked = { date = it.toEpochDay() },
        )
    }
}

/** Hours and minutes once there is an hour to show, minutes before that. */
@Composable
private fun formatRunningMinutes(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(R.string.running_workout_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.running_workout_minutes_only, minutes)
    }
