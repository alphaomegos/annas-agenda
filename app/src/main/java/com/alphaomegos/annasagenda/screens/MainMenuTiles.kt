package com.alphaomegos.annasagenda.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alphaomegos.annasagenda.support.MAIN_MENU_TILE_GAP_DP
import com.alphaomegos.annasagenda.support.MenuCell
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.MenuTile
import com.alphaomegos.annasagenda.components.MenuTileLabel
import com.alphaomegos.annasagenda.components.TinyIconButton
import com.alphaomegos.annasagenda.support.mainMenuTileHeightDp
import com.alphaomegos.annasagenda.support.menuDropTarget

/** How much a held tile grows, so it reads as lifted off the grid. */
private const val DRAGGED_TILE_SCALE = 1.06f

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
 * **Rearranging happens here, in the grid.** A long press turns reorder mode
 * on and the tiles stay where they are: a held tile follows the finger in both
 * directions, and the others make room as its middle passes over them
 * ([menuDropTarget]). Each tile gets a hide button in its corner, which is
 * what swiping a row away does in the list. 0121 switched to the list for
 * this instead, so that only one drag had to exist; on the phone that turned
 * out to be a jump to a different screen at the exact moment you are holding
 * something, which is the worst moment for one.
 *
 * Moves are reported in reading order — the menu's order is one list whatever
 * shape it is drawn in — and the caller keeps the state, as it does for the
 * list: [draggingIndex] and [draggingOffset] are its, this only draws them.
 *
 * Read through [rememberUpdatedState] inside the gesture, as the list does:
 * the drag runs in a coroutine started once per tile, and the index and
 * offset it started with are stale after the first move.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainMenuTiles(
    items: List<MenuEntry>,
    columns: Int,
    reorderMode: Boolean,
    canHideItems: Boolean,
    draggingIndex: Int,
    draggingOffset: Offset,
    onStartReorder: () -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onMoveItem: (from: Int, to: Int, dragCompensation: Offset) -> Unit,
    onHideItem: (String) -> Unit,
    onStopDragging: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    val latestDraggingIndex by rememberUpdatedState(draggingIndex)
    val latestDraggingOffset by rememberUpdatedState(draggingOffset)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tileHeight = mainMenuTileHeightDp(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
            columns = columns,
            itemCount = items.size,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MAIN_MENU_TILE_GAP_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(MAIN_MENU_TILE_GAP_DP.dp),
        ) {
            itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
                val isDragging = reorderMode && index == draggingIndex
                val currentIndex by rememberUpdatedState(index)

                val dragModifier =
                    if (reorderMode) {
                        Modifier.pointerInput(item.id, true) {
                            detectDragGestures(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragStart(currentIndex)
                                },
                                onDragEnd = { onStopDragging() },
                                onDragCancel = { onStopDragging() },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    val active = latestDraggingIndex
                                    if (active < 0) return@detectDragGestures

                                    // The state update below lands on the next
                                    // frame; the arithmetic needs it now.
                                    val offset = latestDraggingOffset + dragAmount
                                    onDrag(dragAmount)

                                    val visible = gridState.layoutInfo.visibleItemsInfo
                                    val dragged = visible.firstOrNull { it.index == active }
                                        ?: return@detectDragGestures

                                    val target = menuDropTarget(
                                        draggedIndex = active,
                                        x = dragged.offset.x + offset.x + dragged.size.width / 2f,
                                        y = dragged.offset.y + offset.y + dragged.size.height / 2f,
                                        cells = visible.map { info ->
                                            MenuCell(
                                                index = info.index,
                                                left = info.offset.x,
                                                top = info.offset.y,
                                                width = info.size.width,
                                                height = info.size.height,
                                            )
                                        },
                                    ) ?: return@detectDragGestures

                                    // The tile is about to be laid out in the
                                    // target's place; shift the drag by the
                                    // same amount so it stays under the finger.
                                    onMoveItem(
                                        active,
                                        target.index,
                                        Offset(
                                            (dragged.offset.x - target.left).toFloat(),
                                            (dragged.offset.y - target.top).toFloat(),
                                        ),
                                    )
                                },
                            )
                        }
                    } else {
                        Modifier
                    }

                Box(
                    modifier = Modifier
                        // The held tile is placed by the finger, not animated:
                        // an animation would fight the compensation above.
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = if (isDragging) {
                                null
                            } else {
                                spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                )
                            },
                        )
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = draggingOffset.x
                                translationY = draggingOffset.y
                                scaleX = DRAGGED_TILE_SCALE
                                scaleY = DRAGGED_TILE_SCALE
                            }
                        }
                        .fillMaxWidth()
                        .height(tileHeight.dp),
                ) {
                    MenuTile(
                        iconRes = item.iconRes,
                        title = stringResource(item.titleRes),
                        onClick = if (reorderMode) null else item.onClick,
                        comingSoon = item.comingSoon,
                        label = MenuTileLabel.Small,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartReorder()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragModifier),
                    )

                    if (reorderMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .alpha(if (canHideItems) 1f else 0.35f),
                        ) {
                            TinyIconButton(
                                onClick = { if (canHideItems) onHideItem(item.id) },
                                icon = Icons.Default.VisibilityOff,
                                cd = stringResource(R.string.hide_menu_item),
                            )
                        }
                    }
                }
            }
        }
    }
}
