package com.alphaomegos.annasagenda.screens.media

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import com.alphaomegos.annasagenda.ReadingSortField
import com.alphaomegos.annasagenda.ReadingViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    onBack: () -> Unit,
    vm: AppViewModel = viewModel(),
    onOpenBook: (Long) -> Unit = {},
    onOpenMovie: (Long) -> Unit = {},
    onOpenSeries: (Long) -> Unit = {},
    onStartReading: (Long) -> Unit = {},
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val shelf = mediaLibraryShelfForTab(selectedTab)

    val prefs = when (shelf) {
        ReadingShelf.PLANS -> st.readingPlansPrefs
        ReadingShelf.NOW -> st.readingNowPrefs
        ReadingShelf.DONE -> st.readingDonePrefs
        ReadingShelf.ABANDONED -> st.readingAbandonedPrefs
    }

    val mediaFilter = st.readingMediaFilter
    val enabledTypeCount = listOf(
        mediaFilter.showBooks,
        mediaFilter.showMovies,
        mediaFilter.showSeries
    ).count { it }

    val isSearching = searchQuery.isNotBlank()

    val items = remember(st, shelf, prefs.sort, searchQuery) {
        buildVisibleReadingItems(
            state = st,
            shelf = shelf,
            sort = prefs.sort,
            query = searchQuery
        )
    }

    val showYearGroups =
        !isSearching &&
                prefs.sort.field == ReadingSortField.YEAR &&
                (shelf == ReadingShelf.DONE || shelf == ReadingShelf.ABANDONED)

    val yearGroups = remember(items, shelf, showYearGroups) {
        if (showYearGroups) {
            buildReadingYearGroups(items, shelf)
        } else {
            emptyList()
        }
    }

    val showMediaTypeLabel = isSearching || enabledTypeCount > 1

    var menuExpanded by remember { mutableStateOf(false) }
    val addDialogOpen = remember { mutableStateOf(false) }
    val sortDialogOpen = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.reading_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { addDialogOpen.value = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reading_menu_toggle_view)) },
                            onClick = {
                                menuExpanded = false
                                vm.setReadingViewMode(shelf, nextMediaLibraryViewMode(prefs.viewMode))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reading_menu_sort)) },
                            onClick = {
                                menuExpanded = false
                                sortDialogOpen.value = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MediaLibraryShelfTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )

            MediaLibraryFilterRow(
                mediaFilter = mediaFilter,
                onShowBooksChange = { vm.setReadingMediaFilter(showBooks = it) },
                onShowMoviesChange = { vm.setReadingMediaFilter(showMovies = it) },
                onShowSeriesChange = { vm.setReadingMediaFilter(showSeries = it) }
            )

            MediaLibrarySearchField(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
            )

            if (items.isEmpty()) {
                MediaLibraryEmptyState(isSearching = isSearching)
            } else {
                MediaLibraryItemsContent(
                    viewMode = prefs.viewMode,
                    showYearGroups = showYearGroups,
                    items = items,
                    yearGroups = yearGroups,
                    remainingHours = { bookId -> vm.estimateRemainingHours(bookId) },
                    showShelfLabel = isSearching,
                    showMediaTypeLabel = showMediaTypeLabel,
                    onOpenItem = { item ->
                        openMediaLibraryItem(
                            item = item,
                            onOpenBook = onOpenBook,
                            onOpenMovie = onOpenMovie,
                            onOpenSeries = onOpenSeries
                        )
                    },
                    onReadItem = { item ->
                        readReadingItem(
                            vm = vm,
                            item = item,
                            onStartReading = onStartReading
                        )
                    },
                    onMoveItem = { item, to ->
                        moveReadingItem(
                            vm = vm,
                            item = item,
                            to = to
                        )
                    },
                    onDeleteItem = { item ->
                        deleteReadingItem(
                            vm = vm,
                            item = item
                        )
                    }
                )
            }
        }
    }

    if (addDialogOpen.value) {
        AddMediaDialog(
            initialShelf = shelf,
            initialType = defaultMediaType(mediaFilter),
            onDismiss = { addDialogOpen.value = false },
            onAddBook = { title, pages, author ->
                val id = vm.addReadingBook(
                    shelf = shelf,
                    title = title,
                    totalPages = pages,
                    author = author
                )
                if (id == null) {
                    showMediaLibraryInvalidInputToast(ctx)
                } else {
                    addDialogOpen.value = false
                }
            },
            onAddMovie = { title, releaseYear, translation ->
                val id = vm.addReadingMovie(
                    shelf = shelf,
                    title = title,
                    releaseYear = releaseYear,
                    translation = translation
                )
                if (id == null) {
                    showMediaLibraryInvalidInputToast(ctx)
                } else {
                    addDialogOpen.value = false
                }
            },
            onAddSeries = { title, totalSeasons, currentSeason, currentEpisode ->
                val id = vm.addReadingSeries(
                    shelf = shelf,
                    title = title,
                    totalSeasons = totalSeasons,
                    currentSeason = currentSeason,
                    currentEpisode = currentEpisode
                )
                if (id == null) {
                    showMediaLibraryInvalidInputToast(ctx)
                } else {
                    addDialogOpen.value = false
                }
            }
        )
    }

    if (sortDialogOpen.value) {
        SortDialog(
            initialField = prefs.sort.field,
            initialAscending = prefs.sort.ascending,
            onDismiss = { sortDialogOpen.value = false },
            onApply = { field, asc ->
                vm.setReadingSort(shelf, field, asc)
                sortDialogOpen.value = false
            }
        )
    }
}

@Composable
private fun MediaLibraryItemsContent(
    viewMode: ReadingViewMode,
    showYearGroups: Boolean,
    items: List<ReadingUiItem>,
    yearGroups: List<ReadingYearGroup>,
    remainingHours: (Long) -> Int?,
    showShelfLabel: Boolean,
    showMediaTypeLabel: Boolean,
    onOpenItem: (ReadingUiItem) -> Unit,
    onReadItem: (ReadingUiItem) -> Unit,
    onMoveItem: (ReadingUiItem, ReadingShelf) -> Unit,
    onDeleteItem: (ReadingUiItem) -> Unit,
) {
    when {
        showYearGroups && viewMode == ReadingViewMode.LIST -> {
            ReadingItemsListGroupedByYear(
                groups = yearGroups,
                remainingHours = remainingHours,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel,
                onOpenItem = onOpenItem,
                onReadItem = onReadItem,
                onMoveItem = onMoveItem,
                onDeleteItem = onDeleteItem
            )
        }

        showYearGroups && viewMode == ReadingViewMode.GRID -> {
            ReadingItemsGridGroupedByYear(
                groups = yearGroups,
                remainingHours = remainingHours,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel,
                onOpenItem = onOpenItem,
                onReadItem = onReadItem,
                onMoveItem = onMoveItem,
                onDeleteItem = onDeleteItem
            )
        }

        viewMode == ReadingViewMode.LIST -> {
            ReadingItemsList(
                items = items,
                remainingHours = remainingHours,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel,
                onOpenItem = onOpenItem,
                onReadItem = onReadItem,
                onMoveItem = onMoveItem,
                onDeleteItem = onDeleteItem
            )
        }

        else -> {
            ReadingItemsGrid(
                items = items,
                remainingHours = remainingHours,
                showShelfLabel = showShelfLabel,
                showMediaTypeLabel = showMediaTypeLabel,
                onOpenItem = onOpenItem,
                onReadItem = onReadItem,
                onMoveItem = onMoveItem,
                onDeleteItem = onDeleteItem
            )
        }
    }
}

private fun mediaLibraryShelfForTab(selectedTab: Int): ReadingShelf {
    return when (selectedTab) {
        0 -> ReadingShelf.PLANS
        1 -> ReadingShelf.NOW
        2 -> ReadingShelf.DONE
        else -> ReadingShelf.ABANDONED
    }
}

private fun nextMediaLibraryViewMode(current: ReadingViewMode): ReadingViewMode {
    return if (current == ReadingViewMode.GRID) {
        ReadingViewMode.LIST
    } else {
        ReadingViewMode.GRID
    }
}

private fun openMediaLibraryItem(
    item: ReadingUiItem,
    onOpenBook: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    when (item) {
        is ReadingBookItem -> onOpenBook(item.id)
        is ReadingMovieItem -> onOpenMovie(item.id)
        is ReadingSeriesItem -> onOpenSeries(item.id)
    }
}

private fun readReadingItem(
    vm: AppViewModel,
    item: ReadingUiItem,
    onStartReading: (Long) -> Unit,
) {
    if (item is ReadingBookItem) {
        val ok = vm.beginReading(item.id)
        if (ok) onStartReading(item.id)
    }
}

private fun moveReadingItem(
    vm: AppViewModel,
    item: ReadingUiItem,
    to: ReadingShelf,
) {
    when (item) {
        is ReadingBookItem -> vm.moveReadingBookToShelf(item.id, to)
        is ReadingMovieItem -> vm.moveReadingMovieToShelf(item.id, to)
        is ReadingSeriesItem -> vm.moveReadingSeriesToShelf(item.id, to)
    }
}

private fun deleteReadingItem(
    vm: AppViewModel,
    item: ReadingUiItem,
) {
    when (item) {
        is ReadingBookItem -> vm.deleteReadingBook(item.id)
        is ReadingMovieItem -> vm.deleteReadingMovie(item.id)
        is ReadingSeriesItem -> vm.deleteReadingSeries(item.id)
    }
}

private fun showMediaLibraryInvalidInputToast(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.reading_toast_invalid_input),
        Toast.LENGTH_SHORT
    ).show()
}