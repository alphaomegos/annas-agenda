package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The new look's frame (05.11), for a screen that comes to it from an app bar:
 * Back, the title fitted to the room, the screen's own keys and home in one
 * row, between the system bars; under it what the screen shows. The counters,
 * the settings and the library have the same row written out.
 */
@Composable
internal fun NewLookScreen(
    title: String,
    onBack: () -> Unit,
    home: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        NewLookTopRow(title = title, onBack = onBack, home = home, actions = actions)
        content()
    }
}

/**
 * The frame's row alone (05.11): Back, the title fitted to the room, the
 * screen's keys, home — for a bar that stands in a Scaffold's top, the
 * metro's and the music's.
 */
@Composable
internal fun NewLookTopRow(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    home: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarBack(onBack)
        val typography = MaterialTheme.typography
        FittingText(
            choices = listOf(title to typography.headlineSmall, title to typography.titleLarge, title to typography.titleMedium, title to typography.titleSmall),
            modifier = Modifier.weight(1f),
        )
        actions()
        if (home) WayHomeButton()
    }
}
