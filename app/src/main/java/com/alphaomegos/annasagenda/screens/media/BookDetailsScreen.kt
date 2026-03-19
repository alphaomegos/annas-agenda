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
fun BookDetailsScreen(
    vm: AppViewModel,
    bookId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()

    val book = st.readingBooks.firstOrNull { it.id == bookId }

    if (book == null) {
        MediaDetailsNotFoundScaffold(
            titleRes = R.string.reading_book_title,
            messageRes = R.string.reading_book_not_found,
            onBack = onBack
        )
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

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.setReadingBookCoverFromPickedUri(
            bookId = bookId,
            sourceUri = uri
        )
    }

    val coverBitmap = rememberMediaDetailsCoverBitmap(book.coverUri)

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
                                    ctx.getString(R.string.reading_book_invalid_input),
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
                hasCover = !book.coverUri.isNullOrBlank(),
                onChooseCover = { pickCover.launch(arrayOf("image/*")) },
                onRemoveCover = {
                    vm.removeReadingBookCover(bookId)
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
                                R.string.reading_book_field_year_read
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
                            ctx.getString(R.string.reading_book_invalid_input),
                            Toast.LENGTH_SHORT
                        ).show()
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

    MediaDetailsDeleteDialog(
        open = confirmDelete.value,
        titleRes = R.string.reading_book_delete_title,
        textRes = R.string.reading_book_delete_text,
        onDismiss = { confirmDelete.value = false },
        onConfirmDelete = {
            vm.deleteReadingBook(bookId)
            confirmDelete.value = false
            onBack()
        }
    )
}