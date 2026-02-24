package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.RunningPlanEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningPlanScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    // prune incomplete past rows when entering
    LaunchedEffect(Unit) {
        vm.pruneRunningPlanNow()
    }

    val showApprove = rememberSaveable { mutableStateOf(false) }

    val showReset = rememberSaveable { mutableStateOf(false) }

    val resetPhrase = rememberSaveable { mutableStateOf("") }

    val locale = Locale.getDefault()
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMMM", locale) }

    val approved = state.runningPlanApproved
    val entriesByDate = remember(state.runningPlanEntries) {
        state.runningPlanEntries.associateBy { it.date }
    }

    val rows: List<LocalDate> = if (!approved) {
        val today = LocalDate.now()
        (0L..364L).map { today.plusDays(it) }
    } else {
        state.runningPlanEntries.sortedBy { it.date }.map { it.date }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.running_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { if (!approved) showApprove.value = true else showReset.value = true }) {
                        Text(
                            if (!approved) stringResource(R.string.running_approve)
                            else stringResource(R.string.running_reset)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            HeaderRow()

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.running_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(rows) { date ->
                        val entry = entriesByDate[date]
                        RunningRow(
                            date = date,
                            dateFmt = dateFmt,
                            entry = entry,
                            approved = approved,
                            onDistanceChange = { vm.updateRunningPlanEntry(date, distanceKmText = it) },
                            onTimeChange = { vm.updateRunningPlanEntry(date, durationHhMmText = it) },
                            onPaceChange = { vm.updateRunningPlanEntry(date, paceText = it) }
                        )
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                    }
                }
            }
        }
    }

    if (showApprove.value) {
        AlertDialog(
            onDismissRequest = { showApprove.value = false },
            title = { Text(stringResource(R.string.running_approve_confirm_title)) },
            text = { Text(stringResource(R.string.running_approve_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showApprove.value = false
                    vm.approveRunningPlan()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApprove.value = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showReset.value) {
        val required = stringResource(R.string.running_reset_phrase_required)
        val canConfirm = resetPhrase.value == required

        AlertDialog(
            onDismissRequest = {
                showReset.value = false
                resetPhrase.value = ""
            },
            title = { Text(stringResource(R.string.running_reset_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.running_reset_confirm_text))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetPhrase.value,
                        onValueChange = { resetPhrase.value = it },
                        label = { Text(stringResource(R.string.running_reset_phrase_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(required, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        showReset.value = false
                        resetPhrase.value = ""
                        vm.resetRunningPlan()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReset.value = false
                    resetPhrase.value = ""
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun HeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.running_col_date),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1.6f)
        )
        Text(
            text = stringResource(R.string.running_col_distance),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1.0f)
        )
        Text(
            text = stringResource(R.string.running_col_time),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.0f)
        )
        Text(
            text = stringResource(R.string.running_col_pace),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.0f)
        )

    }
}

@Composable
private fun RunningRow(
    date: LocalDate,
    dateFmt: DateTimeFormatter,
    entry: RunningPlanEntry?,
    approved: Boolean,
    onDistanceChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onPaceChange: (String) -> Unit,
) {
    val locale = Locale.getDefault()

    val dateText = remember(date, dateFmt, locale) {
        val s = date.format(dateFmt)
        s.replaceFirstChar { it.titlecase(locale) }
    }

    val distance = entry?.distanceKmText.orEmpty()

    val timeRaw = entry?.durationHhMmText.orEmpty()
    val timeDigits = remember(timeRaw) { timeRaw.filter { it.isDigit() }.take(4) }

    val paceRaw = entry?.paceText.orEmpty()
    val paceDigits = remember(paceRaw) { paceRaw.filter { it.isDigit() }.take(4) }


    val isComplete = approved &&
            distance.isNotBlank() &&
            timeDigits.isNotBlank() &&
            paceDigits.isNotBlank()

    val shape = RoundedCornerShape(12.dp)
    val completeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fieldTextStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(if (isComplete) completeBg else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            modifier = Modifier.weight(1.6f),
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = distance,
            onValueChange = { raw ->
                val cleaned = raw
                    .replace("\n", "")
                    .replace("\r", "")
                    .filter { it.isDigit() || it == '.' || it == ',' }
                onDistanceChange(cleaned)
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.running_hint_distance),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            singleLine = true,
            maxLines = 1,
            textStyle = fieldTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .weight(1.0f)
                .padding(start = 8.dp)
                .heightIn(min = 56.dp)
        )

        OutlinedTextField(
            value = timeDigits,
            onValueChange = { raw ->
                val cleaned = raw
                    .replace("\n", "")
                    .replace("\r", "")
                    .filter { it.isDigit() }
                    .take(4)
                onTimeChange(cleaned)
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.running_hint_time),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            singleLine = true,
            maxLines = 1,
            textStyle = fieldTextStyle,
            visualTransformation = TimeHhMmVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1.0f)
                .padding(start = 8.dp)
                .heightIn(min = 56.dp)
        )

        OutlinedTextField(
            value = paceDigits,
            onValueChange = { raw ->
                if (!approved) return@OutlinedTextField
                val cleaned = raw.filter { it.isDigit() }.take(4)
                onPaceChange(cleaned)
            },
            enabled = approved,
            readOnly = !approved,
            placeholder = {
                Text(
                    text = stringResource(R.string.running_hint_pace),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            singleLine = true,
            maxLines = 1,
            textStyle = fieldTextStyle,
            visualTransformation = PaceVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1.0f)
                .padding(start = 8.dp)
                .heightIn(min = 56.dp)
        )
    }
}
private class PaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(4)

        val out = when (raw.length) {
            0 -> ""
            1, 2 -> raw
            3 -> raw.substring(0, 2) + "'" + raw.substring(2, 3)
            else -> raw.substring(0, 2) + "'" + raw.substring(2, 4) + "\""
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                return when {
                    raw.length <= 2 -> o
                    raw.length == 3 -> if (o <= 2) o else 4
                    else -> { // raw.length == 4
                        when {
                            o <= 2 -> o
                            o <= 4 -> o + 1 // apostrophe
                            else -> 5
                        }
                    }
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceAtLeast(0)
                return when {
                    raw.length <= 2 -> t.coerceIn(0, raw.length)
                    raw.length == 3 -> when {
                        t <= 2 -> t
                        t == 3 -> 2 // on apostrophe
                        else -> 3
                    }
                    else -> { // raw.length == 4; transformed like 00'00"
                        when {
                            t <= 2 -> t
                            t == 3 -> 2
                            t <= 5 -> (t - 1).coerceIn(0, 4)
                            else -> 4
                        }
                    }
                }
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

private class TimeHhMmVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(4)

        val out = when (raw.length) {
            0 -> ""
            1, 2 -> raw
            3 -> raw.substring(0, 2) + ":" + raw.substring(2, 3)
            else -> raw.substring(0, 2) + ":" + raw.substring(2, 4)
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                return if (raw.length <= 2) o else if (o <= 2) o else o + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceAtLeast(0)
                return if (raw.length <= 2) {
                    t.coerceIn(0, raw.length)
                } else {
                    when {
                        t <= 2 -> t
                        t == 3 -> 2 // on ':'
                        else -> (t - 1).coerceIn(0, raw.length)
                    }
                }
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
