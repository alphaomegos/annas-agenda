package com.alphaomegos.annasagenda.screens.metro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.model.MetroStyle

/**
 * The metro section after an old program (15.10, Eduard: "an extra version
 * of the theme in the style of old programs"), the user's pick:
 *
 * - [MetroStyle.WIN95] — grey windows, a navy title and highlight, square
 *   corners, a pixel font (Pixelify Sans, OFL);
 * - [MetroStyle.DOS] — blue panels, cyan letters, yellow for what can be
 *   pressed, as Norton Commander, in a monospace (IBM Plex Mono, OFL);
 * - [MetroStyle.TERMINAL] — green on black, as an old monitor, the same
 *   monospace.
 *
 * Today's look ([MetroStyle.MODERN]) is the rest of the app's and stays the
 * default. The style is its own, whatever the app's light or dark: Windows
 * was grey, a terminal black. The fonts' licences are in assets/licenses.
 */
val LocalMetroStyle = staticCompositionLocalOf { MetroStyle.MODERN }

/** The metro section's screens in the user's style. */
@Composable
fun MetroThemed(vm: AppViewModel, content: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    MetroThemed(state.metroPrefs.style, content)
}

/** [content] in [style]. */
@Composable
fun MetroThemed(style: MetroStyle, content: @Composable () -> Unit) {
    if (style == MetroStyle.MODERN) {
        CompositionLocalProvider(LocalMetroStyle provides style, content = content)
        return
    }
    MaterialTheme(
        colorScheme = metroStyleColors(style),
        typography = metroStyleTypography(style),
        shapes = SquareShapes,
    ) {
        CompositionLocalProvider(LocalMetroStyle provides style, content = content)
    }
}

/** The font of [style]; null for today's. */
fun metroStyleFont(style: MetroStyle): FontFamily? = when (style) {
    MetroStyle.MODERN -> null
    MetroStyle.WIN95 -> FontFamily(Font(R.font.pixelify_sans))
    MetroStyle.DOS, MetroStyle.TERMINAL -> FontFamily(Font(R.font.ibm_plex_mono))
}

private val SquareShapes = Shapes(
    extraSmall = RectangleShape,
    small = RectangleShape,
    medium = RectangleShape,
    large = RectangleShape,
    extraLarge = RectangleShape,
)

// Windows 95: the grey of its windows, the navy of a title and a highlight.
private val Win95Grey = Color(0xFFC0C0C0)
private val Win95Navy = Color(0xFF000080)

// Norton Commander's text mode: the CGA blue, cyan, yellow.
private val DosBlue = Color(0xFF0000AA)
private val DosCyan = Color(0xFF55FFFF)
private val DosDarkCyan = Color(0xFF00AAAA)
private val DosYellow = Color(0xFFFFFF55)
private val DosGrey = Color(0xFFAAAAAA)

// A green phosphor monitor.
private val TermBlack = Color(0xFF050A05)
private val TermGreen = Color(0xFF33FF66)
private val TermDimGreen = Color(0xFF1FA846)
private val TermDeepGreen = Color(0xFF0E3A1A)

/** The colours of [style] (MODERN has none of its own: the app's). */
internal fun metroStyleColors(style: MetroStyle): ColorScheme = when (style) {
    MetroStyle.WIN95 -> lightColorScheme(
        primary = Win95Navy, onPrimary = Color.White,
        primaryContainer = Win95Navy, onPrimaryContainer = Color.White,
        secondary = Color(0xFF008080), onSecondary = Color.White,
        secondaryContainer = Win95Navy, onSecondaryContainer = Color.White,
        tertiary = Color(0xFF808000), onTertiary = Color.White,
        background = Win95Grey, onBackground = Color.Black,
        surface = Win95Grey, onSurface = Color.Black,
        surfaceVariant = Win95Grey, onSurfaceVariant = Color(0xFF404040),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Win95Grey,
        surfaceContainer = Win95Grey, surfaceContainerHigh = Win95Grey, surfaceContainerHighest = Color(0xFFDFDFDF),
        outline = Color(0xFF808080), outlineVariant = Color(0xFF808080),
        error = Color(0xFFC00000), onError = Color.White,
        inverseSurface = Color.Black, inverseOnSurface = Color.White,
    )
    MetroStyle.DOS -> darkColorScheme(
        primary = DosYellow, onPrimary = Color.Black,
        primaryContainer = DosDarkCyan, onPrimaryContainer = Color.Black,
        secondary = DosCyan, onSecondary = Color.Black,
        secondaryContainer = DosDarkCyan, onSecondaryContainer = Color.Black,
        tertiary = DosYellow, onTertiary = Color.Black,
        background = DosBlue, onBackground = DosCyan,
        surface = DosBlue, onSurface = DosCyan,
        surfaceVariant = DosBlue, onSurfaceVariant = DosGrey,
        surfaceContainerLowest = DosBlue, surfaceContainerLow = DosBlue,
        surfaceContainer = DosBlue, surfaceContainerHigh = DosBlue, surfaceContainerHighest = DosBlue,
        outline = DosCyan, outlineVariant = DosDarkCyan,
        error = Color(0xFFFF5555), onError = Color.Black,
        inverseSurface = DosGrey, inverseOnSurface = Color.Black,
    )
    MetroStyle.TERMINAL -> darkColorScheme(
        primary = TermGreen, onPrimary = Color.Black,
        primaryContainer = TermDeepGreen, onPrimaryContainer = TermGreen,
        secondary = TermGreen, onSecondary = Color.Black,
        secondaryContainer = TermDeepGreen, onSecondaryContainer = TermGreen,
        tertiary = TermGreen, onTertiary = Color.Black,
        background = TermBlack, onBackground = TermGreen,
        surface = TermBlack, onSurface = TermGreen,
        surfaceVariant = TermBlack, onSurfaceVariant = TermDimGreen,
        surfaceContainerLowest = TermBlack, surfaceContainerLow = TermBlack,
        surfaceContainer = TermBlack, surfaceContainerHigh = TermBlack, surfaceContainerHighest = TermDeepGreen,
        outline = TermDimGreen, outlineVariant = TermDeepGreen,
        error = Color(0xFFFFB000), onError = Color.Black,
        inverseSurface = TermGreen, inverseOnSurface = Color.Black,
    )
    MetroStyle.MODERN -> lightColorScheme()
}

/** The app's type, every style of it in the font of [style]. */
private fun metroStyleTypography(style: MetroStyle): Typography {
    val base = Typography()
    val family = metroStyleFont(style) ?: return base
    fun TextStyle.inFont() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.inFont(), displayMedium = base.displayMedium.inFont(), displaySmall = base.displaySmall.inFont(),
        headlineLarge = base.headlineLarge.inFont(), headlineMedium = base.headlineMedium.inFont(), headlineSmall = base.headlineSmall.inFont(),
        titleLarge = base.titleLarge.inFont(), titleMedium = base.titleMedium.inFont(), titleSmall = base.titleSmall.inFont(),
        bodyLarge = base.bodyLarge.inFont(), bodyMedium = base.bodyMedium.inFont(), bodySmall = base.bodySmall.inFont(),
        labelLarge = base.labelLarge.inFont(), labelMedium = base.labelMedium.inFont(), labelSmall = base.labelSmall.inFont(),
    )
}

/** The style's name, as the chooser and its button say it. */
@Composable
internal fun metroStyleName(style: MetroStyle): String = stringResource(
    when (style) {
        MetroStyle.MODERN -> R.string.metro_style_modern
        MetroStyle.WIN95 -> R.string.metro_style_win95
        MetroStyle.DOS -> R.string.metro_style_dos
        MetroStyle.TERMINAL -> R.string.metro_style_terminal
    },
)

/**
 * The chooser (15.10): each style with a sample of itself — its colours, its
 * font — and today's first.
 */
@Composable
internal fun MetroStyleDialog(current: MetroStyle, onPick: (MetroStyle) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metro_style)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetroStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onPick(style) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == current, onClick = { onPick(style) })
                        Text(metroStyleName(style), modifier = Modifier.weight(1f).padding(end = 8.dp))
                        MetroStyleSample(style)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** A little window of [style]: its ground, its letters, its frame. */
@Composable
private fun MetroStyleSample(style: MetroStyle) {
    val colors = if (style == MetroStyle.MODERN) MaterialTheme.colorScheme else metroStyleColors(style)
    val shape = if (style == MetroStyle.MODERN) RoundedCornerShape(8.dp) else RectangleShape
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 36.dp)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "M 1",
            color = if (style == MetroStyle.WIN95) colors.primary else colors.onSurface,
            // The system's own faces here: the chooser opens before any style's font is needed.
            fontFamily = when (style) {
                MetroStyle.MODERN -> null
                MetroStyle.WIN95 -> FontFamily.SansSerif
                MetroStyle.DOS, MetroStyle.TERMINAL -> FontFamily.Monospace
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
