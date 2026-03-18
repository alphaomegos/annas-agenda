package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ReadingShelf
import com.alphaomegos.annasagenda.ReadingSortField

@Composable
internal fun AddMediaDialog(
    initialShelf: ReadingShelf,
    initialType: ReadingMediaType,
    onDismiss: () -> Unit,
    onAddBook: (title: String, pages: Int, author: String) -> Unit,
    onAddMovie: (title: String, releaseYear: Int?, translation: String) -> Unit,
    onAddSeries: (title: String, totalSeasons: Int, currentSeason: Int, currentEpisode: Int) -> Unit,
) {
    var mediaType by rememberSaveable { mutableStateOf(initialType) }
    var title by rememberSaveable { mutableStateOf("") }
    var pagesText by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var releaseYearText by rememberSaveable { mutableStateOf("") }
    var translation by rememberSaveable { mutableStateOf("") }
    var totalSeasonsText by rememberSaveable { mutableStateOf("1") }
    var currentSeasonText by rememberSaveable { mutableStateOf("1") }
    var currentEpisodeText by rememberSaveable { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_add_item_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaTypeRadioRow(
                    label = stringResource(R.string.reading_media_book),
                    selected = mediaType == ReadingMediaType.BOOKS,
                    onSelect = { mediaType = ReadingMediaType.BOOKS }
                )
                MediaTypeRadioRow(
                    label = stringResource(R.string.reading_media_movie),
                    selected = mediaType == ReadingMediaType.MOVIES,
                    onSelect = { mediaType = ReadingMediaType.MOVIES }
                )
                MediaTypeRadioRow(
                    label = stringResource(R.string.reading_media_series),
                    selected = mediaType == ReadingMediaType.SERIES,
                    onSelect = { mediaType = ReadingMediaType.SERIES }
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.reading_field_title_required)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (mediaType == ReadingMediaType.BOOKS) {
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { pagesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_field_pages_required)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.reading_field_author)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mediaType == ReadingMediaType.MOVIES) {
                    OutlinedTextField(
                        value = releaseYearText,
                        onValueChange = { releaseYearText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.reading_movie_field_release_year)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = translation,
                        onValueChange = { translation = it },
                        label = { Text(stringResource(R.string.reading_movie_field_translation)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (mediaType == ReadingMediaType.SERIES) {
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
                }

                if (initialShelf == ReadingShelf.DONE || initialShelf == ReadingShelf.ABANDONED) {
                    Text(
                        text = stringResource(R.string.reading_note_year_auto),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (mediaType) {
                        ReadingMediaType.BOOKS -> {
                            val pages = pagesText.toIntOrNull() ?: 0
                            onAddBook(title, pages, author)
                        }

                        ReadingMediaType.MOVIES -> {
                            val releaseYear = releaseYearText.toIntOrNull()
                            onAddMovie(title, releaseYear, translation)
                        }

                        ReadingMediaType.SERIES -> {
                            val totalSeasons = totalSeasonsText.toIntOrNull() ?: 1
                            val currentSeason = currentSeasonText.toIntOrNull() ?: 1
                            val currentEpisode = currentEpisodeText.toIntOrNull() ?: 1
                            onAddSeries(title, totalSeasons, currentSeason, currentEpisode)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.reading_action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MediaTypeRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

@Composable
internal fun SortDialog(
    initialField: ReadingSortField,
    initialAscending: Boolean,
    onDismiss: () -> Unit,
    onApply: (ReadingSortField, Boolean) -> Unit,
) {
    var field by rememberSaveable { mutableStateOf(initialField) }
    var asc by rememberSaveable { mutableStateOf(initialAscending) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_sort_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SortFieldRow(
                    label = stringResource(R.string.reading_sort_field_author),
                    selected = field == ReadingSortField.AUTHOR,
                    onSelect = { field = ReadingSortField.AUTHOR }
                )
                SortFieldRow(
                    label = stringResource(R.string.reading_sort_field_title),
                    selected = field == ReadingSortField.TITLE,
                    onSelect = { field = ReadingSortField.TITLE }
                )
                SortFieldRow(
                    label = stringResource(R.string.reading_sort_field_pages),
                    selected = field == ReadingSortField.PAGES,
                    onSelect = { field = ReadingSortField.PAGES }
                )
                SortFieldRow(
                    label = stringResource(R.string.reading_sort_field_year),
                    selected = field == ReadingSortField.YEAR,
                    onSelect = { field = ReadingSortField.YEAR }
                )
                SortFieldRow(
                    label = stringResource(R.string.reading_sort_field_release_year),
                    selected = field == ReadingSortField.RELEASE_YEAR,
                    onSelect = { field = ReadingSortField.RELEASE_YEAR }
                )

                Spacer(modifier = Modifier.size(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.reading_sort_descending))
                    Switch(checked = asc, onCheckedChange = { asc = it })
                    Text(stringResource(R.string.reading_sort_ascending))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(field, asc) }) {
                Text(stringResource(R.string.reading_action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SortFieldRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}