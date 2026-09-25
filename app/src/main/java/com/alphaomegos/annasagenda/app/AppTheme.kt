package com.alphaomegos.annasagenda

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF43A047),
    onSecondary = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF0B1B0E),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF0B1B0E),
)

/**
 * The dark scheme above has existed since the app did. Nothing ever selected
 * it: [AnnaAgendaTheme] took a Boolean that defaulted to false and no caller
 * ever passed anything else, so a phone in night mode still got the light one.
 *
 * The choice now comes from the saved state, so it survives a restart and is
 * the user's rather than the phone's.
 */
@Composable
fun AnnaAgendaTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
