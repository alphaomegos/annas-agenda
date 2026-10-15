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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** Whether the style's own details are drawn (15.10): frames, close box, prompt, scan lines. */
val LocalMetroStyleDetails = staticCompositionLocalOf { true }

/** The metro section's screens in the user's style. */
@Composable
fun MetroThemed(vm: AppViewModel, content: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    MetroThemed(state.metroPrefs.style, state.metroPrefs.styleDetails, content)
}

/** [content] in [style], with its [details] or without. */
@Composable
fun MetroThemed(style: MetroStyle, details: Boolean = true, content: @Composable () -> Unit) {
    if (style == MetroStyle.MODERN) {
        CompositionLocalProvider(LocalMetroStyle provides style, LocalMetroStyleDetails provides details, content = content)
        return
    }
    MaterialTheme(
        colorScheme = metroStyleColors(style),
        typography = metroStyleTypography(style),
        shapes = SquareShapes,
    ) {
        CompositionLocalProvider(LocalMetroStyle provides style, LocalMetroStyleDetails provides details, content = content)
    }
}

/* ---------------- the frame of each style (15.10) ---------------- */

/**
 * The bar over a metro screen, in the style: today's as before; Windows'
 * navy title bar fading to blue, white letters, and — with details — its
 * close box (Back); DOS' cyan menu line with black letters; the terminal's
 * black line with a prompt ("> МЕТРО_") and a green rule under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetroTopBar(
    title: String,
    onBack: () -> Unit,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style = LocalMetroStyle.current
    val details = LocalMetroStyleDetails.current
    if (style == MetroStyle.MODERN) {
        val back: @Composable () -> Unit = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }
        val shown: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (centered) CenterAlignedTopAppBar(title = shown, navigationIcon = back, actions = actions)
        else TopAppBar(title = shown, navigationIcon = back, actions = actions)
        return
    }
    val ink = when (style) {
        MetroStyle.WIN95 -> Color.White
        MetroStyle.DOS -> Color.Black
        else -> TermGreen
    }
    val ground: Brush = when (style) {
        MetroStyle.WIN95 -> Brush.horizontalGradient(listOf(Win95Navy, Color(0xFF1084D0)))
        MetroStyle.DOS -> SolidColor(DosDarkCyan)
        else -> SolidColor(TermBlack)
    }
    val shownTitle = if (style == MetroStyle.TERMINAL && details) "> ${title.uppercase()}_" else title
    TopAppBar(
        title = {
            Text(
                shownTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (style == MetroStyle.WIN95) FontWeight.Bold else null,
            )
        },
        navigationIcon = {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = ink)) {
                Text(stringResource(R.string.back))
            }
        },
        actions = {
            actions()
            if (style == MetroStyle.WIN95 && details) Win95CloseBox(onBack)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = ink,
            navigationIconContentColor = ink,
            actionIconContentColor = ink,
        ),
        modifier = Modifier
            .background(ground)
            .drawWithContent {
                drawContent()
                // The terminal's rule under its line.
                if (style == MetroStyle.TERMINAL && details) {
                    drawLine(TermDimGreen, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
                }
            },
    )
}

/** A key of the DOS line: its number and what it does. */
internal data class MetroKey(val number: Int, val label: String, val onPress: () -> Unit)

/**
 * Norton Commander's line of keys along the bottom (15.10): each a white
 * number on black and a black word on cyan; a tap is the key — and on a
 * keyboard F1…F10 are, too. Drawn only in DOS with the style's details.
 */
@Composable
internal fun MetroKeyBar(keys: List<MetroKey>) {
    if (LocalMetroStyle.current != MetroStyle.DOS || !LocalMetroStyleDetails.current) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState()),
    ) {
        keys.forEach { key ->
            Row(
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = key.label) { key.onPress() }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${key.number}", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp))
                Text(
                    key.label,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(DosDarkCyan).padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Windows' close box: a grey bevelled square with a cross, which goes back. */
@Composable
private fun Win95CloseBox(onBack: () -> Unit) {
    val words = stringResource(R.string.back)
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(width = 26.dp, height = 22.dp)
            .background(Win95Grey)
            .metroFrame(MetroStyle.WIN95, details = true)
            .clickable(role = Role.Button, onClickLabel = words) { onBack() }
            .semantics { contentDescription = words },
        contentAlignment = Alignment.Center,
    ) {
        Text("×", color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

/**
 * The style's frame round a panel (15.10): Windows' bevel, raised or — for a
 * field the picture lies in — sunken; DOS' double line; the terminal's thin
 * green line; nothing today, or without details.
 */
internal fun Modifier.metroFrame(style: MetroStyle, details: Boolean, sunken: Boolean = false): Modifier {
    if (!details || style == MetroStyle.MODERN) return this
    return drawWithContent {
        drawContent()
        val px = 1.dp.toPx()
        val w = size.width
        val h = size.height
        when (style) {
            MetroStyle.WIN95 -> {
                // Two lines each side: light from above left, dark below right — or the other way, sunk.
                val outerLight = if (sunken) Color(0xFF808080) else Color(0xFFDFDFDF)
                val innerLight = if (sunken) Color.Black else Color.White
                val outerDark = if (sunken) Color.White else Color.Black
                val innerDark = if (sunken) Color(0xFFDFDFDF) else Color(0xFF808080)
                fun edges(inset: Float, light: Color, dark: Color) {
                    drawLine(light, Offset(inset, inset), Offset(w - inset, inset), px)
                    drawLine(light, Offset(inset, inset), Offset(inset, h - inset), px)
                    drawLine(dark, Offset(inset, h - inset - px / 2), Offset(w - inset, h - inset - px / 2), px)
                    drawLine(dark, Offset(w - inset - px / 2, inset), Offset(w - inset - px / 2, h - inset), px)
                }
                edges(px / 2, outerLight, outerDark)
                edges(px * 1.5f, innerLight, innerDark)
            }
            MetroStyle.DOS -> {
                val line = DosCyan
                drawRect(line, Offset(3 * px, 3 * px), Size(w - 6 * px, h - 6 * px), style = Stroke(1.5f * px))
                drawRect(line, Offset(6 * px, 6 * px), Size(w - 12 * px, h - 12 * px), style = Stroke(1.5f * px))
            }
            MetroStyle.TERMINAL -> drawRect(TermDimGreen, Offset(px / 2, px / 2), Size(w - px, h - px), style = Stroke(px))
            MetroStyle.MODERN -> Unit
        }
    }
}

/** How far a frame of [style] reaches in, so what it holds keeps clear of it. */
internal fun metroFrameInset(style: MetroStyle, details: Boolean) = when {
    !details -> 0.dp
    style == MetroStyle.WIN95 -> 2.dp
    style == MetroStyle.DOS -> 8.dp
    style == MetroStyle.TERMINAL -> 1.dp
    else -> 0.dp
}

/**
 * The field the picture lies in (15.10): Windows' white page, sunk; DOS' and
 * the terminal's ground, framed; today's, as it was.
 */
@Composable
internal fun Modifier.metroMapField(): Modifier {
    val style = LocalMetroStyle.current
    val details = LocalMetroStyleDetails.current
    if (style == MetroStyle.MODERN) return this
    val ground = if (style == MetroStyle.WIN95) Color.White else MaterialTheme.colorScheme.surface
    return background(ground)
        .metroFrame(style, details, sunken = true)
        .padding(metroFrameInset(style, details))
}

/** What the picture's rings are filled with, and the ground it is saved on: white on Windows' page. */
@Composable
internal fun metroPaper(): Color =
    if (LocalMetroStyle.current == MetroStyle.WIN95) Color.White else MaterialTheme.colorScheme.surface

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
internal fun MetroStyleDialog(
    current: MetroStyle,
    details: Boolean,
    onPick: (MetroStyle) -> Unit,
    onDetails: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
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
                // The style's own details, each style's to have or not.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = current != MetroStyle.MODERN, role = Role.Checkbox) { onDetails(!details) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = details, onCheckedChange = onDetails, enabled = current != MetroStyle.MODERN)
                    Text(stringResource(R.string.metro_style_details), style = MaterialTheme.typography.bodyMedium)
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
