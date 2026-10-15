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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.MetroPrefs
import com.alphaomegos.annasagenda.model.MetroStyle

/**
 * The buttons over the picture (15.10): closer, farther and — where the
 * screen has no button of its own for it, [onFit] — the whole picture. In
 * the corner, as on any map, and in the section's style.
 */
@Composable
internal fun MetroZoomButtons(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetroMapButton(Icons.Default.Add, stringResource(R.string.metro_zoom_in), onZoomIn)
        MetroMapButton(Icons.Default.Remove, stringResource(R.string.metro_zoom_out), onZoomOut)
        if (onFit != null) MetroMapButton(Icons.Default.FitScreen, stringResource(R.string.metro_fit), onFit)
    }
}

/**
 * One small button lying on the picture (15.10): today a round one with a
 * shadow; Windows' grey bevelled square; a cyan block, as Norton's keys; a
 * green-edged square on the terminal's black.
 */
@Composable
internal fun MetroMapButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalMetroStyle.current
    val details = LocalMetroStyleDetails.current
    val shape = if (style == MetroStyle.MODERN) CircleShape else RectangleShape
    val ground = when (style) {
        MetroStyle.MODERN -> MaterialTheme.colorScheme.surface
        MetroStyle.WIN95 -> Win95Grey
        MetroStyle.DOS -> DosCyan
        MetroStyle.TERMINAL -> Color.Black
    }
    val ink = when (style) {
        MetroStyle.MODERN -> MaterialTheme.colorScheme.onSurface
        MetroStyle.WIN95, MetroStyle.DOS -> Color.Black
        MetroStyle.TERMINAL -> TermGreen
    }
    val edge = when (style) {
        MetroStyle.WIN95 -> Modifier.metroFrame(style, details)
        MetroStyle.TERMINAL -> Modifier.border(1.dp, TermDimGreen)
        else -> Modifier
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(if (style == MetroStyle.MODERN) 3.dp else 0.dp, shape)
            .background(ground, shape)
            .then(edge)
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
    }
}

/** A tick with its words; the words toggle it too. */
@Composable
internal fun MetroMapTick(text: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier = modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * How a finger and the corner work the picture (15.10), the same in the
 * scheme and in its editor: the zoom buttons, the double tap, the small
 * picture of the whole.
 */
@Composable
internal fun MetroMapPrefsTicks(prefs: MetroPrefs, onPrefs: ((MetroPrefs) -> MetroPrefs) -> Unit) {
    MetroMapTick(stringResource(R.string.metro_pref_zoom_buttons), prefs.zoomButtons, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(zoomButtons = on) }
    }
    MetroMapTick(stringResource(R.string.metro_pref_double_tap), prefs.doubleTapZoom, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(doubleTapZoom = on) }
    }
    MetroMapTick(stringResource(R.string.metro_pref_minimap), prefs.minimap, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(minimap = on) }
    }
}

/** What only the scheme's picture does, not the editor's (15.10): a route found, framed. */
@Composable
internal fun MetroViewerPrefsTicks(prefs: MetroPrefs, onPrefs: ((MetroPrefs) -> MetroPrefs) -> Unit) {
    MetroMapTick(stringResource(R.string.metro_pref_frame_route), prefs.frameRoute, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(frameRoute = on) }
    }
    MetroMapTick(stringResource(R.string.metro_pref_legend), prefs.legend, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(legend = on) }
    }
    MetroMapTick(stringResource(R.string.metro_pref_station_card), prefs.stationCard, Modifier.fillMaxWidth()) { on ->
        onPrefs { it.copy(stationCard = on) }
    }
}
