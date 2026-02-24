package com.alphaomegos.annasagenda.screens

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppState
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.CalorieGoalChange
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.appLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val DEFAULT_DAILY_GOAL_KCAL = 2000
private const val KCAL_PER_KG_FAT = 7800.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorimeterRoute(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()

    CalorimeterContent(
        state = state,
        onBack = onBack,
        onSetGoalFromToday = { kcal ->
            vm.setDailyCalorieGoalFrom(LocalDate.now(), kcal)
        },
        onAddFood = { date, title, kcal ->
            vm.addFoodEntry(date, title, kcal)
        },
        onDeleteFood = { id ->
            vm.deleteFoodEntry(id)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalorimeterContent(
    state: AppState,
    onBack: () -> Unit,
    onSetGoalFromToday: (Int) -> Unit,
    onAddFood: (LocalDate, String, Int) -> Long,
    onDeleteFood: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val locale = appLocale()

    val today = LocalDate.now()
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }
    val isToday = selectedDate == today

    val dateText = remember(selectedDate, locale) {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        selectedDate.format(fmt)
    }

    val goalSelected = goalFor(selectedDate, state.calorieGoalChanges)

    val eatenSelected = remember(state.foodLog, selectedDate) {
        state.foodLog
            .filter { it.date == selectedDate }
            .sortedBy { it.id }
    }

    val eatenSelectedSum = eatenSelected.sumOf { it.kcal }
    val dayBalance = goalSelected - eatenSelectedSum

    // Only for today:
    val weekGoal = goalSelected * 7
    val start7 = today.minusDays(6)
    val eaten7 = remember(state.foodLog, today) {
        state.foodLog
            .filter { !it.date.isBefore(start7) && !it.date.isAfter(today) }
            .sumOf { it.kcal }
    }
    val weekBalance = weekGoal - eaten7

    val start30 = today.minusDays(29)
    val deficit30 = if (isToday) sumDeficitInRange(state, start30, today) else 0
    val potentialKg = if (isToday) deficit30 / KCAL_PER_KG_FAT else 0.0

    val okGreen = Color(0xFF2E7D32)
    val badRed = MaterialTheme.colorScheme.error
    fun balanceColor(v: Int): Color = if (v >= 0) okGreen else badRed
    fun kgColor(v: Double): Color = if (v >= 0.0) okGreen else badRed

    // Sound (optional): res/raw/snd_munch_01.ogg
    val munchResId = R.raw.snd_munch_01

    val audioAttrs = remember {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttrs)
            .build()
    }
    val munchSoundId = remember(munchResId) {
        soundPool.load(ctx, munchResId, 1)
    }
    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }
    fun playMunch() {
        if (munchSoundId != 0) soundPool.play(munchSoundId, 1f, 1f, 1, 0, 1f)
    }

    val showGoalDialog = rememberSaveable { mutableStateOf(false) }
    var goalInput by rememberSaveable(goalSelected) { mutableStateOf(goalSelected.toString()) }

    val showAllEatenDialog = rememberSaveable { mutableStateOf(false) }
    val showAddDialog = rememberSaveable { mutableStateOf(false) }
    var foodName by rememberSaveable { mutableStateOf("") }
    var foodKcal by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.calorimeter_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date row with arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedEpochDay -= 1 }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Prev day"
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(onClick = { selectedEpochDay += 1 }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day"
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.calorimeter_daily_goal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$goalSelected ${stringResource(R.string.kcal_short)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isToday) {
                            IconButton(onClick = {
                                goalInput = goalSelected.toString()
                                showGoalDialog.value = true
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.calorimeter_set_goal)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.calorimeter_day_balance)}: $dayBalance ${stringResource(R.string.kcal_short)}",
                            color = balanceColor(dayBalance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val previewLimit = 4

                    if (eatenSelected.isEmpty()) {
                        Text(
                            text = stringResource(R.string.calorimeter_no_eaten_today),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        // Show only first 4 items directly (no hidden scrolling)
                        eatenSelected.take(previewLimit).forEach { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = e.title, modifier = Modifier.weight(1f))
                                Text("${e.kcal} ${stringResource(R.string.kcal_short)}")
                                IconButton(onClick = { onDeleteFood(e.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.remove)
                                    )
                                }
                            }
                        }

                        if (eatenSelected.size > previewLimit) {
                            TextButton(
                                onClick = { showAllEatenDialog.value = true },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(stringResource(R.string.calorimeter_show_all_n, eatenSelected.size))
                            }
                        }
                    }
                }
            }

            // Extra sections ONLY for today
            if (isToday) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.calorimeter_week_goal) +
                                    ": $weekGoal ${stringResource(R.string.kcal_short)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.calorimeter_week_balance) +
                                    ": $weekBalance ${stringResource(R.string.kcal_short)}",
                            color = balanceColor(weekBalance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val kgText = String.format(Locale.US, "%.2f", potentialKg)
                        Text(
                            text = stringResource(R.string.calorimeter_month_projection) +
                                    ": $kgText ${stringResource(R.string.kg_short)}",
                            color = kgColor(potentialKg),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        foodName = ""
                        foodKcal = ""
                        showAddDialog.value = true
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(96.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.calorimeter_eaten_button),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    // Dialogs must be conditional; otherwise "showXDialog = false" looks like a write-only variable.
    if (showAllEatenDialog.value) {
        AlertDialog(
            onDismissRequest = { showAllEatenDialog.value = false },
            title = { Text("${stringResource(R.string.calorimeter_eaten_button)} • $dateText") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(eatenSelected, key = { it.id }) { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = e.title, modifier = Modifier.weight(1f))
                            Text("${e.kcal} ${stringResource(R.string.kcal_short)}")
                            IconButton(onClick = { onDeleteFood(e.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.remove)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllEatenDialog.value = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showGoalDialog.value) {
        AlertDialog(
            onDismissRequest = { showGoalDialog.value = false },
            title = { Text(stringResource(R.string.calorimeter_set_goal)) },
            text = {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text(stringResource(R.string.calorimeter_daily_goal)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newGoal = goalInput.trim().toIntOrNull()
                    if (newGoal != null && newGoal > 0) onSetGoalFromToday(newGoal)
                    showGoalDialog.value = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddDialog.value) {
        AlertDialog(
            onDismissRequest = { showAddDialog.value = false },
            title = { Text(stringResource(R.string.calorimeter_add_eaten_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text(stringResource(R.string.calorimeter_food_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = foodKcal,
                        onValueChange = { foodKcal = it },
                        label = {
                            Text(
                                stringResource(R.string.calorimeter_food_kcal) +
                                        " (${stringResource(R.string.kcal_short)})"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val kcal = foodKcal.trim().toIntOrNull()
                    if (kcal != null && kcal > 0) {
                        val id = onAddFood(selectedDate, foodName, kcal)
                        if (id != -1L) playMunch()
                    }
                    showAddDialog.value = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun goalFor(date: LocalDate, changes: List<CalorieGoalChange>): Int {
    val last = changes
        .filter { !it.date.isAfter(date) }
        .maxByOrNull { it.date }
    return last?.kcal ?: DEFAULT_DAILY_GOAL_KCAL
}

private fun sumDeficitInRange(state: AppState, start: LocalDate, end: LocalDate): Int {
    var d = start
    var total = 0
    while (!d.isAfter(end)) {
        val g = goalFor(d, state.calorieGoalChanges)
        val eaten = state.foodLog.filter { it.date == d }.sumOf { it.kcal }
        total += (g - eaten)
        d = d.plusDays(1)
    }
    return total
}
