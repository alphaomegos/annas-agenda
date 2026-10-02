package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.NotificationSettings
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.REMINDER_LEAD_CHOICES
import com.alphaomegos.annasagenda.SummaryToday
import com.alphaomegos.annasagenda.canPostNotifications
import com.alphaomegos.annasagenda.formatTaskTime
import com.alphaomegos.annasagenda.minuteOfDay
import com.alphaomegos.annasagenda.timeFromMinuteOfDay

/** The lead as the reminders choice reads it; -1 stands for "off" in a Bundle. */
private const val LEAD_OFF = -1

/**
 * Daily summaries and task reminders (agreed 02.10).
 *
 * Summaries: any number of times a day, and what they list — today's tasks
 * (all, only those not done, or none) and, separately, the debts. Reminders:
 * one lead for every timed task. Nothing is set until OK, and everything
 * picked survives turning the phone.
 *
 * On OK with something switched on and notifications not allowed, the
 * caller asks for them (Android 13 and later); the settings are kept either
 * way, and work as soon as it is allowed. The asking lives with the caller
 * because this dialog is gone by the time the answer comes.
 */
@Composable
internal fun NotificationSettingsDialog(
    initial: NotificationSettings,
    onDismiss: () -> Unit,
    onSave: (NotificationSettings) -> Unit,
    onNeedPermission: () -> Unit,
) {
    val context = LocalContext.current
    // Minutes of the day joined into one string: a Bundle takes it as is.
    var minutesText by rememberSaveable { mutableStateOf(initial.summaryMinutes.joinToString(",")) }
    val minutes = minutesText.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until 24 * 60 }.distinct().sorted()
    var todayName by rememberSaveable { mutableStateOf(initial.summaryToday.name) }
    val today = SummaryToday.entries.firstOrNull { it.name == todayName } ?: SummaryToday.UNDONE
    var debts by rememberSaveable { mutableStateOf(initial.summaryDebts) }
    var lead by rememberSaveable { mutableIntStateOf(initial.reminderLeadMinutes ?: LEAD_OFF) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    fun result() = NotificationSettings(
        summaryMinutes = minutes,
        summaryToday = today,
        summaryDebts = debts,
        reminderLeadMinutes = lead.takeIf { it != LEAD_OFF },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.notif_summary_section), style = MaterialTheme.typography.titleSmall)
                if (minutes.isEmpty()) {
                    Text(stringResource(R.string.notif_summary_none), style = MaterialTheme.typography.bodyMedium)
                }
                minutes.forEach { m ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFromMinuteOfDay(m)?.let { formatTaskTime(it) }.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(onClick = { minutesText = (minutes - m).joinToString(",") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notif_summary_remove_time))
                        }
                    }
                }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notif_summary_add_time))
                }

                if (minutes.isNotEmpty()) {
                    Text(stringResource(R.string.notif_summary_lists), style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            SummaryToday.UNDONE to R.string.notif_today_undone,
                            SummaryToday.ALL to R.string.notif_today_all,
                            SummaryToday.NONE to R.string.notif_today_none,
                        ).forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { todayName = value.name },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = today == value, onClick = { todayName = value.name })
                                Text(stringResource(label))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { debts = !debts },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = debts, onCheckedChange = { debts = it })
                        Text(stringResource(R.string.notif_summary_with_debts))
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.notif_reminders_section), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    REMINDER_LEAD_CHOICES.forEach { choice ->
                        val value = choice ?: LEAD_OFF
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { lead = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lead == value, onClick = { lead = value })
                            Text(
                                when (choice) {
                                    null -> stringResource(R.string.notif_reminder_off)
                                    0 -> stringResource(R.string.notif_reminder_at_time)
                                    else -> stringResource(R.string.notif_reminder_before, choice)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val settings = result()
                val wantsSomething = settings.summaryMinutes.isNotEmpty() || settings.reminderLeadMinutes != null
                if (wantsSomething && !canPostNotifications(context)) onNeedPermission()
                onSave(settings)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickingTime) {
        PickTimeDialog(
            initialTime = null,
            onDismiss = { pickingTime = false },
            onPicked = { picked ->
                minutesText = (minutes + minuteOfDay(picked)).distinct().sorted().joinToString(",")
            },
        )
    }
}
