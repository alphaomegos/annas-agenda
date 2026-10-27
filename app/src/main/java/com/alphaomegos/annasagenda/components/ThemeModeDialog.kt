package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.model.AppPalette
import com.alphaomegos.annasagenda.model.BackStyle
import com.alphaomegos.annasagenda.model.DayScreenStyle
import com.alphaomegos.annasagenda.model.MainMenuNarrowStyle
import com.alphaomegos.annasagenda.model.AppThemeMode
import com.alphaomegos.annasagenda.R

internal fun themeModeLabelRes(mode: AppThemeMode): Int = when (mode) {
    AppThemeMode.SYSTEM -> R.string.theme_mode_system
    AppThemeMode.LIGHT -> R.string.theme_mode_light
    AppThemeMode.DARK -> R.string.theme_mode_dark
}

internal fun dayScreenStyleLabelRes(style: DayScreenStyle): Int = when (style) {
    DayScreenStyle.NEW -> R.string.day_screen_new
    DayScreenStyle.SUMMER_2025 -> R.string.day_screen_summer_2025
}

internal fun mainMenuNarrowLabelRes(style: MainMenuNarrowStyle): Int = when (style) {
    MainMenuNarrowStyle.LIST -> R.string.main_menu_narrow_list
    MainMenuNarrowStyle.ICONS -> R.string.main_menu_narrow_icons
}

internal fun backStyleLabelRes(style: BackStyle): Int = when (style) {
    BackStyle.ARROW -> R.string.back_style_arrow
    BackStyle.WORD -> R.string.back_style_word
}

internal fun paletteLabelRes(palette: AppPalette): Int = when (palette) {
    AppPalette.CLASSIC -> R.string.palette_classic
    AppPalette.GREEN -> R.string.palette_green
    AppPalette.WALLPAPER -> R.string.palette_wallpaper
}

/**
 * Three mutually exclusive choices, so radio buttons rather than a switch.
 *
 * A switch would have made "follow the phone" either invisible or a second
 * control, and following the phone is the one most people want and the one the
 * app has always silently claimed to do.
 *
 * Choosing applies at once and closes: there is nothing to confirm, the result
 * is immediately visible behind the dialog, and picking again undoes it.
 */
@Composable
fun ThemeModeDialog(
    current: AppThemeMode,
    onPick: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit,
) = OneOfDialog(R.string.theme_mode_title, AppThemeMode.entries, current, ::themeModeLabelRes, onPick, onDismiss)

/**
 * The colours (19.10), the same kind of choice as light or dark and made the
 * same way: it shows at once behind the dialog, and picking again undoes it.
 */
@Composable
fun PaletteDialog(
    current: AppPalette,
    onPick: (AppPalette) -> Unit,
    onDismiss: () -> Unit,
) = OneOfDialog(R.string.palette_title, AppPalette.entries, current, ::paletteLabelRes, onPick, onDismiss)

/** Back in the top bars: the arrow, or the word (19.10). */
@Composable
fun BackStyleDialog(
    current: BackStyle,
    onPick: (BackStyle) -> Unit,
    onDismiss: () -> Unit,
) = OneOfDialog(R.string.back_style_title, BackStyle.entries, current, ::backStyleLabelRes, onPick, onDismiss)

/** The day screen's look: «New», or «Summer 2025» as it was (27.10). */
@Composable
fun DayScreenStyleDialog(
    current: DayScreenStyle,
    onPick: (DayScreenStyle) -> Unit,
    onDismiss: () -> Unit,
) = OneOfDialog(R.string.day_screen_menu, DayScreenStyle.entries, current, ::dayScreenStyleLabelRes, onPick, onDismiss)

/** The main menu on a narrow screen: the list, or «mini buttons» (25.10). */
@Composable
fun MainMenuNarrowDialog(
    current: MainMenuNarrowStyle,
    onPick: (MainMenuNarrowStyle) -> Unit,
    onDismiss: () -> Unit,
) = OneOfDialog(R.string.main_menu_narrow_menu, MainMenuNarrowStyle.entries, current, ::mainMenuNarrowLabelRes, onPick, onDismiss)

@Composable
private fun <T> OneOfDialog(
    titleRes: Int,
    choices: List<T>,
    current: T,
    labelRes: (T) -> Int,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                choices.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onPick(mode)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The row carries the click, so the button itself must
                        // not: two targets for one choice reads as two controls
                        // to anything stepping through them.
                        RadioButton(selected = mode == current, onClick = null)
                        Text(stringResource(labelRes(mode)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
