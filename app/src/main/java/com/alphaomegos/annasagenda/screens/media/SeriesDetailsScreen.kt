package com.alphaomegos.annasagenda.screens.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.NoShelfYears
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ShelfYears
import com.alphaomegos.annasagenda.shelfYearText
import com.alphaomegos.annasagenda.shelfYearsFromText
import java.time.LocalDate

@Composable
fun SeriesDetailsScreen(
    vm: AppViewModel,
    seriesId: Long,
    onBack: () -> Unit,
) {
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

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.setReadingMediaCoverFromPickedUri(
            type = ReadingMediaType.SERIES,
            itemId = seriesId,
            sourceUri = uri
        )
    }

    LaunchedEffect(seriesId, series.shelf, series.yearWatched, series.yearAbandoned) {
        shelf = series.shelf
        yearText = shelfYearText(
            shelf = series.shelf,
            years = ShelfYears(finished = series.yearWatched, abandoned = series.yearAbandoned),
            currentYear = LocalDate.now().year,
        )
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

        val years = shelfYearsFromText(shelf, yearText, LocalDate.now().year)

        vm.updateReadingSeries(
            seriesId = seriesId,
            title = cleanTitle,
            totalSeasons = totalSeasons,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            yearWatched = years.finished,
            yearAbandoned = years.abandoned,
            shelf = shelf
        )
        return true
    }

    MediaDetailsForm(
        type = ReadingMediaType.SERIES,
        coverUri = series.coverUri,
        onPickCover = { pickCover.launch(arrayOf("image/*")) },
        onRemoveCover = { vm.removeReadingMediaCover(ReadingMediaType.SERIES, seriesId) },
        onDelete = { vm.deleteReadingSeries(seriesId) },
        onSave = { validateAndSave() },
        onBack = onBack,
        title = title,
        onTitleChange = { title = it },
        shelf = shelf,
        onShelfChange = { selected ->
            shelf = selected
            yearText = shelfYearText(selected, NoShelfYears, LocalDate.now().year)
        },
        yearText = yearText,
        onYearTextChange = { yearText = it },
    ) {
        MediaDetailsNumberField(
            value = totalSeasonsText,
            onValueChange = { totalSeasonsText = it },
            labelRes = R.string.reading_field_total_seasons,
        )

        MediaDetailsNumberField(
            value = currentSeasonText,
            onValueChange = { currentSeasonText = it },
            labelRes = R.string.reading_field_current_season,
        )

        MediaDetailsNumberField(
            value = currentEpisodeText,
            onValueChange = { currentEpisodeText = it },
            labelRes = R.string.reading_field_current_episode,
        )
    }
}
