package com.alphaomegos.annasagenda.screens.media

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingMediaType
import com.alphaomegos.annasagenda.ReadingShelf

/**
 * Everything the three details screens say about themselves.
 *
 * One row per kind of media, so "which words does a film use" is a table
 * rather than a difference spread across two hundred lines of an otherwise
 * identical screen.
 */
internal data class MediaDetailsStrings(
    val screenTitleRes: Int,
    val notFoundRes: Int,
    val invalidInputRes: Int,
    val doneButtonRes: Int,
    val deleteTitleRes: Int,
    val deleteTextRes: Int,
    /** "Year read" for a book, "year watched" for the two you watch. */
    val yearFinishedLabelRes: Int,
)

internal fun mediaDetailsStrings(type: ReadingMediaType): MediaDetailsStrings = when (type) {
    ReadingMediaType.BOOKS -> MediaDetailsStrings(
        screenTitleRes = R.string.reading_book_title,
        notFoundRes = R.string.reading_book_not_found,
        invalidInputRes = R.string.reading_book_invalid_input,
        doneButtonRes = R.string.reading_book_done,
        deleteTitleRes = R.string.reading_book_delete_title,
        deleteTextRes = R.string.reading_book_delete_text,
        yearFinishedLabelRes = R.string.reading_book_field_year_read,
    )

    ReadingMediaType.MOVIES -> MediaDetailsStrings(
        screenTitleRes = R.string.reading_movie_title,
        notFoundRes = R.string.reading_movie_not_found,
        invalidInputRes = R.string.reading_movie_invalid_input,
        doneButtonRes = R.string.reading_movie_done,
        deleteTitleRes = R.string.reading_movie_delete_title,
        deleteTextRes = R.string.reading_movie_delete_text,
        yearFinishedLabelRes = R.string.reading_movie_field_year_watched,
    )

    ReadingMediaType.SERIES -> MediaDetailsStrings(
        screenTitleRes = R.string.reading_series_title,
        notFoundRes = R.string.reading_series_not_found,
        invalidInputRes = R.string.reading_series_invalid_input,
        doneButtonRes = R.string.reading_series_done,
        deleteTitleRes = R.string.reading_series_delete_title,
        deleteTextRes = R.string.reading_series_delete_text,
        yearFinishedLabelRes = R.string.reading_series_field_year_watched,
    )
}

/**
 * The details screen for one item of media, whichever kind it is.
 *
 * There were three of these, 764 lines between them, and 702 of those lines
 * were the same screen written out three times: the same bar with Delete and
 * Save, the same two toasts, the same cover card, the same card of fields, the
 * same Done button, the same delete dialog. The 62 lines that genuinely
 * differed are the fields each kind owns — pages for a book, a release year
 * for a film, seasons and episodes for a series — and those arrive through
 * [extraFields].
 *
 * The title, the shelf and the year are here rather than in [extraFields]
 * because all three kinds have them and treat them identically. The caller
 * still owns the state: this draws the fields and reports edits back.
 *
 * [onSave] returns whether the item was actually saved, which is what decides
 * between the two toasts and, from the Done button, whether the screen closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaDetailsForm(
    type: ReadingMediaType,
    coverUri: String?,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Boolean,
    onBack: () -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    shelf: ReadingShelf,
    onShelfChange: (ReadingShelf) -> Unit,
    yearText: String,
    onYearTextChange: (String) -> Unit,
    extraFields: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val strings = mediaDetailsStrings(type)

    val confirmDelete = rememberSaveable { mutableStateOf(false) }
    var shelfMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val coverBitmap = rememberMediaDetailsCoverBitmap(coverUri)

    fun toast(messageRes: Int) {
        Toast.makeText(ctx, ctx.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(strings.screenTitleRes)) },
                navigationIcon = {},
                actions = {
                    TextButton(onClick = { confirmDelete.value = true }) {
                        Text(stringResource(R.string.delete))
                    }

                    TextButton(
                        onClick = {
                            if (onSave()) toast(R.string.saved) else toast(strings.invalidInputRes)
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
                hasCover = !coverUri.isNullOrBlank(),
                onChooseCover = onPickCover,
                onRemoveCover = onRemoveCover
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
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.reading_media_field_title_required)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    extraFields()

                    MediaDetailsShelfSelector(
                        shelf = shelf,
                        menuExpanded = shelfMenuExpanded,
                        onMenuExpandedChange = { shelfMenuExpanded = it },
                        onShelfSelected = onShelfChange
                    )

                    if (shelf == ReadingShelf.DONE || shelf == ReadingShelf.ABANDONED) {
                        MediaDetailsNumberField(
                            value = yearText,
                            onValueChange = onYearTextChange,
                            labelRes = if (shelf == ReadingShelf.DONE) {
                                strings.yearFinishedLabelRes
                            } else {
                                R.string.reading_media_field_year_abandoned
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = {
                    if (onSave()) onBack() else toast(strings.invalidInputRes)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(strings.doneButtonRes))
            }
        }
    }

    MediaDetailsDeleteDialog(
        open = confirmDelete.value,
        titleRes = strings.deleteTitleRes,
        textRes = strings.deleteTextRes,
        onDismiss = { confirmDelete.value = false },
        onConfirmDelete = {
            onDelete()
            confirmDelete.value = false
            onBack()
        }
    )
}

/** A whole-number field, the shape every extra field on these screens has. */
@Composable
internal fun MediaDetailsNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    maxDigits: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val digits = typed.filter { it.isDigit() }
            onValueChange(if (maxDigits == null) digits else digits.take(maxDigits))
        },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A plain text field, for the odd one out like a film's translation. */
@Composable
internal fun MediaDetailsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
