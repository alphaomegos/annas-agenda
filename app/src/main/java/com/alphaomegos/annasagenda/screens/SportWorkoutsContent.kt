package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.model.SportWorkout
import com.alphaomegos.annasagenda.support.SPORT_KCAL_MAX
import com.alphaomegos.annasagenda.support.STANDARD_SPORT_KINDS
import com.alphaomegos.annasagenda.support.SportPeriod
import com.alphaomegos.annasagenda.support.SportSort
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.support.parseSportKcal
import com.alphaomegos.annasagenda.support.sportDurationInput
import com.alphaomegos.annasagenda.support.sportKindFromInput
import com.alphaomegos.annasagenda.support.sportKindLabel
import com.alphaomegos.annasagenda.support.sportKindSuggestions
import com.alphaomegos.annasagenda.support.sportPeriodWindow
import com.alphaomegos.annasagenda.support.sportTotals
import com.alphaomegos.annasagenda.support.sportWorkoutsShown
import com.alphaomegos.annasagenda.util.appLocale
import java.text.NumberFormat
import java.time.LocalDate
import com.alphaomegos.annasagenda.components.flatCardElevation
import com.alphaomegos.annasagenda.components.flatCardColors

/** Each standard kind's name (05.11): a key without one here would show as the key. */
private val SPORT_KIND_NAMES: Map<String, Int> = mapOf(
    "snowboard" to R.string.sport_kind_snowboard,
    "alpine_skiing" to R.string.sport_kind_alpine_skiing,
    "cross_country_skiing" to R.string.sport_kind_cross_country_skiing,
    "skating" to R.string.sport_kind_skating,
    "zumba" to R.string.sport_kind_zumba,
    "dance" to R.string.sport_kind_dance,
    "strength" to R.string.sport_kind_strength,
    "crossfit" to R.string.sport_kind_crossfit,
    "yoga" to R.string.sport_kind_yoga,
    "pilates" to R.string.sport_kind_pilates,
    "swimming" to R.string.sport_kind_swimming,
    "cycling" to R.string.sport_kind_cycling,
    "nordic_walking" to R.string.sport_kind_nordic_walking,
    "tennis" to R.string.sport_kind_tennis,
    "football" to R.string.sport_kind_football,
    "martial_arts" to R.string.sport_kind_martial_arts,
    "climbing" to R.string.sport_kind_climbing,
    "rowing" to R.string.sport_kind_rowing,
)

/** The standard kinds' names in the app's language, by key. */
@Composable
internal fun sportKindLabels(): Map<String, String> =
    STANDARD_SPORT_KINDS.mapNotNull { key -> SPORT_KIND_NAMES[key]?.let { key to stringResource(it) } }.toMap()

/** Hours and minutes once there is an hour, minutes before that — the way the runs say it. */
@Composable
private fun sportMinutesText(minutes: Int): String =
    if (minutes >= 60) {
        stringResource(R.string.running_workout_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.running_workout_minutes_only, minutes)
    }

private fun sportPeriodName(period: SportPeriod): Int = when (period) {
    SportPeriod.ALL -> R.string.sport_period_all
    SportPeriod.DAYS_7 -> R.string.sport_period_7
    SportPeriod.DAYS_30 -> R.string.sport_period_30
    SportPeriod.YEAR -> R.string.sport_period_year
    SportPeriod.CUSTOM -> R.string.sport_period_custom
}

private fun sportSortName(sort: SportSort): Int = when (sort) {
    SportSort.DATE -> R.string.sport_sort_date
    SportSort.KCAL -> R.string.sport_sort_kcal
    SportSort.DURATION -> R.string.sport_sort_duration
    SportSort.KIND -> R.string.sport_sort_kind
}

/**
 * «Other» in «Sport» (05.11, Eduard): workouts of any kind on any day, their
 * minutes and kilocalories as typed. Filtered by kind and by period, sorted by
 * date, kilocalories, minutes or kind — the same sort again turns it round; a
 * tap opens a workout to change or delete. «+» in the top bar adds one, each
 * ask a new number, like the runs.
 */
@Composable
internal fun SportWorkoutsContent(
    workouts: List<SportWorkout>,
    today: LocalDate,
    onSave: (Long?, LocalDate, String?, String, String) -> Boolean,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    newLook: Boolean = true,
    addRequest: Int = 0,
) {
    val context = LocalContext.current
    val locale = appLocale()
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val labels = sportKindLabels()

    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // The last «+» answered, saved: turning the phone does not ask again.
    var answeredAdd by rememberSaveable { mutableStateOf(addRequest) }
    LaunchedEffect(addRequest) {
        if (addRequest != answeredAdd) {
            answeredAdd = addRequest
            editingId = null
            editing = true
        }
    }

    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var periodName by rememberSaveable { mutableStateOf(SportPeriod.ALL.name) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var sortName by rememberSaveable { mutableStateOf(SportSort.DATE.name) }
    var descending by rememberSaveable { mutableStateOf(true) }
    // One's own dates are picked in two steps: 1 the first day, 2 the last.
    var pickingStep by rememberSaveable { mutableStateOf(0) }
    var pickedFrom by rememberSaveable { mutableStateOf<Long?>(null) }

    val period = SportPeriod.entries.firstOrNull { it.name == periodName } ?: SportPeriod.ALL
    val sort = SportSort.entries.firstOrNull { it.name == sortName } ?: SportSort.DATE
    // A kind whose workouts are all gone filters nothing rather than everything.
    val kind = kindFilter?.takeIf { k -> workouts.any { it.kind == k } }
    val (from, to) = sportPeriodWindow(period, today, customFrom?.let(LocalDate::ofEpochDay), customTo?.let(LocalDate::ofEpochDay))
    val shown = remember(workouts, labels, kind, from, to, sort, descending) {
        sportWorkoutsShown(workouts, labels, kind, from, to, sort, descending)
    }
    val totals = remember(shown) { sportTotals(shown) }
    val usedKinds = remember(workouts, labels) {
        sportKindSuggestions(workouts, labels).filter { k -> workouts.any { it.kind == k } }
    }
    val periodLabel = if (period == SportPeriod.CUSTOM && from != null && to != null) {
        formatShortDate(from, locale) + " – " + formatShortDate(to, locale)
    } else {
        stringResource(sportPeriodName(period))
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (workouts.isNotEmpty()) {
            SportFilterBar(
                kinds = usedKinds,
                labels = labels,
                kind = kind,
                onKind = { kindFilter = it },
                period = period,
                periodLabel = periodLabel,
                onPeriod = { p -> if (p == SportPeriod.CUSTOM) pickingStep = 1 else periodName = p.name },
                sort = sort,
                descending = descending,
                onSort = { s ->
                    if (s == sort) {
                        descending = !descending
                    } else {
                        sortName = s.name
                        descending = s.descendingFirst
                    }
                },
            )
            val parts = listOfNotNull(
                context.resources.getQuantityString(R.plurals.sport_workout_count, totals.workouts, totals.workouts),
                totals.minutes.takeIf { it > 0 }?.let { sportMinutesText(it) },
                totals.kcal.takeIf { it > 0 }?.let { stringResource(R.string.sport_kcal_value, numbers.format(it)) },
            )
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
        }

        Box(modifier = Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (workouts.isEmpty()) R.string.sport_empty else R.string.sport_empty_filtered))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = shown, key = { it.id }) { workout ->
                        SportWorkoutRow(
                            kindLabel = sportKindLabel(workout.kind, labels),
                            dateLabel = formatShortDate(workout.date, locale),
                            minutesLabel = workout.durationMinutes?.let { sportMinutesText(it) },
                            kcalLabel = workout.kcal?.let { stringResource(R.string.sport_kcal_value, numbers.format(it)) },
                            flat = newLook,
                            onClick = {
                                editingId = workout.id
                                editing = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        val current = editingId?.let { id -> workouts.firstOrNull { it.id == id } }
        SportWorkoutDialog(
            initial = current,
            today = today,
            workouts = workouts,
            labels = labels,
            onDismiss = { editing = false },
            onSave = { date, k, duration, kcal ->
                val saved = onSave(current?.id, date, k, duration, kcal)
                if (saved) editing = false
                saved
            },
            onDelete = current?.let { w ->
                {
                    onDelete(w.id)
                    editing = false
                }
            },
        )
    }

    if (pickingStep == 1) {
        PickDateDialog(
            initialDate = customFrom?.let(LocalDate::ofEpochDay) ?: today.minusDays(29),
            onDismiss = { if (pickingStep == 1) pickingStep = 0 },
            onPicked = {
                pickedFrom = it.toEpochDay()
                pickingStep = 2
            },
        )
    }
    if (pickingStep == 2) {
        PickDateDialog(
            initialDate = customTo?.let(LocalDate::ofEpochDay) ?: today,
            onDismiss = { if (pickingStep == 2) pickingStep = 0 },
            onPicked = { picked ->
                val first = pickedFrom ?: picked.toEpochDay()
                val last = picked.toEpochDay()
                customFrom = minOf(first, last)
                customTo = maxOf(first, last)
                periodName = SportPeriod.CUSTOM.name
                pickingStep = 0
            },
        )
    }
}

/** The kind, the period and the order, as chips that open their choices; the row scrolls sideways on a narrow phone. */
@Composable
private fun SportFilterBar(
    kinds: List<String>,
    labels: Map<String, String>,
    kind: String?,
    onKind: (String?) -> Unit,
    period: SportPeriod,
    periodLabel: String,
    onPeriod: (SportPeriod) -> Unit,
    sort: SportSort,
    descending: Boolean,
    onSort: (SportSort) -> Unit,
) {
    var kindsOpen by rememberSaveable { mutableStateOf(false) }
    var periodsOpen by rememberSaveable { mutableStateOf(false) }
    var sortsOpen by rememberSaveable { mutableStateOf(false) }
    val arrow = if (descending) " ↓" else " ↑"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            FilterChip(
                selected = kind != null,
                onClick = { kindsOpen = true },
                label = {
                    Text(
                        kind?.let { sportKindLabel(it, labels) } ?: stringResource(R.string.sport_kind_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = kindsOpen, onDismissRequest = { kindsOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sport_kind_all)) },
                    onClick = {
                        kindsOpen = false
                        onKind(null)
                    },
                )
                kinds.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(sportKindLabel(k, labels)) },
                        onClick = {
                            kindsOpen = false
                            onKind(k)
                        },
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = period != SportPeriod.ALL,
                onClick = { periodsOpen = true },
                label = { Text(periodLabel, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = periodsOpen, onDismissRequest = { periodsOpen = false }) {
                SportPeriod.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sportPeriodName(p))) },
                        onClick = {
                            periodsOpen = false
                            onPeriod(p)
                        },
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = sort != SportSort.DATE || !descending,
                onClick = { sortsOpen = true },
                label = { Text(stringResource(sportSortName(sort)) + arrow, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = sortsOpen, onDismissRequest = { sortsOpen = false }) {
                SportSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sportSortName(s)) + if (s == sort) arrow else "") },
                        onClick = {
                            sortsOpen = false
                            onSort(s)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SportWorkoutRow(
    kindLabel: String,
    dateLabel: String,
    minutesLabel: String?,
    kcalLabel: String?,
    flat: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        // Flat in the new look: the day screen's surface colour, no shadow.
        colors = if (flat) flatCardColors() else CardDefaults.elevatedCardColors(),
        elevation = if (flat) flatCardElevation() else CardDefaults.elevatedCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dateLabel, style = MaterialTheme.typography.labelMedium)
                Text(text = kindLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (minutesLabel != null) {
                    Text(
                        text = minutesLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (kcalLabel != null) {
                Text(
                    text = kcalLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * A workout, new or one to change. The kind is typed or picked from the list
 * under the field — the most used first, narrowed to what is typed; «▾» shows
 * them all. A typed name of a standard kind is that kind.
 */
@Composable
private fun SportWorkoutDialog(
    initial: SportWorkout?,
    today: LocalDate,
    workouts: List<SportWorkout>,
    labels: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String?, String, String) -> Boolean,
    onDelete: (() -> Unit)?,
) {
    val locale = appLocale()
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    var date by rememberSaveable { mutableStateOf((initial?.date ?: today).toEpochDay()) }
    var kindText by rememberSaveable { mutableStateOf(initial?.let { sportKindLabel(it.kind, labels) } ?: "") }
    var duration by rememberSaveable { mutableStateOf(sportDurationInput(initial?.durationMinutes)) }
    var kcal by rememberSaveable { mutableStateOf(initial?.kcal?.toString() ?: "") }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // Said only after an OK that could not be kept: 1 no kind or no number, 2 the kilocalories.
    var refused by rememberSaveable { mutableStateOf(0) }

    val offered = remember(workouts, labels, kindText, showAll) {
        sportKindSuggestions(workouts, labels, if (showAll) "" else kindText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sport_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sport_date) + ": " + formatShortDate(LocalDate.ofEpochDay(date), locale))
                }

                OutlinedTextField(
                    value = kindText,
                    onValueChange = {
                        kindText = it
                        listOpen = true
                        showAll = false
                        refused = 0
                    },
                    label = { Text(stringResource(R.string.sport_kind)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (listOpen && showAll) {
                                    listOpen = false
                                } else {
                                    listOpen = true
                                    showAll = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.sport_kind_list))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) listOpen = true },
                )

                if (listOpen && offered.isNotEmpty()) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            offered.forEach { k ->
                                val name = sportKindLabel(k, labels)
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            kindText = name
                                            listOpen = false
                                            showAll = false
                                            refused = 0
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = duration,
                    onValueChange = {
                        duration = it
                        refused = 0
                    },
                    label = { Text(stringResource(R.string.sport_duration)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = kcal,
                    onValueChange = {
                        kcal = it
                        refused = 0
                    },
                    label = { Text(stringResource(R.string.sport_kcal)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (refused != 0) {
                    Text(
                        text = if (refused == 2) {
                            stringResource(R.string.sport_kcal_invalid, numbers.format(SPORT_KCAL_MAX))
                        } else {
                            stringResource(R.string.sport_needs)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (kcal.isNotBlank() && parseSportKcal(kcal) == null) {
                        refused = 2
                    } else {
                        val kind = sportKindFromInput(kindText, labels, used = workouts.map { it.kind })
                        refused = if (onSave(LocalDate.ofEpochDay(date), kind, duration, kcal)) 0 else 1
                    }
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
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
