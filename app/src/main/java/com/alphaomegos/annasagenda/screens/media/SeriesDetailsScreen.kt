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
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.support.NoShelfYears
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.ReadingMediaType
import com.alphaomegos.annasagenda.support.ShelfYears
import com.alphaomegos.annasagenda.support.shelfYearText
import com.alphaomegos.annasagenda.support.shelfYearsFromText
import java.time.LocalDate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alphaomegos.annasagenda.components.PickDateDialog
import com.alphaomegos.annasagenda.model.ReadingShelf
import com.alphaomegos.annasagenda.support.formatShortDate
import com.alphaomegos.annasagenda.util.appLocale
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import com.alphaomegos.annasagenda.canPostNotifications

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
    // Waiting for the next season (15.10): its day (as an epoch day, -1 for none) and the reminder.
    var nextSeasonDay by rememberSaveable(seriesId) { mutableStateOf(series.nextSeasonDate?.toEpochDay() ?: -1L) }
    var remind by rememberSaveable(seriesId) { mutableStateOf(series.remindNextSeason) }
    var pickingDay by rememberSaveable(seriesId) { mutableStateOf(false) }
    val context = LocalContext.current
    // Ticking the reminder on a phone that may not post asks, as the settings do (Android 13+).
    val askNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val setRemind: (Boolean) -> Unit = { on ->
        remind = on
        if (on && !canPostNotifications(context) && Build.VERSION.SDK_INT >= 33) {
            askNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
            shelf = shelf,
            nextSeasonDate = nextSeasonDay.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) },
            clearNextSeasonDate = nextSeasonDay < 0,
            remindNextSeason = remind,
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

        // Waiting for the next season (15.10): the day it is expected out, and a reminder on it.
        if (shelf == ReadingShelf.WAITING) {
            val day = nextSeasonDay.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reading_field_next_season),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pickingDay = true }) {
                    Text(day?.let { formatShortDate(it, appLocale()) } ?: stringResource(R.string.reading_next_season_pick))
                }
                if (day != null) {
                    TextButton(onClick = { nextSeasonDay = -1L }) { Text(stringResource(R.string.reading_next_season_clear)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { setRemind(!remind) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remind, onCheckedChange = { setRemind(it) })
                Text(stringResource(R.string.reading_remind_next_season), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.reading_remind_next_season_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (remind && !canPostNotifications(context)) {
                Text(
                    stringResource(R.string.reading_remind_notifications_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (remind && day == null) {
                Text(
                    stringResource(R.string.reading_remind_needs_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (pickingDay) {
        PickDateDialog(
            initialDate = nextSeasonDay.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) },
            onDismiss = { pickingDay = false },
            onPicked = { picked ->
                nextSeasonDay = picked.toEpochDay()
                pickingDay = false
            },
        )
    }
}
