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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
internal enum class MenuTileLabel { Regular, Small }

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
) {
    val small = label == MenuTileLabel.Small

    val shape = RoundedCornerShape(22.dp)
    val colorsEnabled = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
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
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (small) 2.dp else 6.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(if (small) 4.dp else 8.dp))

            Text(
                text = title,
                style = if (small) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                textAlign = TextAlign.Center,
                maxLines = if (small) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val click = onClick
    val longClick = onLongClick
    when {
        enabled && click != null && longClick != null -> ElevatedCard(
            modifier = modifier.pointerInput(click, longClick) {
                detectTapGestures(
                    onTap = { click() },
                    onLongPress = { longClick() },
                )
            },
            shape = shape,
            colors = colorsEnabled
        ) { content() }

        enabled && click != null -> ElevatedCard(
            onClick = click,
            modifier = modifier,
            shape = shape,
            colors = colorsEnabled
        ) { content() }

        else -> ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = if (enabled) colorsEnabled else colorsDisabled
        ) { content() }
    }
}