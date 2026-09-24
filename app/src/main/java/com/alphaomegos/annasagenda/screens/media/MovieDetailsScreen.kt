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
import com.alphaomegos.annasagenda.isPossibleReleaseYear
import com.alphaomegos.annasagenda.shelfYearText
import com.alphaomegos.annasagenda.shelfYearsFromText
import java.time.LocalDate

@Composable
fun MovieDetailsScreen(
    vm: AppViewModel,
    movieId: Long,
    onBack: () -> Unit,
) {
    val st by vm.state.collectAsState()

    val movie = st.readingMovies.firstOrNull { it.id == movieId }

    if (movie == null) {
        MediaDetailsNotFoundScaffold(
            titleRes = R.string.reading_movie_title,
            messageRes = R.string.reading_movie_not_found,
            onBack = onBack
        )
        return
    }

    var title by rememberSaveable(movieId) { mutableStateOf(movie.title) }
    var releaseYearText by rememberSaveable(movieId) {
        mutableStateOf(movie.releaseYear?.toString() ?: "")
    }
    var translation by rememberSaveable(movieId) { mutableStateOf(movie.translation) }
    var yearText by rememberSaveable(movieId) { mutableStateOf(movie.yearWatched?.toString() ?: "") }
    var shelf by rememberSaveable(movieId) { mutableStateOf(movie.shelf) }

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.setReadingMediaCoverFromPickedUri(
            type = ReadingMediaType.MOVIES,
            itemId = movieId,
            sourceUri = uri
        )
    }

    LaunchedEffect(movieId, movie.shelf, movie.yearWatched, movie.yearAbandoned) {
        shelf = movie.shelf
        yearText = shelfYearText(
            shelf = movie.shelf,
            years = ShelfYears(finished = movie.yearWatched, abandoned = movie.yearAbandoned),
            currentYear = LocalDate.now().year,
        )
    }

    fun validateAndSave(): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return false

        // An empty field means "no release year"; anything else has to be one.
        val releaseYear: Int? = if (releaseYearText.isBlank()) {
            null
        } else {
            releaseYearText.toIntOrNull()?.takeIf(::isPossibleReleaseYear) ?: return false
        }

        val years = shelfYearsFromText(shelf, yearText, LocalDate.now().year)

        vm.updateReadingMovie(
            movieId = movieId,
            title = cleanTitle,
            releaseYear = releaseYear,
            clearReleaseYear = releaseYear == null,
            translation = translation.trim(),
            yearWatched = years.finished,
            yearAbandoned = years.abandoned,
            shelf = shelf
        )
        return true
    }

    MediaDetailsForm(
        type = ReadingMediaType.MOVIES,
        coverUri = movie.coverUri,
        onPickCover = { pickCover.launch(arrayOf("image/*")) },
        onRemoveCover = { vm.removeReadingMediaCover(ReadingMediaType.MOVIES, movieId) },
        onDelete = { vm.deleteReadingMovie(movieId) },
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
            value = releaseYearText,
            onValueChange = { releaseYearText = it },
            labelRes = R.string.reading_movie_field_release_year,
            maxDigits = 4,
        )

        MediaDetailsTextField(
            value = translation,
            onValueChange = { translation = it },
            labelRes = R.string.reading_movie_field_translation,
        )
    }
}
