package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RepeatFreq
import com.alphaomegos.annasagenda.RepeatRule
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.collections.orEmpty
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.items


@Composable
fun RecurringTasksScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val locale = appLocale()
    val today = remember { LocalDate.now() }

    val tasksById = remember(state.tasks) { state.tasks.associateBy { it.id } }

    // Template subtasks (the ones user edits on the template task)
    val templateSubtasksByTaskId = remember(state.subtasks) {
        state.subtasks
            .filter { it.originSubtaskId == null }
            .groupBy { it.taskId }
    }

    // 1) repeating TASK templates
    val taskTemplates = remember(state.tasks) {
        state.tasks
            .filter { it.originTaskId == null && it.repeatRule != null }
            .sortedBy { it.id }
    }

    // 2) repeating SUBTASK templates (pair: subtask + its parent template task)
    val subtaskTemplates = remember(state.subtasks, tasksById) {
        state.subtasks
            .filter { it.originSubtaskId == null && it.repeatRule != null }
            .mapNotNull { s ->
                val parent = tasksById[s.taskId] ?: return@mapNotNull null
                if (parent.originTaskId != null) return@mapNotNull null
                s to parent
            }
            .sortedWith(compareBy({ it.second.id }, { it.first.id }))
    }

    val confirmDeleteTaskId = remember { mutableStateOf<Long?>(null) }
    val confirmDeleteSubtaskId = remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.recurring_tasks_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(12.dp))

        if (taskTemplates.isEmpty() && subtaskTemplates.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.recurring_tasks_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                if (taskTemplates.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.repeating_tasks_header),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    items(taskTemplates) { t ->
                        val anchor = t.date
                        val rule = t.repeatRule!!
                        val ruleText = repeatRuleText(rule, anchor)

                        val anchorText = anchor?.let {
                            DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(locale)
                                .format(it)
                        } ?: stringResource(R.string.recurring_tasks_no_date)

                        val subs = templateSubtasksByTaskId[t.id].orEmpty().sortedBy { it.id }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.description, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.recurring_tasks_starts_line,
                                        anchorText,
                                        ruleText
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (subs.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    subs.forEach { s ->
                                        Text(
                                            "• ${s.description}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteTaskId.value = t.id }) {
                                        Text(stringResource(R.string.recurring_tasks_delete_from_today))
                                    }
                                }
                            }
                        }
                    }
                }

                if (subtaskTemplates.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.repeating_subtasks_header),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    items(subtaskTemplates) { (s, parent) ->
                        val anchor = parent.date
                        val rule = s.repeatRule!!
                        val ruleText = repeatRuleText(rule, anchor)

                        val anchorText = anchor?.let {
                            DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(locale)
                                .format(it)
                        } ?: stringResource(R.string.recurring_tasks_no_date)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    parent.description,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.recurring_tasks_starts_line,
                                        anchorText,
                                        ruleText
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "• ${s.description}",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { confirmDeleteSubtaskId.value = s.id }) {
                                        Text(stringResource(R.string.recurring_tasks_delete_from_today))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }

    if (confirmDeleteTaskId.value != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTaskId.value = null },
            title = { Text(stringResource(R.string.delete_repeating_task_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_task_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTaskSeriesFrom(confirmDeleteTaskId.value!!, today)
                    confirmDeleteTaskId.value = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDeleteTaskId.value = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmDeleteSubtaskId.value != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSubtaskId.value = null },
            title = { Text(stringResource(R.string.delete_repeating_subtask_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_repeating_subtask_text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSubtaskSeriesFrom(confirmDeleteSubtaskId.value!!, today)
                    confirmDeleteSubtaskId.value = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSubtaskId.value = null }) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun repeatRuleText(rule: RepeatRule, anchor: LocalDate?): String {
    val interval = rule.interval.coerceAtLeast(1)
    val locale = appLocale()

    return when (rule.freq) {
        RepeatFreq.DAILY -> {
            if (interval == 1) {
                stringResource(R.string.repeat_daily_every_day)
            } else {
                pluralStringResource(R.plurals.repeat_daily_every_n_days, interval, interval)
            }
        }

        RepeatFreq.WEEKLY -> {
            val days = (rule.weekDays.takeIf { it.isNotEmpty() }
                ?: anchor?.let { setOf(it.dayOfWeek) }
                ?: emptySet()
                    ).sortedBy { it.value }

            val names = days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }

            if (interval == 1) {
                stringResource(R.string.repeat_weekly_every_week_on, names)
            } else {
                pluralStringResource(
                    R.plurals.repeat_weekly_every_n_weeks_on,
                    interval,
                    interval,
                    names
                )
            }
        }

        RepeatFreq.MONTHLY -> {
            val dom = rule.dayOfMonth ?: anchor?.dayOfMonth ?: 1

            if (interval == 1) {
                stringResource(R.string.repeat_monthly_every_month_on_day, dom)
            } else {
                pluralStringResource(
                    R.plurals.repeat_monthly_every_n_months_on_day,
                    interval,
                    interval,
                    dom
                )
            }
        }
    }
}