package com.alphaomegos.annasagenda.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RepeatFreq
import com.alphaomegos.annasagenda.RepeatRule
import com.alphaomegos.annasagenda.repeatFreqFromSavedName
import com.alphaomegos.annasagenda.weekDaysFromSavedNames
import com.alphaomegos.annasagenda.weekDaysToSavedNames
import com.alphaomegos.annasagenda.util.orderedWeekDays
import com.alphaomegos.annasagenda.util.appLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable


/**
 * What this dialog has to carry across a rotation.
 *
 * Turning the phone rebuilds the activity, so a plain `remember` is gone and
 * the dialog reopens showing the rule the task already had — which looks like
 * nothing happened, and is exactly what makes it hard to notice that the
 * choices just made were thrown away.
 *
 * Only Bundle-shaped values survive, so the frequency and the chosen days
 * travel as their own names. Giving nothing back for a name this build cannot
 * read is deliberate: see RepeatDraftSaving.
 */
private val repeatFreqSaver = Saver<RepeatFreq, String>(
    save = { it.name },
    restore = { repeatFreqFromSavedName(it) },
)

private val weekDaysSaver = listSaver<Set<DayOfWeek>, String>(
    save = { weekDaysToSavedNames(it) },
    restore = { weekDaysFromSavedNames(it) },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RepeatPickerDialog(
    initial: RepeatRule?,
    onDismiss: () -> Unit,
    onConfirm: (RepeatRule?) -> Unit
) {
    val locale = appLocale()

    val defaultDow = remember { LocalDate.now().dayOfWeek }
    val defaultDom = remember { LocalDate.now().dayOfMonth }

    var enabled by rememberSaveable { mutableStateOf(initial != null) }
    var freq by rememberSaveable(stateSaver = repeatFreqSaver) {
        mutableStateOf(initial?.freq ?: RepeatFreq.WEEKLY)
    }

    var interval by rememberSaveable {
        mutableIntStateOf((initial?.interval ?: 1).coerceAtLeast(1))
    }

    var weekDays by rememberSaveable(stateSaver = weekDaysSaver) {
        mutableStateOf(
            initial?.weekDays?.takeIf { it.isNotEmpty() } ?: setOf(defaultDow)
        )
    }

    var dayOfMonth by rememberSaveable {
        mutableIntStateOf((initial?.dayOfMonth ?: defaultDom).coerceIn(1, 31))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Enabled (Switch)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.repeat_enabled),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                // Frequency (segmented buttons) — fixes "vertical sausage"
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(
                        RepeatFreq.DAILY to R.string.repeat_freq_daily,
                        RepeatFreq.WEEKLY to R.string.repeat_freq_weekly,
                        RepeatFreq.MONTHLY to R.string.repeat_freq_monthly
                    )

                    opts.forEachIndexed { index, (f, labelRes) ->
                        SegmentedButton(
                            selected = freq == f,
                            onClick = { freq = f },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = opts.size
                            ),
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Interval (stepper)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.repeat_interval),
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { interval = (interval - 1).coerceAtLeast(1) },
                        enabled = enabled && interval > 1
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                    }

                    Text(
                        text = interval.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.widthIn(min = 32.dp),
                        textAlign = TextAlign.Center
                    )

                    FilledTonalIconButton(
                        onClick = { interval += 1 },
                        enabled = enabled
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }

                // Weekly: day chips
                AnimatedVisibility(visible = enabled && freq == RepeatFreq.WEEKLY) {
                    val firstDow = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
                    val ordered = remember(firstDow) { orderedWeekDays(firstDow) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.repeat_weekdays),
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ordered.forEach { dow ->
                                val selected = weekDays.contains(dow)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val newSet = weekDays.toMutableSet()
                                        if (selected) newSet.remove(dow) else newSet.add(dow)
                                        weekDays = newSet
                                    },
                                    enabled = enabled,
                                    label = { Text(dow.getDisplayName(TextStyle.SHORT, locale)) }
                                )
                            }
                        }
                    }
                }

                // Monthly: day-of-month stepper
                AnimatedVisibility(visible = enabled && freq == RepeatFreq.MONTHLY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.repeat_day_of_month),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalIconButton(
                            onClick = { dayOfMonth = (dayOfMonth - 1).coerceAtLeast(1) },
                            enabled = enabled && dayOfMonth > 1
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }

                        Text(
                            text = dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.widthIn(min = 32.dp),
                            textAlign = TextAlign.Center
                        )

                        FilledTonalIconButton(
                            onClick = { dayOfMonth = (dayOfMonth + 1).coerceAtMost(31) },
                            enabled = enabled && dayOfMonth < 31
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!enabled) {
                    onConfirm(null)
                    return@TextButton
                }

                val safeWeekDays =
                    if (freq == RepeatFreq.WEEKLY) weekDays.takeIf { it.isNotEmpty() } ?: setOf(
                        defaultDow
                    )
                    else emptySet()

                onConfirm(
                    RepeatRule(
                        freq = freq,
                        interval = interval.coerceAtLeast(1),
                        weekDays = safeWeekDays,
                        dayOfMonth = if (freq == RepeatFreq.MONTHLY) dayOfMonth.coerceIn(
                            1,
                            31
                        ) else null
                    )
                )
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}