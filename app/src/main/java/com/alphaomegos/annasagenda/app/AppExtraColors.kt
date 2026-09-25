package com.alphaomegos.annasagenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours the app names for itself, beyond the Material scheme.
 *
 * Material3 gives a scheme for surfaces, text and the primary colour, and
 * everything the app draws on top of that was written as a literal in the
 * screen that drew it: a pale green behind the selected filter, a pale blue
 * behind a bonus run, a dark green for a figure that is where it should be.
 * All of them were picked to sit on white. On the dark scheme the pale ones
 * turn into bright slabs and the dark green becomes unreadable.
 *
 * Naming them by what they mean rather than by what they look like is what
 * makes a second set possible at all: "positive" has an answer on a dark
 * background, "that green" does not.
 *
 * The line colours on the anthropometry chart are deliberately not here. Those
 * eight hues identify which measurement a line is, the way a key does, and
 * they read on both backgrounds. Changing them per scheme would change what
 * the chart means rather than how it looks.
 */
data class AppExtraColors(
    /** A number that is on the right side of its goal. Never the only signal. */
    val positive: Color,
    /** The wash behind something selected or just acted on. */
    val gentleHighlight: Color,
    /** The wash behind a bonus run, kept distinct from the ordinary one. */
    val bonusHighlight: Color,
    /** The ring on a calendar day that has measurements behind it. */
    val dayMarker: Color,
    val chartGrid: Color,
    val chartLabel: Color,
)

private val LightExtraColors = AppExtraColors(
    positive = Color(0xFF2E7D32),
    gentleHighlight = Color(0xFFDDF4D8),
    bonusHighlight = Color(0xFFE3F2FD),
    dayMarker = Color(0xFFB7E6B0),
    chartGrid = Color.Black.copy(alpha = 0.08f),
    chartLabel = Color.Black.copy(alpha = 0.70f),
)

/**
 * The same meanings on a dark surface: the greens lightened until they read
 * against it, the washes darkened until they are a wash again rather than a
 * slab, the chart drawn in white at the same weights.
 */
private val DarkExtraColors = AppExtraColors(
    positive = Color(0xFF81C784),
    gentleHighlight = Color(0xFF1E3B22),
    bonusHighlight = Color(0xFF16293A),
    dayMarker = Color(0xFF66BB6A),
    chartGrid = Color.White.copy(alpha = 0.14f),
    chartLabel = Color.White.copy(alpha = 0.70f),
)

internal fun extraColorsFor(dark: Boolean): AppExtraColors =
    if (dark) DarkExtraColors else LightExtraColors

/**
 * Static rather than dynamic: the whole set changes together, when the theme
 * does, so there is nothing to gain from recomposing readers individually.
 */
val LocalAppExtraColors = staticCompositionLocalOf { LightExtraColors }

/** Reads like MaterialTheme.colorScheme, and is meant to. */
val appExtraColors: AppExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppExtraColors.current
