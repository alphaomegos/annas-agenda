package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R

/*
 * The way home (17.10, Eduard): from Travel, Flights, the profile and its
 * achievements the main menu was four Backs away.
 *
 * Android's own rule is that Back and Up retrace the way one screen at a
 * time down to the start, and that stays: the system's gesture and each
 * screen's own Back. What the hub-and-spoke apps add, this one included —
 * every section a spoke off the main menu — is that the hub is one tap
 * from anywhere: a house beside Back, on a screen two or more steps from
 * the menu. Not on a screen one step from it, where it would only be Back
 * again; and not on a form (a new task, a flight being edited), where
 * leaving loses what was typed, and which has Cancel.
 */

/** Back to the main menu at once, for the screen it is given to; null where the house is not shown. */
val LocalWayHome = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The house in a top bar, beside Back; nothing where there is no way home to give. */
@Composable
fun WayHomeButton() {
    val home = LocalWayHome.current ?: return
    IconButton(onClick = home) {
        Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.to_main_menu))
    }
}

/** The house beside a screen's own full-width Back at its foot. */
@Composable
fun RowScope.WayHomeFootButton() {
    val home = LocalWayHome.current ?: return
    FilledTonalIconButton(onClick = home, modifier = Modifier.padding(start = 8.dp)) {
        Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.to_main_menu))
    }
}
