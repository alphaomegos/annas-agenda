package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AnthropometryRange
import com.alphaomegos.annasagenda.DateWindow
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.formatShortDate
import java.time.LocalDate

private val rangeLabels = listOf(
    AnthropometryRange.WEEK to R.string.anthro_range_week,
    AnthropometryRange.MONTH to R.string.anthro_range_month,
    AnthropometryRange.QUARTER to R.string.anthro_range_quarter,
    AnthropometryRange.YEAR to R.string.anthro_range_year,
    AnthropometryRange.ALL to R.string.anthro_range_all,
    AnthropometryRange.CUSTOM to R.string.anthro_range_custom,
)

/**
 * The row of chips above the chart.
 *
 * Scrolls sideways rather than wrapping onto a second line: six chips do not
 * fit across a phone in Russian, and a bar that is one line tall on some
 * languages and two on others moves the chart up and down for no reason the
 * user can see.
 *
 * Tapping Custom always opens the picker, including when Custom is already
 * chosen — otherwise the only way back to the dialog to change one of the two
 * dates is to pick another range and return, which is a trap people walk into
 * once and then avoid the feature.
 */
@Composable
internal fun AnthropometryRangeBar(
    selected: AnthropometryRange,
    custom: DateWindow?,
    onSelect: (AnthropometryRange) -> Unit,
    onCustomPicked: (DateWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rangeLabels.forEach { (range, labelRes) ->
            FilterChip(
                selected = range == selected,
                onClick = {
                    if (range == AnthropometryRange.CUSTOM) {
                        showCustom = true
                    }
                    onSelect(range)
                },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }

    if (showCustom) {
        CustomRangeDialog(
            current = custom,
            onDismiss = { showCustom = false },
            onPicked = {
                onCustomPicked(it)
                showCustom = false
            },
        )
    }
}

/**
 * Two dates, each behind its own button.
 *
 * No validation that the first is before the second, on purpose: the range is
 * put the right way round when it is used, so picking them backwards shows the
 * range the user obviously meant rather than an error message about the order
 * of two dates they can both see.
 */
@Composable
private fun CustomRangeDialog(
    current: DateWindow?,
    onDismiss: () -> Unit,
    onPicked: (DateWindow) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val locale = appLocale()

    var from by remember { mutableStateOf(current?.from ?: today.minusMonths(1).plusDays(1)) }
    var to by remember { mutableStateOf(current?.to ?: today) }
    var picking by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anthro_range_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picking = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${stringResource(R.string.anthro_range_from)}: ${formatShortDate(from, locale)}")
                }
                OutlinedButton(
                    onClick = { picking = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${stringResource(R.string.anthro_range_to)}: ${formatShortDate(to, locale)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(DateWindow(from, to)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    val pickingStart = picking
    if (pickingStart != null) {
        PickDateDialog(
            initialDate = if (pickingStart) from else to,
            onDismiss = { picking = null },
            onPicked = { picked ->
                if (pickingStart) from = picked else to = picked
            },
        )
    }
}
