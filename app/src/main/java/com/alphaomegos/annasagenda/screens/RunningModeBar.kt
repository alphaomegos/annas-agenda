package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RunningMode

/**
 * Which of the two things this screen is doing.
 *
 * Both chips are always there, including on a phone where they do not fit
 * side by side — the row scrolls sideways for the same reason the chart's
 * ranges do. A mode the user cannot see is a mode they will not find.
 */
@Composable
internal fun RunningModeBar(
    selected: RunningMode,
    onSelect: (RunningMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == RunningMode.PLAN,
            onClick = { onSelect(RunningMode.PLAN) },
            label = { Text(stringResource(R.string.running_mode_plan)) },
        )
        FilterChip(
            selected = selected == RunningMode.BETWEEN,
            onClick = { onSelect(RunningMode.BETWEEN) },
            label = { Text(stringResource(R.string.running_mode_between)) },
        )
    }
}
