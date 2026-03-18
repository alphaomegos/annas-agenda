package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaFilter

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
internal fun MediaLibraryFilterRow(
    mediaFilter: ReadingMediaFilter,
    onShowBooksChange: (Boolean) -> Unit,
    onShowMoviesChange: (Boolean) -> Unit,
    onShowSeriesChange: (Boolean) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MediaTypeCheckbox(
                checked = mediaFilter.showBooks,
                label = stringResource(R.string.reading_media_book),
                onCheckedChange = onShowBooksChange
            )
        }
        item {
            MediaTypeCheckbox(
                checked = mediaFilter.showMovies,
                label = stringResource(R.string.reading_media_movie),
                onCheckedChange = onShowMoviesChange
            )
        }
        item {
            MediaTypeCheckbox(
                checked = mediaFilter.showSeries,
                label = stringResource(R.string.reading_media_series),
                onCheckedChange = onShowSeriesChange
            )
        }
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

@Composable
private fun MediaTypeCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(text = label)
    }
}