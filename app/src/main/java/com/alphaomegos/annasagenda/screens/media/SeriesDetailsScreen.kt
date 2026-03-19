package com.alphaomegos.annasagenda.screens.media

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    vm: AppViewModel,
    seriesId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()

    val series = st.readingSeries.firstOrNull { it.id == seriesId }

    if (series == null) {
        MediaDetailsNotFoundScaffold(
            titleRes = R.string.reading_series_title,
            messageRes = R.string.reading_series_not_found,
            onBack = onBack
        )
        return
    }

    var title by rememberSaveable(seriesId) { mutableStateOf(series.title) }
    var totalSeasonsText by rememberSaveable(seriesId) {
        mutableStateOf(series.totalSeasons.toString())
    }
    var currentSeasonText by rememberSaveable(seriesId) {
        mutableStateOf(series.currentSeason.toString())
    }
    var currentEpisodeText by rememberSaveable(seriesId) {
        mutableStateOf(series.currentEpisode.toString())
    }
    var yearText by rememberSaveable(seriesId) {
        mutableStateOf(series.yearWatched?.toString() ?: "")
    }
    var shelf by rememberSaveable(seriesId) { mutableStateOf(series.shelf) }

    var shelfMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val confirmDelete = rememberSaveable { mutableStateOf(false) }

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.setReadingSeriesCoverFromPickedUri(
            seriesId = seriesId,
            sourceUri = uri
        )
    }

    val coverBitmap = rememberMediaDetailsCoverBitmap(series.coverUri)

    LaunchedEffect(seriesId, series.shelf, series.yearWatched, series.yearAbandoned) {
        shelf = series.shelf
        yearText = when (series.shelf) {
            ReadingShelf.DONE -> (series.yearWatched ?: LocalDate.now().year).toString()
            ReadingShelf.ABANDONED -> (series.yearAbandoned ?: LocalDate.now().year).toString()
            ReadingShelf.PLANS,
            ReadingShelf.NOW -> ""
        }
    }

    fun validateAndSave(): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return false

        val totalSeasons = totalSeasonsText.toIntOrNull() ?: 0
        if (totalSeasons <= 0) return false

        val currentSeason = currentSeasonText.toIntOrNull() ?: 0
        if (currentSeason !in 1..totalSeasons) return false

        val currentEpisode = currentEpisodeText.toIntOrNull() ?: 0
        if (currentEpisode <= 0) return false

        val yearWatched: Int? = when (shelf) {
            ReadingShelf.DONE -> yearText.toIntOrNull() ?: LocalDate.now().year
            else -> null
        }

        val yearAbandoned: Int? = when (shelf) {
            ReadingShelf.ABANDONED -> yearText.toIntOrNull() ?: LocalDate.now().year
            else -> null
        }

        vm.updateReadingSeries(
            seriesId = seriesId,
            title = cleanTitle,
            totalSeasons = totalSeasons,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            yearWatched = yearWatched,
            yearAbandoned = yearAbandoned,
            shelf = shelf
        )
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_series_title)) },
                navigationIcon = {},
                actions = {
                    TextButton(onClick = { confirmDelete.value = true }) {
                        Text(stringResource(R.string.delete))
                    }
                    TextButton(
                        onClick = {
                            val ok = validateAndSave()
                            if (!ok) {
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.reading_series_invalid_input),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaDetailsCoverCard(
                coverBitmap = coverBitmap,
                hasCover = !series.coverUri.isNullOrBlank(),
                onChooseCover = { pickCover.launch(arrayOf("image/*")) },
                onRemoveCover = {
                    vm.removeReadingSeriesCover(seriesId)
                }
            )

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.reading_book_field_title_required)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = totalSeasonsText,
                        onValueChange = { totalSeasonsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_field_total_seasons)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentSeasonText,
                        onValueChange = { currentSeasonText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_field_current_season)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentEpisodeText,
                        onValueChange = { currentEpisodeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_field_current_episode)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    MediaDetailsShelfSelector(
                        shelf = shelf,
                        menuExpanded = shelfMenuExpanded,
                        onMenuExpandedChange = { shelfMenuExpanded = it },
                        onShelfSelected = { selectedShelf ->
                            shelf = selectedShelf
                            yearText = mediaDetailsDefaultYearForShelf(selectedShelf)
                        }
                    )

                    if (shelf == ReadingShelf.DONE || shelf == ReadingShelf.ABANDONED) {
                        MediaDetailsYearField(
                            value = yearText,
                            onValueChange = { yearText = it },
                            labelRes = if (shelf == ReadingShelf.DONE) {
                                R.string.reading_series_field_year_watched
                            } else {
                                R.string.reading_book_field_year_abandoned
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = {
                    val ok = validateAndSave()
                    if (!ok) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.reading_series_invalid_input),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reading_series_done))
            }
        }
    }

    MediaDetailsDeleteDialog(
        open = confirmDelete.value,
        titleRes = R.string.reading_series_delete_title,
        textRes = R.string.reading_series_delete_text,
        onDismiss = { confirmDelete.value = false },
        onConfirmDelete = {
            vm.deleteReadingSeries(seriesId)
            confirmDelete.value = false
            onBack()
        }
    )
}