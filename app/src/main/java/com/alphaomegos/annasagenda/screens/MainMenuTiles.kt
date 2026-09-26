package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val TILE_MIN_HEIGHT_DP = 150
private const val TILE_ICON_SIZE_DP = 88

/**
 * The main menu as tiles, for a screen wide enough that a row of one item is
 * mostly empty.
 *
 * The icons are the reason this exists. In the list they sit at the end of a
 * row at 64dp and read as decoration; here they are the item, at 88dp, with
 * the word underneath. On an unfolded foldable that is the difference between
 * a long thin list and a menu.
 *
 * **Reordering and hiding are not here.** A long press does what it does in
 * the list — turns reorder mode on — and the caller then shows the list,
 * because dragging a row up and down a column is a thing the list already
 * does properly and a grid would need reinventing in two dimensions. Switching
 * layout to rearrange is a visible seam, but it is an honest one: the shape
 * you are dragging in is the shape the order is stored in.
 */
@Composable
internal fun MainMenuTiles(
    items: List<MenuEntry>,
    columns: Int,
    onStartReorder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items, key = { it.id }) { item ->
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TILE_MIN_HEIGHT_DP.dp)
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { item.onClick() },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartReorder()
                            },
                        )
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(TILE_ICON_SIZE_DP.dp),
                    )

                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
