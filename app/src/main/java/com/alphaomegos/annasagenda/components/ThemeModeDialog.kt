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
import com.alphaomegos.annasagenda.AppThemeMode
import com.alphaomegos.annasagenda.R

private fun labelResFor(mode: AppThemeMode): Int = when (mode) {
    AppThemeMode.SYSTEM -> R.string.theme_mode_system
    AppThemeMode.LIGHT -> R.string.theme_mode_light
    AppThemeMode.DARK -> R.string.theme_mode_dark
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_mode_title)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
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
                        Text(stringResource(labelResFor(mode)))
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
