package com.alphaomegos.annasagenda

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * What the user does: pick a cover, decide they picked the wrong one, pick
 * another.
 *
 * That used to leave the state byte-for-byte identical, because the ref was
 * derived from the media kind and the id alone — so nothing was emitted,
 * nothing reloaded, and the screen kept showing a picture that had already
 * been deleted from disk.
 */
@RunWith(AndroidJUnit4::class)
class ReadingCoverReplacementInstrumentedTest {

    private lateinit var app: Application
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        clearAppStateStoreFile()
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        clearAppStateStoreFile()
    }

    @Test
    fun replacingACoverChangesTheStoredRef() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = requireNotNull(
            vm.addReadingBook(
                shelf = ReadingShelf.PLANS,
                title = "Book",
                totalPages = 100,
                author = "Author",
            )
        )

        vm.setReadingMediaCoverFromPickedUri(ReadingMediaType.BOOKS, bookId, imageUri(Color.RED))
        val firstRef = awaitCoverRef(vm, bookId, previous = null)

        assertTrue("the cover must be stored internally", isInternalCoverRef(firstRef))
        assertTrue("its file must exist", coverFile(firstRef).exists())

        vm.setReadingMediaCoverFromPickedUri(ReadingMediaType.BOOKS, bookId, imageUri(Color.BLUE))
        val secondRef = awaitCoverRef(vm, bookId, previous = firstRef)

        assertNotEquals(
            "picking a different cover must be visible in the state",
            firstRef,
            secondRef,
        )
        assertTrue("the new file must exist", coverFile(secondRef).exists())
    }

    /** Whatever the old cover was, it must not be left behind on disk. */
    @Test
    fun replacingACoverTakesTheOldFileAway() = runBlocking {
        val vm = AppViewModel(app)
        awaitLoaded(vm)
        vm.resetAllData()

        val bookId = requireNotNull(
            vm.addReadingBook(
                shelf = ReadingShelf.PLANS,
                title = "Book",
                totalPages = 100,
                author = "Author",
            )
        )

        vm.setReadingMediaCoverFromPickedUri(ReadingMediaType.BOOKS, bookId, imageUri(Color.RED))
        val firstRef = awaitCoverRef(vm, bookId, previous = null)
        val firstFile = coverFile(firstRef)

        vm.setReadingMediaCoverFromPickedUri(ReadingMediaType.BOOKS, bookId, imageUri(Color.BLUE))
        awaitCoverRef(vm, bookId, previous = firstRef)

        awaitGone(firstFile)
    }

    private fun imageUri(color: Int): Uri {
        val file = File(app.cacheDir, "cover_source_${System.nanoTime()}.png")
        tempFiles += file

        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        return Uri.fromFile(file)
    }

    private fun coverFile(ref: String): File =
        File(app.filesDir, "media_covers/${ref.substringAfterLast('/')}")

    private suspend fun awaitCoverRef(
        vm: AppViewModel,
        bookId: Long,
        previous: String?,
    ): String {
        repeat(200) {
            val ref = vm.state.value.readingBooks.firstOrNull { it.id == bookId }?.coverUri
            if (ref != null && ref != previous) return ref
            delay(25)
        }
        error("the cover ref never changed")
    }

    private suspend fun awaitGone(file: File) {
        repeat(200) {
            if (!file.exists()) return
            delay(25)
        }
        error("the replaced cover file is still on disk: ${file.name}")
    }

    private fun clearAppStateStoreFile() {
        val file = File(app.filesDir, "datastore/app_state_store.preferences_pb")
        if (file.exists()) {
            file.delete()
        }
    }
}
