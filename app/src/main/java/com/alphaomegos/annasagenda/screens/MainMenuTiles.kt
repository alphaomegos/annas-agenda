package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.MAIN_MENU_TILE_GAP_DP
import com.alphaomegos.annasagenda.components.MenuTile
import com.alphaomegos.annasagenda.components.MenuTileLabel
import com.alphaomegos.annasagenda.mainMenuTileHeightDp

/**
 * The main menu as tiles, for a screen wide enough that a row of one item is
 * mostly empty.
 *
 * **The picture is the item.** Each tile is as large as the screen allows —
 * square, or a little shorter when the screen is short, see
 * [mainMenuTileHeightDp] — and the picture fills it; the word underneath is
 * one small line. The first version (0121) did the opposite: big tiles with an
 * 88dp icon in the middle and a title in a heading font, which on an unfolded
 * foldable read as a lot of empty card.
 *
 * The card is the shared [MenuTile], the one the language screen uses, rather
 * than a copy of it written again here.
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tileHeight = mainMenuTileHeightDp(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
            columns = columns,
            itemCount = items.size,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MAIN_MENU_TILE_GAP_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(MAIN_MENU_TILE_GAP_DP.dp),
        ) {
            items(items = items, key = { it.id }) { item ->
                MenuTile(
                    iconRes = item.iconRes,
                    title = stringResource(item.titleRes),
                    onClick = item.onClick,
                    label = MenuTileLabel.Small,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartReorder()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tileHeight.dp),
                )
            }
        }
    }
}
