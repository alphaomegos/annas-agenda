package com.alphaomegos.annasagenda.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.support.UNDONE_HORIZON_CHOICES
import com.alphaomegos.annasagenda.components.NotificationSettingsDialog
import com.alphaomegos.annasagenda.components.ThemeModeDialog
import com.alphaomegos.annasagenda.components.WidgetStyleDialog
import com.alphaomegos.annasagenda.components.themeModeLabelRes

/**
 * Every setting in one place (04.10).
 *
 * Each row opens the very dialog the setting already had where it lives —
 * the calorimeter's gear, the calendar's, the Undone screen's period — so
 * there is one dialog per setting and two ways to it, not two dialogs that
 * could disagree. The gears on the screens stay as shortcuts.
 *
 * Which dialog is open is kept by name, so turning the phone keeps it open.
 * Export, import and reset stay in the main menu's "⋮": they are actions on
 * the data, not settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    onOpenDiet: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val askNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            item { SettingsSection(stringResource(R.string.settings_section_general)) }
            item { SettingsRow(stringResource(R.string.choose_language), null, onLanguage) }
            item {
                SettingsRow(
                    stringResource(R.string.theme_mode_menu),
                    stringResource(themeModeLabelRes(state.themeMode)),
                ) { open = SETTINGS_THEME }
            }
            item {
                SettingsRow(
                    stringResource(R.string.notif_settings_menu),
                    stringResource(
                        if (state.notifications.summaryMinutes.isEmpty() &&
                            state.notifications.reminderLeadMinutes == null
                        ) R.string.settings_off else R.string.settings_on
                    ),
                ) { open = SETTINGS_NOTIFICATIONS }
            }
            item {
                SettingsRow(
                    stringResource(R.string.widget_style_menu),
                    stringResource(R.string.widget_style_background, state.widgetStyle.backgroundPercent),
                ) { open = SETTINGS_WIDGET }
            }

            item { SettingsSection(stringResource(R.string.settings_section_screens)) }
            item {
                SettingsRow(
                    stringResource(R.string.calendar_title),
                    stringResource(R.string.calendar_badges_title),
                ) { open = SETTINGS_CALENDAR }
            }
            item {
                SettingsRow(
                    stringResource(R.string.calorimeter_title),
                    stringResource(R.string.settings_calorimeter_hint),
                ) { open = SETTINGS_CALORIMETER }
            }
            item {
                SettingsRow(
                    stringResource(R.string.anthropometry_title),
                    stringResource(R.string.settings_anthropometry_hint),
                ) { open = SETTINGS_ANTHROPOMETRY }
            }
            item {
                SettingsRow(
                    stringResource(R.string.undone_title),
                    undoneHorizonLabel(state.undoneHorizonDays),
                ) { open = SETTINGS_UNDONE }
            }
        }
    }

    val close = { open = null }
    when (open) {
        SETTINGS_THEME -> ThemeModeDialog(
            current = state.themeMode,
            onPick = vm::setThemeMode,
            onDismiss = close,
        )

        SETTINGS_NOTIFICATIONS -> NotificationSettingsDialog(
            initial = state.notifications,
            onDismiss = close,
            onSave = {
                vm.setNotificationSettings(it)
                close()
            },
            onNeedPermission = {
                if (Build.VERSION.SDK_INT >= 33) {
                    askNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        SETTINGS_WIDGET -> WidgetStyleDialog(
            initial = state.widgetStyle,
            onDismiss = close,
            onSave = {
                vm.setWidgetStyle(it)
                close()
            },
        )

        SETTINGS_CALENDAR -> CalendarBadgesDialog(
            initial = state.calendarBadges,
            onDismiss = close,
            onSave = {
                vm.setCalendarBadges(it)
                close()
            },
        )

        SETTINGS_CALORIMETER -> CalorimeterDisplayDialog(
            dailyGoal = state.calorimeterShowDailyGoal,
            weeklyGoal = state.calorimeterShowWeeklyGoal,
            potentialLoss = state.calorimeterShowPotentialLoss,
            diet = state.dietEnabled,
            dietPastUnticked = state.dietShowPastUnticked,
            library = state.foodLibraryVisible,
            onDismiss = close,
            onSave = { daily, weekly, potential, diet, past, library ->
                vm.setCalorimeterDisplay(daily, weekly, potential)
                vm.setDietSettings(diet, past)
                vm.setFoodLibraryVisible(library)
                close()
            },
            onOpenDiet = {
                close()
                onOpenDiet()
            },
        )

        SETTINGS_ANTHROPOMETRY -> AnthropometryFieldsDialog(
            fieldDefs = anthropometryFieldDefs,
            enabledFieldIds = state.anthropometryEnabledFieldIds,
            showForecast = state.anthropometryShowForecast,
            showEntries = state.anthropometryShowEntries,
            onDismiss = close,
            onSave = { enabledIds, forecast, entries ->
                vm.setAnthropometryEnabledFieldIds(enabledIds)
                vm.setAnthropometryDisplay(showForecast = forecast, showEntries = entries)
                close()
            },
        )

        SETTINGS_UNDONE -> UndoneHorizonDialog(
            current = state.undoneHorizonDays,
            onPick = {
                vm.setUndoneHorizonDays(it)
                close()
            },
            onDismiss = close,
        )
    }
}

private const val SETTINGS_THEME = "theme"
private const val SETTINGS_NOTIFICATIONS = "notifications"
private const val SETTINGS_WIDGET = "widget"
private const val SETTINGS_CALENDAR = "calendar"
private const val SETTINGS_CALORIMETER = "calorimeter"
private const val SETTINGS_ANTHROPOMETRY = "anthropometry"
private const val SETTINGS_UNDONE = "undone"

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** A setting: what it is, and underneath what it is set to now. */
@Composable
private fun SettingsRow(title: String, summary: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/** The Undone screen's period, as radio buttons: the same choices its menu offers. */
@Composable
private fun UndoneHorizonDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.undone_horizon_menu)) },
        text = {
            Column(Modifier.selectableGroup()) {
                UNDONE_HORIZON_CHOICES.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(days) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == current, onClick = { onPick(days) })
                        Text(undoneHorizonLabel(days))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
