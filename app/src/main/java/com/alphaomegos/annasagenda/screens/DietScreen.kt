package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.DietDishOnDay
import com.alphaomegos.annasagenda.DietItem
import com.alphaomegos.annasagenda.FoodDraftState
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.dietKcalFor
import com.alphaomegos.annasagenda.foodDraftAfterKcalTyped
import com.alphaomegos.annasagenda.foodDraftAfterPickingSuggestion
import com.alphaomegos.annasagenda.foodDraftAfterTitleChange
import com.alphaomegos.annasagenda.foodSuggestionForName
import com.alphaomegos.annasagenda.foodSuggestionsFor
import com.alphaomegos.annasagenda.FoodEntry
import com.alphaomegos.annasagenda.util.appLocale
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * 6.2: the day's dishes in the calorimeter, each with a box to tick.
 *
 * A past day's unticked dish is drawn faded and can still be ticked — the
 * breakfast written down a day late. A day ahead shows its plan with the
 * boxes off: nothing has been eaten there yet.
 */
@Composable
internal fun DietDayCard(
    dishes: List<DietDishOnDay>,
    onTick: (itemId: Long, eaten: Boolean) -> Unit,
) {
    val planned = dishes.sumOf { it.item.kcal }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.diet_card_title, planned),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            dishes.forEach { dish ->
                val eaten = dish.eaten != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (dish.faded) 0.5f else 1f)
                        .clickable(enabled = dish.canTick) { onTick(dish.item.id, !eaten) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = eaten,
                        onCheckedChange = { onTick(dish.item.id, it) },
                        enabled = dish.canTick,
                    )
                    Text(text = dish.item.title, modifier = Modifier.weight(1f))
                    // What was eaten, if it was: the dish may have been edited since.
                    Text("${dish.eaten?.kcal ?: dish.item.kcal} ${stringResource(R.string.kcal_short)}")
                }
            }
        }
    }
}

/** The days of the week in the order the phone's language starts its week. */
@Composable
private fun weekInLocaleOrder(): List<DayOfWeek> {
    val locale = appLocale()
    return remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek
        (0L until 7L).map { first.plus(it) }
    }
}

@Composable
fun DietRoute(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.calorimeter.collectAsState()
    DietContent(
        plan = state.dietPlan,
        log = state.foodLog,
        onBack = onBack,
        onAdd = vm::addDietDish,
        onEdit = vm::editDietDish,
        onRemove = vm::removeDietDish,
        onMove = vm::moveDietDish,
        onCopy = vm::copyDietDay,
    )
}

/**
 * The diet itself: a week of days, each a list of dishes.
 *
 * A dish is written the way the food log writes it, portion in the name, so
 * the suggestions from what has been eaten before work here too and a
 * different portion is priced the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietContent(
    plan: Map<DayOfWeek, List<DietItem>>,
    log: List<FoodEntry>,
    onBack: () -> Unit,
    onAdd: (DayOfWeek, String, Int) -> Unit,
    onEdit: (Long, String, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onCopy: (DayOfWeek, Set<DayOfWeek>) -> Unit,
) {
    val locale = appLocale()
    val week = weekInLocaleOrder()

    // ISO day number: a DayOfWeek is an enum, an Int goes into a Bundle as is.
    var dayIso by rememberSaveable { mutableIntStateOf(week.first().value) }
    val day = DayOfWeek.of(dayIso)
    val dishes = plan[day].orEmpty()

    // The dish dialog: null closed, 0 a new dish, otherwise the dish's id.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var copying by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.diet_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                week.forEach { d ->
                    FilterChip(
                        selected = d == day,
                        onClick = { dayIso = d.value },
                        label = { Text(d.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
                    )
                }
            }

            Text(
                text = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { it.titlecase(locale) } +
                    " · " + stringResource(R.string.diet_day_total, dietKcalFor(plan, day)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (dishes.isEmpty()) {
                Text(stringResource(R.string.diet_day_empty), style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(dishes, key = { _, d -> d.id }) { index, dish ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dish.title)
                            Text(
                                "${dish.kcal} ${stringResource(R.string.kcal_short)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { onMove(dish.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.diet_dish_move_up))
                        }
                        IconButton(onClick = { onMove(dish.id, 1) }, enabled = index < dishes.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.diet_dish_move_down))
                        }
                        IconButton(onClick = { editingId = dish.id }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.diet_dish_edit))
                        }
                        IconButton(onClick = { onRemove(dish.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }

            OutlinedButton(onClick = { editingId = 0L }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diet_add_dish))
            }
            OutlinedButton(onClick = { copying = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diet_copy_day))
            }
        }
    }

    editingId?.let { id ->
        val existing = dishes.firstOrNull { it.id == id }
        DietDishDialog(
            initial = existing,
            log = log,
            onDismiss = { editingId = null },
            onSave = { title, kcal ->
                if (existing == null) onAdd(day, title, kcal) else onEdit(id, title, kcal)
                editingId = null
            },
        )
    }

    if (copying) {
        DietCopyDayDialog(
            from = day,
            week = week,
            onDismiss = { copying = false },
            onCopy = { targets ->
                onCopy(day, targets)
                copying = false
            },
        )
    }
}

/** A dish: its name with the portion in it, and its kilocalories. */
@Composable
private fun DietDishDialog(
    initial: DietItem?,
    log: List<FoodEntry>,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var kcalText by rememberSaveable { mutableStateOf(initial?.kcal?.toString().orEmpty()) }
    // As in the calorimeter: "the app is pricing this", kept as a name.
    var pricedFromName by rememberSaveable { mutableStateOf<String?>(null) }

    val pricedFrom = remember(pricedFromName, log) { pricedFromName?.let { foodSuggestionForName(it, log) } }
    val draft = FoodDraftState(title = title, kcalText = kcalText, pricedFrom = pricedFrom)
    fun apply(next: FoodDraftState) {
        title = next.title
        kcalText = next.kcalText
        pricedFromName = next.pricedFrom?.name
    }
    val suggestions = remember(title, log) { foodSuggestionsFor(title, log) }

    val kcal = kcalText.trim().toIntOrNull()
    val canSave = title.isNotBlank() && kcal != null && kcal >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diet_dish_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { apply(foodDraftAfterTitleChange(draft, it)) },
                    label = { Text(stringResource(R.string.diet_dish_title)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FoodSuggestionList(
                    suggestions = suggestions,
                    onPick = { apply(foodDraftAfterPickingSuggestion(it)) },
                )
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { apply(foodDraftAfterKcalTyped(draft, it)) },
                    label = { Text(stringResource(R.string.diet_dish_kcal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (kcal != null) onSave(title, kcal) }, enabled = canSave) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Which days get this day's dishes, replacing their own. */
@Composable
private fun DietCopyDayDialog(
    from: DayOfWeek,
    week: List<DayOfWeek>,
    onDismiss: () -> Unit,
    onCopy: (Set<DayOfWeek>) -> Unit,
) {
    val locale = appLocale()
    // ISO numbers joined into one string: survives turning the phone as is.
    var pickedText by rememberSaveable { mutableStateOf("") }
    val picked = pickedText.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()
    fun toggle(d: DayOfWeek) {
        val next = if (d in picked) picked - d else picked + d
        pickedText = next.map { it.value }.sorted().joinToString(",")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diet_copy_day)) },
        text = {
            Column {
                Text(stringResource(R.string.diet_copy_day_hint), style = MaterialTheme.typography.bodyMedium)
                week.filter { it != from }.forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggle(d) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = d in picked, onCheckedChange = { toggle(d) })
                        Text(d.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(picked) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
