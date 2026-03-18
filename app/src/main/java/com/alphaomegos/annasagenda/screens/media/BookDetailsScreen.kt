package com.alphaomegos.annasagenda.screens.media

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.ReadingShelf
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    vm: AppViewModel,
    bookId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()

    val book = st.readingBooks.firstOrNull { it.id == bookId }

    if (book == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.reading_book_title)) },
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
                    text = stringResource(R.string.reading_book_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var title by rememberSaveable(bookId) { mutableStateOf(book.title) }
    var author by rememberSaveable(bookId) { mutableStateOf(book.author) }
    var pagesText by rememberSaveable(bookId) { mutableStateOf(book.totalPages.toString()) }
    var currentPageText by rememberSaveable(bookId) { mutableStateOf(book.currentPage.toString()) }
    var yearText by rememberSaveable(bookId) { mutableStateOf(book.yearRead?.toString() ?: "") }

    var shelf by rememberSaveable(bookId) { mutableStateOf(book.shelf) }

    var shelfMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val confirmDelete = rememberSaveable { mutableStateOf(false) }

    // Cover picker
    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        }

        vm.updateReadingBook(
            bookId = bookId,
            coverUri = uri.toString(),
            clearCover = false
        )
    }

    // Real image render (no external libs)
    val coverBitmap: ImageBitmap? by produceState(initialValue = null, key1 = book.coverUri) {
        val s = book.coverUri
        if (s.isNullOrBlank()) {
            value = null
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeImageBitmap(ctx, s.toUri(), targetMaxSidePx = 800)
            }.getOrNull()
        }
    }

    LaunchedEffect(bookId, book.shelf, book.yearRead, book.yearAbandoned) {
        shelf = book.shelf
        yearText = when (book.shelf) {
            ReadingShelf.DONE -> (book.yearRead ?: LocalDate.now().year).toString()
            ReadingShelf.ABANDONED -> (book.yearAbandoned ?: LocalDate.now().year).toString()
            ReadingShelf.PLANS,
            ReadingShelf.NOW -> ""
        }
    }

    fun validateAndSave(): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return false

        val pages = pagesText.toIntOrNull() ?: 0
        if (pages <= 0) return false

        val cur = currentPageText.toIntOrNull() ?: 0
        if (cur !in 0..pages) return false

        val yearRead: Int? = when (shelf) {
            ReadingShelf.DONE -> yearText.toIntOrNull() ?: LocalDate.now().year
            else -> null
        }

        val yearAbandoned: Int? = when (shelf) {
            ReadingShelf.ABANDONED -> yearText.toIntOrNull() ?: LocalDate.now().year
            else -> null
        }

        vm.updateReadingBook(
            bookId = bookId,
            author = author,
            title = cleanTitle,
            totalPages = pages,
            currentPage = cur,
            yearRead = yearRead,
            yearAbandoned = yearAbandoned,
            shelf = shelf
        )
        return true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_book_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { confirmDelete.value = true }) {
                        Text(stringResource(R.string.delete))
                    }
                    TextButton(onClick = {
                        val ok = validateAndSave()
                        if (!ok) {
                            Toast.makeText(ctx, ctx.getString(R.string.reading_book_invalid_input), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, ctx.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                        }
                    }) {
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
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                bitmap = coverBitmap!!,
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
                            onClick = { pickCover.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.reading_book_choose_cover))
                        }

                        if (!book.coverUri.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    vm.updateReadingBook(bookId = bookId, clearCover = true)
                                }
                            ) {
                                Text(stringResource(R.string.reading_book_remove_cover))
                            }
                        }
                    }

                    if (!book.coverUri.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.reading_book_cover_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(0.9f)
                        )
                    }
                }
            }

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.reading_book_field_author)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { pagesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_book_field_pages_required)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currentPageText,
                        onValueChange = { currentPageText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.reading_book_field_current_page)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            Button(onClick = { shelfMenuExpanded = true }) {
                                Text(shelfLabel(shelf))
                            }
                            DropdownMenu(
                                expanded = shelfMenuExpanded,
                                onDismissRequest = { shelfMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reading_tab_plans)) },
                                    onClick = {
                                        shelfMenuExpanded = false
                                        shelf = ReadingShelf.PLANS
                                        yearText = ""
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reading_tab_now)) },
                                    onClick = {
                                        shelfMenuExpanded = false
                                        shelf = ReadingShelf.NOW
                                        yearText = ""
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reading_tab_done)) },
                                    onClick = {
                                        shelfMenuExpanded = false
                                        shelf = ReadingShelf.DONE
                                        yearText = LocalDate.now().year.toString()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reading_tab_abandoned)) },
                                    onClick = {
                                        shelfMenuExpanded = false
                                        shelf = ReadingShelf.ABANDONED
                                        yearText = LocalDate.now().year.toString()
                                    }
                                )
                            }
                        }
                    }

                    if (shelf == ReadingShelf.DONE || shelf == ReadingShelf.ABANDONED) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { ch -> ch.isDigit() } },
                            label = {
                                Text(
                                    stringResource(
                                        if (shelf == ReadingShelf.DONE) {
                                            R.string.reading_book_field_year_read
                                        } else {
                                            R.string.reading_book_field_year_abandoned
                                        }
                                    )
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = {
                    val ok = validateAndSave()
                    if (!ok) {
                        Toast.makeText(ctx, ctx.getString(R.string.reading_book_invalid_input), Toast.LENGTH_SHORT).show()
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reading_book_done))
            }
        }
    }

    if (confirmDelete.value) {
        AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text(stringResource(R.string.reading_book_delete_title)) },
            text = { Text(stringResource(R.string.reading_book_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteReadingBook(bookId)
                    confirmDelete.value = false
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun shelfLabel(shelf: ReadingShelf): String {
    return when (shelf) {
        ReadingShelf.PLANS -> stringResource(R.string.reading_tab_plans)
        ReadingShelf.NOW -> stringResource(R.string.reading_tab_now)
        ReadingShelf.DONE -> stringResource(R.string.reading_tab_done)
        ReadingShelf.ABANDONED -> stringResource(R.string.reading_tab_abandoned)
    }
}

private fun decodeImageBitmap(
    ctx: Context,
    uri: Uri,
    targetMaxSidePx: Int
): ImageBitmap {
    val cr = ctx.contentResolver

    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(cr, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val maxSide = max(w, h).coerceAtLeast(1)
            val scale = min(1f, targetMaxSidePx.toFloat() / maxSide.toFloat())
            val tw = max(1, (w * scale).toInt())
            val th = max(1, (h * scale).toInt())
            decoder.setTargetSize(tw, th)
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(cr, uri)
    }

    return bitmap.asImageBitmap()
}