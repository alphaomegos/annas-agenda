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
fun BookDetailsScreen(
    vm: AppViewModel,
    bookId: Long,
    onBack: () -> Unit,
) {
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

    val pickCover = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.setReadingMediaCoverFromPickedUri(
            type = ReadingMediaType.BOOKS,
            itemId = bookId,
            sourceUri = uri
        )
    }

    LaunchedEffect(bookId, book.shelf, book.yearRead, book.yearAbandoned) {
        shelf = book.shelf
        yearText = shelfYearText(
            shelf = book.shelf,
            years = ShelfYears(finished = book.yearRead, abandoned = book.yearAbandoned),
            currentYear = LocalDate.now().year,
        )
    }

    fun validateAndSave(): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return false

        val pages = pagesText.toIntOrNull() ?: 0
        if (pages <= 0) return false

        val cur = currentPageText.toIntOrNull() ?: 0
        if (cur !in 0..pages) return false

        val years = shelfYearsFromText(shelf, yearText, LocalDate.now().year)

        vm.updateReadingBook(
            bookId = bookId,
            author = author,
            title = cleanTitle,
            totalPages = pages,
            currentPage = cur,
            yearRead = years.finished,
            yearAbandoned = years.abandoned,
            shelf = shelf
        )
        return true
    }

    MediaDetailsForm(
        type = ReadingMediaType.BOOKS,
        coverUri = book.coverUri,
        onPickCover = { pickCover.launch(arrayOf("image/*")) },
        onRemoveCover = { vm.removeReadingMediaCover(ReadingMediaType.BOOKS, bookId) },
        onDelete = { vm.deleteReadingBook(bookId) },
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
        MediaDetailsTextField(
            value = author,
            onValueChange = { author = it },
            labelRes = R.string.reading_book_field_author,
        )

        MediaDetailsNumberField(
            value = pagesText,
            onValueChange = { pagesText = it },
            labelRes = R.string.reading_book_field_pages_required,
        )

        MediaDetailsNumberField(
            value = currentPageText,
            onValueChange = { currentPageText = it },
            labelRes = R.string.reading_book_field_current_page,
        )
    }
}
