package com.alphaomegos.annasagenda.screens.media

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

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
    val ctx = LocalContext.current
    val shelf = item.shelf

    val thumbBitmap: ImageBitmap? by produceState(initialValue = null, key1 = item.coverUri) {
        val s = item.coverUri
        if (s.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeImageBitmap(ctx, s.toUri(), targetMaxSidePx = 220)
            }.getOrNull()
        }
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
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_menu_reading),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
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
    val ctx = LocalContext.current
    val shelf = item.shelf

    val coverBitmap: ImageBitmap? by produceState(initialValue = null, key1 = item.coverUri) {
        val s = item.coverUri
        if (s.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeImageBitmap(ctx, s.toUri(), targetMaxSidePx = 520)
            }.getOrNull()
        }
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
                        painter = painterResource(R.drawable.ic_menu_reading),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
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

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canReadItem(item) && onRead != null) {
                    Button(onClick = onRead, modifier = Modifier.weight(1f)) {
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
        if (showShelfLabel) add(shelfLabel(item.shelf))
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

@Composable
private fun shelfLabel(shelf: ReadingShelf): String {
    return when (shelf) {
        ReadingShelf.PLANS -> stringResource(R.string.reading_tab_plans)
        ReadingShelf.NOW -> stringResource(R.string.reading_tab_now)
        ReadingShelf.DONE -> stringResource(R.string.reading_tab_done)
        ReadingShelf.ABANDONED -> stringResource(R.string.reading_tab_abandoned)
    }
}

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

private fun decodeImageBitmap(
    ctx: Context,
    uri: Uri,
    targetMaxSidePx: Int,
): ImageBitmap {
    val cr = ctx.contentResolver

    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(cr, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val maxSide = max(w, h).coerceAtLeast(1)
            val scale = min(1f, targetMaxSidePx.toFloat() / maxSide.toFloat())
            val tw = max(1, (w * scale).toInt())
            val th = max(1, (h * scale).toInt())
            decoder.setTargetSize(tw, th)
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(cr, uri)
    }

    return bitmap.asImageBitmap()
}