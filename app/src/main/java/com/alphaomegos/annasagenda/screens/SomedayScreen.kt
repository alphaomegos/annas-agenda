package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.style.TextAlign
import com.alphaomegos.annasagenda.components.WayHomeButton
import com.alphaomegos.annasagenda.components.TopBarBack
import com.alphaomegos.annasagenda.model.DayScreenStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.DateTasksBlock
import com.alphaomegos.annasagenda.support.isSuppressedTemplateTaskOnItsDate
import androidx.compose.foundation.layout.Row
import com.alphaomegos.annasagenda.components.WayHomeFootButton

@Composable
fun SomedayScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onCreateTask: () -> Unit,
) {
    val state by vm.state.collectAsState()

    val somedayTasksCount = remember(state.tasks, state.suppressedRecurrences) {
        state.tasks.count { t ->
            t.date == null && !isSuppressedTemplateTaskOnItsDate(t, state.suppressedRecurrences)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        val newLook = state.dayScreenStyle == DayScreenStyle.NEW
        if (newLook) {
            // The day screen's new look (28.10): Back, the title and the house
            // in one row at the top, «+ Task» light under it, no foot.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TopBarBack(onBack)
                Text(
                    text = stringResource(R.string.someday_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                WayHomeButton()
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(
                    onClick = onCreateTask,
                    contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.day_new_task))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.someday_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (somedayTasksCount == 0) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.someday_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DateTasksBlock(vm = vm, state = state, date = null, newLook = newLook)
                }
            }
        }

        if (!newLook) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onCreateTask, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.create_task))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.back))
                }
                WayHomeFootButton()
            }
        }
    }
}