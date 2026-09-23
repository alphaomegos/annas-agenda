package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.DateTasksBlock
import com.alphaomegos.annasagenda.UNDONE_HORIZON_CHOICES
import com.alphaomegos.annasagenda.UNDONE_HORIZON_UNLIMITED
import com.alphaomegos.annasagenda.undoneDebt
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndoneTasksScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.undoneTasks.collectAsState()
    val locale = appLocale()

    val today = remember { LocalDate.now() }

    // Snapshotted on entry (and when the horizon changes) so that ticking a
    // task off does not make it vanish from under the finger.
    val initialUndoneTaskIds = remember(state.undoneHorizonDays) {
        undoneDebt(
            tasks = state.tasks,
            suppressedRecurrences = state.suppressedRecurrences,
            today = today,
            horizonDays = state.undoneHorizonDays,
        ).taskIds
    }

    val undoneDates = remember(state.tasks, initialUndoneTaskIds) {
        state.tasks
            .asSequence()
            .filter { it.id in initialUndoneTaskIds }
            .mapNotNull { it.date }
            .distinct()
            .sorted()
            .toList()
    }

    val hasUndone = undoneDates.isNotEmpty()
    var horizonMenuOpen by remember { mutableStateOf(false) }

    val lampIconRes = when {
        state.undoneLampMuted -> R.drawable.ic_undone_lamp_gray
        hasUndone -> R.drawable.ic_undone_lamp_red
        else -> R.drawable.ic_undone_lamp_green
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.undone_title))
                },
                navigationIcon = {
                    IconButton(onClick = { vm.toggleUndoneLampMuted() }) {
                        Icon(
                            painter = painterResource(lampIconRes),
                            contentDescription = stringResource(R.string.undone_lamp_toggle),
                            tint = Color.Unspecified
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { horizonMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = stringResource(R.string.undone_horizon_menu)
                            )
                        }

                        DropdownMenu(
                            expanded = horizonMenuOpen,
                            onDismissRequest = { horizonMenuOpen = false }
                        ) {
                            UNDONE_HORIZON_CHOICES.forEach { days ->
                                DropdownMenuItem(
                                    text = { Text(undoneHorizonLabel(days)) },
                                    onClick = {
                                        horizonMenuOpen = false
                                        vm.setUndoneHorizonDays(days)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (undoneDates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.undone_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(undoneDates, key = { it.toEpochDay() }) { date ->
                    val dateText = remember(date, locale) {
                        val fmt = DateTimeFormatter
                            .ofLocalizedDate(FormatStyle.LONG)
                            .withLocale(locale)
                        date.format(fmt)
                    }

                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    DateTasksBlock(
                        vm = vm,
                        state = state,
                        date = date,
                        includeDoneTasks = true,
                        visibleTaskIds = initialUndoneTaskIds
                    )
                }
            }
        }
    }
}

@Composable
private fun undoneHorizonLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.undone_horizon_7
        30 -> R.string.undone_horizon_30
        90 -> R.string.undone_horizon_90
        365 -> R.string.undone_horizon_365
        UNDONE_HORIZON_UNLIMITED -> R.string.undone_horizon_all
        else -> R.string.undone_horizon_30
    }
)
