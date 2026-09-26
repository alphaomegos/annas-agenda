package com.alphaomegos.annasagenda

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

    CompositionLocalProvider(
        LocalAppExtraColors provides extraColorsFor(dark),
        LocalAppDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content
        )
    }
}

/**
 * Whether the app is currently drawing itself dark.
 *
 * Almost everything follows from the colour scheme and needs no such flag.
 * The exception is a picture that cannot be recoloured — the three Undone
 * lamps are painted illustrations, not silhouettes — where a second drawing
 * has to be picked instead.
 *
 * This is the app's answer, not the phone's. The two differ whenever the user
 * sets a theme explicitly, which is the whole reason the setting exists, and
 * it is why a `-night` resource folder would be the wrong mechanism here.
 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/** Reads like MaterialTheme.colorScheme, and is meant to. */
val appIsDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAppDarkTheme.current
