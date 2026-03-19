package com.alphaomegos.annasagenda.screens.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import java.time.LocalDate
import com.alphaomegos.annasagenda.util.loadCoverBitmapForUi

@Composable
fun mediaDetailsShelfLabel(shelf: ReadingShelf): String {
    return when (shelf) {
        ReadingShelf.PLANS -> stringResource(R.string.reading_tab_plans)
        ReadingShelf.NOW -> stringResource(R.string.reading_tab_now)
        ReadingShelf.DONE -> stringResource(R.string.reading_tab_done)
        ReadingShelf.ABANDONED -> stringResource(R.string.reading_tab_abandoned)
    }
}

@Composable
fun rememberMediaDetailsCoverBitmap(
    coverUri: String?
): ImageBitmap? {
    val context = LocalContext.current

    val bitmap: ImageBitmap? by produceState(initialValue = null, key1 = coverUri) {
        value = loadCoverBitmapForUi(
            context = context,
            coverRef = coverUri
        )
    }

    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsNotFoundScaffold(
    titleRes: Int,
    messageRes: Int,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MediaDetailsCoverCard(
    coverBitmap: ImageBitmap?,
    hasCover: Boolean,
    onChooseCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_menu_reading),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onChooseCover,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.reading_book_choose_cover))
                }

                if (hasCover) {
                    Button(onClick = onRemoveCover) {
                        Text(stringResource(R.string.reading_book_remove_cover))
                    }
                }
            }

            if (hasCover) {
                Text(
                    text = stringResource(R.string.reading_book_cover_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.9f)
                )
            }
        }
    }
}

@Composable
fun MediaDetailsShelfSelector(
    shelf: ReadingShelf,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onShelfSelected: (ReadingShelf) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.reading_book_field_shelf),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Box {
            Button(onClick = { onMenuExpandedChange(true) }) {
                Text(mediaDetailsShelfLabel(shelf))
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reading_tab_plans)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onShelfSelected(ReadingShelf.PLANS)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reading_tab_now)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onShelfSelected(ReadingShelf.NOW)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reading_tab_done)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onShelfSelected(ReadingShelf.DONE)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reading_tab_abandoned)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onShelfSelected(ReadingShelf.ABANDONED)
                    }
                )
            }
        }
    }
}

@Composable
fun MediaDetailsYearField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun MediaDetailsDeleteDialog(
    open: Boolean,
    titleRes: Int,
    textRes: Int,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    if (!open) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(textRes)) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

fun mediaDetailsDefaultYearForShelf(shelf: ReadingShelf): String {
    return when (shelf) {
        ReadingShelf.DONE,
        ReadingShelf.ABANDONED -> LocalDate.now().year.toString()

        ReadingShelf.PLANS,
        ReadingShelf.NOW -> ""
    }
}

