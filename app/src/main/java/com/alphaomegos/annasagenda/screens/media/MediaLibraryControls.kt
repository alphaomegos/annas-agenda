package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun MediaLibraryShelfTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            text = { Text(stringResource(R.string.reading_tab_plans)) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            text = { Text(stringResource(R.string.reading_tab_now)) }
        )
        Tab(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            text = { Text(stringResource(R.string.reading_tab_done)) }
        )
        Tab(
            selected = selectedTab == 3,
            onClick = { onSelectTab(3) },
            text = { Text(stringResource(R.string.reading_tab_abandoned)) }
        )
    }
}

@Composable
internal fun MediaLibraryTopBarFilters(
    mediaFilter: ReadingMediaFilter,
    searchEnabled: Boolean,
    onSearchEnabledChange: (Boolean) -> Unit,
    onShowBooksChange: (Boolean) -> Unit,
    onShowMoviesChange: (Boolean) -> Unit,
    onShowSeriesChange: (Boolean) -> Unit,
    onToggleView: () -> Unit,
    onOpenSort: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaTypeToggleIcon(
            checked = searchEnabled,
            iconRes = R.drawable.ic_filter_search,
            onClick = { onSearchEnabledChange(!searchEnabled) }
        )

        MediaActionIconButton(
            iconRes = R.drawable.ic_filter_view,
            onClick = onToggleView
        )

        MediaActionIconButton(
            iconRes = R.drawable.ic_filter_sort,
            onClick = onOpenSort
        )

        MediaTypeToggleIcon(
            checked = mediaFilter.showBooks,
            iconRes = R.drawable.ic_filter_book,
            onClick = { onShowBooksChange(!mediaFilter.showBooks) }
        )

        MediaTypeToggleIcon(
            checked = mediaFilter.showMovies,
            iconRes = R.drawable.ic_filter_movie,
            onClick = { onShowMoviesChange(!mediaFilter.showMovies) }
        )

        MediaTypeToggleIcon(
            checked = mediaFilter.showSeries,
            iconRes = R.drawable.ic_filter_series,
            onClick = { onShowSeriesChange(!mediaFilter.showSeries) }
        )
    }
}

@Composable
private fun MediaTypeToggleIcon(
    checked: Boolean,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)

    val backgroundColor = if (checked) {
        Color(0xFFDDF4D8)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (checked) 1f else 0.42f)
        )
    }
}

@Composable
private fun MediaActionIconButton(
    iconRes: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val scope = rememberCoroutineScope()
    var flashed by remember { mutableStateOf(false) }

    val backgroundColor = if (flashed) {
        Color(0xFFDDF4D8)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable {
                flashed = true
                scope.launch {
                    delay(140)
                    flashed = false
                }
                onClick()
            }
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
internal fun MediaLibrarySearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text(stringResource(R.string.reading_search_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun MediaLibraryEmptyState(
    isSearching: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(
                if (isSearching) {
                    R.string.reading_nothing_found
                } else {
                    R.string.reading_empty
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}