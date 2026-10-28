package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.components.ProfileDialog
import com.alphaomegos.annasagenda.components.TopBarBack
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
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
import com.alphaomegos.annasagenda.screens.metro.MetroTextDialog
import com.alphaomegos.annasagenda.support.MetroSelection
import com.alphaomegos.annasagenda.support.UNDONE_HORIZON_CHOICES
import com.alphaomegos.annasagenda.support.metroShown
import com.alphaomegos.annasagenda.components.NotificationSettingsDialog
import com.alphaomegos.annasagenda.components.BackStyleDialog
import com.alphaomegos.annasagenda.components.MainMenuNarrowDialog
import com.alphaomegos.annasagenda.components.DayScreenStyleDialog
import com.alphaomegos.annasagenda.components.dayScreenStyleLabelRes
import com.alphaomegos.annasagenda.components.mainMenuNarrowLabelRes
import com.alphaomegos.annasagenda.components.PaletteDialog
import com.alphaomegos.annasagenda.components.backStyleLabelRes
import com.alphaomegos.annasagenda.components.ThemeModeDialog
import com.alphaomegos.annasagenda.components.paletteLabelRes
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
    val together by com.alphaomegos.annasagenda.app.StepsTogether.of(androidx.compose.ui.platform.LocalContext.current).collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val askNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TopBarBack(onBack)
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // Three groups, by what a setting is about (19.10): how the app
            // looks, how tasks behave, and each section's own. A choice
            // between several opens its dialog and shows what it is now; one
            // that is only on or off is a switch, and the row is the switch.
            // The profile (28.10): one set for the whole app — steps, the forecast.
            item {
                SettingsRow(
                    stringResource(R.string.profile_title),
                    profileSummary(state.profileSex, state.stepsHeightCm, state.profileBirthYear),
                ) { open = SETTINGS_PROFILE }
            }
            item { SettingsSection(stringResource(R.string.settings_section_appearance)) }
            item { SettingsRow(stringResource(R.string.choose_language), null, onLanguage) }
            item {
                SettingsRow(
                    stringResource(R.string.theme_mode_menu),
                    stringResource(themeModeLabelRes(state.themeMode)),
                ) { open = SETTINGS_THEME }
            }
            item {
                SettingsRow(
                    stringResource(R.string.palette_menu),
                    stringResource(paletteLabelRes(state.palette)),
                ) { open = SETTINGS_PALETTE }
            }
            item {
                SettingsRow(
                    stringResource(R.string.back_style_menu),
                    stringResource(backStyleLabelRes(state.backStyle)),
                ) { open = SETTINGS_BACK }
            }
            item {
                SettingsRow(
                    stringResource(R.string.main_menu_narrow_menu),
                    stringResource(mainMenuNarrowLabelRes(state.mainMenuNarrow)),
                ) { open = SETTINGS_MENU_NARROW }
            }
            item {
                SettingsRow(
                    stringResource(R.string.day_screen_menu),
                    stringResource(dayScreenStyleLabelRes(state.dayScreenStyle)),
                ) { open = SETTINGS_DAY_SCREEN }
            }
            item {
                SettingsRow(
                    stringResource(R.string.widget_style_menu),
                    stringResource(R.string.widget_style_background, state.widgetStyle.backgroundPercent),
                ) { open = SETTINGS_WIDGET }
            }
            item {
                SettingsRow(stringResource(R.string.steps_widget_menu), null) { open = SETTINGS_STEPS_WIDGET }
            }

            item { SettingsSection(stringResource(R.string.settings_section_tasks)) }
            // Done tasks at the bottom of their day (06.10), done subtasks at
            // the bottom of their task (16.10): each a setting of its own.
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_done_tasks_last),
                    checked = state.doneTasksLast,
                    onChange = vm::setDoneTasksLast,
                )
            }
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_done_subtasks_last),
                    checked = state.doneSubtasksLast,
                    onChange = vm::setDoneSubtasksLast,
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.undone_title),
                    undoneHorizonLabel(state.undoneHorizonDays),
                ) { open = SETTINGS_UNDONE }
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

            item { SettingsSection(stringResource(R.string.settings_section_screens)) }
            // Steps together (25.10): sharing steps with friends through oursidecar.
            item {
                val sharing = together.sharing
                SettingsRow(
                    stringResource(R.string.steps_together_menu),
                    when {
                        sharing == null -> null
                        sharing.enabled -> stringResource(R.string.steps_together_summary_on, together.friends.size)
                        else -> stringResource(R.string.steps_together_summary_off)
                    },
                ) { open = SETTINGS_STEPS_TOGETHER }
            }
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
            // The metro's city (04.10), and where another city's map is started (05.10).
            item {
                SettingsRow(
                    stringResource(R.string.metro_settings_scheme),
                    metroShown(state.metroSchemes, state.metroSelection, vm.metroLibrary)?.scheme?.city
                        ?: stringResource(R.string.metro_settings_none),
                ) { open = SETTINGS_METRO }
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

        SETTINGS_STEPS_TOGETHER -> StepsTogetherDialog(onDismiss = close)

        SETTINGS_PROFILE -> ProfileDialog(
            sex = state.profileSex,
            heightCm = state.stepsHeightCm,
            birthYear = state.profileBirthYear,
            onSave = { s, h, y ->
                vm.setProfile(s, h, y)
                close()
            },
            onDismiss = close,
        )

        SETTINGS_STEPS_WIDGET -> StepsWidgetDialog(
            line = state.stepsWidgetLine,
            showRecord = state.stepsWidgetRecord,
            goal = state.stepsGoal,
            onSave = { line, record, goal ->
                vm.setStepsWidget(line, record, goal)
                close()
            },
            onDismiss = close,
        )

        SETTINGS_DAY_SCREEN -> DayScreenStyleDialog(
            current = state.dayScreenStyle,
            onPick = vm::setDayScreenStyle,
            onDismiss = close,
        )

        SETTINGS_MENU_NARROW -> MainMenuNarrowDialog(
            current = state.mainMenuNarrow,
            onPick = vm::setMainMenuNarrow,
            onDismiss = close,
        )

        SETTINGS_BACK -> BackStyleDialog(
            current = state.backStyle,
            onPick = vm::setBackStyle,
            onDismiss = close,
        )

        SETTINGS_PALETTE -> PaletteDialog(
            current = state.palette,
            onPick = vm::setPalette,
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
            initialSteps = state.calendarShowSteps,
            onDismiss = close,
            onSave = { badges, steps ->
                vm.setCalendarBadges(badges)
                vm.setCalendarShowSteps(steps)
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

        SETTINGS_METRO -> {
            val shown = metroShown(state.metroSchemes, state.metroSelection, vm.metroLibrary)
            val librarySuffix = stringResource(R.string.metro_library_suffix)
            MetroSchemeDialog(
                // The user's own first, then the library's — those the user
                // has not already copied, since the copy is the one to use.
                choices = state.metroSchemes.map { MetroSelection.User(it.id) to it.city } +
                    vm.metroLibrary.entries
                        .filter { (key, _) -> state.metroSchemes.none { it.librarySource == key } }
                        .sortedBy { it.key }
                        .map { (key, scheme) -> MetroSelection.Library(key) to "${scheme.city} $librarySuffix" },
                current = shown?.let { if (it.libraryKey != null) MetroSelection.Library(it.libraryKey) else MetroSelection.User(it.scheme.id) },
                onPick = {
                    vm.setMetroSelection(it)
                    close()
                },
                onCreate = { open = SETTINGS_METRO_NEW },
                onDismiss = close,
            )
        }

        SETTINGS_METRO_NEW -> MetroTextDialog(
            title = stringResource(R.string.metro_create_scheme),
            label = stringResource(R.string.metro_city),
            initial = "",
            onDismiss = close,
            onSave = { city ->
                // Made and chosen at once; its lines are added from the Metro screen's pencil.
                if (vm.createMetroScheme(city) != null) close()
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
private const val SETTINGS_PALETTE = "palette"
private const val SETTINGS_BACK = "back"
private const val SETTINGS_MENU_NARROW = "menu-narrow"
private const val SETTINGS_DAY_SCREEN = "day-screen"
private const val SETTINGS_STEPS_TOGETHER = "steps-together"
private const val SETTINGS_PROFILE = "profile"
private const val SETTINGS_STEPS_WIDGET = "steps-widget"
private const val SETTINGS_NOTIFICATIONS = "notifications"
private const val SETTINGS_WIDGET = "widget"
private const val SETTINGS_CALENDAR = "calendar"
private const val SETTINGS_CALORIMETER = "calorimeter"
private const val SETTINGS_ANTHROPOMETRY = "anthropometry"
private const val SETTINGS_UNDONE = "undone"
private const val SETTINGS_METRO = "metro"
private const val SETTINGS_METRO_NEW = "metro_new"

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * A setting: what it is, and underneath what it is set to now.
 *
 * No line under each row any more (19.10): the section headings already say
 * where one group ends, and a rule under every row is a grid of lines to read
 * past. At least 56 dp tall, Material's one-line list item.
 */
@Composable
private fun SettingsRow(title: String, summary: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
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
}

/**
 * A setting that is only on or off (19.10): a switch, not a row that opens
 * nothing and says «On» underneath. The whole row is the switch — one target,
 * read by TalkBack as a switch with its state.
 */
@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
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

/** The metro schemes by city, the user's and the library's, as radio buttons. */
@Composable
private fun MetroSchemeDialog(
    choices: List<Pair<MetroSelection, String>>,
    current: MetroSelection?,
    onPick: (MetroSelection) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_settings_scheme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                choices.forEach { (choice, city) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(choice) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == current, onClick = { onPick(choice) })
                        Text(city)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        dismissButton = {
            TextButton(onClick = onCreate) { Text(stringResource(R.string.metro_create_scheme)) }
        },
    )
}

/** The profile row's summary: «Female · 168 cm · 1990», what is set of it; null when nothing is. */
@Composable
private fun profileSummary(sex: ProfileSex?, heightCm: Int?, birthYear: Int?): String? {
    val parts = listOfNotNull(
        sex?.let { stringResource(if (it == ProfileSex.FEMALE) R.string.profile_female else R.string.profile_male) },
        heightCm?.let { stringResource(R.string.profile_height_cm, it) },
        birthYear?.toString(),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
