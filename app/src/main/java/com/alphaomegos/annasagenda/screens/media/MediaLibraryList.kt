package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import com.alphaomegos.annasagenda.util.loadCoverBitmapForUi
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
internal fun ReadingItemsList(
    items: List<ReadingUiItem>,
    remainingHours: (Long) -> Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpenItem: (ReadingUiItem) -> Unit,
    onReadItem: (ReadingUiItem) -> Unit,
    onMoveItem: (ReadingUiItem, ReadingShelf) -> Unit,
    onDeleteItem: (ReadingUiItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = items, key = { it.stableKey }) { item ->
            ReadingItemRowCard(
                item = item,
                remainingHours = if (item is ReadingBookItem) remainingHours(item.id) else null,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel,
                onOpen = { onOpenItem(item) },
                onRead = if (item is ReadingBookItem) ({ onReadItem(item) }) else null,
                onMove = { to -> onMoveItem(item, to) },
                onDelete = { onDeleteItem(item) }
            )
        }
    }
}

@Composable
internal fun ReadingItemsGrid(
    items: List<ReadingUiItem>,
    remainingHours: (Long) -> Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpenItem: (ReadingUiItem) -> Unit,
    onReadItem: (ReadingUiItem) -> Unit,
    onMoveItem: (ReadingUiItem, ReadingShelf) -> Unit,
    onDeleteItem: (ReadingUiItem) -> Unit,
) {
    val rows = remember(items) { items.chunked(2) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = rows, key = { row -> row.firstOrNull()?.stableKey ?: "empty-row" }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { item ->
                    ReadingItemGridCard(
                        item = item,
                        remainingHours = if (item is ReadingBookItem) remainingHours(item.id) else null,
                        showShelfLabel = showShelfLabel,
                        showMediaTypeLabel = showMediaTypeLabel,
                        modifier = Modifier.weight(1f),
                        onOpen = { onOpenItem(item) },
                        onRead = if (item is ReadingBookItem) ({ onReadItem(item) }) else null,
                        onMove = { to -> onMoveItem(item, to) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable
internal fun ReadingItemsWall(
    items: List<ReadingUiItem>,
    onOpenItem: (ReadingUiItem) -> Unit,
) {
    if (items.isEmpty()) return

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val context = LocalContext.current
    val seed = remember { Random.nextInt() }

    val coverCache = remember(items) {
        mutableStateMapOf<String, ImageBitmap?>()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { size ->
                viewportWidthPx = size.width
                viewportHeightPx = size.height
            }
            .pointerInput(seed, items.size) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return@Box

        val viewportWidth = viewportWidthPx.toFloat()
        val viewportHeight = viewportHeightPx.toFloat()

        val minTileWidthPx = with(density) { 112.dp.toPx() }
        val maxTileWidthPx = with(density) { 168.dp.toPx() }

        val tileWidthPx = (viewportWidth / 3.2f)
            .coerceIn(minTileWidthPx, maxTileWidthPx)

        val tileHeightPx = tileWidthPx * 1.5f

        val tileWidthDp = with(density) { tileWidthPx.toDp() }
        val tileHeightDp = with(density) { tileHeightPx.toDp() }

        val columnRange = wallVisibleRange(
            offsetPx = offsetX,
            tilePx = tileWidthPx,
            viewportPx = viewportWidth
        )

        val rowRange = wallVisibleRange(
            offsetPx = offsetY,
            tilePx = tileHeightPx,
            viewportPx = viewportHeight
        )

        val visibleCells = remember(
            items,
            seed,
            rowRange.first,
            rowRange.last,
            columnRange.first,
            columnRange.last
        ) {
            buildList {
                for (row in rowRange) {
                    for (col in columnRange) {
                        add(
                            WallCell(
                                row = row,
                                col = col,
                                item = wallItemForCell(
                                    items = items,
                                    seed = seed,
                                    row = row,
                                    col = col
                                )
                            )
                        )
                    }
                }
            }
        }

        val visibleCoverRefs = remember(visibleCells) {
            visibleCells
                .mapNotNull { it.item.coverUri?.takeIf(String::isNotBlank) }
                .distinct()
        }

        LaunchedEffect(visibleCoverRefs) {
            visibleCoverRefs.forEach { ref ->
                if (!coverCache.containsKey(ref)) {
                    coverCache[ref] = loadCoverBitmapForUi(
                        context = context,
                        coverRef = ref,
                        targetMaxSidePx = 280
                    )
                }
            }
        }

        for (cell in visibleCells) {
            ReadingWallTile(
                item = cell.item,
                coverBitmap = cell.item.coverUri?.let { coverCache[it] },
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (cell.col * tileWidthPx + offsetX).roundToInt(),
                            y = (cell.row * tileHeightPx + offsetY).roundToInt()
                        )
                    }
                    .width(tileWidthDp)
                    .height(tileHeightDp),
                onOpen = { onOpenItem(cell.item) }
            )
        }
    }
}


@Composable
internal fun ReadingItemsListGroupedByYear(
    groups: List<ReadingYearGroup>,
    remainingHours: (Long) -> Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpenItem: (ReadingUiItem) -> Unit,
    onReadItem: (ReadingUiItem) -> Unit,
    onMoveItem: (ReadingUiItem, ReadingShelf) -> Unit,
    onDeleteItem: (ReadingUiItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { group ->
            item(key = "year-header-${group.year}-${group.items.firstOrNull()?.stableKey ?: "empty"}") {
                YearGroupHeader(
                    year = group.year,
                    count = group.items.size
                )
            }

            items(items = group.items, key = { it.stableKey }) { item ->
                ReadingItemRowCard(
                    item = item,
                    remainingHours = if (item is ReadingBookItem) remainingHours(item.id) else null,
                    showShelfLabel = showShelfLabel,
                    showMediaTypeLabel = showMediaTypeLabel,
                    onOpen = { onOpenItem(item) },
                    onRead = if (item is ReadingBookItem) ({ onReadItem(item) }) else null,
                    onMove = { to -> onMoveItem(item, to) },
                    onDelete = { onDeleteItem(item) }
                )
            }
        }
    }
}

@Composable
internal fun ReadingItemsGridGroupedByYear(
    groups: List<ReadingYearGroup>,
    remainingHours: (Long) -> Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpenItem: (ReadingUiItem) -> Unit,
    onReadItem: (ReadingUiItem) -> Unit,
    onMoveItem: (ReadingUiItem, ReadingShelf) -> Unit,
    onDeleteItem: (ReadingUiItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { group ->
            item(key = "year-header-${group.year}-${group.items.firstOrNull()?.stableKey ?: "empty"}") {
                YearGroupHeader(
                    year = group.year,
                    count = group.items.size
                )
            }

            val rows = group.items.chunked(2)
            items(
                items = rows,
                key = { row -> "year-row-${group.year}-${row.firstOrNull()?.stableKey ?: "empty"}" }
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { item ->
                        ReadingItemGridCard(
                            item = item,
                            remainingHours = if (item is ReadingBookItem) remainingHours(item.id) else null,
                            showShelfLabel = showShelfLabel,
                            showMediaTypeLabel = showMediaTypeLabel,
                            modifier = Modifier.weight(1f),
                            onOpen = { onOpenItem(item) },
                            onRead = if (item is ReadingBookItem) ({ onReadItem(item) }) else null,
                            onMove = { to -> onMoveItem(item, to) },
                            onDelete = { onDeleteItem(item) }
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGroupHeader(
    year: Int?,
    count: Int,
) {
    val yearText = year?.toString() ?: "?"

    Text(
        text = stringResource(R.string.reading_year_group_header, yearText, count),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ReadingItemRowCard(
    item: ReadingUiItem,
    remainingHours: Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpen: (() -> Unit)?,
    onRead: (() -> Unit)?,
    onMove: (ReadingShelf) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val shelf = item.shelf

    val thumbBitmap: ImageBitmap? by produceState(initialValue = null, key1 = item.coverUri) {
        value = loadCoverBitmapForUi(
            context = context,
            coverRef = item.coverUri,
            targetMaxSidePx = 220
        )
    }

    val shape = RoundedCornerShape(22.dp)

    ElevatedCard(
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageModifier = if (onOpen != null) {
                Modifier
                    .size(56.dp)
                    .clickable(onClick = onOpen)
            } else {
                Modifier.size(56.dp)
            }

            Box(
                modifier = imageModifier,
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(mediaPlaceholderRes(item)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                val metaLabel = readingItemMetaLabel(
                    item = item,
                    showShelfLabel = showShelfLabel,
                    showMediaTypeLabel = showMediaTypeLabel
                )
                if (metaLabel.isNotBlank()) {
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )

                val secondary = readingItemSecondaryText(
                    item = item,
                    remainingHours = remainingHours
                )

                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.9f)
                    )
                }
            }

            if (canReadItem(item) && onRead != null) {
                Button(onClick = onRead) {
                    Text(stringResource(R.string.reading_button_read))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            ReadingItemMenu(
                currentShelf = shelf,
                onMove = onMove,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun ReadingItemGridCard(
    item: ReadingUiItem,
    remainingHours: Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    modifier: Modifier,
    onOpen: (() -> Unit)?,
    onRead: (() -> Unit)?,
    onMove: (ReadingShelf) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val shelf = item.shelf

    val coverBitmap: ImageBitmap? by produceState(initialValue = null, key1 = item.coverUri) {
        value = loadCoverBitmapForUi(
            context = context,
            coverRef = item.coverUri,
            targetMaxSidePx = 520
        )
    }

    val shape = RoundedCornerShape(22.dp)

    ElevatedCard(
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val imageModifier = if (onOpen != null) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clickable(onClick = onOpen)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
            }

            Box(
                modifier = imageModifier,
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(mediaPlaceholderRes(item)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = CircleShape
                        )
                ) {
                    ReadingItemMenu(
                        currentShelf = shelf,
                        onMove = onMove,
                        onDelete = onDelete
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            val metaLabel = readingItemMetaLabel(
                item = item,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel
            )
            if (metaLabel.isNotBlank()) {
                Text(
                    text = metaLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall
            )

            val secondary = readingItemSecondaryText(
                item = item,
                remainingHours = remainingHours
            )

            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canReadItem(item) && onRead != null) {
                Spacer(modifier = Modifier.size(8.dp))

                Button(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.reading_button_read))
                }
            }
        }
    }
}

@Composable
private fun ReadingWallTile(
    item: ReadingUiItem,
    coverBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(mediaPlaceholderRes(item)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun mediaPlaceholderRes(item: ReadingUiItem): Int {
    return when (item) {
        is ReadingBookItem -> R.drawable.media_placeholder_book
        is ReadingMovieItem -> R.drawable.media_placeholder_movie
        is ReadingSeriesItem -> R.drawable.media_placeholder_series
    }
}

private fun wallVisibleRange(
    offsetPx: Float,
    tilePx: Float,
    viewportPx: Float,
): IntRange {
    val overscan = 2
    val first = floor((-offsetPx) / tilePx).toInt() - overscan
    val last = ceil((viewportPx - offsetPx) / tilePx).toInt() + overscan
    return first..last
}

private fun wallItemForCell(
    items: List<ReadingUiItem>,
    seed: Int,
    row: Int,
    col: Int,
): ReadingUiItem {
    val index = positiveMod(
        value = mixWallCell(seed = seed, row = row, col = col),
        mod = items.size
    )
    return items[index]
}

private fun mixWallCell(
    seed: Int,
    row: Int,
    col: Int,
): Int {
    var x = seed.toLong()
    x = x * 1103515245L + 12345L + row * 1000003L + col * 2000003L
    x = x xor (x ushr 16)
    x *= 2246822519L
    x = x xor (x ushr 13)
    return x.toInt()
}

private fun positiveMod(
    value: Int,
    mod: Int,
): Int {
    val raw = value % mod
    return if (raw >= 0) raw else raw + mod
}


@Composable
private fun ReadingItemMenu(
    currentShelf: ReadingShelf,
    onMove: (ReadingShelf) -> Unit,
    onDelete: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    IconButton(onClick = { expanded.value = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }

    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        if (currentShelf != ReadingShelf.PLANS) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reading_menu_move_to_plans)) },
                onClick = {
                    expanded.value = false
                    onMove(ReadingShelf.PLANS)
                }
            )
        }
        if (currentShelf != ReadingShelf.NOW) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reading_menu_move_to_now)) },
                onClick = {
                    expanded.value = false
                    onMove(ReadingShelf.NOW)
                }
            )
        }
        if (currentShelf != ReadingShelf.DONE) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reading_menu_move_to_done)) },
                onClick = {
                    expanded.value = false
                    onMove(ReadingShelf.DONE)
                }
            )
        }
        if (currentShelf != ReadingShelf.ABANDONED) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reading_menu_move_to_abandoned)) },
                onClick = {
                    expanded.value = false
                    onMove(ReadingShelf.ABANDONED)
                }
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.reading_menu_delete)) },
            onClick = {
                expanded.value = false
                onDelete()
            }
        )
    }
}

@Composable
private fun readingItemMetaLabel(
    item: ReadingUiItem,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
): String {
    val parts = buildList {
        if (showMediaTypeLabel) add(mediaTypeLabel(item))
        if (showShelfLabel) add(mediaDetailsShelfLabel(item.shelf))
    }
    return parts.joinToString(" • ")
}

@Composable
private fun mediaTypeLabel(item: ReadingUiItem): String {
    return when (item) {
        is ReadingBookItem -> stringResource(R.string.reading_media_book)
        is ReadingMovieItem -> stringResource(R.string.reading_media_movie)
        is ReadingSeriesItem -> stringResource(R.string.reading_media_series)
    }
}

private data class WallCell(
    val row: Int,
    val col: Int,
    val item: ReadingUiItem,
)

@Composable
private fun readingItemSecondaryText(
    item: ReadingUiItem,
    remainingHours: Int?,
): String {
    return when (item) {
        is ReadingBookItem -> {
            when (item.shelf) {
                ReadingShelf.NOW -> {
                    if (remainingHours == null) {
                        stringResource(R.string.reading_remaining_unknown)
                    } else {
                        stringResource(R.string.reading_remaining_hours, remainingHours)
                    }
                }

                ReadingShelf.DONE -> {
                    val y = item.book.yearRead
                    if (y == null) "" else stringResource(R.string.reading_year, y)
                }

                ReadingShelf.ABANDONED -> {
                    val y = item.book.yearAbandoned
                    if (y == null) "" else stringResource(R.string.reading_year, y)
                }

                ReadingShelf.PLANS -> ""
            }
        }

        is ReadingMovieItem -> {
            val releaseYear = item.movie.releaseYear
            if (releaseYear != null) {
                stringResource(R.string.reading_year, releaseYear)
            } else {
                when (item.shelf) {
                    ReadingShelf.DONE -> {
                        val y = item.movie.yearWatched
                        if (y == null) "" else stringResource(R.string.reading_year, y)
                    }

                    ReadingShelf.ABANDONED -> {
                        val y = item.movie.yearAbandoned
                        if (y == null) "" else stringResource(R.string.reading_year, y)
                    }

                    ReadingShelf.PLANS,
                    ReadingShelf.NOW -> ""
                }
            }
        }

        is ReadingSeriesItem -> {
            when (item.shelf) {
                ReadingShelf.DONE -> {
                    val y = item.series.yearWatched
                    if (y == null) "" else stringResource(R.string.reading_year, y)
                }

                ReadingShelf.ABANDONED -> {
                    val y = item.series.yearAbandoned
                    if (y == null) "" else stringResource(R.string.reading_year, y)
                }

                ReadingShelf.PLANS,
                ReadingShelf.NOW -> {
                    stringResource(
                        R.string.reading_series_progress,
                        item.series.currentSeason,
                        item.series.currentEpisode,
                        item.series.totalSeasons
                    )
                }
            }
        }
    }
}