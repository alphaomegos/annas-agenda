package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * How the words sit under a tile's picture.
 *
 * [Regular] is the language screen: four tiles, each a quarter of the screen,
 * where the name of the language matters as much as its flag. [Small] is the
 * main menu on a wide screen, where the picture is the item and the word is
 * only there to confirm it — one line, small, and as little of the tile as a
 * word can take.
 */
/** Regular and Small carry the title under the picture; IconOnly (25.10, «mini buttons») shows the picture alone. */
internal enum class MenuTileLabel { Regular, Small, IconOnly }

/** A section not there yet: its picture in grey, a little faded. */
internal val comingSoonIconFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

internal const val COMING_SOON_ICON_ALPHA = 0.6f

/**
 * A card with a picture filling it and a caption underneath.
 *
 * [onLongClick], when given, is a long press on the whole tile — the main menu
 * uses it to start rearranging. It goes through the same tap detector the
 * menu's list rows use rather than through the card's own click, because the
 * card's click knows nothing about long presses and two detectors on one tile
 * would race for the same touch.
 *
 * [onClick] null means the tile answers no touch at all, which is what the
 * main menu wants while tiles are being dragged around: the drag is then the
 * only thing listening, and a finger lifted on a tile does not open it.
 */
@Composable
internal fun MenuTile(
    iconRes: Int,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: MenuTileLabel = MenuTileLabel.Regular,
    onLongClick: (() -> Unit)? = null,
    comingSoon: Boolean = false,
    // Said under a grey tile's name; "Coming soon" when none is given.
    greyNote: String? = null,
) {
    val small = label != MenuTileLabel.Regular
    val titled = label != MenuTileLabel.IconOnly

    val shape = RoundedCornerShape(22.dp)
    val colorsEnabled = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val colorsComingSoon = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    val colorsDisabled = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (small) 8.dp else 12.dp)
                .alpha(if (enabled) 1f else 0.55f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    // Without a title under it, the picture says what it is to TalkBack.
                    contentDescription = if (titled) null else title,
                    colorFilter = if (comingSoon) comingSoonIconFilter else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (small) 2.dp else 6.dp)
                        .alpha(if (comingSoon) COMING_SOON_ICON_ALPHA else 1f),
                    contentScale = ContentScale.Fit
                )
            }

            if (titled) Spacer(Modifier.height(if (small) 4.dp else 8.dp))

            if (titled) Text(
                text = title,
                style = if (small) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                textAlign = TextAlign.Center,
                maxLines = if (small) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                color = if (comingSoon) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier.fillMaxWidth()
            )
            if (comingSoon && titled) {
                Text(
                    text = greyNote ?: stringResource(R.string.menu_coming_soon),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val click = onClick
    val longClick = onLongClick
    // Mini buttons (28.10): no card round the picture — no fill, no shadow.
    val bareColors = CardDefaults.elevatedCardColors(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    )
    val elevation = if (titled) CardDefaults.elevatedCardElevation()
    else CardDefaults.elevatedCardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    when {
        enabled && click != null && longClick != null -> ElevatedCard(
            // Taps through a gesture detector carry no semantics; name the two
            // actions so TalkBack can press and long-press the tile (04.10).
            modifier = modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                this.onClick(label = null) { click(); true }
                this.onLongClick(label = null) { longClick(); true }
            }.pointerInput(click, longClick) {
                detectTapGestures(
                    onTap = { click() },
                    onLongPress = { longClick() },
                )
            },
            shape = shape,
            elevation = elevation,
            colors = if (!titled) bareColors else if (comingSoon) colorsComingSoon else colorsEnabled
        ) { content() }

        enabled && click != null -> ElevatedCard(
            onClick = click,
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            colors = if (!titled) bareColors else if (comingSoon) colorsComingSoon else colorsEnabled
        ) { content() }

        else -> ElevatedCard(
            modifier = modifier,
            shape = shape,
            elevation = elevation,
            colors = if (!titled) bareColors else if (!enabled) colorsDisabled else if (comingSoon) colorsComingSoon else colorsEnabled
        ) { content() }
    }
}