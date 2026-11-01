package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.FoodEntry
import com.alphaomegos.annasagenda.support.DayEnergy
import com.alphaomegos.annasagenda.support.DietDishOnDay
import com.alphaomegos.annasagenda.support.monthAfterDay
import com.alphaomegos.annasagenda.util.formatTwoDecimals
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** What the calorimeter's new look shows for its day (01.11): worked out by the screen, drawn here. */
internal class CalorimeterDayView(
    val date: LocalDate,
    val isToday: Boolean,
    val goalKcal: Int,
    val showGoal: Boolean,
    val eatenKcal: Int,
    val eaten: List<FoodEntry>,
    val hasRecords: Boolean,
    val energy: DayEnergy?,
    val profile: String?,
    val dietDishes: List<DietDishOnDay>,
    val showWeek: Boolean,
    val weekGoalKcal: Int,
    val weekBalanceKcal: Int,
    val showMonth: Boolean,
    val monthKg: Double,
)

/**
 * The calorimeter in the new look (01.11), in the day screen's language: the date big,
 * one light row of ‹ Today › and «+ Eaten»; then everything on grounded surfaces with
 * hairlines between the rows, no floating cards. The day's summary carries the goal, what
 * was eaten and the balance with a bar, and — new — the day's spending as the weight
 * forecast counts it (the norm, the steps, the runs) with the profile it comes from, and
 * a word when the day has no records (the forecast leaves such days out).
 */
@Composable
internal fun CalorimeterNewDay(
    view: CalorimeterDayView,
    locale: Locale,
    positive: Color,
    onPrev: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    onEditGoal: () -> Unit,
    onDelete: (Long) -> Unit,
    onProfile: () -> Unit,
    dietCard: @Composable () -> Unit,
) {
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val kcal = stringResource(R.string.kcal_short)
    fun k(v: Int) = numbers.format(v) + " " + kcal
    val negative = MaterialTheme.colorScheme.error
    val month = monthAfterDay(stringResource(R.string.months_after_day), view.date)
    val weekday = remember(view.date, locale) { view.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.day_screen_title, view.date.dayOfMonth, month),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.day_screen_subtitle, weekday, view.date.dayOfMonth, month, view.date.year),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_day))
        }
        TextButton(onClick = onToday, enabled = !view.isToday, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(stringResource(R.string.today))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_day))
        }
        Spacer(modifier = Modifier.weight(1f))
        FilledTonalButton(
            onClick = onAdd,
            contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.calorimeter_eaten_button))
        }
    }

    if (view.dietDishes.isNotEmpty()) dietCard()

    // The day: goal, eaten, balance and its bar; the spending; a word on an empty day.
    GroundedSurface {
        if (view.showGoal) {
            SummaryRow(stringResource(R.string.calorimeter_daily_goal), k(view.goalKcal)) {
                if (view.isToday) {
                    IconButton(onClick = onEditGoal) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.calorimeter_set_goal))
                    }
                }
            }
            Hairline()
        }
        SummaryRow(stringResource(R.string.calorimeter_eaten_button), k(view.eatenKcal))
        Hairline()
        val balance = view.goalKcal - view.eatenKcal
        SummaryRow(
            stringResource(R.string.calorimeter_day_balance),
            (if (balance > 0) "+" else "") + k(balance),
            valueColor = if (balance >= 0) positive else negative,
        )
        if (view.goalKcal > 0) {
            LinearProgressIndicator(
                progress = { (view.eatenKcal.toFloat() / view.goalKcal).coerceIn(0f, 1f) },
                color = if (balance >= 0) MaterialTheme.colorScheme.primary else negative,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        Hairline()
        val energy = view.energy
        if (energy != null) {
            SummaryRow(stringResource(R.string.calorimeter_spent), k(energy.totalKcal))
            Text(
                stringResource(
                    if (energy.ownNorm) R.string.calorimeter_spent_parts_own else R.string.calorimeter_spent_parts,
                    numbers.format(energy.normKcal), numbers.format(energy.stepsKcal), numbers.format(energy.runKcal),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        } else {
            Text(
                stringResource(R.string.calorimeter_spent_needs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                view.profile ?: stringResource(R.string.profile_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onProfile) { Text(stringResource(R.string.profile_title)) }
        }
        if (!view.hasRecords) {
            Hairline()
            Text(
                stringResource(R.string.calorimeter_no_records),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    // What was eaten, every entry, one surface.
    if (view.eaten.isNotEmpty()) {
        GroundedSurface {
            view.eaten.forEachIndexed { i, e ->
                if (i > 0) Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    Text(k(e.kcal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onDelete(e.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                    }
                }
            }
        }
    }

    // Today only: the week and the thirty days.
    if (view.isToday && (view.showWeek || view.showMonth)) {
        GroundedSurface {
            if (view.showWeek) {
                SummaryRow(stringResource(R.string.calorimeter_week_goal), k(view.weekGoalKcal))
                Hairline()
                SummaryRow(
                    stringResource(R.string.calorimeter_week_balance),
                    (if (view.weekBalanceKcal > 0) "+" else "") + k(view.weekBalanceKcal),
                    valueColor = if (view.weekBalanceKcal >= 0) positive else negative,
                )
            }
            if (view.showWeek && view.showMonth) Hairline()
            if (view.showMonth) {
                SummaryRow(
                    stringResource(R.string.calorimeter_month_projection),
                    formatTwoDecimals(view.monthKg) + " " + stringResource(R.string.kg_short),
                    valueColor = if (view.monthKg >= 0.0) positive else negative,
                )
            }
        }
    }
}

// One surface for rows that belong together, laid on the screen (the day screen's list).
@Composable
private fun GroundedSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.Top) { content() }
    }
}

@Composable
private fun Hairline() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}
