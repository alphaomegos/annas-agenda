package com.alphaomegos.annasagenda.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The «Green» palette (AppPalette.GREEN, 19.10): every role of Material 3's
 * colour scheme grown from the app's own green, 0xFF2E7D32.
 *
 * Generated, not picked: Material Color Utilities (github.com/
 * material-foundation/material-color-utilities, Java), SchemeTonalSpot — the
 * scheme Android itself builds from a wallpaper — from that one seed, contrast
 * 0.0, light and dark. To change the green, regenerate all of them together;
 * a role edited by hand is a role that no longer belongs to the family.
 *
 * The «fixed» roles are left out: this Compose (material3 1.3) has no
 * parameters for them.
 */

internal val GreenLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF3C6939),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCF0B4),
    onPrimaryContainer = Color(0xFF245024),
    inversePrimary = Color(0xFFA1D39A),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E8CE),
    onSecondaryContainer = Color(0xFF3B4B38),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF1F4D52),
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceTint = Color(0xFF3C6939),
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEFF2E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FBF1),
    surfaceContainer = Color(0xFFECEFE6),
    surfaceContainerHigh = Color(0xFFE6E9E0),
    surfaceContainerHighest = Color(0xFFE0E4DB),
    surfaceContainerLow = Color(0xFFF1F5EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8DBD2),
)

internal val GreenDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA1D39A),
    onPrimary = Color(0xFF0A390F),
    primaryContainer = Color(0xFF245024),
    onPrimaryContainer = Color(0xFFBCF0B4),
    inversePrimary = Color(0xFF3C6939),
    secondary = Color(0xFFBACCB3),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD6E8CE),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceTint = Color(0xFFA1D39A),
    inverseSurface = Color(0xFFE0E4DB),
    inverseOnSurface = Color(0xFF2D322C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A34),
    surfaceContainer = Color(0xFF1D211B),
    surfaceContainerHigh = Color(0xFF272B25),
    surfaceContainerHighest = Color(0xFF323630),
    surfaceContainerLow = Color(0xFF191D17),
    surfaceContainerLowest = Color(0xFF0B0F0A),
    surfaceDim = Color(0xFF10140F),
)
